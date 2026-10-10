package com.id.mimikopi.core

import com.id.mimikopi.core.srs.Leitner
import com.id.mimikopi.core.time.Clock
import com.id.mimikopi.core.time.MILLIS_PER_DAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis() = now
    fun advanceDays(days: Int) { now += days * MILLIS_PER_DAY }
}

class LeitnerTest {
    private val leitner = Leitner()

    @Test
    fun newItemIsDueNowInBoxOne() {
        val clock = FakeClock()
        val item = leitner.newItem("note:G4:treble", clock.nowMillis())
        assertEquals(1, item.box)
        assertEquals(listOf(item), leitner.selectDue(listOf(item), clock.nowMillis()))
    }

    @Test
    fun correctFastAnswersClimbBoxesWithGrowingIntervals() {
        val clock = FakeClock()
        var item = leitner.newItem("key:C", clock.nowMillis())
        val expectedIntervals = listOf(2, 4, 8, 16, 16) // box 2,3,4,5, then stays in 5
        for (days in expectedIntervals) {
            item = leitner.review(item, correct = true, responseMs = 1200, nowMillis = clock.nowMillis())
            assertEquals(clock.nowMillis() + days * MILLIS_PER_DAY, item.dueAtMillis)
            clock.advanceDays(days)
        }
        assertEquals(5, item.box)
    }

    @Test
    fun wrongOrSlowAnswerResetsToBoxOne() {
        val clock = FakeClock()
        var item = leitner.newItem("note:F3:bass", clock.nowMillis())
        repeat(3) { item = leitner.review(item, true, 1000, clock.nowMillis()) }
        assertEquals(4, item.box)
        val slow = leitner.review(item, correct = true, responseMs = 3001, nowMillis = clock.nowMillis())
        assertEquals(1, slow.box)
        assertEquals(clock.nowMillis() + MILLIS_PER_DAY, slow.dueAtMillis)
        val wrong = leitner.review(item, correct = false, responseMs = 500, nowMillis = clock.nowMillis())
        assertEquals(1, wrong.box)
        assertTrue(wrong.lastWasWrong)
    }

    @Test
    fun selectionPrefersRecentlyWrongThenMostOverdueAndCapsAt20() {
        val clock = FakeClock()
        val now = clock.nowMillis()
        val items = (0 until 30).map { i ->
            leitner.newItem("item:$i", now - i * 1000L)
        }.toMutableList()
        items[5] = items[5].copy(lastResults = listOf(true, false))
        val notDue = leitner.newItem("future", now + MILLIS_PER_DAY)
        val selected = leitner.selectDue(items + notDue, now)
        assertEquals(20, selected.size)
        assertEquals("item:5", selected.first().itemId)
        assertEquals("item:29", selected[1].itemId) // most overdue next
        assertTrue(selected.none { it.itemId == "future" })
    }
}
