package com.id.mimikopi.desktop.learning

import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.config.LearningConfig
import com.id.mimikopi.core.ear.EarStats
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.srs.ItemState
import com.id.mimikopi.core.srs.Leitner
import com.id.mimikopi.desktop.Keys
import com.id.mimikopi.desktop.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Reads a bundled content file (content/ is on the classpath). */
fun resourceText(path: String): String? = Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

/**
 * The ear ladder on the desktop: rungs (content/ear), their records (the same "lesson.records" row as the Android app,
 * so progress moves with a backup) and the spaced-repetition stats of ear items.
 */
class EarLearning(private val store: Store) {
    private val json = Json { ignoreUnknownKeys = true }
    private val recordsSerializer = MapSerializer(String.serializer(), LessonRecord.serializer())

    val config: LearningConfig = resourceText("config/learning.json")?.let { LearningConfig.fromJson(it) } ?: LearningConfig()
    val leitner = Leitner(config.srs)

    /** e1, e2 ... until the first missing file. */
    val rungs: List<Lesson> = generateSequence(1) { it + 1 }.map { resourceText("ear/e$it.json") }.takeWhile { it != null }
        .map { Lesson.parse(it!!) }.toList()

    fun rung(id: String): Lesson? = rungs.firstOrNull { it.id == id }

    private val _records = MutableStateFlow(load())
    val records: StateFlow<Map<String, LessonRecord>> = _records.asStateFlow()

    private fun load(): Map<String, LessonRecord> =
        store.get(Keys.LESSON_RECORDS)?.let { runCatching { json.decodeFromString(recordsSerializer, it) }.getOrNull() } ?: emptyMap()

    /** Re-reads after a backup import. */
    fun reload() { _records.value = load() }

    private fun save(next: Map<String, LessonRecord>) {
        _records.value = next
        store.put(Keys.LESSON_RECORDS, json.encodeToString(recordsSerializer, next))
    }

    /** Steps whose confirming run failed on an earlier day go back to practice. */
    fun reopenFailedConfirmations(today: LocalDate = LocalDate.now()) {
        val next = _records.value.toMutableMap()
        var changed = false
        for (l in rungs) {
            val r = next[l.id] ?: continue
            if (ProgressRules.failedConfirmation(l, r, today)) { next[l.id] = ProgressRules.reopen(r); changed = true }
        }
        if (changed) save(next)
    }

    /** Records one run of a rung's drill; returns true when the rung is now fully passed. */
    fun recordDrill(lesson: Lesson, drillIndex: Int, met: Boolean, day: LocalDate = LocalDate.now()): Boolean {
        val current = _records.value[lesson.id] ?: LessonRecord(lesson.id)
        var r = current.copy(attempts = current.attempts + 1)
        if (drillIndex in lesson.pass.requiredDrills) r = r.copy(triedDays = r.triedDays + day.toString())
        if (met && drillIndex in lesson.pass.requiredDrills) r = ProgressRules.recordDrillMet(r, lesson, drillIndex, day, helperUsed = false)
        save(_records.value + (lesson.id to r))
        return ProgressRules.isPassed(r, lesson)
    }

    // ---------------- spaced repetition of ear items ----------------

    private fun states(): Map<String, ItemState> = store.itemStats().mapValues { (id, r) -> ItemState(id, r.box, r.dueAt, r.lastResults.map { it == '1' }) }

    fun review(itemId: String, correct: Boolean, now: Long = System.currentTimeMillis()) {
        val state = states()[itemId] ?: leitner.newItem(itemId, now)
        val next = leitner.review(state, correct, 0, now)
        store.putItemStat(ItemStatRow(next.itemId, next.box, next.dueAtMillis, next.lastResults.joinToString("") { if (it) "1" else "0" }, null))
    }

    fun earStats(): List<EarStats.ItemStat> = store.itemStats().values.filter { it.itemId.startsWith("ear:") }
        .map { EarStats.ItemStat(it.itemId, it.lastResults.count { c -> c == '1' }, it.lastResults.length, it.medianResponseMs) }

    fun earDueCount(now: Long = System.currentTimeMillis()): Int =
        leitner.selectDue(states().values.filter { it.itemId.startsWith("ear:") }, now).size
}
