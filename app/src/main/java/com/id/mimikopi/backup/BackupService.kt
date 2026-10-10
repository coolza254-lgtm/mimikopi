package com.id.mimikopi.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.id.mimikopi.BuildConfig
import com.id.mimikopi.core.backup.AttemptEventRow
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.backup.BackupCheck
import com.id.mimikopi.core.backup.BackupData
import com.id.mimikopi.core.backup.BackupFile
import com.id.mimikopi.core.backup.BackupValidator
import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.backup.SessionRow
import com.id.mimikopi.core.backup.SettingRow
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.data.db.AttemptEntity
import com.id.mimikopi.data.db.AttemptEventEntity
import com.id.mimikopi.data.db.ItemStatEntity
import com.id.mimikopi.data.db.PracticeSessionEntity
import com.id.mimikopi.data.db.SettingEntity
import java.io.File
import java.io.IOException
import java.time.Instant

/** Export/import of all user data as one JSON file (manual backup, and automatic before updates). */
class BackupService(private val context: Context, private val db: AppDatabase) {

    private val autoDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun snapshot(): BackupFile {
        val dao = db.backupDao()
        // Read inside one transaction so the snapshot is consistent.
        val data = db.withTransaction {
            BackupData(
                practiceSessions = dao.sessions().map { SessionRow(it.id, it.startedAtMillis, it.endedAtMillis, it.plannedMinutes, it.activeSeconds) },
                attempts = dao.attempts().map {
                    AttemptRow(it.id, it.sessionId, it.exerciseId, it.paramsJson, it.startedAtMillis, it.hand, it.tempoBpm,
                        it.noteAccuracy, it.timingWithinTol, it.stumbles, it.clean, it.helperUses)
                },
                attemptEvents = dao.attemptEvents().map {
                    AttemptEventRow(it.id, it.attemptId, it.expectedPitch, it.actualPitch, it.expectedOnsetMs, it.actualOnsetMs, it.offsetMs, it.velocity)
                },
                itemStats = dao.itemStats().map { ItemStatRow(it.itemId, it.box, it.dueAtMillis, it.lastResults, it.medianResponseMs) },
                settings = dao.settings().map { SettingRow(it.key, it.value) },
            )
        }
        return BackupFile(
            applicationId = BuildConfig.APPLICATION_ID,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            databaseVersion = AppDatabase.VERSION,
            createdAtUtc = Instant.now().toString(),
            data = data,
        )
    }

    /** Writes a backup to a user-chosen document. Returns the number of rows written. */
    suspend fun exportTo(uri: Uri): Int {
        val backup = snapshot()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("cannot open $uri")
        out.use { it.write(backup.toJson().toByteArray(Charsets.UTF_8)) }
        return backup.data.totalRows
    }

    /** Saves a backup inside app storage (kept across updates). Keeps the newest [KEEP_AUTO]. */
    suspend fun autoBackup(reason: String): File {
        val backup = snapshot()
        val stamp = backup.createdAtUtc.replace(":", "").replace(".", "")
        val file = File(autoDir, "auto-$stamp-$reason.json")
        val tmp = File(autoDir, file.name + ".part")
        tmp.writeText(backup.toJson())
        if (!tmp.renameTo(file)) throw IOException("cannot write ${file.name}")
        autoBackups().drop(KEEP_AUTO).forEach { it.delete() }
        return file
    }

    /** Automatic backups, newest first. */
    fun autoBackups(): List<File> =
        autoDir.listFiles { f -> f.name.startsWith("auto-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }.orEmpty()

    fun readDocument(uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open $uri")
        return input.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun validate(text: String): BackupCheck = BackupValidator.check(
        text,
        expectedApplicationIds = setOf(RELEASE_APP_ID, "$RELEASE_APP_ID.debug"),
        currentDatabaseVersion = AppDatabase.VERSION,
    )

    /**
     * Replaces all user data with [backup]. A safety copy of the current data is saved first, and the
     * replacement runs in one transaction: it either fully succeeds or leaves the database unchanged.
     */
    suspend fun restore(backup: BackupFile) {
        autoBackup("before-restore")
        val dao = db.backupDao()
        val d = backup.data
        db.withTransaction {
            dao.clearAttemptEvents()
            dao.clearAttempts()
            dao.clearSessions()
            dao.clearItemStats()
            dao.clearSettings()
            dao.insertSessions(d.practiceSessions.map { PracticeSessionEntity(it.id, it.startedAt, it.endedAt, it.plannedMinutes, it.activeSeconds) })
            dao.insertAttempts(d.attempts.map {
                AttemptEntity(it.id, it.sessionId, it.exerciseId, it.paramsJson, it.startedAt, it.hand, it.tempoBpm,
                    it.noteAccuracy, it.timingWithinTol, it.stumbles, it.clean, it.helperUses)
            })
            dao.insertAttemptEvents(d.attemptEvents.map {
                AttemptEventEntity(it.id, it.attemptId, it.expectedPitch, it.actualPitch, it.expectedOnsetMs, it.actualOnsetMs, it.offsetMs, it.velocity)
            })
            dao.insertItemStats(d.itemStats.map { ItemStatEntity(it.itemId, it.box, it.dueAt, it.lastResults, it.medianResponseMs) })
            dao.insertSettings(d.settings.map { SettingEntity(it.key, it.value) })
        }
    }

    companion object {
        const val RELEASE_APP_ID = "com.id.mimikopi"
        const val KEEP_AUTO = 5
    }
}
