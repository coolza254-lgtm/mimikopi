package com.id.mimikopi.desktop.audio

import com.id.mimikopi.core.piano.PianoSound
import com.id.mimikopi.core.piano.SampleMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Where the app's sound goes: notes, prompts, answers, demos and the metronome. */
interface NoteOutput {
    fun noteOn(note: Int, velocity: Int)
    fun noteOff(note: Int)
    fun sustain(down: Boolean)
}

/** How the output is opened (Settings → เสียง). Kept per computer, never synced. */
data class AudioPrefs(val periodFrames: Int = 256, val exclusive: Boolean = true) {
    fun encode() = "$periodFrames|$exclusive"

    companion object {
        val PERIODS = listOf(128, 256, 512)
        fun decode(s: String?): AudioPrefs {
            val p = s?.split('|') ?: return AudioPrefs()
            return AudioPrefs(p.getOrNull(0)?.toIntOrNull()?.takeIf { it in PERIODS } ?: 256, p.getOrNull(1)?.toBooleanStrictOrNull() ?: true)
        }
    }
}

/** What the output is doing now, for the sound and latency pages. */
data class AudioStatus(
    val open: Boolean = false,
    val exclusive: Boolean = false,
    val sampleRate: Int = 0,
    val periodFrames: Int = 0,
    val periods: Int = 0,
    val bufferMs: Double? = null,
    /** Real piano samples are loaded; otherwise the built-in tone plays. */
    val samples: Boolean = false,
    val error: String? = null,
    val restarts: Int = 0,
)

/**
 * The one piano engine of the program (like Android's PianoController). Screens acquire it while they can play and
 * release it when they close; the output device stays open only while someone holds it. If Windows stops the device
 * (unplugged headphones, another program took exclusive mode) a watchdog reopens it on the current default device.
 *
 * Every time this class returns is on the JVM's System.nanoTime clock, the same clock as MIDI and keyboard input and
 * the judge: the native clock's offset is measured once at start.
 */
class Piano(
    @Volatile var prefs: AudioPrefs = AudioPrefs(),
    /** Tests: run without a sound card (miniaudio's null device). */
    private val nullDevice: Boolean = false,
) : NoteOutput, AutoCloseable {
    private val handle: Long = if (NativeAudio.available) NativeAudio.nativeCreate() else 0L
    private var users = 0
    private var wantOpen = false
    private var restarts = 0
    private var clockOffsetNanos = 0L

    private val _status = MutableStateFlow(AudioStatus(error = NativeAudio.loadError?.let { "โหลดระบบเสียงไม่ได้: $it" }))
    val status: StateFlow<AudioStatus> = _status.asStateFlow()

    val available: Boolean get() = handle != 0L

    private val watchdog = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mimikopi-audio-watchdog").apply { isDaemon = true } }

    init {
        if (available) {
            clockOffsetNanos = measureClockOffset()
            watchdog.scheduleWithFixedDelay({ runCatching { checkDevice() } }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun measureClockOffset(): Long {
        var best = Long.MAX_VALUE
        var offset = 0L
        repeat(25) {
            val a = System.nanoTime()
            val n = NativeAudio.nativeNowNanos()
            val b = System.nanoTime()
            if (b - a < best) {
                best = b - a
                offset = (a + b) / 2 - n
            }
        }
        return offset
    }

    /** Opens the output if nobody had it open. Returns true when sound can play. */
    @Synchronized
    fun acquire(): Boolean {
        if (!available) return false
        users++
        wantOpen = true
        if (NativeAudio.nativeRunning(handle)) return true
        return open()
    }

    @Synchronized
    fun release() {
        if (users > 0) users--
        if (users == 0 && available) {
            wantOpen = false
            NativeAudio.nativeAllNotesOff(handle)
            NativeAudio.nativeSetMetronome(handle, false, 80.0, 1, intArrayOf(0), 0f)
            NativeAudio.nativeStop(handle)
            refreshStatus(null)
        }
    }

    /** Reopens the output with new [prefs] (Settings), keeping whoever holds it. */
    @Synchronized
    fun reopen(newPrefs: AudioPrefs): Boolean {
        prefs = newPrefs
        if (!available) return false
        if (!wantOpen) {
            refreshStatus(null)
            return true
        }
        NativeAudio.nativeStop(handle)
        return open()
    }

    private fun open(): Boolean {
        val err = NativeAudio.nativeStart(handle, prefs.periodFrames, prefs.exclusive, nullDevice)
        applySound()
        refreshStatus(err)
        return err == null
    }

    @Synchronized
    private fun checkDevice() {
        if (!wantOpen || NativeAudio.nativeRunning(handle)) return
        NativeAudio.nativeStop(handle)
        if (open()) restarts++
        refreshStatus(_status.value.error)
    }

    private fun refreshStatus(error: String?) {
        if (!available) return
        val m = NativeAudio.nativeMetrics(handle)
        val open = NativeAudio.nativeRunning(handle)
        _status.value = AudioStatus(
            open = open,
            exclusive = open && m[4] > 0.5,
            sampleRate = m[0].toInt(),
            periodFrames = m[1].toInt(),
            periods = m[2].toInt(),
            bufferMs = m[3].takeIf { open && it >= 0 },
            samples = m[11] > 0,
            error = error,
            restarts = restarts,
        )
    }

    // ---------------- sample pack ----------------

    /** Loads an installed pack (decoded .pcm files) into the engine; the built-in tone plays until then. */
    @Synchronized
    fun loadPack(dir: File, map: SampleMap, packRate: Int, releaseSec: List<Double>, keyCenters: List<Int>): String? {
        if (!available) return NativeAudio.loadError
        val wasOpen = NativeAudio.nativeRunning(handle)
        if (wasOpen) NativeAudio.nativeStop(handle)
        val layers = map.filesInOrder()
        val table = map.layerTable()
        val err = NativeAudio.nativeLoad(
            handle,
            layers.map { File(dir, it.file.removeSuffix(".flac") + ".pcm").absolutePath }.toTypedArray(),
            packRate,
            map.noteToRegion,
            table.map { it.first }.toIntArray(),
            table.map { it.second }.toIntArray(),
            keyCenters.toIntArray(),
            releaseSec.map { it.toFloat() }.toFloatArray(),
            layers.map { it.loVel }.toIntArray(),
            layers.map { it.hiVel }.toIntArray(),
        )
        if (wasOpen) open() else refreshStatus(err)
        return err
    }

    @Synchronized
    fun unloadPack() {
        if (!available) return
        val wasOpen = NativeAudio.nativeRunning(handle)
        if (wasOpen) NativeAudio.nativeStop(handle)
        NativeAudio.nativeUnload(handle)
        if (wasOpen) open() else refreshStatus(null)
    }

    // ---------------- playing ----------------

    override fun noteOn(note: Int, velocity: Int) { if (available) NativeAudio.nativeNoteOn(handle, note, velocity) }
    override fun noteOff(note: Int) { if (available) NativeAudio.nativeNoteOff(handle, note) }
    override fun sustain(down: Boolean) { if (available) NativeAudio.nativeSustain(handle, down) }
    fun allNotesOff() { if (available) NativeAudio.nativeAllNotesOff(handle) }

    fun setMetronome(on: Boolean, bpm: Double, ticksPerBeat: Int, levels: IntArray, volume: Float) {
        if (available) NativeAudio.nativeSetMetronome(handle, on, bpm, ticksPerBeat, levels, volume)
    }

    fun metronomeTickCount(): Long = if (available) NativeAudio.nativeMetronomeTickCount(handle) else 0
    fun drainMetronomeTicks(): LongArray = if (available) NativeAudio.nativeDrainTicks(handle) else LongArray(0)

    /** (frame position, System.nanoTime at which that frame is heard), or null while the output is closed. */
    fun timestamp(): LongArray? {
        if (!available) return null
        val t = NativeAudio.nativeTimestamp(handle) ?: return null
        return longArrayOf(t[0], t[1] + clockOffsetNanos)
    }

    fun outputSampleRate(): Int = if (available && NativeAudio.nativeRunning(handle)) NativeAudio.nativeMetrics(handle)[0].toInt() else 0

    /** The device's buffering in ms (what a note waits before it is heard), null when closed. */
    fun outputLatencyMs(): Double? = status.value.bufferMs

    fun isOpen(): Boolean = available && NativeAudio.nativeRunning(handle)

    /** Volume, room and warmth (the same PianoSound setting as Android). */
    @Volatile var sound: PianoSound = PianoSound()
        set(value) {
            field = value
            applySound()
        }

    private fun applySound() {
        if (!available) return
        NativeAudio.nativeSetMasterGain(handle, ENGINE_GAIN * sound.factor)
        NativeAudio.nativeSetTone(handle, sound.reverb, sound.warmth)
    }

    /** Tests: renders a block without a device (the engine must not be open). */
    internal fun renderForTest(frames: Int): FloatArray = NativeAudio.nativeRender(handle, frames)

    override fun close() {
        watchdog.shutdownNow()
        if (available) {
            NativeAudio.nativeStop(handle)
            NativeAudio.nativeDestroy(handle)
        }
    }

    companion object {
        const val ENGINE_GAIN = 0.35f
    }
}
