package com.id.mimikopi.desktop.learning

import com.id.mimikopi.core.backup.AttemptEventRow
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.config.LearningConfig
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.practice.TimingMode
import com.id.mimikopi.core.practice.TimingRules
import com.id.mimikopi.core.progress.BenchmarkResult
import com.id.mimikopi.core.progress.Benchmarks
import com.id.mimikopi.core.srs.ItemState
import com.id.mimikopi.core.srs.Leitner
import com.id.mimikopi.desktop.data.Database
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One played note inside an attempt (learning-system.md 2.2 `events[]`). */
data class AttemptEvent(
    val expectedPitch: Int?,
    val actualPitch: Int?,
    val expectedOnsetMs: Long? = null,
    val actualOnsetMs: Long? = null,
    val offsetMs: Long? = null,
    val velocity: Int? = null,
)

data class PracticeStats(val totalMinutes: Long, val daysPracticed: Int, val minutesThisWeek: Long, val minutesLastWeek: Long, val minutesToday: Long)

/**
 * Lessons, lesson progress, spaced repetition, attempts and practice sessions: the Android LearningRepository on the
 * desktop database. Every decision (pass rules, review intervals, timing tolerance) is core's.
 */
class LearningRepository(private val db: Database) {
    private val json = Json { ignoreUnknownKeys = true }
    private val recordsSerializer = MapSerializer(String.serializer(), LessonRecord.serializer())
    private val lock = Any()

    val config: LearningConfig by lazy { LearningConfig.fromJson(Content.read("config/learning.json") ?: error("config/learning.json missing")) }
    val leitner: Leitner by lazy { Leitner(config.srs) }

    val lessons: List<Lesson> by lazy {
        Content.list("lessons").map { Lesson.parse(Content.read(it)!!) }
            .sortedWith(compareBy({ it.id.substringBefore('.').toInt() }, { it.id.substringAfter('.').toInt() }))
    }

    fun lesson(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    private val _records = MutableStateFlow<Map<String, LessonRecord>>(emptyMap())
    val records: StateFlow<Map<String, LessonRecord>> = _records.asStateFlow()
    private val _timingMode = MutableStateFlow(TimingMode.NORMAL)
    val timingMode: StateFlow<TimingMode> = _timingMode.asStateFlow()
    private var loaded = false

    fun load(): Unit = synchronized(lock) {
        if (loaded) return
        loaded = true
        val raw = db.get(SettingKeys.LESSON_RECORDS)
        val stored = raw?.let { runCatching { json.decodeFromString(recordsSerializer, it) }.getOrNull() } ?: emptyMap()
        if (db.get(SettingKeys.PATH_VERSION) != "3") {
            // Same as Android M15: a database from before the step ladder keeps its old records aside.
            if (raw != null) db.put(SettingKeys.LESSON_RECORDS_V2, raw)
            db.put(SettingKeys.LESSON_RECORDS, json.encodeToString(recordsSerializer, emptyMap()))
            db.put(SettingKeys.PATH_VERSION, "3")
            _records.value = emptyMap()
        } else {
            _records.value = stored
        }
        db.get(SettingKeys.TIMING_MODE)?.let { runCatching { TimingMode.valueOf(it) }.getOrNull() }?.let { _timingMode.value = it }
    }

    /** Reads the stored rows again (after an import). */
    fun reload() {
        synchronized(lock) { loaded = false }
        load()
    }

    fun currentStep(): Lesson? = ProgressRules.nextToLearn(lessons, _records.value)

    fun confirmations(ladder: List<Lesson>, today: LocalDate = LocalDate.now()): List<Lesson> =
        ProgressRules.awaitingConfirmation(ladder, _records.value, today)

    fun reopenFailedConfirmations(today: LocalDate = LocalDate.now()) {
        load()
        synchronized(lock) {
            var changed = false
            val next = _records.value.toMutableMap()
            for (l in lessons) {
                val r = next[l.id] ?: continue
                if (ProgressRules.failedConfirmation(l, r, today)) { next[l.id] = ProgressRules.reopen(r, today); changed = true }
            }
            if (changed) {
                _records.value = next
                db.put(SettingKeys.LESSON_RECORDS, json.encodeToString(recordsSerializer, next))
            }
        }
    }

    fun setTimingMode(mode: TimingMode) {
        _timingMode.value = mode
        db.put(SettingKeys.TIMING_MODE, mode.name)
    }

    fun toleranceMs(stage: Int, bpm: Double, benchmark: Boolean = false): Int =
        TimingRules(config.timing).toleranceMs(stage, bpm, if (benchmark) TimingMode.NORMAL else _timingMode.value)

    /** Updates a lesson's record after drill [drillIndex] ran; returns true if the lesson is now passed. */
    fun recordDrill(lesson: Lesson, drillIndex: Int, met: Boolean, helperUsed: Boolean, day: LocalDate = LocalDate.now()): Boolean {
        load()
        val updated = synchronized(lock) {
            val current = _records.value[lesson.id] ?: LessonRecord(lesson.id)
            var r = current.copy(attempts = current.attempts + 1)
            if (drillIndex in lesson.pass.requiredDrills) r = r.copy(triedDays = r.triedDays + day.toString())
            if (met && drillIndex in lesson.pass.requiredDrills) r = ProgressRules.recordDrillMet(r, lesson, drillIndex, day, helperUsed)
            _records.value = _records.value + (lesson.id to r)
            db.put(SettingKeys.LESSON_RECORDS, json.encodeToString(recordsSerializer, _records.value))
            r
        }
        return ProgressRules.isPassed(updated, lesson)
    }

    fun checkPieceLessons(isPiecePassed: (pieceId: String, hand: String) -> Boolean): List<Lesson> {
        load()
        val passedNow = mutableListOf<Lesson>()
        for (l in lessons) {
            val need = l.pass.piecesPassed ?: continue
            if (ProgressRules.isPassed(_records.value[l.id], l)) continue
            for (i in l.pass.requiredDrills) {
                val d = l.drills[i]
                if (d.type != ExerciseType.E_PIECE) continue
                if (d.pieces.count { isPiecePassed(it, d.hand) } >= need && recordDrill(l, i, met = true, helperUsed = false)) passedNow += l
            }
        }
        return passedNow
    }

    // ---------------- benchmarks ----------------

    private val benchSerializer = ListSerializer(BenchmarkResult.serializer())

    fun benchmarks(): List<BenchmarkResult> =
        db.get(SettingKeys.BENCHMARKS)?.let { runCatching { json.decodeFromString(benchSerializer, it) }.getOrNull() } ?: emptyList()

    fun updateBenchmark(change: (BenchmarkResult) -> BenchmarkResult) = synchronized(lock) {
        val next = Benchmarks.update(benchmarks(), LocalDate.now(), change)
        db.put(SettingKeys.BENCHMARKS, json.encodeToString(benchSerializer, next))
    }

    // ---------------- spaced repetition ----------------

    fun itemStates(): Map<String, ItemState> = db.itemStats().associate {
        it.itemId to ItemState(it.itemId, it.box, it.dueAt, it.lastResults.map { c -> c == '1' })
    }

    fun dueItems(prefix: String? = null, now: Long = System.currentTimeMillis()): List<String> =
        leitner.selectDue(itemStates().values.filter { prefix == null || it.itemId.startsWith(prefix) }, now).map { it.itemId }

    fun isReading(itemId: String) = itemId.startsWith("key:") || itemId.startsWith("note:")

    fun dueCount(now: Long = System.currentTimeMillis()): Int = leitner.selectDue(itemStates().values.filter { isReading(it.itemId) }, now).size

    fun readingDueItems(now: Long = System.currentTimeMillis()): List<String> =
        leitner.selectDue(itemStates().values.filter { isReading(it.itemId) }, now).map { it.itemId }

    fun enrol(itemIds: List<String>, now: Long = System.currentTimeMillis()) {
        val existing = itemStates()
        itemIds.filter { it !in existing }.forEach { put(leitner.newItem(it, now), null) }
    }

    fun review(itemId: String, correct: Boolean, responseMs: Long, now: Long = System.currentTimeMillis()) {
        val state = itemStates()[itemId] ?: leitner.newItem(itemId, now)
        put(leitner.review(state, correct, responseMs, now), responseMs)
    }

    private fun put(state: ItemState, responseMs: Long?) {
        db.putItemStat(ItemStatRow(state.itemId, state.box, state.dueAtMillis, state.lastResults.joinToString("") { if (it) "1" else "0" }, responseMs))
    }

    // ---------------- attempts and sessions ----------------

    fun saveAttempt(
        exerciseId: String,
        paramsJson: String,
        startedAt: Long,
        tempoBpm: Double?,
        noteAccuracy: Double?,
        timingWithinTol: Double?,
        clean: Boolean?,
        helperUses: Int,
        events: List<AttemptEvent>,
        sessionId: Long?,
        hand: String? = null,
        stumbles: Int? = null,
    ): Long = db.insertAttempt(
        AttemptRow(0, sessionId, exerciseId, paramsJson, startedAt, hand, tempoBpm, noteAccuracy, timingWithinTol, stumbles, clean, helperUses),
        events.map { AttemptEventRow(0, 0, it.expectedPitch, it.actualPitch, it.expectedOnsetMs, it.actualOnsetMs, it.offsetMs, it.velocity) },
    )

    fun lessonAttempts(lessonId: String): List<AttemptRow> = db.lessonAttempts(lessonId)

    fun stats(): PracticeStats {
        val zone = ZoneId.systemDefault()
        val sessions = db.sessions()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        fun minutes(filter: (LocalDate) -> Boolean) = sessions.filter { filter(day(it.startedAt)) }.sumOf { it.activeSeconds } / 60
        return PracticeStats(
            totalMinutes = sessions.sumOf { it.activeSeconds } / 60,
            daysPracticed = sessions.filter { it.activeSeconds >= 60 }.map { day(it.startedAt) }.toSet().size,
            minutesThisWeek = minutes { !it.isBefore(weekStart) },
            minutesLastWeek = minutes { !it.isBefore(weekStart.minusWeeks(1)) && it.isBefore(weekStart) },
            minutesToday = minutes { it == today },
        )
    }

    /** Daily averages over the last [days] days: reading accuracy (untimed) and the share within timing (timed). */
    fun dailyTrends(days: Int = 30): Pair<List<Pair<LocalDate, Double>>, List<Pair<LocalDate, Double>>> {
        val zone = ZoneId.systemDefault()
        val since = LocalDate.now().minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val attempts = db.attemptsSince(since)
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val reading = attempts.filter { it.noteAccuracy != null && it.tempoBpm == null }
            .groupBy { day(it.startedAt) }.mapValues { (_, a) -> a.mapNotNull { it.noteAccuracy }.average() }
        val timing = attempts.filter { it.timingWithinTol != null }
            .groupBy { day(it.startedAt) }.mapValues { (_, a) -> a.mapNotNull { it.timingWithinTol }.average() }
        return reading.toSortedMap().toList() to timing.toSortedMap().toList()
    }
}
