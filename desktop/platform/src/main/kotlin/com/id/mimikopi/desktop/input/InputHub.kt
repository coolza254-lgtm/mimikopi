package com.id.mimikopi.desktop.input

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.Transposer
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.desktop.audio.NoteOutput
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single input stream, as on Android: MIDI, the computer keyboard and clicks on the on-screen keys all call
 * [dispatch]; sound starts here at once on the caller's thread and everything else (highlights, judging, latency
 * measurement) observes [events]. Every event carries System.nanoTime of its arrival.
 */
class InputHub(private val piano: NoteOutput) {
    private val _events = MutableSharedFlow<InputEvent>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<InputEvent> = _events.asSharedFlow()

    private val _pressed = MutableStateFlow<Set<Int>>(emptySet())
    val pressed: StateFlow<Set<Int>> = _pressed.asStateFlow()

    private val _sustainPressed = MutableStateFlow(false)
    val sustainPressed: StateFlow<Boolean> = _sustainPressed.asStateFlow()

    /**
     * Play MIDI notes with the program's piano. Off by default: a digital piano is heard through its own speaker,
     * and doubling it with a delayed copy makes timing harder. Clicks and computer keys always sound.
     */
    @Volatile var soundThrough: Boolean = false

    @Volatile var velocity: VelocityMode = VelocityMode()

    val transpose = Transposer()

    private var silentOwners = 0

    /** Ear answers still reach judging and highlights but never sound over a question or its explanation. */
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

    private fun sounds(source: InputSource) = silentOwners == 0 && (soundThrough || !source.isMidi)

    @Synchronized
    fun dispatch(event: InputEvent) {
        when (event) {
            is InputEvent.NoteOn -> {
                if (sounds(event.source)) transpose.press(event.note)?.let { piano.noteOn(it, velocity.sound(event.velocity)) }
                _pressed.value = _pressed.value + event.note
            }
            is InputEvent.NoteOff -> {
                transpose.release(event.note)?.let { piano.noteOff(it) }
                _pressed.value = _pressed.value - event.note
            }
            is InputEvent.Sustain -> {
                _sustainPressed.value = event.down
                if (sounds(event.source)) piano.sustain(event.down)
            }
            is InputEvent.Control -> Unit
        }
        _events.tryEmit(event)
    }

    /** Clears highlights and silences notes (a screen closed, a device was unplugged). */
    fun releaseAll() {
        synchronized(this) {
            transpose.clear().forEach { piano.noteOff(it) }
            _pressed.value = emptySet()
            _sustainPressed.value = false
        }
        piano.sustain(false)
    }
}

val InputSource.isMidi: Boolean get() = this == InputSource.USB_MIDI || this == InputSource.BLE_MIDI
