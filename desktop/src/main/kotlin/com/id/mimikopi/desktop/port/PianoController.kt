package com.id.mimikopi.desktop.port

import com.id.mimikopi.desktop.audio.Metronome
import com.id.mimikopi.core.piano.PianoSound
import com.id.mimikopi.desktop.audio.Piano

/**
 * The Android PianoController's interface over the desktop piano and [Metronome]. The desktop output is always open,
 * so acquire/release only stop the clicks. Metronome "frames" are nanoseconds since the metronome's epoch with a
 * "sample rate" of 1e9: the shared timing code (count-in, tap offsets) turns them into real times unchanged.
 */
class PianoController(private val piano: () -> Piano) {
    private val metronome = Metronome(piano)
    /** Called when the last user releases the output (the metronome stops with it), as on Android. */
    var onStopped: (() -> Unit)? = null

    fun acquire(@Suppress("UNUSED_PARAMETER") needPack: Boolean = true): Boolean = true

    /** The piano page's sound; only the volume applies on the desktop (no room or warmth controls here). */
    @Volatile var sound: PianoSound = PianoSound()
        set(value) { field = value; piano().setLevel(value.factor) }
    val supportsTone: Boolean = false

    fun release() {
        metronome.set(false, 60.0, 1, intArrayOf(0), 0f)
        piano().allNotesOff()
        onStopped?.invoke()
    }

    fun noteOn(note: Int, velocity: Int) = piano().noteOn(note, velocity)
    fun noteOff(note: Int) = piano().noteOff(note)
    fun sustain(down: Boolean) = piano().sustain(down)
    fun allNotesOff() = piano().allNotesOff()

    fun setMetronome(on: Boolean, bpm: Double, ticksPerBeat: Int, levels: IntArray, volume: Float) = metronome.set(on, bpm, ticksPerBeat, levels, volume)
    fun drainMetronomeTicks(): LongArray = metronome.drain()
    fun metronomeTickCount(): Long = metronome.count()
    /** Output underruns are not reported by Java Sound. */
    fun xRuns(): Int? = null

    /** [frame position, time in nanos]: frame 0 is the epoch. */
    fun timestamp(): LongArray? = longArrayOf(0L, metronome.epoch)
    fun outputSampleRate(): Int = 1_000_000_000

    /** How long after it is sent a note is heard (the output buffer), so accompaniment can be sent early to match the clicks. */
    fun outputLatencyMs(): Double? = piano().latencyNanos / 1e6
}
