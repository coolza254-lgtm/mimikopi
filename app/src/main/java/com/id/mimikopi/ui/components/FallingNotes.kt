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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.keyboard.KeyRect
import com.id.mimikopi.core.keyboard.KeyboardLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.round

/** Background notes belong to the accompaniment; only judged notes become done or missed. */
enum class FallingState { WAITING, DONE, MISSED, BACKGROUND }
data class FallingNote(val midi: Int, val start: Double, val length: Double, val left: Boolean, val label: String, val state: FallingState = FallingState.WAITING)

fun fallingKeyRange(low: Int, high: Int, minOctaves: Int = 2): IntRange {
    var lo = low - Math.floorMod(low, 12)
    var hi = if (high % 12 == 0) high else high - high % 12 + 12
    while (hi - lo < minOctaves * 12) { if ((hi - lo) / 12 % 2 == 0) lo -= 12 else hi += 12 }
    return lo.coerceAtLeast(12)..hi.coerceAtMost(120)
}

/** The very same geometry is used for the drawn keys, falling bars, effects and touch input. */
internal class FallingGeometry(range: IntRange, width: Float, height: Float) {
    private val units = KeyboardLayout(range.first, range.last, 1f, 1f)
    val whiteWidth = width / units.whiteKeys.size
    val keyHeight = (height * .28f).coerceAtMost(whiteWidth * 6f)
    val hitY = height - keyHeight
    val keyboard = KeyboardLayout(range.first, range.last, whiteWidth, keyHeight)
    val byNote = (keyboard.whiteKeys + keyboard.blackKeys).associateBy { it.note }
    fun hit(x: Float, y: Float): Int? = keyboard.hitTest(x, y - hitY)
}

internal object FallingColors {
    val left = Color(0xFF78AFE1)
    val right = Color(0xFFA1EF52)
    val timing = Color(0xFFFFC56B)
    val error = Color(0xFFFF6577)
    val ink = Color(0xFF16251B)
    fun hand(leftHand: Boolean) = if (leftHand) left else right
    fun feedback(event: FallingFeedback) = when (event.verdict) {
        FallingVerdict.HIT, FallingVerdict.CORRECTED -> hand(event.left)
        FallingVerdict.EARLY, FallingVerdict.LATE -> timing
        FallingVerdict.WRONG, FallingVerdict.MISS -> error
    }
}

@Composable
fun FallingNotesView(
    notes: List<FallingNote>, cursor: (Long) -> Double, animate: Boolean,
    keyRange: IntRange, pressed: Set<Int>, keyLabel: (Int) -> String?,
    modifier: Modifier = Modifier, window: Double = 8.0, smooth: Boolean = false,
    lines: List<Double> = emptyList(), feedback: List<FallingFeedback> = emptyList(),
    totalBeats: Double = 0.0,
    onKey: ((Int, Boolean) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val renderer = remember(measurer, keyRange) { FallingRenderer(keyRange, measurer) }
    val sorted = remember(notes) { notes.sortedBy { it.start } }
    val longest = remember(sorted) { sorted.maxOfOrNull { it.length } ?: 0.0 }
    var frame by remember { mutableLongStateOf(System.nanoTime()) }
    val shown = remember { doubleArrayOf(Double.NaN, 0.0) }
    // Finish pulses even when playback stops, then stop requesting frames while idle.
    LaunchedEffect(animate, feedback) {
        do {
            withFrameNanos { frame = it }
        } while (animate || feedback.any { it.age(frame) < 1f })
    }
    val touch = if (onKey == null) Modifier else Modifier.touchKeys(keyRange, { x, y, w, h ->
        renderer.geometry(w, h).hit(x, y)
    }, onKey)
    Canvas(modifier.then(touch)) {
        val target = cursor(frame)
        val dt = ((frame - shown[1]) / 1e9).coerceIn(0.0, .1)
        shown[1] = frame.toDouble()
        val cur = if (!smooth || !animate || shown[0].isNaN() || abs(target - shown[0]) > window) target
        else shown[0] + (target - shown[0]) * (1 - exp(-dt * 14))
        shown[0] = cur
        renderer.draw(this, sorted, longest, cur, window, lines, pressed, keyLabel, feedback, frame, totalBeats)
    }
}

/** Shared by the live Canvas and offscreen Android rendering checks. Keeps text and geometry out of the frame loop. */
internal class FallingRenderer(private val range: IntRange, private val measurer: TextMeasurer) {
    private var dimensions = Size.Unspecified
    private var layout: FallingGeometry? = null
    private val barLabels = LabelCache()
    private val blackBarLabels = LabelCache()
    private val whiteLabels = LabelCache()
    private val blackLabels = LabelCache()
    private val captionLabels = LabelCache()
    private val latest = arrayOfNulls<FallingFeedback>(128)

    fun geometry(width: Float, height: Float): FallingGeometry {
        if (dimensions != Size(width, height)) {
            dimensions = Size(width, height)
            layout = FallingGeometry(range, width, height)
        }
        return requireNotNull(layout)
    }

    fun draw(scope: DrawScope, notes: List<FallingNote>, longest: Double, cursor: Double, window: Double,
             lines: List<Double>, pressed: Set<Int>, keyLabel: (Int) -> String?,
             feedback: List<FallingFeedback>, now: Long, totalBeats: Double) = with(scope) {
        if (size.width <= 0 || size.height <= 0) return@with
        val g = geometry(size.width, size.height)
        val hitY = g.hitY
        val unit = hitY / window.coerceAtLeast(.1).toFloat()
        val whiteW = g.whiteWidth
        drawRect(Brush.verticalGradient(listOf(Color(0xFF202428), Color(0xFF292D31)), endY = hitY))
        clipRect(bottom = hitY) {
            for (k in g.keyboard.whiteKeys) {
                if (k.note % 12 == 0) drawRect(Color.White.copy(alpha = .025f), Offset(k.left, 0f), Size(k.width, hitY))
                drawLine(Color.White.copy(alpha = if (k.note % 12 == 0) .13f else .045f), Offset(k.left, 0f), Offset(k.left, hitY))
            }
            for (b in lines) {
                val y = hitY - ((b - cursor) * unit).toFloat()
                if (y in 0f..hitY) drawLine(Color.White.copy(alpha = .1f), Offset(0f, y), Offset(size.width, y))
            }
            var lo = 0
            var hi = notes.size
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (notes[mid].start < cursor - longest - 1) lo = mid + 1 else hi = mid }
            for (i in lo until notes.size) {
                val n = notes[i]
                val bottom = hitY - ((n.start - cursor) * unit).toFloat()
                if (bottom < 0) break
                val k = g.byNote[n.midi] ?: continue
                val top = bottom - (n.length * unit).toFloat() + 2f
                if (top > hitY) continue
                val w = k.width * if (k.black) .94f else .86f
                val x = k.left + (k.width - w) / 2
                val t = top.coerceAtLeast(-20f)
                val h = (bottom - t).coerceAtLeast(2f)
                val color = FallingColors.hand(n.left)
                val radius = CornerRadius((w * .18f).coerceAtMost(8f))
                when (n.state) {
                    FallingState.BACKGROUND -> drawRoundRect(color.copy(alpha = .32f), Offset(x, t), Size(w, h), radius, style = Stroke(1.5f))
                    FallingState.MISSED -> drawRoundRect(FallingColors.error.copy(alpha = .55f), Offset(x, t), Size(w, h), radius, style = Stroke(2f))
                    else -> {
                        val alpha = if (n.state == FallingState.DONE) .22f else 1f
                        drawRoundRect(Brush.horizontalGradient(listOf(color.copy(alpha = alpha * .67f), color.copy(alpha = alpha), color.copy(alpha = alpha * .8f)), x, x + w), Offset(x, t), Size(w, h), radius)
                        drawRoundRect(Color.White.copy(alpha = .22f * alpha), Offset(x + 1f, t + 1f), Size((w - 2).coerceAtLeast(1f), (h - 2).coerceAtLeast(1f)), radius, style = Stroke(1f))
                    }
                }
                if (n.state == FallingState.WAITING && h > 12f && n.label.isNotEmpty()) {
                    label(measurer, if (k.black) blackBarLabels else barLabels, n.label, x + w / 2, bottom - 5f,
                        (w * .52f).coerceAtMost(22.sp.toPx()).coerceAtMost((h - 4f) * .7f), FallingColors.ink, w - 4f)
                }
            }
        }
        latest.fill(null)
        for (e in feedback) if (e.midi in 0..127) latest[e.midi] = e
        fun keyColor(k: KeyRect): Color? {
            val e = latest[k.note]
            if (e != null && e.age(now) < 1f) return FallingColors.feedback(e).copy(alpha = (1f - e.age(now)) * .65f + .35f)
            return if (k.note in pressed) Color(0xFF81909C) else null
        }
        for (k in g.keyboard.whiteKeys) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFAFAEC), Color(0xFFE4E5D8)), hitY, size.height), Offset(k.left, hitY), Size(k.width, g.keyHeight))
            keyColor(k)?.let { drawRect(it, Offset(k.left + 1f, hitY), Size(k.width - 2f, g.keyHeight)) }
            drawLine(Color(0xFF42474A), Offset(k.left, hitY), Offset(k.left, size.height), 1.5f)
            drawLine(Color(0xFF9A9B92), Offset(k.left, size.height - 3f), Offset(k.right, size.height - 3f), 3f)
            keyLabel(k.note)?.let { label(measurer, whiteLabels, it, (k.left + k.right) / 2, size.height - 8f, (whiteW * .43f).coerceAtMost(18.sp.toPx()), Color(0xFF31383B), k.width - 3f) }
        }
        for (k in g.keyboard.blackKeys) {
            val pos = Offset(k.left, hitY)
            val sz = Size(k.width, k.bottom)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF111416), Color(0xFF30363C), Color(0xFF080A0B)), hitY, hitY + k.bottom), pos, sz, CornerRadius(3f))
            keyColor(k)?.let { drawRoundRect(it, pos + Offset(2f, 0f), Size((k.width - 4f).coerceAtLeast(1f), k.bottom - 3f), CornerRadius(2f)) }
            drawLine(Color.White.copy(alpha = .16f), Offset(k.left + 2, hitY + 3), Offset(k.left + 2, hitY + k.bottom - 6), 1f)
            keyLabel(k.note)?.let { label(measurer, blackLabels, it, (k.left + k.right) / 2, hitY + k.bottom - 6f, (k.width * .4f).coerceAtMost(13.sp.toPx()), Color(0xFFF5F5EE), k.width - 3f) }
        }
        drawLine(Color(0xFFBE4B59), Offset(0f, hitY), Offset(size.width, hitY), 2f)
        // One pulse per judged event. Quiet accompaniment and ignored inputs never create a success pulse.
        for (e in feedback) {
            val age = e.age(now)
            if (age >= 1f) continue
            val k = g.byNote[e.midi] ?: continue
            val x = (k.left + k.right) / 2
            val color = FallingColors.feedback(e)
            val alpha = 1 - age
            val center = Offset(x, hitY - 3f)
            if (e.verdict == FallingVerdict.WRONG || e.verdict == FallingVerdict.MISS) {
                val r = (whiteW * .25f).coerceAtLeast(5f) + age * 10f
                val cy = hitY - 18f - age * 18f
                drawCircle(color.copy(alpha = alpha * .8f), r + 4f, Offset(x, cy), style = Stroke(1.5f))
                if (e.verdict == FallingVerdict.WRONG) {
                    drawLine(color.copy(alpha = alpha), Offset(x - r / 2, cy - r / 2), Offset(x + r / 2, cy + r / 2), 2.5f)
                    drawLine(color.copy(alpha = alpha), Offset(x - r / 2, cy + r / 2), Offset(x + r / 2, cy - r / 2), 2.5f)
                } else drawLine(color.copy(alpha = alpha), Offset(x - r / 2, cy), Offset(x + r / 2, cy), 2.5f)
            } else {
                val glowR = whiteW * (1f + age * .8f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = alpha * .8f), color.copy(alpha = alpha * .4f), color.copy(alpha = 0f)), center, glowR), glowR, center)
                drawCircle(color.copy(alpha = alpha), whiteW * (.2f + age * .7f), center, style = Stroke(2f * alpha + .5f))
                for (i in 0..6) {
                    val angle = (i * Math.PI / 6).toFloat()
                    val reach = whiteW * (.25f + age * 1.5f)
                    val p = Offset(x + cos(angle) * reach, hitY - sin(angle) * reach - age * 16f)
                    drawLine(Color.White.copy(alpha = alpha), p, p + Offset(cos(angle) * 4f, -sin(angle) * 6f), 1.5f)
                }
            }
        }
        feedback.lastOrNull { it.age(now) < 1f && it.midi in g.byNote }?.let { e ->
            val age = e.age(now)
            val textSize = 14.sp.toPx()
            val text = captionLabels.get(this, measurer, e.verdict.caption, textSize, FallingColors.ink)
            val k = g.byNote.getValue(e.midi)
            val centerX = ((k.left + k.right) / 2).coerceIn(text.size.width / 2f + 8f, (size.width - text.size.width / 2f - 8f).coerceAtLeast(text.size.width / 2f + 8f))
            val bottom = hitY - 38f - age * 20f
            drawRoundRect(FallingColors.feedback(e).copy(alpha = (1f - age) * .95f), Offset(centerX - text.size.width / 2f - 6f, bottom - text.size.height - 3f), Size(text.size.width + 12f, text.size.height + 6f), CornerRadius(5f))
            drawText(text, topLeft = Offset(centerX - text.size.width / 2f, bottom - text.size.height), alpha = 1f - age)
        }
        drawRect(Color(0xFF111518), size = Size(size.width, 4f))
        if (totalBeats > 0) drawRect(FallingColors.right, size = Size(size.width * (cursor / totalBeats).toFloat().coerceIn(0f, 1f), 4f))
    }
}
/** Bounded layouts, including fitted sizes for short notes and multi-character names. */
private class LabelCache {
    private data class Key(val text: String, val size: Float, val color: Color)
    private val layouts = HashMap<Key, TextLayoutResult>()

    fun get(scope: DrawScope, measurer: TextMeasurer, text: String, sizePx: Float, color: Color): TextLayoutResult {
        val px = round(sizePx * 2) / 2
        val key = Key(text, px, color)
        layouts[key]?.let { return it }
        if (layouts.size >= 256) layouts.clear()
        return measurer.measure(text, TextStyle(fontSize = with(scope) { px.toSp() }, fontWeight = FontWeight.SemiBold, color = color))
            .also { layouts[key] = it }
    }
}

private fun DrawScope.label(measurer: TextMeasurer, cache: LabelCache, text: String, centerX: Float, bottom: Float, sizePx: Float, color: Color, maxWidth: Float = Float.POSITIVE_INFINITY) {
    if (sizePx < 3f || maxWidth <= 0) return
    var layout = cache.get(this, measurer, text, sizePx, color)
    if (layout.size.width > maxWidth) {
        val fitted = sizePx * maxWidth / layout.size.width
        if (fitted < 2f) return
        layout = cache.get(this, measurer, text, fitted, color)
    }
    drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, bottom - layout.size.height))
}
