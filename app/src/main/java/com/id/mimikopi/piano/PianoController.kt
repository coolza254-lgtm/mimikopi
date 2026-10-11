package com.id.mimikopi.piano

import com.id.mimikopi.audio.EngineMetrics
import com.id.mimikopi.audio.PianoEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide owner of the piano engine. Screens acquire/release it so the exclusive low-latency
 * stream is open only while something can play, and the pack is loaded once per process.
 */
class PianoController(private val engineFactory: () -> PianoEngine, private val packs: SamplePackManager) {
    private var engine: PianoEngine? = null
    private var users = 0

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private fun engine(): PianoEngine = engine ?: engineFactory().also { engine = it }

    /** Opens the stream; loads the installed pack first if needed. Returns true when sound can play. */
    @Synchronized
    fun acquire(): Boolean {
        users++
        val e = engine()
        if (!e.isLoaded) {
            val pack = packs.installed() ?: return false.also { users-- }
            if (e.isRunning) e.stop()
            e.load(pack.dir, pack.manifest)?.let { _lastError.value = it; users--; return false }
        }
        e.start()?.let { _lastError.value = it; users--; return false }
        _lastError.value = null
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
        if (users == 0) engine?.let { it.allNotesOff(); it.stop() }
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

    /** True while the native output stream is open (it can close by itself on a route change). */
    fun streamAlive(): Boolean = engine?.let { it.isRunning && it.metrics().sampleRate > 0 } ?: false
    fun setGain(gain: Float) = engine?.setMasterGain(gain) ?: Unit

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
