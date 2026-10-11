package com.id.mimikopi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Whole-table access used only by backup export/import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM practice_sessions ORDER BY id")
    suspend fun sessions(): List<PracticeSessionEntity>

    @Query("SELECT * FROM attempts ORDER BY id")
    suspend fun attempts(): List<AttemptEntity>

    @Query("SELECT * FROM attempt_events ORDER BY id")
    suspend fun attemptEvents(): List<AttemptEventEntity>

    @Query("SELECT * FROM item_stats ORDER BY item_id")
    suspend fun itemStats(): List<ItemStatEntity>

    @Query("SELECT * FROM settings ORDER BY `key`")
    suspend fun settings(): List<SettingEntity>

    // Children first so foreign keys never point at deleted rows.
    @Query("DELETE FROM attempt_events")
    suspend fun clearAttemptEvents()

    @Query("DELETE FROM attempts")
    suspend fun clearAttempts()

    @Query("DELETE FROM practice_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM item_stats")
    suspend fun clearItemStats()

    @Query("DELETE FROM settings")
    suspend fun clearSettings()

    @Insert
    suspend fun insertSessions(rows: List<PracticeSessionEntity>)

    @Insert
    suspend fun insertAttempts(rows: List<AttemptEntity>)

    @Insert
    suspend fun insertAttemptEvents(rows: List<AttemptEventEntity>)

    @Insert
    suspend fun insertItemStats(rows: List<ItemStatEntity>)

    @Insert
    suspend fun insertSettings(rows: List<SettingEntity>)
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM item_stats")
    suspend fun itemStats(): List<ItemStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putItemStat(item: ItemStatEntity)

    @Insert
    suspend fun insertAttempt(attempt: AttemptEntity): Long

    @Insert
    suspend fun insertEvents(events: List<AttemptEventEntity>)

    @Insert
    suspend fun insertSession(session: PracticeSessionEntity): Long

    @Query("UPDATE practice_sessions SET ended_at = :endedAt, active_seconds = :activeSeconds WHERE id = :id")
    suspend fun finishSession(id: Long, endedAt: Long, activeSeconds: Long)

    @Query("SELECT * FROM practice_sessions ORDER BY started_at")
    suspend fun sessions(): List<PracticeSessionEntity>

    @Query("SELECT * FROM attempts WHERE started_at >= :since ORDER BY started_at")
    suspend fun attemptsSince(since: Long): List<AttemptEntity>
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("SELECT * FROM settings WHERE `key` LIKE :prefix || '%'")
    suspend fun withPrefix(prefix: String): List<SettingEntity>

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}

/** Setting keys, in one place. */
object SettingKeys {
    const val LAST_UPDATE_CHECK = "update.lastCheck"
    const val PIANO_LOW_NOTE = "piano.lowNote"
    const val PIANO_KEY_WIDTH = "piano.whiteKeyDp"
    const val PIANO_VELOCITY = "piano.velocity"
    const val PIANO_GAIN = "piano.volume"
    const val PIANO_REVERB = "piano.reverb"
    const val PIANO_WARMTH = "piano.warmth"
    const val PIANO_TOUCH_VELOCITY = "piano.touchVelocity"
    const val NOTE_NAME_STYLE = "notes.nameStyle"
    const val HELPER_NOTE_NAMES_COUNT = "helper.noteNames.count"
    const val METRONOME_SETTINGS = "metronome.settings"
    const val METRONOME_SELF_TEST = "metronome.lastSelfTest"
    const val INPUT_CONFIG = "input.config"
    const val MIDI_SOUND_THROUGH = "midi.soundThrough"
    const val LESSON_RECORDS = "lesson.records"
    const val PIECE_PROGRESS = "piece.progress"
    const val READER_OPTIONS = "reader.options"
    const val BENCHMARKS = "benchmarks"
    /** User scores: one row per score, key "score:<id>", value = piece JSON (backups include them). */
    const val USER_SCORE_PREFIX = "score:"
    const val SESSION_MINUTES = "session.minutes"
    const val KEYBOARD_KEYS = "piano.keyCount"
}
