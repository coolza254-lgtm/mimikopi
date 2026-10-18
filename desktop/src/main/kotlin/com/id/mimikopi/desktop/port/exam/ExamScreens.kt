package com.id.mimikopi.desktop.port.exam

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/exam/ExamScreens.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
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
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.Lifecycle
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.desktop.port.LifecycleEventEffect
import com.id.mimikopi.desktop.port.collectAsStateWithLifecycle
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
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
import com.id.mimikopi.desktop.port.components.BarChart
import com.id.mimikopi.desktop.port.components.Group
import com.id.mimikopi.desktop.port.components.GroupDivider
import com.id.mimikopi.desktop.port.components.GroupFooter
import com.id.mimikopi.desktop.port.components.GroupHeader
import com.id.mimikopi.desktop.port.components.GroupRow
import com.id.mimikopi.desktop.port.components.LargeTitle
import com.id.mimikopi.desktop.port.components.PageMargin
import com.id.mimikopi.desktop.port.components.PageMaxWidth
import com.id.mimikopi.desktop.port.components.PrimaryButton
import com.id.mimikopi.desktop.port.components.StepMark
import com.id.mimikopi.desktop.port.components.paper
import com.id.mimikopi.desktop.port.progress.shortDate
import com.id.mimikopi.desktop.ui.Tokens
import com.id.mimikopi.desktop.port.today.Open
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Grade exams (learning-system.md 2.7): the grades with the best result of each, the requirements of a grade with
 * practice links for every part, the exam in progress (each part once, finished on the day), and the result with
 * marks per part. Shared by the phone and the desktop (ported).
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

private const val HONEST = "โครงสร้างและเกณฑ์คะแนนเลียนแบบการสอบปฏิบัติ ABRSM (เพลง 3 เพลง สเกล อ่านโน้ตใหม่ และการฟัง รวม 150 คะแนน " +
    "ผ่าน 100 ดี 120 ดีเยี่ยม 130) แต่เพลง สเกล ข้อสอบ และการให้คะแนนเป็นของแอพเอง ไม่ใช่การสอบหรือเกรดทางการ"

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
fun ExamHomeScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ExamViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val log by vm.exams.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val active = ExamLog.active(log, today())
    val finished = ExamLog.finished(log)

    Page("สอบเกรด", "วัดว่าเก่งขึ้นจริงไหม แบบการสอบเปียโน", onBack) {
        if (!ready) return@Page
        if (active != null) {
            val g = Exams.grade(active.grade)
            GroupHeader("กำลังสอบ")
            Group {
                GroupRow("${g?.name ?: ""} · ได้ ${active.total} คะแนนแล้ว", subtitle = "สอบแล้ว ${active.parts.size} จาก ${ExamPart.entries.size} ส่วน · ต้องจบภายในวันนี้",
                    onClick = { onOpen(Open.ExamGrade(active.grade)) })
            }
        }
        GroupHeader("เกรด")
        Group {
            Exams.GRADES.forEachIndexed { i, g ->
                if (i > 0) GroupDivider()
                val best = ExamLog.best(log, g.n)
                GroupRow(g.name, subtitle = g.about, value = best?.let { "${it.total} · ${Exams.band(it.total).title.substringBefore(" (")}" } ?: "${g.minutes} นาที",
                    onClick = { onOpen(Open.ExamGrade(g.n)) })
            }
        }
        GroupFooter(HONEST)

        if (finished.isNotEmpty()) {
            GroupHeader("ผลสอบที่ผ่านมา")
            Group {
                val recent = finished.take(12).reversed()
                Column(Modifier.padding(16.dp)) {
                    BarChart(recent.map { it.total.toFloat() / Exams.MAX }, listOfNotNull(recent.firstOrNull()?.let(::day), "ล่าสุด"))
                    Text("ผ่านที่ ${Exams.PASS} · ดีที่ ${Exams.MERIT} · ดีเยี่ยมที่ ${Exams.DISTINCTION} จาก ${Exams.MAX}", fontSize = 13.sp,
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
fun ExamGradeScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ExamViewModel) {
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
            GroupHeader("ผลสอบวันนี้")
            Group {
                ResultCard(todayResult)
                GroupDivider()
                GroupRow("ดูคะแนนแต่ละส่วน", onClick = { onOpen(Open.ExamResult(todayResult.id)) })
            }
        }
        Requirements(grade, chosen, vm, onOpen)
        if (active != null) GroupFooter("กำลังสอบ ${Exams.grade(active.grade)?.name ?: ""} อยู่ เริ่มเกรดนี้จะยกเลิกการสอบนั้น")
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (chosen.size == 2) "เริ่มสอบ ${grade.name}" else "เลือกเพลงให้ครบ 2 เพลงก่อน", onClick = vm::start, enabled = chosen.size == 2)
        GroupFooter("ใช้เวลาราว ${grade.minutes} นาที ต้องสอบให้ครบในวันเดียว แต่ละส่วนสอบได้ครั้งเดียวเหมือนสอบจริง")
    }
}

@Composable
private fun Requirements(grade: Grade, chosen: List<String>, vm: ExamViewModel, onOpen: (Open) -> Unit) {
    GroupHeader(if (grade.pieces.size == 2) "เพลง (สอบทั้งสองเพลง)" else "เพลง · เลือก 2 เพลง")
    Group {
        grade.pieces.forEachIndexed { i, id ->
            if (i > 0) GroupDivider()
            val selected = id in chosen
            GroupRow(
                vm.pieceTitle(id),
                subtitle = vm.pieceTempo(id)?.let { "สองมือ ♩ = $it ตามที่เขียน" },
                leading = { Choice(selected) },
                trailing = { TextButton(onClick = { onOpen(Open.Piece(id)) }) { Text("ซ้อม", color = Tokens.Palette.Ink) } },
                onClick = if (grade.pieces.size == 2) null else ({ vm.toggle(id) }),
            )
        }
    }
    GroupFooter("เพลงจากคลังเพลงสาธารณะของแอพ ซ้อมในหน้าเพลงได้ตามปกติก่อนสอบ")

    val pop = Exams.pop(grade)
    GroupHeader("เพลงป็อป")
    Group {
        GroupRow(pop.title, subtitle = "คีย์ ${Keys.name(grade.pop.key)} · ♩ = ${grade.pop.bpm} · มือซ้าย${Accompaniment.LEVEL_NAMES[grade.pop.pattern] ?: ""} · ${grade.pop.bars} ห้อง",
            value = "ซ้อม", onClick = { onOpen(Open.Piece(pop.id)) })
    }
    GroupFooter("แอพแต่งเองบนทางเดินคอร์ดแบบเพลงไอดอล มีชื่อคอร์ดกำกับ เพลงเดิมทุกครั้งจึงซ้อมไว้ก่อนได้")

    GroupHeader("สเกลและอาร์เพจจิโอ · สอบ ${grade.ask} จาก ${grade.scales.size}")
    Group {
        grade.scales.forEachIndexed { i, s ->
            if (i > 0) GroupDivider()
            GroupRow(s.title(), onClick = { onOpen(Open.Piece(Exams.scalePractice(grade, s).id)) })
        }
    }
    GroupFooter("♩ = ${grade.scaleBpm} ${if (grade.quarters) "ตัวดำ" else "เขบ็ตหนึ่งชั้น"} ขึ้นแล้วลง ใช้นิ้วตามที่เขียน (ไมเนอร์เป็นแบบฮาร์โมนิก) " +
        "ตอนสอบแอพสุ่มถาม ${grade.ask} ข้อต่อกันในรอบเดียว")

    GroupHeader("อ่านโน้ตใหม่")
    Group {
        val hands = when (Exams.sightHand(grade)) { "LEFT" -> "มือซ้าย"; "BOTH" -> "สองมือ"; else -> "มือขวา" }
        GroupRow("บทใหม่ระดับ L${grade.sightLevel} · $hands · ♩ = ${grade.sightBpm}", subtitle = "ดูโน้ต 30 วินาทีแล้วเริ่มเล่นทันที เล่นครั้งเดียว",
            value = "ฝึก", onClick = { onOpen(if (grade.sightLevel <= 5) Open.Sight(grade.sightLevel) else Open.Skill(SkillId.SIGHT)) })
    }

    GroupHeader("ทดสอบการฟัง · ${grade.aural.count} ข้อ")
    Group {
        GroupRow(grade.aural.items.map { EarCourse.kindOf(it).title }.distinct().joinToString(" · "),
            subtitle = grade.aural.items.joinToString(" · ") { EarCourse.label(it) }, value = "ฝึก", onClick = { onOpen(Open.EarHome) })
    }

    GroupHeader("คะแนน")
    Group {
        ExamPart.entries.forEachIndexed { i, p ->
            if (i > 0) GroupDivider()
            GroupRow(p.title, value = "${p.max} (ผ่าน ${p.pass})")
        }
    }
    GroupFooter("รวม ${Exams.MAX} · ผ่าน ${Exams.PASS} · ดี ${Exams.MERIT} · ดีเยี่ยม ${Exams.DISTINCTION} ตัดสินจากคะแนนรวมเหมือน ABRSM " +
        "คะแนนการเล่นมาจากโน้ตที่ถูก ความตรงจังหวะ และการหยุดกลางทาง\n\n$HONEST")
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
    ExamPart.SCALES -> "${grade.ask} ข้อต่อกัน ♩ = ${grade.scaleBpm}"
    ExamPart.SIGHT -> "ระดับ L${grade.sightLevel} · ดูโน้ต 30 วินาที"
    ExamPart.AURAL -> "${grade.aural.count} ข้อ ตอบครั้งเดียว"
}

@Composable
private fun InProgress(s: ExamSitting, grade: Grade, vm: ExamViewModel, onOpen: (Open) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    GroupHeader("กำลังสอบ")
    Group {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.total}", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text(" / ${Exams.MAX} คะแนน", fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text("สอบแล้ว ${s.parts.size} จาก ${ExamPart.entries.size} ส่วน · ผ่าน ${Exams.PASS} · ดี ${Exams.MERIT} · ดีเยี่ยม ${Exams.DISTINCTION}",
                fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
    GroupHeader("ส่วนของการสอบ")
    Group {
        val next = ExamPart.entries.firstOrNull { s.mark(it) == null }
        ExamPart.entries.forEachIndexed { i, p ->
            if (i > 0) GroupDivider()
            val m = s.mark(p)
            GroupRow(
                p.title, subtitle = partLine(p, s, grade, vm),
                value = m?.let { "${it.marks}/${p.max}" } ?: "สอบ",
                leading = { StepMark(done = m != null, current = p == next) },
                onClick = if (m == null) ({ onOpen(Open.ExamTake(s.id, p.name)) }) else null,
            )
        }
    }
    GroupFooter("ทำส่วนไหนก่อนก็ได้ แต่ละส่วนสอบได้ครั้งเดียว เล่นจบหรือกดหยุดแล้วนับคะแนน (ออกจากหน้ากลางทางไม่นับ) " +
        "เมโทรนอมเปิดอยู่เพราะแอพวัดจังหวะเทียบกับความเร็วคงที่ ปิดชื่อโน้ตระหว่างสอบ")
    Spacer(Modifier.height(8.dp))
    Group { GroupRow("ยกเลิกการสอบนี้", titleColor = Tokens.Palette.InkMuted, onClick = { confirm = true }) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("ยกเลิกการสอบนี้?") },
        text = { Text("ส่วนที่สอบแล้วจะไม่นับ การสอบนี้จะไม่อยู่ในผลสอบ") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.abandon(s.id) }) { Text("ยกเลิกการสอบ", color = Tokens.Palette.Ink) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("สอบต่อ", color = Tokens.Palette.Ink) } },
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
            Band.BELOW -> "ขาดอีก $toPass คะแนนถึงเกณฑ์ผ่าน"
            Band.PASS -> "อีก ${Exams.MERIT - s.total} คะแนนถึงระดับดี"
            Band.MERIT -> "อีก ${Exams.DISTINCTION - s.total} คะแนนถึงระดับดีเยี่ยม"
            Band.DISTINCTION -> "พร้อมขยับไปเกรดถัดไป"
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
fun ExamResultScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ExamViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val log by vm.exams.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val s = log.firstOrNull { it.id == vm.sittingId }
    val grade = s?.let { Exams.grade(it.grade) }
    if (!ready || s == null || grade == null) {
        Page("ผลสอบ", null, onBack) {}
        return
    }
    Page("ผลสอบ ${grade.name}", day(s), onBack) {
        if (s.abandoned || !s.complete) GroupFooter(if (s.abandoned) "การสอบนี้ถูกยกเลิก จึงไม่นับ" else "สอบไม่ครบทุกส่วน จึงไม่นับ")
        Group { ResultCard(s) }
        GroupHeader("แต่ละส่วน")
        Group {
            ExamPart.entries.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                val m = s.mark(p)
                GroupRow(
                    "${p.title} · ${partLine(p, s, grade, vm)}",
                    subtitle = m?.let { listOf(Exams.partBand(p, it.marks).title, Exams.remark(it)).filter { t -> t.isNotEmpty() }.joinToString("\n") } ?: "ไม่ได้สอบ",
                    value = m?.let { "${it.marks}/${p.max}" } ?: "–",
                )
            }
        }
        // Practise next: the two parts furthest below their full marks.
        val weak = ExamPart.entries.filter { s.mark(it) != null }.sortedBy { (s.mark(it)!!.marks).toDouble() / it.max }.take(2)
        if (weak.isNotEmpty()) {
            GroupHeader("ฝึกต่อ")
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
