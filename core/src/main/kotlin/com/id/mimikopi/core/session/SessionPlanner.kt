package com.id.mimikopi.core.session

import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.ProgressRules

/** Blocks of the daily session (learning-system.md section 3). */
enum class BlockKind { WARMUP, REVIEW, RHYTHM, LESSON, NEW_READING, EAR, FREE_PLAY }

data class PlannedBlock(val kind: BlockKind, val minutes: Int)

/**
 * Splits the chosen practice time into blocks using the 30-minute template (warm-up 3, review 5,
 * rhythm 4, lesson 10, new reading 4, ear 4) scaled to the time chosen. Blocks whose content is not
 * available yet are left out and their time goes to the others. A free-play block (no scoring) is
 * always included (2.4): 3 minutes, 15 at 45 minutes or more (section 3).
 */
object SessionPlanner {
    private val TEMPLATE = linkedMapOf(
        BlockKind.WARMUP to 3, BlockKind.REVIEW to 5, BlockKind.RHYTHM to 4,
        BlockKind.LESSON to 10, BlockKind.NEW_READING to 4, BlockKind.EAR to 4,
    )

    fun plan(totalMinutes: Int, available: Set<BlockKind>): List<PlannedBlock> {
        require(totalMinutes in 10..120)
        val free = if (totalMinutes >= 45) 15 else 3
        val budget = totalMinutes - free
        val used = TEMPLATE.filterKeys { it in available }
        val weight = used.values.sum().takeIf { it > 0 } ?: return listOf(PlannedBlock(BlockKind.FREE_PLAY, totalMinutes))
        // Largest-remainder rounding: floors first, then the leftover minutes go to the biggest fractions,
        // so the plan adds up exactly without distorting one block.
        val exact = used.mapValues { (_, w) -> budget * w.toDouble() / weight }
        val minutes = exact.mapValues { (_, x) -> maxOf(1, x.toInt()) }.toMutableMap()
        var left = budget - minutes.values.sum()
        for (k in exact.keys.sortedByDescending { exact.getValue(it) - exact.getValue(it).toInt() }) {
            if (left <= 0) break
            minutes[k] = minutes.getValue(k) + 1
            left--
        }
        val blocks = used.keys.map { PlannedBlock(it, minutes.getValue(it)) }
        return blocks + PlannedBlock(BlockKind.FREE_PLAY, free)
    }
}

/** Picks content for the warm-up and new-reading blocks (learning-system.md 3). */
object BlockContent {
    private fun readLevel(l: Lesson): Int? =
        l.drills.filter { it.type == ExerciseType.E_READ_PIECE }
            .maxOfOrNull { it.level.removePrefix("L").toInt() }

    /**
     * New reading is one or two levels below the current lesson: here one level below the highest level met so
     * far (lesson passed, or the current lesson), never below L1. Null until the first reading lesson is reached.
     */
    fun newReadingLevel(lessons: List<Lesson>, records: Map<String, LessonRecord>): Int? {
        val current = ProgressRules.current(lessons, records)
        val reached = lessons.filter { ProgressRules.isPassed(records[it.id], it) || it == current }
            .mapNotNull { readLevel(it) }.maxOrNull() ?: return null
        val passedAtReached = lessons.any { readLevel(it) == reached && ProgressRules.isPassed(records[it.id], it) }
        return if (passedAtReached) reached else maxOf(1, reached - 1)
    }

    /**
     * Warm-up: the five-finger or scale drill of the latest lesson reached that has one (lesson index, drill index).
     */
    fun warmup(lessons: List<Lesson>, records: Map<String, LessonRecord>): Pair<String, Int>? {
        val current = ProgressRules.current(lessons, records)
        val reached = lessons.filter { (records[it.id]?.attempts ?: 0) > 0 || it == current }
        for (l in reached.reversed()) {
            val i = l.drills.indexOfLast {
                it.type == ExerciseType.E_FINGER_PATTERN ||
                    (it.type == ExerciseType.E_PIECE && it.pieces.all { p -> p.startsWith("ex-") })
            }
            if (i >= 0) return l.id to i
        }
        return null
    }
}
