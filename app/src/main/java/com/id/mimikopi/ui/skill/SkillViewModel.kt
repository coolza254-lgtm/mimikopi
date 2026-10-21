package com.id.mimikopi.ui.skill

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.lesson.ProgressRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The two practice ladders (M15). */
enum class Skill { READING }

/** One part of today's practice in a ladder (shared with the computer program, in core). */
typealias TodayKind = com.id.mimikopi.core.session.TodayKind
typealias TodayItem = com.id.mimikopi.core.session.TodayItem

data class LadderRow(val lesson: Lesson, val passDays: Int, val status: LessonStatus, val current: Boolean)

data class SkillUi(
    val today: List<TodayItem> = emptyList(),
    val rows: List<LadderRow> = emptyList(),
    val passed: Int = 0,
    val total: Int = 0,
    /** Every rung met at least once (the ladder is finished; only confirmations and reviews remain). */
    val finished: Boolean = false,
    val loaded: Boolean = false,
) {
    val next: TodayItem? get() = today.firstOrNull { !it.done }
    val currentStage: Int? get() = rows.firstOrNull { it.current }?.lesson?.stage
}

/**
 * One tab's practice: the reading ladder or the ear ladder. Nothing to choose: confirmations of steps passed on an
 * earlier day come first, then the review of due items, one older step repeated, and the next new step.
 */
class SkillViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val repo = services.learning

    private val _ui = MutableStateFlow(SkillUi())
    val ui: StateFlow<SkillUi> = _ui.asStateFlow()
    var skill: Skill = Skill.READING
        private set
    private var bound = false

    fun bind(s: Skill) {
        if (bound) return
        bound = true
        skill = s
        refresh()
    }

    private fun ladder(): List<Lesson> = repo.lessons

    fun refresh() {
        if (!bound) return
        viewModelScope.launch {
            repo.load()
            val today = LocalDate.now()
            repo.reopenFailedConfirmations(today)
            val records = repo.records.value
            val ladder = ladder()
            val items = com.id.mimikopi.core.session.LadderToday.items(ladder, records, repo.readingDueItems().isNotEmpty(), today)
            val step = ProgressRules.nextToLearn(ladder, records)

            _ui.value = SkillUi(
                today = items,
                rows = ladder.map { l -> LadderRow(l, records[l.id]?.passDays?.size ?: 0, ProgressRules.status(l, records), l.id == step?.id) },
                passed = ladder.count { ProgressRules.isPassed(records[it.id], it) },
                total = ladder.size,
                finished = step == null,
                loaded = true,
            )
        }
    }
}
