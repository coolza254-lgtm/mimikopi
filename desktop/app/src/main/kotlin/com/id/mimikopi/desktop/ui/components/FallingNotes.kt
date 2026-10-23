package com.id.mimikopi.desktop.ui.components

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
import com.id.mimikopi.desktop.session.FallingFeedback
import com.id.mimikopi.desktop.session.FallingVerdict
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * Colours of the falling-notes stage (the tutorial-video look the user asked for): a dark stage, blue for the left
 * hand and green for the right, each a light shade on white keys and a deep shade on black keys.
 */
internal object FallingColors {
    val stage = Color(0xFF2B2B2B)
    // Approved design: right hand pink, left hand yellow, each bar a soft gradient on a dark warm stage.
    val stageTop = Color(0xFF3A2A33)
    val stageBottom = Color(0xFF141113)
    val leftWhite = Color(0xFFFFE29A)
    val leftBlack = Color(0xFFF3BE52)
    val rightWhite = Color(0xFFF7B2C4)
    val rightBlack = Color(0xFFE6889E)
    val missed = Color(0xFF8A8486)
    val hitLine = Color(0xFFF7B2C4)
    val hitLineEnd = Color(0xFFFFE29A)
    val timing = Color(0xFFFFC56B)
    val error = Color(0xFFFF6577)
    val ink = Color(0xFF16251B)
    val outline = Color(0xFF1C1C1C)
    fun hand(leftHand: Boolean, black: Boolean = false) = when {
        leftHand -> if (black) leftBlack else leftWhite
        else -> if (black) rightBlack else rightWhite
    }
    fun feedback(event: FallingFeedback) = when (event.verdict) {
        FallingVerdict.HIT, FallingVerdict.CORRECTED -> hand(event.left)
        FallingVerdict.EARLY, FallingVerdict.LATE, FallingVerdict.WRONG, FallingVerdict.MISS -> missed
    }
    fun darker(c: Color, f: Float = 0.68f) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)
}

@Composable
fun FallingNotesView(
    notes: List<FallingNote>, cursor: (Long) -> Double, animate: Boolean,
    keyRange: IntRange, pressed: Set<Int>, keyLabel: (Int) -> String?,
    modifier: Modifier = Modifier, window: Double = 8.0, smooth: Boolean = false,
    lines: List<Double> = emptyList(), feedback: List<FallingFeedback> = emptyList(),
    totalBeats: Double = 0.0,
    onKey: ((Int, Boolean) -> Unit)? = null,
    /** Shown small at the lower left of the stage, e.g. the key of the piece ("A♭ Major"). */
    caption: String? = null,
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
    val touch = if (onKey == null) Modifier else Modifier.pointerKeys(keyRange, { x, y, w, h ->
        renderer.geometry(w, h).hit(x, y)
    }, onKey)
    Canvas(modifier.then(touch)) {
        val target = cursor(frame)
        val dt = ((frame - shown[1]) / 1e9).coerceIn(0.0, .1)
        shown[1] = frame.toDouble()
        val cur = if (!smooth || !animate || shown[0].isNaN() || abs(target - shown[0]) > window) target
        else shown[0] + (target - shown[0]) * (1 - exp(-dt * 14))
        shown[0] = cur
        renderer.draw(this, sorted, longest, cur, window, lines, pressed, keyLabel, feedback, frame, totalBeats, caption)
    }
}

/** Shared by the live Canvas and offscreen rendering checks. Keeps text and geometry out of the frame loop. */
internal class FallingRenderer(private val range: IntRange, private val measurer: TextMeasurer) {
    private var dimensions = Size.Unspecified
    private var layout: FallingGeometry? = null
    private val barLabels = LabelCache()
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
             feedback: List<FallingFeedback>, now: Long, totalBeats: Double, caption: String? = null) = with(scope) {
        if (size.width <= 0 || size.height <= 0) return@with
        val g = geometry(size.width, size.height)
        val hitY = g.hitY
        val unit = hitY / window.coerceAtLeast(.1).toFloat()
        val whiteW = g.whiteWidth
        drawRect(Brush.radialGradient(listOf(FallingColors.stageTop, FallingColors.stageBottom), Offset(size.width / 2, hitY * .3f),
            maxOf(size.width, hitY) * .8f), size = Size(size.width, hitY))
        clipRect(bottom = hitY) {
            // A faint line at every C and F, as on the keyboard's groups of two and three black keys.
            for (k in g.keyboard.whiteKeys) {
                val pc = k.note % 12
                drawLine(Color.White.copy(alpha = if (pc == 0) .12f else .04f), Offset(k.left, 0f), Offset(k.left, hitY), 1f)
            }
            for (b in lines) {
                val y = hitY - ((b - cursor) * unit).toFloat()
                if (y in 0f..hitY) drawLine(Color.White.copy(alpha = .06f), Offset(0f, y), Offset(size.width, y))
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
                val w = k.width * if (k.black) .9f else .84f
                val x = k.left + (k.width - w) / 2
                val t = top.coerceAtLeast(-20f)
                val h = (bottom - t).coerceAtLeast(2f)
                val color = FallingColors.hand(n.left, k.black)
                val radius = CornerRadius((w * .28f).coerceAtMost(10f))
                when (n.state) {
                    FallingState.BACKGROUND -> {
                        drawRoundRect(color.copy(alpha = .38f), Offset(x, t), Size(w, h), radius)
                        drawRoundRect(color.copy(alpha = .6f), Offset(x, t), Size(w, h), radius, style = Stroke(1.5f))
                    }
                    // A missed or off-time note just fades to grey: no cross.
                    FallingState.MISSED -> drawRoundRect(FallingColors.missed.copy(alpha = .4f), Offset(x, t), Size(w, h), radius)
                    else -> {
                        val alpha = if (n.state == FallingState.DONE) .28f else 1f
                        val light = FallingColors.hand(n.left, false)
                        val deep = FallingColors.hand(n.left, true)
                        if (n.state == FallingState.WAITING) drawRoundRect(light.copy(alpha = .22f), Offset(x - 3f, t - 3f), Size(w + 6f, h + 6f), CornerRadius(radius.x + 3f))
                        drawRoundRect(Brush.verticalGradient(listOf(light.copy(alpha = alpha), deep.copy(alpha = alpha)), t, t + h), Offset(x, t), Size(w, h), radius)
                    }
                }
                if (n.state == FallingState.WAITING && h > 12f && n.label.isNotEmpty()) {
                    val textSize = if (k.black) (w * .62f).coerceAtMost(15.sp.toPx()) else (w * .72f).coerceAtMost(24.sp.toPx())
                    outlinedLabel(measurer, barLabels, n.label, x + w / 2, bottom - 4f, textSize.coerceAtMost((h - 4f) * .8f), w + 8f)
                }
            }
        }
        latest.fill(null)
        for (e in feedback) if (e.midi in 0..127) latest[e.midi] = e
        fun keyColor(k: KeyRect): Color? {
            val e = latest[k.note]
            if (e != null && e.age(now) < 1f) {
                val c = if (e.verdict == FallingVerdict.HIT || e.verdict == FallingVerdict.CORRECTED) FallingColors.hand(e.left, k.black) else FallingColors.feedback(e)
                return c.copy(alpha = (1f - e.age(now)) * .6f + .4f)
            }
            return if (k.note in pressed) (if (k.black) Color(0xFF5E6A73) else Color(0xFFCFD6DA)) else null
        }
        for (k in g.keyboard.whiteKeys) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFFEF6), Color(0xFFF1EEDC)), hitY, size.height), Offset(k.left, hitY), Size(k.width, g.keyHeight))
            keyColor(k)?.let { drawRect(it, Offset(k.left + 1f, hitY), Size(k.width - 2f, g.keyHeight)) }
            drawLine(Color(0xFF8F8B7A), Offset(k.left, hitY), Offset(k.left, size.height), 1.2f)
            drawLine(Color(0xFFC9C5B2), Offset(k.left, size.height - 3f), Offset(k.right, size.height - 3f), 3f)
            keyLabel(k.note)?.let { label(measurer, whiteLabels, it, (k.left + k.right) / 2, size.height - 10f, (whiteW * .5f).coerceAtMost(22.sp.toPx()), Color(0xFF2B2B2B), k.width - 3f) }
        }
        for (k in g.keyboard.blackKeys) {
            val pos = Offset(k.left, hitY)
            val sz = Size(k.width, k.bottom)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2C2C2C), Color(0xFF151515), Color(0xFF050505)), hitY, hitY + k.bottom), pos, sz, CornerRadius(3f))
            keyColor(k)?.let { drawRoundRect(it, pos + Offset(2f, 0f), Size((k.width - 4f).coerceAtLeast(1f), k.bottom - 4f), CornerRadius(2f)) }
            drawLine(Color.White.copy(alpha = .18f), Offset(k.left + 2, hitY + 3), Offset(k.left + 2, hitY + k.bottom - 8), 1.2f)
            drawRect(Color.White.copy(alpha = .10f), Offset(k.left + 2f, hitY + k.bottom - 8f), Size(k.width - 4f, 4f))
            keyLabel(k.note)?.let { label(measurer, blackLabels, it, (k.left + k.right) / 2, hitY + k.bottom - 12f, (k.width * .5f).coerceAtMost(13.sp.toPx()), Color(0xFFF5F5EE), k.width + 6f) }
        }
        // The line the notes are played at.
        val line = Brush.horizontalGradient(listOf(FallingColors.hitLine, FallingColors.hitLineEnd))
        drawLine(line, Offset(0f, hitY - 2f), Offset(size.width, hitY - 2f), 10f, alpha = .25f)
        drawLine(line, Offset(0f, hitY), Offset(size.width, hitY), 3f)
        caption?.let { label(measurer, captionLabels, it, 0f, hitY - 8f, 13.sp.toPx(), Color.White.copy(alpha = .6f), alignLeft = 10f) }
        // One pulse per judged event. Quiet accompaniment and ignored inputs never create a success pulse.
        for (e in feedback) {
            val age = e.age(now)
            if (age >= 1f) continue
            val k = g.byNote[e.midi] ?: continue
            val x = (k.left + k.right) / 2
            val color = FallingColors.feedback(e)
            val alpha = 1 - age
            val center = Offset(x, hitY - 3f)
            if (e.verdict == FallingVerdict.WRONG || e.verdict == FallingVerdict.MISS || e.verdict == FallingVerdict.EARLY || e.verdict == FallingVerdict.LATE) {
                // Off: the bar itself greys out; nothing pops.
            } else {
                val glowR = whiteW * (1f + age * .8f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = alpha * .8f), color.copy(alpha = alpha * .4f), color.copy(alpha = 0f)), center, glowR), glowR, center)
                drawCircle(color.copy(alpha = alpha), whiteW * (.2f + age * .7f), center, style = Stroke(2f * alpha + .5f))
                for (i in 0 until 6) {
                    val angle = (Math.PI / 12 + i * Math.PI / 6).toFloat()
                    val reach = whiteW * (.25f + age * 1.5f)
                    val p = Offset(x + cos(angle) * reach, hitY - sin(angle) * reach - age * 16f)
                    drawLine(Color.White.copy(alpha = alpha), p, p + Offset(cos(angle) * 4f, -sin(angle) * 6f), 1.5f)
                }
            }
        }
        drawRect(Color(0xFF1C1C1C), size = Size(size.width, 4f))
        if (totalBeats > 0) drawRect(Brush.horizontalGradient(listOf(FallingColors.hitLine, FallingColors.hitLineEnd)), size = Size(size.width * (cursor / totalBeats).toFloat().coerceIn(0f, 1f), 4f))
    }
}
/** Bounded layouts, including fitted sizes for short notes and multi-character names; [outline] > 0 draws a text outline. */
private class LabelCache {
    private data class Key(val text: String, val size: Float, val color: Color, val outline: Float)
    private val layouts = HashMap<Key, TextLayoutResult>()

    fun get(scope: DrawScope, measurer: TextMeasurer, text: String, sizePx: Float, color: Color, outline: Float = 0f): TextLayoutResult {
        val px = round(sizePx * 2) / 2
        val key = Key(text, px, color, outline)
        layouts[key]?.let { return it }
        if (layouts.size >= 256) layouts.clear()
        val style = TextStyle(fontSize = with(scope) { px.toSp() }, fontWeight = FontWeight.Bold, color = color)
        return measurer.measure(text, if (outline > 0f) style.copy(drawStyle = Stroke(width = outline, join = androidx.compose.ui.graphics.StrokeJoin.Round)) else style)
            .also { layouts[key] = it }
    }
}

private fun DrawScope.label(measurer: TextMeasurer, cache: LabelCache, text: String, centerX: Float, bottom: Float, sizePx: Float, color: Color,
                            maxWidth: Float = Float.POSITIVE_INFINITY, alignLeft: Float? = null) {
    if (sizePx < 3f || maxWidth <= 0) return
    var layout = cache.get(this, measurer, text, sizePx, color)
    if (layout.size.width > maxWidth) {
        val fitted = sizePx * maxWidth / layout.size.width
        if (fitted < 2f) return
        layout = cache.get(this, measurer, text, fitted, color)
    }
    val x = alignLeft ?: (centerX - layout.size.width / 2f)
    drawText(layout, topLeft = Offset(x, bottom - layout.size.height))
}

/** A note name on a bar: white letters with a dark outline, readable on both shades of both hands. */
private fun DrawScope.outlinedLabel(measurer: TextMeasurer, cache: LabelCache, text: String, centerX: Float, bottom: Float, sizePx: Float, maxWidth: Float) {
    if (sizePx < 4f) return
    var size = sizePx
    var fill = cache.get(this, measurer, text, size, Color.White)
    if (fill.size.width > maxWidth) {
        size = sizePx * maxWidth / fill.size.width
        if (size < 4f) return
        fill = cache.get(this, measurer, text, size, Color.White)
    }
    val outline = cache.get(this, measurer, text, size, FallingColors.outline, outline = (size * .16f).coerceAtLeast(1.5f))
    val topLeft = Offset(centerX - fill.size.width / 2f, bottom - fill.size.height)
    drawText(outline, topLeft = topLeft)
    drawText(fill, topLeft = topLeft)
}

/** Mouse (and touch) on the drawn keys: press plays, dragging slides to the next key, release stops. */
private fun Modifier.pointerKeys(key: Any, hit: (x: Float, y: Float, width: Float, height: Float) -> Int?, play: (Int, Boolean) -> Unit): Modifier =
    pointerInput(key) {
        awaitPointerEventScope {
            val fingers = mutableMapOf<PointerId, Int>()
            val holds = IntArray(128)
            try { while (true) {
                val event = awaitPointerEvent()
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                for (change in event.changes) {
                    val now = if (change.pressed) hit(change.position.x, change.position.y, w, h) else null
                    val before = fingers[change.id]
                    if (now != before) {
                        if (before != null && --holds[before] == 0) play(before, false)
                        if (now != null && holds[now]++ == 0) play(now, true)
                        if (now == null) fingers.remove(change.id) else fingers[change.id] = now
                    }
                    change.consume()
                }
            } } finally {
                holds.forEachIndexed { note, count -> if (count > 0) play(note, false) }
            }
        }
    }
