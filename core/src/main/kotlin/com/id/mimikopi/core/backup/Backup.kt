package com.id.mimikopi.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Full JSON backup of user data (PROMPT.md section 2). Pure data mirror of the Room tables so the
 * format is testable on the JVM and stays stable if storage changes. Bump [FORMAT] on breaking
 * changes and keep reading older formats.
 */
@Serializable
data class BackupFile(
    val format: Int = FORMAT,
    val applicationId: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseVersion: Int,
    val createdAtUtc: String,
    val data: BackupData,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = 1
        private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

        fun fromJson(text: String): BackupFile = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class BackupData(
    val practiceSessions: List<SessionRow> = emptyList(),
    val attempts: List<AttemptRow> = emptyList(),
    val attemptEvents: List<AttemptEventRow> = emptyList(),
    val itemStats: List<ItemStatRow> = emptyList(),
    val settings: List<SettingRow> = emptyList(),
) {
    val totalRows: Int
        get() = practiceSessions.size + attempts.size + attemptEvents.size + itemStats.size + settings.size
}

@Serializable
data class SessionRow(val id: Long, val startedAt: Long, val endedAt: Long?, val plannedMinutes: Int, val activeSeconds: Long)

@Serializable
data class AttemptRow(
    val id: Long,
    val sessionId: Long?,
    val exerciseId: String,
    val paramsJson: String,
    val startedAt: Long,
    val hand: String?,
    val tempoBpm: Double?,
    val noteAccuracy: Double?,
    val timingWithinTol: Double?,
    val stumbles: Int?,
    val clean: Boolean?,
    val helperUses: Int,
)

@Serializable
data class AttemptEventRow(
    val id: Long,
    val attemptId: Long,
    val expectedPitch: Int?,
    val actualPitch: Int?,
    val expectedOnsetMs: Long?,
    val actualOnsetMs: Long?,
    val offsetMs: Long?,
    val velocity: Int?,
)

@Serializable
data class ItemStatRow(val itemId: String, val box: Int, val dueAt: Long, val lastResults: String, val medianResponseMs: Long?)

@Serializable
data class SettingRow(val key: String, val value: String)

sealed interface BackupCheck {
    data class Ok(val backup: BackupFile) : BackupCheck
    data class Rejected(val reason: String) : BackupCheck
}

/** Validates a backup before anything in the database is touched. */
object BackupValidator {
    fun check(text: String, expectedApplicationIds: Set<String>, currentDatabaseVersion: Int): BackupCheck {
        val backup = runCatching { BackupFile.fromJson(text) }.getOrElse {
            return BackupCheck.Rejected("not a ミミコピ backup file (${it.message?.take(120)})")
        }
        if (backup.format > BackupFile.FORMAT) return BackupCheck.Rejected("backup format ${backup.format} is newer than this app")
        if (backup.applicationId !in expectedApplicationIds) return BackupCheck.Rejected("backup is from ${backup.applicationId}")
        if (backup.databaseVersion > currentDatabaseVersion) {
            return BackupCheck.Rejected("backup database v${backup.databaseVersion} is newer than this app (v$currentDatabaseVersion)")
        }
        val d = backup.data
        val sessionIds = d.practiceSessions.map { it.id }.toSet()
        val attemptIds = d.attempts.map { it.id }.toSet()
        if (sessionIds.size != d.practiceSessions.size || attemptIds.size != d.attempts.size) {
            return BackupCheck.Rejected("duplicate ids")
        }
        if (d.attempts.any { it.sessionId != null && it.sessionId !in sessionIds }) return BackupCheck.Rejected("attempt points to a missing session")
        if (d.attemptEvents.any { it.attemptId !in attemptIds }) return BackupCheck.Rejected("event points to a missing attempt")
        if (d.itemStats.map { it.itemId }.toSet().size != d.itemStats.size) return BackupCheck.Rejected("duplicate item ids")
        if (d.settings.map { it.key }.toSet().size != d.settings.size) return BackupCheck.Rejected("duplicate setting keys")
        return BackupCheck.Ok(backup)
    }
}
