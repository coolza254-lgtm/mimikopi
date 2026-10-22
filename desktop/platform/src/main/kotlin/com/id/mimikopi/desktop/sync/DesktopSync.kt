package com.id.mimikopi.desktop.sync

import com.id.mimikopi.core.sync.GitHubContents
import com.id.mimikopi.core.sync.GitHubSync
import com.id.mimikopi.core.sync.GitHubSyncConfig
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.core.sync.SyncFailure
import com.id.mimikopi.core.sync.SyncPayload
import com.id.mimikopi.desktop.Services
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantLock

/** The last sync, for the settings page: when, and what it did or why it failed. */
data class SyncStatus(val running: Boolean = false, val line: String? = null, val failed: Boolean = false)

/**
 * The PC's side of the GitHub sync (learning-system.md 2.11), the same as the phone's: merges the phone's progress in
 * and puts this computer's up, through the private data repository. Runs when the program opens and closes, and from
 * Settings. A backup comes first whenever something is written; device settings and user scores never travel
 * (private songs do, since v0.33). The token is in `github-sync.json` in the data folder, outside the database, so never in an export.
 */
class DesktopSync(private val services: Services, private val configFile: File = File(services.dirs.root, "github-sync.json")) {
    private val lock = ReentrantLock()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun config(): GitHubSyncConfig = GitHubSyncConfig.fromJson(runCatching { configFile.readText() }.getOrNull())

    fun saveConfig(config: GitHubSyncConfig) {
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.copy(repo = config.repo.trim(), token = config.token.trim()).toJson())
    }

    private fun snapshot(): SyncPayload {
        val settings = ProgressMerge.progressRows(services.db.settings())
        return SyncPayload(deviceId = services.dirs.deviceId, deviceName = "คอม", appVersion = services.appVersion,
            settings = settings, itemStats = services.db.itemStats().sortedBy { it.itemId })
    }

    /**
     * One sync when set up (blocking: call it off the UI thread). [quiet] runs (opening or closing the program) leave
     * no failure on screen when there is no internet; a sync already running is not started twice.
     */
    fun sync(quiet: Boolean = false) {
        val config = config()
        if (!config.ready) {
            if (!quiet) _status.value = SyncStatus(line = "ยังไม่ได้ตั้งค่า ใส่ชื่อ repo และ token ก่อน", failed = true)
            return
        }
        if (!lock.tryLock()) return
        try {
            _status.value = _status.value.copy(running = true)
            var changed = false
            val report = GitHubSync.run(GitHubContents(config), services.dirs.deviceId, ::snapshot, { services.learning.leitner.intervalDays(it) }) { r ->
                services.transfer.autoBackup("before-sync")
                services.db.transaction {
                    services.db.putAll(r.settings)
                    services.db.putItemStats(r.itemStats)
                }
                changed = true
            }
            if (changed) services.reloadAll()
            _status.value = SyncStatus(line = "${now()} · ${report.text()}")
        } catch (e: SyncFailure) {
            _status.value = if (quiet) _status.value.copy(running = false) else SyncStatus(line = "${now()} · ${e.thai}", failed = true)
        } catch (e: Exception) {
            _status.value = if (quiet) _status.value.copy(running = false) else SyncStatus(line = "${now()} · ซิงค์ไม่สำเร็จ: ${e.message}", failed = true)
        } finally {
            lock.unlock()
        }
    }

    private fun now() = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
}
