package com.id.mimikopi.ui.today

import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.session.DayLog
import com.id.mimikopi.core.session.PracticeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Practice minutes per day and the level tests taken, kept in two settings rows (so backups carry them). Every
 * runner logs its finished runs here; the Today and Skills pages read it. Storage is passed in, so the phone and the
 * desktop share this class.
 */
class ProgressStore(private val read: suspend (String) -> String?, private val write: suspend (String, String) -> Unit) {
    private val mutex = Mutex()
    private var loaded = false
    private val _log = MutableStateFlow<List<DayLog>>(emptyList())
    val log: StateFlow<List<DayLog>> = _log.asStateFlow()
    private val _checks = MutableStateFlow<List<CheckRecord>>(emptyList())
    val checks: StateFlow<List<CheckRecord>> = _checks.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        _log.value = PracticeLog.parse(read(LOG))
        _checks.value = CheckRecord.parse(read(CHECKS))
        loaded = true
    }

    /** Re-reads after a backup import. */
    suspend fun reload() {
        mutex.withLock { loaded = false }
        load()
    }

    /** Adds [seconds] of practice of [kind] (a [com.id.mimikopi.core.session.BlockKind] log name, or "free") today. */
    suspend fun log(kind: String, seconds: Long) {
        load()
        mutex.withLock {
            val next = PracticeLog.add(_log.value, LocalDate.now(), kind, seconds)
            if (next == _log.value) return@withLock
            _log.value = next
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

    companion object {
        const val LOG = "practice.log"
        const val CHECKS = "skills.checks"
        /** Today's plan (DailyPlan.Stored) and the minutes last chosen. */
        const val PLAN = "today.plan"
        const val MINUTES = "today.minutes"
        /** The chord practice the Today plan opens (ChordPractice JSON), apart from the user's own choices. */
        const val CHORDS_TODAY = "chords.today"
    }
}
