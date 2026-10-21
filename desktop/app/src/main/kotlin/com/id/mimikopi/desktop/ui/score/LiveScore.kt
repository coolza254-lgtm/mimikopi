package com.id.mimikopi.desktop.ui.score

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import com.id.mimikopi.desktop.session.WrongMark

private class System(val bars: IntRange, val score: Score, val layout: ScoreLayout, val startBeat: Double, val endBeat: Double)

/**
 * The music for playing, as large as the window allows: wrapped lines (systems) whose height is a share of the
 * available height, so a maximised or fullscreen window gives big staves and a small window still shows the next
 * line. While playing, the line with the cursor is kept in view and the page turns a line early, so the eye always
 * has the next bar to read. There is no paper-size mode: the size follows the window.
 */
@Composable
fun LiveScore(
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
    /** Lines visible at once; null = from the window height (one line per ~380 dp, at least one). */
    lines: Int? = null,
) {
    val beatNow by rememberUpdatedState(cursorBeat)
    BoxWithConstraints(modifier) {
        // A one-hand piece has one staff (about half as tall), so more lines fit at the same note size.
        val staves = remember(score) { ScoreLayout.staves(score) }
        val tall = remember(score) { ScoreLayout.of(score).spacesTall.toFloat() }
        // Staff spaces of at most 18 dp: large and clear, while a line still holds two to four bars.
        val perLine = 18.dp * tall
        val shown = (lines ?: (maxHeight / perLine).toInt()).coerceIn(1, 3)
        val systemHeight: Dp = (maxHeight / shown).coerceIn(110.dp, perLine)
        val pageWidth = maxWidth
        val density = LocalDensity.current
        val widthSpaces = with(density) { pageWidth.toPx() / (systemHeight.toPx() / tall) } - 3f
        // Whole steps of 8 spaces: resizing the window re-engraves now and then, not every pixel.
        val lineSpaces = ((widthSpaces / 8f).toInt() * 8).coerceAtLeast(24)
        val systems = remember(score, lineSpaces) {
            ScoreLayout.systems(score, lineSpaces.toDouble()).map { r ->
                val slice = score.slice(r.first, r.last)
                // Lines fill the width like engraved music; a short last line keeps natural spacing, and only the
                // piece's own end gets the final double bar.
                val end = r.last == score.measures - 1
                val natural = ScoreLayout.line(slice, finalBar = end, timeSignature = r.first == 0, staves = staves)
                val layout = if (end && natural.width < lineSpaces * 0.5) natural
                else ScoreLayout.justified(slice, lineSpaces.toDouble(), finalBar = end, timeSignature = r.first == 0, staves = staves)
                System(r, slice, layout, score.measureStart(r.first), score.measureStart(r.last + 1))
            }
        }
        val list = rememberLazyListState()
        var current by remember { mutableIntStateOf(0) }
        LaunchedEffect(playing, systems) {
            if (!playing) return@LaunchedEffect
            var shownLine = -1
            while (true) {
                val beat = withFrameNanos { beatNow(it) }
                val i = systems.indexOfLast { it.startBeat <= beat + 1e-6 }.coerceAtLeast(0)
                if (i != shownLine) {
                    shownLine = i
                    current = i
                    // Keep the cursor's line on top when only one or two lines fit; with three, turn when it reaches the last.
                    val first = list.firstVisibleItemIndex
                    if (i < first || i >= first + shown - (if (shown > 1) 1 else 0)) list.animateScrollToItem(i)
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            items(systems.size) { i ->
                val sys = systems[i]
                val shift = sys.startBeat
                ScoreView(
                    score = sys.score,
                    layout = sys.layout,
                    cursorBeat = { f -> (beatNow(f) - shift).let { b -> if (i == systems.size - 1) minOf(b, sys.score.totalBeats - 1e-3) else b } },
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
