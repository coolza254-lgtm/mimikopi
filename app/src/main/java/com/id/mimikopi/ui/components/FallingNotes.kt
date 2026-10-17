package com.id.mimikopi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.id.mimikopi.ui.theme.Tokens
import kotlin.math.exp

/** How a bar of the falling-notes view is drawn. */
enum class FallingState {
    /** Still to play. */
    WAITING,
    /** Played (it fades). */
    DONE,
    /** Not played in time: an outline. */
    MISSED,
    /** Played by the app (the other hand) or outside the part being practised: a quiet outline. */
    BACKGROUND,
}

/**
 * One bar: key [midi], from [start] for [length] in the view's unit (beats for a song, steps for a section).
 * [left] = left hand (grey instead of white).
 */
data class FallingNote(val midi: Int, val start: Double, val length: Double, val left: Boolean, val label: String, val state: FallingState = FallingState.WAITING)

private fun isBlack(midi: Int) = (midi % 12) in intArrayOf(1, 3, 6, 8, 10)

/** Keys from the C at or below [low] to the C at or above [high], at least [minOctaves] octaves, centred on the notes. */
fun fallingKeyRange(low: Int, high: Int, minOctaves: Int = 2): IntRange {
    var lo = low - Math.floorMod(low, 12)
    var hi = if (high % 12 == 0) high else high - high % 12 + 12
    while (hi - lo < minOctaves * 12) { if ((hi - lo) / 12 % 2 == 0) lo -= 12 else hi += 12 }
    return lo.coerceAtLeast(12)..hi.coerceAtMost(120) // C0..C9
}

/**
 * Falling notes over a keyboard, like the piano-roll apps, in black and white: bars come down onto the keys and reach
 * the line above the keyboard when it is time to play them. [cursor] gives the current position in the bars' unit
 * (read every frame while [animate]); [smooth] eases jumps (wait mode moves from note to note). Keys held on the MIDI
 * keyboard ([pressed]) are shown pressed; [marks] are wrong keys (MIDI note, System.nanoTime) shown briefly.
 */
@Composable
fun FallingNotesView(
    notes: List<FallingNote>,
    cursor: (frameNanos: Long) -> Double,
    animate: Boolean,
    keyRange: IntRange,
    pressed: Set<Int>,
    keyLabel: (Int) -> String?,
    modifier: Modifier = Modifier,
    window: Double = 8.0,
    smooth: Boolean = false,
    lines: List<Double> = emptyList(),
    marks: List<Pair<Int, Long>> = emptyList(),
    /** Without a MIDI piano the keys can be tapped (note, down); null = display only. */
    onKey: ((Int, Boolean) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    var frame by remember { mutableLongStateOf(System.nanoTime()) }
    // Shown position and the frame it belongs to, for easing.
    val shown = remember { doubleArrayOf(Double.NaN, 0.0) }
    LaunchedEffect(animate) {
        if (animate) while (true) withFrameNanos { frame = it } else frame = System.nanoTime()
    }
    val whites = remember(keyRange) { keyRange.filter { !isBlack(it) } }

    val touch = if (onKey == null) Modifier else Modifier.touchKeys(keyRange, { x, y, w, h -> fallingKeyAt(x, y, w, h, keyRange, whites) }, onKey)
    Canvas(modifier.then(touch)) {
        val target = cursor(frame)
        val dt = ((frame - shown[1]) / 1e9).coerceIn(0.0, 0.1)
        shown[1] = frame.toDouble()
        val cur = if (!smooth || !animate || shown[0].isNaN() || kotlin.math.abs(target - shown[0]) > window) target
        else shown[0] + (target - shown[0]) * (1 - exp(-dt * 14))
        shown[0] = cur

        val whiteW = size.width / whites.size.coerceAtLeast(1)
        val blackW = whiteW * 0.62f
        val keysH = (size.height * 0.26f).coerceAtMost(whiteW * 6f)
        val hitY = size.height - keysH
        val unit = hitY / window.toFloat()
        val dark = Tokens.dark

        fun keyX(midi: Int): Pair<Float, Float> {
            if (!isBlack(midi)) {
                val i = whites.indexOf(midi).coerceAtLeast(0)
                return i * whiteW to whiteW
            }
            val below = whites.indexOfLast { it < midi }.coerceAtLeast(0)
            return (below + 1) * whiteW - blackW / 2 to blackW
        }

        // Bar lines (measures, or phrase starts).
        for (b in lines) {
            val y = hitY - ((b - cur) * unit).toFloat()
            if (y < 0 || y > hitY) continue
            drawLine(Tokens.Palette.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        // The bars, clipped to the area above the keyboard.
        val radius = CornerRadius(whiteW * 0.16f)
        for (n in notes) {
            if (n.midi !in keyRange) continue
            val bottom = hitY - ((n.start - cur) * unit).toFloat()
            val top = bottom - (n.length * unit).toFloat() + 2f
            if (top > hitY || bottom < 0) continue
            val (kx, kw) = keyX(n.midi)
            val w = if (isBlack(n.midi)) kw * 0.92f else kw * 0.82f
            val x = kx + (kw - w) / 2
            val t = top.coerceAtLeast(0f)
            val b = bottom.coerceAtMost(hitY)
            if (b - t < 1f) continue
            val fill = when (n.state) {
                FallingState.WAITING -> if (n.left) Tokens.Palette.InkMuted else Tokens.Palette.Ink
                FallingState.DONE -> Tokens.Palette.Faint
                FallingState.MISSED, FallingState.BACKGROUND -> null
            }
            if (fill != null) drawRoundRect(fill, Offset(x, t), Size(w, b - t), radius)
            else drawRoundRect(
                if (n.state == FallingState.MISSED) Tokens.Palette.Ink else Tokens.Palette.Dim,
                Offset(x, t), Size(w, b - t), radius, style = Stroke(if (n.state == FallingState.MISSED) 2.5f else 1.5f),
            )
            if (n.state == FallingState.WAITING && b - t > w * 0.9f && n.label.isNotEmpty()) {
                label(measurer, n.label, x + w / 2, b - 4f, (w * 0.5f).coerceAtMost(20.sp.toPx()), Tokens.Palette.OnInk)
            }
        }

        // The line where notes are played.
        drawLine(Tokens.Palette.Ink, Offset(0f, hitY - 1.5f), Offset(size.width, hitY - 1.5f), strokeWidth = 3f)

        // Keyboard: white keys light and black keys dark in both looks, held keys grey.
        val whiteKey = if (dark) Color(0xFFE6E6E6) else Color(0xFFFFFFFF)
        val heldWhite = Color(0xFF8C8C8C)
        val heldBlack = Color(0xFF7A7A7A)
        val now = System.nanoTime()
        val marked = marks.filter { now - it.second < 700_000_000L }.map { it.first }.toSet()
        for ((i, m) in whites.withIndex()) {
            val x = i * whiteW
            drawRect(if (m in pressed) heldWhite else whiteKey, Offset(x, hitY), Size(whiteW, keysH))
            drawLine(Color(0xFF9E9E9E), Offset(x, hitY), Offset(x, size.height), strokeWidth = 1f)
            keyLabel(m)?.let { label(measurer, it, x + whiteW / 2, size.height - 6f, (whiteW * 0.42f).coerceAtMost(16.sp.toPx()), Color(0xFF5E5E5E)) }
            if (m in marked) drawCircle(Color(0xFF000000), whiteW * 0.28f, Offset(x + whiteW / 2, hitY + keysH * 0.45f), style = Stroke(whiteW * 0.08f))
        }
        for (m in keyRange) {
            if (!isBlack(m)) continue
            val (x, w) = keyX(m)
            drawRoundRect(if (m in pressed) heldBlack else Color(0xFF151515), Offset(x, hitY), Size(w, keysH * 0.62f), CornerRadius(w * 0.12f))
            if (m in marked) drawCircle(Color(0xFFFFFFFF), w * 0.32f, Offset(x + w / 2, hitY + keysH * 0.38f), style = Stroke(w * 0.1f))
        }
    }
}

/** The key under a point, with the same geometry as the keyboard drawn by [FallingNotesView]. */
private fun fallingKeyAt(x: Float, y: Float, width: Float, height: Float, keyRange: IntRange, whites: List<Int>): Int? {
    if (whites.isEmpty()) return null
    val whiteW = width / whites.size
    val blackW = whiteW * 0.62f
    val keysH = (height * 0.26f).coerceAtMost(whiteW * 6f)
    val hitY = height - keysH
    if (y < hitY || y > height) return null
    if (y < hitY + keysH * 0.62f) {
        for (m in keyRange) {
            if (!isBlack(m)) continue
            val below = whites.indexOfLast { it < m }.coerceAtLeast(0)
            val left = (below + 1) * whiteW - blackW / 2
            if (x >= left && x < left + blackW) return m
        }
    }
    return whites.getOrNull((x / whiteW).toInt().coerceIn(0, whites.lastIndex))
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, centerX: Float, bottom: Float, sizePx: Float, color: Color) {
    if (sizePx < 6f) return
    val layout = measurer.measure(text, TextStyle(fontSize = sizePx.toSp(), fontWeight = FontWeight.SemiBold, color = color))
    drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, bottom - layout.size.height))
}
