package com.id.mimikopi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.keyboard.KeyboardLayout
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.music.isBlackKey
import com.id.mimikopi.ui.theme.Tokens

/** True while a MIDI piano is connected and open (USB or Bluetooth). */
@Composable
fun midiConnected(): Boolean {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.id.mimikopi.MimikopiApp
    val devices by app.midiInput.devices.collectAsState()
    return devices.any { it.open }
}

/**
 * Multi-touch piano keyboard. Each finger is tracked separately; sliding a finger across keys plays
 * a glissando. Notes are reported once per key even if two fingers hold the same key.
 * Note names are hidden unless [showNames] is on (reading is practised from day one).
 */
@Composable
fun PianoKeyboard(
    lowNote: Int,
    highNote: Int,
    pressed: Set<Int>,
    showNames: Boolean,
    nameStyle: NoteNameStyle,
    /** Note and touch depth on the key (0 = back/top, 1 = front edge). */
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Keys to point out (e.g. the right answer after a mistake, or keys being demonstrated). */
    marked: Set<Int> = emptySet(),
    /** Notes whose names are shown even when [showNames] is off (e.g. confirming a correct key). */
    namedNotes: Set<Int> = emptySet(),
    /** With a MIDI piano connected the real keys are used: the on-screen keyboard steps aside (not on the piano screen). */
    hideWithMidi: Boolean = true,
) {
    if (hideWithMidi && midiConnected()) {
        // Keys that would be pointed out (an answer, a given first note) are named instead.
        if (marked.isNotEmpty()) androidx.compose.material3.Text(
            marked.sorted().joinToString("  ·  ") { SpelledPitch.fromMidiSharp(it).name(nameStyle, withOctave = true) },
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = Tokens.Palette.RoseDeep,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        return
    }
    val noteOn by rememberUpdatedState(onNoteOn)
    val noteOff by rememberUpdatedState(onNoteOff)
    val measurer = rememberTextMeasurer()
    val whiteCount = remember(lowNote, highNote) { KeyboardLayout(lowNote, highNote, 1f, 1f).whiteKeys.size }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(lowNote, highNote) {
                awaitPointerEventScope {
                    val fingers = mutableMapOf<PointerId, Int>()
                    val holdCount = IntArray(128)
                    while (true) {
                        val event = awaitPointerEvent()
                        val layout = KeyboardLayout(lowNote, highNote, size.width.toFloat() / whiteCount, size.height.toFloat())
                        for (change in event.changes) {
                            val now = if (change.pressed) layout.hitTest(change.position.x, change.position.y) else null
                            val before = fingers[change.id]
                            if (now != before) {
                                if (before != null && --holdCount[before] == 0) noteOff(before)
                                if (now != null && holdCount[now]++ == 0) {
                                    val bottom = if (isBlackKey(now)) layout.height * layout.blackHeightRatio else layout.height
                                    noteOn(now, change.position.y / bottom)
                                }
                                if (now == null) fingers.remove(change.id) else fingers[change.id] = now
                            }
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val layout = KeyboardLayout(lowNote, highNote, size.width / whiteCount, size.height)
        val radius = CornerRadius(layout.whiteKeyWidth * 0.12f)
        for (k in layout.whiteKeys) {
            val down = k.note in pressed
            drawRoundRect(
                color = when {
                    down -> Tokens.Palette.RoseSoft
                    k.note in marked -> Tokens.Palette.MustardSoft
                    else -> Color.White
                },
                topLeft = Offset(k.left + 1f, 0f),
                size = Size(k.width - 2f, k.bottom),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = Tokens.Palette.Line,
                topLeft = Offset(k.left + 1f, 0f),
                size = Size(k.width - 2f, k.bottom),
                cornerRadius = radius,
                style = Stroke(width = 2f),
            )
            if (showNames || k.note in namedNotes) {
                val label = SpelledPitch.fromMidiSharp(k.note).name(nameStyle, withOctave = k.note % 12 == 0)
                val text = measurer.measure(label, TextStyle(fontSize = 13.sp, color = Tokens.Palette.InkMuted))
                drawText(text, topLeft = Offset(k.left + (k.width - text.size.width) / 2, k.bottom - text.size.height - 8f))
            } else if (k.note == 60) {
                // A small dot marks Middle C, like a sticker on a real piano; it is not a name.
                drawCircle(Tokens.Palette.Mustard, radius = layout.whiteKeyWidth * 0.08f, center = Offset(k.left + k.width / 2, k.bottom - layout.whiteKeyWidth * 0.3f))
            }
        }
        for (k in layout.blackKeys) {
            val down = k.note in pressed
            drawRoundRect(
                color = when {
                    down -> Tokens.Palette.Rose
                    k.note in marked -> Tokens.Palette.Mustard
                    else -> Tokens.Palette.Ink
                },
                topLeft = Offset(k.left, 0f),
                size = Size(k.width, k.bottom),
                cornerRadius = CornerRadius(k.width * 0.15f),
            )
        }
    }
}
