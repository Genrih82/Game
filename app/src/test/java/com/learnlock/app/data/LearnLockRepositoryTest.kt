package com.learnlock.app.data

import com.learnlock.taskengine.AnswerInputType
import com.learnlock.taskengine.SkillTag
import com.learnlock.taskengine.Task
import com.learnlock.taskengine.TaskCategory
import com.learnlock.taskengine.TaskEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LearnLockRepositoryTest {
    @Test
    fun dueLaunchStaysDueWhenIntervalIsUnchanged() = runBlocking {
        val past = System.currentTimeMillis() - 5_000
        val session = FakeSession().apply {
            setNextLaunchAt(past)
            setTasksSolved(3)
        }
        val profile = ChildProfileEntity(lockEnabled = true, launchIntervalMinutes = 30)
        val repository = repository(session, profile)

        repository.scheduleNextLaunchAfterProfileEdit(profile, profile.copy(displayName = "Аня"))

        val state = repository.observeSession().first()
        assertEquals(past, state.nextLaunchAtMillis)
        assertEquals(3, state.tasksSolved)
        assertTrue(repository.shouldPresentLock())
    }

    @Test
    fun unsetLaunchGetsATimeWithoutClearingProgress() = runBlocking {
        val session = FakeSession().apply { setTasksSolved(2) }
        val profile = ChildProfileEntity(lockEnabled = true, launchIntervalMinutes = 30)
        val repository = repository(session, profile)
        val before = System.currentTimeMillis()

        repository.scheduleNextLaunchAfterProfileEdit(profile, profile)

        val state = repository.observeSession().first()
        assertTrue(state.nextLaunchAtMillis >= before + TimeUnit.MINUTES.toMillis(30))
        assertEquals(2, state.tasksSolved)
    }

    @Test
    fun unchangedFutureLaunchAndProgressSurviveProfileEdit() = runBlocking {
        val future = System.currentTimeMillis() + 120_000
        val session = FakeSession().apply {
            setNextLaunchAt(future)
            setTasksSolved(4)
        }
        val profile = ChildProfileEntity(lockEnabled = true, launchIntervalMinutes = 30)
        val repository = repository(session, profile)

        repository.scheduleNextLaunchAfterProfileEdit(profile, profile.copy(backgroundColor = 2))

        val state = repository.observeSession().first()
        assertEquals(future, state.nextLaunchAtMillis)
        assertEquals(4, state.tasksSolved)
    }

    @Test
    fun changedIntervalMovesLaunchWithoutClearingProgress() = runBlocking {
        val future = System.currentTimeMillis() + 60_000
        val session = FakeSession().apply {
            setNextLaunchAt(future)
            setTasksSolved(6)
        }
        val before = ChildProfileEntity(lockEnabled = true, launchIntervalMinutes = 30)
        val after = before.copy(launchIntervalMinutes = 15)
        val repository = repository(session, before)
        val started = System.currentTimeMillis()

        repository.scheduleNextLaunchAfterProfileEdit(before, after)

        val state = repository.observeSession().first()
        assertTrue(state.nextLaunchAtMillis >= started + TimeUnit.MINUTES.toMillis(15))
        assertTrue(state.nextLaunchAtMillis > future)
        assertEquals(6, state.tasksSolved)
    }

    @Test
    fun completedSeriesWritesFutureLaunchAndClearsProgress() = runBlocking {
        val session = FakeSession()
        val profile = ChildProfileEntity(lockEnabled = true, tasksToUnlock = 2, launchIntervalMinutes = 20)
        val repository = repository(session, profile)
        val task = sampleTask()

        assertEquals(1, repository.onCorrectAnswer(task, usedHint = false))
        assertEquals(1, repository.observeSession().first().tasksSolved)
        assertEquals(2, repository.onCorrectAnswer(task, usedHint = false))
        val started = System.currentTimeMillis()
        repository.scheduleNextLaunch(profile, resetProgress = true)

        val state = repository.observeSession().first()
        assertTrue(state.nextLaunchAtMillis >= started + TimeUnit.MINUTES.toMillis(20))
        assertEquals(0, state.tasksSolved)
        assertEquals(0, state.consecutiveWrong)
    }

    @Test
    fun parentPinWritesNextLaunchBeforeReturning() = runBlocking {
        val session = FakeSession().apply {
            setNextLaunchAt(System.currentTimeMillis() - 1_000)
            setTasksSolved(4)
        }
        val profile = ChildProfileEntity(lockEnabled = true, launchIntervalMinutes = 12, tasksToUnlock = 10)
        val repository = repository(session, profile)
        repository.setParentPin("1234")
        val started = System.currentTimeMillis()

        assertTrue(repository.unlockWithParentPin("1234"))

        val state = repository.observeSession().first()
        assertTrue(state.nextLaunchAtMillis >= started + TimeUnit.MINUTES.toMillis(12))
        assertEquals(0, state.tasksSolved)
        assertFalse(repository.shouldPresentLock())
    }

    @Test
    fun threeWrongPinsLockTheFourthUntilTheDeadlinePasses() = runBlocking {
        val auth = FakeParentAuth()
        val repository = repository(FakeSession(), ChildProfileEntity(lockEnabled = true), auth)
        repository.setParentPin("1234")

        assertFalse(repository.verifyParentPin("0000").valid)
        assertFalse(repository.verifyParentPin("0000").valid)
        val third = repository.verifyParentPin("0000")
        assertFalse(third.valid)
        assertTrue(third.lockedUntilMillis > System.currentTimeMillis())

        val duringLock = repository.verifyParentPin("1234")
        assertFalse(duringLock.valid)
        assertEquals(third.lockedUntilMillis, duringLock.lockedUntilMillis)

        auth.expireLock()
        val afterDeadline = repository.verifyParentPin("1234")
        assertTrue(afterDeadline.valid)
        assertEquals(0L, afterDeadline.lockedUntilMillis)
    }

    @Test
    fun saveAndNextTaskRepairAnEmptyCategorySet() = runBlocking {
        val profiles = FakeProfiles()
        val repository = repository(FakeSession(), ChildProfileEntity(), profiles = profiles)
        val saved = repository.saveProfile(
            ChildProfileEntity(mathEnabled = false, readingEnabled = false, logicEnabled = false)
        )
        assertTrue(saved.mathEnabled)

        profiles.profile = saved.copy(mathEnabled = false, readingEnabled = false, logicEnabled = false)
        val task = repository.nextTask()
        assertEquals(TaskCategory.MATH, task.category)
        assertTrue(repository.getProfileOrDefault().mathEnabled)
    }

    private fun repository(
        session: FakeSession,
        profile: ChildProfileEntity,
        auth: FakeParentAuth = FakeParentAuth(),
        profiles: FakeProfiles = FakeProfiles().apply { this.profile = profile }
    ): LearnLockRepository = LearnLockRepository(
        profileDao = profiles,
        parentAuthDao = auth,
        statsDao = FakeStats(),
        sessionStore = session,
        taskEngine = TaskEngine()
    )
}

private fun sampleTask() = Task(
    id = "t",
    category = TaskCategory.MATH,
    prompt = "1+1",
    inputType = AnswerInputType.NUMBER,
    acceptedAnswers = listOf("2"),
    difficultyLevel = 1,
    skillTag = SkillTag.MATH_ADD
)

private class FakeSession : LearnLockSession {
    private val states = MutableStateFlow(SessionState())
    override val state: Flow<SessionState> = states

    override suspend fun setUnlockedUntil(millis: Long) = update { it.copy(unlockedUntilMillis = millis) }
    override suspend fun setNextLaunchAt(millis: Long) = update { it.copy(nextLaunchAtMillis = millis) }
    override suspend fun setTasksSolved(value: Int) = update { it.copy(tasksSolved = value) }
    override suspend fun setConsecutiveWrong(value: Int) = update { it.copy(consecutiveWrong = value) }
    override suspend fun setAccuracy(value: Double) = update { it.copy(lastAccuracyWindow = value) }
    override suspend fun clearUnlock() = update { it.copy(unlockedUntilMillis = 0L) }

    private fun update(block: (SessionState) -> SessionState) {
        states.value = block(states.value)
    }
}

private class FakeProfiles : ProfileDao {
    var profile: ChildProfileEntity? = null
    override fun observeProfile(): Flow<ChildProfileEntity?> = MutableStateFlow(profile)
    override suspend fun getProfile(): ChildProfileEntity? = profile
    override suspend fun upsert(profile: ChildProfileEntity) {
        this.profile = profile
    }
}

private class FakeParentAuth : ParentAuthDao {
    var entity: ParentAuthEntity? = null
    override suspend fun get(): ParentAuthEntity? = entity
    override suspend fun upsert(auth: ParentAuthEntity) {
        entity = auth
    }
    override suspend fun delete() {
        entity = null
    }

    fun expireLock() {
        entity = entity?.copy(lockedUntilMillis = System.currentTimeMillis() - 1)
    }
}

private class FakeStats : StatsDao {
    val attempts = mutableListOf<TaskAttemptEntity>()
    override suspend fun insert(attempt: TaskAttemptEntity) {
        attempts += attempt
    }
    override fun observeSince(fromMillis: Long): Flow<List<TaskAttemptEntity>> =
        MutableStateFlow(attempts.filter { it.timestampMillis >= fromMillis })
    override suspend fun accuracySince(fromMillis: Long): Double? = null
    override suspend fun recentLearningAttempts(limit: Int): List<TaskAttemptEntity> =
        attempts.filter { !it.usedParentUnlock }.take(limit)
}
