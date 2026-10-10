package com.id.mimikopi.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface StatsDao {
    @Query("SELECT COUNT(*) FROM practice_sessions")
    suspend fun countSessions(): Long

    @Query("SELECT COUNT(*) FROM attempts")
    suspend fun countAttempts(): Long

    @Query("SELECT COUNT(*) FROM attempt_events")
    suspend fun countAttemptEvents(): Long

    @Query("SELECT COUNT(*) FROM item_stats")
    suspend fun countItemStats(): Long

    @Query("SELECT COUNT(*) FROM settings")
    suspend fun countSettings(): Long
}

@Database(
    entities = [
        PracticeSessionEntity::class,
        AttemptEntity::class,
        AttemptEventEntity::class,
        ItemStatEntity::class,
        SettingEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao

    companion object {
        const val VERSION = 1

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mimikopi.db")
                // No destructive migration fallback: user progress must never be silently wiped.
                .build()
    }
}
