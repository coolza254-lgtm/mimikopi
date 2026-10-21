package com.id.mimikopi.core

import com.id.mimikopi.core.backup.AttemptEventRow
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.backup.BackupData
import com.id.mimikopi.core.backup.SessionRow
import com.id.mimikopi.core.sync.HistoryMerge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryMergeTest {
    private fun attempt(id: Long, at: Long, session: Long? = null, exercise: String = "1.1:E_KEYFIND:0") =
        AttemptRow(id, session, exercise, """{"seed":$at,"completed":true,"mode":"ANSWER"}""", at, "RIGHT", null, 0.9, null, null, true, 0)

    private fun event(id: Long, attempt: Long) = AttemptEventRow(id, attempt, 60, 60, null, 500, null, 80)

    private val phone = BackupData(
        practiceSessions = listOf(SessionRow(1, 1_000, 2_000, 15, 600)),
        attempts = listOf(attempt(1, 1_100, 1), attempt(2, 1_200, 1)),
        attemptEvents = listOf(event(1, 1), event(2, 1), event(3, 2)),
    )

    // The computer numbered its own rows from 1 too: the same ids mean different rows.
    private val pc = BackupData(
        practiceSessions = listOf(SessionRow(1, 5_000, 6_000, 30, 900)),
        attempts = listOf(attempt(1, 5_100, 1, "2.1:E_NOTEID:0")),
        attemptEvents = listOf(event(1, 1)),
    )

    @Test
    fun colliding_ids_from_two_devices_are_all_kept() {
        val plan = HistoryMerge.plan(pc, phone)
        assertEquals(listOf(1L), plan.newSessions.map { it.id })
        assertEquals(listOf(1L, 2L), plan.newAttempts.map { it.id })
        assertEquals(3, plan.newEvents.size)
        assertTrue(plan.sessionIds.isEmpty())
    }

    @Test
    fun merging_again_adds_nothing() {
        val plan = HistoryMerge.plan(pc, phone)
        // What the computer holds after applying the plan, with its own new ids.
        val merged = BackupData(
            practiceSessions = pc.practiceSessions + plan.newSessions.map { it.copy(id = 2) },
            attempts = pc.attempts + plan.newAttempts.mapIndexed { i, a -> a.copy(id = 2L + i, sessionId = 2) },
            attemptEvents = pc.attemptEvents + plan.newEvents.mapIndexed { i, e -> e.copy(id = 2L + i, attemptId = if (e.attemptId == 1L) 2 else 3) },
        )
        assertTrue(HistoryMerge.plan(merged, phone).isEmpty)
        // And the phone importing the computer's file gets only the computer's own round.
        val back = HistoryMerge.plan(phone, merged)
        assertEquals(listOf("2.1:E_NOTEID:0"), back.newAttempts.map { it.exerciseId })
        assertEquals(1, back.newEvents.size)
    }

    @Test
    fun shared_session_maps_to_the_local_id_and_only_grows() {
        val local = BackupData(practiceSessions = listOf(SessionRow(7, 1_000, null, 15, 120)))
        val plan = HistoryMerge.plan(local, phone)
        assertEquals(mapOf(1L to 7L), plan.sessionIds)
        assertTrue(plan.newSessions.isEmpty())
        assertEquals(listOf(SessionRow(7, 1_000, 2_000, 15, 600)), plan.sessionUpdates)
        val shorter = HistoryMerge.plan(BackupData(practiceSessions = listOf(SessionRow(7, 1_000, 3_000, 15, 900))), phone)
        assertTrue(shorter.sessionUpdates.isEmpty())
    }

    @Test
    fun duplicate_rows_inside_one_file_are_added_once() {
        val twice = phone.copy(attempts = phone.attempts + attempt(9, 1_100, 1), attemptEvents = phone.attemptEvents + event(9, 9))
        val plan = HistoryMerge.plan(BackupData(), twice)
        assertEquals(2, plan.newAttempts.size)
        assertEquals(listOf(1L, 2L, 3L), plan.newEvents.map { it.id })
    }
}
