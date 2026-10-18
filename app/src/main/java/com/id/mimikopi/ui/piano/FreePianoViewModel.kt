package com.id.mimikopi.ui.piano

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.Transposer
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.core.piano.PianoSound
import com.id.mimikopi.core.session.PracticeLog
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class FreePianoUi(
    val sound: PianoSound = PianoSound(),
    val velocity: VelocityMode = VelocityMode(),
    val transpose: Int = 0,
    /** The app's piano could not start (on the phone: the piano sound is not installed yet). */
    val noSound: Boolean = false,
    /** The lowest C on the on-screen keys. */
    val lowNote: Int = 48,
    /** Free playing logged today, in minutes. */
    val minutesToday: Int = 0,
)

/**
 * The free piano: play from the MIDI piano (or the on-screen keys) with the app's sound, e.g. into headphones while the
 * piano's own speaker is off. While the page is open the app's sound is always on and [Transposer] applies; both go
 * back when it closes, so practice pages are never affected. Time spent playing counts as "free" practice.
 */
class FreePianoViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val hub = services.inputHub
    private val piano = services.piano
    private val dao = services.database.settingsDao()
    private val progress = services.progress

    val pressed: StateFlow<Set<Int>> = hub.pressed
    val supportsTone: Boolean get() = piano.supportsTone

    private val _ui = MutableStateFlow(FreePianoUi(sound = piano.sound, velocity = hub.velocity))
    val ui: StateFlow<FreePianoUi> = _ui.asStateFlow()

    /** "Sound through" as it was before the page opened (null while closed). */
    private var savedThrough: Boolean? = null
    private var holding = false
    private var lastNote = 0L
    private var activeNanos = 0L

    init {
        viewModelScope.launch { hub.events.collect { e -> if (e is InputEvent.NoteOn && savedThrough != null) played(e.timeNanos) } }
        viewModelScope.launch {
            progress.load()
            progress.log.collect { log -> _ui.update { it.copy(minutesToday = (PracticeLog.on(log, LocalDate.now()).seconds["free"] ?: 0) / 60) } }
        }
    }

    fun enter() {
        if (savedThrough == null) {
            savedThrough = hub.soundThrough
            hub.soundThrough = true
        }
        lastNote = 0L
        _ui.update { it.copy(sound = piano.sound, velocity = hub.velocity) }
        startSound()
    }

    fun leave() {
        savedThrough?.let { hub.soundThrough = it }
        savedThrough = null
        hub.transpose.semitones = 0
        _ui.update { it.copy(transpose = 0) }
        hub.releaseAll()
        flush()
        if (holding) {
            holding = false
            services.appScope.launch(Dispatchers.IO) { piano.release() }
        }
    }

    private fun startSound() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { holding || piano.acquire().also { holding = it } }
            _ui.update { it.copy(noSound = !ok) }
        }
    }

    /** The piano sound was just installed (the engine was reset): open it again. */
    fun soundInstalled() {
        holding = false
        startSound()
    }

    /** Moves the sound while sliding; [save] when the finger lifts. */
    fun setSound(sound: PianoSound, save: Boolean) {
        piano.sound = sound
        _ui.update { it.copy(sound = sound) }
        if (save) services.appScope.launch { dao.put(SettingEntity(SettingKeys.PIANO_SOUND, sound.encode())) }
    }

    fun setVelocity(mode: VelocityMode) {
        hub.velocity = mode
        _ui.update { it.copy(velocity = mode) }
        services.appScope.launch { dao.put(SettingEntity(SettingKeys.VELOCITY_MODE, mode.encode())) }
    }

    fun transpose(delta: Int) {
        hub.transpose.semitones = _ui.value.transpose + delta
        _ui.update { it.copy(transpose = hub.transpose.semitones) }
    }

    fun shiftOctave(delta: Int) {
        hub.releaseAll()
        _ui.update { it.copy(lowNote = (it.lowNote + 12 * delta).coerceIn(LOWEST_C, HIGHEST_C)) }
    }

    /** Counts the time between notes played less than [GAP_NANOS] apart: playing, not the page left open. */
    private fun played(t: Long) {
        if (lastNote != 0L) (t - lastNote).takeIf { it in 0..GAP_NANOS }?.let { activeNanos += it }
        lastNote = t
        if (activeNanos >= FLUSH_NANOS) flush()
    }

    private fun flush() {
        val seconds = activeNanos / 1_000_000_000L
        if (seconds <= 0) return
        activeNanos -= seconds * 1_000_000_000L
        services.appScope.launch { progress.log("free", seconds) }
    }

    override fun onCleared() {
        leave()
    }

    companion object {
        const val GAP_NANOS = 10_000_000_000L
        const val FLUSH_NANOS = 60_000_000_000L
        const val LOWEST_C = 24
        const val HIGHEST_C = 84
    }
}
