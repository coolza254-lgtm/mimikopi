package com.id.mimikopi.core.practice

import com.id.mimikopi.core.config.TimingConfig
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TimingMode { STRICT, NORMAL, FORGIVING }

enum class OnsetJudgement { EARLY, ON_TIME, LATE }

/** Timing tolerance rules from learning-system.md 6.2. */
class TimingRules(private val config: TimingConfig = TimingConfig()) {

    /** Allowed ± onset error in ms for a stage (1..6) at a tempo, in a strictness mode. */
    fun toleranceMs(stage: Int, tempoBpm: Double, mode: TimingMode = TimingMode.NORMAL): Int {
        val stageMs = config.stageToleranceMs[stage.coerceIn(1, 6)]
            ?: error("no tolerance configured for stage $stage")
        val base = if (tempoBpm <= config.slowTempoBpm) {
            stageMs.toDouble()
        } else {
            minOf(stageMs.toDouble(), config.fastTempoBeatFraction * beatMs(tempoBpm))
        }
        val multiplier = when (mode) {
            TimingMode.STRICT -> config.strictMultiplier
            TimingMode.NORMAL -> config.normalMultiplier
            TimingMode.FORGIVING -> config.forgivingMultiplier
        }
        return (base * multiplier).roundToInt()
    }

    /**
     * Judges one onset. [latencyMs] is the calibrated delay of the input/output route; it is
     * subtracted from the raw offset *before* the tolerance is applied.
     * Returns the compensated offset (negative = early) and the judgement.
     */
    fun judge(expectedOnsetMs: Long, actualOnsetMs: Long, latencyMs: Long, toleranceMs: Int): Pair<Long, OnsetJudgement> {
        val offset = actualOnsetMs - expectedOnsetMs - latencyMs
        val judgement = when {
            abs(offset) <= toleranceMs -> OnsetJudgement.ON_TIME
            offset < 0 -> OnsetJudgement.EARLY
            else -> OnsetJudgement.LATE
        }
        return offset to judgement
    }

    companion object {
        fun beatMs(tempoBpm: Double): Double = 60_000.0 / tempoBpm
    }
}
