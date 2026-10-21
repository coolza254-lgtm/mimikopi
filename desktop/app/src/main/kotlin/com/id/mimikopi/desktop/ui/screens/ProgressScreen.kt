package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.exam.ExamLog
import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.progress.Fix
import com.id.mimikopi.core.progress.History
import com.id.mimikopi.core.progress.Mistakes
import com.id.mimikopi.desktop.session.DrillSource
import com.id.mimikopi.desktop.session.EarArgs
import com.id.mimikopi.desktop.ui.AppContext
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.Tab
import com.id.mimikopi.desktop.ui.components.BarChart
import com.id.mimikopi.desktop.ui.components.Group
import com.id.mimikopi.desktop.ui.components.GroupDivider
import com.id.mimikopi.desktop.ui.components.GroupFooter
import com.id.mimikopi.desktop.ui.components.GroupHeader
import com.id.mimikopi.desktop.ui.components.GroupRow
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.StepChart
import com.id.mimikopi.desktop.ui.theme.Tokens
import java.time.LocalDate

private val MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
fun shortDate(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"

/** Where a fix is practised. */
private fun openFix(app: AppContext, fix: Fix) = when (fix) {
    Fix.Metronome -> app.nav.open(Screen.Metronome)
    Fix.Review -> app.nav.open(Screen.Drill(DrillSource.Review))
    Fix.Songs -> app.nav.home(Tab.SONGS)
    is Fix.Ear -> app.nav.open(Screen.Ear(EarArgs(focus = fix.kind)))
}

/**
 * Looking back: the total of all skill levels over four months (from tests only) with each level change, minutes
 * practised per week, ear accuracy per week, past exams and the patterns in recent mistakes with what to practise.
 */
@Composable
fun ProgressScreen() {
    val app = LocalApp.current
    val p = app.services.progress
    val checks by p.checks.collectAsState()
    val log by p.log.collectAsState()
    val mistakes by p.mistakes.collectAsState()
    val sittings by p.exams.collectAsState()
    val today = LocalDate.now()
    val ear = remember(log) { EarProgress.parse(app.services.db.get(EarProgress.KEY)) }
    val total = History.levelsOn(checks, today).values.sum()
    val levels = History.levelSeries(checks, today, 120)
    val changes = History.changes(checks, today, 120).take(10)
    val weeks = History.weeklyMinutes(log, today, 12)
    val earWeeks = History.earWeekly(ear, today, 12)
    val fixes = Mistakes.summarize(mistakes, ear, today)
    val exams = ExamLog.finished(sittings).reversed()

    Page {
        LargeTitle("พัฒนาการ", "ดูย้อนหลังว่าเก่งขึ้นจริงไหม")
        GroupHeader("ระดับรวม 4 เดือนล่าสุด")
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$total", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                    Text(" จาก ${History.MAX_TOTAL} ระดับ", fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
                }
                val first = levels.firstOrNull()?.first
                val last = levels.lastOrNull()?.first
                if (first != null && last != null) {
                    val span = (last.toEpochDay() - first.toEpochDay()).coerceAtLeast(1).toFloat()
                    StepChart(
                        levels.map { (d, v) -> (d.toEpochDay() - first.toEpochDay()) / span to v.toFloat() / History.MAX_TOTAL },
                        listOf(shortDate(first), shortDate(last)),
                        Modifier.padding(top = 12.dp),
                    )
                }
            }
            changes.forEach { c ->
                GroupDivider()
                GroupRow("${c.skill.title} ${c.from} → ${c.to}", value = shortDate(c.date), titleColor = if (c.to > c.from) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
        GroupFooter("นับจากแบบทดสอบในหน้าทักษะเท่านั้น ระดับลดได้ถ้าทดสอบระดับที่ถืออยู่ไม่ผ่านสองครั้งติด")

        GroupHeader("เวลาฝึกต่อสัปดาห์")
        Group {
            Column(Modifier.padding(16.dp)) {
                val max = weeks.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                BarChart(weeks.map { it.second.toFloat() / max }, listOfNotNull(weeks.firstOrNull()?.first?.let(::shortDate), "สัปดาห์นี้"))
                val avg = if (weeks.isEmpty()) 0 else weeks.sumOf { it.second } / weeks.size
                Text("สัปดาห์นี้ ${weeks.lastOrNull()?.second ?: 0} นาที · เฉลี่ย 12 สัปดาห์ $avg นาที", fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
            }
        }
        GroupFooter("รวมข้อมูลที่นำเข้าจากมือถือ นับเฉพาะเวลาที่เล่นจริง")

        GroupHeader("หู: ถูกครั้งแรกต่อสัปดาห์")
        Group {
            Column(Modifier.padding(16.dp)) {
                BarChart(earWeeks.map { it.second?.toFloat() }, listOfNotNull(earWeeks.firstOrNull()?.first?.let(::shortDate), "สัปดาห์นี้"))
                val heard = earWeeks.mapNotNull { it.second }
                Text(if (heard.isEmpty()) "ยังไม่ได้ฝึกหูในช่วงนี้" else "ล่าสุด ${(heard.last() * 100).toInt()}% · ฝึก ${heard.size} สัปดาห์จาก 12",
                    fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
            }
        }
        GroupFooter("ข้อยากขึ้นตามขั้นที่เปิด เปอร์เซ็นต์จึงอาจลดลงตอนเข้าขั้นใหม่ ดูระดับหูประกอบ")

        if (exams.isNotEmpty()) {
            GroupHeader("ผลสอบเดิม (จากมือถือ)")
            Group {
                Column(Modifier.padding(16.dp)) {
                    val recent = exams.takeLast(12)
                    BarChart(recent.map { it.total.toFloat() / Exams.MAX }, listOfNotNull(recent.firstOrNull()?.let { runCatching { shortDate(LocalDate.parse(it.day)) }.getOrNull() }, "ล่าสุด"))
                    val last = exams.last()
                    Text("ล่าสุด ${Exams.grade(last.grade)?.name ?: ""} ${last.total}/${Exams.MAX} · ${Exams.band(last.total).title}",
                        fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                }
            }
            GroupFooter("คะแนนรวม 150 ผ่าน 100 ดี 120 ดีเยี่ยม 130 เทียบกันได้เฉพาะเกรดเดียวกัน")
        }

        GroupHeader("สิ่งที่ควรแก้")
        Group {
            if (fixes.isEmpty()) GroupRow("ยังไม่พบรูปแบบที่ผิดซ้ำ", "โปรแกรมดูจาก ${Mistakes.DAYS} วันล่าสุด ต้องเกิดซ้ำหลายรอบก่อนจะขึ้นตรงนี้")
            fixes.forEachIndexed { i, m ->
                if (i > 0) GroupDivider()
                GroupRow(m.title, "${m.detail}\n${m.fixText}", onClick = { openFix(app, m.fix) })
            }
        }
        GroupFooter("ดูจาก ${Mistakes.DAYS} วันล่าสุด: การเล่นเพลงและแบบฝึก การอ่านโน้ต และฝึกหู")
    }
}
