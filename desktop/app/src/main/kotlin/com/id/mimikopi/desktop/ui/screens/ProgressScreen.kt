package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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

private val MONTHS = listOf(tr("ม.ค.", "Jan", "1月"), tr("ก.พ.", "Feb", "2月"), tr("มี.ค.", "Mar", "3月"), tr("เม.ย.", "Apr", "4月"), tr("พ.ค.", "May", "5月"), tr("มิ.ย.", "Jun", "6月"), tr("ก.ค.", "Jul", "7月"), tr("ส.ค.", "Aug", "8月"), tr("ก.ย.", "Sep", "9月"), tr("ต.ค.", "Oct", "10月"), tr("พ.ย.", "Nov", "11月"), tr("ธ.ค.", "Dec", "12月"))
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
        LargeTitle(tr("พัฒนาการ", "Progress", "上達"), tr("ดูย้อนหลังว่าเก่งขึ้นจริงไหม", "Look back: did you really improve?", "振り返り：本当に上達した？"))
        // The three numbers to read first, each against the week or month before; the details follow below.
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            val thisWeek = weeks.lastOrNull()?.second ?: 0
            val lastWeek = weeks.getOrNull(weeks.size - 2)?.second ?: 0
            SummaryTile(tr("เวลาฝึกสัปดาห์นี้", "Practice this week", "今週の練習時間"), tr("$thisWeek นาที", "$thisWeek min", "${thisWeek}分"), change(thisWeek - lastWeek, tr("นาที", "min", "分"), tr("จากสัปดาห์ก่อน", "vs last week", "先週比")), Modifier.weight(1f))
            val levelThen = levels.lastOrNull { !it.first.isAfter(today.minusDays(30)) }?.second ?: 0
            SummaryTile(tr("ระดับรวม", "Overall level", "総合レベル"), "$total", change(total - levelThen, tr("ระดับ", "Level", "レベル"), tr("ใน 30 วัน", "in 30 days", "30日間")), Modifier.weight(1f))
            val heard = earWeeks.mapNotNull { it.second }
            val earNow = earWeeks.lastOrNull()?.second
            val earBefore = earWeeks.dropLast(1).lastOrNull { it.second != null }?.second
            SummaryTile(tr("หู: ถูกครั้งแรก", "Ear: right first time", "耳トレ：1回目正解"), earNow?.let { "${(it * 100).toInt()}%" } ?: if (heard.isEmpty()) "–" else "${(heard.last() * 100).toInt()}%",
                if (earNow != null && earBefore != null) change(((earNow - earBefore) * 100).toInt(), "%", tr("จากสัปดาห์ก่อน", "vs last week", "先週比")) else null, Modifier.weight(1f))
        }
        GroupHeader(tr("ระดับรวม 4 เดือนล่าสุด", "Overall level, last 4 months", "総合レベル（直近4か月）"))
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$total", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                    Text(tr(" จาก ${History.MAX_TOTAL} ระดับ", " of ${History.MAX_TOTAL}", " / ${History.MAX_TOTAL}"), fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
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
        GroupFooter(tr("นับจากแบบทดสอบในหน้าทักษะเท่านั้น ระดับลดได้ถ้าทดสอบระดับที่ถืออยู่ไม่ผ่านสองครั้งติด", "Counted only from tests on the Skills page. A level can drop if you fail the one you hold twice in a row", "スキルページのテストだけで数えます。今のレベルに2回続けて不合格だと下がることがあります"))

        GroupHeader(tr("เวลาฝึกต่อสัปดาห์", "Practice time per week", "週ごとの練習時間"))
        Group {
            Column(Modifier.padding(16.dp)) {
                val max = weeks.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                BarChart(weeks.map { it.second.toFloat() / max }, listOfNotNull(weeks.firstOrNull()?.first?.let(::shortDate), tr("สัปดาห์นี้", "This week", "今週")))
                val avg = if (weeks.isEmpty()) 0 else weeks.sumOf { it.second } / weeks.size
                Text(tr("สัปดาห์นี้ ${weeks.lastOrNull()?.second ?: 0} นาที · เฉลี่ย 12 สัปดาห์ $avg นาที", "This week ${weeks.lastOrNull()?.second ?: 0} min · 12-week average $avg min", "今週${weeks.lastOrNull()?.second ?: 0}分 · 12週平均${avg}分"), fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
            }
        }
        GroupFooter(tr("รวมข้อมูลที่นำเข้าจากมือถือ นับเฉพาะเวลาที่เล่นจริง", "Includes data imported from the phone, counting only time actually playing", "スマホからインポートしたデータを含む、実際に弾いた時間だけ"))

        GroupHeader(tr("หู: ถูกครั้งแรกต่อสัปดาห์", "Ear: right first time per week", "耳トレ：週ごとの1回目正解率"))
        Group {
            Column(Modifier.padding(16.dp)) {
                BarChart(earWeeks.map { it.second?.toFloat() }, listOfNotNull(earWeeks.firstOrNull()?.first?.let(::shortDate), tr("สัปดาห์นี้", "This week", "今週")),
                    steps = listOf(Tokens.Skill.Ear.fill, androidx.compose.ui.graphics.Color(0xFFEBCFAE), androidx.compose.ui.graphics.Color(0xFFE2BA8C), Tokens.Skill.Ear.ink))
                val heard = earWeeks.mapNotNull { it.second }
                Text(if (heard.isEmpty()) tr("ยังไม่ได้ฝึกหูในช่วงนี้", "No ear training in this period", "この期間は耳トレなし") else tr("ล่าสุด ${(heard.last() * 100).toInt()}% · ฝึก ${heard.size} สัปดาห์จาก 12", "Latest ${(heard.last() * 100).toInt()}% · practised ${heard.size} of 12 weeks", "最新 ${(heard.last() * 100).toInt()}% · 12週中${heard.size}週練習"),
                    fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
            }
        }
        GroupFooter(tr("ข้อยากขึ้นตามขั้นที่เปิด เปอร์เซ็นต์จึงอาจลดลงตอนเข้าขั้นใหม่ ดูระดับหูประกอบ", "Questions get harder as steps open, so the percentage can drop at a new step; look at the ear level too", "段階が開くと問題が難しくなるため、新しい段階で割合が下がることがあります。耳のレベルも見てください"))

        if (exams.isNotEmpty()) {
            GroupHeader(tr("ผลสอบเดิม (จากมือถือ)", "Earlier exam results (from the phone)", "以前の試験結果（スマホから）"))
            Group {
                Column(Modifier.padding(16.dp)) {
                    val recent = exams.takeLast(12)
                    BarChart(recent.map { it.total.toFloat() / Exams.MAX }, listOfNotNull(recent.firstOrNull()?.let { runCatching { shortDate(LocalDate.parse(it.day)) }.getOrNull() }, tr("ล่าสุด", "Latest", "最新")))
                    val last = exams.last()
                    Text("ล่าสุด ${Exams.grade(last.grade)?.name ?: ""} ${last.total}/${Exams.MAX} · ${Exams.band(last.total).title}",
                        fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                    // One bar per part of the latest exam, filled to its share of the part's marks: the weak part at a glance.
                    Column(Modifier.padding(top = 12.dp)) {
                        com.id.mimikopi.core.exam.ExamPart.entries.forEach { part ->
                            val m = last.mark(part)?.marks ?: 0
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(part.title, fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.width(140.dp))
                                androidx.compose.foundation.layout.Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Tokens.Palette.Line)) {
                                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(m.toFloat() / part.max).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Tokens.Palette.Accent))
                                }
                                Text("$m/${part.max}", fontSize = 13.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(start = 10.dp).width(48.dp))
                            }
                        }
                    }
                }
                exams.asReversed().take(10).forEach { e ->
                    GroupDivider()
                    GroupRow("${Exams.grade(e.grade)?.name ?: ""} · ${e.total}/${Exams.MAX}", Exams.band(e.total).title,
                        value = runCatching { shortDate(LocalDate.parse(e.day)) }.getOrDefault(e.day))
                }
            }
            GroupFooter(tr("คะแนนรวม 150 ผ่าน 100 ดี 120 ดีเยี่ยม 130 เทียบกันได้เฉพาะเกรดเดียวกัน", "Total 150: pass 100, merit 120, distinction 130. Compare only within the same grade", "合計150点：合格100、良120、優130。同じグレード内でのみ比較できます"))
        }

        GroupHeader(tr("สิ่งที่ควรแก้", "What to fix", "直すところ"))
        Group {
            if (fixes.isEmpty()) GroupRow(tr("ยังไม่พบรูปแบบที่ผิดซ้ำ", "No repeated mistakes found yet", "繰り返すミスはまだ見つかっていません"), tr("โปรแกรมดูจาก ${Mistakes.DAYS} วันล่าสุด ต้องเกิดซ้ำหลายรอบก่อนจะขึ้นตรงนี้", "The app looks at the last ${Mistakes.DAYS} days; a mistake must repeat several times before it shows here", "アプリは直近${Mistakes.DAYS}日を見ます。何度も繰り返したミスだけがここに出ます"))
            fixes.forEachIndexed { i, m ->
                if (i > 0) GroupDivider()
                GroupRow(m.title, "${m.detail}\n${m.fixText}", onClick = { openFix(app, m.fix) })
            }
        }
        GroupFooter(tr("ดูจาก ${Mistakes.DAYS} วันล่าสุด: การเล่นเพลงและแบบฝึก การอ่านโน้ต และฝึกหู", "From the last ${Mistakes.DAYS} days: playing pieces and drills, note reading and ear training", "直近${Mistakes.DAYS}日から：曲と練習、読譜、耳トレ"))
    }
}

/** "+12 นาที จากสัปดาห์ก่อน" with whether it went up, or null when nothing changed. */
private fun change(delta: Int, unit: String, since: String): Pair<String, Boolean>? =
    if (delta == 0) null else ("${if (delta > 0) "↑" else "↓"} ${kotlin.math.abs(delta)} $unit $since") to (delta > 0)

@Composable
private fun SummaryTile(title: String, value: String, delta: Pair<String, Boolean>?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Tokens.Palette.Group)
        .border(1.dp, Tokens.Palette.Hair, RoundedCornerShape(20.dp)).padding(18.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted)
        Text(value, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        Text(delta?.first ?: tr("เท่าเดิม", "Original", "原曲どおり"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = when (delta?.second) { true -> Tokens.Palette.Accent; false -> Tokens.Palette.Timing; null -> Tokens.Palette.InkMuted })
    }
}

