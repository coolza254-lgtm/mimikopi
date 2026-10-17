package com.id.mimikopi.desktop.learning

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
import com.id.mimikopi.desktop.data.Store
import com.id.mimikopi.desktop.port.AttemptEvent
import com.id.mimikopi.desktop.port.CONTENT_FILES
import com.id.mimikopi.desktop.port.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Reads a bundled content file (content/ is on the classpath). */
fun resourceText(path: String): String? = Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

/** Bundled content files in one folder ("lessons", "pieces" ...), in name order. */
fun contentFiles(folder: String): List<String> = CONTENT_FILES.filter { it.startsWith("$folder/") && it.count { c -> c == '/' } == 1 }

/**
 * The reading steps (content/lessons) on the desktop, their records (the same
 * "lesson.records" row as the Android app, so progress moves with a backup) and the spaced-repetition items. The
 * same calls as the Android LearningRepository, so the ported screens use it unchanged; attempts and practice
 * sessions are not kept on the desktop.
 */
class LearningRepository(private val store: Store) {
    private val json = Json { ignoreUnknownKeys = true }
    private val recordsSerializer = MapSerializer(String.serializer(), LessonRecord.serializer())
    private val benchSerializer = ListSerializer(BenchmarkResult.serializer())

    val config: LearningConfig = resourceText("config/learning.json")?.let { LearningConfig.fromJson(it) } ?: LearningConfig()
    val leitner = Leitner(config.srs)

    /** All reading steps, in id order (1.1, 1.2 ... 1.10). */
    val lessons: List<Lesson> = contentFiles("lessons").mapNotNull { resourceText(it) }.map { Lesson.parse(it) }
        .sortedWith(compareBy({ it.id.substringBefore('.').toInt() }, { it.id.substringAfter('.').toInt() }))

    fun lesson(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    private val _records = MutableStateFlow(loadRecords())
    val records: StateFlow<Map<String, LessonRecord>> = _records.asStateFlow()

    private val _timingMode = MutableStateFlow(loadTimingMode())
    /** The user's timing strictness (set on the phone; a backup brings it along). */
    val timingMode: StateFlow<TimingMode> = _timingMode.asStateFlow()

    init {
        // The Android app restarts progress when this marker is missing (M15); desktop records are already on the new ladders.
        if (store.get(SettingKeys.PATH_VERSION) == null) store.put(SettingKeys.PATH_VERSION, "3")
    }

    private fun loadRecords(): Map<String, LessonRecord> =
        store.get(SettingKeys.LESSON_RECORDS)?.let { runCatching { json.decodeFromString(recordsSerializer, it) }.getOrNull() } ?: emptyMap()

    private fun loadTimingMode(): TimingMode =
        store.get(SettingKeys.TIMING_MODE)?.let { runCatching { TimingMode.valueOf(it) }.getOrNull() } ?: TimingMode.NORMAL

    /** The user's timing strictness, kept in the same row as on the phone. */
    fun setTimingMode(mode: TimingMode) {
        _timingMode.value = mode
        store.put(SettingKeys.TIMING_MODE, mode.name)
    }

    /** The tolerance a mode allows at a slow tempo, to show what it means. */
    fun exampleMs(mode: TimingMode): Int = TimingRules(config.timing).toleranceMs(1, 60.0, mode)

    /** Everything is read when the app starts; kept so the ported code reads like the Android code. */
    fun load() = Unit

    /** Re-reads after a backup import. */
    fun reload() {
        _records.value = loadRecords()
        _timingMode.value = loadTimingMode()
    }

    private fun save(next: Map<String, LessonRecord>) {
        _records.value = next
        store.put(SettingKeys.LESSON_RECORDS, json.encodeToString(recordsSerializer, next))
    }

    /** Steps whose confirming run failed on an earlier day go back to practice. */
    fun reopenFailedConfirmations(today: LocalDate = LocalDate.now()) {
        val next = _records.value.toMutableMap()
        var changed = false
        for (l in lessons) {
            val r = next[l.id] ?: continue
            if (ProgressRules.failedConfirmation(l, r, today)) { next[l.id] = ProgressRules.reopen(r); changed = true }
        }
        if (changed) save(next)
    }

    /** Allowed onset error for a stage and tempo in the user's strictness mode (or NORMAL for benchmarks). */
    fun toleranceMs(stage: Int, bpm: Double, benchmark: Boolean = false): Int =
        TimingRules(config.timing).toleranceMs(stage, bpm, if (benchmark) TimingMode.NORMAL else _timingMode.value)

    /** Records one run of a step's drill; returns true when the step is now fully passed. */
    fun recordDrill(lesson: Lesson, drillIndex: Int, met: Boolean, helperUsed: Boolean = false, day: LocalDate = LocalDate.now()): Boolean {
        val current = _records.value[lesson.id] ?: LessonRecord(lesson.id)
        var r = current.copy(attempts = current.attempts + 1)
        if (drillIndex in lesson.pass.requiredDrills) r = r.copy(triedDays = r.triedDays + day.toString())
        if (met && drillIndex in lesson.pass.requiredDrills) r = ProgressRules.recordDrillMet(r, lesson, drillIndex, day, helperUsed)
        save(_records.value + (lesson.id to r))
        return ProgressRules.isPassed(r, lesson)
    }

    /** Steps that ask for a number of passed pieces: met once enough of them are passed with the drill's hand. */
    fun checkPieceLessons(isPiecePassed: (pieceId: String, hand: String) -> Boolean): List<Lesson> {
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

    fun benchmarks(): List<BenchmarkResult> =
        store.get(SettingKeys.BENCHMARKS)?.let { runCatching { json.decodeFromString(benchSerializer, it) }.getOrNull() } ?: emptyList()

    fun updateBenchmark(change: (BenchmarkResult) -> BenchmarkResult) {
        store.put(SettingKeys.BENCHMARKS, json.encodeToString(benchSerializer, Benchmarks.update(benchmarks(), LocalDate.now(), change)))
    }

    // ---------------- spaced repetition ----------------

    fun itemStates(): Map<String, ItemState> = store.itemStats().mapValues { (id, r) -> ItemState(id, r.box, r.dueAt, r.lastResults.map { it == '1' }) }

    fun dueItems(prefix: String? = null, now: Long = System.currentTimeMillis()): List<String> =
        leitner.selectDue(itemStates().values.filter { prefix == null || it.itemId.startsWith(prefix) }, now).map { it.itemId }

    fun isReading(itemId: String) = itemId.startsWith("key:") || itemId.startsWith("note:")

    fun readingDueItems(now: Long = System.currentTimeMillis()): List<String> =
        leitner.selectDue(itemStates().values.filter { isReading(it.itemId) }, now).map { it.itemId }

    /** Adds items not seen before (box 1, due now). */
    fun enrol(itemIds: List<String>, now: Long = System.currentTimeMillis()) {
        val existing = itemStates()
        itemIds.filter { it !in existing }.forEach { put(leitner.newItem(it, now), null) }
    }

    fun review(itemId: String, correct: Boolean, responseMs: Long = 0, now: Long = System.currentTimeMillis()) {
        val state = itemStates()[itemId] ?: leitner.newItem(itemId, now)
        put(leitner.review(state, correct, responseMs, now), responseMs)
    }

    private fun put(state: ItemState, responseMs: Long?) {
        store.putItemStat(ItemStatRow(state.itemId, state.box, state.dueAtMillis, state.lastResults.joinToString("") { if (it) "1" else "0" }, responseMs))
    }

    /** The desktop keeps no attempt history (the phone's statistics stay on the phone). */
    @Suppress("UNUSED_PARAMETER")
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
    ) = Unit
}
