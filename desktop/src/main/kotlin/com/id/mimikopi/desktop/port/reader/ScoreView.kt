package com.id.mimikopi.desktop.port.reader

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/reader/ScoreView.kt): imports changed, other changes marked "Desktop:".

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
import com.id.mimikopi.desktop.port.components.Bravura
import com.id.mimikopi.desktop.ui.Tokens
import kotlinx.coroutines.delay

/** A wrong key shown briefly at the cursor as a hollow red notehead. */
data class WrongMark(val midi: Int, val beat: Double, val atNanos: Long)

private const val SPACES_TALL = 24f
private const val TOP_PAD = 6f // staff spaces above the treble staff's top line
private const val CURSOR_AT = 0.3f // fraction of the width where the cursor sits while following
private const val WRONG_MARK_NANOS = 700_000_000L
private const val MARK_GRACE_NANOS = 100_000_000L // redraw a little past a wrong key's time, so the frame clearing it is drawn

/**
 * Draws a [ScoreLayout] (one long grand-staff strip) scaled to the box height. While [following],
 * the strip scrolls so the cursor stays near the left; otherwise it can be dragged, and tapping a
 * bar reports it (section selection). [cursorBeat] is read every frame, so the cursor moves smoothly
 * without recomposing the screen; frames where nothing moves (wait mode between notes) are not redrawn.
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
    /** Editor: the selected position (beat) and hand, drawn as a box instead of the play cursor. */
    editorCursor: Pair<Double, Hand>? = null,
    /** Editor: taps report the beat, the staff (hand) and the staff position (0 = middle line). */
    onScoreTap: ((beat: Double, hand: Hand, position: Int) -> Unit)? = null,
    /** Redraw every frame (the cursor moves) even when not scrolling to follow it (page view systems). */
    animate: Boolean = following,
    /** Page view: this strip starts at that bar of the whole piece (bar numbers continue). */
    firstBarNumber: Int = 1,
    /** Page view: draw the cursor only while it is inside this strip (every system but the last). */
    cursorOnlyInside: Boolean = false,
) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val texts = remember(measurer) { TextCache() }
    var frame by remember { mutableLongStateOf(System.nanoTime()) }
    var manualOffset by remember { mutableFloatStateOf(0f) }
    val tap by rememberUpdatedState(onMeasureTap)
    val scoreTap by rememberUpdatedState(onScoreTap)
    val beatNow by rememberUpdatedState(cursorBeat)
    val marksNow by rememberUpdatedState(wrongMarks)
    val names = remember(score, nameStyle) {
        score.events.flatMap { e -> e.pitches.mapIndexed { i, p -> NoteKey(e.id, i) to p.name(nameStyle) } }.toMap()
    }
    val barNumbers = remember(layout, firstBarNumber) { layout.measures.map { (it.index + firstBarNumber).toString() } }
    val tiePath = remember { androidx.compose.ui.graphics.Path() }
    // Last scroll offset in px, shared between drawing and gestures.
    val scroll = remember { floatArrayOf(0f) }
    val spacePx = remember { floatArrayOf(1f) }

    // While playing, redraw on the frames where the cursor moved or a wrong key is fading out; otherwise state
    // changes redraw it (and once more when a wrong key still shown at the stop has had its time).
    LaunchedEffect(animate) {
        if (animate) {
            var drawn = Double.NaN
            while (true) withFrameNanos { f ->
                val beat = beatNow(f)
                if (beat != drawn || marksNow.any { f - it.atNanos <= WRONG_MARK_NANOS + MARK_GRACE_NANOS }) {
                    drawn = beat
                    frame = f
                }
            }
        } else {
            frame = System.nanoTime()
            if (marksNow.any { frame - it.atNanos <= WRONG_MARK_NANOS }) {
                delay((WRONG_MARK_NANOS + MARK_GRACE_NANOS) / 1_000_000)
                frame = System.nanoTime()
            }
        }
    }

    Canvas(
        modifier
            .pointerInput(layout) {
                detectTapGestures { pos ->
                    val sp = spacePx[0]
                    val x = (pos.x + scroll[0]) / sp - 1f
                    val st = scoreTap
                    if (st != null) {
                        val yScore = pos.y / sp + (ScoreLayout.TOP_Y.toFloat() - TOP_PAD)
                        val hand = if (yScore < (ScoreLayout.TREBLE_Y + ScoreLayout.BASS_Y) / 2) Hand.RIGHT else Hand.LEFT
                        val position = Math.round((ScoreLayout.staffY(hand) - yScore) * 2).toInt()
                        st(layout.beatAtX(x.toDouble()), hand, position)
                    } else {
                        layout.measureAtX(x.toDouble())?.let { tap(it) }
                    }
                }
            }
            .pointerInput(layout, following) {
                if (!following) detectHorizontalDragGestures { _, dx -> manualOffset = (manualOffset - dx).coerceAtLeast(0f) }
            },
    ) {
        val space = size.height / SPACES_TALL
        spacePx[0] = space
        texts.at(space)
        val ink = Tokens.Palette.Ink
        val maxOffset = (layout.width.toFloat() * space + space * 2 - size.width).coerceAtLeast(0f)
        val beat = cursorBeat(frame)
        val cursorX = layout.xAt(beat).toFloat() * space
        val editX = editorCursor?.let { layout.xAt(it.first).toFloat() * space }
        val offset = when {
            following -> (cursorX - size.width * CURSOR_AT).coerceIn(0f, maxOffset)
            // The editor keeps the selection on screen.
            editX != null -> (editX - size.width * 0.4f).coerceIn(0f, maxOffset)
            else -> manualOffset.coerceIn(0f, maxOffset)
        }
        scroll[0] = offset
        fun px(x: Double) = x.toFloat() * space - offset + space
        fun py(y: Double) = (y.toFloat() - (ScoreLayout.TOP_Y.toFloat() - TOP_PAD)) * space
        val visible = -space * 4..size.width + space * 4

        // Section and problem-spot tints behind the music.
        for (mi in layout.measures.indices) {
            val m = layout.measures[mi]
            val x0 = px(m.x0)
            val x1 = px(m.x1)
            if (x1 < 0 || x0 > size.width) continue
            val tint = when {
                loop != null && m.index in loop -> Tokens.Palette.Ink.copy(alpha = 0.10f)
                m.index in hotMeasures -> Tokens.Palette.Ink.copy(alpha = 0.06f)
                else -> null
            }
            if (tint != null) drawRect(tint, Offset(x0, py(ScoreLayout.TOP_Y - 3)), Size(x1 - x0, py(ScoreLayout.BOTTOM_Y + 3) - py(ScoreLayout.TOP_Y - 3)))
            text(measurer, texts.bars, barNumbers[mi], x0 + space * 0.2f, py(ScoreLayout.TOP_Y - 2.6), space * 1.2f, Tokens.Palette.InkMuted)
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
            glyph(texts.glyphs.getOrPut(g.codepoint) { measurer.measure(g.codepoint.toString(), glyphStyle) }, x, py(g.y), color)
            val key = g.note ?: continue
            val marker = when (result) {
                NoteResult.EARLY -> "←"
                NoteResult.LATE -> "→"
                NoteResult.MISSED -> "×"
                NoteResult.CORRECTED -> "!"
                else -> null
            }
            if (marker != null) text(measurer, texts.markers, marker, x, py(g.y) - space * 2.6f, space * 1.3f, color)
            if (showNames) {
                names[key]?.let { text(measurer, texts.names, it, x - space * 0.2f, py(g.y) + space * 0.7f, space * 1.1f, Tokens.Palette.InkMuted) }
            }
        }

        // Finger numbers, centred on their (x, y).
        if (!hideNotes) for (lb in layout.labels) {
            val x = px(lb.x)
            if (x !in visible) continue
            val chord = lb.kind == ScoreLayout.LabelKind.CHORD
            val t = (if (chord) texts.chords else texts.fingers).getOrPut(lb.text) {
                measurer.measure(lb.text, TextStyle(fontSize = (space * if (chord) 1.9f else 1.3f).toSp(), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            }
            val left = if (chord) x else x - t.size.width / 2f
            drawText(t, color = Tokens.Palette.Ink, topLeft = Offset(left, py(lb.y) - t.size.height / 2f))
        }
        val tieStroke = Stroke(space * 0.16f)
        if (!hideNotes) for (tie in layout.ties) {
            val x1 = px(tie.x1)
            val x2 = px(tie.x2)
            if (x2 < visible.start || x1 > visible.endInclusive) continue
            val y = py(tie.y)
            val bulge = (if (tie.above) -1f else 1f) * space * 0.9f
            tiePath.reset()
            tiePath.moveTo(x1, y)
            tiePath.quadraticTo((x1 + x2) / 2, y + bulge * 2, x2, y)
            drawPath(tiePath, Tokens.Palette.Ink, style = tieStroke)
        }

        // Wrong keys: hollow white rings where that pitch would sit, at the beat it was played.
        val now = frame
        for (w in wrongMarks) {
            if (now - w.atNanos > WRONG_MARK_NANOS) continue
            val hand = if (w.midi >= 60) Hand.RIGHT else Hand.LEFT
            val pos = Staff.position(SpelledPitch.fromMidiSharp(w.midi), score.clefFor(hand))
            val cx = px(layout.xAt(w.beat)) + space * 0.6f
            drawCircle(Tokens.Palette.Ink, radius = space * 0.6f, center = Offset(cx, py(ScoreLayout.yOf(hand, pos))), style = Stroke(space * 0.22f))
        }

        if (editorCursor != null) {
            val (b, hand) = editorCursor
            val cx = px(layout.xAt(b))
            val cy = py(ScoreLayout.staffY(hand))
            drawRect(Tokens.Palette.Ink.copy(alpha = 0.15f), Offset(cx - space * 0.5f, cy - space * 3.5f), Size(space * 2.3f, space * 7f))
            drawRect(Tokens.Palette.Ink, Offset(cx - space * 0.5f, cy - space * 3.5f), Size(space * 2.3f, space * 7f), style = Stroke(space * 0.15f))
        } else if (!cursorOnlyInside || (beat > -1e-6 && beat < score.totalBeats - 1e-6)) {
            // The play position: a thin line through both staves, like a finger following the page.
            drawLine(Tokens.Palette.Ink, Offset(px(layout.xAt(beat)) - space * 0.3f, py(ScoreLayout.TOP_Y - 2.5)),
                Offset(px(layout.xAt(beat)) - space * 0.3f, py(ScoreLayout.BOTTOM_Y + 2.5)), strokeWidth = (space * 0.14f).coerceAtLeast(1.5f))
        }
    }
}

private fun colorFor(result: NoteResult?): Color? = when (result) {
    NoteResult.CORRECT -> Tokens.Palette.Dim // played: fades, like the design
    NoteResult.EARLY, NoteResult.LATE -> Tokens.Palette.InkMuted
    NoteResult.MISSED, NoteResult.CORRECTED -> Tokens.Palette.Ink
    else -> null
}

/**
 * Measured text of the strip, by kind and string (colours are given when drawing). Every size follows the staff
 * space, so a new space (another height) empties it; one cache per measurer, i.e. per density and font settings.
 */
private class TextCache {
    private var space = Float.NaN
    val glyphs = HashMap<Char, TextLayoutResult>()
    val bars = HashMap<String, TextLayoutResult>()
    val markers = HashMap<String, TextLayoutResult>()
    val names = HashMap<String, TextLayoutResult>()
    val fingers = HashMap<String, TextLayoutResult>()
    val chords = HashMap<String, TextLayoutResult>()

    fun at(space: Float) {
        if (space == this.space) return
        this.space = space
        glyphs.clear(); bars.clear(); markers.clear(); names.clear(); fingers.clear(); chords.clear()
    }
}

/** SMuFL glyph with its origin (baseline, left) at (x, baselineY). */
private fun DrawScope.glyph(layout: TextLayoutResult, x: Float, baselineY: Float, color: Color) {
    drawText(layout, color = color, topLeft = Offset(x, baselineY - layout.firstBaseline))
}

/** [s] at [sizePx], measured once into [cache] (one size per cache). */
private fun DrawScope.text(measurer: TextMeasurer, cache: HashMap<String, TextLayoutResult>, s: String, x: Float, top: Float, sizePx: Float, color: Color) {
    val layout = cache.getOrPut(s) { measurer.measure(s, TextStyle(fontSize = sizePx.toSp())) }
    drawText(layout, color = color, topLeft = Offset(x, top))
}
