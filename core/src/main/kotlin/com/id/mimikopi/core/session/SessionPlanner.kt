package com.id.mimikopi.core.session

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
