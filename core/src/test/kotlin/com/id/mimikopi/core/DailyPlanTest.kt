package com.id.mimikopi.core

import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.core.session.DailyPlan
import com.id.mimikopi.core.session.PlanInput
import com.id.mimikopi.core.session.PracticeLog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyPlanTest {
    private val full = PlanInput(30, "1.6/0", reviewDue = 5, ladder = true, sightLevel = 1, earLevel = "course", chords = true, check = "NOTES:2", weakest = null)

    @Test
    fun aPlanUsesTheTimeChosenInTheRightOrder() {
        for (m in DailyPlan.CHOICES) {
            val plan = DailyPlan.build(full.copy(minutes = m))
            assertEquals(m, plan.sumOf { it.minutes }, "minutes $m: $plan")
            assertEquals(plan.sortedBy { it.kind.ordinal }, plan)
            assertTrue(plan.all { it.minutes >= 2 })
        }
        val plan = DailyPlan.build(full)
        assertEquals(BlockKind.WARMUP, plan.first().kind)
        assertEquals(BlockKind.SONG, plan.last().kind)
        assertEquals(plan.maxBy { it.minutes }.kind, BlockKind.LADDER)
    }

    @Test
    fun shortDaysKeepTheEssentialsAndTheWeakestPart() {
        val plan = DailyPlan.build(full.copy(minutes = 10, weakest = SkillId.EAR))
        assertEquals(setOf(BlockKind.WARMUP, BlockKind.CHECK, BlockKind.LADDER, BlockKind.EAR), plan.map { it.kind }.toSet())
    }

    @Test
    fun theWeakestSkillGetsMoreTime() {
        val normal = DailyPlan.build(full.copy(minutes = 45)).first { it.kind == BlockKind.EAR }.minutes
        val weak = DailyPlan.build(full.copy(minutes = 45, weakest = SkillId.EAR)).first { it.kind == BlockKind.EAR }.minutes
        assertTrue(weak > normal, "$weak vs $normal")
    }

    @Test
    fun weakTechniqueGetsExtraMainPracticeInsteadOfAnIneffectiveFixedWarmupBoost() {
        val normal = DailyPlan.build(full.copy(minutes = 45)).associate { it.kind to it.minutes }
        val focused = DailyPlan.build(full.copy(minutes = 45, weakest = SkillId.TECH)).associate { it.kind to it.minutes }
        assertTrue(focused.getValue(BlockKind.LADDER) > normal.getValue(BlockKind.LADDER))
        assertEquals(normal[BlockKind.WARMUP], focused[BlockKind.WARMUP])
    }

    @Test
    fun everyCombinationOfAvailableActivitiesUsesExactlyTheChosenMinutes() {
        for (mask in 0 until 128) for (skill in listOf(null) + SkillId.entries) for (minutes in DailyPlan.CHOICES) {
            fun available(bit: Int) = mask and (1 shl bit) != 0
            val input = full.copy(
                minutes = minutes, weakest = skill,
                warmup = full.warmup.takeIf { available(0) },
                check = full.check.takeIf { available(1) },
                reviewDue = if (available(2)) 5 else 0,
                ladder = available(3), sightLevel = 1.takeIf { available(4) },
                earLevel = full.earLevel.takeIf { available(5) }, chords = available(6),
            )
            val plan = DailyPlan.build(input)
            val context = "minutes=$minutes, available=$mask, weakest=$skill: $plan"
            assertEquals(minutes, plan.sumOf { it.minutes }, context)
            assertTrue(plan.all { it.minutes >= 2 }, context)
            assertEquals(plan.size, plan.map { it.kind }.distinct().size, context)
            assertEquals(plan.sortedBy { it.kind.ordinal }, plan, context)
        }
    }

    @Test
    fun storedPlansWithWrongTotalsAreRebuiltWithoutChangingPracticeHistory() {
        val bad = DailyPlan.Stored("2026-10-20", 10, DailyPlan.build(full).take(1))
        assertEquals(null, DailyPlan.parse(DailyPlan.encode(bad)))
        val valid = DailyPlan.Stored("2026-10-20", 30, DailyPlan.build(full))
        assertEquals(valid, DailyPlan.parse(DailyPlan.encode(valid)))
    }

    @Test
    fun practiceTimeIsLoggedPerDayAndCapped() {
        val d = LocalDate.of(2026, 10, 18)
        var log = PracticeLog.add(emptyList(), d, "ear", 120)
        log = PracticeLog.add(log, d, "ear", 60)
        log = PracticeLog.add(log, d, "ladder", 99_999)
        assertEquals(180, PracticeLog.on(log, d).seconds["ear"])
        assertEquals(PracticeLog.MAX_RUN_SECONDS, PracticeLog.on(log, d).seconds["ladder"])
        assertEquals(log, PracticeLog.parse(PracticeLog.encode(log)))
        assertEquals(1 to (180 + PracticeLog.MAX_RUN_SECONDS) / 60, PracticeLog.week(log, d))
        val block = DailyPlan.build(full).first { it.kind == BlockKind.EAR }
        assertTrue(DailyPlan.isDone(block, PracticeLog.on(log, d), false, 0) == (180 >= block.minutes * 36))
    }
}

class PlanContentTest {
    private val lessons = java.io.File("../content/lessons").listFiles()!!.filter { it.name.endsWith(".json") }
        .map { com.id.mimikopi.core.lesson.Lesson.parse(it.readText()) }
        .sortedWith(compareBy({ it.id.substringBefore('.').toInt() }, { it.id.substringAfter('.').toInt() }))

    @Test
    fun aNewUserGetsAWarmUpAndNoReadingYet() {
        val w = com.id.mimikopi.core.session.PlanContent.warmup(lessons, emptyMap())
        assertTrue(w != null && w.contains('/'), "$w")
        assertEquals(null, com.id.mimikopi.core.session.PlanContent.sightLevel(lessons, emptyMap(), 0))
        assertEquals(3, com.id.mimikopi.core.session.PlanContent.sightLevel(lessons, emptyMap(), 3))
    }

    @Test
    fun chordAndEarPracticeFollowTheLevel() {
        for (l in 0..7) assertTrue(com.id.mimikopi.core.session.PlanContent.chords(l).drill().problems().isEmpty())
        assertEquals("course", com.id.mimikopi.core.session.PlanContent.EAR_ROUND)
    }
}
