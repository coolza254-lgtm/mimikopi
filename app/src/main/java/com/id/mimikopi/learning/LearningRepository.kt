package com.id.mimikopi.learning

import android.content.Context
import com.id.mimikopi.core.config.LearningConfig
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.srs.ItemState
import com.id.mimikopi.core.srs.Leitner
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.data.db.AttemptEntity
import com.id.mimikopi.data.db.AttemptEventEntity
import com.id.mimikopi.data.db.ItemStatEntity
import com.id.mimikopi.data.db.PracticeSessionEntity
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One played note inside an attempt (learning-system.md 2.2 `events[]`). */
data class AttemptEvent(
    val expectedPitch: Int?,
    val actualPitch: Int?,
    val expectedOnsetMs: Long? = null,
    val actualOnsetMs: Long? = null,
    val offsetMs: Long? = null,
    val velocity: Int? = null,
)

data class PracticeStats(
    val totalMinutes: Long,
    val daysPracticed: Int,
    val minutesThisWeek: Long,
    val minutesLastWeek: Long,
    val minutesToday: Long,
)

/**
 * Lessons (from assets/content), lesson progress, spaced repetition state, attempts and practice
 * sessions. Everything stays on the device (Room + settings table, so JSON backups cover it).
 */
class LearningRepository(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private val recordsSerializer = MapSerializer(String.serializer(), LessonRecord.serializer())
    private val mutex = Mutex()

    val config: LearningConfig by lazy {
        context.assets.open("config/learning.json").use { LearningConfig.fromJson(it.readBytes().toString(Charsets.UTF_8)) }
    }
    val leitner: Leitner by lazy { Leitner(config.srs) }

    /** All lessons in content/lessons, in id order (1.1, 1.3, 1.5 ...). */
    val lessons: List<Lesson> by lazy {
        context.assets.list("lessons").orEmpty().filter { it.endsWith(".json") }
            .map { name -> context.assets.open("lessons/$name").use { Lesson.parse(it.readBytes().toString(Charsets.UTF_8)) } }
            .sortedWith(compareBy({ it.id.substringBefore('.').toInt() }, { it.id.substringAfter('.').toInt() }))
    }

    fun lesson(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    private val _records = MutableStateFlow<Map<String, LessonRecord>>(emptyMap())
    val records: StateFlow<Map<String, LessonRecord>> = _records.asStateFlow()
    private var loaded = false

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        loaded = true
        db.settingsDao().get(SettingKeys.LESSON_RECORDS)
            ?.let { runCatching { json.decodeFromString(recordsSerializer, it) }.getOrNull() }
            ?.let { _records.value = it }
    }

    /**
     * Updates a lesson's record after drill [drillIndex] ran. [met] = the drill met its criterion (only drills the
     * pass rule requires count). Returns true if the lesson is now fully passed.
     */
    suspend fun recordDrill(lesson: Lesson, drillIndex: Int, met: Boolean, helperUsed: Boolean, day: LocalDate = LocalDate.now()): Boolean {
        load()
        val updated = mutex.withLock {
            val current = _records.value[lesson.id] ?: LessonRecord(lesson.id)
            var r = current.copy(attempts = current.attempts + 1)
            if (met && drillIndex in lesson.pass.requiredDrills) r = ProgressRules.recordDrillMet(r, lesson, drillIndex, day, helperUsed)
            _records.value = _records.value + (lesson.id to r)
            db.settingsDao().put(SettingEntity(SettingKeys.LESSON_RECORDS, json.encodeToString(recordsSerializer, _records.value)))
            r
        }
        return ProgressRules.isPassed(updated, lesson)
    }

    /**
     * Lessons whose drill asks for a number of passed pieces (2.8, 3.7): records the drill as met once enough of
     * its pieces are passed with the drill's hand. Called after a piece run. Returns the lessons passed now.
     */
    suspend fun checkPieceLessons(isPiecePassed: (pieceId: String, hand: String) -> Boolean): List<Lesson> {
        load()
        val passedNow = mutableListOf<Lesson>()
        for (l in lessons) {
            val need = l.pass.piecesPassed ?: continue
            if (ProgressRules.isPassed(_records.value[l.id], l)) continue
            for (i in l.pass.requiredDrills) {
                val d = l.drills[i]
                if (d.type != com.id.mimikopi.core.lesson.ExerciseType.E_PIECE) continue
                if (d.pieces.count { isPiecePassed(it, d.hand) } >= need && recordDrill(l, i, met = true, helperUsed = false)) passedNow += l
            }
        }
        return passedNow
    }

    // --- Benchmarks (12.1) ---

    private val benchSerializer = kotlinx.serialization.builtins.ListSerializer(com.id.mimikopi.core.progress.BenchmarkResult.serializer())

    suspend fun benchmarks(): List<com.id.mimikopi.core.progress.BenchmarkResult> =
        db.settingsDao().get(SettingKeys.BENCHMARKS)?.let { runCatching { json.decodeFromString(benchSerializer, it) }.getOrNull() } ?: emptyList()

    suspend fun updateBenchmark(change: (com.id.mimikopi.core.progress.BenchmarkResult) -> com.id.mimikopi.core.progress.BenchmarkResult) = mutex.withLock {
        val next = com.id.mimikopi.core.progress.Benchmarks.update(benchmarks(), LocalDate.now(), change)
        db.settingsDao().put(SettingEntity(SettingKeys.BENCHMARKS, json.encodeToString(benchSerializer, next)))
    }

    // --- Spaced repetition (6.6) ---

    suspend fun itemStates(): Map<String, ItemState> = db.learningDao().itemStats().associate {
        it.itemId to ItemState(it.itemId, it.box, it.dueAtMillis, it.lastResults.map { c -> c == '1' })
    }

    /** Due items of the given ids (or all), most urgent first, capped per session. */
    suspend fun dueItems(prefix: String? = null, now: Long = System.currentTimeMillis()): List<String> =
        leitner.selectDue(itemStates().values.filter { prefix == null || it.itemId.startsWith(prefix) }, now).map { it.itemId }

    /** Due reading items (key positions and notes); ear items are practised in ear training. */
    suspend fun dueCount(now: Long = System.currentTimeMillis()): Int =
        leitner.selectDue(itemStates().values.filter { isReading(it.itemId) }, now).size

    suspend fun readingDueItems(now: Long = System.currentTimeMillis()): List<String> =
        leitner.selectDue(itemStates().values.filter { isReading(it.itemId) }, now).map { it.itemId }

    /** Due ear items, most urgent first. */
    suspend fun earDueItems(now: Long = System.currentTimeMillis()): List<String> =
        leitner.selectDue(itemStates().values.filter { it.itemId.startsWith("ear:") }, now).map { it.itemId }

    fun isReading(itemId: String) = itemId.startsWith("key:") || itemId.startsWith("note:")

    /** Recent results per ear item (the last 10 answers each) for the statistics of learning-system.md 11.4. */
    suspend fun earStats(): List<com.id.mimikopi.core.ear.EarStats.ItemStat> =
        db.learningDao().itemStats().filter { it.itemId.startsWith("ear:") }.map {
            com.id.mimikopi.core.ear.EarStats.ItemStat(it.itemId, it.lastResults.count { c -> c == '1' }, it.lastResults.length, it.medianResponseMs)
        }

    suspend fun dueTomorrowCount(): Int {
        val tomorrow = LocalDate.now().plusDays(1).atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return itemStates().values.count { it.dueAtMillis <= tomorrow }
    }

    /** Adds the lesson's SRS items (new items start in box 1, due now) when a lesson is first practised. */
    suspend fun enrol(itemIds: List<String>, now: Long = System.currentTimeMillis()) {
        val existing = itemStates()
        itemIds.filter { it !in existing }.forEach { put(leitner.newItem(it, now), null) }
    }

    suspend fun review(itemId: String, correct: Boolean, responseMs: Long, now: Long = System.currentTimeMillis()) {
        val state = itemStates()[itemId] ?: leitner.newItem(itemId, now)
        put(leitner.review(state, correct, responseMs, now), responseMs)
    }

    private suspend fun put(state: ItemState, responseMs: Long?) {
        db.learningDao().putItemStat(
            ItemStatEntity(state.itemId, state.box, state.dueAtMillis, state.lastResults.joinToString("") { if (it) "1" else "0" }, responseMs),
        )
    }

    // --- Attempts and sessions (2.2) ---

    suspend fun saveAttempt(
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
    ) {
        val dao = db.learningDao()
        val id = dao.insertAttempt(
            AttemptEntity(
                sessionId = sessionId, exerciseId = exerciseId, paramsJson = paramsJson, startedAtMillis = startedAt,
                hand = hand, tempoBpm = tempoBpm, noteAccuracy = noteAccuracy, timingWithinTol = timingWithinTol,
                stumbles = stumbles, clean = clean, helperUses = helperUses,
            ),
        )
        dao.insertEvents(events.map {
            AttemptEventEntity(attemptId = id, expectedPitch = it.expectedPitch, actualPitch = it.actualPitch,
                expectedOnsetMs = it.expectedOnsetMs, actualOnsetMs = it.actualOnsetMs, offsetMs = it.offsetMs, velocity = it.velocity)
        })
    }

    suspend fun startSession(plannedMinutes: Int): Long =
        db.learningDao().insertSession(PracticeSessionEntity(startedAtMillis = System.currentTimeMillis(), endedAtMillis = null, plannedMinutes = plannedMinutes, activeSeconds = 0))

    suspend fun finishSession(id: Long, activeSeconds: Long) =
        db.learningDao().finishSession(id, System.currentTimeMillis(), activeSeconds)

    /** Neutral practice numbers (no streaks, no rewards; section 3 and 2.4). */
    suspend fun stats(): PracticeStats {
        val zone = ZoneId.systemDefault()
        val sessions = db.learningDao().sessions()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        fun minutes(filter: (LocalDate) -> Boolean) = sessions.filter { filter(day(it.startedAtMillis)) }.sumOf { it.activeSeconds } / 60
        return PracticeStats(
            totalMinutes = sessions.sumOf { it.activeSeconds } / 60,
            daysPracticed = sessions.filter { it.activeSeconds >= 60 }.map { day(it.startedAtMillis) }.toSet().size,
            minutesThisWeek = minutes { !it.isBefore(weekStart) },
            minutesLastWeek = minutes { !it.isBefore(weekStart.minusWeeks(1)) && it.isBefore(weekStart) },
            minutesToday = minutes { it == today },
        )
    }

    /** Average first-try accuracy of reading drills today vs. the previous day practised. */
    suspend fun accuracyTodayVsBefore(): Pair<Double?, Double?> {
        val zone = ZoneId.systemDefault()
        val since = LocalDate.now().minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val attempts = db.learningDao().attemptsSince(since).filter { it.noteAccuracy != null }
        val byDay = attempts.groupBy { Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() }
        val today = LocalDate.now()
        val todayAvg = byDay[today]?.mapNotNull { it.noteAccuracy }?.average()
        val prevDay = byDay.keys.filter { it.isBefore(today) }.maxOrNull()
        val prevAvg = prevDay?.let { d -> byDay.getValue(d).mapNotNull { it.noteAccuracy }.average() }
        return todayAvg to prevAvg
    }

    /** Practice minutes for each of the last [weeks] weeks (Monday start), oldest first. */
    suspend fun weeklyMinutes(weeks: Int = 8): List<Pair<LocalDate, Long>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val thisWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val sessions = db.learningDao().sessions()
        return (weeks - 1 downTo 0).map { w ->
            val start = thisWeek.minusWeeks(w.toLong())
            val end = start.plusWeeks(1)
            start to sessions.filter {
                val d = Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate()
                !d.isBefore(start) && d.isBefore(end)
            }.sumOf { it.activeSeconds } / 60
        }
    }

    /**
     * Daily averages over the last [days] days, oldest first: note accuracy of reading drills (untimed) and the
     * share of notes within the timing tolerance (timed drills and pieces).
     */
    suspend fun dailyTrends(days: Int = 30): Pair<List<Pair<LocalDate, Double>>, List<Pair<LocalDate, Double>>> {
        val zone = ZoneId.systemDefault()
        val since = LocalDate.now().minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val attempts = db.learningDao().attemptsSince(since)
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val reading = attempts.filter { it.noteAccuracy != null && it.tempoBpm == null }
            .groupBy { day(it.startedAtMillis) }.mapValues { (_, a) -> a.mapNotNull { it.noteAccuracy }.average() }
        val timing = attempts.filter { it.timingWithinTol != null }
            .groupBy { day(it.startedAtMillis) }.mapValues { (_, a) -> a.mapNotNull { it.timingWithinTol }.average() }
        return reading.toSortedMap().toList() to timing.toSortedMap().toList()
    }

    fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
}
