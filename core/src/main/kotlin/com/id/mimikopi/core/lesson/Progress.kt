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
)

enum class LessonStatus { LOCKED, AVAILABLE, IN_PROGRESS, PASSED }

/** Unlocking and passing (learning-system.md 6.4): criterion met on N different days; next unlocks after. */
object ProgressRules {
    fun recordPass(record: LessonRecord, day: LocalDate, helperUsed: Boolean): LessonRecord =
        if (helperUsed) record.copy(helperDays = record.helperDays + day.toString())
        else record.copy(passDays = record.passDays + day.toString())

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

    /** The lesson to work on now: the first not-yet-passed, unlocked lesson in order. */
    fun current(lessons: List<Lesson>, records: Map<String, LessonRecord>): Lesson? =
        lessons.firstOrNull { status(it, records).let { s -> s == LessonStatus.AVAILABLE || s == LessonStatus.IN_PROGRESS } }
}
