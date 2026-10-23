package com.id.mimikopi.ui.progress

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.ui.skills.look
import com.id.mimikopi.ui.components.softShadow
import com.id.mimikopi.ui.components.SkillIcon
import androidx.compose.ui.graphics.Color
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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

private val MONTHS = listOf(tr("ม.ค.", "Jan", "1月"), tr("ก.พ.", "Feb", "2月"), tr("มี.ค.", "Mar", "3月"), tr("เม.ย.", "Apr", "4月"), tr("พ.ค.", "May", "5月"), tr("มิ.ย.", "Jun", "6月"), tr("ก.ค.", "Jul", "7月"), tr("ส.ค.", "Aug", "8月"), tr("ก.ย.", "Sep", "9月"), tr("ต.ค.", "Oct", "10月"), tr("พ.ย.", "Nov", "11月"), tr("ธ.ค.", "Dec", "12月"))
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
            LargeTitle(tr("พัฒนาการ", "Progress", "上達"), tr("ดูย้อนหลังว่าเก่งขึ้นจริงไหม", "Look back: did you really improve?", "振り返り：本当に上達した？"), onBack = onBack)
            if (!ui.loaded) return@Column
            Column(Modifier.padding(horizontal = PageMargin)) {
                // The three numbers to read first, each against the week or month before; the details follow below.
                val today = LocalDate.now()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val thisWeek = ui.weeks.lastOrNull()?.second ?: 0
                    val lastWeek = ui.weeks.getOrNull(ui.weeks.size - 2)?.second ?: 0
                    SummaryTile(tr("ฝึกสัปดาห์นี้", "Practised this week", "今週の練習"), tr("$thisWeek นาที", "$thisWeek min", "${thisWeek}分"), change(thisWeek - lastWeek, tr("นาที", "min", "分")), Modifier.weight(1f))
                    val levelThen = ui.levels.lastOrNull { !it.first.isAfter(today.minusDays(30)) }?.second ?: 0
                    SummaryTile(tr("ระดับรวม", "Overall level", "総合レベル"), "${ui.total}", change(ui.total - levelThen, tr("ใน 30 วัน", "in 30 days", "30日間")), Modifier.weight(1f))
                    val earNow = ui.ear.lastOrNull()?.second
                    val earBefore = ui.ear.dropLast(1).lastOrNull { it.second != null }?.second
                    SummaryTile(tr("หู ถูกครั้งแรก", "Ear, right first time", "耳トレ 1回目で正解"), earNow?.let { "${(it * 100).toInt()}%" } ?: "–",
                        if (earNow != null && earBefore != null) change(((earNow - earBefore) * 100).toInt(), "%") else null, Modifier.weight(1f))
                }
                GroupHeader(tr("ระดับรวม 4 เดือนล่าสุด", "Overall level, last 4 months", "総合レベル（直近4か月）"))
                Group {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${ui.total}", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                            Text(tr(" จาก ${History.MAX_TOTAL} ระดับ", " of ${History.MAX_TOTAL}", " / ${History.MAX_TOTAL}"), fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
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
                        GroupRow("${c.skill.title} ${c.from} → ${c.to}", value = shortDate(c.date), titleColor = if (c.to > c.from) Tokens.Palette.Ink else Tokens.Palette.InkMuted,
                            leading = { SkillIcon(look(c.skill).first, look(c.skill).second, size = 32.dp, filled = true) })
                    }
                }
                GroupFooter(tr("นับจากแบบทดสอบในหน้าทักษะเท่านั้น ระดับลดได้ถ้าทดสอบระดับที่ถืออยู่ไม่ผ่านสองครั้งติด", "Counted only from tests on the Skills page. A level can drop if you fail the one you hold twice in a row", "スキルページのテストだけで数えます。今のレベルに2回続けて不合格だと下がることがあります"))

                GroupHeader(tr("เวลาฝึกต่อสัปดาห์", "Practice time per week", "週ごとの練習時間"))
                Group {
                    Column(Modifier.padding(16.dp)) {
                        val max = ui.weeks.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                        BarChart(ui.weeks.map { it.second.toFloat() / max }, listOfNotNull(ui.weeks.firstOrNull()?.first?.let(::shortDate), tr("สัปดาห์นี้", "This week", "今週")))
                        val avg = if (ui.weeks.isEmpty()) 0 else ui.weeks.sumOf { it.second } / ui.weeks.size
                        Text(tr("สัปดาห์นี้ ${ui.weeks.lastOrNull()?.second ?: 0} นาที · เฉลี่ย 12 สัปดาห์ $avg นาที", "This week ${ui.weeks.lastOrNull()?.second ?: 0} min · 12-week average $avg min", "今週${ui.weeks.lastOrNull()?.second ?: 0}分 · 12週平均${avg}分"), fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                GroupFooter(tr("รวมทุกเครื่องที่ซิงค์กัน นับเฉพาะเวลาที่เล่นจริง", "All synced devices, counting only time actually playing", "同期したすべての端末の合計、実際に弾いた時間だけ"))

                GroupHeader(tr("หู: ถูกครั้งแรกต่อสัปดาห์", "Ear: right first time per week", "耳トレ：週ごとの1回目正解率"))
                Group {
                    Column(Modifier.padding(16.dp)) {
                        val ear = Tokens.Skill.Ear
                        BarChart(ui.ear.map { it.second?.toFloat() }, listOfNotNull(ui.ear.firstOrNull()?.first?.let(::shortDate), tr("สัปดาห์นี้", "This week", "今週")),
                            steps = listOf(ear.fill, Color(0xFFEBCFAE), Color(0xFFE2BA8C), ear.ink))
                        val heard = ui.ear.mapNotNull { it.second }
                        Text(if (heard.isEmpty()) tr("ยังไม่ได้ฝึกหูในช่วงนี้", "No ear training in this period", "この期間は耳トレなし") else tr("ล่าสุด ${(heard.last() * 100).toInt()}% · ฝึก ${heard.size} สัปดาห์จาก 12", "Latest ${(heard.last() * 100).toInt()}% · practised ${heard.size} of 12 weeks", "最新 ${(heard.last() * 100).toInt()}% · 12週中${heard.size}週練習"),
                            fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                GroupFooter(tr("ข้อยากขึ้นตามขั้นที่เปิด เปอร์เซ็นต์จึงอาจลดลงตอนเข้าขั้นใหม่ ดูระดับหูประกอบ", "Questions get harder as steps open, so the percentage can drop at a new step; look at the ear level too", "段階が開くと問題が難しくなるため、新しい段階で割合が下がることがあります。耳のレベルも見てください"))

                GroupHeader(tr("สอบเกรด", "Grade exams", "グレード試験"))
                Group {
                    if (ui.exams.isEmpty()) {
                        GroupRow(tr("ยังไม่ได้สอบ", "No exams yet", "まだ受験していません"), subtitle = tr("การสอบวัดทุกด้านพร้อมกัน: เพลง สเกล อ่านโน้ตใหม่ และการฟัง", "An exam measures everything at once: pieces, scales, sight-reading and aural", "試験はすべてを一度に測ります：曲、スケール、初見、聴音"), onClick = { onOpen(Open.Exams) })
                    } else {
                        Column(Modifier.padding(16.dp)) {
                            val recent = ui.exams.takeLast(12)
                            BarChart(recent.map { it.total.toFloat() / Exams.MAX }, listOfNotNull(recent.firstOrNull()?.let { runCatching { shortDate(LocalDate.parse(it.day)) }.getOrNull() }, tr("ล่าสุด", "Latest", "最新")))
                            val last = ui.exams.last()
                            Text("ล่าสุด ${Exams.grade(last.grade)?.name ?: ""} ${last.total}/${Exams.MAX} · ${Exams.band(last.total).title}",
                                fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                        }
                        GroupDivider()
                        GroupRow(tr("ผลสอบทั้งหมด", "All exam results", "すべての試験結果"), onClick = { onOpen(Open.Exams) })
                    }
                }
                GroupFooter(tr("คะแนนรวม 150 ผ่าน 100 ดี 120 ดีเยี่ยม 130 เทียบกันได้เฉพาะเกรดเดียวกัน", "Total 150: pass 100, merit 120, distinction 130. Compare only within the same grade", "合計150点：合格100、良120、優130。同じグレード内でのみ比較できます"))

                GroupHeader(tr("สิ่งที่ควรแก้", "What to fix", "直すところ"))
                Group {
                    if (ui.fixes.isEmpty()) GroupRow(tr("ยังไม่พบรูปแบบที่ผิดซ้ำ", "No repeated mistakes found yet", "繰り返すミスはまだ見つかっていません"), subtitle = tr("แอพดูจาก ${Mistakes.DAYS} วันล่าสุด ต้องเกิดซ้ำหลายรอบก่อนจะขึ้นตรงนี้", "The app looks at the last ${Mistakes.DAYS} days; a mistake must repeat several times before it shows here", "アプリは直近${Mistakes.DAYS}日を見ます。何度も繰り返したミスだけがここに出ます"))
                    ui.fixes.forEachIndexed { i, m ->
                        if (i > 0) GroupDivider()
                        GroupRow(m.title, subtitle = "${m.detail}\n${m.fixText}", onClick = { onOpen(fixTarget(m.fix)) })
                    }
                }
                GroupFooter(tr("ดูจาก ${Mistakes.DAYS} วันล่าสุด: การเล่นเพลงและแบบฝึก การอ่านโน้ต และฝึกหู", "From the last ${Mistakes.DAYS} days: playing pieces and drills, note reading and ear training", "直近${Mistakes.DAYS}日から：曲と練習、読譜、耳トレ"))
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** "↑ 12 นาที" with whether it went up, or null when nothing changed. */
private fun change(delta: Int, unit: String): Pair<String, Boolean>? =
    if (delta == 0) null else ("${if (delta > 0) "↑" else "↓"} ${kotlin.math.abs(delta)} $unit") to (delta > 0)

@Composable
private fun SummaryTile(title: String, value: String, delta: Pair<String, Boolean>?, modifier: Modifier) {
    val shape = RoundedCornerShape(Tokens.Radius.box)
    Column(modifier.softShadow(shape, small = true).clip(shape).background(Tokens.Palette.Group).padding(14.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted, maxLines = 1)
        Text(value, fontSize = 22.sp, fontFamily = Tokens.Fonts.body, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, maxLines = 1)
        Text(delta?.first ?: tr("เท่าเดิม", "Original", "原曲どおり"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = when (delta?.second) { true -> Tokens.Palette.Accent; false -> Tokens.Palette.Timing; null -> Tokens.Palette.InkMuted })
    }
}

