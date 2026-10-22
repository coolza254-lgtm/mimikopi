package com.id.mimikopi.core.playalong

import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.practice.isClean
import com.id.mimikopi.core.score.Hand
import kotlin.math.abs

/** Coach feedback after a run (learning-system.md 7). The UI turns each one into a short Thai sentence. */
sealed interface Advice {
    /** The right notes keep landing an octave too high or too low (reading the octave, not the note, is the problem). */
    data class OctaveOff(val higher: Boolean) : Advice
    /** The same note keeps going wrong. */
    data class TroubleNote(val pitch: SpelledPitch) : Advice
    /** Right notes, consistently early or late: check latency calibration first, then count aloud. */
    data class ConsistentlyOff(val early: Boolean, val meanOffsetMs: Int) : Advice
    /** Errors cluster across a bar line: practise across the join. */
    data class Chain(val fromMeasure: Int, val toMeasure: Int) : Advice
    /** Only one hand went wrong while both played: practise that hand alone first. */
    data class HandAlone(val hand: Hand) : Advice
    /** Too fast for now. */
    data class SlowDown(val bpm: Int) : Advice
    /** Clean: repeat once more or move to the next tempo/section. */
    data object Clean : Advice
}

object Coach {
    /**
     * At most three pieces of advice, most useful first. [slowerBpm] is the tempo the ramp would fall back
     * to (or a 10% slower value) for the SlowDown hint.
     */
    fun advise(result: PlayResult, slowerBpm: Int): List<Advice> {
        val summary = result.summary ?: return emptyList()
        if (result.timingJudged && summary.isClean()) return listOf(Advice.Clean)
        val out = mutableListOf<Advice>()
        // First: slowing down does not help a hand that sits an octave away.
        val slips = result.octaveUp + result.octaveDown
        if (slips >= 2 && slips * 3 >= result.wrongPresses.size) out += Advice.OctaveOff(result.octaveUp >= result.octaveDown)
        if (result.timingJudged && summary.noteAccuracy < 0.8) out += Advice.SlowDown(slowerBpm)
        val hands = result.setup.hands
        if (hands.size == 2) {
            val r = result.errorsByHand[Hand.RIGHT] ?: 0
            val l = result.errorsByHand[Hand.LEFT] ?: 0
            if (l >= 2 && r == 0) out += Advice.HandAlone(Hand.LEFT)
            if (r >= 2 && l == 0) out += Advice.HandAlone(Hand.RIGHT)
        }
        val p = result.problemMeasures
        if (p.size >= 2) {
            val sorted = p.take(2).sorted()
            if (sorted[1] - sorted[0] == 1) out += Advice.Chain(sorted[0], sorted[1])
        }
        result.troubleNotes.firstOrNull()?.let { out += Advice.TroubleNote(it) }
        val mean = result.meanOffsetMs
        if (mean != null && abs(mean) > result.setup.toleranceMs * 0.5) {
            val hits = result.outcomes.values.filter { it.offsetMs != null }
            val sameSide = hits.count { (it.offsetMs!! < 0) == (mean < 0) }
            if (hits.size >= 4 && sameSide >= hits.size * 0.75) out += Advice.ConsistentlyOff(mean < 0, mean.toInt())
        }
        return out.take(3)
    }
}
