package com.id.mimikopi.ui.settings

import androidx.compose.foundation.layout.height
import com.id.mimikopi.ui.components.midiConnected
import com.id.mimikopi.ui.components.Segmented
import com.id.mimikopi.ui.components.SecondaryButton
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.Group
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
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
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenMidi: () -> Unit,
    onOpenMetronome: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val update by vm.update.collectAsStateWithLifecycle()
    val backup by vm.backup.collectAsStateWithLifecycle()
    val lastCheck by vm.lastCheck.collectAsStateWithLifecycle()
    val autoCount by vm.autoBackupCount.collectAsStateWithLifecycle()
    val timingMode by vm.timingMode.collectAsStateWithLifecycle()
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
        val midi = midiConnected()
        Section("การเชื่อมต่อและเสียง") {
            Group {
                GroupRow(
                    stringResource(R.string.home_midi_title),
                    subtitle = if (midi) "เชื่อมต่อแล้ว" else "ยังไม่ได้ต่อ · สายหรือบลูทูธ",
                    onClick = onOpenMidi,
                    leading = { Box(Modifier.size(10.dp).background(if (midi) Tokens.Palette.Positive else Tokens.Palette.Dim, CircleShape)) },
                    trailing = { Chevron() },
                )
                GroupDivider()
                GroupRow(stringResource(R.string.home_metronome_title), subtitle = "ความเร็ว จังหวะ เสียงเคาะ", onClick = onOpenMetronome, trailing = { Chevron() })
                GroupDivider()
                GroupRow(stringResource(R.string.home_diagnostics_title), subtitle = "ตรวจเสียง MIDI และความหน่วง", onClick = onOpenDiagnostics, trailing = { Chevron() })
            }
        }

        Section("การแสดงผล") {
            PageCard {
                val app = context.applicationContext as com.id.mimikopi.MimikopiApp
                val mode by app.themeMode.collectAsStateWithLifecycle()
                Label(stringResource(R.string.theme_title))
                Segmented(
                    listOf(com.id.mimikopi.ui.theme.ThemeMode.LIGHT, com.id.mimikopi.ui.theme.ThemeMode.DARK, com.id.mimikopi.ui.theme.ThemeMode.SYSTEM),
                    mode,
                    { m -> when (m) {
                        com.id.mimikopi.ui.theme.ThemeMode.LIGHT -> "สว่าง"
                        com.id.mimikopi.ui.theme.ThemeMode.DARK -> "มืด"
                        else -> "ตามเครื่อง"
                    } },
                    { app.setThemeMode(it) },
                )
            }
        }

        SyncButton()

        Section("ข้อมูลและอัปเดต") {
            PageCard {
                Label(stringResource(R.string.backup_title))
                Body(stringResource(R.string.backup_body), muted = true)
                Body(stringResource(R.string.backup_auto_count, autoCount), muted = true)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                    PrimaryButton(stringResource(R.string.backup_export), onClick = { exportLauncher.launch(backupName) })
                    SecondaryButton(stringResource(R.string.backup_import), onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth())
                    if (autoCount > 0) SecondaryButton(stringResource(R.string.backup_restore_auto), onClick = vm::restoreLatestAutoBackup, modifier = Modifier.fillMaxWidth())
                }
                BackupStatus(backup)

                Box(Modifier.padding(vertical = 20.dp).fillMaxWidth().height(1.dp).background(Tokens.Palette.Hair))
                Label(stringResource(R.string.update_title))
                Body(stringResource(R.string.update_current, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), muted = true)
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
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
            FooterLink("เกี่ยวกับและเครดิต", onOpenAbout)
            FooterLink("ข้อมูลวินิจฉัย", onOpenDiagnostics)
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
                SecondaryButton(stringResource(R.string.update_check), onClick = onCheck, modifier = Modifier.fillMaxWidth())
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
                PrimaryButton(stringResource(R.string.update_install), onClick = { onStart(m) })
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            }
            is UpdateUi.Downloading -> {
                Body(stringResource(R.string.update_downloading, (state.progress * 100).toInt()))
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateUi.NeedsInstallPermission -> {
                Body(stringResource(R.string.update_need_permission))
                PrimaryButton(stringResource(R.string.update_open_permission), onClick = onOpenPermission)
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

/** A section of settings: a small grey heading over its card. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        content()
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = modifier.padding(bottom = 10.dp))
}

@Composable
private fun Chevron() {
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Tokens.Palette.Dim, modifier = Modifier.size(16.dp))
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 14.sp, color = Tokens.Palette.InkMuted, textDecoration = TextDecoration.Underline,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp))
}

/**
 * Sync with the PC (learning-system.md 2.11): one button (the user asked for nothing else). The repository and branch are
 * built in; the first press asks once for the GitHub token, which can't ship inside a public app, and keeps it on this
 * phone only. Sync also runs by itself when the app opens and closes.
 */
@Composable
private fun SyncButton() {
    val app = LocalContext.current.applicationContext as com.id.mimikopi.MimikopiApp
    val status by app.sync.status.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    Column(Modifier.padding(top = 20.dp)) {
        PrimaryButton(
            if (status.running) "กำลังซิงค์…" else "Sync",
            onClick = { if (app.sync.config().ready) scope.launch { app.sync.sync() } else asking = true },
            enabled = !status.running,
        )
        status.line?.let { Body(it, muted = !status.failed, error = status.failed) }
    }
    if (asking) androidx.compose.material3.AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text("เชื่อมต่อครั้งแรก") },
        text = {
            Column {
                Text("ใส่ GitHub token ครั้งเดียว เก็บในเครื่องนี้เท่านั้น")
                androidx.compose.material3.OutlinedTextField(token, { token = it.trim().take(200) }, singleLine = true, label = { Text("token") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                app.sync.saveConfig(com.id.mimikopi.core.sync.GitHubSyncConfig(token = token))
                asking = false
                scope.launch { app.sync.sync() }
            }, enabled = token.isNotBlank()) { Text("Sync") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text("ยกเลิก") } },
    )
}

