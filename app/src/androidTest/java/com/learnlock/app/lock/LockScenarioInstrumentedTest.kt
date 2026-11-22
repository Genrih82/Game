package com.learnlock.app.lock

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.learnlock.app.data.AppDatabase
import com.learnlock.app.data.ChildProfileEntity
import com.learnlock.app.data.LearnLockRepository
import com.learnlock.app.data.LearnLockSession
import com.learnlock.app.data.SessionState
import com.learnlock.taskengine.AnswerInputType
import com.learnlock.taskengine.SkillTag
import com.learnlock.taskengine.Task
import com.learnlock.taskengine.TaskCategory
import com.learnlock.taskengine.TaskEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Инструментальный сценарий блокировки на устройстве/эмуляторе:
 * due → ответы → scheduleNextLaunch → shouldPresentLock=false.
 */
@RunWith(AndroidJUnit4::class)
class LockScenarioInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var session: InMemorySession
    private lateinit var repository: LearnLockRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        session = InMemorySession()
        repository = LearnLockRepository(
            profileDao = db.profileDao(),
            parentAuthDao = db.parentAuthDao(),
            statsDao = db.statsDao(),
            sessionStore = session,
            taskEngine = TaskEngine()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun lockPresentsWhenDueAndClearsAfterRequiredTasks() = runBlocking {
        val profile = ChildProfileEntity(
            lockEnabled = true,
            tasksToUnlock = 2,
            launchIntervalMinutes = 30,
            ageYears = 8
        )
        db.profileDao().upsert(profile)
        session.setNextLaunchAt(System.currentTimeMillis() - 1_000)

        assertTrue(repository.shouldPresentLock())

        val task = sampleTask()
        val afterFirst = repository.onCorrectAnswer(task, usedHint = false)
        assertEquals(1, afterFirst)
        assertTrue(repository.shouldPresentLock())

        val afterSecond = repository.onCorrectAnswer(task, usedHint = false)
        assertEquals(2, afterSecond)
        repository.scheduleNextLaunch(profile, resetProgress = true)

        val state = repository.observeSession().first()
        assertTrue(state.nextLaunchAtMillis > System.currentTimeMillis())
        assertEquals(0, state.tasksSolved)
        assertFalse(repository.shouldPresentLock())
    }

    @Test
    fun disabledLockNeverPresents() = runBlocking {
        db.profileDao().upsert(ChildProfileEntity(lockEnabled = false))
        session.setNextLaunchAt(0L)
        assertFalse(repository.shouldPresentLock())
    }

    @Test
    fun futureLaunchDoesNotPresent() = runBlocking {
        db.profileDao().upsert(ChildProfileEntity(lockEnabled = true))
        session.setNextLaunchAt(System.currentTimeMillis() + 60_000)
        assertFalse(repository.shouldPresentLock())
    }

    private fun sampleTask() = Task(
        id = "instr-t",
        category = TaskCategory.MATH,
        prompt = "2+2",
        inputType = AnswerInputType.NUMBER,
        acceptedAnswers = listOf("4"),
        difficultyLevel = 1,
        skillTag = SkillTag.MATH_ADD
    )
}

private class InMemorySession : LearnLockSession {
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
