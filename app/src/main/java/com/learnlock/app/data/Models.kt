package com.learnlock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.learnlock.taskengine.TaskCategory

/**
 * Профиль ребёнка и настройки LearnLock (одна строка в БД).
 *
 * Паузу после нужного числа верных ответов задаёт [launchIntervalMinutes]
 * и записанный в сессии nextLaunchAtMillis.
 * [sessionMinutes] и [accessDurationMinutes] хранятся и запуск не считают.
 */
@Entity(tableName = "child_profile")
data class ChildProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "Ребёнок",
    val ageYears: Int = 8,
    /** Смещение сложности: −1 легче, 0 норма, +1 сложнее. */
    val difficultyOffset: Int = 0,
    /** Старое поле длительности. Запуском и показом не управляет. */
    val sessionMinutes: Int = 30,
    /** Минуты до следующего задания после набранной серии или родительского PIN. */
    val launchIntervalMinutes: Int = 30,
    /** Старое поле длительности доступа. Запуском и показом не управляет. */
    val accessDurationMinutes: Int = 5,
    val tasksToUnlock: Int = 10,
    /** Вариант фона главного и экранов настроек. */
    val backgroundColor: Int = 0,
    val mathEnabled: Boolean = true,
    val readingEnabled: Boolean = true,
    val logicEnabled: Boolean = true,
    val englishEnabled: Boolean = true,
    val mathWeight: Int = 1,
    val readingWeight: Int = 1,
    val logicWeight: Int = 1,
    val englishWeight: Int = 1,
    /** После стольких ошибок подряд генератор упрощает задачу. */
    val maxWrongBeforeEasier: Int = 3,
    val hintsEnabled: Boolean = true,
    /** Главный рубильник: слушать сон/пробуждение и показывать задачи. */
    val lockEnabled: Boolean = false
)

/** Соль + PBKDF2-хеш родительского PIN. */
@Entity(tableName = "parent_auth")
data class ParentAuthEntity(
    @PrimaryKey val id: Int = 1,
    val pinHash: String,
    val salt: String,
    val failedAttempts: Int = 0,
    val lockedUntilMillis: Long = 0L
)

data class ParentPinVerification(
    val valid: Boolean,
    val lockedUntilMillis: Long = 0L
)

/** Одна попытка решения (или parent unlock) для статистики и адаптации. */
@Entity(tableName = "task_attempt")
data class TaskAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val category: String,
    val correct: Boolean,
    val usedHint: Boolean,
    val usedParentUnlock: Boolean,
    val difficultyLevel: Int,
    val skillTag: String = ""
)

/** Общие итоги учебных ответов без учёта разблокировок родительским PIN. */
data class AttemptTotals(
    val correct: Int = 0,
    val incorrect: Int = 0
)

/**
 * Эфемерное состояние сессии (DataStore, не Room).
 * @property unlockedUntilMillis устаревший ключ DataStore, показ его не читает
 * @property nextLaunchAtMillis момент следующего задания; пока он в будущем, экран не показывается
 * @property consecutiveWrong подряд ошибок (для упрощения)
 * @property lastAccuracyWindow кэш точности за неделю
 */
data class SessionState(
    val unlockedUntilMillis: Long = 0L,
    val nextLaunchAtMillis: Long = 0L,
    val tasksSolved: Int = 0,
    val consecutiveWrong: Int = 0,
    val lastAccuracyWindow: Double = 0.7,
    /** Последняя выданная категория задач для чередования; пусто — ещё не было. */
    val lastTaskCategory: String = ""
)

/** Не даёт сохранить профиль, в котором выключены все категории задач. */
fun ChildProfileEntity.withAtLeastOneCategory(): ChildProfileEntity =
    if (mathEnabled || readingEnabled || logicEnabled || englishEnabled) this
    else copy(mathEnabled = true)

/** Включённые категории задач по флагам профиля. */
fun ChildProfileEntity.enabledCategories(): Set<TaskCategory> = buildSet {
    if (mathEnabled) add(TaskCategory.MATH)
    if (readingEnabled) add(TaskCategory.READING)
    if (logicEnabled) add(TaskCategory.LOGIC)
    if (englishEnabled) add(TaskCategory.ENGLISH)
}

/** Веса категорий для взвешенного выбора провайдера. */
fun ChildProfileEntity.categoryWeights(): Map<TaskCategory, Int> = mapOf(
    TaskCategory.MATH to mathWeight,
    TaskCategory.READING to readingWeight,
    TaskCategory.LOGIC to logicWeight,
    TaskCategory.ENGLISH to englishWeight
)
