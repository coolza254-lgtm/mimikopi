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
    private val full = PlanInput(30, "1.6/0", reviewDue = 5, ladder = true, sightLevel = 1, earLevel = "c-major-1", chords = true, check = "NOTES:2", weakest = null)

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
