package com.learnlock.app.data

import com.learnlock.taskengine.AttemptRecord
import com.learnlock.taskengine.LearnerStats
import com.learnlock.taskengine.SkillTag
import com.learnlock.taskengine.Task
import com.learnlock.taskengine.TaskCategory
import com.learnlock.taskengine.TaskEngine
import com.learnlock.taskengine.TaskRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Фасад доменной логики LearnLock для UI и lock-слоя.
 * Связывает профиль, сессию, статистику и [TaskEngine].
 */
class LearnLockRepository(
    private val profileDao: ProfileDao,
    private val parentAuthDao: ParentAuthDao,
    private val statsDao: StatsDao,
    private val sessionStore: LearnLockSession,
    private val taskEngine: TaskEngine
) {
    fun observeProfile(): Flow<ChildProfileEntity?> = profileDao.observeProfile()
    fun observeSession(): Flow<SessionState> = sessionStore.state

    fun observeAttemptTotals(): Flow<AttemptTotals> = statsDao.observeTotals()

    /** Попытки за последние [days] дней (экран статистики). */
    fun observeStats(days: Int): Flow<List<TaskAttemptEntity>> {
        val from = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return statsDao.observeSince(from)
    }

    /** Создаёт профиль по умолчанию, если БД пуста. */
    suspend fun ensureDefaults() {
        if (profileDao.getProfile() == null) {
            profileDao.upsert(ChildProfileEntity())
        }
    }

    suspend fun hasParentPin(): Boolean = parentAuthDao.get() != null

    /** Сохраняет новый родительский PIN (4–12 цифр) как PBKDF2-хеш. */
    suspend fun setParentPin(pin: String) {
        require(pin.length in 4..12) { "PIN must be 4-12 digits" }
        val salt = PinHasher.createSalt()
        parentAuthDao.upsert(
            ParentAuthEntity(
                pinHash = PinHasher.hash(pin, salt),
                salt = salt
            )
        )
    }

    /** Проверяет введённый PIN родителя. */
    suspend fun verifyParentPin(pin: String): ParentPinVerification {
        val entity = parentAuthDao.get() ?: return ParentPinVerification(valid = false)
        val now = System.currentTimeMillis()
        if (entity.lockedUntilMillis > now) {
            return ParentPinVerification(valid = false, lockedUntilMillis = entity.lockedUntilMillis)
        }
        if (PinHasher.matches(pin, entity)) {
            parentAuthDao.upsert(entity.copy(failedAttempts = 0, lockedUntilMillis = 0L))
            return ParentPinVerification(valid = true)
        }
        val attempts = entity.failedAttempts + 1
        val lockedUntil = if (attempts >= 3) now + TimeUnit.SECONDS.toMillis(30) else 0L
        parentAuthDao.upsert(
            entity.copy(
                failedAttempts = if (lockedUntil > 0L) 0 else attempts,
                lockedUntilMillis = lockedUntil
            )
        )
        return ParentPinVerification(valid = false, lockedUntilMillis = lockedUntil)
    }

    suspend fun clearParentPin() = parentAuthDao.delete()

    /** Сохраняет профиль. Пустой набор категорий чинится включением математики. */
    suspend fun saveProfile(profile: ChildProfileEntity): ChildProfileEntity {
        val stored = profile.withAtLeastOneCategory()
        profileDao.upsert(stored)
        return stored
    }

    /**
     * Пишет следующий запуск: сейчас + [ChildProfileEntity.launchIntervalMinutes], не меньше 5 минут.
     * [resetProgress] обнуляет [SessionState.tasksSolved] только когда серия закрыта
     * верными ответами или родительским PIN.
     */
    suspend fun scheduleNextLaunch(profile: ChildProfileEntity? = null, resetProgress: Boolean = true) {
        val currentProfile = profile ?: getProfileOrDefault()
        val intervalMinutes = currentProfile.launchIntervalMinutes.coerceAtLeast(5)
        val nextLaunchAt = System.currentTimeMillis() +
            TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
        sessionStore.setNextLaunchAt(nextLaunchAt)
        if (resetProgress) {
            sessionStore.setTasksSolved(0)
            sessionStore.setConsecutiveWrong(0)
        }
    }

    /**
     * После правки профиля двигает срок только если он ещё не задан
     * или родитель изменил интервал. Уже идущую серию не обнуляет.
     */
    suspend fun scheduleNextLaunchAfterProfileEdit(before: ChildProfileEntity, after: ChildProfileEntity) {
        val session = sessionStore.state.first()
        val intervalChanged = before.launchIntervalMinutes != after.launchIntervalMinutes
        if (session.nextLaunchAtMillis != 0L && !intervalChanged) return
        scheduleNextLaunch(after, resetProgress = false)
    }

    suspend fun getProfileOrDefault(): ChildProfileEntity =
        profileDao.getProfile() ?: ChildProfileEntity().also { profileDao.upsert(it) }

    /** true, пока записанный следующий запуск ещё не наступил. */
    suspend fun isSessionActive(): Boolean {
        val state = sessionStore.state.first()
        return state.nextLaunchAtMillis > System.currentTimeMillis()
    }

    /**
     * Нужно ли показать экран задачи после пробуждения / смены приложения.
     *
     * Экран не показывается, если блокировка выключена или [SessionState.nextLaunchAtMillis]
     * ещё в будущем.
     */
    suspend fun shouldPresentLock(): Boolean {
        val profile = getProfileOrDefault()
        if (!profile.lockEnabled) return false
        val session = sessionStore.state.first()
        return session.nextLaunchAtMillis == 0L || session.nextLaunchAtMillis <= System.currentTimeMillis()
    }

    /** Генерирует следующую задачу с учётом возраста, точности и слабых навыков. */
    suspend fun nextTask(): Task {
        val stored = getProfileOrDefault()
        val profile = if (stored.enabledCategories().isEmpty()) {
            saveProfile(stored.copy(mathEnabled = true))
        } else {
            stored
        }
        val session = sessionStore.state.first()
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val accuracy = statsDao.accuracySince(weekAgo) ?: session.lastAccuracyWindow
        val forceEasier = session.consecutiveWrong >= profile.maxWrongBeforeEasier
        val learnerStats = loadLearnerStats()
        val lastCategory = session.lastTaskCategory
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { TaskCategory.valueOf(it) }.getOrNull() }
        val task = taskEngine.nextTask(
            TaskRequest(
                ageYears = profile.ageYears,
                difficultyOffset = profile.difficultyOffset,
                enabledCategories = profile.enabledCategories(),
                categoryWeights = profile.categoryWeights(),
                recentAccuracy = accuracy,
                forceEasier = forceEasier,
                learnerStats = learnerStats,
                lastCategory = lastCategory
            )
        )
        sessionStore.setLastTaskCategory(task.category.name)
        return task
    }

    /**
     * Строит [LearnerStats] из последних попыток для AdaptiveSelector.
     * @param limit сколько последних учебных попыток учесть
     */
    suspend fun loadLearnerStats(limit: Int = 30): LearnerStats {
        val attempts = statsDao.recentLearningAttempts(limit)
        val records = attempts.mapNotNull { entity ->
            val skill = entity.skillTag.takeIf { it.isNotBlank() }
                ?.let { runCatching { SkillTag.valueOf(it) }.getOrNull() }
                ?: return@mapNotNull null
            val category = runCatching { TaskCategory.valueOf(entity.category) }.getOrNull()
                ?: skill.category
            AttemptRecord(
                skillTag = skill,
                category = category,
                correct = entity.correct
            )
        }
        return LearnerStats.fromAttempts(records)
    }

    /**
     * Верный ответ увеличивает [SessionState.tasksSolved] на 1.
     * Следующий запуск пишет вызывающий код, когда счётчик достигает tasksToUnlock.
     */
    suspend fun onCorrectAnswer(task: Task, usedHint: Boolean): Int {
        val solved = sessionStore.state.first().tasksSolved + 1
        sessionStore.setTasksSolved(solved)
        sessionStore.setConsecutiveWrong(0)
        insertAttempt(task, correct = true, usedHint = usedHint, usedParentUnlock = false)
        refreshAccuracy()
        return solved
    }

    /**
     * Ответ в свободном запуске с ярлыка.
     * Пишет статистику и не меняет [SessionState.tasksSolved] и срок блокировки.
     */
    suspend fun recordPracticeAttempt(task: Task, correct: Boolean, usedHint: Boolean) {
        insertAttempt(task, correct = correct, usedHint = usedHint, usedParentUnlock = false)
        refreshAccuracy()
    }

    /** Неверный ответ: увеличивает счётчик ошибок, сессию не открывает. */
    suspend fun onWrongAnswer(task: Task, usedHint: Boolean) {
        val session = sessionStore.state.first()
        sessionStore.setConsecutiveWrong(session.consecutiveWrong + 1)
        insertAttempt(task, correct = false, usedHint = usedHint, usedParentUnlock = false)
        refreshAccuracy()
    }

    /**
     * Emergency-разблокировка родителем.
     * При верном PIN до возврата записывает следующий запуск и обнуляет серию.
     * @return true, если PIN верен и срок следующего запуска записан
     */
    suspend fun unlockWithParentPin(pin: String): Boolean {
        if (!verifyParentPin(pin).valid) return false
        val profile = getProfileOrDefault()
        statsDao.insert(
            TaskAttemptEntity(
                timestampMillis = System.currentTimeMillis(),
                category = "PARENT",
                correct = true,
                usedHint = false,
                usedParentUnlock = true,
                difficultyLevel = 0,
                skillTag = ""
            )
        )
        scheduleNextLaunch(profile, resetProgress = true)
        return true
    }

    /** Обнуляет устаревший ключ `unlocked_until`. Срок запуска и tasksSolved не меняет. */
    suspend fun endSessionNow() {
        sessionStore.clearUnlock()
    }

    private suspend fun insertAttempt(
        task: Task,
        correct: Boolean,
        usedHint: Boolean,
        usedParentUnlock: Boolean
    ) {
        statsDao.insert(
            TaskAttemptEntity(
                timestampMillis = System.currentTimeMillis(),
                category = task.category.name,
                correct = correct,
                usedHint = usedHint,
                usedParentUnlock = usedParentUnlock,
                difficultyLevel = task.difficultyLevel,
                skillTag = task.skillTag.name
            )
        )
    }

    private suspend fun refreshAccuracy() {
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val accuracy = statsDao.accuracySince(weekAgo) ?: 0.7
        sessionStore.setAccuracy(accuracy)
    }

}
