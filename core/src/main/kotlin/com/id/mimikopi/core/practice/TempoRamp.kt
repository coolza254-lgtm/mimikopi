package com.id.mimikopi.core.practice

import com.id.mimikopi.core.config.TempoRampConfig
import kotlin.math.roundToInt

/**
 * Tempo ramp from learning-system.md 6.3: start at a fraction of the target, step up after N clean
 * runs in a row, step back down one level after M failed runs in a row. Immutable; each run
 * returns a new state.
 */
data class TempoRamp(
    val targetBpm: Int,
    val currentBpm: Int,
    val cleanStreak: Int = 0,
    val failStreak: Int = 0,
    /** Previous tempo levels, so "down one step" returns exactly to the last level. */
    val history: List<Int> = emptyList(),
    val config: TempoRampConfig = TempoRampConfig(),
) {
    val reachedTarget: Boolean get() = currentBpm >= targetBpm

    fun onRun(clean: Boolean): TempoRamp = if (clean) onClean() else onFail()

    private fun onClean(): TempoRamp {
        val streak = cleanStreak + 1
        if (streak < config.cleanRunsToStepUp || reachedTarget) {
            return copy(cleanStreak = streak, failStreak = 0)
        }
        val next = minOf(targetBpm, currentBpm + stepFrom(currentBpm))
        return copy(currentBpm = next, cleanStreak = 0, failStreak = 0, history = history + currentBpm)
    }

    private fun onFail(): TempoRamp {
        val streak = failStreak + 1
        if (streak < config.failedRunsToStepDown || history.isEmpty()) {
            return copy(cleanStreak = 0, failStreak = streak)
        }
        return copy(currentBpm = history.last(), cleanStreak = 0, failStreak = 0, history = history.dropLast(1))
    }

    private fun stepFrom(bpm: Int): Int =
        if (bpm > config.percentStepAboveBpm) maxOf(1, (bpm * config.stepPercent).roundToInt()) else config.stepBpm

    companion object {
        fun start(targetBpm: Int, config: TempoRampConfig = TempoRampConfig()): TempoRamp {
            require(targetBpm > 0)
            val start = maxOf(config.minStartBpm, (targetBpm * config.startFraction).roundToInt())
            return TempoRamp(targetBpm, minOf(start, targetBpm), config = config)
        }
    }
}
