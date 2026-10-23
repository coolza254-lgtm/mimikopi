package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
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
    1 to tr("ขั้น 1 · คีย์บอร์ด จังหวะ และการอ่านเบื้องต้น", "Keyboard, rhythm and first reading", "鍵盤、リズム、読譜の基礎"),
    2 to tr("ขั้น 2 · อ่านโน้ตและมือขวา", "Note reading and the right hand", "読譜と右手"),
    3 to tr("ขั้น 3 · มือซ้ายและสองมือ", "Left hand and both hands", "左手と両手"),
    4 to tr("ขั้น 4 · สองมือ สเกล และเพลงพื้นฐาน", "Both hands, scales and first pieces", "両手、スケール、はじめての曲"),
)

@Composable
fun LessonsScreen() {
    val app = LocalApp.current
    val learning = app.services.learning
    val records by learning.records.collectAsState()
    val current = remember(records) { ProgressRules.nextToLearn(learning.lessons, records) }
    Page {
        LargeTitle(tr("บทเรียน", "Lessons", "レッスン"), tr("ทีละขั้น ผ่านแต่ละบทเมื่อทำได้ตามเกณฑ์ในวันที่ต่างกัน ไม่ล็อกเวลา", "Step by step: pass each lesson by meeting its criteria on different days, no time limit", "1段階ずつ：別々の日に基準を満たすと各レッスンに合格、時間制限なし"))
        current?.let { l ->
            PrimaryButton(tr("ฝึกต่อ: ${l.id} ${l.title}", "Continue: ${l.title}", "続き：${l.title}"), onClick = { app.nav.open(Screen.Lesson(l.id)) })
        }
        for ((stage, lessons) in learning.lessons.groupBy { it.stage }) {
            GroupHeader(STAGES[stage] ?: tr("ขั้น $stage", "Stage", "段階"))
            Group {
                lessons.forEachIndexed { i, l ->
                    if (i > 0) GroupDivider(52.dp)
                    val status = ProgressRules.status(l, records)
                    val days = records[l.id]?.passDays?.size ?: 0
                    GroupRow(
                        title = "${l.id}  ${l.title}",
                        subtitle = when (status) {
                            LessonStatus.PASSED -> tr("ผ่านแล้ว", "Passed", "合格")
                            LessonStatus.LOCKED -> tr("ต้องผ่าน ${l.prerequisites.joinToString()} ก่อน", "Pass ${l.prerequisites.joinToString()} first", "先に${l.prerequisites.joinToString()}に合格")
                            else -> if (days > 0) tr("ผ่านเกณฑ์ $days/${l.pass.days} วัน", "Criteria met $days/${l.pass.days} days", "基準達成 $days/${l.pass.days}日") else l.goal
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
        Page { LargeTitle(tr("ไม่พบบทนี้", "Lesson not found", "レッスンが見つかりません"), onBack = app.nav::back) }
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
                LessonStatus.PASSED -> tr("ผ่านบทนี้แล้ว", "Lesson passed", "レッスン合格")
                LessonStatus.LOCKED -> tr("ยังล็อก: ผ่าน ${lesson.prerequisites.joinToString()} ก่อน", "Locked: pass ${lesson.prerequisites.joinToString()} first", "ロック中：先に${lesson.prerequisites.joinToString()}に合格")
                else -> tr("ผ่านเกณฑ์ $days/${lesson.pass.days} วัน", "Criteria met $days/${lesson.pass.days} days", "基準達成 $days/${lesson.pass.days}日") + ((record?.helperDays?.size ?: 0).takeIf { it > 0 }?.let { tr(" · ใช้ตัวช่วย $it วัน (ไม่นับ)", " · with help on $it days (not counted)", " · 補助ありの日${it}日（数えません）") } ?: "")
            }, fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }

        PageCard {
            var more by remember { mutableStateOf(false) }
            lesson.explain.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            val extra = lesson.more.filter { it !in lesson.explain }.map { "• $it" } + lesson.tips.map { tr("เคล็ดลับ: $it", "Tip: $it", "ヒント：$it") } + lesson.caveats.map { "ⓘ $it" }
            if (extra.isNotEmpty()) {
                if (more) extra.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted) }
                TextButton(onClick = { more = !more }) { Text(if (more) tr("ย่อ", "Less", "閉じる") else tr("อ่านละเอียด", "Read more", "詳しく読む")) }
            }
        }

        GroupHeader(tr("แบบฝึก", "Drills", "練習"))
        Group {
            lesson.drills.forEachIndexed { i, d ->
                if (i > 0) GroupDivider()
                val counts = i in lesson.pass.requiredDrills
                GroupRow(
                    title = d.title,
                    subtitle = drillLine(d.type, d.mode, d.hand, d.bpm),
                    onClick = if (status != LessonStatus.LOCKED) ({ app.nav.open(openDrill(lesson, i)) }) else null,
                    leading = if (counts) ({ Pill(tr("นับผ่าน", "Counts", "合格に数える")) }) else null,
                )
            }
        }
        Text(tr("เกณฑ์ผ่าน: ${lesson.passText}", "To pass: ${lesson.passText}", "合格基準：${lesson.passText}"), fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(horizontal = 4.dp))

        LessonHistory(lesson, history)

        if (lesson.tryIt.isNotEmpty()) PageCard(title = tr("ลองกับโปรแกรม (ไม่นับผล)", "Try with the app (not counted)", "アプリで試す（数えません）")) {
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
                            SecondaryButton(tr("ฟังเสียง", "Listen", "音を聞く"), onClick = { scope.launch { s.piano.noteOn(pitch.midi, 85); delay(900); s.piano.noteOff(pitch.midi) } })
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
    ExerciseType.E_KEYFIND -> tr("หาคีย์ตามชื่อ", "Find keys by name", "名前で鍵盤を探す")
    ExerciseType.E_NOTEID -> tr("อ่านโน้ตทีละตัว", "Read notes one by one", "1音ずつ読む")
    ExerciseType.E_PULSE -> tr("เคาะตามคลิกให้สม่ำเสมอ", "Tap evenly with the click", "クリックに合わせて均等にタップ")
    ExerciseType.E_INTERVAL_READ -> tr("ดูระยะห่างแล้วเล่น", "See the distance, then play", "音の距離を見て弾く")
    ExerciseType.E_CHORD_BUILD -> tr("กดคอร์ดตามชื่อ", "Press chords by name", "名前でコードを押す")
    ExerciseType.E_CHORD_CHANGE -> tr("เปลี่ยนคอร์ดตามเวลา", "Change chords in time", "時間内にコードを替える")
    else -> listOf(
        when (hand) { "LEFT" -> tr("มือซ้าย", "Left hand", "左手"); "BOTH" -> tr("สองมือ", "Both hands", "両手"); else -> tr("มือขวา", "Right hand", "右手") },
        if (mode == "WAIT") tr("โหมดรอ (ไม่วัดจังหวะ)", "Wait mode (timing not measured)", "待ちモード（リズムは測りません）") else tr("ตามจังหวะ $bpm bpm", "In time $bpm bpm", "リズム $bpm bpm"),
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
    PageCard(title = tr("ประวัติและพัฒนาการบทนี้", "This lesson's history and progress", "このレッスンの履歴と上達")) {
        Text(tr("บันทึกผลอัตโนมัติเมื่อจบรอบ · เก็บในเครื่องนี้ · นำเข้าประวัติจากมือถือได้ที่ตั้งค่า → ข้อมูล", "Saved automatically at the end of each round · kept on this computer · import phone history in Settings → Data", "回の終わりに自動保存 · このPCに保存 · スマホの履歴は設定 → データでインポート"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        if (all.isEmpty()) {
            Text(tr("ยังไม่มีผลการเล่นบทนี้ เริ่มฝึกแล้วกลับมาดูผลได้ที่นี่", "No results for this lesson yet. Start practising and come back to see them here", "このレッスンの結果はまだありません。練習を始めたらここで確認できます"))
            return@PageCard
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected == -1, { selected = -1 }, label = { Text(tr("ทั้งหมด", "All", "すべて")) })
            lesson.drills.forEachIndexed { i, d -> FilterChip(selected == i, { selected = i }, label = { Text(d.title) }) }
        }
        val days = rows.map { Instant.ofEpochMilli(it.row.startedAt).atZone(zone).toLocalDate() }.distinct().size
        Text(tr("${rows.size} รอบ · $days วันที่มีผลบันทึก", "${rows.size} rounds · $days days with results", "${rows.size}回 · 記録のある日${days}日"), fontWeight = FontWeight.SemiBold)
        comparison?.let { c ->
            val notes = listOfNotNull(AttemptHistory.fraction(c.first.row.noteAccuracy), AttemptHistory.fraction(c.latest.row.noteAccuracy))
            val timing = listOfNotNull(AttemptHistory.fraction(c.first.row.timingWithinTol), AttemptHistory.fraction(c.latest.row.timingWithinTol))
            Text(tr("เทียบรอบแรกกับล่าสุดที่เงื่อนไขเดียวกัน", "First vs latest round with the same settings", "同じ条件での最初と最新の比較"), fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(
                if (notes.size == 2) tr("โน้ต ${notes[0]} → ${notes[1]}", "Notes ${notes[0]} → ${notes[1]}", "音 ${notes[0]} → ${notes[1]}") else null,
                if (timing.size == 2) tr("จังหวะ ${timing[0]} → ${timing[1]}", "Timing ${timing[0]} → ${timing[1]}", "リズム ${timing[0]} → ${timing[1]}") else null,
                if (c.first.value("count") != null && c.latest.value("count") != null) "เปลี่ยนคอร์ด ${c.first.value("count")} → ${c.latest.value("count")} ครั้ง" else null,
            ).joinToString(" · ").ifEmpty { tr("มี ${rows.count { it.conditions == c.latest.conditions && it.comparable }} รอบที่เทียบกันได้", "${rows.count { it.conditions == c.latest.conditions && it.comparable }} comparable rounds", "比較できる回 ${rows.count { it.conditions == c.latest.conditions && it.comparable }}回") }, fontSize = 18.sp)
            Text("${AttemptHistory.handLabel(c.latest.row.hand)} · ${AttemptHistory.modeLabel(c.latest.mode)}" + (c.latest.value("bpm")?.let { " · $it bpm" } ?: ""), fontSize = 13.sp)
            Text(tr("เป็นผลการเล่น ไม่ใช่หลักฐานว่าจำได้ถาวร · ไม่นำรอบใช้ตัวช่วย หยุดกลางคัน หรือเงื่อนไขต่างกันมาเทียบ", "Rounds with helpers, stopped midway or with different settings aren't compared", "補助あり、途中終了、条件の違う回は比較しません"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        } ?: Text(tr("ยังไม่มีสองรอบที่เงื่อนไขเดียวกันและไม่ใช้ตัวช่วยให้เทียบ", "No two rounds yet with the same settings and no helpers to compare", "同じ条件で補助なしの比較できる2回がまだありません"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        rows.take(limit).forEach { a ->
            val r = a.row
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Tokens.Palette.Hair)
            Text(Instant.ofEpochMilli(r.startedAt).atZone(zone).format(date), fontSize = 12.sp, color = Tokens.Palette.InkMuted)
            Text(lesson.drills.getOrNull(a.drillIndex ?: -1)?.title ?: r.exerciseId, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(AttemptHistory.handLabel(r.hand), AttemptHistory.modeLabel(a.mode), a.value("bpm")?.let { "$it bpm" }, sourceLabel(a.value("inputSources"))).joinToString(" · "), fontSize = 13.sp)
            Text(listOfNotNull(AttemptHistory.fraction(r.noteAccuracy)?.let { tr("โน้ตถูก $it", "Notes $it", "音 $it") }, AttemptHistory.fraction(r.timingWithinTol)?.let { tr("ตรงจังหวะ $it", "On time $it", "リズム $it") },
                r.stumbles?.let { tr("สะดุด $it ครั้ง", "Stumbled $it times", "つまずき${it}回") }, a.value("count")?.let { tr("เปลี่ยนคอร์ด $it ครั้ง", "$it chord changes", "コードチェンジ${it}回") }).joinToString(" · ").ifEmpty { tr("ไม่มีค่าความแม่นในรอบนี้", "No accuracy for this round", "この回の正確さはありません") })
            Text(listOf(when (a.complete) { true -> tr("จบรอบ", "Finished", "完了"); false -> tr("หยุดกลางคัน · ไม่นำมาเทียบ", "Stopped midway · not compared", "途中終了 · 比較しません"); null -> tr("ประวัติเดิม · ไม่ทราบสถานะจบรอบ", "Older history · unknown whether finished", "以前の履歴 · 完了したか不明") },
                if (a.assisted) tr("ใช้ตัวช่วย ${r.helperUses} ครั้ง", "Helpers used ${r.helperUses} times", "補助${r.helperUses}回") else tr("ไม่ใช้ตัวช่วย", "No helpers", "補助なし")).joinToString(" · "), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }
        if (rows.size > limit) TextButton(onClick = { limit += 10 }) { Text(tr("ดูย้อนหลังเพิ่ม", "See more history", "履歴をもっと見る")) }
    }
}

private fun sourceLabel(sources: String?): String? = sources?.takeIf { it.isNotBlank() }?.split('+')?.joinToString("+") {
    when (it) {
        "USB_MIDI", "BLE_MIDI" -> "MIDI"
        "COMPUTER_KEYS" -> tr("คีย์บอร์ดคอม", "Computer keyboard", "PCキーボード")
        "TOUCH" -> tr("คลิก/แตะจอ", "Click / tap the screen", "クリック／画面タップ")
        else -> it
    }
}
