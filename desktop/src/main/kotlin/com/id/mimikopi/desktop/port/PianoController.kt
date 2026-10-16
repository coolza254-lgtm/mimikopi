package com.id.mimikopi.desktop.port

import com.id.mimikopi.desktop.audio.Metronome
import com.id.mimikopi.desktop.audio.Piano

/**
 * The Android PianoController's interface over the desktop piano and [Metronome]. The desktop output is always open,
 * so acquire/release only stop the clicks. Metronome "frames" are nanoseconds since the metronome's epoch with a
 * "sample rate" of 1e9: the shared timing code (count-in, tap offsets) turns them into real times unchanged.
 */
class PianoController(private val piano: () -> Piano) {
    private val metronome = Metronome(piano)

    fun acquire(@Suppress("UNUSED_PARAMETER") needPack: Boolean = true): Boolean = true

    fun release() {
        metronome.set(false, 60.0, 1, intArrayOf(0), 0f)
        piano().allNotesOff()
    }

    fun noteOn(note: Int, velocity: Int) = piano().noteOn(note, velocity)
    fun noteOff(note: Int) = piano().noteOff(note)
    fun sustain(down: Boolean) = piano().sustain(down)
    fun allNotesOff() = piano().allNotesOff()

    fun setMetronome(on: Boolean, bpm: Double, ticksPerBeat: Int, levels: IntArray, volume: Float) = metronome.set(on, bpm, ticksPerBeat, levels, volume)
    fun drainMetronomeTicks(): LongArray = metronome.drain()

    /** [frame position, time in nanos]: frame 0 is the epoch. */
    fun timestamp(): LongArray? = longArrayOf(0L, metronome.epoch)
    fun outputSampleRate(): Int = 1_000_000_000

    /** How long after it is sent a note is heard (the output buffer), so accompaniment can be sent early to match the clicks. */
    fun outputLatencyMs(): Double? = piano().latencyNanos / 1e6
}
