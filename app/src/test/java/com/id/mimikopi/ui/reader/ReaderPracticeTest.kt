package com.id.mimikopi.ui.reader

import com.id.mimikopi.core.playalong.PlayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ReaderPracticeTest {
    private val completed = ReaderUi(
        phase = Phase.RESULT, bpm = 100, targetBpm = 120, section = 0..7,
        options = ReaderOptions(mode = PlayMode.TEMPO, hands = HandChoice.BOTH, useRamp = true),
        pendingSectionStart = 4, message = "old feedback",
    )

    @Test fun correctiveLoopKeepsTheRequestedBarsAndSlowerTempoWithoutTheRampTakingOver() {
        val next = ResultAction.Loop(2, 3, 90).prepare(completed, 8)
        assertEquals(2..3, next.section)
        assertEquals(90, next.bpm)
        assertEquals(120, next.targetBpm)
        assertEquals(HandChoice.BOTH, next.options.hands)
        assertEquals(PlayMode.TEMPO, next.options.mode)
        assertFalse(next.options.useRamp)
        assertEquals(Phase.IDLE, next.phase)
        assertNull(next.pendingSectionStart)
        assertNull(next.message)
        assertNull(next.result)
    }

    @Test fun practisingOneHandPreservesTheSectionAndOtherHandPlayback() {
        val next = ResultAction.Hands(HandChoice.LEFT, 80).prepare(completed.copy(section = 3..5), 8)
        assertEquals(3..5, next.section)
        assertEquals(HandChoice.LEFT, next.options.hands)
        assertEquals(80, next.bpm)
        assertEquals(completed.options.playOtherHand, next.options.playOtherHand)
    }

    @Test fun aWaitModeLoopRemainsUntimedWhileATempoActionStartsTimedPractice() {
        val waiting = completed.copy(options = completed.options.copy(mode = PlayMode.WAIT))
        assertEquals(PlayMode.WAIT, ResultAction.Loop(0, 0, 60).prepare(waiting, 8).options.mode)
        val timed = ResultAction.Tempo(60).prepare(waiting, 8)
        assertEquals(PlayMode.TEMPO, timed.options.mode)
        assertEquals(60, timed.bpm)
        assertEquals(waiting.section, timed.section)
    }

    @Test fun theNextStepIsTheFirstAdviceThatCanBePractised() {
        val loop = CoachLine("ฝึกบาร์ที่พลาด", ResultAction.Loop(1, 1, 60))
        val tip = CoachLine("ส่วนใหญ่กดช้ากว่าจังหวะ ลองนับออกเสียงหรือเคาะเท้าไปด้วย")
        val result = ResultUi("ผล", "", false, listOf(tip, loop), null)
        assertEquals(loop, result.nextStep)
        assertEquals(tip, result.copy(coach = listOf(tip)).nextStep)
    }
}
