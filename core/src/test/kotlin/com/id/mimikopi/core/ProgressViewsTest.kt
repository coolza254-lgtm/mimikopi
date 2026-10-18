package com.id.mimikopi.core

import com.id.mimikopi.core.ear.EarItemStat
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarSession
import com.id.mimikopi.core.ear.EarState
import com.id.mimikopi.core.goal.IdolGoal
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.Fix
import com.id.mimikopi.core.progress.History
import com.id.mimikopi.core.progress.Mistakes
import com.id.mimikopi.core.progress.RunMistakes
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.session.DayLog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressViewsTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun day(back: Long) = today.minusDays(back).toString()

    @Test
    fun oneBadRunIsNotAPatternButThreeAre() {
        val one = listOf(RunMistakes(1, day(1), "piece", notes = 40, meanOffsetMs = -80))
        assertTrue(Mistakes.summarize(one, null, today).none { it.id == "rush" })
        val three = (1..3).map { RunMistakes(it.toLong(), day(it.toLong()), "piece", notes = 40, meanOffsetMs = -50) }
        val m = Mistakes.summarize(three, null, today).first { it.id == "rush" }
        assertTrue(m.detail.contains("50"))
        assertEquals(Fix.Metronome, m.fix)
        // Old runs (outside the window) do not count.
        val old = (30..32).map { RunMistakes(it.toLong(), day(it.toLong()), "piece", notes = 40, meanOffsetMs = -50) }
        assertTrue(Mistakes.summarize(old, null, today).isEmpty())
    }

    @Test
    fun aWeakHandRepeatedNotesConfusionsStopsAndEarAreFound() {
        val runs = (1..4).map {
            RunMistakes(it.toLong(), day(it.toLong()), "piece", notes = 30, leftNotes = 15, leftErrors = 3, rightNotes = 15, rightErrors = 0,
                trouble = listOf("F4"), stumbles = 2, confusions = listOf("E4>F4"))
        }
        val ear = EarState(items = mapOf("deg:4" to EarItemStat(8, 2, 0.3, 1, 2000)), sessions = listOf(EarSession(1, day(1), 8, 2)))
        val ids = Mistakes.summarize(runs, ear, today).map { it.id }
        assertTrue("hand" in ids && "notes" in ids && "confuse" in ids && "stumble" in ids && "ear:deg:4" in ids, "$ids")
        val hand = Mistakes.summarize(runs, null, today).first { it.id == "hand" }
        assertTrue(hand.title.contains("ซ้าย"))
        assertEquals(Fix.Ear(EarKind.DEGREE), Mistakes.summarize(runs, ear, today).first { it.id.startsWith("ear") }.fix)
        assertEquals(runs, Mistakes.merge(runs, runs.take(2)))
    }

    @Test
    fun levelsOverTimeAndTheirChangesComeFromTheTests() {
        val records = listOf(
            CheckRecord(SkillId.KEYS, 1, day(20), true),
            CheckRecord(SkillId.KEYS, 2, day(10), true),
            CheckRecord(SkillId.NOTES, 1, day(10), true),
            CheckRecord(SkillId.NOTES, 1, day(5), false),
            CheckRecord(SkillId.NOTES, 1, day(4), false),
        )
        val series = History.levelSeries(records, today, 30)
        assertEquals(listOf(0, 1, 3, 3, 2, 2), series.map { it.second })
        val changes = History.changes(records, today, 30)
        assertEquals(SkillId.NOTES, changes.first().skill)
        assertEquals(1 to 0, changes.first().from to changes.first().to)
        assertEquals(History.MAX_TOTAL, SkillId.entries.sumOf { SkillBook.maxLevel(it) })
    }

    @Test
    fun weeklyMinutesAndEarAccuracyLineUpByWeek() {
        val log = listOf(DayLog(day(0), mapOf("ladder" to 600)), DayLog(day(8), mapOf("ear" to 1200)))
        val weeks = History.weeklyMinutes(log, today, 3)
        assertEquals(3, weeks.size)
        assertEquals(10, weeks.last().second)
        assertEquals(20, weeks[weeks.size - 2].second + weeks[weeks.size - 3].second)
        val ear = EarState(sessions = listOf(EarSession(1, day(0), 10, 8)))
        assertEquals(0.8, History.earWeekly(ear, today, 2).last().second)
        assertEquals(null, History.earWeekly(ear, today, 2).first().second)
    }

    @Test
    fun theGoalSaysWhatEachSongStillNeedsAndItsDrillsAreValid() {
        val statuses = SkillId.entries.map { SkillBook.status(it, listOf(CheckRecord(SkillId.EAR, 1, day(1), true)), today) }
        val first = IdolGoal.SONGS.first()
        val gaps = IdolGoal.gaps(first, statuses)
        assertEquals(first.needs.size, gaps.size)
        assertTrue(gaps.first().need - gaps.first().have >= gaps.last().need - gaps.last().have)
        assertTrue(IdolGoal.readiness(first, statuses) in 0.0..0.2)
        for (song in IdolGoal.SONGS) assertTrue(song.needs.all { (s, n) -> n in 1..SkillBook.maxLevel(s) }, song.title)
        for (d in IdolGoal.DRILLS) assertTrue(d.practice.drill().problems().isEmpty(), d.title)
    }
}
