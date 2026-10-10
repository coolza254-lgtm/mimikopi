package com.id.mimikopi.core.srs

import com.id.mimikopi.core.config.SrsConfig
import com.id.mimikopi.core.time.MILLIS_PER_DAY

/**
 * Review state of one item, e.g. "note:G4:treble" or "key:C" (learning-system.md 2.2, 6.6).
 * [lastResults] keeps the most recent outcomes, newest last.
 */
data class ItemState(
    val itemId: String,
    val box: Int,
    val dueAtMillis: Long,
    val lastResults: List<Boolean> = emptyList(),
) {
    val lastWasWrong: Boolean get() = lastResults.lastOrNull() == false
}

/** Five-box Leitner scheduler. Pure: every call takes "now" explicitly. */
class Leitner(private val config: SrsConfig = SrsConfig()) {
    val boxCount: Int get() = config.boxIntervalsDays.size

    init {
        require(config.boxIntervalsDays.isNotEmpty())
    }

    /** A new item starts in box 1 and is due immediately. */
    fun newItem(itemId: String, nowMillis: Long) = ItemState(itemId, box = 1, dueAtMillis = nowMillis)

    /**
     * Correct and fast enough -> up one box; wrong or too slow -> back to box 1.
     * [maxResponseMs] defaults to the note-reading limit; other item kinds may pass their own.
     */
    fun review(
        state: ItemState,
        correct: Boolean,
        responseMs: Long,
        nowMillis: Long,
        maxResponseMs: Long = config.noteReadingMaxResponseMs,
    ): ItemState {
        val passed = correct && responseMs <= maxResponseMs
        val box = if (passed) minOf(state.box + 1, boxCount) else 1
        return state.copy(
            box = box,
            dueAtMillis = nowMillis + intervalDays(box) * MILLIS_PER_DAY,
            lastResults = (state.lastResults + passed).takeLast(HISTORY),
        )
    }

    fun intervalDays(box: Int): Int = config.boxIntervalsDays[box.coerceIn(1, boxCount) - 1]

    /**
     * Items to review now: only due items, recently-wrong first, then most overdue first,
     * capped at the per-session maximum.
     */
    fun selectDue(items: Collection<ItemState>, nowMillis: Long): List<ItemState> =
        items.asSequence()
            .filter { it.dueAtMillis <= nowMillis }
            .sortedWith(compareByDescending<ItemState> { it.lastWasWrong }.thenBy { it.dueAtMillis }.thenBy { it.itemId })
            .take(config.maxItemsPerSession)
            .toList()

    private companion object {
        const val HISTORY = 10
    }
}
