package com.id.mimikopi.desktop.ui.components

import androidx.compose.foundation.Canvas
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
    Canvas(
        modifier.fillMaxWidth().height(height).pointerInput(range) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val g = KeyGeometry(range, size.width.toFloat(), size.height.toFloat())
                    for (c in event.changes) {
                        val id = c.id.value
                        when {
                            event.type == PointerEventType.Press || (c.pressed && !c.previousPressed) -> {
                                g.noteAt(c.position.x, c.position.y)?.let { n -> holding[id] = n; key(n, true) }
                                c.consume()
                            }
                            !c.pressed && c.previousPressed -> {
                                holding.remove(id)?.let { key(it, false) }
                                c.consume()
                            }
                            c.pressed -> {
                                // Sliding along the keys moves the note (a glissando), like a real keyboard.
                                val n = g.noteAt(c.position.x, c.position.y)
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
        },
    ) {
        val g = KeyGeometry(range, size.width, size.height)
        // Keys look like keys in both looks (as on the phone): white and black, pressed ones grey.
        val whiteKey = if (Tokens.dark) Color(0xFFE6E6E6) else Color(0xFFFFFFFF)
        val keyText = Color(0xFF5E5E5E)
        val labelStyle = TextStyle(fontSize = (g.whiteW * 0.28f).coerceIn(9f, 16f).toSp())
        for (n in g.whites) {
            val x = g.whiteX(n)
            val on = n in pressed
            val mark = n in marked
            drawRoundRect(if (on) Color(0xFF8C8C8C) else if (mark) Color(0xFFCFCFCF) else whiteKey, Offset(x + 1f, 0f), Size(g.whiteW - 2f, size.height), CornerRadius(6f, 6f))
            drawRoundRect(if (mark) Color(0xFF000000) else Color(0xFF9E9E9E), Offset(x + 1f, 0f), Size(g.whiteW - 2f, size.height), CornerRadius(6f, 6f), style = Stroke(if (mark) 2f else 1f))
            val text = buildList {
                labels?.let { add(SpelledPitch.fromMidiSharp(n).name(it)) }
                computerKeysLowC?.let { ComputerKeyboard.labelFor(n, it)?.let(::add) }
            }.joinToString("\n")
            if (text.isNotEmpty()) {
                val t = measurer.measure(text, labelStyle)
                drawText(t, color = if (on) Color.White else keyText, topLeft = Offset(x + (g.whiteW - t.size.width) / 2f, size.height - t.size.height - 6f))
            }
            if (n == 60) drawCircle(keyText, radius = 3f, center = Offset(x + g.whiteW / 2f, size.height * 0.68f))
        }
        for (n in range) {
            if (n % 12 !in BLACK || n - 1 !in range) continue
            val x = g.blackX(n)
            val on = n in pressed
            val mark = n in marked
            drawRoundRect(if (on) Color(0xFF7A7A7A) else Color(0xFF151515), Offset(x, 0f), Size(g.blackW, g.blackH), CornerRadius(4f, 4f))
            if (mark && !on) drawRoundRect(Color.White, Offset(x + 3f, 3f), Size(g.blackW - 6f, g.blackH - 6f), CornerRadius(3f, 3f), style = Stroke(2f))
            computerKeysLowC?.let { ComputerKeyboard.labelFor(n, it) }?.let { label ->
                val t = measurer.measure(label, labelStyle)
                drawText(t, color = Color.White, topLeft = Offset(x + (g.blackW - t.size.width) / 2f, g.blackH - t.size.height - 4f))
            }
        }
    }
}
