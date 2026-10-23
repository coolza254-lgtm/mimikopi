package com.id.mimikopi.desktop.learning

import com.id.mimikopi.core.exam.ExamLog
import com.id.mimikopi.core.exam.ExamPart
import com.id.mimikopi.core.exam.ExamSitting
import com.id.mimikopi.core.exam.PartMark
import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.Mistakes
import com.id.mimikopi.core.progress.RunMistakes
import com.id.mimikopi.core.session.DayLog
import com.id.mimikopi.core.session.PracticeLog
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.desktop.data.Database
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Practice minutes per day, level tests, mistakes and exams in settings rows (the Android ProgressStore's rows and
 * rules, so a backup means the same on both).
 */
class ProgressStore(private val db: Database) {
    private val lock = Any()
    private var loaded = false
    private var own: List<DayLog> = emptyList()
    private var peers: Map<String, List<DayLog>> = emptyMap()
    private val _log = MutableStateFlow<List<DayLog>>(emptyList())
    val log: StateFlow<List<DayLog>> = _log.asStateFlow()
    private val _checks = MutableStateFlow<List<CheckRecord>>(emptyList())
    val checks: StateFlow<List<CheckRecord>> = _checks.asStateFlow()
    private val _mistakes = MutableStateFlow<List<RunMistakes>>(emptyList())
    val mistakes: StateFlow<List<RunMistakes>> = _mistakes.asStateFlow()
    private val _exams = MutableStateFlow<List<ExamSitting>>(emptyList())
    val exams: StateFlow<List<ExamSitting>> = _exams.asStateFlow()

    fun load(): Unit = synchronized(lock) { if (!loaded) readAll() }

    fun reload(): Unit = synchronized(lock) { readAll() }

    private fun readAll() {
        own = PracticeLog.parse(db.get(LOG))
        peers = ProgressMerge.parsePeers(db.get(PEERS))
        _log.value = ProgressMerge.combinedLog(own, peers)
        _checks.value = CheckRecord.parse(db.get(CHECKS))
        _mistakes.value = Mistakes.parse(db.get(MISTAKES))
        _exams.value = ExamLog.parse(db.get(EXAMS))
        loaded = true
    }

    fun log(kind: String, seconds: Long, day: LocalDate = LocalDate.now()) {
        load()
        synchronized(lock) {
            val next = PracticeLog.add(own, day, kind, seconds)
            if (next == own) return
            own = next
            _log.value = ProgressMerge.combinedLog(own, peers)
            db.put(LOG, PracticeLog.encode(next))
        }
    }

    fun addCheck(record: CheckRecord) {
        load()
        synchronized(lock) {
            val next = _checks.value + record
            _checks.value = next
            db.put(CHECKS, CheckRecord.encode(next))
        }
    }

    fun addMistakes(run: RunMistakes) {
        load()
        synchronized(lock) {
            val next = (_mistakes.value + run).takeLast(Mistakes.KEEP)
            _mistakes.value = next
            db.put(MISTAKES, Mistakes.encode(next))
        }
    }

    fun startExam(sitting: ExamSitting) = changeExams { ExamLog.begin(it, sitting) }
    fun recordExam(id: String, part: ExamPart, mark: PartMark) = changeExams { ExamLog.record(it, id, part, mark) }
    fun abandonExam(id: String) = changeExams { ExamLog.abandon(it, id) }

    private fun changeExams(change: (List<ExamSitting>) -> List<ExamSitting>) {
        load()
        synchronized(lock) {
            val next = change(_exams.value)
            if (next == _exams.value) return
            _exams.value = next
            db.put(EXAMS, ExamLog.encode(next))
        }
    }

    companion object {
        const val LOG = ProgressMerge.LOG
        const val PEERS = ProgressMerge.PEERS
        const val CHECKS = "skills.checks"
        const val MISTAKES = Mistakes.KEY
        const val EXAMS = ExamLog.KEY
        const val PLAN = "today.plan"
        const val MINUTES = "today.minutes"
        const val CHORDS_TODAY = "chords.today"
    }
}

/** The practice session started on the Today page; attempts made meanwhile point to it (Android's SessionTracker). */
class SessionTracker(private val db: Database) {
    data class Active(val id: Long, val startedAtMillis: Long, val plannedMinutes: Int)

    private val _active = MutableStateFlow<Active?>(null)
    val active: StateFlow<Active?> = _active.asStateFlow()

    fun currentId(): Long? = _active.value?.id

    fun start(plannedMinutes: Int) {
        if (_active.value != null) return
        val now = System.currentTimeMillis()
        _active.value = Active(db.insertSession(now, plannedMinutes), now, plannedMinutes)
    }

    /** Ends the session; returns minutes practised (capped: a session left open overnight is not hours of practice). */
    fun finish(): Long {
        val a = _active.value ?: return 0
        val seconds = ((System.currentTimeMillis() - a.startedAtMillis) / 1000).coerceAtMost(a.plannedMinutes * 60L * 2 + 600)
        db.finishSession(a.id, System.currentTimeMillis(), seconds)
        _active.value = null
        return seconds / 60
    }
}

/** Calibration results: octave offset, velocity probe, latency per input and output path. */
class InputConfigStore(private val db: Database) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _config = MutableStateFlow(
        db.get(SettingKeys.INPUT_CONFIG)?.let { runCatching { json.decodeFromString(InputConfig.serializer(), it) }.getOrNull() } ?: InputConfig(),
    )
    val config: StateFlow<InputConfig> = _config.asStateFlow()

    fun update(transform: (InputConfig) -> InputConfig) {
        _config.update(transform)
        db.put(SettingKeys.INPUT_CONFIG, json.encodeToString(InputConfig.serializer(), _config.value))
    }

    fun reload() {
        _config.value = db.get(SettingKeys.INPUT_CONFIG)?.let { runCatching { json.decodeFromString(InputConfig.serializer(), it) }.getOrNull() } ?: InputConfig()
    }
}
