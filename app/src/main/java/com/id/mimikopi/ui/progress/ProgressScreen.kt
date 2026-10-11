package com.id.mimikopi.ui.progress

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.border

import androidx.compose.foundation.background

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.progress.BenchmarkResult
import com.id.mimikopi.core.progress.Benchmarks
import com.id.mimikopi.core.progress.TrackProgress
import com.id.mimikopi.core.progress.TrackSummary
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class ProgressUi(
    val stages: List<TrackProgress.StageSummary> = emptyList(),
    val tracks: List<TrackSummary> = emptyList(),
    val weakest: TrackSummary? = null,
    val weeks: List<Pair<LocalDate, Long>> = emptyList(),
    val reading: List<Pair<LocalDate, Double>> = emptyList(),
    val timing: List<Pair<LocalDate, Double>> = emptyList(),
    val piecesPassed: Int = 0,
    val piecesTotal: Int = 0,
    val benchmarks: List<BenchmarkResult> = emptyList(),
    val benchDue: Boolean = true,
    val nextBench: LocalDate? = null,
    val totalMinutes: Long = 0,
    val daysPracticed: Int = 0,
)

/** Neutral progress numbers (learning-system.md 2.4 and 12.1): no streaks, no rewards. */
class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val _ui = MutableStateFlow(ProgressUi())
    val ui: StateFlow<ProgressUi> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val repo = services.learning
            repo.load()
            services.pieces.load()
            val records = repo.records.value
            val tracks = TrackProgress.summarize(repo.lessons, records)
            val (reading, timing) = repo.dailyTrends()
            val bench = repo.benchmarks()
            val stats = repo.stats()
            val progress = services.pieces.progress.value
            _ui.value = ProgressUi(
                stages = TrackProgress.stages(repo.lessons, records),
                tracks = tracks,
                weakest = TrackProgress.weakest(tracks),
                weeks = repo.weeklyMinutes(),
                reading = reading,
                timing = timing,
                piecesPassed = services.pieces.pieces.count { s -> progress[s.id]?.let { PieceRules.isPassed(it) } == true },
                piecesTotal = services.pieces.pieces.size,
                benchmarks = bench,
                benchDue = Benchmarks.isDue(bench, LocalDate.now()),
                nextBench = Benchmarks.nextDue(bench),
                totalMinutes = stats.totalMinutes,
                daysPracticed = stats.daysPracticed,
            )
        }
    }
}

@Composable
fun ProgressScreen(onBack: (() -> Unit)?, onBenchNotes: () -> Unit, onBenchPulse: () -> Unit, onBenchReading: () -> Unit, onBenchEar: () -> Unit, vm: ProgressViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    ScreenScaffold(title = stringResource(R.string.progress_title), onBack = onBack) {
        Text(stringResource(R.string.progress_intro, ui.totalMinutes, ui.daysPracticed), style = MaterialTheme.typography.bodyMedium)

        PageCard(title = stringResource(R.string.progress_lessons)) {
            // Stage tiles: done (green), current (rose with a ring), later (plain).
            val current = ui.stages.firstOrNull { it.passed < it.total }?.stage
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = Tokens.Space.s)) {
                ui.stages.forEach { st ->
                    val done = st.passed >= st.total && st.total > 0
                    val now = st.stage == current
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(44.dp)
                                .background(if (done) Tokens.Palette.Good else if (now) Tokens.Palette.Rose else Tokens.Palette.Track, RoundedCornerShape(10.dp))
                                .border(if (now) 2.dp else 0.dp, if (now) Tokens.Palette.RoseDeep else Tokens.Palette.Track, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${st.stage}", fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Black, fontSize = 17.sp,
                                color = if (done) androidx.compose.ui.graphics.Color.White else if (now) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                        }
                        Text("${st.passed}/${st.total}", style = MaterialTheme.typography.labelSmall, color = if (now) Tokens.Palette.RoseDeep else Tokens.Palette.InkMuted)
                    }
                }
            }
            Text(stringResource(R.string.progress_pieces, ui.piecesPassed, ui.piecesTotal), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Tokens.Space.s))
        }

        PageCard(title = stringResource(R.string.progress_tracks)) {
            ui.tracks.forEach { t -> LabeledBar(trackName(t.track), t.passed, t.total) }
            ui.weakest?.takeIf { it.passed < it.total }?.let {
                Text(stringResource(R.string.progress_weakest, trackName(it.track)), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Tokens.Space.s))
            }
        }

        PageCard(title = stringResource(R.string.progress_minutes)) {
            WeeklyBars(ui.weeks, Modifier.fillMaxWidth().height(150.dp).padding(top = Tokens.Space.s))
            val thisWeek = ui.weeks.lastOrNull()?.second ?: 0
            val lastWeek = ui.weeks.getOrNull(ui.weeks.size - 2)?.second ?: 0
            Text(stringResource(R.string.progress_minutes_text, thisWeek, lastWeek), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        }

        PageCard(title = stringResource(R.string.progress_reading)) {
            if (ui.reading.size < 2) Text(stringResource(R.string.progress_not_enough), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            else TrendLine(ui.reading, Modifier.fillMaxWidth().height(130.dp).padding(top = Tokens.Space.s))
            ui.reading.lastOrNull()?.let { Text(stringResource(R.string.progress_latest, pct(it.second)), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted) }
        }

        PageCard(title = stringResource(R.string.progress_timing)) {
            if (ui.timing.size < 2) Text(stringResource(R.string.progress_not_enough), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            else TrendLine(ui.timing, Modifier.fillMaxWidth().height(130.dp).padding(top = Tokens.Space.s))
            ui.timing.lastOrNull()?.let { Text(stringResource(R.string.progress_latest, pct(it.second)), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted) }
        }

        PageCard(title = stringResource(R.string.progress_bench)) {
            Text(
                if (ui.benchDue) stringResource(R.string.progress_bench_due) else stringResource(R.string.progress_bench_next, ui.nextBench.toString()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.progress_bench_parts), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs), modifier = Modifier.padding(top = Tokens.Space.s).horizontalScroll(rememberScrollState())) {
                val today = ui.benchmarks.firstOrNull { it.date == LocalDate.now().toString() }
                BenchButton(R.string.progress_bench_notes, today?.noteIdPerMinute != null, onBenchNotes)
                BenchButton(R.string.progress_bench_reading, today?.sightReadingLevel != null, onBenchReading)
                BenchButton(R.string.progress_bench_pulse, today?.pulseSdMs != null, onBenchPulse)
                BenchButton(R.string.progress_bench_ear, today?.earAccuracy != null, onBenchEar)
            }
            val first = ui.benchmarks.firstOrNull()
            val last = ui.benchmarks.lastOrNull()
            if (first != null && last != null) {
                Text(stringResource(R.string.progress_bench_table), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Tokens.Space.m))
                BenchRow(stringResource(R.string.progress_bench_col), first.date, last.date)
                BenchRow(stringResource(R.string.progress_bench_notes_row), first.noteIdPerMinute?.let { "%.1f".format(it) }, last.noteIdPerMinute?.let { "%.1f".format(it) })
                BenchRow(stringResource(R.string.progress_bench_acc_row), first.noteIdAccuracy?.let { pct(it) }, last.noteIdAccuracy?.let { pct(it) })
                BenchRow(stringResource(R.string.progress_bench_read_row), first.sightReadingLevel?.let { "L$it" }, last.sightReadingLevel?.let { "L$it" })
                BenchRow(stringResource(R.string.progress_bench_pulse_row), first.pulseSdMs?.let { "${it.roundToInt()} ms" }, last.pulseSdMs?.let { "${it.roundToInt()} ms" })
                BenchRow(stringResource(R.string.progress_bench_ear_row), first.earAccuracy?.let { pct(it) }, last.earAccuracy?.let { pct(it) })
                Text(stringResource(R.string.progress_bench_later), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted,
                    modifier = Modifier.padding(top = Tokens.Space.s))
            }
        }
    }
}

private fun pct(v: Double) = "${(v * 100).roundToInt()}%"

@Composable
private fun trackName(track: String): String = stringResource(when (track) {
    "KEYS" -> R.string.track_keys
    "READ_PITCH" -> R.string.track_read_pitch
    "READ_RHYTHM" -> R.string.track_read_rhythm
    "HANDS" -> R.string.track_hands
    "THEORY" -> R.string.track_theory
    "EAR" -> R.string.track_ear
    else -> R.string.track_repertoire
})

@Composable
private fun LabeledBar(label: String, passed: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Tokens.Space.xs)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(150.dp))
        com.id.mimikopi.ui.components.MeterBar(if (total == 0) 0f else passed.toFloat() / total, Modifier.weight(1f), height = 10.dp)
        Text("  $passed/$total", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BenchButton(label: Int, done: Boolean, onClick: () -> Unit) {
    if (done) OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text("✓ " + stringResource(label)) }
    else Button(onClick = onClick, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(label)) }
}

@Composable
private fun BenchRow(label: String, first: String?, last: String?) {
    Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.xs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
        Text(first ?: "–", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(last ?: "–", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/** Single series: thin rounded bars, recessive baseline, only this week's value labelled. */
@Composable
private fun WeeklyBars(weeks: List<Pair<LocalDate, Long>>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 11.sp, color = Tokens.Palette.InkMuted)
    val value = TextStyle(fontSize = 12.sp, color = Tokens.Palette.Ink)
    Canvas(modifier) {
        if (weeks.isEmpty()) return@Canvas
        val bottomPad = 18.dp.toPx()
        val topPad = 16.dp.toPx()
        val h = size.height - bottomPad - topPad
        val max = maxOf(30L, weeks.maxOf { it.second }).toFloat()
        val slot = size.width / weeks.size
        val barW = minOf(18.dp.toPx(), slot * 0.5f)
        drawLine(Tokens.Palette.Line, Offset(0f, topPad + h), Offset(size.width, topPad + h), strokeWidth = 1.dp.toPx())
        weeks.forEachIndexed { i, (start, minutes) ->
            val x = slot * i + (slot - barW) / 2
            val bh = h * minutes / max
            if (minutes > 0) drawRoundRect(Tokens.Palette.ChartMark, Offset(x, topPad + h - bh), Size(barW, bh), CornerRadius(4.dp.toPx()))
            val l = measurer.measure("${start.dayOfMonth}/${start.monthValue}", label)
            drawText(l, topLeft = Offset(slot * i + (slot - l.size.width) / 2, size.height - l.size.height))
            if (i == weeks.lastIndex) {
                val v = measurer.measure("$minutes", value)
                drawText(v, topLeft = Offset(slot * i + (slot - v.size.width) / 2, topPad + h - bh - v.size.height))
            }
        }
    }
}

/** Single series 0-100%: 2px line, markers, light 50% and 100% guides, the last point labelled. */
@Composable
private fun TrendLine(points: List<Pair<LocalDate, Double>>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 11.sp, color = Tokens.Palette.InkMuted)
    Canvas(modifier) {
        val left = 30.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        val top = 8.dp.toPx()
        fun y(v: Double) = bottom - (bottom - top) * v.toFloat().coerceIn(0f, 1f)
        for (g in listOf(0.5, 1.0)) {
            drawLine(Tokens.Palette.Line, Offset(left, y(g)), Offset(size.width, y(g)), strokeWidth = 1.dp.toPx())
            val t = measurer.measure("${(g * 100).roundToInt()}%", label)
            drawText(t, topLeft = Offset(0f, y(g) - t.size.height / 2))
        }
        val first = points.first().first.toEpochDay()
        val span = maxOf(1L, points.last().first.toEpochDay() - first).toFloat()
        fun x(d: LocalDate) = left + (size.width - left - 8.dp.toPx()) * (d.toEpochDay() - first) / span
        val path = Path()
        points.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
        drawPath(path, Tokens.Palette.ChartMark, style = Stroke(width = 2.dp.toPx()))
        points.forEach { (d, v) ->
            drawCircle(Tokens.Palette.Paper, radius = 5.dp.toPx(), center = Offset(x(d), y(v)))
            drawCircle(Tokens.Palette.ChartMark, radius = 4.dp.toPx(), center = Offset(x(d), y(v)))
        }
        val (d0, _) = points.first()
        val (d1, _) = points.last()
        val a = measurer.measure("${d0.dayOfMonth}/${d0.monthValue}", label)
        drawText(a, topLeft = Offset(left, size.height - a.size.height))
        val b = measurer.measure("${d1.dayOfMonth}/${d1.monthValue}", label)
        drawText(b, topLeft = Offset(size.width - b.size.width, size.height - b.size.height))
    }
}
