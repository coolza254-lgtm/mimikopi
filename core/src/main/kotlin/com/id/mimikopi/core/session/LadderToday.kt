package com.id.mimikopi.core.session

import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.ProgressRules
import java.time.LocalDate

/** One part of today's practice in the step ladder, done top to bottom. */
enum class TodayKind { CONFIRM, REVIEW, REPEAT, STEP }

data class TodayItem(
    val kind: TodayKind,
    val title: String,
    /** The step it opens (null for the review). */
    val lessonId: String?,
    val done: Boolean,
    /** Run today without meeting the criterion yet. */
    val notYet: Boolean = false,
    /** Ran it before: open the drill directly instead of the step page. */
    val tried: Boolean = false,
)

/**
 * Today's ladder practice, shared by the phone and the computer. Nothing to choose: confirmations of steps passed on
 * an earlier day come first, then the review of due items, one older step repeated, and the next new step.
 */
object LadderToday {
    fun items(ladder: List<Lesson>, records: Map<String, LessonRecord>, readingDue: Boolean, today: LocalDate): List<TodayItem> {
        val t = today.toString()
        val items = mutableListOf<TodayItem>()
        for (l in ladder) {
            val r = records[l.id] ?: continue
            if (l.pass.days < 2) continue
            val due = r.passDays.size in 1 until l.pass.days && r.passDays.all { it < t }
            val confirmedToday = r.passDays.size >= 2 && t in r.passDays && r.passDays.any { it < t }
            if (due || confirmedToday) items += TodayItem(TodayKind.CONFIRM, l.title, l.id, done = confirmedToday, notYet = due && t in r.triedDays, tried = true)
        }
        if (readingDue) items += TodayItem(TodayKind.REVIEW, "", null, done = false)
        // Repetition: one older, confirmed step, chosen by the date (practice makes it stick).
        val older = ladder.filter { l ->
            val r = records[l.id]
            r != null && r.passDays.size >= 2 && l.pass.piecesPassed == null && l.drills.first().type != ExerciseType.E_PULSE && items.none { it.lessonId == l.id }
        }
        if (older.size >= 3) {
            val l = older[Math.floorMod(today.toEpochDay().toInt(), older.size)]
            items += TodayItem(TodayKind.REPEAT, l.title, l.id, done = t in records[l.id]!!.triedDays, tried = true)
        }
        ladder.filter { records[it.id]?.passDays == setOf(t) }.forEach { items += TodayItem(TodayKind.STEP, it.title, it.id, done = true, tried = true) }
        val step = ProgressRules.nextToLearn(ladder, records)
        if (step != null) {
            val r = records[step.id]
            items += TodayItem(TodayKind.STEP, step.title, step.id, done = false, notYet = r?.triedDays?.contains(t) == true, tried = (r?.attempts ?: 0) > 0)
        }
        return items
    }
}
