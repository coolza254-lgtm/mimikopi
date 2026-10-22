package com.id.mimikopi.sync

import android.os.Build
import androidx.room.withTransaction
import com.id.mimikopi.BuildConfig
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.sync.GitHubContents
import com.id.mimikopi.core.sync.GitHubSync
import com.id.mimikopi.core.sync.GitHubSyncConfig
import com.id.mimikopi.core.sync.MergeResult
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.core.sync.SyncFailure
import com.id.mimikopi.core.sync.SyncPayload
import com.id.mimikopi.data.db.ItemStatEntity
import com.id.mimikopi.data.db.SettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** The last sync, for the settings page: when, and what it did or why it failed. */
data class SyncStatus(val running: Boolean = false, val line: String? = null, val failed: Boolean = false)

/**
 * The phone's side of the GitHub sync (learning-system.md 2.11): merges the PC's progress into the phone's and puts
 * the phone's up for the PC, through the private data repository ([GitHubSync]). Runs when the app opens and when it
 * goes to the background, and from Settings. Before anything is written here a backup is made; device settings
 * and user scores never travel (private songs do, since v0.33). The token lives in its own file in the no-backup folder, never in the
 * database, so it is never in a backup file or in the synced data.
 */
class SyncService(private val app: MimikopiApp) {
    private val mutex = Mutex()
    private val configFile get() = File(app.noBackupFilesDir, "github-sync.json")
    private var lastRunAt = 0L

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** This phone's sync id, in its own file (not in the database, so a backup restore never copies another device's id). */
    val deviceId: String by lazy {
        val f = File(app.filesDir, "device-id")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also { f.writeText(it) }
    }

    fun config(): GitHubSyncConfig = GitHubSyncConfig.fromJson(runCatching { configFile.readText() }.getOrNull())

    fun saveConfig(config: GitHubSyncConfig) {
        configFile.writeText(config.copy(repo = config.repo.trim(), token = config.token.trim()).toJson())
    }

    private suspend fun snapshot(): SyncPayload {
        val dao = app.database.settingsDao()
        val settings = ProgressMerge.KEYS.mapNotNull { k -> dao.get(k)?.let { k to it } }.toMap()
        val items = app.database.learningDao().itemStats().map { ItemStatRow(it.itemId, it.box, it.dueAtMillis, it.lastResults, it.medianResponseMs) }
        return SyncPayload(deviceId = deviceId, deviceName = "มือถือ ${Build.MODEL}", appVersion = BuildConfig.VERSION_NAME, settings = settings, itemStats = items.sortedBy { it.itemId })
    }

    private suspend fun apply(r: MergeResult) {
        app.backupService.autoBackup("before-sync")
        app.database.withTransaction {
            val dao = app.database.settingsDao()
            r.settings.forEach { (k, v) -> dao.put(SettingEntity(k, v)) }
            val learning = app.database.learningDao()
            r.itemStats.forEach { learning.putItemStat(ItemStatEntity(it.itemId, it.box, it.dueAt, it.lastResults, it.medianResponseMs)) }
        }
    }

    /**
     * One sync when set up. [quiet] runs (opening or leaving the app) leave no failure on screen when there is no
     * internet; [throttle] skips a run within [QUIET_GAP_MS] of the last (coming back to the app often); a sync
     * already running is not started twice.
     */
    suspend fun sync(quiet: Boolean = false, throttle: Boolean = false) {
        val config = config()
        if (!config.ready) {
            if (!quiet) _status.value = SyncStatus(line = "ยังไม่ได้ตั้งค่า ใส่ชื่อ repo และ token ก่อน", failed = true)
            return
        }
        if (throttle && System.currentTimeMillis() - lastRunAt < QUIET_GAP_MS) return
        if (!mutex.tryLock()) return
        try {
            lastRunAt = System.currentTimeMillis()
            _status.value = _status.value.copy(running = true)
            var changed = false
            val report = withContext(Dispatchers.IO) {
                GitHubSync.run(GitHubContents(config), deviceId, { runBlocking { snapshot() } }, app.learning.leitner::intervalDays) { r ->
                    runBlocking { apply(r) }
                    changed = true
                }
            }
            if (changed) {
                app.learning.reload()
                app.pieces.reload()
                app.progress.reload()
                runCatching { app.privateSongs.reload() }
            }
            _status.value = SyncStatus(line = "${now()} · ${report.text()}")
        } catch (e: SyncFailure) {
            _status.value = if (quiet) _status.value.copy(running = false) else SyncStatus(line = "${now()} · ${e.thai}", failed = true)
        } catch (e: Exception) {
            _status.value = if (quiet) _status.value.copy(running = false) else SyncStatus(line = "${now()} · ซิงค์ไม่สำเร็จ: ${e.message}", failed = true)
        } finally {
            mutex.unlock()
        }
    }

    private fun now() = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    companion object {
        const val QUIET_GAP_MS = 2 * 60 * 1000L
    }
}
