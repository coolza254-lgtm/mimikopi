package com.id.mimikopi.core.progress

import com.id.mimikopi.core.ear.EarState
import com.id.mimikopi.core.session.DayLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Long-term views for the progress page, all from data the app already keeps: the level tests (levels as they stood
 * on each test day), the practice log (minutes per week) and the ear course (accuracy per week).
 */
object History {
    data class LevelChange(val date: LocalDate, val skill: SkillId, val from: Int, val to: Int)

    /** Sum of every skill's highest level ([SkillBook] maxima added up). */
    val MAX_TOTAL: Int get() = SkillId.entries.sumOf { SkillBook.maxLevel(it) }

    private fun date(text: String) = runCatching { LocalDate.parse(text) }.getOrNull()

    /** Every skill's level from the tests taken up to and including [day]. */
    fun levelsOn(records: List<CheckRecord>, day: LocalDate): Map<SkillId, Int> {
        val upTo = records.filter { it.date <= day.toString() }
        return SkillId.entries.associateWith { SkillBook.status(it, upTo, day).level }
    }

    /** The total level over the last [days] days: at the window's start, on each test day and today (a step line). */
    fun levelSeries(records: List<CheckRecord>, today: LocalDate, days: Long = 120): List<Pair<LocalDate, Int>> {
        val start = today.minusDays(days - 1)
        val dates = (records.mapNotNull { date(it.date) }.filter { !it.isBefore(start) && !it.isAfter(today) } + start + today).distinct().sorted()
        return dates.map { d -> d to levelsOn(records, d).values.sum() }
    }

    /** Level ups and downs within the last [days] days, newest first. */
    fun changes(records: List<CheckRecord>, today: LocalDate, days: Long = 120): List<LevelChange> {
        val start = today.minusDays(days - 1)
        val dates = records.mapNotNull { date(it.date) }.filter { !it.isBefore(start) && !it.isAfter(today) }.distinct().sorted()
        var before = levelsOn(records, start.minusDays(1))
        val out = mutableListOf<LevelChange>()
        for (d in dates) {
            val now = levelsOn(records, d)
            SkillId.entries.forEach { s -> if (now[s] != before[s]) out += LevelChange(d, s, before[s] ?: 0, now[s] ?: 0) }
            before = now
        }
        return out.asReversed()
    }

    /** Minutes practised in each of the last [weeks] weeks (Monday to Sunday), oldest first. */
    fun weeklyMinutes(log: List<DayLog>, today: LocalDate, weeks: Int = 12): List<Pair<LocalDate, Int>> {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (weeks - 1 downTo 0).map { back ->
            val monday = thisMonday.minusWeeks(back.toLong())
            val sunday = monday.plusDays(6)
            monday to log.filter { d -> date(d.date)?.let { !it.isBefore(monday) && !it.isAfter(sunday) } == true }.sumOf { it.total } / 60
        }
    }

    /** Ear first-try accuracy in each of the last [weeks] weeks (null = no ear work that week), oldest first. */
    fun earWeekly(state: EarState, today: LocalDate, weeks: Int = 12): List<Pair<LocalDate, Double?>> {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (weeks - 1 downTo 0).map { back ->
            val monday = thisMonday.minusWeeks(back.toLong())
            val sunday = monday.plusDays(6)
            val ss = state.sessions.filter { s -> date(s.day)?.let { !it.isBefore(monday) && !it.isAfter(sunday) } == true }
            monday to (if (ss.isEmpty()) null else ss.sumOf { it.right }.toDouble() / ss.sumOf { it.asked }.coerceAtLeast(1))
        }
    }
}
