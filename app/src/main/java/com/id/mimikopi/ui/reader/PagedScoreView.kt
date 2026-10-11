package com.id.mimikopi.ui.reader

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * The score wrapped into systems that fit the width (same line breaking as the PDF), stacked vertically. While
 * playing, the list scrolls so the system holding the cursor stays in view. Each system is a [ScoreView] of the
 * slice; note ids are kept by slicing, so results and taps map back to the whole piece.
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
) {
    BoxWithConstraints(modifier) {
        // About two systems on screen in landscape, more in portrait; never smaller than readable.
        val systemHeight = (maxHeight / 2).coerceIn(120.dp, 190.dp)
        val density = LocalDensity.current
        val widthSpaces = with(density) { maxWidth.toPx() / (systemHeight.toPx() / 24f) } - 3f
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
                    list.animateScrollToItem(i)
                }
            }
        }
        LazyColumn(state = list) {
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
                    modifier = Modifier.fillMaxWidth().height(systemHeight),
                    hideNotes = hideNotes,
                    animate = playing,
                    firstBarNumber = sys.bars.first + 1,
                    cursorOnlyInside = true,
                )
            }
        }
    }
}
