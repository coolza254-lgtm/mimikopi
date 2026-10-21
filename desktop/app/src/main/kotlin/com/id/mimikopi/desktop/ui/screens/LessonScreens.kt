package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.core.progress.AttemptHistory
import com.id.mimikopi.core.progress.HistoryAttempt
import com.id.mimikopi.desktop.session.DrillSource
import com.id.mimikopi.desktop.session.ReaderArgs
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.components.Group
import com.id.mimikopi.desktop.ui.components.GroupDivider
import com.id.mimikopi.desktop.ui.components.GroupHeader
import com.id.mimikopi.desktop.ui.components.GroupRow
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PageCard
import com.id.mimikopi.desktop.ui.components.PianoKeys
import com.id.mimikopi.desktop.ui.components.Pill
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.StaffView
import com.id.mimikopi.desktop.ui.components.StepMark
import com.id.mimikopi.desktop.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Opens a lesson drill on the page that runs it: score drills in the player, the rest in the drill runner. */
fun openDrill(lesson: Lesson, index: Int): Screen =
    if (lesson.drills[index].type.isScore) Screen.Reader(ReaderArgs(lessonId = lesson.id, drill = index))
    else Screen.Drill(DrillSource.LessonDrill(lesson.id, index))

private val STAGES = mapOf(
    1 to "ขั้น 1 · คีย์บอร์ด จังหวะ และการอ่านเบื้องต้น",
    2 to "ขั้น 2 · อ่านโน้ตและมือขวา",
    3 to "ขั้น 3 · มือซ้ายและสองมือ",
    4 to "ขั้น 4 · สองมือ สเกล และเพลงพื้นฐาน",
)

@Composable
fun LessonsScreen() {
    val app = LocalApp.current
    val learning = app.services.learning
    val records by learning.records.collectAsState()
    val current = remember(records) { ProgressRules.nextToLearn(learning.lessons, records) }
    Page {
        LargeTitle("บทเรียน", "ทีละขั้น ผ่านแต่ละบทเมื่อทำได้ตามเกณฑ์ในวันที่ต่างกัน ไม่ล็อกเวลา")
        current?.let { l ->
            PrimaryButton("ฝึกต่อ: ${l.id} ${l.title}", onClick = { app.nav.open(Screen.Lesson(l.id)) })
        }
        for ((stage, lessons) in learning.lessons.groupBy { it.stage }) {
            GroupHeader(STAGES[stage] ?: "ขั้น $stage")
            Group {
                lessons.forEachIndexed { i, l ->
                    if (i > 0) GroupDivider(52.dp)
                    val status = ProgressRules.status(l, records)
                    val days = records[l.id]?.passDays?.size ?: 0
                    GroupRow(
                        title = "${l.id}  ${l.title}",
                        subtitle = when (status) {
                            LessonStatus.PASSED -> "ผ่านแล้ว"
                            LessonStatus.LOCKED -> "ต้องผ่าน ${l.prerequisites.joinToString()} ก่อน"
                            else -> if (days > 0) "ผ่านเกณฑ์ $days/${l.pass.days} วัน" else l.goal
                        },
                        onClick = { app.nav.open(Screen.Lesson(l.id)) },
                        leading = { StepMark(status == LessonStatus.PASSED, l.id == current?.id) },
                        titleColor = if (status == LessonStatus.LOCKED) Tokens.Palette.InkMuted else Tokens.Palette.Ink,
                    )
                }
            }
        }
    }
}

/** A lesson laid out like learning-system.md section 10: explain → try → drills → pass rule → history. */
@Composable
fun LessonScreen(id: String) {
    val app = LocalApp.current
    val s = app.services
    val lesson = remember(id) { s.learning.lesson(id) }
    val records by s.learning.records.collectAsState()
    val scope = rememberCoroutineScope()
    if (lesson == null) {
        Page { LargeTitle("ไม่พบบทนี้", onBack = app.nav::back) }
        return
    }
    val status = ProgressRules.status(lesson, records)
    val record = records[lesson.id]
    // History is read when the page opens (it is shown again after every run, because the run is another page).
    val history = remember(lesson.id) { s.learning.lessonAttempts(lesson.id) }
    var holding by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        holding = s.piano.acquire()
        onDispose { if (holding) s.piano.release() }
    }
    var marked by remember { mutableStateOf(emptySet<Int>()) }
    val pressed by s.hub.pressed.collectAsState()

    Page {
        LargeTitle("${lesson.id} ${lesson.title}", lesson.goal, onBack = app.nav::back)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val days = record?.passDays?.size ?: 0
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(lesson.pass.days) { d ->
                    Box(Modifier.size(28.dp, 8.dp).background(if (d < days) Tokens.Palette.Ink else Tokens.Palette.Line, RoundedCornerShape(4.dp)))
                }
            }
            Text(when (status) {
                LessonStatus.PASSED -> "ผ่านบทนี้แล้ว"
                LessonStatus.LOCKED -> "ยังล็อก: ผ่าน ${lesson.prerequisites.joinToString()} ก่อน"
                else -> "ผ่านเกณฑ์ $days/${lesson.pass.days} วัน" + ((record?.helperDays?.size ?: 0).takeIf { it > 0 }?.let { " · ใช้ตัวช่วย $it วัน (ไม่นับ)" } ?: "")
            }, fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }

        PageCard {
            var more by remember { mutableStateOf(false) }
            lesson.explain.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            val extra = lesson.more.filter { it !in lesson.explain }.map { "• $it" } + lesson.tips.map { "เคล็ดลับ: $it" } + lesson.caveats.map { "ⓘ $it" }
            if (extra.isNotEmpty()) {
                if (more) extra.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted) }
                TextButton(onClick = { more = !more }) { Text(if (more) "ย่อ" else "อ่านละเอียด") }
            }
        }

        GroupHeader("แบบฝึก")
        Group {
            lesson.drills.forEachIndexed { i, d ->
                if (i > 0) GroupDivider()
                val counts = i in lesson.pass.requiredDrills
                GroupRow(
                    title = d.title,
                    subtitle = drillLine(d.type, d.mode, d.hand, d.bpm),
                    onClick = if (status != LessonStatus.LOCKED) ({ app.nav.open(openDrill(lesson, i)) }) else null,
                    leading = if (counts) ({ Pill("นับผ่าน") }) else null,
                )
            }
        }
        Text("เกณฑ์ผ่าน: ${lesson.passText}", fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(horizontal = 4.dp))

        LessonHistory(lesson, history)

        if (lesson.tryIt.isNotEmpty()) PageCard(title = "ลองกับโปรแกรม (ไม่นับผล)") {
            lesson.tryIt.forEachIndexed { i, step ->
                Text("${i + 1}. ${step.text}", style = MaterialTheme.typography.bodyLarge)
                val (kind, arg) = step.action?.split(":", limit = 2)?.let { it[0] to it.getOrNull(1) } ?: (null to null)
                when (kind) {
                    "highlight" -> SecondaryButton("แสดงคีย์ ${arg ?: ""} ทุกตัว", onClick = {
                        val letter = arg?.let { runCatching { Letter.valueOf(it) }.getOrNull() } ?: return@SecondaryButton
                        scope.launch {
                            marked = emptySet()
                            for (k in (48..83).filter { it % 12 == letter.semitonesFromC }) {
                                marked = marked + k
                                s.piano.noteOn(k, 80)
                                delay(500)
                                s.piano.noteOff(k)
                            }
                        }
                    })
                    "show" -> {
                        val parts = arg?.split(":")
                        val pitch = parts?.getOrNull(0)?.let { runCatching { SpelledPitch.parse(it) }.getOrNull() }
                        val clef = parts?.getOrNull(1)?.let { runCatching { Clef.valueOf(it.uppercase()) }.getOrNull() }
                        if (pitch != null && clef != null) {
                            StaffView(clef, pitch, Modifier.widthIn(max = 360.dp).fillMaxWidth().height(130.dp))
                            SecondaryButton("ฟังเสียง", onClick = { scope.launch { s.piano.noteOn(pitch.midi, 85); delay(900); s.piano.noteOff(pitch.midi) } })
                        }
                    }
                }
            }
            val lowC by s.keysLowC.collectAsState()
            PianoKeys(48..83, pressed, { n, down -> s.hub.dispatch(keyEvent(n, down)) }, marked = marked, labels = NoteNameStyle.BOTH, computerKeysLowC = lowC, height = 120.dp)
        }
    }
}

fun keyEvent(note: Int, down: Boolean) = if (down) com.id.mimikopi.core.input.InputEvent.NoteOn(note, 80, System.nanoTime(), com.id.mimikopi.core.input.InputSource.TOUCH)
else com.id.mimikopi.core.input.InputEvent.NoteOff(note, System.nanoTime(), com.id.mimikopi.core.input.InputSource.TOUCH)

private fun drillLine(type: ExerciseType, mode: String, hand: String, bpm: Int): String = when (type) {
    ExerciseType.E_KEYFIND -> "หาคีย์ตามชื่อ"
    ExerciseType.E_NOTEID -> "อ่านโน้ตทีละตัว"
    ExerciseType.E_PULSE -> "เคาะตามคลิกให้สม่ำเสมอ"
    ExerciseType.E_INTERVAL_READ -> "ดูระยะห่างแล้วเล่น"
    ExerciseType.E_CHORD_BUILD -> "กดคอร์ดตามชื่อ"
    ExerciseType.E_CHORD_CHANGE -> "เปลี่ยนคอร์ดตามเวลา"
    else -> listOf(
        when (hand) { "LEFT" -> "มือซ้าย"; "BOTH" -> "สองมือ"; else -> "มือขวา" },
        if (mode == "WAIT") "โหมดรอ (ไม่วัดจังหวะ)" else "ตามจังหวะ $bpm bpm",
    ).joinToString(" · ")
}

/** Per-lesson history and progress (LESSON-HISTORY.md), from core's shared rules. */
@Composable
fun LessonHistory(lesson: Lesson, history: List<AttemptRow>) {
    var selected by remember(lesson.id) { mutableIntStateOf(-1) }
    var limit by remember(lesson.id, selected) { mutableIntStateOf(10) }
    val all = remember(history) { history.map(::HistoryAttempt) }
    val rows = all.filter { selected == -1 || it.drillIndex == selected }
    val comparison = remember(rows) { AttemptHistory.comparison(rows) }
    val zone = ZoneId.systemDefault()
    val date = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }
    PageCard(title = "ประวัติและพัฒนาการบทนี้") {
        Text("บันทึกผลอัตโนมัติเมื่อจบรอบ · เก็บในเครื่องนี้ · นำเข้าประวัติจากมือถือได้ที่ตั้งค่า → ข้อมูล", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        if (all.isEmpty()) {
            Text("ยังไม่มีผลการเล่นบทนี้ เริ่มฝึกแล้วกลับมาดูผลได้ที่นี่")
            return@PageCard
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected == -1, { selected = -1 }, label = { Text("ทั้งหมด") })
            lesson.drills.forEachIndexed { i, d -> FilterChip(selected == i, { selected = i }, label = { Text(d.title) }) }
        }
        val days = rows.map { Instant.ofEpochMilli(it.row.startedAt).atZone(zone).toLocalDate() }.distinct().size
        Text("${rows.size} รอบ · $days วันที่มีผลบันทึก", fontWeight = FontWeight.SemiBold)
        comparison?.let { c ->
            val notes = listOfNotNull(AttemptHistory.fraction(c.first.row.noteAccuracy), AttemptHistory.fraction(c.latest.row.noteAccuracy))
            val timing = listOfNotNull(AttemptHistory.fraction(c.first.row.timingWithinTol), AttemptHistory.fraction(c.latest.row.timingWithinTol))
            Text("เทียบรอบแรกกับล่าสุดที่เงื่อนไขเดียวกัน", fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(
                if (notes.size == 2) "โน้ต ${notes[0]} → ${notes[1]}" else null,
                if (timing.size == 2) "จังหวะ ${timing[0]} → ${timing[1]}" else null,
                if (c.first.value("count") != null && c.latest.value("count") != null) "เปลี่ยนคอร์ด ${c.first.value("count")} → ${c.latest.value("count")} ครั้ง" else null,
            ).joinToString(" · ").ifEmpty { "มี ${rows.count { it.conditions == c.latest.conditions && it.comparable }} รอบที่เทียบกันได้" }, fontSize = 18.sp)
            Text("${AttemptHistory.handLabel(c.latest.row.hand)} · ${AttemptHistory.modeLabel(c.latest.mode)}" + (c.latest.value("bpm")?.let { " · $it bpm" } ?: ""), fontSize = 13.sp)
            Text("เป็นผลการเล่น ไม่ใช่หลักฐานว่าจำได้ถาวร · ไม่นำรอบใช้ตัวช่วย หยุดกลางคัน หรือเงื่อนไขต่างกันมาเทียบ", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        } ?: Text("ยังไม่มีสองรอบที่เงื่อนไขเดียวกันและไม่ใช้ตัวช่วยให้เทียบ", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        rows.take(limit).forEach { a ->
            val r = a.row
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Tokens.Palette.Hair)
            Text(Instant.ofEpochMilli(r.startedAt).atZone(zone).format(date), fontSize = 12.sp, color = Tokens.Palette.InkMuted)
            Text(lesson.drills.getOrNull(a.drillIndex ?: -1)?.title ?: r.exerciseId, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(AttemptHistory.handLabel(r.hand), AttemptHistory.modeLabel(a.mode), a.value("bpm")?.let { "$it bpm" }, sourceLabel(a.value("inputSources"))).joinToString(" · "), fontSize = 13.sp)
            Text(listOfNotNull(AttemptHistory.fraction(r.noteAccuracy)?.let { "โน้ตถูก $it" }, AttemptHistory.fraction(r.timingWithinTol)?.let { "ตรงจังหวะ $it" },
                r.stumbles?.let { "สะดุด $it ครั้ง" }, a.value("count")?.let { "เปลี่ยนคอร์ด $it ครั้ง" }).joinToString(" · ").ifEmpty { "ไม่มีค่าความแม่นในรอบนี้" })
            Text(listOf(when (a.complete) { true -> "จบรอบ"; false -> "หยุดกลางคัน · ไม่นำมาเทียบ"; null -> "ประวัติเดิม · ไม่ทราบสถานะจบรอบ" },
                if (a.assisted) "ใช้ตัวช่วย ${r.helperUses} ครั้ง" else "ไม่ใช้ตัวช่วย").joinToString(" · "), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }
        if (rows.size > limit) TextButton(onClick = { limit += 10 }) { Text("ดูย้อนหลังเพิ่ม") }
    }
}

private fun sourceLabel(sources: String?): String? = sources?.takeIf { it.isNotBlank() }?.split('+')?.joinToString("+") {
    when (it) {
        "USB_MIDI", "BLE_MIDI" -> "MIDI"
        "COMPUTER_KEYS" -> "คีย์บอร์ดคอม"
        "TOUCH" -> "คลิก/แตะจอ"
        else -> it
    }
}
