package com.id.mimikopi.ui.components

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.playalong.NoteResult

/** Visual events use the judge's verdict, never merely the set of held keys. */
enum class FallingVerdict(val caption: String) {
    HIT(tr("ถูก", "Right", "正解")), CORRECTED(tr("แก้ถูก", "Corrected", "修正")), EARLY(tr("เร็ว", "Early", "早い")), LATE(tr("ช้า", "Late", "遅い")), WRONG(tr("ผิดคีย์", "Wrong key", "違う鍵盤")), MISS(tr("พลาด", "Missed", "ミス"))
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
