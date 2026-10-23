package com.id.mimikopi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.keyboard.KeyboardLayout
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.ui.theme.Tokens

/** True while a MIDI piano is connected, open and (as far as the app can tell) switched on. */
@Composable
fun midiConnected(): Boolean {
    val app = LocalContext.current.applicationContext as MimikopiApp
    val devices by app.midiInput.devices.collectAsState()
    return devices.any { it.ready }
}

/** True while a MIDI piano is connected by USB cable: the only input whose timing is judged (learning-system.md 2.11). */
@Composable
fun cableConnected(): Boolean {
    val app = LocalContext.current.applicationContext as MimikopiApp
    val devices by app.midiInput.devices.collectAsState()
    return devices.any { it.ready && it.source == com.id.mimikopi.core.input.InputSource.USB_MIDI }
}

/** Two octaves from C3: the on-screen keyboard where a screen has no range of its own. */
val DEFAULT_KEY_RANGE = 48..72

/**
 * The keys at the bottom of a practice screen. With a MIDI piano connected the real keys are used and only the keys
 * the app points out ([marked]: an answer after a mistake, a given first note) are named in large type. Without one,
 * an on-screen keyboard over [range] can be played to try things out ([keyboard] off where the screen already draws
 * playable keys, e.g. falling notes).
 */
@Composable
fun KeyHint(
    marked: Set<Int>,
    nameStyle: NoteNameStyle,
    modifier: Modifier = Modifier,
    range: IntRange = DEFAULT_KEY_RANGE,
    showNames: Boolean = false,
    keyboard: Boolean = true,
    keysHeight: Dp = 150.dp,
) {
    val connected = midiConnected()
    if (marked.isNotEmpty()) Text(
        marked.sorted().joinToString("  ·  ") { SpelledPitch.fromMidiSharp(it).name(nameStyle, withOctave = true) },
        style = MaterialTheme.typography.headlineSmall,
        color = Tokens.Palette.Ink,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    )
    if (!connected && keyboard) {
        Text(
            stringResource(R.string.midi_needed),
            style = MaterialTheme.typography.bodySmall,
            color = Tokens.Palette.InkMuted,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // Keys the app points out are always on the keyboard.
        val keys = if (marked.all { it in range }) range else fallingKeyRange(minOf(range.first, marked.min()), maxOf(range.last, marked.max()))
        TouchKeyboard(keys, marked, showNames, nameStyle, modifier.fillMaxWidth().widthIn(max = 900.dp).height(keysHeight).padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/**
 * Plays notes from touches (or the mouse) into the same input stream as a MIDI piano, so every exercise judges them
 * the same way. Each finger is tracked; sliding across keys plays each key in turn. The notes still held when the
 * keys leave the screen are released.
 */
@Composable
fun rememberTouchPlayer(): (note: Int, down: Boolean) -> Unit {
    val app = LocalContext.current.applicationContext as MimikopiApp
    val hub = app.inputHub
    val held = remember { HashSet<Int>() }
    DisposableEffect(hub) {
        onDispose {
            held.forEach { hub.dispatch(InputEvent.NoteOff(it, System.nanoTime(), InputSource.TOUCH)) }
            held.clear()
        }
    }
    return remember(hub) {
        { note, down ->
            val now = System.nanoTime()
            if (down) { held += note; hub.dispatch(InputEvent.NoteOn(note, TOUCH_VELOCITY, now, InputSource.TOUCH)) }
            else if (held.remove(note)) { hub.dispatch(InputEvent.NoteOff(note, now, InputSource.TOUCH)) }
        }
    }
}

/** A medium touch: touches carry no strength. */
private const val TOUCH_VELOCITY = 80

/**
 * Tracks fingers over keys ([hit] finds the key under a point for the size at hand, or null) and reports each key
 * once, even when two fingers hold it.
 */
fun Modifier.touchKeys(key: Any, hit: (x: Float, y: Float, width: Float, height: Float) -> Int?, play: (Int, Boolean) -> Unit): Modifier =
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

/**
 * The on-screen keyboard (minimal, user-approved 2026-10-23): flat white keys with thin gaps and flat ink black keys on
 * the page itself, no case, felt or gradients; held keys in the accent's pinks with their name, [marked] keys outlined,
 * Middle C with a dot.
 */
@Composable
fun TouchKeyboard(range: IntRange, marked: Set<Int>, showNames: Boolean, nameStyle: NoteNameStyle, modifier: Modifier) {
    val app = LocalContext.current.applicationContext as MimikopiApp
    val pressed by app.inputHub.pressed.collectAsState()
    val play = rememberTouchPlayer()
    TouchKeyboardSurface(range, marked, showNames, nameStyle, pressed, play, modifier)
}

/** The actual touch geometry and Canvas, independent of the application container. */
@Composable
fun TouchKeyboardSurface(range: IntRange, marked: Set<Int>, showNames: Boolean, nameStyle: NoteNameStyle,
    pressed: Set<Int>, play: (Int, Boolean) -> Unit, modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val whites = remember(range) { KeyboardLayout(range.first, range.last, 1f, 1f).whiteKeys.size }
    // The layout for the current size, kept between touches and frames; built again only when the keys are resized.
    val cached = remember(range) { arrayOfNulls<KeyboardLayout>(1) }
    fun layoutFor(w: Float, h: Float): KeyboardLayout {
        val last = cached[0]
        if (last != null && last.whiteKeyWidth == w / whites && last.height == h) return last
        return KeyboardLayout(range.first, range.last, w / whites, h).also { cached[0] = it }
    }

    Box(modifier.touchKeys(range, { x, y, w, h -> layoutFor(w, h).hitTest(x.coerceIn(0f, w - 1f), y.coerceIn(0f, h - 1f)) }, play)) {
        Canvas(Modifier.fillMaxSize()) {
            val layout = layoutFor(size.width, size.height)
            val radius = CornerRadius(layout.whiteKeyWidth * 0.16f)
            val gap = 1.5.dp.toPx()
            for (k in layout.whiteKeys) {
                val down = k.note in pressed
                val topLeft = Offset(k.left + gap, 0f)
                val keySize = Size(k.width - 2 * gap, k.bottom)
                drawRoundRect(if (down) Tokens.Palette.AccentSoft else Tokens.Piano.WhiteTop, topLeft, keySize, radius)
                if (k.note in marked) drawRoundRect(Tokens.Palette.Accent, topLeft, keySize, radius, style = Stroke(width = 5f))
                if (showNames || k.note in marked || down) {
                    val label = SpelledPitch.fromMidiSharp(k.note).name(nameStyle, withOctave = k.note % 12 == 0)
                    val strong = down || k.note in marked
                    val text = measurer.measure(label, TextStyle(fontSize = 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                        color = if (strong) Tokens.Palette.AccentDeep else Tokens.Palette.InkMuted))
                    drawText(text, topLeft = Offset(k.left + (k.width - text.size.width) / 2, k.bottom - text.size.height - 8f))
                } else if (k.note == 60) {
                    // A small dot marks Middle C, like a sticker on a real piano.
                    drawCircle(Tokens.Palette.Dim, radius = layout.whiteKeyWidth * 0.07f, center = Offset(k.left + k.width / 2, k.bottom - layout.whiteKeyWidth * 0.3f))
                }
            }
            for (k in layout.blackKeys) {
                val down = k.note in pressed
                val r = CornerRadius(k.width * 0.22f)
                drawRoundRect(if (down) Tokens.Palette.AccentDeep else Tokens.Palette.Ink, Offset(k.left, 0f), Size(k.width, k.bottom), r)
                if (k.note in marked) drawRoundRect(
                    Tokens.Palette.AccentMid, Offset(k.left + 3f, 3f), Size(k.width - 6f, k.bottom - 6f), CornerRadius(k.width * 0.18f), style = Stroke(width = 3f),
                )
            }
        }
    }
}
