package com.id.mimikopi.core

import com.id.mimikopi.core.exercise.ChordDrills
import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.exercise.ChordRun
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.theory.StageGenerators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordPracticeTest {
    @Test
    fun everyChoiceMakesAValidDrill() {
        for (key in ChordPractice.KEYS) {
            for (g in ChordPractice.GROUPS) {
                val d = ChordPractice(ChordPractice.Mode.NAME, listOf(key), groups = listOf(g.id)).drill()
                assertTrue(d.problems().isEmpty(), "${g.id} in $key: ${d.problems()}")
                assertEquals(10, ChordDrills.build(d, 1).size)
            }
            for (n in ChordPractice.NUMERAL_SETS) {
                val d = ChordPractice(ChordPractice.Mode.NUMERAL, listOf(key), numeralSets = listOf(n.id)).drill()
                assertTrue(d.problems().isEmpty(), "${n.id} in $key: ${d.problems()}")
                // Every numeral resolves to a chord in every key.
                n.members.forEach { StageGenerators.degreeChord(it, key) }
                assertTrue(ChordDrills.build(d, 2).isNotEmpty())
            }
            for (p in StageGenerators.PROGRESSIONS) {
                val d = ChordPractice(ChordPractice.Mode.CHANGE, listOf(key), progression = p.name).drill()
                assertEquals(ExerciseType.E_CHORD_CHANGE, d.type)
                assertTrue(d.problems().isEmpty(), "${p.name}: ${d.problems()}")
                assertEquals(p.degrees.size, ChordDrills.changes(d).size)
            }
        }
    }

    @Test
    fun nothingChosenFallsBackToMajorAndMinorInC() {
        val d = ChordPractice(ChordPractice.Mode.NAME, keys = emptyList(), groups = emptyList()).drill()
        assertEquals(listOf("", "m"), d.qualities)
        assertEquals(listOf(0), d.keys)
    }

    @Test
    fun settingsAndRunsSurviveARoundTrip() {
        val p = ChordPractice(ChordPractice.Mode.NUMERAL, listOf(1, -1), numeralSets = listOf("sevenths"), items = 20)
        assertEquals(p, ChordPractice.parse(ChordPractice.encode(p)))
        assertEquals(ChordPractice(), ChordPractice.parse("not json"))
        var runs = emptyList<ChordRun>()
        repeat(25) { runs = ChordRun.add(runs, ChordRun("2026-10-17", "s$it", "r")) }
        assertEquals(ChordRun.KEEP, runs.size)
        assertEquals("s24", runs.first().summary)
        assertEquals(runs, ChordRun.parse(ChordRun.encode(runs)))
    }
}
