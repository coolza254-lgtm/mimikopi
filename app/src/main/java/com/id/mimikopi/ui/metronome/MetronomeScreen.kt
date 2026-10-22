package com.id.mimikopi.ui.metronome

import com.id.mimikopi.ui.components.softShadow
import com.id.mimikopi.ui.components.Chip
import com.id.mimikopi.ui.components.Segmented
import com.id.mimikopi.ui.components.SecondaryButton
import com.id.mimikopi.ui.components.PrimaryButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.metronome.ClickLevel
import com.id.mimikopi.core.metronome.MetronomeSettings
import com.id.mimikopi.core.metronome.TimeSignature
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens

@Composable
fun MetronomeScreen(onBack: () -> Unit, vm: MetronomeViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val ready by vm.outputReady.collectAsStateWithLifecycle()
    val test by vm.selfTest.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    ScreenScaffold(title = stringResource(R.string.metronome_title), onBack = onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BeatDots(s, running, vm::tickCount, dotSize = 22)
            Dial(s.bpm, Modifier.padding(top = 16.dp))
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundKey("−", onClick = { vm.nudge(-1) }, onLong = { vm.nudge(-5) })
                val shape = RoundedCornerShape(28.dp)
                Box(
                    Modifier.height(56.dp).softShadow(shape, small = true).clip(shape).background(Tokens.Palette.Group).clickable(onClick = vm::tap)
                        .padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.metronome_tap), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink) }
                RoundKey("+", onClick = { vm.nudge(1) }, onLong = { vm.nudge(5) })
            }
            Slider(
                value = s.bpm.toFloat(),
                onValueChange = { vm.setBpm(it.toInt()) },
                valueRange = MetronomeSettings.MIN_BPM.toFloat()..MetronomeSettings.MAX_BPM.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Tokens.Palette.Group, activeTrackColor = Tokens.Palette.Accent, inactiveTrackColor = Tokens.Palette.Track),
                modifier = Modifier.padding(top = 8.dp),
            )
            PrimaryButton(stringResource(if (running) R.string.metronome_stop else R.string.metronome_start), onClick = vm::toggle, enabled = ready, play = !running)
        }
        PageCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.metronome_meter), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(end = 12.dp))
                Segmented(TimeSignature.entries.toList(), s.signature, { it.label }, vm::setSignature, Modifier.weight(1f))
            }
            if (s.signature.fixedSubdivision == null) {
                Text(stringResource(R.string.metronome_subdivision), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink,
                    modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
                val subs = listOf(1 to R.string.sub_none, 2 to R.string.sub_eighth, 3 to R.string.sub_triplet, 4 to R.string.sub_sixteenth)
                val names = subs.associate { (n, label) -> n to stringResource(label) }
                Segmented(subs.map { it.first }, s.subdivision, { names.getValue(it) }, vm::setSubdivision)
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chip(stringResource(R.string.metronome_accent), selected = s.accent) { vm.setAccent(!s.accent) }
                Text(stringResource(R.string.metronome_volume), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                Slider(value = s.volume, onValueChange = vm::setVolume, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Tokens.Palette.Group, activeTrackColor = Tokens.Palette.Accent, inactiveTrackColor = Tokens.Palette.Track))
            }
        }
        PageCard(title = stringResource(R.string.selftest_title)) {
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
            SecondaryButton(
                stringResource(R.string.selftest_run),
                onClick = { if (ready && test !is SelfTestUi.Running) vm.runSelfTest() },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Tokens.Space.xs)) {
        pattern.forEachIndexed { i, level ->
            val on = i == current
            val size = if (level == ClickLevel.SUB) dotSize * 0.6f else dotSize.toFloat()
            Box(
                Modifier
                    .size(size.dp)
                    .background(
                        when {
                            on && level == ClickLevel.STRONG -> Tokens.Palette.Accent
                            on -> Tokens.Palette.AccentMidStrong
                            else -> Tokens.Palette.Track
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/** The tempo in a white dial with a soft shadow; the accent arc shows where it sits between the slowest and fastest. */
@Composable
private fun Dial(bpm: Int, modifier: Modifier = Modifier) {
    val accent = Tokens.Palette.Accent
    val track = Tokens.Palette.Paper
    val fraction = (bpm - MetronomeSettings.MIN_BPM).toFloat() / (MetronomeSettings.MAX_BPM - MetronomeSettings.MIN_BPM)
    Box(modifier.size(250.dp).softShadow(CircleShape).clip(CircleShape).background(Tokens.Palette.Group), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(250.dp)) {
            val stroke = 8.dp.toPx()
            val inset = 12.dp.toPx()
            val arc = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * fraction.coerceIn(0.02f, 1f), false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$bpm", fontSize = 76.sp, lineHeight = 84.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            Text("BPM · ${tempoName(bpm)}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
        }
    }
}

/** The usual Italian tempo word for a speed, as a rough guide. */
private fun tempoName(bpm: Int) = when {
    bpm < 60 -> "Largo"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 168 -> "Allegro"
    else -> "Presto"
}

/** A white round key with a soft shadow: tap for one step, hold for a bigger one. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RoundKey(text: String, onClick: () -> Unit, onLong: () -> Unit) {
    Box(
        Modifier.size(56.dp).softShadow(CircleShape, small = true).clip(CircleShape).background(Tokens.Palette.Group)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, color = Tokens.Palette.Ink) }
}
