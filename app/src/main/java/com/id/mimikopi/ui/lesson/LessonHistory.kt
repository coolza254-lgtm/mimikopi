package com.id.mimikopi.ui.lesson

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.data.db.AttemptEntity
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Unknown legacy completion is kept visible but never promoted into comparable evidence. */
internal class LessonAttempt(val row: AttemptEntity) {
    private val params = runCatching { Json.parseToJsonElement(row.paramsJson).jsonObject }.getOrNull()
    fun value(key: String): String? = (params?.get(key) as? JsonPrimitive)?.contentOrNull
    val drillIndex = row.exerciseId.substringAfterLast(':').toIntOrNull()
    val complete = (params?.get("completed") as? JsonPrimitive)?.booleanOrNull
    val mode = value("mode")
    val assisted get() = row.helperUses > 0
    val comparable get() = complete == true && !assisted && mode != null && value("inputPresent") != "false"
    // Seed deliberately varies for generated exercises. Pace, hand, section and timing criteria must match.
    val conditions get() = listOf(row.exerciseId, mode, row.hand, row.tempoBpm?.toString(),
        value("from"), value("to"), value("bpm"), value("toleranceMs"), value("beats"), value("seconds"), value("timingMode"), value("latencyMs"), value("inputSources"), value("piece"))
}

internal data class LessonComparison(val first: LessonAttempt, val latest: LessonAttempt)
internal fun lessonComparison(rows: List<LessonAttempt>): LessonComparison? {
    val usable = rows.filter { it.comparable }.sortedWith(compareBy({ it.row.startedAtMillis }, { it.row.id }))
    val latest = usable.lastOrNull() ?: return null
    val matches = usable.filter { it.conditions == latest.conditions }
    return if (matches.size < 2) null else LessonComparison(matches.first(), matches.last())
}
private fun fraction(value: Double?) = value?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let { "${(it * 100).roundToInt()}%" }
private fun handLabel(hand: String?) = when (hand) {
    "R", "RIGHT" -> "มือขวา"
    "L", "LEFT" -> "มือซ้าย"
    "RL", "BOTH", "RIGHT+LEFT", "LEFT+RIGHT" -> "สองมือ"
    else -> "ไม่ได้ระบุมือ"
}
private fun modeLabel(mode: String?) = when (mode) {
    "WAIT" -> "โหมดรอ · ไม่ตัดสินจังหวะ"
    "TEMPO" -> "ตามจังหวะ"
    "ANSWER" -> "ตอบคำถาม"
    "PULSE" -> "เคาะจังหวะ"
    "CHORD_CHANGE" -> "เปลี่ยนคอร์ด"
    else -> "ประวัติเดิม"
}

@Composable
internal fun LessonHistory(lesson: Lesson, history: List<AttemptEntity>) {
    var selected by rememberSaveable(lesson.id) { mutableIntStateOf(-1) }
    var limit by rememberSaveable(lesson.id, selected) { mutableIntStateOf(10) }
    val all = remember(history) { history.map(::LessonAttempt) }
    val rows = all.filter { selected == -1 || it.drillIndex == selected }
    val comparison = remember(rows) { lessonComparison(rows) }
    val zone = ZoneId.systemDefault()
    val date = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }
    PageCard(title = "ประวัติและพัฒนาการบทนี้", modifier = Modifier.testTag("lesson-history")) {
        Text("บันทึกผลอัตโนมัติเมื่อจบรอบ · ข้อมูลเดิมยังอยู่หลังอัปเดต", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        if (all.isEmpty()) {
            Text("ยังไม่มีผลการเล่นบทนี้ เริ่มฝึกแล้วกลับมาดูผลได้ที่นี่", modifier = Modifier.testTag("lesson-history-empty"))
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected == -1, { selected = -1 }, label = { Text("ทั้งหมด") })
                lesson.drills.forEachIndexed { i, d ->
                    FilterChip(selected == i, { selected = i }, label = { Text(d.title) }, modifier = Modifier.testTag("lesson-history-filter-$i"))
                }
            }
            val days = rows.map { Instant.ofEpochMilli(it.row.startedAtMillis).atZone(zone).toLocalDate() }.distinct().size
            Text("${rows.size} รอบ · $days วันที่มีผลบันทึก", style = MaterialTheme.typography.titleSmall)
            comparison?.let { c ->
                val notes = listOfNotNull(fraction(c.first.row.noteAccuracy), fraction(c.latest.row.noteAccuracy))
                val timing = listOfNotNull(fraction(c.first.row.timingWithinTol), fraction(c.latest.row.timingWithinTol))
                Text("เทียบรอบแรกกับล่าสุดที่เงื่อนไขเดียวกัน", style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(
                    if (notes.size == 2) "โน้ต ${notes[0]} → ${notes[1]}" else null,
                    if (timing.size == 2) "จังหวะ ${timing[0]} → ${timing[1]}" else null,
                    if (c.first.value("count") != null && c.latest.value("count") != null) "เปลี่ยนคอร์ด ${c.first.value("count")} → ${c.latest.value("count")} ครั้ง" else null,
                ).joinToString(" · ").ifEmpty { "มี ${rows.count { it.conditions == c.latest.conditions && it.comparable }} รอบที่เทียบกันได้" },
                    modifier = Modifier.testTag("lesson-history-comparison"))
                Text("${handLabel(c.latest.row.hand)} · ${modeLabel(c.latest.mode)}" + (c.latest.value("bpm")?.let { " · $it bpm" } ?: ""), style = MaterialTheme.typography.bodySmall)
                Text("เป็นผลการเล่น ไม่ใช่หลักฐานว่าจำได้ถาวร · ไม่นำรอบใช้ตัวช่วย หยุดกลางคัน หรือเงื่อนไขต่างกันมาเทียบ", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            } ?: Text("ยังไม่มีสองรอบที่เงื่อนไขเดียวกันและไม่ใช้ตัวช่วยให้เทียบ", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            rows.take(limit).forEach { attempt ->
                val r = attempt.row
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(Instant.ofEpochMilli(r.startedAtMillis).atZone(zone).format(date), style = MaterialTheme.typography.labelMedium)
                Text(lesson.drills.getOrNull(attempt.drillIndex ?: -1)?.title ?: r.exerciseId, style = MaterialTheme.typography.titleSmall)
                Text(listOf(handLabel(r.hand), modeLabel(attempt.mode), attempt.value("bpm")?.let { "$it bpm" }).filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text(listOfNotNull(fraction(r.noteAccuracy)?.let { "โน้ตถูก $it" }, fraction(r.timingWithinTol)?.let { "ตรงจังหวะ $it" },
                    r.stumbles?.let { "สะดุด $it ครั้ง" }, attempt.value("count")?.let { "เปลี่ยนคอร์ด $it ครั้ง" }).joinToString(" · ").ifEmpty { "ไม่มีค่าความแม่นในรอบนี้" })
                Text(listOf(when (attempt.complete) { true -> "จบรอบ"; false -> "หยุดกลางคัน · ไม่นำมาเทียบ"; null -> "ประวัติเดิม · ไม่ทราบสถานะจบรอบ" },
                    if (attempt.assisted) "ใช้ตัวช่วย ${r.helperUses} ครั้ง" else "ไม่ใช้ตัวช่วย").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            }
            if (rows.size > limit) TextButton(onClick = { limit += 10 }, modifier = Modifier.testTag("lesson-history-more")) { Text("ดูย้อนหลังเพิ่ม") }
        }
    }
}
