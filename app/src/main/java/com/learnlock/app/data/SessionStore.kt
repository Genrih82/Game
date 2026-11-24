package com.learnlock.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore("session")

/**
 * Операции сессии, от которых зависит показ задания и срок следующего запуска.
 * DataStore-реализация — [SessionStore]. В тестах подставляется другая реализация.
 */
interface LearnLockSession {
    val state: Flow<SessionState>
    suspend fun setUnlockedUntil(millis: Long)
    suspend fun setNextLaunchAt(millis: Long)
    suspend fun setTasksSolved(value: Int)
    suspend fun setConsecutiveWrong(value: Int)
    suspend fun setAccuracy(value: Double)
    suspend fun setLastTaskCategory(value: String)
    suspend fun clearUnlock()
}

/**
 * Хранилище текущей сессии (DataStore Preferences).
 * Отдельно от Room, чтобы быстро читать/писать без миграций схемы.
 * Паузу до следующего задания хранит [SessionState.nextLaunchAtMillis].
 * Ключ `unlocked_until` остаётся в файле и показом не управляет.
 */
class SessionStore(private val context: Context) : LearnLockSession {
    private val unlockedUntil = longPreferencesKey("unlocked_until")
    private val nextLaunchAt = longPreferencesKey("next_launch_at")
    private val tasksSolved = intPreferencesKey("tasks_solved")
    private val consecutiveWrong = intPreferencesKey("consecutive_wrong")
    private val accuracy = doublePreferencesKey("accuracy")
    private val lastTaskCategory = stringPreferencesKey("last_task_category")

    /** Поток состояния сессии для UI и проверок блокировки. */
    override val state: Flow<SessionState> = context.sessionDataStore.data.map { prefs ->
        SessionState(
            unlockedUntilMillis = prefs[unlockedUntil] ?: 0L,
            nextLaunchAtMillis = prefs[nextLaunchAt] ?: 0L,
            tasksSolved = prefs[tasksSolved] ?: 0,
            consecutiveWrong = prefs[consecutiveWrong] ?: 0,
            lastAccuracyWindow = prefs[accuracy] ?: 0.7,
            lastTaskCategory = prefs[lastTaskCategory].orEmpty()
        )
    }

    /** Пишет устаревший ключ `unlocked_until`. Показ задания его не читает. */
    override suspend fun setUnlockedUntil(millis: Long) {
        context.sessionDataStore.edit { it[unlockedUntil] = millis }
    }

    override suspend fun setNextLaunchAt(millis: Long) {
        context.sessionDataStore.edit { it[nextLaunchAt] = millis }
    }

    override suspend fun setTasksSolved(value: Int) {
        context.sessionDataStore.edit { it[tasksSolved] = value }
    }

    /** Обновляет счётчик подряд идущих ошибок. */
    override suspend fun setConsecutiveWrong(value: Int) {
        context.sessionDataStore.edit { it[consecutiveWrong] = value }
    }

    /** Кэширует accuracy для генератора задач. */
    override suspend fun setAccuracy(value: Double) {
        context.sessionDataStore.edit { it[accuracy] = value }
    }

    /** Запоминает категорию последней выданной задачи для чередования. */
    override suspend fun setLastTaskCategory(value: String) {
        context.sessionDataStore.edit { it[lastTaskCategory] = value }
    }

    /** Обнуляет только устаревший `unlocked_until`, не срок следующего запуска. */
    override suspend fun clearUnlock() {
        context.sessionDataStore.edit { it[unlockedUntil] = 0L }
    }
}
