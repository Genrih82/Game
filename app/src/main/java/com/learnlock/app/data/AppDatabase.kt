package com.learnlock.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Локальная Room-БД LearnLock.
 * Хранит профиль ребёнка, родительский PIN и историю попыток.
 *
 * Смена схемы идёт только по миграциям 2→3→4→5→6→7→8.
 * Неизвестная версия не пересоздаёт базу: профиль и PIN при этом сохраняются на диске,
 * а открытие завершается ошибкой Room.
 */
@Database(
    entities = [
        ChildProfileEntity::class,
        ParentAuthEntity::class,
        TaskAttemptEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun parentAuthDao(): ParentAuthDao
    abstract fun statsDao(): StatsDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE child_profile ADD COLUMN backgroundColor INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE child_profile ADD COLUMN launchIntervalMinutes INTEGER NOT NULL DEFAULT 5")
                database.execSQL("ALTER TABLE child_profile ADD COLUMN accessDurationMinutes INTEGER NOT NULL DEFAULT 5")
                database.execSQL("UPDATE child_profile SET launchIntervalMinutes = sessionMinutes, accessDurationMinutes = sessionMinutes")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE parent_auth ADD COLUMN failedAttempts INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE parent_auth ADD COLUMN lockedUntilMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE child_profile ADD COLUMN tasksToUnlock INTEGER NOT NULL DEFAULT 10")
            }
        }

        /** Убирает неиспользуемое окно часов и outsideWindowMode из профиля. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS child_profile_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        ageYears INTEGER NOT NULL,
                        difficultyOffset INTEGER NOT NULL,
                        sessionMinutes INTEGER NOT NULL,
                        launchIntervalMinutes INTEGER NOT NULL,
                        accessDurationMinutes INTEGER NOT NULL,
                        tasksToUnlock INTEGER NOT NULL,
                        backgroundColor INTEGER NOT NULL,
                        mathEnabled INTEGER NOT NULL,
                        readingEnabled INTEGER NOT NULL,
                        logicEnabled INTEGER NOT NULL,
                        mathWeight INTEGER NOT NULL,
                        readingWeight INTEGER NOT NULL,
                        logicWeight INTEGER NOT NULL,
                        maxWrongBeforeEasier INTEGER NOT NULL,
                        hintsEnabled INTEGER NOT NULL,
                        lockEnabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO child_profile_new (
                        id, displayName, ageYears, difficultyOffset, sessionMinutes,
                        launchIntervalMinutes, accessDurationMinutes, tasksToUnlock, backgroundColor,
                        mathEnabled, readingEnabled, logicEnabled, mathWeight, readingWeight, logicWeight,
                        maxWrongBeforeEasier, hintsEnabled, lockEnabled
                    )
                    SELECT
                        id, displayName, ageYears, difficultyOffset, sessionMinutes,
                        launchIntervalMinutes, accessDurationMinutes, tasksToUnlock, backgroundColor,
                        mathEnabled, readingEnabled, logicEnabled, mathWeight, readingWeight, logicWeight,
                        maxWrongBeforeEasier, hintsEnabled, lockEnabled
                    FROM child_profile
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE child_profile")
                database.execSQL("ALTER TABLE child_profile_new RENAME TO child_profile")
            }
        }

        /** Добавляет категорию «Английский» в профиль. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE child_profile ADD COLUMN englishEnabled INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "ALTER TABLE child_profile ADD COLUMN englishWeight INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /** Создаёт singleton-подобную БД `learnlock.db` в application context. */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "learnlock.db")
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .build()
    }
}
