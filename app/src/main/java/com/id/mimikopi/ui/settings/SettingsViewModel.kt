package com.id.mimikopi.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.backup.BackupCheck
import com.id.mimikopi.core.backup.BackupFile
import com.id.mimikopi.core.update.UpdateCheckResult
import com.id.mimikopi.core.update.UpdateManifest
import com.id.mimikopi.update.UpdateException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val latestVersionName: String) : UpdateUi
    data class Available(val manifest: UpdateManifest) : UpdateUi
    /** Ask whether to also export a backup file before updating. */
    data class AskBackup(val manifest: UpdateManifest) : UpdateUi
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateUi
    data class NeedsInstallPermission(val manifest: UpdateManifest, val apk: File) : UpdateUi
    data class Installing(val manifest: UpdateManifest) : UpdateUi
    data class Failed(val reason: Reason, val detail: String?) : UpdateUi
}

enum class Reason { NETWORK, BAD_MANIFEST, CHECKSUM, WRONG_APK, INSTALL, DEBUG_BUILD, INSTALL_CANCELLED }

sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi
    data class Exported(val rows: Int) : BackupUi
    data class ConfirmRestore(val backup: BackupFile) : BackupUi
    data class Restored(val rows: Int) : BackupUi
    data class Rejected(val reason: String) : BackupUi
    data class Error(val detail: String) : BackupUi
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val updates = services.updateService
    private val backups = services.backupService

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private val _backup = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val backup: StateFlow<BackupUi> = _backup.asStateFlow()

    private val _lastCheck = MutableStateFlow<String?>(null)
    val lastCheck: StateFlow<String?> = _lastCheck.asStateFlow()

    private val _autoBackupCount = MutableStateFlow(0)
    val autoBackupCount: StateFlow<Int> = _autoBackupCount.asStateFlow()

    private var job: Job? = null

    val timingMode = services.learning.timingMode

    init {
        refreshInfo()
        viewModelScope.launch { services.learning.load() }
    }

    fun setTimingMode(mode: com.id.mimikopi.core.practice.TimingMode) {
        viewModelScope.launch { services.learning.setTimingMode(mode) }
    }

    /** Example tolerance (stage 1 at 60 bpm) for each mode, shown next to the choice. */
    fun exampleMs(mode: com.id.mimikopi.core.practice.TimingMode): Int =
        com.id.mimikopi.core.practice.TimingRules(services.learning.config.timing).toleranceMs(1, 60.0, mode)

    private fun refreshInfo() {
        viewModelScope.launch {
            _lastCheck.value = updates.lastCheck()
            _autoBackupCount.value = withContext(Dispatchers.IO) { backups.autoBackups().size }
        }
    }

    fun checkForUpdate() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _update.value = UpdateUi.Checking
            _update.value = try {
                when (val r = withContext(Dispatchers.IO) { updates.check() }) {
                    is UpdateCheckResult.Available -> UpdateUi.Available(r.manifest)
                    is UpdateCheckResult.UpToDate -> UpdateUi.UpToDate(r.latestVersionName)
                    is UpdateCheckResult.Invalid -> UpdateUi.Failed(Reason.BAD_MANIFEST, r.reason)
                }
            } catch (e: UpdateException) {
                failure(e)
            }
            refreshInfo()
        }
    }

    /** First step of "update now": a debug build cannot update; otherwise offer a backup file. */
    fun startUpdate(manifest: UpdateManifest) {
        _update.value = if (!updates.installSupported) UpdateUi.Failed(Reason.DEBUG_BUILD, null) else UpdateUi.AskBackup(manifest)
    }

    /** Continue after the backup question. An automatic internal backup is always made. */
    fun downloadAndInstall(manifest: UpdateManifest) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { backups.autoBackup("before-update-${manifest.tag}") }
                _update.value = UpdateUi.Downloading(manifest, 0f)
                val apk = withContext(Dispatchers.IO) {
                    updates.download(manifest) { p -> _update.value = UpdateUi.Downloading(manifest, p.coerceIn(0f, 1f)) }
                }
                installOrAskPermission(manifest, apk)
            } catch (e: UpdateException) {
                _update.value = failure(e)
            } catch (e: java.io.IOException) {
                _update.value = UpdateUi.Failed(Reason.NETWORK, e.message)
            }
            refreshInfo()
        }
    }

    /** Called when returning from the "Install unknown apps" settings screen. */
    fun onResume() {
        val state = _update.value
        if (state is UpdateUi.NeedsInstallPermission && updates.canInstall()) {
            installOrAskPermission(state.manifest, state.apk)
        }
    }

    private fun installOrAskPermission(manifest: UpdateManifest, apk: File) {
        if (!updates.canInstall()) {
            _update.value = UpdateUi.NeedsInstallPermission(manifest, apk)
            return
        }
        _update.value = UpdateUi.Installing(manifest)
        try {
            updates.install(apk)
        } catch (e: UpdateException) {
            _update.value = failure(e)
        }
    }

    fun onInstallFailed(status: Int, message: String?) {
        updates.clearDownloads()
        val cancelled = status == android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED
        _update.value = UpdateUi.Failed(if (cancelled) Reason.INSTALL_CANCELLED else Reason.INSTALL, message ?: "status $status")
    }

    fun dismissUpdateMessage() {
        val s = _update.value
        _update.value = if (s is UpdateUi.AskBackup) UpdateUi.Available(s.manifest) else UpdateUi.Idle
    }

    // --- Backup ---

    fun exportTo(uri: Uri, thenUpdate: UpdateManifest? = null) {
        viewModelScope.launch {
            _backup.value = BackupUi.Working
            _backup.value = try {
                BackupUi.Exported(withContext(Dispatchers.IO) { backups.exportTo(uri) })
            } catch (e: Exception) {
                BackupUi.Error(e.message ?: e.javaClass.simpleName)
            }
            if (thenUpdate != null && _backup.value is BackupUi.Exported) downloadAndInstall(thenUpdate)
            else if (thenUpdate != null) _update.value = UpdateUi.Available(thenUpdate)
        }
    }

    fun pickedRestoreFile(uri: Uri) {
        viewModelScope.launch {
            _backup.value = BackupUi.Working
            _backup.value = try {
                val text = withContext(Dispatchers.IO) { backups.readDocument(uri) }
                checkToUi(backups.validate(text))
            } catch (e: Exception) {
                BackupUi.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun restoreLatestAutoBackup() {
        viewModelScope.launch {
            _backup.value = BackupUi.Working
            _backup.value = try {
                val file = withContext(Dispatchers.IO) { backups.autoBackups().firstOrNull() }
                if (file == null) BackupUi.Rejected("no automatic backup") else checkToUi(backups.validate(withContext(Dispatchers.IO) { file.readText() }))
            } catch (e: Exception) {
                BackupUi.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun confirmRestore(backup: BackupFile) {
        viewModelScope.launch {
            _backup.value = BackupUi.Working
            _backup.value = try {
                withContext(Dispatchers.IO) { backups.restore(backup) }
                BackupUi.Restored(backup.data.totalRows)
            } catch (e: Exception) {
                BackupUi.Error(e.message ?: e.javaClass.simpleName)
            }
            refreshInfo()
        }
    }

    fun dismissBackupMessage() {
        _backup.value = BackupUi.Idle
    }

    private fun checkToUi(check: BackupCheck): BackupUi = when (check) {
        is BackupCheck.Ok -> BackupUi.ConfirmRestore(check.backup)
        is BackupCheck.Rejected -> BackupUi.Rejected(check.reason)
    }

    private fun failure(e: UpdateException) = UpdateUi.Failed(
        when (e.kind) {
            UpdateException.Kind.NETWORK -> Reason.NETWORK
            UpdateException.Kind.BAD_MANIFEST -> Reason.BAD_MANIFEST
            UpdateException.Kind.CHECKSUM -> Reason.CHECKSUM
            UpdateException.Kind.WRONG_APK -> Reason.WRONG_APK
            UpdateException.Kind.INSTALL -> Reason.INSTALL
        },
        e.message,
    )
}
