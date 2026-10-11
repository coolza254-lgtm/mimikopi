package com.id.mimikopi.piano

import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import kotlinx.coroutines.delay

/**
 * Starts the native metronome and returns the System.nanoTime of the downbeat after [countIn] clicks. The first
 * two clicks' frame positions are mapped to presentation time (getTimestamp), so the downbeat matches what is
 * heard. Without an audio clock it falls back to a visual count from now. The metronome keeps running.
 */
suspend fun countInDownbeat(piano: PianoController, bpm: Double, beatsPerBar: Int, countIn: Int, volume: Float = 0.9f): Long {
    val beatNanos = (60_000_000_000.0 / bpm).toLong()
    val pattern = IntArray(beatsPerBar) { if (it == 0) 0 else 2 }
    piano.drainMetronomeTicks()
    piano.setMetronome(true, bpm, 1, pattern, volume)
    val stamps = mutableListOf<FrameTimestamp>()
    val ticks = mutableListOf<Long>()
    val began = System.nanoTime()
    while (true) {
        delay(5)
        piano.timestamp()?.let { stamps += FrameTimestamp(it[0], it[1]) }
        ticks += piano.drainMetronomeTicks().toList()
        val rate = piano.outputSampleRate()
        if (ticks.size >= 2 && stamps.isNotEmpty() && rate > 0) {
            val times = JitterAnalysis.presentationTimesNanos(ticks.take(2), stamps, rate)
            return (times.mapIndexed { i, t -> t - i * beatNanos }.average() + countIn * beatNanos).toLong()
        }
        if (System.nanoTime() - began > 3 * beatNanos + 600_000_000L) return System.nanoTime() + countIn * beatNanos
    }
}
