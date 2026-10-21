package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.core.piano.PianoSound
import com.id.mimikopi.core.session.PracticeLog
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.learning.SettingKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FreePianoUi(
    val sound: PianoSound = PianoSound(),
    val velocity: VelocityMode = VelocityMode(),
    val transpose: Int = 0,
    val noSound: Boolean = false,
    val lowNote: Int = 48,
    val minutesToday: Int = 0,
    val metronome: Boolean = false,
    val bpm: Int = 80,
)

/**
 * The free piano (the Android FreePianoViewModel): play from the MIDI piano, the computer keyboard or the on-screen
 * keys with the program's sound, e.g. into headphones. While the page is open MIDI notes always sound and the
 * transposition applies; both go back when it closes. Time spent playing (notes less than 10 s apart, not the page
 * left open) counts as free practice.
 */
class FreePianoSession(private val services: Services, private val scope: CoroutineScope) {
    private val hub = services.hub
    private val piano = services.piano
    val pressed = hub.pressed
    private val _ui = MutableStateFlow(FreePianoUi(sound = piano.sound, velocity = hub.velocity))
    val ui: StateFlow<FreePianoUi> = _ui.asStateFlow()
    private var savedThrough: Boolean? = null
    private var holding = false
    private var lastNote = 0L
    private var activeNanos = 0L

    init {
        scope.launch { hub.events.collect { e -> if (e is InputEvent.NoteOn && savedThrough != null) played(e.timeNanos) } }
        scope.launch {
            services.progress.log.collect { log -> _ui.update { it.copy(minutesToday = (PracticeLog.on(log, LocalDate.now()).seconds["free"] ?: 0) / 60) } }
        }
    }

    fun enter() {
        if (savedThrough == null) {
            savedThrough = hub.soundThrough
            hub.soundThrough = true
        }
        lastNote = 0L
        if (!holding) holding = piano.acquire()
        _ui.update { it.copy(sound = piano.sound, velocity = hub.velocity, noSound = !holding) }
    }

    fun leave() {
        savedThrough?.let { hub.soundThrough = it }
        savedThrough = null
        hub.transpose.semitones = 0
        setMetronome(false)
        _ui.update { it.copy(transpose = 0) }
        hub.releaseAll()
        flush()
        if (holding) { holding = false; piano.release() }
    }

    fun setSound(sound: PianoSound, save: Boolean) {
        piano.sound = sound
        _ui.update { it.copy(sound = sound) }
        if (save) services.appScope.launch { services.db.put(SettingKeys.PIANO_SOUND, sound.encode()) }
    }

    fun setVelocity(mode: VelocityMode) {
        hub.velocity = mode
        _ui.update { it.copy(velocity = mode) }
        services.appScope.launch { services.db.put(SettingKeys.VELOCITY_MODE, mode.encode()) }
    }

    fun transpose(delta: Int) {
        hub.transpose.semitones = _ui.value.transpose + delta
        _ui.update { it.copy(transpose = hub.transpose.semitones) }
    }

    fun shiftOctave(delta: Int) {
        hub.releaseAll()
        _ui.update { it.copy(lowNote = (it.lowNote + 12 * delta).coerceIn(LOWEST_C, HIGHEST_C)) }
    }

    fun setMetronome(on: Boolean, bpm: Int = _ui.value.bpm) {
        val b = bpm.coerceIn(30, 240)
        _ui.update { it.copy(metronome = on, bpm = b) }
        piano.setMetronome(on, b.toDouble(), 1, intArrayOf(0, 2, 2, 2), 0.8f)
    }

    private fun played(t: Long) {
        if (lastNote != 0L) (t - lastNote).takeIf { it in 0..GAP_NANOS }?.let { activeNanos += it }
        lastNote = t
        if (activeNanos >= FLUSH_NANOS) flush()
    }

    private fun flush() {
        val seconds = activeNanos / 1_000_000_000L
        if (seconds <= 0) return
        activeNanos -= seconds * 1_000_000_000L
        services.appScope.launch { services.progress.log("free", seconds) }
    }

    companion object {
        const val GAP_NANOS = 10_000_000_000L
        const val FLUSH_NANOS = 60_000_000_000L
        const val LOWEST_C = 24
        const val HIGHEST_C = 84
    }
}
