package com.id.mimikopi.core

import com.id.mimikopi.core.ear.Compare
import com.id.mimikopi.core.ear.Degree
import com.id.mimikopi.core.ear.Dir
import com.id.mimikopi.core.ear.EarAnswers
import com.id.mimikopi.core.ear.EarGenerator
import com.id.mimikopi.core.ear.EarKey
import com.id.mimikopi.core.ear.EarLevel
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.EarStats
import com.id.mimikopi.core.ear.IntervalPlay
import com.id.mimikopi.core.ear.chordNotes
import com.id.mimikopi.core.ear.earNoteName
import com.id.mimikopi.core.ear.scaleNote
import com.id.mimikopi.core.score.Score
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EarTest {
    @Test
    fun everyLevelGeneratesPlayableQuestionsInRange() {
        for (level in EarLevel.entries) {
            val g = EarGenerator(level.ordinal.toLong())
            repeat(300) {
                val q = g.next(level)
                assertEquals(level, q.level, "$q")
                assertTrue(q.itemIds.isNotEmpty())
                assertTrue(q.itemIds.all { id -> level in EarStats.levelsOf(id) }, "$level ${q.itemIds}")
                q.sounds().forEach { s -> assertTrue(s.midi in 21..108 && s.atMs >= 0 && s.durMs > 0, "$level $s") }
                when (q) {
                    is EarQuestion.Interval -> {
                        assertTrue(q.semitones in (if (level == EarLevel.E2) 1..7 else 1..12))
                        if (level == EarLevel.E2) assertEquals(IntervalPlay.UP, q.play)
                        assertEquals(q.semitones, kotlin.math.abs(q.target - q.root))
                    }
                    is EarQuestion.Melody -> {
                        assertTrue(q.notes.size in 3..4)
                        val steps = q.notes.zipWithNext { a, b -> kotlin.math.abs(a - b) }
                        if (!q.skips) assertTrue(steps.all { it in 1..2 }, "steps only: ${q.notes}")
                    }
                    is EarQuestion.Rhythm -> Score.from(q.piece)
                    is EarQuestion.Progression -> assertEquals(4, q.degrees.size)
                    is EarQuestion.Mixed -> assertTrue(q.melody.size in 4..8 && q.melody.all { it in 60..79 })
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun answersAndTheory() {
        assertEquals(Compare.HIGHER, EarQuestion.Higher(60, 64).answer)
        assertEquals(Compare.SAME, EarQuestion.Higher(60, 60).answer)
        assertEquals(listOf(Dir.UP, Dir.SAME, Dir.DOWN), EarQuestion.Direction(listOf(60, 62, 62, 59)).answers)
        assertEquals(55, EarQuestion.Interval(EarLevel.E4, 60, 5, IntervalPlay.DOWN).target)
        assertEquals(listOf(60, 64, 67), chordNotes(EarKey.C, Degree.I).first)
        assertEquals(listOf(57, 60, 64), chordNotes(EarKey.C, Degree.vi).first) // A minor: A3 C4 E4
        assertEquals(71, scaleNote(60, 6))
        assertEquals(59, scaleNote(60, -1))
        val bass = EarQuestion.BassLine(EarKey.C, listOf(Degree.I to 0, Degree.V to 2, Degree.vi to 0, Degree.IV to 0))
        assertEquals(listOf(1, 7, 6, 4), bass.bassDegrees) // C - B (G/B) - A - F
        assertTrue(EarAnswers.bassMatches(bass, listOf(48, 47, 45, 41)))
        assertTrue(EarAnswers.melodyMatches(listOf(60, 62, 64), listOf(62, 64)))
        assertFalse(EarAnswers.melodyMatches(listOf(60, 62, 64), listOf(62, 65)))
        assertTrue(EarAnswers.tonicMatches(EarKey.G, 43))
        assertEquals("Bb4", earNoteName(70, EarKey.F).toString())
        assertEquals("A#4", earNoteName(70, EarKey.C).toString())
    }

    @Test
    fun weakItemsAreChosenMoreOften() {
        val stats = listOf(EarStats.ItemStat("ear:int:3", 1, 10, null), EarStats.ItemStat("ear:int:7", 10, 10, null))
        val w = EarStats.weights(stats)
        assertTrue(w.getValue("ear:int:3") > 2.0 && w.getValue("ear:int:7") == 0.5)
        val g = EarGenerator(1, w)
        val counts = List(2000) { (g.next(EarLevel.E2) as EarQuestion.Interval).semitones }.groupingBy { it }.eachCount()
        assertTrue(counts.getValue(3) > counts.getValue(7) * 3, "$counts")
    }

    @Test
    fun unlockingAndBenchmarkSet() {
        assertEquals(listOf(EarLevel.E1, EarLevel.E2), EarStats.unlocked(1))
        assertTrue(EarLevel.E6 in EarStats.unlocked(2) && EarLevel.E7 !in EarStats.unlocked(3))
        assertEquals(EarStats.benchmarkSet(), EarStats.benchmarkSet())
        assertEquals(20, EarStats.benchmarkSet().size)
        val mixed = EarGenerator(5).mixed(listOf(EarLevel.E1, EarLevel.E3), 40)
        assertTrue(mixed.any { it.level == EarLevel.E1 } && mixed.any { it.level == EarLevel.E3 })
    }
}
