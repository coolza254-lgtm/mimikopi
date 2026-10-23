package com.id.mimikopi.core.progress

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.backup.AttemptRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt

/**
 * One saved round of a lesson as the per-lesson history reads it (LESSON-HISTORY.md), shared by the phone and the
 * computer. Unknown legacy completion stays visible but never becomes comparable evidence.
 */
class HistoryAttempt(val row: AttemptRow) {
    private val params = runCatching { Json.parseToJsonElement(row.paramsJson).jsonObject }.getOrNull()
    fun value(key: String): String? = (params?.get(key) as? JsonPrimitive)?.contentOrNull
    val drillIndex: Int? = row.exerciseId.substringAfterLast(':').toIntOrNull()
    val complete: Boolean? = (params?.get("completed") as? JsonPrimitive)?.booleanOrNull
    val mode: String? = value("mode")
    val assisted: Boolean get() = row.helperUses > 0
    val comparable: Boolean get() = complete == true && !assisted && mode != null && value("inputPresent") != "false"
    // The seed deliberately varies for generated exercises. Pace, hand, section and timing criteria must match.
    val conditions: List<String?> get() = listOf(row.exerciseId, mode, row.hand, row.tempoBpm?.toString(),
        value("from"), value("to"), value("bpm"), value("toleranceMs"), value("beats"), value("seconds"), value("timingMode"),
        value("latencyMs"), value("inputSources"), value("piece"))
}

data class HistoryComparison(val first: HistoryAttempt, val latest: HistoryAttempt)

object AttemptHistory {
    /** First and latest comparable rounds with the latest round's exact conditions, or null with fewer than two. */
    fun comparison(rows: List<HistoryAttempt>): HistoryComparison? {
        val usable = rows.filter { it.comparable }.sortedWith(compareBy({ it.row.startedAt }, { it.row.id }))
        val latest = usable.lastOrNull() ?: return null
        val matches = usable.filter { it.conditions == latest.conditions }
        return if (matches.size < 2) null else HistoryComparison(matches.first(), matches.last())
    }

    fun fraction(value: Double?): String? = value?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let { "${(it * 100).roundToInt()}%" }

    fun handLabel(hand: String?): String = when (hand) {
        "R", "RIGHT" -> tr("มือขวา", "Right hand", "右手")
        "L", "LEFT" -> tr("มือซ้าย", "Left hand", "左手")
        "RL", "BOTH", "RIGHT+LEFT", "LEFT+RIGHT" -> tr("สองมือ", "Both hands", "両手")
        else -> tr("ไม่ได้ระบุมือ", "Hand not set", "手の指定なし")
    }

    fun modeLabel(mode: String?): String = when (mode) {
        "WAIT" -> tr("โหมดรอ · ไม่ตัดสินจังหวะ", "Wait mode · timing not judged", "待ちモード · リズムは判定しません")
        "TEMPO" -> tr("ตามจังหวะ", "In time", "リズムに合わせて")
        "ANSWER" -> tr("ตอบคำถาม", "Answering questions", "問題に答える")
        "PULSE" -> tr("เคาะจังหวะ", "Tapping rhythm", "リズムタップ")
        "CHORD_CHANGE" -> tr("เปลี่ยนคอร์ด", "Chord changes", "コードチェンジ")
        else -> tr("ประวัติเดิม", "Older history", "以前の履歴")
    }
}
