package com.id.mimikopi.core.playalong

import java.time.LocalDate

/**
 * Reviewing library pieces already passed (learning-system.md 2.12, step 2): a passed piece comes back on a
 * five-box schedule like 6.6, with longer gaps because a whole piece is bigger than one note. A clean run of the
 * whole piece, both hands, at the target tempo and without helpers on a due day moves it up a box; a run that is
 * not clean on a due day sends it back to the first box. The "play songs" part of today's plan opens the most
 * overdue piece first.
 */
object SongReview {
    /** Days until the next review, by box (1-5). */
    val GAPS = listOf(2, 4, 8, 16, 32)

    fun gap(box: Int): Int = GAPS[(box.coerceIn(1, GAPS.size)) - 1]

    /** The day the piece is next due, or null while it is not passed. A piece passed before reviews existed is due now. */
    fun dueOn(p: PieceProgress): LocalDate? {
        if (!PieceRules.isPassed(p)) return null
        val last = p.lastReview ?: return LocalDate.MIN
        return runCatching { LocalDate.parse(last).plusDays(gap(p.reviewBox).toLong()) }.getOrDefault(LocalDate.MIN)
    }

    fun isDue(p: PieceProgress, today: LocalDate): Boolean = dueOn(p)?.let { !it.isAfter(today) } == true

    /** Passed pieces due today, most overdue first. */
    fun due(all: Collection<PieceProgress>, today: LocalDate): List<PieceProgress> =
        all.filter { isDue(it, today) }.sortedWith(compareBy<PieceProgress> { dueOn(it) }.thenBy { it.pieceId })

    /**
     * After a run that counted for passing (whole piece, both hands, target tempo, no helper, timing judged).
     * [wasPassed]: the piece was passed before this run; a piece that becomes passed now starts in box 1.
     */
    fun afterCountingRun(before: PieceProgress, after: PieceProgress, clean: Boolean, day: LocalDate): PieceProgress = when {
        !PieceRules.isPassed(before) && PieceRules.isPassed(after) -> after.copy(reviewBox = 1, lastReview = day.toString())
        isDue(before, day) -> if (clean) after.copy(reviewBox = (before.reviewBox + 1).coerceAtMost(GAPS.size), lastReview = day.toString())
            else after.copy(reviewBox = 1, lastReview = day.toString())
        else -> after
    }
}
