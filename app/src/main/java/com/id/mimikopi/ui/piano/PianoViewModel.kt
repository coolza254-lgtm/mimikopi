package com.id.mimikopi.ui.piano

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.isBlackKey
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.piano.PackException
import com.id.mimikopi.piano.PackProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PackUi {
    data object Checking : PackUi
    data class Missing(val downloadBytes: Long?, val decodedBytes: Long?) : PackUi
    data class Downloading(val fraction: Float) : PackUi
    data class Decoding(val done: Int, val total: Int) : PackUi
    data class Failed(val kind: PackException.Kind?, val detail: String?) : PackUi
    data object Ready : PackUi
}

data class KeyboardUi(
    val lowNote: Int = 48, // C3
    val whiteKeyDp: Int = 44,
    val velocity: Int = 80,
    /** 0..1 slider; mapped to -24..+12 dB (see [volumeToGain]). */
    val gain: Float = 0.75f,
    val reverb: Float = 0.4f,
    val warmth: Float = 0.6f,
    /** Velocity follows where the key is touched: near the top is soft, near the front edge is loud. */
    val touchVelocity: Boolean = true,
    val showTone: Boolean = false,
    val showNames: Boolean = false,
    val nameStyle: NoteNameStyle = NoteNameStyle.BOTH,
    val sustain: Boolean = false,
    val pressed: Set<Int> = emptySet(),
    val soundOn: Boolean = false,
    /** At least one start attempt finished (avoids flashing "no sound" while starting). */
    val soundTried: Boolean = false,
)

class PianoViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val packs = services.samplePacks
    private val piano = services.piano
    private val settings = services.database.settingsDao()

    private val _pack = MutableStateFlow<PackUi>(PackUi.Checking)
    val pack: StateFlow<PackUi> = _pack.asStateFlow()

    private val _keys = MutableStateFlow(KeyboardUi())
    val keys: StateFlow<KeyboardUi> = _keys.asStateFlow()

    val engineError: StateFlow<String?> = piano.lastError

    private var installJob: Job? = null
    /** The screen is visible and wants sound. */
    private var acquired = false
    /** This ViewModel currently holds one reference on the shared engine (balanced acquire/release). */
    private var holding = false

    init {
        viewModelScope.launch {
            loadSettings()
            refreshPack()
        }
        // Watch the output: if the stream died (and could not reopen), say so instead of staying silent.
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (acquired && holding) {
                    val alive = withContext(Dispatchers.IO) { piano.streamAlive() }
                    if (alive != _keys.value.soundOn) _keys.update { it.copy(soundOn = alive) }
                }
            }
        }
    }

    private suspend fun loadSettings() {
        fun String?.int(default: Int) = this?.toIntOrNull() ?: default
        val s = _keys.value
        _keys.value = s.copy(
            lowNote = settings.get(SettingKeys.PIANO_LOW_NOTE).int(s.lowNote),
            whiteKeyDp = settings.get(SettingKeys.PIANO_KEY_WIDTH).int(s.whiteKeyDp),
            velocity = settings.get(SettingKeys.PIANO_VELOCITY).int(s.velocity),
            gain = settings.get(SettingKeys.PIANO_GAIN)?.toFloatOrNull() ?: s.gain,
            reverb = settings.get(SettingKeys.PIANO_REVERB)?.toFloatOrNull() ?: s.reverb,
            warmth = settings.get(SettingKeys.PIANO_WARMTH)?.toFloatOrNull() ?: s.warmth,
            touchVelocity = settings.get(SettingKeys.PIANO_TOUCH_VELOCITY)?.toBooleanStrictOrNull() ?: s.touchVelocity,
            nameStyle = settings.get(SettingKeys.NOTE_NAME_STYLE)?.let { runCatching { NoteNameStyle.valueOf(it) }.getOrNull() } ?: s.nameStyle,
        )
    }

    private suspend fun refreshPack() {
        if (withContext(Dispatchers.IO) { packs.installed() } != null) {
            _pack.value = PackUi.Ready
            // The screen may have started before the pack check finished: start the sound now.
            if (acquired) startSound()
            return
        }
        _pack.value = PackUi.Missing(null, null)
        // Show the size before asking; failure here is fine (offline), the button still works.
        runCatching { withContext(Dispatchers.IO) { packs.fetchDescriptor() } }
            .onSuccess { _pack.value = PackUi.Missing(it.size, it.decodedBytes) }
    }

    fun installPack() {
        if (installJob?.isActive == true) return
        installJob = viewModelScope.launch {
            _pack.value = PackUi.Downloading(0f)
            try {
                withContext(Dispatchers.IO) {
                    val d = packs.fetchDescriptor()
                    packs.install(d) { p ->
                        _pack.value = when (p) {
                            is PackProgress.Downloading -> PackUi.Downloading(p.fraction)
                            is PackProgress.Decoding -> PackUi.Decoding(p.done, p.total)
                        }
                    }
                }
                piano.reset()
                _pack.value = PackUi.Ready
                if (acquired) startSound()
            } catch (e: PackException) {
                _pack.value = PackUi.Failed(e.kind, e.message)
            } catch (e: java.io.IOException) {
                _pack.value = PackUi.Failed(null, e.message)
            }
        }
    }

    /** Screen visible: open the audio stream. */
    fun onStart() {
        acquired = true
        startSound()
    }

    /** Screen hidden: release the stream so other apps get audio back. */
    fun onStop() {
        if (!acquired) return
        acquired = false
        _keys.update { it.copy(pressed = emptySet(), sustain = false, soundOn = false) }
        viewModelScope.launch {
            if (holding) {
                holding = false
                withContext(Dispatchers.IO) { piano.release() }
            }
        }
    }

    /** Also used by the "turn sound on again" button when the output was lost. */
    fun startSound() {
        if (_pack.value != PackUi.Ready || !acquired) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (holding) piano.restart() else piano.acquire().also { holding = it }
            }
            if (ok) applySound()
            _keys.update { it.copy(soundOn = ok, soundTried = true) }
        }
    }

    private fun applySound() {
        val k = _keys.value
        piano.setGain(volumeToGain(k.gain))
        piano.setTone(k.reverb, k.warmth)
        services.metronome.apply() // the metronome bar shares this output
    }

    /** [depth] is where on the key the finger landed: 0 = back (top), 1 = front edge. */
    fun noteOn(note: Int, depth: Float) {
        val k = _keys.value
        val velocity = if (k.touchVelocity) {
            (k.velocity - 35 + depth.coerceIn(0f, 1f) * 60).toInt().coerceIn(15, 127)
        } else {
            k.velocity
        }
        piano.noteOn(note, velocity)
        _keys.update { it.copy(pressed = it.pressed + note) }
    }

    fun noteOff(note: Int) {
        piano.noteOff(note)
        _keys.update { it.copy(pressed = it.pressed - note) }
    }

    fun setSustain(down: Boolean) {
        piano.sustain(down)
        _keys.update { it.copy(sustain = down) }
    }

    /** Move the visible range by one octave, staying on the 88 keys (A0..C8). */
    fun shiftOctave(delta: Int) {
        releaseAllKeys()
        var low = (_keys.value.lowNote + 12 * delta).coerceIn(21, 96)
        if (isBlackKey(low)) low -= 1
        _keys.update { it.copy(lowNote = low) }
        save(SettingKeys.PIANO_LOW_NOTE, low.toString())
    }

    fun zoom(deltaDp: Int) {
        releaseAllKeys()
        val w = (_keys.value.whiteKeyDp + deltaDp).coerceIn(28, 80)
        _keys.update { it.copy(whiteKeyDp = w) }
        save(SettingKeys.PIANO_KEY_WIDTH, w.toString())
    }

    fun setVelocity(v: Int) {
        _keys.update { it.copy(velocity = v.coerceIn(20, 127)) }
        save(SettingKeys.PIANO_VELOCITY, _keys.value.velocity.toString())
    }

    fun setGain(g: Float) {
        _keys.update { it.copy(gain = g) }
        piano.setGain(volumeToGain(g))
        save(SettingKeys.PIANO_GAIN, g.toString())
    }

    fun setReverb(r: Float) {
        _keys.update { it.copy(reverb = r) }
        applySound()
        save(SettingKeys.PIANO_REVERB, r.toString())
    }

    fun setWarmth(w: Float) {
        _keys.update { it.copy(warmth = w) }
        applySound()
        save(SettingKeys.PIANO_WARMTH, w.toString())
    }

    fun setTouchVelocity(on: Boolean) {
        _keys.update { it.copy(touchVelocity = on) }
        save(SettingKeys.PIANO_TOUCH_VELOCITY, on.toString())
    }

    fun toggleTonePanel() {
        _keys.update { it.copy(showTone = !it.showTone) }
    }

    /** Showing names is a helper (learning-system.md 2.4); every use is counted. */
    fun toggleNames() {
        val show = !_keys.value.showNames
        _keys.update { it.copy(showNames = show) }
        if (show) {
            viewModelScope.launch {
                val n = (settings.get(SettingKeys.HELPER_NOTE_NAMES_COUNT)?.toIntOrNull() ?: 0) + 1
                settings.put(SettingEntity(SettingKeys.HELPER_NOTE_NAMES_COUNT, n.toString()))
            }
        }
    }

    fun setNameStyle(style: NoteNameStyle) {
        _keys.update { it.copy(nameStyle = style) }
        save(SettingKeys.NOTE_NAME_STYLE, style.name)
    }

    /** The keyboard's touch tracking restarts when its range changes; never leave notes hanging. */
    private fun releaseAllKeys() {
        piano.allNotesOff()
        _keys.update { it.copy(pressed = emptySet(), sustain = false) }
    }

    private fun save(key: String, value: String) {
        viewModelScope.launch { settings.put(SettingEntity(key, value)) }
    }

    override fun onCleared() {
        onStop()
    }
}

/** Volume slider 0..1 -> linear gain, -24 dB .. +12 dB (the compressor/limiter keeps loud settings clean). */
fun volumeToGain(v: Float): Float = Math.pow(10.0, (v.coerceIn(0f, 1f) * 36.0 - 24.0) / 20.0).toFloat()
