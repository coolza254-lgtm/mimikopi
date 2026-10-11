package com.id.mimikopi.core.practice

/**
 * Sustain-pedal changes (learning-system.md 4.5): at each new chord the pedal should come up and go down again soon
 * after the chord is struck (legato pedalling), so the old harmony does not blur into the new one.
 */
object PedalCheck {
    data class Result(val changes: Int, val good: Int, val sawPedal: Boolean) {
        val fraction: Double get() = if (changes == 0) 0.0 else good.toDouble() / changes
    }

    /** [chordsMs] = when each chord was played; [pedal] = (time, down) events. */
    fun analyze(chordsMs: List<Long>, pedal: List<Pair<Long, Boolean>>): Result {
        val sorted = pedal.sortedBy { it.first }
        var good = 0
        for (t in chordsMs.drop(1)) {
            val up = sorted.firstOrNull { !it.second && it.first in (t - 150)..(t + 500) } ?: continue
            if (sorted.any { it.second && it.first > up.first && it.first <= up.first + 700 }) good++
        }
        return Result((chordsMs.size - 1).coerceAtLeast(0), good, sorted.isNotEmpty())
    }
}
