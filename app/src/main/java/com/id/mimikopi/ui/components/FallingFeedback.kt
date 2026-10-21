package com.id.mimikopi.ui.components

import com.id.mimikopi.core.playalong.NoteResult

/** Visual events use the judge's verdict, never merely the set of held keys. */
enum class FallingVerdict(val caption: String) {
    HIT("ถูก"), CORRECTED("แก้ถูก"), EARLY("เร็ว"), LATE("ช้า"), WRONG("ผิดคีย์"), MISS("พลาด")
}

data class FallingFeedback(val midi: Int, val verdict: FallingVerdict, val left: Boolean, val atNanos: Long) {
    fun age(now: Long): Float = ((now - atNanos) / 850_000_000f).coerceAtLeast(0f)
    companion object {
        fun judged(midi: Int, result: NoteResult, left: Boolean, atNanos: Long): FallingFeedback? {
            val verdict = when (result) {
                NoteResult.CORRECT -> FallingVerdict.HIT
                NoteResult.CORRECTED -> FallingVerdict.CORRECTED
                NoteResult.EARLY -> FallingVerdict.EARLY
                NoteResult.LATE -> FallingVerdict.LATE
                NoteResult.MISSED -> FallingVerdict.MISS
                NoteResult.PENDING -> return null
            }
            return FallingFeedback(midi, verdict, left, atNanos)
        }
    }
}

/** Drop expired history as well as bounding chord/repeated-note bursts. */
internal fun List<FallingFeedback>.withFeedback(events: List<FallingFeedback>, now: Long): List<FallingFeedback> =
    (filter { it.age(now) < 1f } + events).takeLast(48)
