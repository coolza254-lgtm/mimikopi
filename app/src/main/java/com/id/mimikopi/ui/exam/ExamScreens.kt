package com.id.mimikopi.ui.exam

import com.id.mimikopi.core.i18n.tr
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.exam.Band
import com.id.mimikopi.core.exam.ExamLog
import com.id.mimikopi.core.exam.ExamPart
import com.id.mimikopi.core.exam.ExamSitting
import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.exam.Grade
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.theory.Accompaniment
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.ui.components.BarChart
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupFooter
import com.id.mimikopi.ui.components.GroupHeader
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.LargeTitle
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.components.StepMark
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.progress.shortDate
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.Open
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Grade exams (learning-system.md 2.7): the grades with the best result of each, the requirements of a grade with
 * practice links for every part, the exam in progress (each part once, finished on the day), and the result with
 * marks per part.
 */
class ExamViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    val exams: StateFlow<List<ExamSitting>> = services.progress.exams
    val grade: Grade? = args.get<String>("grade")?.toIntOrNull()?.let { Exams.grade(it) }
    val sittingId: String? = args.get<String>("id")

    private val _chosen = MutableStateFlow<List<String>>(grade?.pieces?.takeIf { it.size == 2 } ?: emptyList())
    /** The two library pieces chosen for the next exam. */
    val chosen: StateFlow<List<String>> = _chosen.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** The exams and the piece library are read (piece titles need the library). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            services.progress.load()
            services.pieces.load()
            _ready.value = true
        }
    }

    fun pieceTitle(id: String): String = services.pieces.pieceOrExercise(id)?.title ?: id
    fun pieceTempo(id: String): Int? = services.pieces.pieceOrExercise(id)?.tempo

    fun toggle(id: String) {
        val now = _chosen.value
        _chosen.value = when {
            id in now -> now - id
            now.size >= 2 -> now.drop(1) + id
            else -> now + id
        }
    }

    fun start() {
        val g = grade ?: return
        val pieces = _chosen.value.takeIf { it.size == 2 } ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch { services.progress.startExam(ExamLog.start(g.n, pieces, now, LocalDate.now().toString(), now)) }
    }

    fun abandon(id: String) {
        viewModelScope.launch { services.progress.abandonExam(id) }
    }
}

private fun today() = LocalDate.now().toString()
private fun day(s: ExamSitting) = runCatching { shortDate(LocalDate.parse(s.day)) }.getOrDefault(s.day)

private val HONEST: String get() = tr("โครงสร้างและเกณฑ์คะแนนเลียนแบบการสอบปฏิบัติ ABRSM (เพลง 3 เพลง สเกล อ่านโน้ตใหม่ และการฟัง รวม 150 คะแนน ", "The structure and marks copy the ABRSM practical exam (3 pieces, scales, sight-reading and aural, 150 marks in total; ", "構成と採点はABRSM実技試験にならっています（3曲、スケール、初見、聴音、合計150点、") +
    tr("ผ่าน 100 ดี 120 ดีเยี่ยม 130) แต่เพลง สเกล ข้อสอบ และการให้คะแนนเป็นของแอพเอง ไม่ใช่การสอบหรือเกรดทางการ", "pass 100, merit 120, distinction 130), but the pieces, scales, tests and marking are the app's own, not an official exam or grade", "合格100、良120、優130）。ただし曲・スケール・問題・採点はアプリ独自のもので、公式の試験やグレードではありません")

@Composable
private fun Page(title: String, subtitle: String?, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) {
            LargeTitle(title, subtitle, onBack = onBack)
            Column(Modifier.padding(horizontal = PageMargin)) {
                content()
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ---------------- home ----------------

@Composable
fun ExamHomeScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ExamViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val log by vm.exams.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val active = ExamLog.active(log, today())
    val finished = ExamLog.finished(log)

    Page(tr("สอบเกรด", "Grade exams", "グレード試験"), tr("วัดว่าเก่งขึ้นจริงไหม แบบการสอบเปียโน", "See if you really improved, like a piano exam", "ピアノ試験のように本当に上達したか確かめる"), onBack) {
        if (!ready) return@Page
        if (active != null) {
            val g = Exams.grade(active.grade)
            GroupHeader(tr("กำลังสอบ", "In progress", "受験中"))
            Group {
                GroupRow("${g?.name ?: ""} · ได้ ${active.total} คะแนนแล้ว", subtitle = tr("สอบแล้ว ${active.parts.size} จาก ${ExamPart.entries.size} ส่วน · ต้องจบภายในวันนี้", "${active.parts.size} of ${ExamPart.entries.size} parts done · must finish today", "${ExamPart.entries.size}部中${active.parts.size}部済み · 今日中に終えること"),
                    onClick = { onOpen(Open.ExamGrade(active.grade)) })
            }
        }
        GroupHeader(tr("เกรด", "Grade", "グレード"))
        Group {
            Exams.GRADES.forEachIndexed { i, g ->
                if (i > 0) GroupDivider()
                val best = ExamLog.best(log, g.n)
                GroupRow(g.name, subtitle = g.about, value = best?.let { "${it.total} · ${Exams.band(it.total).title.substringBefore(" (")}" } ?: tr("${g.minutes} นาที", "${g.minutes} min", "${g.minutes}分"),
                    onClick = { onOpen(Open.ExamGrade(g.n)) })
            }
        }
        GroupFooter(HONEST)

        if (finished.isNotEmpty()) {
            GroupHeader(tr("ผลสอบที่ผ่านมา", "Past results", "過去の結果"))
            Group {
                val recent = finished.take(12).reversed()
                Column(Modifier.padding(16.dp)) {
                    BarChart(recent.map { it.total.toFloat() / Exams.MAX }, listOfNotNull(recent.firstOrNull()?.let(::day), tr("ล่าสุด", "Latest", "最新")))
                    Text(tr("ผ่านที่ ${Exams.PASS} · ดีที่ ${Exams.MERIT} · ดีเยี่ยมที่ ${Exams.DISTINCTION} จาก ${Exams.MAX}", "Pass at ${Exams.PASS} · merit at ${Exams.MERIT} · distinction at ${Exams.DISTINCTION} out of ${Exams.MAX}", "${Exams.MAX}点中 合格${Exams.PASS} · 良${Exams.MERIT} · 優${Exams.DISTINCTION}"), fontSize = 13.sp,
                        color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
                }
                finished.take(20).forEach { s ->
                    GroupDivider()
                    GroupRow("${Exams.grade(s.grade)?.name ?: ""} · ${s.total}/${Exams.MAX}", subtitle = Exams.band(s.total).title, value = day(s),
                        onClick = { onOpen(Open.ExamResult(s.id)) })
                }
            }
        }
    }
}

// ---------------- one grade ----------------

@Composable
fun ExamGradeScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ExamViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val log by vm.exams.collectAsStateWithLifecycle()
    val chosen by vm.chosen.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val grade = vm.grade ?: return
    val active = ExamLog.active(log, today())
    val sitting = active?.takeIf { it.grade == grade.n }
    val todayResult = ExamLog.finished(log).firstOrNull { it.grade == grade.n && it.day == today() }

    Page(grade.name, grade.about, onBack) {
        if (!ready) return@Page
        if (sitting != null) {
            InProgress(sitting, grade, vm, onOpen)
            return@Page
        }
        if (todayResult != null) {
            GroupHeader(tr("ผลสอบวันนี้", "Today's result", "今日の結果"))
            Group {
                ResultCard(todayResult)
                GroupDivider()
                GroupRow(tr("ดูคะแนนแต่ละส่วน", "See marks for each part", "各部の点数を見る"), onClick = { onOpen(Open.ExamResult(todayResult.id)) })
            }
        }
        Requirements(grade, chosen, vm, onOpen)
        if (active != null) GroupFooter("กำลังสอบ ${Exams.grade(active.grade)?.name ?: ""} อยู่ เริ่มเกรดนี้จะยกเลิกการสอบนั้น")
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (chosen.size == 2) tr("เริ่มสอบ ${grade.name}", "Start ${grade.name}", "${grade.name}を受ける") else tr("เลือกเพลงให้ครบ 2 เพลงก่อน", "Choose 2 pieces first", "先に2曲選んでください"), onClick = vm::start, enabled = chosen.size == 2)
        GroupFooter(tr("ใช้เวลาราว ${grade.minutes} นาที ต้องสอบให้ครบในวันเดียว แต่ละส่วนสอบได้ครั้งเดียวเหมือนสอบจริง", "Takes about ${grade.minutes} min and must be finished in one day. Each part can be taken once, like a real exam", "約${grade.minutes}分。1日で全部受ける必要があり、各部は本番同様1回だけです"))
    }
}

@Composable
private fun Requirements(grade: Grade, chosen: List<String>, vm: ExamViewModel, onOpen: (Open) -> Unit) {
    GroupHeader(if (grade.pieces.size == 2) tr("เพลง (สอบทั้งสองเพลง)", "Pieces (both are examined)", "曲（2曲とも受験）") else tr("เพลง · เลือก 2 เพลง", "Pieces · choose 2", "曲 · 2曲選ぶ"))
    Group {
        grade.pieces.forEachIndexed { i, id ->
            if (i > 0) GroupDivider()
            val selected = id in chosen
            GroupRow(
                vm.pieceTitle(id),
                subtitle = vm.pieceTempo(id)?.let { tr("สองมือ ♩ = $it ตามที่เขียน", "Both hands ♩ = $it as written", "両手 ♩ = $it 楽譜どおり") },
                leading = { Choice(selected) },
                trailing = { TextButton(onClick = { onOpen(Open.Piece(id)) }) { Text(tr("ซ้อม", "Rehearse", "練習"), color = Tokens.Palette.Ink) } },
                onClick = if (grade.pieces.size == 2) null else ({ vm.toggle(id) }),
            )
        }
    }
    GroupFooter(tr("เพลงจากคลังเพลงสาธารณะของแอพ ซ้อมในหน้าเพลงได้ตามปกติก่อนสอบ", "Pieces from the app's public library; rehearse them on the Songs page before the exam", "アプリの公開ライブラリの曲。試験前に曲ページで自由に練習できます"))

    val pop = Exams.pop(grade)
    GroupHeader(tr("เพลงป็อป", "Pop piece", "ポップ曲"))
    Group {
        GroupRow(pop.title, subtitle = "คีย์ ${Keys.name(grade.pop.key)} · ♩ = ${grade.pop.bpm} · มือซ้าย${Accompaniment.LEVEL_NAMES[grade.pop.pattern] ?: ""} · ${grade.pop.bars} ห้อง",
            value = tr("ซ้อม", "Rehearse", "練習"), onClick = { onOpen(Open.Piece(pop.id)) })
    }
    GroupFooter(tr("แอพแต่งเองบนทางเดินคอร์ดแบบเพลงไอดอล มีชื่อคอร์ดกำกับ เพลงเดิมทุกครั้งจึงซ้อมไว้ก่อนได้", "Written by the app over an idol-song progression with chord names; it is the same every time, so you can rehearse it", "アイドル曲風のコード進行でアプリが作曲、コード名付き。毎回同じ曲なので事前に練習できます"))

    GroupHeader(tr("สเกลและอาร์เพจจิโอ · สอบ ${grade.ask} จาก ${grade.scales.size}", "Scales and arpeggios · ${grade.ask} of ${grade.scales.size} asked", "スケールとアルペジオ · ${grade.scales.size}個中${grade.ask}個出題"))
    Group {
        grade.scales.forEachIndexed { i, s ->
            if (i > 0) GroupDivider()
            GroupRow(s.title(), onClick = { onOpen(Open.Piece(Exams.scalePractice(grade, s).id)) })
        }
    }
    GroupFooter("♩ = ${grade.scaleBpm} ${if (grade.quarters) tr("ตัวดำ", "Crotchets", "四分音符") else tr("เขบ็ตหนึ่งชั้น", "Quavers", "八分音符")} ขึ้นแล้วลง ใช้นิ้วตามที่เขียน (ไมเนอร์เป็นแบบฮาร์โมนิก) " +
        tr("ตอนสอบแอพสุ่มถาม ${grade.ask} ข้อต่อกันในรอบเดียว", "In the exam the app asks ${grade.ask} at random, one after another", "試験ではアプリが${grade.ask}個をランダムに続けて出題します"))

    GroupHeader(tr("อ่านโน้ตใหม่", "Sight-reading", "初見"))
    Group {
        val hands = when (Exams.sightHand(grade)) { "LEFT" -> tr("มือซ้าย", "Left hand", "左手"); "BOTH" -> tr("สองมือ", "Both hands", "両手"); else -> tr("มือขวา", "Right hand", "右手") }
        GroupRow(tr("บทใหม่ · $hands · ♩ = ${grade.sightBpm}", "New piece · $hands · ♩ = ${grade.sightBpm}", "新しい曲 · $hands · ♩ = ${grade.sightBpm}"), subtitle = tr("ดูโน้ต 30 วินาทีแล้วเริ่มเล่นทันที เล่นครั้งเดียว", "Look for 30 seconds, then play straight away, once", "30秒見てすぐに弾く、1回だけ"),
            value = tr("ฝึก", "Practise", "練習"), onClick = { onOpen(if (grade.sightLevel <= 5) Open.Sight(grade.sightLevel) else Open.Skill(SkillId.SIGHT)) })
    }

    GroupHeader(tr("ทดสอบการฟัง · ${grade.aural.count} ข้อ", "Aural test · ${grade.aural.count} questions", "聴音テスト · ${grade.aural.count}問"))
    Group {
        GroupRow(grade.aural.items.map { EarCourse.kindOf(it).title }.distinct().joinToString(" · "),
            subtitle = grade.aural.items.joinToString(" · ") { EarCourse.label(it) }, value = tr("ฝึก", "Practise", "練習"), onClick = { onOpen(Open.EarHome) })
    }

    GroupHeader(tr("คะแนน", "Marks", "点数"))
    Group {
        ExamPart.entries.forEachIndexed { i, p ->
            if (i > 0) GroupDivider()
            GroupRow(p.title, value = tr("${p.max} (ผ่าน ${p.pass})", "${p.max} (pass ${p.pass})", "${p.max}（合格${p.pass}）"))
        }
    }
    GroupFooter(tr("รวม ${Exams.MAX} · ผ่าน ${Exams.PASS} · ดี ${Exams.MERIT} · ดีเยี่ยม ${Exams.DISTINCTION} ตัดสินจากคะแนนรวมเหมือน ABRSM ", "Total ${Exams.MAX} · pass ${Exams.PASS} · merit ${Exams.MERIT} · distinction ${Exams.DISTINCTION}, judged on the total like ABRSM. ", "合計${Exams.MAX} · 合格${Exams.PASS} · 良${Exams.MERIT} · 優${Exams.DISTINCTION}。ABRSMと同じく合計点で判定。") +
        tr("คะแนนการเล่นมาจากโน้ตที่ถูก ความตรงจังหวะ และการหยุดกลางทาง\n\n$HONEST", "Playing marks come from right notes, steady timing and not stopping\n\n$HONEST", "演奏点は正しい音、リズムの正確さ、途中で止まらないことから\n\n$HONEST"))
}

@Composable
private fun Choice(selected: Boolean) {
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(Tokens.Palette.Ink) else Modifier.border(1.5.dp, Tokens.Palette.Line, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Text("✓", fontSize = 13.sp, color = Tokens.Palette.Paper, fontWeight = FontWeight.Bold)
    }
}

/** What each part is in this exam (the chosen piece, the pop piece, how many scales ...). */
private fun partLine(part: ExamPart, s: ExamSitting, grade: Grade, vm: ExamViewModel): String = when (part) {
    ExamPart.PIECE_1 -> vm.pieceTitle(s.pieces[0])
    ExamPart.PIECE_2 -> vm.pieceTitle(s.pieces[1])
    ExamPart.POP -> Exams.pop(grade).title
    ExamPart.SCALES -> tr("${grade.ask} ข้อต่อกัน ♩ = ${grade.scaleBpm}", "${grade.ask} in a row ♩ = ${grade.scaleBpm}", "${grade.ask}個続けて ♩ = ${grade.scaleBpm}")
    ExamPart.SIGHT -> tr("ดูโน้ต 30 วินาที", "Look at the music for 30 seconds", "楽譜を30秒見る")
    ExamPart.AURAL -> tr("${grade.aural.count} ข้อ ตอบครั้งเดียว", "${grade.aural.count} questions, answer once", "${grade.aural.count}問、回答は1回")
}

@Composable
private fun InProgress(s: ExamSitting, grade: Grade, vm: ExamViewModel, onOpen: (Open) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    GroupHeader(tr("กำลังสอบ", "In progress", "受験中"))
    Group {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.total}", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text(tr(" / ${Exams.MAX} คะแนน", " / ${Exams.MAX} marks", " / ${Exams.MAX}点"), fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(tr("สอบแล้ว ${s.parts.size} จาก ${ExamPart.entries.size} ส่วน · ผ่าน ${Exams.PASS} · ดี ${Exams.MERIT} · ดีเยี่ยม ${Exams.DISTINCTION}", "${s.parts.size} of ${ExamPart.entries.size} parts done · pass ${Exams.PASS} · merit ${Exams.MERIT} · distinction ${Exams.DISTINCTION}", "${ExamPart.entries.size}部中${s.parts.size}部済み · 合格${Exams.PASS} · 良${Exams.MERIT} · 優${Exams.DISTINCTION}"),
                fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
    GroupHeader(tr("ส่วนของการสอบ", "Exam parts", "試験の各部"))
    Group {
        val next = ExamPart.entries.firstOrNull { s.mark(it) == null }
        ExamPart.entries.forEachIndexed { i, p ->
            if (i > 0) GroupDivider()
            val m = s.mark(p)
            GroupRow(
                p.title, subtitle = partLine(p, s, grade, vm),
                value = m?.let { "${it.marks}/${p.max}" } ?: tr("สอบ", "Take", "受ける"),
                leading = { StepMark(done = m != null, current = p == next) },
                onClick = if (m == null) ({ onOpen(Open.ExamTake(s.id, p.name)) }) else null,
            )
        }
    }
    GroupFooter(tr("ทำส่วนไหนก่อนก็ได้ แต่ละส่วนสอบได้ครั้งเดียว เล่นจบหรือกดหยุดแล้วนับคะแนน (ออกจากหน้ากลางทางไม่นับ) ", "Take the parts in any order, each once. Marks count when you finish or press stop (leaving midway doesn't count). ", "どの部から受けてもよく、各部1回です。弾き終えるか停止を押すと採点されます（途中で離れると数えません）。") +
        tr("เมโทรนอมเปิดอยู่เพราะแอพวัดจังหวะเทียบกับความเร็วคงที่ ปิดชื่อโน้ตระหว่างสอบ", "The metronome is on because timing is judged against a steady tempo. Note names are off during the exam", "一定のテンポでリズムを判定するためメトロノームはオン。試験中は音名を表示しません"))
    Spacer(Modifier.height(8.dp))
    Group { GroupRow(tr("ยกเลิกการสอบนี้", "Cancel this exam", "この試験をやめる"), titleColor = Tokens.Palette.InkMuted, onClick = { confirm = true }) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(tr("ยกเลิกการสอบนี้?", "Cancel this exam?", "この試験をやめますか？")) },
        text = { Text(tr("ส่วนที่สอบแล้วจะไม่นับ การสอบนี้จะไม่อยู่ในผลสอบ", "Parts already taken won't count and this exam won't appear in the results", "受けた部は数えず、この試験は結果に残りません")) },
        confirmButton = { TextButton(onClick = { confirm = false; vm.abandon(s.id) }) { Text(tr("ยกเลิกการสอบ", "Cancel exam", "試験をやめる"), color = Tokens.Palette.Ink) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("สอบต่อ", "Continue", "続ける"), color = Tokens.Palette.Ink) } },
    )
}

@Composable
private fun ResultCard(s: ExamSitting) {
    val band = Exams.band(s.total)
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${s.total}", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            Text(" / ${Exams.MAX}", fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(band.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (band == Band.BELOW) Tokens.Palette.InkMuted else Tokens.Palette.Ink,
            modifier = Modifier.padding(top = 2.dp))
        val toPass = Exams.PASS - s.total
        val line = when (band) {
            Band.BELOW -> tr("ขาดอีก $toPass คะแนนถึงเกณฑ์ผ่าน", "$toPass more marks to pass", "合格まであと${toPass}点")
            Band.PASS -> tr("อีก ${Exams.MERIT - s.total} คะแนนถึงระดับดี", "${Exams.MERIT - s.total} more marks to merit", "良まであと${Exams.MERIT - s.total}点")
            Band.MERIT -> tr("อีก ${Exams.DISTINCTION - s.total} คะแนนถึงระดับดีเยี่ยม", "${Exams.DISTINCTION - s.total} more marks to distinction", "優まであと${Exams.DISTINCTION - s.total}点")
            Band.DISTINCTION -> if (Exams.grade(s.grade + 1) == null) tr("ผ่านเกรดสูงสุดของแอพแล้ว", "You passed the app's highest grade", "アプリの最高グレードに合格しました") else tr("พร้อมขยับไปเกรดถัดไป", "Ready for the next grade", "次のグレードへ進めます")
        }
        Text(line, fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
        // One bar per part, filled to its share of the part's marks, so the weak part shows at a glance.
        Column(Modifier.padding(top = 12.dp)) {
            ExamPart.entries.forEach { p ->
                val m = s.mark(p)?.marks ?: 0
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.title, fontSize = 12.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.widthIn(min = 112.dp))
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Line)) {
                        Box(Modifier.fillMaxWidth(m.toFloat() / p.max).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Ink))
                    }
                    Text("$m", fontSize = 12.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(start = 8.dp).widthIn(min = 20.dp))
                }
            }
        }
    }
}

// ---------------- one result ----------------

@Composable
fun ExamResultScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ExamViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val log by vm.exams.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val s = log.firstOrNull { it.id == vm.sittingId }
    val grade = s?.let { Exams.grade(it.grade) }
    if (!ready || s == null || grade == null) {
        Page(tr("ผลสอบ", "Results", "結果"), null, onBack) {}
        return
    }
    Page(tr("ผลสอบ ${grade.name}", "${grade.name} result", "${grade.name}の結果"), day(s), onBack) {
        if (s.abandoned || !s.complete) GroupFooter(if (s.abandoned) tr("การสอบนี้ถูกยกเลิก จึงไม่นับ", "This exam was cancelled, so it doesn't count", "この試験は中止されたため数えません") else tr("สอบไม่ครบทุกส่วน จึงไม่นับ", "Not every part was taken, so it doesn't count", "全部を受けていないため数えません"))
        Group { ResultCard(s) }
        GroupHeader(tr("แต่ละส่วน", "Each part", "各部"))
        Group {
            ExamPart.entries.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                val m = s.mark(p)
                GroupRow(
                    "${p.title} · ${partLine(p, s, grade, vm)}",
                    subtitle = m?.let { listOf(Exams.partBand(p, it.marks).title, Exams.remark(it)).filter { t -> t.isNotEmpty() }.joinToString("\n") } ?: tr("ไม่ได้สอบ", "Not taken", "未受験"),
                    value = m?.let { "${it.marks}/${p.max}" } ?: "–",
                )
            }
        }
        // Practise next: the two parts furthest below their full marks.
        val weak = ExamPart.entries.filter { s.mark(it) != null }.sortedBy { (s.mark(it)!!.marks).toDouble() / it.max }.take(2)
        if (weak.isNotEmpty()) {
            GroupHeader(tr("ฝึกต่อ", "Keep practising", "練習を続ける"))
            Group {
                weak.forEachIndexed { i, p ->
                    if (i > 0) GroupDivider()
                    val target = when (p) {
                        ExamPart.PIECE_1 -> Open.Piece(s.pieces[0])
                        ExamPart.PIECE_2 -> Open.Piece(s.pieces[1])
                        ExamPart.POP -> Open.Piece(Exams.pop(grade).id)
                        ExamPart.SCALES -> Open.ExamGrade(grade.n)
                        ExamPart.SIGHT -> if (grade.sightLevel <= 5) Open.Sight(grade.sightLevel) else Open.Skill(SkillId.SIGHT)
                        ExamPart.AURAL -> Open.EarHome
                    }
                    GroupRow(p.title, subtitle = partLine(p, s, grade, vm), value = "${s.mark(p)!!.marks}/${p.max}", onClick = { onOpen(target) })
                }
            }
        }
        GroupFooter(HONEST)
    }
}
