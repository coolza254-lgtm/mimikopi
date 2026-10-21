package com.id.mimikopi.desktop.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.playalong.HeroicPlan
import com.id.mimikopi.desktop.session.DrillSource
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
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.theme.Tokens
import java.time.LocalDate

/** What to practise now: the next lesson step, confirmations due, reviews due and the next Heroic target. */
@Composable
fun TodayScreen() {
    val app = LocalApp.current
    val s = app.services
    val records by s.learning.records.collectAsState()
    val log by s.progress.log.collectAsState()
    val pieces by s.pieces.progress.collectAsState()
    val today = LocalDate.now()
    remember(today) { s.learning.reopenFailedConfirmations(today) }
    val step = ProgressRules.nextToLearn(s.learning.lessons, records)
    val confirm = s.learning.confirmations(s.learning.lessons, today)
    val due = remember(records) { s.learning.dueCount() }
    val heroic = HeroicPlan.next(pieces)
    val seconds = log.firstOrNull { it.date == today.toString() }?.total ?: 0
    Page {
        LargeTitle("วันนี้", if (seconds > 0) "ฝึกแล้ววันนี้ ${seconds / 60} นาที ${seconds % 60} วินาที (นับเฉพาะรอบที่มีการเล่น)" else "ยังไม่มีการฝึกวันนี้")
        if (confirm.isNotEmpty()) {
            GroupHeader("ยืนยันข้ามวัน")
            Group {
                confirm.forEachIndexed { i, l ->
                    if (i > 0) GroupDivider()
                    GroupRow("${l.id} ${l.title}", "ผ่านเกณฑ์แล้วในวันก่อน · ทำอีกครั้งวันนี้เพื่อยืนยันว่าจำได้", onClick = { app.nav.open(Screen.Lesson(l.id)) })
                }
            }
        }
        GroupHeader("ฝึกต่อ")
        Group {
            if (step != null) GroupRow("บท ${step.id} ${step.title}", step.goal, onClick = { app.nav.open(Screen.Lesson(step.id)) })
            else GroupRow("ผ่านทุกบทแล้ว", "ทบทวนหรือฝึกเพลงต่อ")
            GroupDivider()
            GroupRow("ทบทวนการอ่าน", if (due > 0) "ถึงกำหนด $due รายการ" else "ไม่มีรายการถึงกำหนดตอนนี้",
                onClick = if (due > 0) ({ app.nav.open(Screen.Drill(DrillSource.Review)) }) else null)
            GroupDivider()
            GroupRow("ฝึกอ่านโน้ต", "เลือกกุญแจ ช่วงเสียง และเครื่องหมายเอง · ไม่เปลี่ยนระดับทักษะ", onClick = { app.nav.open(Screen.Drill(DrillSource.NotePractice)) })
        }
        GroupHeader("Heroic Polonaise")
        Group {
            if (heroic != null) GroupRow(heroic.label, heroic.goal, onClick = { app.nav.open(Screen.Reader(ReaderArgs(pieceId = heroic.piece, target = heroic.id))) })
            else GroupRow("ทำครบแผนท่อนหลักแล้ว", "ดูผลรายช่วงที่หน้าเพลง", onClick = { app.nav.home(Tab.SONGS) })
        }
        GroupFooter("ไม่มีแต้ม รางวัล หรือการแจ้งเตือน · ผลมาจากการเล่นจริงเท่านั้น")
        Text("เปียโน MIDI: เสียบสาย USB แล้วรอสักครู่ โปรแกรมเชื่อมต่อเอง · ไม่มีเปียโน: ใช้เมาส์หรือแป้นพิมพ์ได้ทุกหน้า",
            fontSize = 13.sp, color = Tokens.Palette.InkMuted, fontWeight = FontWeight.Normal)
    }
}
