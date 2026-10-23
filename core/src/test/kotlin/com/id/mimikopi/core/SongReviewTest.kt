package com.id.mimikopi.core

import com.id.mimikopi.core.playalong.PieceProgress
import com.id.mimikopi.core.playalong.SongReview
import com.id.mimikopi.core.session.DailyPlan
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.core.session.PlanInput
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongReviewTest {
    private val d = LocalDate.parse("2026-10-23")
    private val passed = PieceProgress("p", passDays = listOf("2026-10-01", "2026-10-02"))

    @Test fun notPassedIsNeverDue() = assertNull(SongReview.dueOn(PieceProgress("q", passDays = listOf("2026-10-01"))))

    @Test fun passedBeforeReviewsIsDue() = assertTrue(SongReview.isDue(passed, d))

    @Test fun boxesSpreadOut() {
        val p = passed.copy(reviewBox = 3, lastReview = "2026-10-20")
        assertFalse(SongReview.isDue(p, d))
        assertTrue(SongReview.isDue(p, LocalDate.parse("2026-10-28")))
    }

    @Test fun cleanReviewMovesUpAndMissGoesBack() {
        val before = passed.copy(reviewBox = 2, lastReview = "2026-10-10")
        assertEquals(3, SongReview.afterCountingRun(before, before, true, d).reviewBox)
        val miss = SongReview.afterCountingRun(before, before, false, d)
        assertEquals(1, miss.reviewBox); assertEquals(d.toString(), miss.lastReview)
        // Not due: nothing changes.
        val fresh = passed.copy(reviewBox = 5, lastReview = "2026-10-22")
        assertEquals(fresh, SongReview.afterCountingRun(fresh, fresh, false, d))
    }

    @Test fun becomingPassedStartsBoxOne() {
        val before = PieceProgress("p", passDays = listOf("2026-10-22"))
        val r = SongReview.afterCountingRun(before, before.copy(passDays = before.passDays + d.toString()), true, d)
        assertEquals(1, r.reviewBox); assertFalse(SongReview.isDue(r, d))
    }

    @Test fun mostOverdueFirstAndInPlan() {
        val a = passed.copy(pieceId = "a", reviewBox = 1, lastReview = "2026-10-15")
        val b = passed.copy(pieceId = "b", reviewBox = 1, lastReview = "2026-10-05")
        assertEquals(listOf("b", "a"), SongReview.due(listOf(a, b), d).map { it.pieceId })
        val plan = DailyPlan.build(PlanInput(30, 0, false, null, null, false, null, null, songDue = "b"))
        assertEquals("b", plan.single { it.kind == BlockKind.SONG }.target)
    }
}
