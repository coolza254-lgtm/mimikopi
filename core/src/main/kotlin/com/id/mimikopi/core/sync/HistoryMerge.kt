package com.id.mimikopi.core.sync

import com.id.mimikopi.core.backup.AttemptEventRow
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.backup.BackupData
import com.id.mimikopi.core.backup.SessionRow

/**
 * Merges another device's practice history (the attempts, attempt_events and practice_sessions tables) into this
 * one's. Row ids are local to each database, so they never decide what is the same row: an attempt is identified by
 * what it recorded (exercise, start time to the millisecond, hand and its parameters JSON), a session by its start
 * time and planned length. Rows already present are skipped, so importing the same file twice, or A into B and then
 * B into A, adds nothing the second time; nothing local is deleted or overwritten except a session the other copy saw
 * running longer (its end and active seconds only grow).
 *
 * The caller inserts [Plan.newSessions] and [Plan.newAttempts] with fresh local ids and maps the other device's ids
 * through [Plan.sessionIds] (existing sessions) and the ids it assigns.
 */
object HistoryMerge {
    data class Plan(
        /** Sessions to add (with the other device's ids, to be replaced). */
        val newSessions: List<SessionRow>,
        /** Other device's session id -> local id, for sessions both have. */
        val sessionIds: Map<Long, Long>,
        /** Local sessions whose stored end/active seconds grow (local ids). */
        val sessionUpdates: List<SessionRow>,
        /** Attempts to add (other device's ids; sessionId still the other device's). */
        val newAttempts: List<AttemptRow>,
        /** Events of [newAttempts] (attemptId still the other device's). */
        val newEvents: List<AttemptEventRow>,
    ) {
        val isEmpty: Boolean get() = newSessions.isEmpty() && sessionUpdates.isEmpty() && newAttempts.isEmpty()
    }

    data class AttemptKey(val exerciseId: String, val startedAt: Long, val hand: String?, val paramsJson: String)
    data class SessionKey(val startedAt: Long, val plannedMinutes: Int)

    fun key(a: AttemptRow) = AttemptKey(a.exerciseId, a.startedAt, a.hand, a.paramsJson)
    fun key(s: SessionRow) = SessionKey(s.startedAt, s.plannedMinutes)

    fun plan(local: BackupData, remote: BackupData): Plan {
        val localSessions = local.practiceSessions.associateBy(::key)
        val sessionIds = HashMap<Long, Long>()
        val newSessions = ArrayList<SessionRow>()
        val updates = ArrayList<SessionRow>()
        val seenSessions = HashSet<SessionKey>()
        for (s in remote.practiceSessions.sortedBy { it.id }) {
            val k = key(s)
            val mine = localSessions[k]
            if (mine != null) {
                sessionIds[s.id] = mine.id
                val grown = mine.copy(
                    endedAt = listOfNotNull(mine.endedAt, s.endedAt).maxOrNull(),
                    activeSeconds = maxOf(mine.activeSeconds, s.activeSeconds),
                )
                if (grown != mine && updates.none { it.id == mine.id }) updates += grown
            } else if (seenSessions.add(k)) {
                newSessions += s
            }
        }
        val localAttempts = local.attempts.mapTo(HashSet(), ::key)
        val newAttempts = ArrayList<AttemptRow>()
        val seenAttempts = HashSet<AttemptKey>()
        for (a in remote.attempts.sortedBy { it.id }) {
            val k = key(a)
            if (k in localAttempts || !seenAttempts.add(k)) continue
            newAttempts += a
        }
        val ids = newAttempts.mapTo(HashSet()) { it.id }
        val events = remote.attemptEvents.filter { it.attemptId in ids }.sortedBy { it.id }
        return Plan(newSessions, sessionIds, updates, newAttempts, events)
    }
}
