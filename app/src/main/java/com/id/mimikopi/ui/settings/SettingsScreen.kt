package com.id.mimikopi.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.BuildConfig
import com.id.mimikopi.R
import com.id.mimikopi.core.update.UpdateManifest
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.update.InstallEvent
import com.id.mimikopi.update.InstallEvents
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val update by vm.update.collectAsStateWithLifecycle()
    val backup by vm.backup.collectAsStateWithLifecycle()
    val lastCheck by vm.lastCheck.collectAsStateWithLifecycle()
    val autoCount by vm.autoBackupCount.collectAsStateWithLifecycle()
    val installEvent by InstallEvents.events.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Export that may be followed by an update (set when the user chose "back up first").
    var updateAfterExport by remember { mutableStateOf<UpdateManifest?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val then = updateAfterExport
        updateAfterExport = null
        if (uri != null) vm.exportTo(uri, then) else if (then != null) vm.dismissUpdateMessage()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickedRestoreFile(uri)
    }
    val backupName = "mimikopi-backup-${LocalDate.now()}.json"

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    // The installer's confirmation must be launched from the foreground activity.
    LaunchedEffect(installEvent) {
        when (val e = installEvent) {
            is InstallEvent.NeedsConfirmation -> context.startActivity(e.confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            is InstallEvent.Failed -> vm.onInstallFailed(e.status, e.message)
            InstallEvent.Succeeded, null -> Unit
        }
        if (installEvent != null) InstallEvents.consume()
    }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        PageCard(title = stringResource(R.string.update_title)) {
            Body(stringResource(R.string.update_current, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            lastCheck?.let { Body(stringResource(R.string.update_last_check, it), muted = true) }
            UpdateSection(
                state = update,
                onCheck = vm::checkForUpdate,
                onStart = vm::startUpdate,
                onOpenPermission = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                    )
                },
                onDismiss = vm::dismissUpdateMessage,
            )
        }

        PageCard(title = stringResource(R.string.backup_title)) {
            Body(stringResource(R.string.backup_body))
            Body(stringResource(R.string.backup_auto_count, autoCount), muted = true)
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.padding(top = Tokens.Space.s)) {
                Button(onClick = { exportLauncher.launch(backupName) }, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.backup_import))
                }
                if (autoCount > 0) {
                    OutlinedButton(onClick = vm::restoreLatestAutoBackup, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
                        Text(stringResource(R.string.backup_restore_auto))
                    }
                }
            }
            BackupStatus(backup)
        }
    }

    // Dialogs
    when (val s = update) {
        is UpdateUi.AskBackup -> AlertDialog(
            onDismissRequest = vm::dismissUpdateMessage,
            title = { Text(stringResource(R.string.update_backup_q_title)) },
            text = { Text(stringResource(R.string.update_backup_q_body)) },
            confirmButton = {
                TextButton(onClick = {
                    updateAfterExport = s.manifest
                    exportLauncher.launch(backupName)
                }) { Text(stringResource(R.string.update_backup_q_save)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.downloadAndInstall(s.manifest) }) { Text(stringResource(R.string.update_backup_q_skip)) }
            },
        )
        else -> Unit
    }
    when (val b = backup) {
        is BackupUi.ConfirmRestore -> AlertDialog(
            onDismissRequest = vm::dismissBackupMessage,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                Text(stringResource(R.string.restore_confirm_body, b.backup.createdAtUtc, b.backup.appVersionName, b.backup.data.totalRows))
            },
            confirmButton = { TextButton(onClick = { vm.confirmRestore(b.backup) }) { Text(stringResource(R.string.restore_confirm_yes)) } },
            dismissButton = { TextButton(onClick = vm::dismissBackupMessage) { Text(stringResource(R.string.action_cancel)) } },
        )
        else -> Unit
    }
}

@Composable
private fun UpdateSection(
    state: UpdateUi,
    onCheck: () -> Unit,
    onStart: (UpdateManifest) -> Unit,
    onOpenPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.padding(top = Tokens.Space.s)) {
        when (state) {
            UpdateUi.Idle, is UpdateUi.UpToDate, is UpdateUi.Failed -> {
                if (state is UpdateUi.UpToDate) Body(stringResource(R.string.update_up_to_date, state.latestVersionName))
                if (state is UpdateUi.Failed) Body(failureText(state), error = true)
                Button(onClick = onCheck, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.update_check))
                }
            }
            UpdateUi.Checking -> {
                Body(stringResource(R.string.update_checking))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is UpdateUi.Available, is UpdateUi.AskBackup -> {
                val m = (state as? UpdateUi.Available)?.manifest ?: (state as UpdateUi.AskBackup).manifest
                Text(stringResource(R.string.update_available, m.versionName), style = MaterialTheme.typography.titleMedium)
                Body(m.notesTh)
                Body(stringResource(R.string.update_size, m.apkSize / 1_000_000.0), muted = true)
                Button(onClick = { onStart(m) }, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.update_install))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            }
            is UpdateUi.Downloading -> {
                Body(stringResource(R.string.update_downloading, (state.progress * 100).toInt()))
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateUi.NeedsInstallPermission -> {
                Body(stringResource(R.string.update_need_permission))
                Button(onClick = onOpenPermission, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.update_open_permission))
                }
            }
            is UpdateUi.Installing -> Body(stringResource(R.string.update_installing))
        }
    }
}

@Composable
private fun failureText(f: UpdateUi.Failed): String {
    val base = stringResource(
        when (f.reason) {
            Reason.NETWORK -> R.string.update_err_network
            Reason.BAD_MANIFEST -> R.string.update_err_manifest
            Reason.CHECKSUM -> R.string.update_err_checksum
            Reason.WRONG_APK -> R.string.update_err_wrong_apk
            Reason.INSTALL -> R.string.update_err_install
            Reason.DEBUG_BUILD -> R.string.update_err_debug
            Reason.INSTALL_CANCELLED -> R.string.update_err_cancelled
        },
    )
    return if (f.detail.isNullOrBlank() || f.reason == Reason.DEBUG_BUILD || f.reason == Reason.INSTALL_CANCELLED) base else "$base\n(${f.detail})"
}

@Composable
private fun BackupStatus(state: BackupUi) {
    when (state) {
        BackupUi.Idle, is BackupUi.ConfirmRestore -> Unit
        BackupUi.Working -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s))
        is BackupUi.Exported -> Body(stringResource(R.string.backup_exported, state.rows))
        is BackupUi.Restored -> Body(stringResource(R.string.backup_restored, state.rows))
        is BackupUi.Rejected -> Body(stringResource(R.string.backup_rejected, state.reason), error = true)
        is BackupUi.Error -> Body(stringResource(R.string.backup_error, state.detail), error = true)
    }
}

@Composable
private fun Body(text: String, muted: Boolean = false, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            error -> MaterialTheme.colorScheme.error
            muted -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.padding(top = Tokens.Space.xs),
    )
}
