package com.id.mimikopi.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One practice session (learning-system.md section 3). */
@Entity(tableName = "practice_sessions")
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAtMillis: Long,
    @ColumnInfo(name = "ended_at") val endedAtMillis: Long?,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "active_seconds") val activeSeconds: Long,
)

/** One attempt at an exercise (learning-system.md 2.2 `attempt`). */
@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = PracticeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long?,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    /** Generator parameters (incl. seed) as JSON, so the exact exercise can be regenerated. */
    @ColumnInfo(name = "params_json") val paramsJson: String,
    @ColumnInfo(name = "started_at") val startedAtMillis: Long,
    /** "R", "L" or "RL". */
    val hand: String?,
    @ColumnInfo(name = "tempo_bpm") val tempoBpm: Double?,
    @ColumnInfo(name = "note_accuracy") val noteAccuracy: Double?,
    @ColumnInfo(name = "timing_within_tol") val timingWithinTol: Double?,
    val stumbles: Int?,
    val clean: Boolean?,
    /** Times the "show note name" helper was used (learning-system.md 2.4). */
    @ColumnInfo(name = "helper_uses") val helperUses: Int = 0,
)

/** One expected/played note inside an attempt (learning-system.md 2.2 `events[]`). */
@Entity(
    tableName = "attempt_events",
    foreignKeys = [
        ForeignKey(
            entity = AttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attempt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("attempt_id")],
)
data class AttemptEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "attempt_id") val attemptId: Long,
    @ColumnInfo(name = "expected_pitch") val expectedPitch: Int?,
    @ColumnInfo(name = "actual_pitch") val actualPitch: Int?,
    @ColumnInfo(name = "expected_onset_ms") val expectedOnsetMs: Long?,
    @ColumnInfo(name = "actual_onset_ms") val actualOnsetMs: Long?,
    /** After latency compensation; negative = early. */
    @ColumnInfo(name = "offset_ms") val offsetMs: Long?,
    val velocity: Int?,
)

/** Spaced-repetition state of one item (learning-system.md 2.2 `item_stats`, 6.6). */
@Entity(tableName = "item_stats")
data class ItemStatEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: String,
    val box: Int,
    @ColumnInfo(name = "due_at") val dueAtMillis: Long,
    /** Recent outcomes, oldest first, as a string of '1' (pass) and '0' (fail). */
    @ColumnInfo(name = "last_results") val lastResults: String,
    @ColumnInfo(name = "median_response_ms") val medianResponseMs: Long?,
)

/** Simple key/value settings, kept in the database so a single JSON backup covers everything. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
