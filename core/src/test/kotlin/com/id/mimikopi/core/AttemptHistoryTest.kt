package com.id.mimikopi.core

import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.practice.ReadingClef
import com.id.mimikopi.core.practice.ReadingPractice
import com.id.mimikopi.core.practice.ReadingRange
import com.id.mimikopi.core.progress.AttemptHistory
import com.id.mimikopi.core.progress.HistoryAttempt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttemptHistoryTest {
    private fun row(id: Long, params: String = """{"completed":true,"mode":"TEMPO","bpm":60,"from":0,"to":3,"toleranceMs":100,"inputSources":"TOUCH"}""", hand: String = "RIGHT", helper: Int = 0) =
        HistoryAttempt(AttemptRow(id, null, "1.5:E_FINGER_PATTERN:0", params, id * 1000, hand, 60.0, .8, .7, 2, false, helper))

    @Test fun comparisonIsChronologicalAndNeverMixesHandsOrHelpers() {
        val rows = listOf(row(4, hand = "LEFT"), row(3, helper = 1), row(2), row(1))
        assertNull(AttemptHistory.comparison(rows))
        val right = AttemptHistory.comparison(rows.filter { it.row.hand == "RIGHT" })!!
        assertEquals(1L, right.first.row.id)
        assertEquals(2L, right.latest.row.id)
    }

    @Test fun changedConditionsAreNotImprovement() {
        val baseline = row(1)
        for ((old, new) in listOf("60" to "80", "\"to\":3" to "\"to\":1", "100" to "200", "TOUCH" to "USB_MIDI", "TOUCH" to "COMPUTER_KEYS", "TEMPO" to "WAIT")) {
            val changed = row(2, baseline.row.paramsJson.replace(old, new))
            assertNull(AttemptHistory.comparison(listOf(baseline, changed)), "$old → $new")
        }
    }

    @Test fun stoppedLegacyAndEmptyRoundsStayOut() {
        val excluded = listOf(row(2, "{}"), row(3, "bad"), row(4, """{"completed":false,"mode":"TEMPO"}"""),
            row(5, """{"completed":true,"mode":"TEMPO","inputPresent":false}"""), row(6, helper = 1))
        assertTrue(excluded.none { it.comparable })
        assertNull(AttemptHistory.comparison(listOf(row(1)) + excluded))
        assertNotNull(AttemptHistory.comparison(listOf(row(1, """{"seed":1,"completed":true,"mode":"ANSWER"}"""), row(2, """{"seed":9,"completed":true,"mode":"ANSWER"}"""))))
    }

    @Test fun readingPoolsAreExactPitches() {
        val pool = ReadingPractice(ReadingClef.BOTH, ReadingRange.FIVE).drill().pool
        assertTrue("C4:treble" in pool && "F3:bass" in pool)
        assertTrue(pool.none { '#' in it || 'b' in it.substringBefore(':') })
    }
}
