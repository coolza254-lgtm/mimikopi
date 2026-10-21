package com.id.mimikopi.ui.lesson

import com.id.mimikopi.data.db.AttemptEntity
import org.junit.Test
import kotlin.test.*

class LessonHistoryTest {
    private fun row(id: Long, params: String = """{"completed":true,"mode":"TEMPO","bpm":60,"from":0,"to":3,"toleranceMs":100,"inputSources":"TOUCH"}""", hand: String = "RIGHT", helper: Int = 0) =
        AttemptEntity(id, null, "1.5:E_FINGER_PATTERN:0", params, id * 1000, hand, 60.0, .8, .7, 2, false, helper)

    @Test fun comparisonUsesChronologicalOrderWithoutMixingHandsOrHelpers() {
        val rows = listOf(row(4, hand = "LEFT"), row(3, helper = 1), row(2), row(1)).map(::LessonAttempt)
        assertNull(lessonComparison(rows), "The newest eligible left-hand attempt has no matching baseline")
        val right = lessonComparison(rows.filter { it.row.hand == "RIGHT" })!!
        assertEquals(1L, right.first.row.id)
        assertEquals(2L, right.latest.row.id)
    }
    @Test fun tempoSectionTimingAndInputChangesCannotAppearAsImprovement() {
        val baseline = row(1)
        for ((old, new) in listOf("60" to "80", "\"to\":3" to "\"to\":1", "100" to "200", "TOUCH" to "USB_MIDI", "TEMPO" to "WAIT")) {
            val changed = row(2, baseline.paramsJson.replace(old, new))
            assertNull(lessonComparison(listOf(baseline, changed).map(::LessonAttempt)), "$old → $new")
        }
    }
    @Test fun legacyCorruptStoppedAndEmptyInputRemainVisibleButCannotSupplyEvidence() {
        val excluded = listOf(row(2, "{}"), row(3, "bad"), row(4, """{"completed":false,"mode":"TEMPO"}"""),
            row(5, """{"completed":true,"mode":"TEMPO","inputPresent":false}"""), row(6, helper = 1))
        assertTrue(excluded.map(::LessonAttempt).none { it.comparable })
        assertNull(lessonComparison((listOf(row(1)) + excluded).map(::LessonAttempt)))
    }
    @Test fun randomSeedsMayVaryWithoutChangingTheExerciseConditions() {
        val a = row(1, """{"seed":1,"completed":true,"mode":"ANSWER"}""")
        val b = row(2, """{"seed":999,"completed":true,"mode":"ANSWER"}""")
        assertNotNull(lessonComparison(listOf(a, b).map(::LessonAttempt)))
    }
}
