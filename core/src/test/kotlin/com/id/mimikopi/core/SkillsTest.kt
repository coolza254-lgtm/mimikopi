package com.id.mimikopi.core

import com.id.mimikopi.core.exercise.ChordDrills
import com.id.mimikopi.core.exercise.Generators
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.progress.CheckMetrics
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillsTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun rec(level: Int, passed: Boolean, daysAgo: Long, skill: SkillId = SkillId.NOTES) =
        CheckRecord(skill, level, today.minusDays(daysAgo).toString(), passed)

    @Test
    fun everyTestIsAValidDrill() {
        for (s in SkillId.entries) for (l in SkillBook.levels(s)) {
            when (val r = l.run) {
                is CheckRun.DrillRun -> {
                    assertTrue(r.drill.problems().isEmpty(), "${s} ${l.level}: ${r.drill.problems()}")
                    when (r.drill.type) {
                        ExerciseType.E_NOTEID -> assertEquals(20, Generators.noteId(r.drill, 1, emptyList()).size)
                        ExerciseType.E_CHORD_BUILD -> assertTrue(ChordDrills.build(r.drill, 1).isNotEmpty())
                        ExerciseType.E_CHORD_CHANGE -> assertTrue(ChordDrills.changes(r.drill).isNotEmpty())
                        else -> Unit
                    }
                }
                is CheckRun.ScoreRun -> assertTrue(r.drill.problems().isEmpty(), "${s} ${l.level}: ${r.drill.problems()}")
                is CheckRun.EarRun -> assertTrue(r.levelId.isNotBlank())
            }
            assertTrue(l.criteriaText().isNotBlank())
        }
    }

    @Test
    fun aLevelIsEarnedByPassingAndLostAfterTwoFailsInARow() {
        assertEquals(0, SkillBook.status(SkillId.NOTES, emptyList(), today).level)
        val passed = listOf(rec(1, true, 20), rec(2, true, 14))
        assertEquals(2, SkillBook.status(SkillId.NOTES, passed, today).level)
        assertEquals(3, SkillBook.status(SkillId.NOTES, passed, today).nextTest)
        // One fail at the held level: kept. Two in a row: lost.
        assertEquals(2, SkillBook.status(SkillId.NOTES, passed + rec(2, false, 7), today).level)
        val lost = SkillBook.status(SkillId.NOTES, passed + rec(2, false, 7) + rec(2, false, 0), today)
        assertEquals(1, lost.level)
        assertTrue(lost.dropped)
        // Failing a higher level never lowers the level held.
        assertEquals(2, SkillBook.status(SkillId.NOTES, passed + rec(3, false, 1) + rec(3, false, 0), today).level)
    }

    @Test
    fun testsAreDueWeeklyAndLimitedToTwoFailedTriesADay() {
        val s = SkillBook.status(SkillId.NOTES, listOf(rec(1, true, 3)), today)
        assertFalse(s.due)
        assertTrue(SkillBook.status(SkillId.NOTES, listOf(rec(1, true, 7)), today).due)
        val locked = SkillBook.status(SkillId.NOTES, listOf(rec(1, true, 9), rec(2, false, 0), rec(2, false, 0)), today)
        assertTrue(locked.lockedToday)
        assertFalse(locked.due)
    }

    @Test
    fun placementComesFirstThenTheOldestAndTheWeakestGetsMoreTime() {
        val records = SkillId.entries.filter { it != SkillId.EAR }.map { rec(1, true, 10, it) } + rec(3, true, 10, SkillId.KEYS)
        assertEquals(SkillId.EAR, SkillBook.dueCheck(records, today)?.skill)
        val weakest = SkillBook.weakest(records, today)
        assertTrue(weakest != SkillId.KEYS && weakest != null)
        assertNull(SkillBook.overall(emptyList(), today))
    }

    @Test
    fun judgingSaysWhatWasReached() {
        val l = SkillBook.level(SkillId.NOTES, 2)!!
        val v = l.judge(CheckMetrics(accuracy = 0.95, medianSec = 3.4))
        assertFalse(v.passed)
        assertEquals(2, v.lines.size)
        assertTrue(l.judge(CheckMetrics(accuracy = 0.9, medianSec = 2.0)).passed)
        assertEquals(l, SkillBook.parseKey(SkillBook.key(SkillId.NOTES, 2)))
    }
}
