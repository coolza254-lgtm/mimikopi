package com.id.mimikopi.core.lesson

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Saved per lesson. Dates are local calendar days (ISO yyyy-mm-dd). */
@Serializable
data class LessonRecord(
    val lessonId: String,
    /** Days on which the pass criterion was met without helpers. */
    val passDays: Set<String> = emptySet(),
    /** Days on which it was met but a helper (e.g. note names) was used: not a full pass (2.4). */
    val helperDays: Set<String> = emptySet(),
    val attempts: Int = 0,
    /** Days on which each drill (by index) met its criterion without helpers; a pass day needs every required drill. */
    val drillDays: Map<Int, Set<String>> = emptyMap(),
    /** Days on which a required drill was run at all (met or not): the step ladder's confirmation check. */
    val triedDays: Set<String> = emptySet(),
    /** The day the step was sent back to practice ([ProgressRules.reopen]); pass days before it no longer count (sync keeps it so). */
    val reopenedOn: String? = null,
)

enum class LessonStatus { LOCKED, AVAILABLE, IN_PROGRESS, PASSED }

/** Unlocking and passing (learning-system.md 6.4): criterion met on N different days; next unlocks after. */
object ProgressRules {
    fun recordPass(record: LessonRecord, day: LocalDate, helperUsed: Boolean): LessonRecord =
        if (helperUsed) record.copy(helperDays = record.helperDays + day.toString())
        else record.copy(passDays = record.passDays + day.toString())

    /**
     * Records that drill [drillIndex] met its criterion on [day]. The day becomes a pass day once every
     * required drill of the lesson met its criterion that day. A helper makes it a helper day instead.
     */
    fun recordDrillMet(record: LessonRecord, lesson: Lesson, drillIndex: Int, day: LocalDate, helperUsed: Boolean): LessonRecord {
        if (helperUsed) return record.copy(helperDays = record.helperDays + day.toString())
        val d = day.toString()
        val days = record.drillDays + (drillIndex to (record.drillDays[drillIndex].orEmpty() + d))
        var r = record.copy(drillDays = days)
        if (lesson.pass.requiredDrills.all { d in days[it].orEmpty() }) r = r.copy(passDays = r.passDays + d)
        return r
    }

    fun isPassed(record: LessonRecord?, lesson: Lesson): Boolean = (record?.passDays?.size ?: 0) >= lesson.pass.days

    fun status(lesson: Lesson, records: Map<String, LessonRecord>): LessonStatus {
        val record = records[lesson.id]
        if (isPassed(record, lesson)) return LessonStatus.PASSED
        // A lesson unlocks once its prerequisites met their criterion at least once; the second day
        // that completes the full pass can come later (spacing), so learning keeps moving.
        val prerequisitesMet = lesson.prerequisites.all { p -> (records[p]?.passDays?.size ?: 0) >= 1 }
        if (!prerequisitesMet) return LessonStatus.LOCKED
        return if ((record?.attempts ?: 0) > 0) LessonStatus.IN_PROGRESS else LessonStatus.AVAILABLE
    }

    // ---------------- step ladder (M14): pass today, confirm on a later day ----------------

    /**
     * Steps passed on one day that still need their confirming day (shown first in the next day's session),
     * oldest first. Steps passed in one day (pieces, songs) never wait.
     */
    fun awaitingConfirmation(lessons: List<Lesson>, records: Map<String, LessonRecord>, today: LocalDate): List<Lesson> =
        lessons.filter { l ->
            val r = records[l.id] ?: return@filter false
            l.pass.days > 1 && r.passDays.size in 1 until l.pass.days && r.passDays.all { it < today.toString() }
        }

    /**
     * A step passed on one day, then run again on a later day (before today) without meeting its criterion,
     * failed its confirmation: it goes back to practice so the ladder returns to it.
     */
    fun failedConfirmation(lesson: Lesson, record: LessonRecord, today: LocalDate): Boolean {
        if (lesson.pass.days < 2 || record.passDays.size != 1) return false
        val passed = record.passDays.first()
        return record.triedDays.any { it > passed && it < today.toString() && it !in record.passDays }
    }

    /** The ladder's step for today: the first unlocked step not yet met on any day (confirmations are separate). */
    fun nextToLearn(lessons: List<Lesson>, records: Map<String, LessonRecord>): Lesson? =
        lessons.firstOrNull { l -> records[l.id]?.passDays.isNullOrEmpty() && status(l, records) != LessonStatus.LOCKED }

    /** Back to practice: the pass days are cleared, attempts and history stay. */
    fun reopen(record: LessonRecord, on: LocalDate? = null): LessonRecord =
        record.copy(passDays = emptySet(), drillDays = emptyMap(), reopenedOn = on?.toString() ?: record.reopenedOn)

    /** The lesson to work on now: the first not-yet-passed, unlocked lesson in order. */
    fun current(lessons: List<Lesson>, records: Map<String, LessonRecord>): Lesson? =
        lessons.firstOrNull { status(it, records).let { s -> s == LessonStatus.AVAILABLE || s == LessonStatus.IN_PROGRESS } }
}
