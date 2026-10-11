package com.id.mimikopi.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.core.session.PlannedBlock
import com.id.mimikopi.core.session.SessionPlanner
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.learning.PracticeStats
import com.id.mimikopi.learning.SessionTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class TodayUi(
    val minutes: Int = 30,
    val plan: List<PlannedBlock> = emptyList(),
    val currentLesson: Lesson? = null,
    /** Rhythm block: the latest rhythm drill reached (lesson, drill index). */
    val rhythmDrill: Pair<Lesson, Int>? = null,
    /** Warm-up block: five-finger or scale drill of the latest lesson reached. */
    val warmup: Pair<Lesson, Int>? = null,
    /** New-reading block: level 1-5, or null before reading lessons. */
    val newReadingLevel: Int? = null,
    val dueCount: Int = 0,
    val stats: PracticeStats? = null,
    val lessons: List<Pair<Lesson, LessonStatus>> = emptyList(),
    val lightDay: Boolean = false,
)

data class SessionSummary(val minutes: Long, val accuracyToday: Double?, val accuracyBefore: Double?, val dueTomorrow: Int)

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val repo = services.learning
    private val tracker: SessionTracker = services.session

    private val _ui = MutableStateFlow(TodayUi())
    val ui: StateFlow<TodayUi> = _ui.asStateFlow()
    val active: StateFlow<SessionTracker.Active?> = tracker.active

    private val _summary = MutableStateFlow<SessionSummary?>(null)
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()

    init {
        viewModelScope.launch {
            repo.load()
            val saved = services.database.settingsDao().get(SettingKeys.SESSION_MINUTES)?.toIntOrNull() ?: 30
            refresh(saved)
        }
    }

    fun refresh(minutes: Int = _ui.value.minutes) {
        viewModelScope.launch {
            val records = repo.records.value
            val lessons = repo.lessons
            val current = ProgressRules.current(lessons, records)
            val reached = lessons.filter { (records[it.id]?.attempts ?: 0) > 0 || it == current }
            val rhythmTypes = setOf(com.id.mimikopi.core.lesson.ExerciseType.E_RHYTHM_TAP, com.id.mimikopi.core.lesson.ExerciseType.E_PULSE)
            val rhythm = (reached.lastOrNull { l -> l.drills.any { it.type == com.id.mimikopi.core.lesson.ExerciseType.E_RHYTHM_TAP } }
                ?: lessons.firstOrNull { l -> l.drills.any { it.type in rhythmTypes } })
                ?.let { l -> l to l.drills.indexOfLast { it.type in rhythmTypes && it.mode == "TEMPO" }.coerceAtLeast(0) }
            val warmup = com.id.mimikopi.core.session.BlockContent.warmup(lessons, records)?.let { (id, i) -> repo.lesson(id)?.let { it to i } }
            val newReading = com.id.mimikopi.core.session.BlockContent.newReadingLevel(lessons, records)
            val due = repo.dueCount()
            // Day 7 of the week is lighter: review and favourite pieces, no new lesson (section 3).
            val light = LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY
            val available = buildSet {
                // Only blocks with real content (ear training comes in a later version).
                if (warmup != null) add(BlockKind.WARMUP)
                if (newReading != null) add(BlockKind.NEW_READING)
                add(BlockKind.REVIEW)
                if (rhythm != null) add(BlockKind.RHYTHM)
                if (current != null && !light) add(BlockKind.LESSON)
            }
            _ui.value = TodayUi(
                minutes = minutes,
                plan = SessionPlanner.plan(minutes, available),
                currentLesson = current,
                rhythmDrill = rhythm,
                warmup = warmup,
                newReadingLevel = newReading,
                dueCount = due,
                stats = repo.stats(),
                lessons = lessons.map { it to ProgressRules.status(it, records) },
                lightDay = light,
            )
        }
    }

    fun setMinutes(m: Int) {
        refresh(m)
        viewModelScope.launch { services.database.settingsDao().put(SettingEntity(SettingKeys.SESSION_MINUTES, m.toString())) }
    }

    fun startSession() {
        viewModelScope.launch { tracker.start(_ui.value.minutes) }
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
