package com.id.mimikopi.core.metronome

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** Loudness/pitch class of one metronome click. Values are sent to the native engine. */
enum class ClickLevel(val code: Int) { STRONG(0), MEDIUM(1), WEAK(2), SUB(3) }

/**
 * Time signatures offered (PROMPT.md section 5). The tempo always counts the beat: a quarter note in
 * 2/4, 3/4, 4/4 and a dotted quarter in 6/8 (two beats of three eighths each).
 */
enum class TimeSignature(val label: String, val beatsPerBar: Int, val fixedSubdivision: Int?) {
    TWO_FOUR("2/4", 2, null),
    THREE_FOUR("3/4", 3, null),
    FOUR_FOUR("4/4", 4, null),
    SIX_EIGHT("6/8", 2, 3),
}

@Serializable
data class MetronomeSettings(
    val bpm: Int = 80,
    val signature: TimeSignature = TimeSignature.FOUR_FOUR,
    /** Clicks per beat: 1 = beats only, 2 = eighths, 3 = triplets, 4 = sixteenths. Ignored for 6/8. */
    val subdivision: Int = 1,
    val accent: Boolean = true,
    val volume: Float = 0.7f,
) {
    val ticksPerBeat: Int get() = signature.fixedSubdivision ?: subdivision.coerceIn(1, 4)

    fun normalized(): MetronomeSettings = copy(bpm = bpm.coerceIn(MIN_BPM, MAX_BPM), subdivision = subdivision.coerceIn(1, 4), volume = volume.coerceIn(0f, 1f))

    /** One click level per tick of a bar, in order. */
    fun barPattern(): List<ClickLevel> {
        val tpb = ticksPerBeat
        return List(signature.beatsPerBar * tpb) { tick ->
            val beat = tick / tpb
            val onBeat = tick % tpb == 0
            when {
                !onBeat -> ClickLevel.SUB
                !accent -> ClickLevel.WEAK
                beat == 0 -> ClickLevel.STRONG
                // 6/8: the second dotted-quarter beat gets a secondary accent.
                signature == TimeSignature.SIX_EIGHT -> ClickLevel.MEDIUM
                else -> ClickLevel.WEAK
            }
        }
    }

    /** Exact tick length in audio frames at [sampleRate] (kept fractional; the engine accumulates it). */
    fun framesPerTick(sampleRate: Int): Double = sampleRate * 60.0 / bpm / ticksPerBeat

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 240
    }
}

/** Tap tempo: averages the most recent intervals; a pause longer than [resetAfterMs] starts over. */
class TapTempo(private val maxTaps: Int = 6, private val resetAfterMs: Long = 2000) {
    private val taps = ArrayDeque<Long>()

    /** Registers a tap at [timeMs]; returns the tempo once two taps exist, else null. */
    fun tap(timeMs: Long): Int? {
        if (taps.isNotEmpty() && timeMs - taps.last() > resetAfterMs) taps.clear()
        taps.addLast(timeMs)
        while (taps.size > maxTaps) taps.removeFirst()
        if (taps.size < 2) return null
        val intervals = taps.zipWithNext { a, b -> (b - a).toDouble() }.sorted()
        val median = intervals[intervals.size / 2].let { if (intervals.size % 2 == 0) (it + intervals[intervals.size / 2 - 1]) / 2 else it }
        return (60_000.0 / median).roundToInt().coerceIn(MetronomeSettings.MIN_BPM, MetronomeSettings.MAX_BPM)
    }

    fun reset() = taps.clear()
}

/** A (frame position, monotonic time) pair reported by the audio stream's presentation timestamp. */
data class FrameTimestamp(val framePosition: Long, val timeNanos: Long)

@Serializable
data class JitterReport(
    val clicks: Int,
    val meanAbsMs: Double,
    val maxAbsMs: Double,
    val method: String,
)

/**
 * Metronome timing self-test (PROMPT.md section 5). Clicks are scheduled at exact frame positions
 * in the audio callback; this estimates *when each click is presented* by mapping its frame through
 * the stream's (frame, time) timestamps, then compares click-to-click intervals with the ideal.
 * It catches underruns, frame-clock drift and scheduling errors. It cannot see the speaker itself:
 * the fixed output latency is constant and does not show up as jitter (see docs/DEVICE-TESTING.md).
 */
object JitterAnalysis {
    const val METHOD = "Click frames from the audio callback mapped to presentation time with AAudio/Oboe " +
        "getTimestamp(CLOCK_MONOTONIC); jitter = deviation of click-to-click intervals from 60/bpm/subdivision."

    fun presentationTimesNanos(clickFrames: List<Long>, stamps: List<FrameTimestamp>, sampleRate: Int): List<Double> {
        require(stamps.isNotEmpty() && sampleRate > 0)
        val sorted = stamps.sortedBy { it.framePosition }
        return clickFrames.map { frame ->
            // Use the timestamp nearest in frames: the device's clock is mapped locally, not extrapolated far.
            val ts = sorted.minBy { abs(it.framePosition - frame) }
            ts.timeNanos + (frame - ts.framePosition) * 1e9 / sampleRate
        }
    }

    fun analyze(timesNanos: List<Double>, idealIntervalMs: Double): JitterReport {
        val deviations = timesNanos.zipWithNext { a, b -> (b - a) / 1e6 - idealIntervalMs }
        if (deviations.isEmpty()) return JitterReport(timesNanos.size, 0.0, 0.0, METHOD)
        return JitterReport(
            clicks = timesNanos.size,
            meanAbsMs = deviations.sumOf { abs(it) } / deviations.size,
            maxAbsMs = deviations.maxOf { abs(it) },
            method = METHOD,
        )
    }
}
