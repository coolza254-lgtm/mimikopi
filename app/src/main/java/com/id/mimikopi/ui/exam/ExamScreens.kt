package com.id.mimikopi.ui.exam

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

    fun pieceTitle(id: String): String = services.pieces.pieceOrExercise(id)?.title ?: "เพลงเดิมที่นำออก ($id)"
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
        if (g.pieces.any { services.pieces.piece(it) == null }) return
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
fun ExamHomeScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ExamViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val log by vm.exams.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val finished = ExamLog.finished(log)
    Page("วัดพื้นฐานและผลสอบเดิม", "ฝึกเพื่อ Heroic Polonaise", onBack) {
        if (!ready) return@Page
        GroupFooter("เอาแบบสอบหลายเพลงออกตามเป้าหมายเพลงเดียวแล้ว ประวัติคะแนนเดิมยังเก็บไว้ ใช้แบบทดสอบพื้นฐานและผลรายช่วง Heroic เพื่อติดตามต่อ")
        GroupHeader("ฝึกและวัดผลต่อ")
        Group {
            GroupRow("แผน Heroic Polonaise", subtitle = "แยกมือ รวมมือ และดูผลของท่อนหลัก", onClick = { onOpen(Open.Goal) })
            listOf(SkillId.NOTES, SkillId.RHYTHM, SkillId.TECH, SkillId.CHORDS, SkillId.EAR).forEach { skill ->
                GroupDivider()
                GroupRow(skill.title, subtitle = "ฝึกพื้นฐานและทดสอบระดับ", onClick = { onOpen(Open.Skill(skill)) })
            }
        }
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
fun ExamGradeScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ExamViewModel = viewModel()) {
    ExamHomeScreen(onBack, onOpen, vm)
}

private fun partLine(part: ExamPart, s: ExamSitting, grade: Grade, vm: ExamViewModel): String = when (part) {
    ExamPart.PIECE_1 -> vm.pieceTitle(s.pieces[0])
    ExamPart.PIECE_2 -> vm.pieceTitle(s.pieces[1])
    ExamPart.POP -> Exams.pop(grade).title
    ExamPart.SCALES -> "${grade.ask} ข้อต่อกัน ♩ = ${grade.scaleBpm}"
    ExamPart.SIGHT -> "ระดับ L${grade.sightLevel} · ดูโน้ต 30 วินาที"
    ExamPart.AURAL -> "${grade.aural.count} ข้อ ตอบครั้งเดียว"
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
            Band.DISTINCTION -> if (Exams.grade(s.grade + 1) == null) "ผ่านเกรดสูงสุดของแอพแล้ว" else "พร้อมขยับไปเกรดถัดไป"
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
                        ExamPart.PIECE_1 -> Open.Goal
                        ExamPart.PIECE_2 -> Open.Goal
                        ExamPart.POP -> Open.Skill(SkillId.CHORDS)
                        ExamPart.SCALES -> Open.Skill(SkillId.TECH)
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
