package com.learnlock.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Доступ к единственной записи профиля ребёнка (id = 1). */
@Dao
interface ProfileDao {
    /** Наблюдение за профилем для UI (главный экран, настройки). */
    @Query("SELECT * FROM child_profile WHERE id = 1")
    fun observeProfile(): Flow<ChildProfileEntity?>

    /** Одноразовое чтение профиля (логика блокировки, генерация задач). */
    @Query("SELECT * FROM child_profile WHERE id = 1")
    suspend fun getProfile(): ChildProfileEntity?

    /** Вставка или полная замена профиля. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ChildProfileEntity)
}

/** Хранение хеша родительского PIN (одна запись). */
@Dao
interface ParentAuthDao {
    @Query("SELECT * FROM parent_auth WHERE id = 1")
    suspend fun get(): ParentAuthEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(auth: ParentAuthEntity)

    @Query("DELETE FROM parent_auth WHERE id = 1")
    suspend fun delete()
}

/** История попыток решения задач и emergency-разблокировок. */
@Dao
interface StatsDao {
    /** Добавить попытку (успех/ошибка/parent unlock). */
    @Insert
    suspend fun insert(attempt: TaskAttemptEntity)

    /**
     * Поток попыток с указанного момента (для экрана статистики).
     * @param fromMillis нижняя граница timestamp (включительно)
     */
    @Query(
        """
        SELECT * FROM task_attempt
        WHERE timestampMillis >= :fromMillis
        ORDER BY timestampMillis DESC
        """
    )
    fun observeSince(fromMillis: Long): Flow<List<TaskAttemptEntity>>

    /** Общие накопленные итоги правильных и неправильных ответов. */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN correct = 1 AND usedParentUnlock = 0 THEN 1 ELSE 0 END), 0) AS correct,
            COALESCE(SUM(CASE WHEN correct = 0 AND usedParentUnlock = 0 THEN 1 ELSE 0 END), 0) AS incorrect
        FROM task_attempt
        """
    )
    fun observeTotals(): Flow<AttemptTotals>

    /**
     * Средняя точность за период, без учёта parent-unlock.
     * @return null, если попыток ещё не было
     */
    @Query(
        """
        SELECT AVG(CASE WHEN correct = 1 THEN 1.0 ELSE 0.0 END) FROM task_attempt
        WHERE timestampMillis >= :fromMillis AND usedParentUnlock = 0
        """
    )
    suspend fun accuracySince(fromMillis: Long): Double?

    /**
     * Последние учебные попытки для адаптивного селектора навыков.
     * Parent-unlock исключается.
     */
    @Query(
        """
        SELECT * FROM task_attempt
        WHERE usedParentUnlock = 0
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    suspend fun recentLearningAttempts(limit: Int): List<TaskAttemptEntity>
}
