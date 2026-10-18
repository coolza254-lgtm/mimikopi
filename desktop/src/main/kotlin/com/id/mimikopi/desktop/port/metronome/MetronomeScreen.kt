package com.id.mimikopi.desktop.port.metronome

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/metronome/MetronomeScreen.kt): imports changed, other changes marked "Desktop:".

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.id.mimikopi.desktop.port.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.port.Lifecycle
import com.id.mimikopi.desktop.port.LifecycleEventEffect
import com.id.mimikopi.desktop.port.collectAsStateWithLifecycle
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.core.metronome.ClickLevel
import com.id.mimikopi.core.metronome.MetronomeSettings
import com.id.mimikopi.core.metronome.TimeSignature
import com.id.mimikopi.desktop.port.components.PageCard
import com.id.mimikopi.desktop.port.components.ScreenScaffold
import com.id.mimikopi.desktop.ui.Tokens

@Composable
fun MetronomeScreen(onBack: () -> Unit, vm: MetronomeViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val ready by vm.outputReady.collectAsStateWithLifecycle()
    val test by vm.selfTest.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    ScreenScaffold(title = stringResource(R.string.metronome_title), onBack = onBack) {
        PageCard {
            BeatDots(s, running, vm::tickCount)
            Text(
                "${s.bpm}",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text("BPM · ${s.signature.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s)) {
                listOf(-5, -1, +1, +5).forEach { d ->
                    OutlinedButton(onClick = { vm.nudge(d) }, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) {
                        Text(if (d > 0) "+$d" else "$d")
                    }
                }
            }
            Slider(
                value = s.bpm.toFloat(),
                onValueChange = { vm.setBpm(it.toInt()) },
                valueRange = MetronomeSettings.MIN_BPM.toFloat()..MetronomeSettings.MAX_BPM.toFloat(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = vm::toggle, enabled = ready, modifier = Modifier.weight(2f).heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(if (running) R.string.metronome_stop else R.string.metronome_start))
                }
                OutlinedButton(onClick = vm::tap, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.metronome_tap))
                }
            }
        }
        PageCard(title = stringResource(R.string.metronome_meter)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                TimeSignature.entries.forEach { sig ->
                    FilterChip(selected = s.signature == sig, onClick = { vm.setSignature(sig) }, label = { Text(sig.label) })
                }
            }
            if (s.signature.fixedSubdivision == null) {
                Text(stringResource(R.string.metronome_subdivision), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                    listOf(1 to R.string.sub_none, 2 to R.string.sub_eighth, 3 to R.string.sub_triplet, 4 to R.string.sub_sixteenth).forEach { (n, label) ->
                        FilterChip(selected = s.subdivision == n, onClick = { vm.setSubdivision(n) }, label = { Text(stringResource(label)) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Tokens.Space.s)) {
                FilterChip(selected = s.accent, onClick = { vm.setAccent(!s.accent) }, label = { Text(stringResource(R.string.metronome_accent)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                Text(stringResource(R.string.metronome_volume), style = MaterialTheme.typography.bodyMedium)
                Slider(value = s.volume, onValueChange = vm::setVolume, modifier = Modifier.weight(1f))
            }
        }
        // Desktop: no self-test; the desktop's click times are scheduled, not measured from the audio output.
        if (false) PageCard(title = stringResource(R.string.selftest_title)) {
            Text(stringResource(R.string.selftest_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.xs))
            when (val t = test) {
                is SelfTestUi.Running -> {
                    Text(stringResource(R.string.selftest_running), modifier = Modifier.padding(top = Tokens.Space.s))
                    LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
                }
                is SelfTestUi.Done -> Text(
                    stringResource(R.string.selftest_result, t.result.bpm, "%.2f".format(t.result.report.meanAbsMs),
                        "%.2f".format(t.result.report.maxAbsMs), t.result.report.clicks, t.result.xRunsDuring ?: -1),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Tokens.Space.s),
                )
                SelfTestUi.Failed -> Text(stringResource(R.string.selftest_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Tokens.Space.s))
                SelfTestUi.Idle -> Unit
            }
            OutlinedButton(
                onClick = vm::runSelfTest,
                enabled = ready && test !is SelfTestUi.Running,
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s).heightIn(min = Tokens.minTouch),
            ) { Text(stringResource(R.string.selftest_run)) }
        }
    }
}

/**
 * One dot per tick of the bar; the current tick lights up. The light follows the audio clock's tick
 * counter (polled every frame), so it is close to, not exactly at, the sound.
 */
@Composable
fun BeatDots(s: MetronomeSettings, running: Boolean, tickCount: () -> Long, dotSize: Int = 18) {
    val pattern = remember(s) { s.barPattern() }
    var count by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        while (running) {
            withFrameNanos { }
            count = tickCount()
        }
        count = 0
    }
    val current = if (running && count > 0) ((count - 1) % pattern.size).toInt() else -1
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Tokens.Space.xs)) {
        pattern.forEachIndexed { i, level ->
            val on = i == current
            val size = if (level == ClickLevel.SUB) dotSize * 0.6f else dotSize.toFloat()
            Box(
                Modifier
                    .size(size.dp)
                    .background(
                        when {
                            on && level == ClickLevel.STRONG -> Tokens.Palette.Ink
                            on -> Tokens.Palette.InkMuted
                            else -> Tokens.Palette.Line
                        },
                        CircleShape,
                    ),
            )
        }
    }
}
