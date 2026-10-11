package com.id.mimikopi.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Staff
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import com.id.mimikopi.ui.components.Bravura
import com.id.mimikopi.ui.theme.Tokens

/** A wrong key shown briefly at the cursor as a hollow red notehead. */
data class WrongMark(val midi: Int, val beat: Double, val atNanos: Long)

private const val SPACES_TALL = 24f
private const val TOP_PAD = 6f // staff spaces above the treble staff's top line
private const val CURSOR_AT = 0.3f // fraction of the width where the cursor sits while following
private const val WRONG_MARK_NANOS = 700_000_000L

/**
 * Draws a [ScoreLayout] (one long grand-staff strip) scaled to the box height. While [following],
 * the strip scrolls so the cursor stays near the left; otherwise it can be dragged, and tapping a
 * bar reports it (section selection). [cursorBeat] is read every frame, so the cursor moves smoothly
 * without recomposing the screen.
 */
@Composable
fun ScoreView(
    score: Score,
    layout: ScoreLayout,
    cursorBeat: (frameNanos: Long) -> Double,
    following: Boolean,
    results: Map<NoteKey, NoteResult>,
    wrongMarks: List<WrongMark>,
    loop: IntRange?,
    hotMeasures: List<Int>,
    showNames: Boolean,
    nameStyle: NoteNameStyle,
    onMeasureTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Transcribe helper: draw only the staves, clefs and bar lines. */
    hideNotes: Boolean = false,
) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    var frame by remember { mutableLongStateOf(System.nanoTime()) }
    var manualOffset by remember { mutableFloatStateOf(0f) }
    val tap by rememberUpdatedState(onMeasureTap)
    val pitches = remember(score) {
        score.events.flatMap { e -> e.pitches.mapIndexed { i, p -> NoteKey(e.id, i) to (e.hand to p) } }.toMap()
    }
    // Last scroll offset in px, shared between drawing and gestures.
    val scroll = remember { floatArrayOf(0f) }
    val spacePx = remember { floatArrayOf(1f) }

    // Redraw every frame only while playing (the cursor moves); otherwise state changes redraw it.
    LaunchedEffect(following) {
        if (following) while (true) withFrameNanos { frame = it } else frame = System.nanoTime()
    }

    Canvas(
        modifier
            .pointerInput(layout) {
                detectTapGestures { pos ->
                    val x = (pos.x + scroll[0]) / spacePx[0]
                    layout.measureAtX(x.toDouble())?.let { tap(it) }
                }
            }
            .pointerInput(layout, following) {
                if (!following) detectHorizontalDragGestures { _, dx -> manualOffset = (manualOffset - dx).coerceAtLeast(0f) }
            },
    ) {
        val space = size.height / SPACES_TALL
        spacePx[0] = space
        val ink = Tokens.Palette.Ink
        val maxOffset = (layout.width.toFloat() * space + space * 2 - size.width).coerceAtLeast(0f)
        val beat = cursorBeat(frame)
        val cursorX = layout.xAt(beat).toFloat() * space
        val offset = if (following) (cursorX - size.width * CURSOR_AT).coerceIn(0f, maxOffset) else manualOffset.coerceIn(0f, maxOffset)
        scroll[0] = offset
        fun px(x: Double) = x.toFloat() * space - offset + space
        fun py(y: Double) = (y.toFloat() - (ScoreLayout.TOP_Y.toFloat() - TOP_PAD)) * space
        val visible = -space * 4..size.width + space * 4

        // Section and problem-spot tints behind the music.
        for (m in layout.measures) {
            val x0 = px(m.x0)
            val x1 = px(m.x1)
            if (x1 < 0 || x0 > size.width) continue
            val tint = when {
                loop != null && m.index in loop -> Tokens.Palette.MustardSoft.copy(alpha = 0.55f)
                m.index in hotMeasures -> Tokens.Palette.RoseSoft.copy(alpha = 0.45f)
                else -> null
            }
            if (tint != null) drawRect(tint, Offset(x0, py(ScoreLayout.TOP_Y - 3)), Size(x1 - x0, py(ScoreLayout.BOTTOM_Y + 3) - py(ScoreLayout.TOP_Y - 3)))
            text(measurer, (m.index + 1).toString(), x0 + space * 0.2f, py(ScoreLayout.TOP_Y - 2.6), space * 1.2f, Tokens.Palette.InkMuted)
        }

        for (l in layout.lines) {
            if (hideNotes && l.eventId != null) continue
            val x1 = px(l.x1)
            val x2 = px(l.x2)
            if (maxOf(x1, x2) < visible.start || minOf(x1, x2) > visible.endInclusive) continue
            drawLine(ink, Offset(x1, py(l.y1)), Offset(x2, py(l.y2)), strokeWidth = l.thickness.toFloat() * space)
        }

        val glyphStyle = TextStyle(fontFamily = Bravura, fontSize = (space * 4f).toSp())
        for (g in layout.glyphs) {
            if (hideNotes && g.eventId != null) continue
            val x = px(g.x)
            if (x !in visible) continue
            val result = g.note?.let { results[it] }
            val color = colorFor(result) ?: ink
            glyph(measurer, g.codepoint, glyphStyle, x, py(g.y), color)
            val key = g.note ?: continue
            val marker = when (result) {
                NoteResult.EARLY -> "←"
                NoteResult.LATE -> "→"
                NoteResult.MISSED -> "×"
                NoteResult.CORRECTED -> "!"
                else -> null
            }
            if (marker != null) text(measurer, marker, x, py(g.y) - space * 2.6f, space * 1.3f, color)
            if (showNames) {
                pitches[key]?.let { (_, p) -> text(measurer, p.name(nameStyle), x - space * 0.2f, py(g.y) + space * 0.7f, space * 1.1f, Tokens.Palette.InkMuted) }
            }
        }

        // Finger numbers, centred on their (x, y).
        if (!hideNotes) for (lb in layout.labels) {
            val x = px(lb.x)
            if (x !in visible) continue
            val t = measurer.measure(lb.text, TextStyle(fontSize = (space * 1.3f).toSp(), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            drawText(t, color = Tokens.Palette.Ink, topLeft = Offset(x - t.size.width / 2f, py(lb.y) - t.size.height / 2f))
        }

        // Wrong keys: hollow red heads where that pitch would sit, at the beat it was played.
        val now = frame
        for (w in wrongMarks) {
            if (now - w.atNanos > WRONG_MARK_NANOS) continue
            val hand = if (w.midi >= 60) Hand.RIGHT else Hand.LEFT
            val pos = Staff.position(SpelledPitch.fromMidiSharp(w.midi), score.clefFor(hand))
            val cx = px(layout.xAt(w.beat)) + space * 0.6f
            drawCircle(Tokens.Palette.Error, radius = space * 0.6f, center = Offset(cx, py(ScoreLayout.yOf(hand, pos))), style = Stroke(space * 0.22f))
        }

        drawLine(Tokens.Palette.Rose, Offset(px(layout.xAt(beat)) - space * 0.3f, py(ScoreLayout.TOP_Y - 2.5)),
            Offset(px(layout.xAt(beat)) - space * 0.3f, py(ScoreLayout.BOTTOM_Y + 2.5)), strokeWidth = space * 0.3f)
    }
}

private fun colorFor(result: NoteResult?): Color? = when (result) {
    NoteResult.CORRECT -> Tokens.Palette.Good
    NoteResult.EARLY, NoteResult.LATE -> Tokens.Palette.Off
    NoteResult.MISSED, NoteResult.CORRECTED -> Tokens.Palette.Error
    else -> null
}

/** SMuFL glyph with its origin (baseline, left) at (x, baselineY). Layouts are cached by the measurer. */
private fun DrawScope.glyph(measurer: TextMeasurer, c: Char, style: TextStyle, x: Float, baselineY: Float, color: Color) {
    val layout: TextLayoutResult = measurer.measure(c.toString(), style)
    drawText(layout, color = color, topLeft = Offset(x, baselineY - layout.firstBaseline))
}

private fun DrawScope.text(measurer: TextMeasurer, s: String, x: Float, top: Float, sizePx: Float, color: Color) {
    val layout = measurer.measure(s, TextStyle(fontSize = sizePx.toSp()))
    drawText(layout, color = color, topLeft = Offset(x, top))
}
