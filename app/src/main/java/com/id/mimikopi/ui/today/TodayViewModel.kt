package com.id.mimikopi.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.learning.PracticeStats
import com.id.mimikopi.learning.SessionTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** One part of today's session, in the order it is done. */
enum class FlowKind { CONFIRM, REVIEW, REPEAT, STEP, EAR, BENCH }

data class FlowItem(
    val kind: FlowKind,
    val title: String,
    val detail: String,
    /** The step or ear rung it opens (null for the review and the benchmark). */
    val lessonId: String? = null,
    val done: Boolean = false,
    /** Run today without meeting the criterion yet (can be retried). */
    val notYet: Boolean = false,
    /** Extra for today (the next step after one was met): not needed for the day to be complete. */
    val optional: Boolean = false,
)

data class TodayUi(
    val items: List<FlowItem> = emptyList(),
    val step: Lesson? = null,
    /** Steps met at least once, of all steps. */
    val stepsMet: Int = 0,
    val stepsTotal: Int = 0,
    val earMet: Int = 0,
    val earTotal: Int = 0,
    val dueCount: Int = 0,
    val stats: PracticeStats? = null,
    val loaded: Boolean = false,
) {
    /** The next thing to do: the first item not done. */
    val next: FlowItem? get() = items.firstOrNull { !it.done && !it.optional }
    val allDone: Boolean get() = loaded && items.isNotEmpty() && items.all { it.done || it.optional }
}

data class SessionSummary(val minutes: Long, val accuracyToday: Double?, val accuracyBefore: Double?, val dueTomorrow: Int)

/** What the morning's session asked for, kept for the day so finished parts can be ticked. */
@Serializable
private data class FlowSnapshot(val confirm: List<String> = emptyList(), val repeat: String? = null, val review: Boolean = false)

/**
 * Today's session on the step ladder (M14): one list done top to bottom — confirm yesterday's steps, review,
 * repeat one older step, today's new step (more may follow, there is no time limit), the ear rung, and the
 * benchmark when it is due. Nothing to choose: the first unfinished item is the button.
 */
class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val repo = services.learning
    private val tracker: SessionTracker = services.session
    private val json = Json { ignoreUnknownKeys = true }

    private val _ui = MutableStateFlow(TodayUi())
    val ui: StateFlow<TodayUi> = _ui.asStateFlow()
    val active: StateFlow<SessionTracker.Active?> = tracker.active

    private val _summary = MutableStateFlow<SessionSummary?>(null)
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repo.load()
            val today = LocalDate.now()
            repo.reopenFailedConfirmations(today)
            val records = repo.records.value
            val t = today.toString()
            val snapshot = snapshotFor(today, records)
            val items = mutableListOf<FlowItem>()

            for (id in snapshot.confirm) {
                val l = repo.lesson(id) ?: continue
                val r = records[id]
                val done = r?.passDays?.contains(t) == true
                items += FlowItem(FlowKind.CONFIRM, l.title, if (l.id.startsWith("e")) "ยืนยันการฟังที่ผ่านเมื่อวาน" else "ยืนยันขั้นที่ผ่านไปแล้ว ทำให้ผ่านเกณฑ์อีกครั้ง",
                    id, done, notYet = !done && r?.triedDays?.contains(t) == true)
            }
            val due = repo.readingDueItems().size
            if (snapshot.review) items += FlowItem(FlowKind.REVIEW, "ทวนโน้ตและคีย์", if (due > 0) "ถึงรอบทวน $due รายการ" else "ทวนครบแล้ว", done = due == 0)
            snapshot.repeat?.let { id ->
                repo.lesson(id)?.let { l ->
                    items += FlowItem(FlowKind.REPEAT, l.title, "ฝึกซ้ำขั้นเก่า ให้ติดมือ", id, done = records[id]?.triedDays?.contains(t) == true)
                }
            }
            // New steps: those first met today (ticked), then the current one.
            val metToday = repo.lessons.filter { records[it.id]?.passDays == setOf(t) }
            metToday.forEach { items += FlowItem(FlowKind.STEP, it.title, "ขั้นใหม่ ผ่านวันนี้แล้ว พรุ่งนี้ยืนยันอีกครั้ง", it.id, done = true) }
            val step = repo.currentStep()
            if (step != null) items += FlowItem(FlowKind.STEP, step.title,
                if (metToday.isEmpty()) "ขั้นใหม่วันนี้: อ่านสั้นๆ แล้วฝึกซ้ำจนผ่านเกณฑ์" else "ขั้นต่อไป (ไม่บังคับ ฝึกต่อได้ถ้ายังไหว)",
                step.id, done = false, notYet = records[step.id]?.triedDays?.contains(t) == true, optional = metToday.isNotEmpty())
            val earToday = repo.earRungs.filter { records[it.id]?.passDays == setOf(t) }
            earToday.forEach { items += FlowItem(FlowKind.EAR, it.title, "ฝึกฟัง ผ่านวันนี้แล้ว", it.id, done = true) }
            val rung = repo.currentEarRung()
            if (rung != null && earToday.isEmpty()) items += FlowItem(FlowKind.EAR, rung.title, "ฝึกฟังขั้นเล็กๆ 20 ข้อ", rung.id,
                notYet = records[rung.id]?.triedDays?.contains(t) == true)
            val bench = repo.benchmarks()
            if (com.id.mimikopi.core.progress.Benchmarks.isDue(bench, today))
                items += FlowItem(FlowKind.BENCH, "วัดผลทุก 2 สัปดาห์", "ชุดโจทย์เดิมทุกครั้ง เพื่อดูว่าเก่งขึ้นจริงไหม",
                    done = bench.any { it.date == t })

            val finalItems = items
            _ui.value = TodayUi(
                items = finalItems,
                step = step,
                stepsMet = repo.lessons.count { !records[it.id]?.passDays.isNullOrEmpty() },
                stepsTotal = repo.lessons.size,
                earMet = repo.earRungs.count { !records[it.id]?.passDays.isNullOrEmpty() },
                earTotal = repo.earRungs.size,
                dueCount = due,
                stats = repo.stats(),
                loaded = true,
            )
        }
    }

    /** The day's fixed parts: decided on the first open of the day, then kept so they can be ticked off. */
    private suspend fun snapshotFor(today: LocalDate, records: Map<String, LessonRecord>): FlowSnapshot {
        val dao = services.database.settingsDao()
        val key = SettingKeys.FLOW_PREFIX + today
        dao.get(key)?.let { raw -> runCatching { json.decodeFromString(FlowSnapshot.serializer(), raw) }.getOrNull() }?.let { return it }
        val confirm = repo.confirmations(today).take(3).map { it.id }
        // Repetition: one older, confirmed step (not a piece-rule or song step), chosen by the date.
        val done = repo.lessons.filter { l ->
            val r = records[l.id]
            r != null && r.passDays.size >= 2 && l.pass.piecesPassed == null && l.pass.songsCompleted == null &&
                l.drills.first().type != ExerciseType.E_PULSE && l.id !in confirm
        }
        val repeat = if (done.isEmpty()) null else done[Math.floorMod(today.toEpochDay().toInt(), done.size)].id
        val snapshot = FlowSnapshot(confirm, repeat, review = repo.readingDueItems().isNotEmpty())
        dao.put(SettingEntity(key, json.encodeToString(FlowSnapshot.serializer(), snapshot)))
        return snapshot
    }

    /** Practice minutes are counted from the first part opened until "finish". */
    fun opened() {
        if (tracker.active.value == null) viewModelScope.launch { tracker.start(0) }
    }

    fun finishSession() {
        viewModelScope.launch {
            val minutes = tracker.finish()
            val (today, before) = repo.accuracyTodayVsBefore()
            _summary.value = SessionSummary(minutes, today, before, repo.dueTomorrowCount())
            refresh()
        }
    }

    fun dismissSummary() {
        _summary.value = null
    }
}
