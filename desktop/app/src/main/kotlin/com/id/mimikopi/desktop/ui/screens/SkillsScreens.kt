package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.progress.SkillLevel
import com.id.mimikopi.core.progress.SkillStatus
import com.id.mimikopi.desktop.session.DrillSource
import com.id.mimikopi.desktop.session.EarArgs
import com.id.mimikopi.desktop.session.ReaderArgs
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.Tab
import com.id.mimikopi.desktop.ui.components.Group
import com.id.mimikopi.desktop.ui.components.GroupDivider
import com.id.mimikopi.desktop.ui.components.GroupFooter
import com.id.mimikopi.desktop.ui.components.GroupHeader
import com.id.mimikopi.desktop.ui.components.GroupRow
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.LevelBars
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.StepMark
import com.id.mimikopi.desktop.ui.theme.Tokens
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun ago(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (val d = ChronoUnit.DAYS.between(date, today)) {
    0L -> "วันนี้"
    1L -> "เมื่อวาน"
    else -> "$d วันก่อน"
}

private fun metricsText(r: CheckRecord): String = listOfNotNull(
    r.metrics.accuracy?.let { "ถูก ${SkillLevel.pct(it)}" },
    r.metrics.timing?.let { "ตรงจังหวะ ${SkillLevel.pct(it)}" },
    r.metrics.medianSec?.let { SkillLevel.sec(it) },
    r.metrics.count?.let { "เปลี่ยนคอร์ด $it ครั้ง" },
).joinToString(" · ")

private fun statusLine(s: SkillStatus): String {
    val tested = s.lastTested ?: return "ยังไม่ได้วัด"
    val parts = mutableListOf("ระดับ ${s.level} จาก ${s.maxLevel}", "ทดสอบ${ago(tested)}")
    if (s.due) parts += "ถึงเวลาทดสอบ"
    return parts.joinToString(" · ")
}

/** The page a level test runs on: drills in the drill runner, played tests in the score player, ear tests in ear training. */
fun checkScreen(key: String, run: CheckRun): Screen = when (run) {
    is CheckRun.DrillRun -> Screen.Drill(DrillSource.Check(key))
    is CheckRun.ScoreRun -> Screen.Reader(ReaderArgs(check = key))
    is CheckRun.EarRun -> Screen.Ear(EarArgs(check = key))
}

/** Where each skill stands, from fixed tests only, and the rooms to practise any part outside the day's plan. */
@Composable
fun SkillsScreen() {
    val app = LocalApp.current
    val checks by app.services.progress.checks.collectAsState()
    val today = LocalDate.now()
    val statuses = SkillId.entries.map { SkillBook.status(it, checks, today) }
    val overall = SkillBook.overall(checks, today)
    Page {
        LargeTitle("ทักษะ", overall?.let { "ภาพรวม: $it" } ?: "ยังไม่ได้วัด")
        Group {
            statuses.forEachIndexed { i, s ->
                if (i > 0) GroupDivider()
                GroupRow(s.skill.title, statusLine(s), onClick = { app.nav.open(Screen.Skill(s.skill)) },
                    trailing = { Row(verticalAlignment = Alignment.CenterVertically) { LevelBars(s.level, s.maxLevel, width = 7.dp); Text("  ›", color = Tokens.Palette.Dim) } })
            }
        }
        GroupFooter("ระดับได้จากแบบทดสอบสั้นที่มีเกณฑ์ตายตัวเท่านั้น เวลาที่ฝึกหรือขั้นที่ผ่านไม่ทำให้ระดับขึ้น " +
            "ถ้าทดสอบระดับที่ถืออยู่ไม่ผ่านสองครั้งติด ระดับจะลดลง ภาพรวมเทียบกับระดับในโปรแกรมนี้ ไม่ใช่เกรดสอบดนตรี")
        GroupHeader("ฝึกเอง")
        val sight = statuses.firstOrNull { it.skill == SkillId.SIGHT }?.level ?: 0
        Group {
            GroupRow("ขั้นหลัก", "บทเรียนทีละขั้น: อ่านโน้ต จังหวะ นิ้ว เพลงสั้น", onClick = { app.nav.home(Tab.LESSONS) })
            GroupDivider()
            GroupRow("ฝึกอ่านโน้ต", "ทีละตัว · เลือกกุญแจและช่วงเสียง", onClick = { app.nav.open(Screen.Drill(DrillSource.NotePractice)) })
            GroupDivider()
            GroupRow("ทวนโน้ตและคีย์", "ตัวที่ถึงกำหนดทวน", onClick = { app.nav.open(Screen.Drill(DrillSource.Review)) })
            GroupDivider()
            GroupRow("อ่านโน้ตใหม่", "โน้ตที่ไม่เคยเห็น เล่นครั้งแรก", onClick = { app.nav.open(Screen.Reader(ReaderArgs(newReading = sight.coerceIn(1, 5)))) })
            GroupDivider()
            GroupRow("ฝึกหู", "โน้ต ทำนอง คอร์ด ทางเดินคอร์ด", onClick = { app.nav.home(Tab.EAR) })
            GroupDivider()
            GroupRow("คอร์ด", "กดคอร์ดตามชื่อ · เปลี่ยนคอร์ดตามเวลา", onClick = { app.nav.open(Screen.Drill(DrillSource.Chords(false))) })
        }
        GroupFooter("ฝึกส่วนไหนก็นับเวลาเข้าแผนวันนี้")
    }
}

/** One skill: the level held, the next test with its fixed criteria, where to practise it, every test taken. */
@Composable
fun SkillDetailScreen(skill: SkillId) {
    val app = LocalApp.current
    val checks by app.services.progress.checks.collectAsState()
    val today = LocalDate.now()
    val s = SkillBook.status(skill, checks, today)
    val levels = SkillBook.levels(skill)
    Page {
        LargeTitle(skill.title, skill.about, onBack = app.nav::back)
        Group {
            Column(Modifier.padding(20.dp)) {
                Text("ระดับ", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${s.level}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                    Text(" จาก ${s.maxLevel}", fontSize = 17.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                LevelBars(s.level, s.maxLevel, width = 22.dp)
                Text(if (s.level > 0) "ทำได้แล้ว: ${levels[s.level - 1].canDo}" else if (s.lastTested == null) "ยังไม่ได้วัด เริ่มจากทดสอบระดับ 1" else "ยังไม่ผ่านระดับ 1",
                    fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp))
                if (s.dropped) Text("ระดับลดลง เพราะทดสอบระดับที่เคยผ่านไม่ผ่านสองครั้งติด", fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                s.lastTested?.let { Text("ทดสอบล่าสุด${ago(it, today)}", fontSize = 13.sp, color = Tokens.Palette.InkMuted) }
            }
        }
        GroupHeader("ทดสอบถัดไป")
        Group {
            Column(Modifier.padding(20.dp)) {
                val next = s.nextTest?.let { SkillBook.level(skill, it) }
                if (next != null) {
                    Text(if (next.level == s.level) "ยืนยันระดับ ${next.level}" else "ระดับ ${next.level}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(next.canDo, fontSize = 15.sp, color = Tokens.Palette.Ink)
                    Text("เกณฑ์ผ่าน: ${next.criteriaText()}", fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(if (s.lockedToday) "พรุ่งนี้ลองใหม่ได้" else "ทดสอบระดับ ${next.level}",
                        onClick = { app.nav.open(checkScreen(SkillBook.key(skill, next.level), next.run)) }, enabled = !s.lockedToday)
                } else {
                    Text("ถือระดับสูงสุดอยู่", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("ทดสอบซ้ำได้เมื่อครบ ${SkillBook.HOLD_DAYS} วันนับจากครั้งล่าสุด", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
                }
            }
        }
        GroupFooter("ทดสอบเมื่อพร้อม ไม่ผ่านระดับที่สูงกว่าไม่เสียอะไร ลองได้วันละ 2 ครั้งต่อระดับ ระหว่างทดสอบไม่มีตัวช่วย")
        GroupHeader("ผลทดสอบ")
        val mine = checks.filter { it.skill == skill }
        Group {
            if (mine.isEmpty()) GroupRow("ยังไม่เคยทดสอบ", "ผลทุกครั้งจะอยู่ตรงนี้ ทั้งที่ผ่านและไม่ผ่าน")
            mine.takeLast(12).asReversed().forEachIndexed { i, r ->
                if (i > 0) GroupDivider()
                GroupRow("ระดับ ${r.level} · ${if (r.passed) "ผ่าน" else "ไม่ผ่าน"}", metricsText(r).ifEmpty { null },
                    value = runCatching { ago(LocalDate.parse(r.date), today) }.getOrNull(),
                    titleColor = if (r.passed) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
        GroupHeader("ทุกระดับ")
        Group {
            levels.forEachIndexed { i, l ->
                if (i > 0) GroupDivider(52.dp)
                GroupRow("ระดับ ${l.level} · ${l.canDo}", "เกณฑ์: ${l.criteriaText()}",
                    leading = { StepMark(l.level <= s.level, l.level == s.nextTest && l.level > s.level) },
                    titleColor = if (l.level <= s.level) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
    }
}
