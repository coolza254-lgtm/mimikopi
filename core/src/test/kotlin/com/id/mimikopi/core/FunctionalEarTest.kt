package com.id.mimikopi.core

import com.id.mimikopi.core.ear.CadenceMode
import com.id.mimikopi.core.ear.ToneStats
import com.id.mimikopi.core.ear.ToneRun
import com.id.mimikopi.core.ear.EarScale
import com.id.mimikopi.core.ear.FunctionalEar
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FunctionalEarTest {
    @Test
    fun introLevelsAskWhatTheirTitlesSay() {
        val i = FunctionalEar.intro
        assertEquals(listOf(0, 2), i[0].notes)
        assertEquals(listOf(0, 2), i[0].answers)
        // "Ti do": the ti below the high do and the high do; answers are places, so the high do is "do" (0).
        assertEquals(listOf(11, 12), i[3].notes)
        assertEquals(listOf(0, 11), i[3].answers)
        assertEquals(8, i[6].notes.size) // do to do
        assertEquals(15, i[8].notes.size) // two octaves of the scale
        assertEquals(i.size, i.map { it.id }.toSet().size)
        assertEquals(FunctionalEar.basic.size, FunctionalEar.basic.map { it.id }.toSet().size)
    }

    @Test
    fun cadencesAreTheRightChords() {
        val major = FunctionalEar.cadence(EarScale.MAJOR).map { c -> c.map { Math.floorMod(it, 12) }.toSet() }
        assertEquals(listOf(setOf(0, 4, 7), setOf(5, 9, 0), setOf(7, 11, 2), setOf(0, 4, 7)), major)
        val minor = FunctionalEar.cadence(EarScale.MINOR).map { c -> c.map { Math.floorMod(it, 12) }.toSet() }
        // i-iv-V-i: the dominant keeps the raised leading tone.
        assertEquals(listOf(setOf(0, 3, 7), setOf(5, 8, 0), setOf(7, 11, 2), setOf(0, 3, 7)), minor)
    }

    @Test
    fun notesResolveStepwiseToTheNearestDo() {
        assertEquals(listOf(0), FunctionalEar.resolution(0, EarScale.MAJOR))
        assertEquals(listOf(2, 0), FunctionalEar.resolution(2, EarScale.MAJOR))
        assertEquals(listOf(5, 4, 2, 0), FunctionalEar.resolution(5, EarScale.MAJOR))
        assertEquals(listOf(7, 9, 11, 12), FunctionalEar.resolution(7, EarScale.MAJOR))
        assertEquals(listOf(11, 12), FunctionalEar.resolution(11, EarScale.MAJOR))
        assertEquals(listOf(-1, 0), FunctionalEar.resolution(-1, EarScale.MAJOR)) // ti below resolves up to the middle do
        assertEquals(listOf(-10, -12), FunctionalEar.resolution(-10, EarScale.MAJOR))
        assertEquals(listOf(8, 10, 12), FunctionalEar.resolution(8, EarScale.MINOR))
        assertEquals(listOf(1, 0), FunctionalEar.resolution(1, EarScale.MAJOR))
    }

    @Test
    fun questionsDoNotRepeatTheSameNoteTwiceInARow() {
        val level = FunctionalEar.intro[0]
        val r = Random(7)
        var prev: Int? = null
        repeat(50) { val n = FunctionalEar.pick(level, r, prev); assertNotEquals(prev, n); assertTrue(n in level.notes); prev = n }
    }

    @Test
    fun aKeyAnswersInAnyOctave() {
        val base = FunctionalEar.base(0)
        assertEquals(60, base)
        assertTrue(FunctionalEar.isRightKey(62, base, 2))
        assertTrue(FunctionalEar.isRightKey(74, base, 2))
        assertTrue(FunctionalEar.isRightKey(48, base, 12))
        assertFalse(FunctionalEar.isRightKey(64, base, 2))
        // Every key sits between F#3 and F4.
        assertTrue((0..11).all { FunctionalEar.base(it) in 54..65 })
    }

    @Test
    fun customLevelsKeepTheChosenPlacesAndRange() {
        val l = FunctionalEar.custom("c1", 8, EarScale.MAJOR, 2, chromatic = false, degrees = listOf(0, 1, 4, 7), questions = 20, cadence = CadenceMode.FIRST)
        assertEquals(listOf(0, 4, 7), l.degrees) // ♭2 dropped without chromatics
        assertEquals("A♭ เมเจอร์", l.title)
        assertTrue(l.notes.all { it in -12..12 && Math.floorMod(it, 12) in listOf(0, 4, 7) })
        val back = FunctionalEar.parseLevels(FunctionalEar.levelsJson(listOf(l)))
        assertEquals(listOf(l), back)
    }

    @Test
    fun statsKeepTheRecentRuns() {
        var s = ToneStats()
        repeat(FunctionalEar.KEEP_RUNS + 5) { s = s.plus(ToneRun("2026-10-17", 10, it % 11)) }
        assertEquals(FunctionalEar.KEEP_RUNS, s.runs.size)
        assertEquals(s, FunctionalEar.parseStats(FunctionalEar.statsJson(mapOf("x" to s)))["x"])
        assertEquals(0.4, ToneRun("d", 10, 4).accuracy, 1e-9)
    }
}
