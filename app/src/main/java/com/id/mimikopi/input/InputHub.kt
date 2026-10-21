package com.id.mimikopi.input

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.Transposer
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.piano.NoteOutput
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single input stream (PROMPT.md section 6). Every source (on-screen keys, USB/BLE MIDI, the
 * debug simulator) calls [dispatch]; sound is triggered here immediately on the caller's thread, and
 * everything else (keyboard highlights, monitor, calibration, scoring) observes [events].
 */
class InputHub(private val piano: NoteOutput) {
    private val _events = MutableSharedFlow<InputEvent>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<InputEvent> = _events.asSharedFlow()

    private val _pressed = MutableStateFlow<Set<Int>>(emptySet())
    /** Notes currently held, from any source (lights the on-screen keyboard for MIDI too). */
    val pressed: StateFlow<Set<Int>> = _pressed.asStateFlow()

    private val _sustainPressed = MutableStateFlow(false)
    val sustainPressed: StateFlow<Boolean> = _sustainPressed.asStateFlow()

    /** Play incoming notes with the app's piano (off when the real piano's own speaker is enough). */
    @Volatile var soundThrough: Boolean = true

    /** Whether key strength changes the app's sound (off: every note at one level). */
    @Volatile var velocity: VelocityMode = VelocityMode()

    /** Semitones added to the app's sound (the piano page only; it resets when the page closes). */
    val transpose = Transposer()

    private var silentOwners = 0

    /** Ear answers still reach scoring/highlights, but cannot sound over a question or its explanation.
     * This lease is separate from the user's sound-through preference, including when that preference changes.
     */
    @Synchronized
    fun silenceInput(): AutoCloseable {
        silentOwners++
        transpose.clear().forEach(piano::noteOff)
        piano.sustain(false)
        var closed = false
        return AutoCloseable {
            synchronized(this) {
                if (!closed) { closed = true; silentOwners-- }
            }
        }
    }

    @Synchronized
    fun dispatch(event: InputEvent) {
        // Touch normally always sounds, but ear answers are silent for every source.
        val sound = silentOwners == 0 && (soundThrough || event.source == InputSource.TOUCH)
        when (event) {
            is InputEvent.NoteOn -> {
                if (sound) transpose.press(event.note)?.let { piano.noteOn(it, velocity.sound(event.velocity)) }
                _pressed.value = _pressed.value + event.note
            }
            is InputEvent.NoteOff -> {
                transpose.release(event.note)?.let { piano.noteOff(it) }
                _pressed.value = _pressed.value - event.note
            }
            is InputEvent.Sustain -> {
                _sustainPressed.value = event.down
                if (silentOwners == 0 && soundThrough) piano.sustain(event.down)
            }
            is InputEvent.Control -> Unit
        }
        _events.tryEmit(event)
    }

    /** Clears highlights and silences notes (e.g. when a screen closes or a device disconnects). */
    fun releaseAll() {
        synchronized(this) {
            transpose.clear().forEach { piano.noteOff(it) }
            _pressed.value = emptySet()
            _sustainPressed.value = false
        }
        piano.sustain(false)
    }
}
