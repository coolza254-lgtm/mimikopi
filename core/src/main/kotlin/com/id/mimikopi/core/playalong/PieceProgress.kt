package com.id.mimikopi.core.playalong

import com.id.mimikopi.core.config.CleanRunConfig
import com.id.mimikopi.core.config.TempoRampConfig
import com.id.mimikopi.core.practice.TempoRamp
import com.id.mimikopi.core.practice.isClean
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.Score
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Stored form of a [TempoRamp] (the config comes from learning.json, not from storage). */
@Serializable
data class RampState(val currentBpm: Int, val cleanStreak: Int = 0, val failStreak: Int = 0, val history: List<Int> = emptyList()) {
    fun toRamp(targetBpm: Int, config: TempoRampConfig) = TempoRamp(targetBpm, currentBpm, cleanStreak, failStreak, history, config)

    companion object {
        fun of(r: TempoRamp) = RampState(r.currentBpm, r.cleanStreak, r.failStreak, r.history)
    }
}

/** Practice record of one piece (kept in the settings table, so backups include it). */
@Serializable
data class PieceProgress(
    val pieceId: String,
    val runs: Int = 0,
    /** Clean tempo-mode runs in a row of the whole piece, both hands, at the target tempo, without the name helper. */
    val cleanAtTargetStreak: Int = 0,
    /** Days (ISO dates) on which the streak reached three (learning-system.md 6.4). */
    val passDays: List<String> = emptyList(),
    /** Recent error weight per measure (decays every run), for the problem-spot list. */
    val measureHeat: Map<Int, Double> = emptyMap(),
    /** Tempo ramp per practice target: "<hands>|<from>-<to>". */
    val ramps: Map<String, RampState> = emptyMap(),
    val bestCleanBpm: Int? = null,
)

data class RunUpdate(val progress: PieceProgress, val newBpm: Int?, val passedToday: Boolean, val piecePassed: Boolean)

object PieceRules {
    const val HEAT_DECAY = 0.7
    const val CLEAN_RUNS_FOR_DAY = 3
    const val DAYS_TO_PASS = 2

    fun rampKey(hands: Set<Hand>, from: Int, to: Int): String =
        "${hands.sortedBy { it.ordinal }.joinToString("+") { it.name }}|$from-$to"

    fun ramp(progress: PieceProgress, key: String, targetBpm: Int, config: TempoRampConfig): TempoRamp =
        progress.ramps[key]?.toRamp(targetBpm, config) ?: TempoRamp.start(targetBpm, config)

    fun isPassed(p: PieceProgress): Boolean = p.passDays.toSet().size >= DAYS_TO_PASS

    /** Measures that keep going wrong, worst first. */
    fun hotMeasures(p: PieceProgress, limit: Int = 3): List<Int> =
        p.measureHeat.filter { it.value >= 1.0 }.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    /**
     * Folds one finished run into the record. Wait-mode runs only update the problem spots (timing is not
     * judged there). [useRamp] = false leaves the ramp untouched (the player set the tempo by hand).
     */
    fun afterRun(
        progress: PieceProgress,
        score: Score,
        result: PlayResult,
        helperUsed: Boolean,
        useRamp: Boolean,
        rampConfig: TempoRampConfig,
        cleanConfig: CleanRunConfig = CleanRunConfig(),
        day: LocalDate = LocalDate.now(),
    ): RunUpdate {
        val setup = result.setup
        val heat = progress.measureHeat.mapValues { it.value * HEAT_DECAY }.toMutableMap()
        for (m in result.perMeasure) if (m.errors > 0) heat[m.measure] = (heat[m.measure] ?: 0.0) + m.errors
        var p = progress.copy(runs = progress.runs + 1, measureHeat = heat.filterValues { it >= 0.05 })
        val summary = result.summary
        if (!result.timingJudged || summary == null) return RunUpdate(p, null, false, isPassed(p))

        val clean = summary.isClean(cleanConfig)
        var newBpm: Int? = null
        if (useRamp) {
            val key = rampKey(setup.hands, setup.fromMeasure, setup.toMeasure)
            val before = ramp(p, key, score.tempo, rampConfig)
            val after = before.onRun(clean)
            p = p.copy(ramps = p.ramps + (key to RampState.of(after)))
            if (after.currentBpm != before.currentBpm) newBpm = after.currentBpm
        }
        val bpm = setup.bpm.toInt()
        if (clean) p = p.copy(bestCleanBpm = maxOf(p.bestCleanBpm ?: 0, bpm))
        val wholePiece = setup.fromMeasure == 0 && setup.toMeasure == score.measures - 1 && setup.hands.size == Hand.entries.size
        val countsForPass = wholePiece && bpm >= score.tempo && !helperUsed
        var passedToday = false
        if (countsForPass) {
            val streak = if (clean) p.cleanAtTargetStreak + 1 else 0
            p = p.copy(cleanAtTargetStreak = streak)
            if (streak >= CLEAN_RUNS_FOR_DAY) {
                passedToday = true
                p = p.copy(cleanAtTargetStreak = 0, passDays = (p.passDays + day.toString()).distinct())
            }
        }
        return RunUpdate(p, newBpm, passedToday, isPassed(p))
    }
}
