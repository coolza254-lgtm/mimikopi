package com.id.mimikopi.ui.reader

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout

/** One line of the page view: its bars, the slice of the score and its engraving. */
private class PageSystem(val bars: IntRange, val score: Score, val layout: ScoreLayout, val startBeat: Double, val endBeat: Double)

/**
 * The score wrapped into systems that fit the width (same line breaking as the PDF), stacked vertically like a
 * printed sheet. [paper]: the size of printed music, the page no wider than a sheet, several lines on screen;
 * otherwise large lines for a phone on the music stand. While playing, the page turns (the current line goes to the
 * top) when the cursor reaches the last line that is fully on screen, so the lines ahead can be read in advance.
 * Each system is a [ScoreView] of the slice; note ids are kept by slicing, so results and taps map back to the piece.
 */
@Composable
fun PagedScoreView(
    score: Score,
    cursorBeat: (frameNanos: Long) -> Double,
    playing: Boolean,
    results: Map<NoteKey, NoteResult>,
    wrongMarks: List<WrongMark>,
    loop: IntRange?,
    hotMeasures: List<Int>,
    showNames: Boolean,
    nameStyle: NoteNameStyle,
    onMeasureTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hideNotes: Boolean = false,
    paper: Boolean = true,
) {
    BoxWithConstraints(modifier) {
        // Printed size: a grand staff about as tall as on paper; large: about one and a half systems on screen.
        val systemHeight = if (paper) 170.dp else (maxHeight / 1.5f).coerceIn(160.dp, 320.dp)
        val pageWidth = if (paper) minOf(maxWidth, 1000.dp) else maxWidth
        val density = LocalDensity.current
        val widthSpaces = with(density) { pageWidth.toPx() / (systemHeight.toPx() / 24f) } - 3f
        val systems = remember(score, widthSpaces.toInt()) {
            ScoreLayout.systems(score, widthSpaces.toDouble()).map { r ->
                val slice = score.slice(r.first, r.last)
                PageSystem(r, slice, ScoreLayout.of(slice), score.measureStart(r.first), score.measureStart(r.last + 1))
            }
        }
        val list = rememberLazyListState()
        LaunchedEffect(playing, systems) {
            if (!playing) return@LaunchedEffect
            var shown = -1
            while (true) {
                val beat = withFrameNanos { cursorBeat(it) }
                val i = systems.indexOfLast { it.startBeat <= beat + 1e-6 }.coerceAtLeast(0)
                if (i != shown) {
                    shown = i
                    // Turn the page only when the cursor is off screen or on the last line fully shown.
                    val info = list.layoutInfo
                    val fully = info.visibleItemsInfo.filter { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }.map { it.index }
                    if (i !in fully || i == fully.lastOrNull() && i < systems.size - 1 && fully.size > 1) list.animateScrollToItem(i)
                }
            }
        }
        LazyColumn(state = list, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            items(systems.size) { i ->
                val sys = systems[i]
                val shift = sys.startBeat
                ScoreView(
                    score = sys.score,
                    layout = sys.layout,
                    // The last system keeps the cursor at its end when the run finishes.
                    cursorBeat = { f -> (cursorBeat(f) - shift).let { b -> if (i == systems.size - 1) minOf(b, sys.score.totalBeats - 1e-3) else b } },
                    following = false,
                    results = results,
                    wrongMarks = wrongMarks.filter { it.beat >= sys.startBeat - 1e-6 && it.beat < sys.endBeat }.map { it.copy(beat = it.beat - shift) },
                    loop = loop?.let { l -> (maxOf(l.first, sys.bars.first) - sys.bars.first)..(minOf(l.last, sys.bars.last) - sys.bars.first) }?.takeIf { !it.isEmpty() },
                    hotMeasures = hotMeasures.filter { it in sys.bars }.map { it - sys.bars.first },
                    showNames = showNames,
                    nameStyle = nameStyle,
                    onMeasureTap = { onMeasureTap(it + sys.bars.first) },
                    modifier = Modifier.width(pageWidth).height(systemHeight),
                    hideNotes = hideNotes,
                    animate = playing,
                    firstBarNumber = sys.bars.first + 1,
                    cursorOnlyInside = true,
                )
            }
        }
    }
}
