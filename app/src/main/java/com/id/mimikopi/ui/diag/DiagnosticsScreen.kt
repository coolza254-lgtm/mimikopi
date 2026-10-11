package com.id.mimikopi.ui.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.diag.DiagnosticsReport
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, vm: DiagnosticsViewModel = viewModel()) {
    val report by vm.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.diag_copied)

    ScreenScaffold(title = stringResource(R.string.diag_title), onBack = onBack, snackbarHostState = snackbar) {
        Text(stringResource(R.string.diag_intro), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    report?.let { copyToClipboard(context, it.toJson()) }
                    scope.launch { snackbar.showSnackbar(copiedText) }
                },
                enabled = report != null,
                modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch),
            ) { Text(stringResource(R.string.diag_copy)) }
            OutlinedButton(
                onClick = { report?.let { share(context, it.toJson()) } },
                enabled = report != null,
                modifier = Modifier.heightIn(min = Tokens.minTouch),
            ) { Text(stringResource(R.string.diag_share)) }
        }
        OutlinedButton(onClick = vm::refresh, modifier = Modifier.heightIn(min = Tokens.minTouch)) {
            Text(stringResource(R.string.diag_refresh))
        }
        val r = report
        if (r == null) {
            Text(stringResource(R.string.diag_loading))
        } else {
            ReportSections(r)
        }
    }
}

@Composable
private fun ReportSections(r: DiagnosticsReport) {
    val yes = stringResource(R.string.diag_yes)
    val no = stringResource(R.string.diag_no)
    val unknown = stringResource(R.string.diag_unknown)

    PageCard(title = stringResource(R.string.diag_section_device)) {
        Line("${r.device.manufacturer} ${r.device.model}")
        Line("Android ${r.device.androidRelease} (API ${r.device.sdkInt})")
        Line(r.device.supportedAbis.joinToString())
    }
    PageCard(title = stringResource(R.string.diag_section_features)) {
        r.features.forEach { (name, has) -> Line("${name.substringAfterLast('.')}: ${if (has) yes else no}") }
    }
    PageCard(title = stringResource(R.string.diag_section_audio)) {
        Line("${stringResource(R.string.diag_sample_rate)}: ${r.audio.outputSampleRate?.let { "$it Hz" } ?: unknown}")
        Line("${stringResource(R.string.diag_frames_hint)}: ${r.audio.framesPerBufferHint ?: unknown}")
        Line("${stringResource(R.string.diag_route)}: ${r.audio.currentRoute.joinToString().ifEmpty { unknown }}")
        Line("${stringResource(R.string.diag_outputs)}: ${r.audio.availableOutputs.joinToString()}")
        r.engine?.let { e ->
            Line(stringResource(R.string.diag_engine, e.sampleRate, e.framesPerBurst, e.bufferSizeFrames))
            Line(stringResource(R.string.diag_latency, e.estimatedLatencyMs?.let { "%.1f".format(it) } ?: unknown))
            Line(stringResource(R.string.diag_stream_mode, if (e.exclusive) yes else no, if (e.lowLatency) yes else no, e.xRuns ?: -1))
            Line(stringResource(R.string.diag_pack, if (e.samplePackInstalled) yes else no, e.samplePackBytes / 1_000_000))
        }
    }
    PageCard(title = stringResource(R.string.diag_section_midi)) {
        if (r.midi.devices.isEmpty()) {
            Line(stringResource(R.string.diag_midi_none))
        } else {
            r.midi.devices.forEach { Line("${it.name} · ${it.type} · in ${it.inputPorts} / out ${it.outputPorts}") }
        }
    }
    PageCard(title = stringResource(R.string.diag_section_app)) {
        Line("${r.app.applicationId} ${r.app.versionName} (${r.app.versionCode}) ${r.app.buildType}")
        r.database?.let { db -> Line("DB v${db.schemaVersion}: " + db.tableRowCounts.entries.joinToString { "${it.key}=${it.value}" }) }
    }
    PageCard(title = stringResource(R.string.diag_section_notes)) {
        r.notes.forEach { Line(it, mono = true) }
    }
}

@Composable
private fun Line(text: String, mono: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = if (mono) FontFamily.Monospace else null,
        modifier = Modifier.padding(top = Tokens.Space.xs),
    )
}

private fun copyToClipboard(context: Context, json: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("mimikopi-diagnostics", json))
}

private fun share(context: Context, json: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(send, null))
}
