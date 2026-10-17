package com.id.mimikopi.desktop.port.skill

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/skill/SkillViewModel.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
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

/** One part of today's practice in a ladder, done top to bottom. */
enum class TodayKind { CONFIRM, REVIEW, REPEAT, STEP }

data class TodayItem(
    val kind: TodayKind,
    val title: String,
    /** The step or rung it opens (null for the review). */
    val lessonId: String?,
    val done: Boolean,
    /** Run today without meeting the criterion yet. */
    val notYet: Boolean = false,
    /** Ran it before: open the drill directly instead of the step page. */
    val tried: Boolean = false,
)

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
            val t = today.toString()
            repo.reopenFailedConfirmations(today)
            val records = repo.records.value
            val ladder = ladder()
            val items = mutableListOf<TodayItem>()

            // Confirmations: due ones, and those confirmed today (ticked).
            for (l in ladder) {
                val r = records[l.id] ?: continue
                if (l.pass.days < 2) continue
                val due = r.passDays.size in 1 until l.pass.days && r.passDays.all { it < t }
                val confirmedToday = r.passDays.size >= 2 && t in r.passDays && r.passDays.any { it < t }
                if (due || confirmedToday) items += TodayItem(TodayKind.CONFIRM, l.title, l.id, done = confirmedToday,
                    notYet = due && t in r.triedDays, tried = true)
            }
            if (repo.readingDueItems().isNotEmpty()) items += TodayItem(TodayKind.REVIEW, "", null, done = false)
            // Repetition: one older, confirmed step, chosen by the date (practice makes it stick).
            val older = ladder.filter { l ->
                val r = records[l.id]
                r != null && r.passDays.size >= 2 && l.pass.piecesPassed == null && l.drills.first().type != ExerciseType.E_PULSE &&
                    items.none { it.lessonId == l.id }
            }
            if (older.size >= 3) {
                val l = older[Math.floorMod(today.toEpochDay().toInt(), older.size)]
                items += TodayItem(TodayKind.REPEAT, l.title, l.id, done = t in records[l.id]!!.triedDays, tried = true)
            }
            // New steps met today (ticked), then the next one.
            ladder.filter { records[it.id]?.passDays == setOf(t) }.forEach { items += TodayItem(TodayKind.STEP, it.title, it.id, done = true, tried = true) }
            val step = ProgressRules.nextToLearn(ladder, records)
            if (step != null) {
                val r = records[step.id]
                items += TodayItem(TodayKind.STEP, step.title, step.id, done = false, notYet = r?.triedDays?.contains(t) == true, tried = (r?.attempts ?: 0) > 0)
            }

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
