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

    /** Updates a lesson's record after a drill. Returns true if the lesson is now fully passed. */
    suspend fun recordDrill(lesson: Lesson, metPassCriterion: Boolean, helperUsed: Boolean, day: LocalDate = LocalDate.now()): Boolean {
        load()
        val updated = mutex.withLock {
            val current = _records.value[lesson.id] ?: LessonRecord(lesson.id)
            var r = current.copy(attempts = current.attempts + 1)
            if (metPassCriterion) r = ProgressRules.recordPass(r, day, helperUsed)
            _records.value = _records.value + (lesson.id to r)
            db.settingsDao().put(SettingEntity(SettingKeys.LESSON_RECORDS, json.encodeToString(recordsSerializer, _records.value)))
            r
        }
        return ProgressRules.isPassed(updated, lesson)
    }

    // --- Spaced repetition (6.6) ---

    suspend fun itemStates(): Map<String, ItemState> = db.learningDao().itemStats().associate {
        it.itemId to ItemState(it.itemId, it.box, it.dueAtMillis, it.lastResults.map { c -> c == '1' })
    }

    /** Due items of the given ids (or all), most urgent first, capped per session. */
    suspend fun dueItems(prefix: String? = null, now: Long = System.currentTimeMillis()): List<String> =
        leitner.selectDue(itemStates().values.filter { prefix == null || it.itemId.startsWith(prefix) }, now).map { it.itemId }

    suspend fun dueCount(now: Long = System.currentTimeMillis()): Int = leitner.selectDue(itemStates().values, now).size

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
    ) {
        val dao = db.learningDao()
        val id = dao.insertAttempt(
            AttemptEntity(
                sessionId = sessionId, exerciseId = exerciseId, paramsJson = paramsJson, startedAtMillis = startedAt,
                hand = null, tempoBpm = tempoBpm, noteAccuracy = noteAccuracy, timingWithinTol = timingWithinTol,
                stumbles = null, clean = clean, helperUses = helperUses,
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

    fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
}
