package com.id.mimikopi.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.desktop.input.ComputerKeyboard
import com.id.mimikopi.desktop.ui.theme.Tokens

private val BLACK = setOf(1, 3, 6, 8, 10)

/** Geometry of the white and black keys of [range] in a box [width] wide. */
private class KeyGeometry(val range: IntRange, width: Float, height: Float) {
    val whites = range.filter { it % 12 !in BLACK }
    val whiteW = width / whites.size.coerceAtLeast(1)
    val blackW = whiteW * 0.62f
    val blackH = height * 0.62f
    private val whiteIndex = whites.withIndex().associate { (i, n) -> n to i }

    fun whiteX(note: Int) = (whiteIndex[note] ?: 0) * whiteW

    fun blackX(note: Int): Float = whiteX(note - 1) + whiteW - blackW / 2f

    fun noteAt(x: Float, y: Float): Int? {
        if (y < blackH) for (n in range) if (n % 12 in BLACK && n - 1 in range) {
            val bx = blackX(n)
            if (x in bx..bx + blackW) return n
        }
        val i = (x / whiteW).toInt()
        return whites.getOrNull(i)
    }
}

/**
 * The on-screen keys: played with the mouse (or a touch screen), and lit by every input source. Notes the page wants
 * shown are [marked]; [labels] adds note names, and [computerKeysLowC] shows which typing keys play each note.
 * Pages hide these keys while a MIDI piano is connected, so the music gets the room.
 */
@Composable
fun PianoKeys(
    range: IntRange,
    pressed: Set<Int>,
    onKey: (note: Int, down: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    marked: Set<Int> = emptySet(),
    labels: NoteNameStyle? = null,
    computerKeysLowC: Int? = null,
    height: Dp = 150.dp,
) {
    val measurer = rememberTextMeasurer()
    val key by rememberUpdatedState(onKey)
    val holding = remember { HashMap<Long, Int>() }
    val caseShape = RoundedCornerShape(20.dp)
    Box(
        // Touches on the case (its rounded corners too, so before the clip) count as the nearest key.
        modifier.fillMaxWidth().height(height).pointerInput(range) {
            val side = 6.dp.toPx()
            val top = 10.dp.toPx()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val kw = size.width - 2 * side
                    val kh = size.height - top - side
                    val g = KeyGeometry(range, kw, kh)
                    fun at(p: Offset) = g.noteAt((p.x - side).coerceIn(0f, kw - 1f), (p.y - top).coerceIn(0f, kh - 1f))
                    for (c in event.changes) {
                        val id = c.id.value
                        when {
                            event.type == PointerEventType.Press || (c.pressed && !c.previousPressed) -> {
                                at(c.position)?.let { n -> holding[id] = n; key(n, true) }
                                c.consume()
                            }
                            !c.pressed && c.previousPressed -> {
                                holding.remove(id)?.let { key(it, false) }
                                c.consume()
                            }
                            c.pressed -> {
                                // Sliding along the keys moves the note (a glissando), like a real keyboard.
                                val n = at(c.position)
                                val old = holding[id]
                                if (n != null && n != old) {
                                    old?.let { key(it, false) }
                                    holding[id] = n
                                    key(n, true)
                                }
                            }
                        }
                    }
                }
            }
        }
            .shadow(Tokens.Elevation.keyboard, caseShape, ambientColor = Tokens.Elevation.tint, spotColor = Tokens.Elevation.tint)
            .clip(caseShape).background(Tokens.Piano.Case).padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
    ) {
        // The felt strip above the keys.
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-6).dp).fillMaxWidth().padding(horizontal = 18.dp).height(3.dp)
            .clip(RoundedCornerShape(2.dp)).background(Tokens.Piano.Felt))
        Canvas(Modifier.fillMaxSize()) {
            val g = KeyGeometry(range, size.width, size.height)
            // Design J keys (as on the phone): white keys fading at the bottom, graded black keys, held keys in the pinks.
            val white = Brush.verticalGradient(0.75f to Tokens.Piano.WhiteTop, 1f to Tokens.Piano.WhiteBottom)
            val black = Brush.verticalGradient(listOf(Tokens.Piano.BlackTop, Tokens.Piano.BlackBottom))
            val keyText = Tokens.Palette.InkMuted
            val labelStyle = TextStyle(fontSize = (g.whiteW * 0.28f).coerceIn(9f, 16f).toSp())
            for (n in g.whites) {
                val x = g.whiteX(n)
                val on = n in pressed
                val mark = n in marked
                val r = CornerRadius(g.whiteW * 0.14f)
                if (on) drawRoundRect(Tokens.Palette.AccentSoft, Offset(x + 1.5f, 0f), Size(g.whiteW - 3f, size.height), r)
                else drawRoundRect(white, Offset(x + 1.5f, 0f), Size(g.whiteW - 3f, size.height), r)
                if (mark) drawRoundRect(Tokens.Palette.Accent, Offset(x + 1.5f, 0f), Size(g.whiteW - 3f, size.height), r, style = Stroke(4f))
                val text = buildList {
                    labels?.let { add(SpelledPitch.fromMidiSharp(n).name(it)) }
                    computerKeysLowC?.let { ComputerKeyboard.labelFor(n, it)?.let(::add) }
                }.joinToString("\n")
                if (text.isNotEmpty()) {
                    val t = measurer.measure(text, labelStyle)
                    drawText(t, color = if (on || mark) Tokens.Palette.AccentDeep else keyText, topLeft = Offset(x + (g.whiteW - t.size.width) / 2f, size.height - t.size.height - 6f))
                }
                if (n == 60) drawCircle(keyText, radius = 3f, center = Offset(x + g.whiteW / 2f, size.height * 0.68f))
            }
            for (n in range) {
                if (n % 12 !in BLACK || n - 1 !in range) continue
                val x = g.blackX(n)
                val on = n in pressed
                val mark = n in marked
                val r = CornerRadius(g.blackW * 0.22f)
                if (on) drawRoundRect(Tokens.Palette.AccentDeep, Offset(x, 0f), Size(g.blackW, g.blackH), r)
                else drawRoundRect(black, Offset(x, 0f), Size(g.blackW, g.blackH), r)
                if (mark && !on) drawRoundRect(Tokens.Palette.AccentMid, Offset(x + 3f, 3f), Size(g.blackW - 6f, g.blackH - 6f), CornerRadius(g.blackW * 0.18f), style = Stroke(2.5f))
                computerKeysLowC?.let { ComputerKeyboard.labelFor(n, it) }?.let { label ->
                    val t = measurer.measure(label, labelStyle)
                    drawText(t, color = Color.White, topLeft = Offset(x + (g.blackW - t.size.width) / 2f, g.blackH - t.size.height - 4f))
                }
            }
        }
    }
}
