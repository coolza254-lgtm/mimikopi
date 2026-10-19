package com.id.mimikopi.piano

import com.id.mimikopi.audio.EngineMetrics
import com.id.mimikopi.audio.PianoEngine
import com.id.mimikopi.core.piano.PianoSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide owner of the piano engine. Screens acquire/release it so the exclusive low-latency
 * stream is open only while something can play, and the pack is loaded once per process.
 */
class PianoController(private val engineFactory: () -> PianoEngine, private val packs: SamplePackManager) {
    companion object {
        /** The engine's own master gain, heard at the usual volume. */
        const val ENGINE_GAIN = 0.35f
    }

    private var engine: PianoEngine? = null
    private var users = 0
    /** Called when the last user releases the engine (the metronome stops with it). */
    var onStopped: (() -> Unit)? = null

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private fun engine(): PianoEngine = engine ?: engineFactory().also { engine = it }

    /**
     * Opens the stream; loads the installed pack first if needed. Returns true when sound can play.
     * With [needPack] = false (metronome) the stream opens even without piano samples.
     */
    @Synchronized
    fun acquire(needPack: Boolean = true): Boolean {
        users++
        val e = engine()
        val pack = if (!e.isLoaded) packs.installed() else null
        if (!e.isLoaded && pack == null && needPack) return false.also { users-- }
        if (!e.isLoaded && pack != null) {
            if (e.isRunning) e.stop()
            e.load(pack.dir, pack.manifest)?.let { _lastError.value = it; users--; return false }
        }
        e.start()?.let { _lastError.value = it; users--; return false }
        _lastError.value = null
        applySound()
        return true
    }

    /** Reopen the output (e.g. after the route changed and the stream could not recover). */
    @Synchronized
    fun restart(): Boolean {
        val e = engine ?: return false
        e.stop()
        val err = e.start()
        _lastError.value = err
        return err == null
    }

    @Synchronized
    fun release() {
        if (users > 0) users--
        if (users == 0) engine?.let {
            it.allNotesOff()
            it.setMetronome(false, 80.0, 1, intArrayOf(0), 0f)
            it.stop()
            onStopped?.invoke()
        }
    }

    /** Forget the loaded pack (after reinstall/uninstall). */
    @Synchronized
    fun reset() {
        engine?.stop()
        engine = null
        users = 0
    }

    fun noteOn(note: Int, velocity: Int) = engine?.noteOn(note, velocity) ?: Unit
    fun noteOff(note: Int) = engine?.noteOff(note) ?: Unit
    fun sustain(down: Boolean) = engine?.sustain(down) ?: Unit
    fun allNotesOff() = engine?.allNotesOff() ?: Unit

    fun setMetronome(on: Boolean, bpm: Double, ticksPerBeat: Int, levels: IntArray, volume: Float) =
        engine?.setMetronome(on, bpm, ticksPerBeat, levels, volume) ?: Unit
    fun metronomeTickCount(): Long = engine?.metronomeTickCount() ?: 0
    fun drainMetronomeTicks(): LongArray = engine?.drainMetronomeTicks() ?: LongArray(0)
    fun timestamp(): LongArray? = engine?.timestamp()
    fun outputSampleRate(): Int = engine?.metrics()?.sampleRate ?: 0
    fun xRuns(): Int? = engine?.metrics()?.xRuns
    /** Oboe's estimate of the output latency of the open stream, in ms (null when unknown). */
    fun outputLatencyMs(): Double? = engine?.metrics()?.estimatedLatencyMs

    /** True while the native output stream is open (it can close by itself on a route change). */
    fun streamAlive(): Boolean = engine?.let { it.isRunning && it.metrics().sampleRate > 0 } ?: false

    /** Volume, room and warmth from the piano page; kept here so a new engine gets them too. */
    @Volatile var sound: PianoSound = PianoSound()
        set(value) { field = value; applySound() }
    /** The engine has room and warmth controls. */
    val supportsTone: Boolean = true

    private fun applySound() {
        val e = engine ?: return
        e.setMasterGain(ENGINE_GAIN * sound.factor)
        e.setTone(sound.reverb, sound.warmth)
    }

    /**
     * Measures the output stream for Diagnostics. If nothing is playing, opens the stream briefly
     * (silent when no pack is loaded) so the numbers reflect the current output route.
     */
    fun probeMetrics(settleMillis: Long = 400): EngineMetrics? {
        synchronized(this) {
            val e = engine()
            if (e.isRunning) return e.metrics()
            if (e.start() != null) return null
        }
        Thread.sleep(settleMillis)
        synchronized(this) {
            val e = engine ?: return null
            val m = e.metrics()
            if (users == 0) e.stop()
            return m
        }
    }
}
