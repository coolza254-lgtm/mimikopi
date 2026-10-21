package com.id.mimikopi.ui.reader

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Large wrapped systems for setup and result review. Live performance uses a full-height scrolling strip. */
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
        // Previews wrap at a readable phone size; live playback uses the landscape strip.
        val systemHeight = (maxHeight / 1.5f).coerceIn(160.dp, 320.dp)
        val pageWidth = maxWidth
        val density = LocalDensity.current
        val widthSpaces = with(density) { pageWidth.toPx() / (systemHeight.toPx() / 24f) } - 3f
        // Whole steps of 8 spaces (rounded down, so lines still fit): resizing a window re-engraves now and then, not per pixel.
        val lineSpaces = (widthSpaces / 8f).toInt() * 8
        val systems = remember(score, lineSpaces) {
            ScoreLayout.systems(score, lineSpaces.toDouble()).map { r ->
                val slice = score.slice(r.first, r.last)
                PageSystem(r, slice, ScoreLayout.of(slice), score.measureStart(r.first), score.measureStart(r.last + 1))
            }
        }
        val list = rememberLazyListState()
        // The line the cursor is on while playing.
        var current by remember { mutableIntStateOf(0) }
        LaunchedEffect(playing, systems) {
            if (!playing) return@LaunchedEffect
            var shown = -1
            while (true) {
                val beat = withFrameNanos { cursorBeat(it) }
                val i = systems.indexOfLast { it.startBeat <= beat + 1e-6 }.coerceAtLeast(0)
                if (i != shown) {
                    shown = i
                    current = i
                    // Turn the page only when the cursor is off screen or on the last line fully shown.
                    val info = list.layoutInfo
                    val fully = info.visibleItemsInfo.filter { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }.map { it.index }
                    if (i !in fully || i == fully.lastOrNull() && i < systems.size - 1 && fully.size > 1) list.animateScrollToItem(i)
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), state = list, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
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
                    animate = playing && i == current,
                    firstBarNumber = sys.bars.first + 1,
                    cursorOnlyInside = true,
                )
            }
        }
    }
}
