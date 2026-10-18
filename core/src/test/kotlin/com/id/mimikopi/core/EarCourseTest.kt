package com.id.mimikopi.core

import com.id.mimikopi.core.ear.EarAnswer
import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.ear.EarItemStat
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.ear.EarResult
import com.id.mimikopi.core.ear.EarState
import com.id.mimikopi.core.ear.KeyPool
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EarCourseTest {
    private val now = 1_800_000_000_000L

    @Test
    fun everyItemMakesAnAnswerableQuestionInEveryKey() {
        val all = EarCourse.ALL_ITEMS
        for (seed in 0 until 40) for (item in all) for (pool in KeyPool.entries) {
            val q = EarCourse.question(item, all, pool, wide = seed % 2 == 0, strength = (seed % 10) / 10.0, random = Random(seed * 31 + item.hashCode()))
            assertEquals(EarCourse.kindOf(item), q.kind)
            assertTrue(q.sound.isNotEmpty(), item)
            for (step in q.sound + q.after) assertTrue(step.notes.all { it in 24..100 }, "$item ${step.notes}")
            when (val a = q.answer) {
                is EarAnswer.Choice -> assertTrue(q.options.any { it.id == a.id }, "$item options ${q.options}")
                is EarAnswer.Notes -> {
                    assertEquals(item.substringAfter(':').toInt(), a.places.size)
                    assertTrue(a.places.all { it in -12..12 })
                }
                is EarAnswer.Taps -> {
                    assertEquals(0.0, a.beats.first())
                    assertTrue(a.beats.zipWithNext().all { (x, y) -> y > x } && a.beats.last() < 4.0, "$item ${a.beats}")
                }
            }
        }
        // Every test item is taught in some stage.
        assertTrue(EarCourse.TESTS.flatMap { it.items }.all { it in all })
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun melodiesUseOnlyTaughtNotesAndMoveMostlyByStep() {
        val pool = listOf(0, 2, 4, 5, 7)
        var steps = 0; var moves = 0
        repeat(300) { seed ->
            val m = EarCourse.melody(6, pool, wide = false, random = Random(seed))
            assertEquals(6, m.size)
            assertTrue(m.all { it in pool }, "$m")
            assertTrue(m.first() in setOf(0, 4, 7))
            m.zipWithNext().forEach { (a, b) -> moves++; if (kotlin.math.abs(pool.indexOf(a) - pool.indexOf(b)) == 1) steps++ }
        }
        assertTrue(steps.toDouble() / moves > 0.5, "steps $steps of $moves")
    }

    @Test
    fun aRoundStartsWithStageOneAndTheNextStageOpensOnceItIsMastered() {
        val fresh = EarState()
        assertEquals(1, EarProgress.currentStage(fresh).n)
        val items = EarProgress.plan(fresh, now, Random(1))
        assertEquals(EarProgress.ROUND, items.size)
        assertEquals(setOf("deg:0", "deg:4", "deg:7"), items.toSet())
        assertTrue(items.groupingBy { it }.eachCount().values.all { it <= 5 })

        var s = fresh
        repeat(8) { s = EarProgress.record(s, listOf("deg:0", "deg:4", "deg:7").map { EarResult(it, true, 1500) }, now + it) }
        assertEquals(2, EarProgress.currentStage(s).n)
        assertTrue("deg:2" in EarProgress.unlocked(s) && "rh:1" in EarProgress.unlocked(s))
        // The new items now come up most.
        val next = EarProgress.plan(s, now, Random(2), count = 40)
        assertTrue(next.count { it in setOf("deg:2", "deg:5", "rh:1") } > next.size / 2, "$next")
    }

    @Test
    fun weakItemsComeUpMoreThanMasteredOnes() {
        val state = EarState(items = mapOf(
            "deg:0" to EarItemStat(20, 19, 0.95, now, 900),
            "deg:4" to EarItemStat(20, 8, 0.35, now, 2500),
            "deg:7" to EarItemStat(20, 19, 0.95, now, 900),
        ))
        val picks = (0 until 30).flatMap { EarProgress.plan(state, now, Random(it)) }
        // The weak note fills its share in almost every round (capped so a round still mixes); the known ones less.
        assertTrue(picks.count { it == "deg:4" } >= 30 * 4, "${picks.count { it == "deg:4" }}")
        assertTrue(picks.count { it == "deg:4" } > picks.count { it == "deg:0" })
    }

    @Test
    fun answersMoveStrengthAndOneLuckyAnswerDoesNotMaster() {
        var s = EarProgress.record(EarState(), listOf(EarResult("deg:4", true, 1200)), now, ZoneOffset.UTC)
        val st = EarProgress.stat(s, "deg:4")
        assertEquals(1, st.asked)
        assertEquals(0.65, st.strength, 1e-9)
        assertTrue(!st.mastered)
        repeat(5) { s = EarProgress.record(s, listOf(EarResult("deg:4", true, 1000)), now + it, ZoneOffset.UTC) }
        assertTrue(EarProgress.stat(s, "deg:4").mastered)
        s = EarProgress.record(s, listOf(EarResult("deg:4", false, 3000)), now + 10, ZoneOffset.UTC)
        assertTrue(!EarProgress.stat(s, "deg:4").mastered)
        assertEquals(7, s.sessions.size)
        val day = LocalDate.ofEpochDay(now / 86_400_000)
        assertEquals(listOf(day to 6.0 / 7), EarProgress.daily(s, day))
    }

    @Test
    fun playedMelodiesAndTappedRhythmsAreJudged() {
        val base = EarCourse.baseOf(0)
        assertEquals(listOf(true, true, false), EarCourse.judgeNotes(listOf(0, 4, 7), listOf(base + 12, base + 4, base + 9), base))
        assertEquals(listOf(true, false), EarCourse.judgeNotes(listOf(0, 2), listOf(base), base))
        val taps = EarAnswer.Taps(listOf(0.0, 1.0, 1.5, 2.0), 60)
        assertTrue(EarCourse.judgeTaps(taps, listOf(20, 1010, 1480, 2050)).first)
        assertTrue(!EarCourse.judgeTaps(taps, listOf(20, 1010, 1700, 2050)).first)
        assertTrue(!EarCourse.judgeTaps(taps, listOf(20, 1010, 1480)).first)
    }

    @Test
    fun syncMergeKeepsTheBusierCopyAndEverySession() {
        val a = EarProgress.record(EarState(), listOf(EarResult("deg:0", true, 1000), EarResult("deg:4", false, 2000)), now, ZoneOffset.UTC)
        val b = EarProgress.record(EarState(), listOf(EarResult("deg:0", true, 900)), now + 5, ZoneOffset.UTC)
        val ab = EarProgress.merge(a, b)
        assertEquals(ab, EarProgress.merge(b, a))
        assertEquals(ab, EarProgress.merge(ab, b))
        assertEquals(2, ab.sessions.size)
        assertEquals(setOf("deg:0", "deg:4"), ab.items.keys)
    }

    @Test
    fun aTestCoversEveryItemAndUsesItsOwnKeys() {
        for (t in EarCourse.TESTS) {
            val qs = EarProgress.testRound(t, Random(t.level))
            assertEquals(t.count, qs.size)
            assertTrue(t.items.all { item -> qs.any { it.item == item } }, "level ${t.level}")
            assertTrue(qs.all { it.tonic in t.keys.tonics || it.kind == EarKind.QUALITY || it.kind == EarKind.RHYTHM })
        }
    }
}
