package com.id.mimikopi.core

import com.id.mimikopi.core.practice.OnsetJudgement
import com.id.mimikopi.core.practice.RunSummary
import com.id.mimikopi.core.practice.TempoRamp
import com.id.mimikopi.core.practice.TimingMode
import com.id.mimikopi.core.practice.TimingRules
import com.id.mimikopi.core.practice.isClean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimingRulesTest {
    private val rules = TimingRules()

    @Test
    fun slowTempoUsesStageTable() {
        assertEquals(150, rules.toleranceMs(stage = 1, tempoBpm = 60.0))
        assertEquals(120, rules.toleranceMs(stage = 2, tempoBpm = 80.0))
        assertEquals(90, rules.toleranceMs(stage = 6, tempoBpm = 72.0))
    }

    @Test
    fun fastTempoCapsAtQuarterOfABeat() {
        // 160 bpm -> beat 375 ms -> 25% = 93.75 ms < 150
        assertEquals(94, rules.toleranceMs(stage = 1, tempoBpm = 160.0))
        // 100 bpm -> beat 600 ms -> 150 ms; stage 4 = 100 is smaller
        assertEquals(100, rules.toleranceMs(stage = 4, tempoBpm = 100.0))
    }

    @Test
    fun modesMultiply() {
        assertEquals(75, rules.toleranceMs(4, 60.0, TimingMode.STRICT))
        assertEquals(130, rules.toleranceMs(4, 60.0, TimingMode.FORGIVING))
    }

    @Test
    fun latencyIsSubtractedBeforeJudging() {
        // Played 180 ms "late" through a route with 120 ms latency -> really 60 ms late.
        val (offset, judgement) = rules.judge(expectedOnsetMs = 1000, actualOnsetMs = 1180, latencyMs = 120, toleranceMs = 100)
        assertEquals(60, offset)
        assertEquals(OnsetJudgement.ON_TIME, judgement)
        assertEquals(OnsetJudgement.EARLY, rules.judge(1000, 850, 0, 100).second)
        assertEquals(OnsetJudgement.LATE, rules.judge(1000, 1101, 0, 100).second)
        assertEquals(OnsetJudgement.ON_TIME, rules.judge(1000, 1100, 0, 100).second) // boundary inclusive
    }
}

class CleanRunTest {
    @Test
    fun thresholds() {
        assertTrue(RunSummary(expectedNotes = 20, correctNotes = 19, notesWithinTiming = 18, stumbles = 1).isClean())
        assertFalse(RunSummary(20, 18, 20, 0).isClean()) // 90% notes
        assertFalse(RunSummary(20, 20, 17, 0).isClean()) // 85% timing
        assertFalse(RunSummary(20, 20, 20, 2).isClean()) // two stumbles
    }
}

class TempoRampTest {
    @Test
    fun startsAtHalfButNotBelow40() {
        assertEquals(50, TempoRamp.start(100).currentBpm)
        assertEquals(40, TempoRamp.start(60).currentBpm)
        assertEquals(30, TempoRamp.start(30).currentBpm) // never above the target itself
    }

    @Test
    fun stepsUpAfterThreeCleanRuns() {
        var ramp = TempoRamp.start(100) // 50
        ramp = ramp.onRun(true).onRun(true)
        assertEquals(50, ramp.currentBpm)
        ramp = ramp.onRun(true)
        assertEquals(55, ramp.currentBpm)
        assertEquals(0, ramp.cleanStreak)
    }

    @Test
    fun usesTenPercentStepsAbove80AndStopsAtTarget() {
        var ramp = TempoRamp(targetBpm = 100, currentBpm = 85)
        repeat(3) { ramp = ramp.onRun(true) }
        assertEquals(94, ramp.currentBpm) // 85 + 8.5 rounded
        repeat(3) { ramp = ramp.onRun(true) }
        assertEquals(100, ramp.currentBpm) // capped
        assertTrue(ramp.reachedTarget)
        repeat(3) { ramp = ramp.onRun(true) }
        assertEquals(100, ramp.currentBpm)
    }

    @Test
    fun stepsBackOneLevelAfterTwoFailures() {
        var ramp = TempoRamp.start(100)
        repeat(6) { ramp = ramp.onRun(true) } // 50 -> 55 -> 60
        assertEquals(60, ramp.currentBpm)
        ramp = ramp.onRun(false)
        assertEquals(60, ramp.currentBpm)
        ramp = ramp.onRun(false)
        assertEquals(55, ramp.currentBpm)
        // A clean run resets the failure streak.
        ramp = ramp.onRun(false).onRun(true).onRun(false)
        assertEquals(55, ramp.currentBpm)
    }

    @Test
    fun neverDropsBelowStart() {
        var ramp = TempoRamp.start(100)
        repeat(10) { ramp = ramp.onRun(false) }
        assertEquals(50, ramp.currentBpm)
    }
}
