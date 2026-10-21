package com.id.mimikopi.ui.progress

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.exam.ExamLog
import com.id.mimikopi.core.exam.ExamSitting
import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.progress.Fix
import com.id.mimikopi.core.progress.History
import com.id.mimikopi.core.progress.Mistake
import com.id.mimikopi.core.progress.Mistakes
import com.id.mimikopi.ui.components.BarChart
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupFooter
import com.id.mimikopi.ui.components.GroupHeader
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.LargeTitle
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.StepChart
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.Open
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProgressUi(
    val loaded: Boolean = false,
    val total: Int = 0,
    val levels: List<Pair<LocalDate, Int>> = emptyList(),
    val changes: List<History.LevelChange> = emptyList(),
    val weeks: List<Pair<LocalDate, Int>> = emptyList(),
    val ear: List<Pair<LocalDate, Double?>> = emptyList(),
    val fixes: List<Mistake> = emptyList(),
    /** Finished grade exams, oldest first. */
    val exams: List<ExamSitting> = emptyList(),
)

class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val _ui = MutableStateFlow(ProgressUi())
    val ui: StateFlow<ProgressUi> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val p = services.progress
            p.load()
            val today = LocalDate.now()
            val ear = EarProgress.parse(services.database.settingsDao().get(EarProgress.KEY))
            val checks = p.checks.value
            _ui.value = ProgressUi(
                loaded = true,
                total = History.levelsOn(checks, today).values.sum(),
                levels = History.levelSeries(checks, today, 120),
                changes = History.changes(checks, today, 120).take(10),
                weeks = History.weeklyMinutes(p.log.value, today, 12),
                ear = History.earWeekly(ear, today, 12),
                fixes = Mistakes.summarize(p.mistakes.value, ear, today),
                exams = ExamLog.finished(p.exams.value).reversed(),
            )
        }
    }
}

/** Where a fix is practised. */
fun fixTarget(fix: Fix): Open = when (fix) {
    Fix.Metronome -> Open.Metronome
    Fix.Review -> Open.Review
    Fix.Songs -> Open.Songs
    is Fix.Ear -> Open.Ear(fix.kind.name)
}

private val MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
fun shortDate(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"

/**
 * Looking back: the total of all skill levels over four months (from tests only) with each level change, minutes
 * practised per week, ear accuracy per week, and the patterns in recent mistakes with what to practise for each.
 */
@Composable
fun ProgressScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: ProgressViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) {
            LargeTitle("พัฒนาการ", "ดูย้อนหลังว่าเก่งขึ้นจริงไหม", onBack = onBack)
            if (!ui.loaded) return@Column
            Column(Modifier.padding(horizontal = PageMargin)) {
                GroupHeader("ระดับรวม 4 เดือนล่าสุด")
                Group {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${ui.total}", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                            Text(" จาก ${History.MAX_TOTAL} ระดับ", fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        val first = ui.levels.firstOrNull()?.first
                        val last = ui.levels.lastOrNull()?.first
                        if (first != null && last != null) {
                            val span = (last.toEpochDay() - first.toEpochDay()).coerceAtLeast(1).toFloat()
                            StepChart(
                                ui.levels.map { (d, v) -> (d.toEpochDay() - first.toEpochDay()) / span to v.toFloat() / History.MAX_TOTAL },
                                listOf(shortDate(first), shortDate(last)),
                                Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    ui.changes.forEach { c ->
                        GroupDivider()
                        GroupRow("${c.skill.title} ${c.from} → ${c.to}", value = shortDate(c.date), titleColor = if (c.to > c.from) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                    }
                }
                GroupFooter("นับจากแบบทดสอบในหน้าทักษะเท่านั้น ระดับลดได้ถ้าทดสอบระดับที่ถืออยู่ไม่ผ่านสองครั้งติด")

                GroupHeader("เวลาฝึกต่อสัปดาห์")
                Group {
                    Column(Modifier.padding(16.dp)) {
                        val max = ui.weeks.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                        BarChart(ui.weeks.map { it.second.toFloat() / max }, listOfNotNull(ui.weeks.firstOrNull()?.first?.let(::shortDate), "สัปดาห์นี้"))
                        val avg = if (ui.weeks.isEmpty()) 0 else ui.weeks.sumOf { it.second } / ui.weeks.size
                        Text("สัปดาห์นี้ ${ui.weeks.lastOrNull()?.second ?: 0} นาที · เฉลี่ย 12 สัปดาห์ $avg นาที", fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                GroupFooter("รวมทุกเครื่องที่ซิงค์กัน นับเฉพาะเวลาที่เล่นจริง")

                GroupHeader("หู: ถูกครั้งแรกต่อสัปดาห์")
                Group {
                    Column(Modifier.padding(16.dp)) {
                        BarChart(ui.ear.map { it.second?.toFloat() }, listOfNotNull(ui.ear.firstOrNull()?.first?.let(::shortDate), "สัปดาห์นี้"))
                        val heard = ui.ear.mapNotNull { it.second }
                        Text(if (heard.isEmpty()) "ยังไม่ได้ฝึกหูในช่วงนี้" else "ล่าสุด ${(heard.last() * 100).toInt()}% · ฝึก ${heard.size} สัปดาห์จาก 12",
                            fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                GroupFooter("ข้อยากขึ้นตามขั้นที่เปิด เปอร์เซ็นต์จึงอาจลดลงตอนเข้าขั้นใหม่ ดูระดับหูประกอบ")

                GroupHeader("ผลสอบเดิม")
                Group {
                    if (ui.exams.isEmpty()) {
                        GroupRow("ติดตาม Heroic Polonaise", subtitle = "ดูผลตามช่วง มือ และความเร็ว", onClick = { onOpen(Open.Goal) })
                    } else {
                        Column(Modifier.padding(16.dp)) {
                            val recent = ui.exams.takeLast(12)
                            BarChart(recent.map { it.total.toFloat() / Exams.MAX }, listOfNotNull(recent.firstOrNull()?.let { runCatching { shortDate(LocalDate.parse(it.day)) }.getOrNull() }, "ล่าสุด"))
                            val last = ui.exams.last()
                            Text("ล่าสุด ${Exams.grade(last.grade)?.name ?: ""} ${last.total}/${Exams.MAX} · ${Exams.band(last.total).title}",
                                fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                        }
                        GroupDivider()
                        GroupRow("ผลสอบทั้งหมด", onClick = { onOpen(Open.Exams) })
                    }
                }
                GroupFooter("คะแนนรวม 150 ผ่าน 100 ดี 120 ดีเยี่ยม 130 เทียบกันได้เฉพาะเกรดเดียวกัน")

                GroupHeader("สิ่งที่ควรแก้")
                Group {
                    if (ui.fixes.isEmpty()) GroupRow("ยังไม่พบรูปแบบที่ผิดซ้ำ", subtitle = "แอพดูจาก ${Mistakes.DAYS} วันล่าสุด ต้องเกิดซ้ำหลายรอบก่อนจะขึ้นตรงนี้")
                    ui.fixes.forEachIndexed { i, m ->
                        if (i > 0) GroupDivider()
                        GroupRow(m.title, subtitle = "${m.detail}\n${m.fixText}", onClick = { onOpen(fixTarget(m.fix)) })
                    }
                }
                GroupFooter("ดูจาก ${Mistakes.DAYS} วันล่าสุด: การเล่นเพลงและแบบฝึก การอ่านโน้ต และฝึกหู")
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
