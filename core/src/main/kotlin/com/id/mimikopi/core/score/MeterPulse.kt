package com.id.mimikopi.core.score

/** Clicks are separate from the quarter-note coordinates used by the score, judge and audio scheduler. */
data class MeterPulse(val clicksPerMeasure: Int, val quarterBeatsPerClick: Double, val ticksPerBeat: Int) {
    val countInClicks: Int get() = if (clicksPerMeasure >= 3) clicksPerMeasure else clicksPerMeasure * 2
    val levels: IntArray get() = IntArray(clicksPerMeasure) { if (it == 0) 0 else 2 }
    fun nativeBpm(quarterBpm: Double): Double = quarterBpm / (quarterBeatsPerClick * ticksPerBeat)
    fun clickMillis(quarterBpm: Double): Double = 60_000.0 / quarterBpm * quarterBeatsPerClick

    companion object {
        fun of(score: Score) = of(score.beatsPerMeasure, score.beatUnit)

        fun of(numerator: Int, denominator: Int): MeterPulse {
            require(numerator in 1..12 && denominator in listOf(2, 4, 8))
            return when {
                denominator == 8 && numerator >= 6 && numerator % 3 == 0 -> MeterPulse(numerator / 3, 1.5, 1)
                denominator == 8 -> MeterPulse(numerator, .5, 2)
                denominator == 2 -> MeterPulse(numerator * 2, 1.0, 1)
                else -> MeterPulse(numerator, 1.0, 1)
            }
        }
    }
}
