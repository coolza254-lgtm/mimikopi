package com.id.mimikopi.core.input

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** learning-system.md 2.3 item 4: is the keyboard velocity-sensitive? */
@Serializable
data class VelocityProbeResult(
    val softMin: Int,
    val softMax: Int,
    val hardMin: Int,
    val hardMax: Int,
    val distinctValues: Int,
    val velocitySensitive: Boolean,
) {
    fun describe(): String = "soft $softMin-$softMax, hard $hardMin-$hardMax, $distinctValues distinct, " +
        if (velocitySensitive) "velocity-sensitive" else "fixed velocity"
}

object VelocityProbe {
    /**
     * Sensitive when hard presses are clearly louder than soft ones and values actually vary.
     * A fixed-velocity keyboard sends one value (often 64, 100 or 127) for everything.
     */
    fun analyze(soft: List<Int>, hard: List<Int>): VelocityProbeResult {
        require(soft.isNotEmpty() && hard.isNotEmpty())
        val distinct = (soft + hard).toSet().size
        val sensitive = distinct >= 3 && hard.average() - soft.average() >= 15
        return VelocityProbeResult(soft.min(), soft.max(), hard.min(), hard.max(), distinct, sensitive)
    }
}

/** learning-system.md 2.3 item 2: the user presses Middle C once; some keyboards send it an octave off. */
object OctaveCalibration {
    const val MIDDLE_C = 60

    /** Offset in semitones (a multiple of 12) to add to incoming notes, or null if implausible. */
    fun offsetFor(pressedNote: Int): Int? {
        val raw = MIDDLE_C - pressedNote
        val octaves = (raw / 12.0).roundToInt()
        // A key within two semitones of a C means the user aimed at C; anything else is a mistake.
        if (abs(raw - octaves * 12) > 2 || abs(octaves) > 3) return null
        return octaves * 12
    }
}

@Serializable
data class LatencyResult(val offsetMs: Double, val spreadMs: Double, val taps: Int)

/**
 * learning-system.md 2.3 item 1: the user taps along with 16 clicks; the median of (tap - click)
 * is the delay of this input + output combination, subtracted later when judging timing.
 */
object LatencyCalibration {
    // Bluetooth output can add ~300 ms; the calibration clicks are 1 s apart, so 400 ms stays unambiguous.
    const val MAX_ABS_OFFSET_MS = 400.0
    const val MIN_TAPS = 10

    /** Click and tap times in nanoseconds on the same clock. Returns null if too few usable taps. */
    fun analyze(clickNanos: List<Double>, tapNanos: List<Long>): LatencyResult? {
        if (clickNanos.isEmpty()) return null
        val offsets = tapNanos.mapNotNull { tap ->
            val nearest = clickNanos.minBy { abs(it - tap) }
            ((tap - nearest) / 1e6).takeIf { abs(it) <= MAX_ABS_OFFSET_MS }
        }.sorted()
        if (offsets.size < MIN_TAPS) return null
        val median = offsets[offsets.size / 2]
        val iqr = offsets[(offsets.size * 3) / 4] - offsets[offsets.size / 4]
        return LatencyResult(median, iqr, offsets.size)
    }
}

/** Calibrated input settings (stored as JSON in the settings table). */
@Serializable
data class InputConfig(
    val octaveOffset: Int = 0,
    val velocityProbe: VelocityProbeResult? = null,
    /** Key: "<InputSource>|<output route kind>", e.g. "USB_MIDI|SPEAKER". Value: delay in ms. */
    val latencyMs: Map<String, Double> = emptyMap(),
) {
    /** Unknown until probed; dynamics exercises are hidden only when the probe says fixed velocity. */
    val dynamicsAvailable: Boolean get() = velocityProbe?.velocitySensitive != false

    companion object {
        fun latencyKey(source: InputSource, routeKind: String) = "${source.name}|$routeKind"
    }
}
