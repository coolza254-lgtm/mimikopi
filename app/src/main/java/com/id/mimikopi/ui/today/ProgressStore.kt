package com.id.mimikopi.ui.today

import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.Mistakes
import com.id.mimikopi.core.progress.RunMistakes
import com.id.mimikopi.core.session.DayLog
import com.id.mimikopi.core.session.PracticeLog
import com.id.mimikopi.core.sync.ProgressMerge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Practice minutes per day and the level tests taken, kept in settings rows (so backups carry them). Every
 * runner logs its finished runs here; the Today and Skills pages read it. Storage is passed in, so the phone and the
 * desktop share this class.
 */
class ProgressStore(private val read: suspend (String) -> String?, private val write: suspend (String, String) -> Unit) {
    private val mutex = Mutex()
    private var loaded = false
    /** This device's own practice log; the other devices' logs (copied by sync) are kept apart and only added for display. */
    private var own: List<DayLog> = emptyList()
    private var peers: Map<String, List<DayLog>> = emptyMap()
    private val _log = MutableStateFlow<List<DayLog>>(emptyList())
    /** Practice per day from every device (this one plus the ones synced with it). */
    val log: StateFlow<List<DayLog>> = _log.asStateFlow()
    private val _checks = MutableStateFlow<List<CheckRecord>>(emptyList())
    val checks: StateFlow<List<CheckRecord>> = _checks.asStateFlow()
    private val _mistakes = MutableStateFlow<List<RunMistakes>>(emptyList())
    /** What went wrong in each finished run (for "what to fix"). */
    val mistakes: StateFlow<List<RunMistakes>> = _mistakes.asStateFlow()

    suspend fun load() = mutex.withLock { if (!loaded) readAll() }

    /** Re-reads after a backup import or a sync (in one step, so a run saved meanwhile is not lost). */
    suspend fun reload() = mutex.withLock { readAll() }

    private suspend fun readAll() {
        own = PracticeLog.parse(read(LOG))
        peers = ProgressMerge.parsePeers(read(PEERS))
        _log.value = ProgressMerge.combinedLog(own, peers)
        _checks.value = CheckRecord.parse(read(CHECKS))
        _mistakes.value = Mistakes.parse(read(MISTAKES))
        loaded = true
    }

    /** Adds [seconds] of practice of [kind] (a [com.id.mimikopi.core.session.BlockKind] log name, or "free") today. */
    suspend fun log(kind: String, seconds: Long) {
        load()
        mutex.withLock {
            val next = PracticeLog.add(own, LocalDate.now(), kind, seconds)
            if (next == own) return@withLock
            own = next
            _log.value = ProgressMerge.combinedLog(own, peers)
            write(LOG, PracticeLog.encode(next))
        }
    }

    suspend fun addCheck(record: CheckRecord) {
        load()
        mutex.withLock {
            val next = _checks.value + record
            _checks.value = next
            write(CHECKS, CheckRecord.encode(next))
        }
    }

    suspend fun addMistakes(run: RunMistakes) {
        load()
        mutex.withLock {
            val next = (_mistakes.value + run).takeLast(Mistakes.KEEP)
            _mistakes.value = next
            write(MISTAKES, Mistakes.encode(next))
        }
    }

    companion object {
        const val LOG = ProgressMerge.LOG
        /** The other devices' own logs by device id (written only by sync). */
        const val PEERS = ProgressMerge.PEERS
        const val CHECKS = "skills.checks"
        const val MISTAKES = Mistakes.KEY
        /** Today's plan (DailyPlan.Stored) and the minutes last chosen. */
        const val PLAN = "today.plan"
        const val MINUTES = "today.minutes"
        /** The chord practice the Today plan opens (ChordPractice JSON), apart from the user's own choices. */
        const val CHORDS_TODAY = "chords.today"
    }
}
