package com.id.mimikopi.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.session.DailyPlan
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupFooter
import com.id.mimikopi.ui.components.GroupHeader
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.LargeTitle
import com.id.mimikopi.ui.components.MidiPill
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.components.Segmented
import com.id.mimikopi.ui.components.SecondaryButton
import com.id.mimikopi.ui.components.StepMark
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.progress.fixTarget
import com.id.mimikopi.ui.theme.Tokens

/**
 * The home page: what to practise next in large type with one button, today's plan as a short list that ticks itself
 * off, the time chosen, the last two weeks as plain squares, and where the skills stand. Nothing to decide but "start".
 */
@Composable
fun TodayScreen(onOpen: (Open) -> Unit, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit, vm: TodayViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showPlan by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) {
            LargeTitle("วันนี้", ui.dateText) {
                MidiPill(onOpenMidi)
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "ตั้งค่า", tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
                }
            }
            if (!ui.loaded) return@Column
            Column(Modifier.padding(horizontal = PageMargin)) {
                Spacer(Modifier.height(16.dp))
                com.id.mimikopi.ui.components.PageCard(title = "Heroic Polonaise · เพลงเป้าหมายเดียว", onClick = { onOpen(Open.Goal) }) {
                    Text(ui.goalSong ?: "เริ่มท่อนหลักทีละ 2 ห้อง", color = Tokens.Palette.Ink)
                    Text("เปิดแผนและเริ่มช่วงถัดไป →", color = Tokens.Palette.Ink)
                }
                Spacer(Modifier.height(16.dp))
                com.id.mimikopi.ui.components.PageCard(title = "เพลงส่วนตัว · ลองเล่นจากความจำ", onClick = { onOpen(Open.PrivateSongs) }) {
                    Text("แบ่งช่วงจาก YouTube แล้วลองเองก่อนดูคำตอบ · ข้อมูลอยู่ในเครื่อง", color = Tokens.Palette.Ink)
                }
                Spacer(Modifier.height(16.dp))
                Text("เวลาเสริมพื้นฐานวันนี้", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.Ink)
                Spacer(Modifier.height(8.dp))
                Segmented(DailyPlan.CHOICES, ui.minutesChosen, { "$it" }, vm::choose)
                Spacer(Modifier.height(16.dp))
                Hero(ui, onStart = { b -> vm.open(b, onOpen) }, onPiano = { onOpen(Open.Piano) })

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ทำแล้ว ${ui.blocks.count { it.done }} จาก ${ui.blocks.size} ส่วน", fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showPlan = !showPlan }) { Text(if (showPlan) "ซ่อนแผน" else "ดูแผนทั้งหมด") }
                }
                if (showPlan) Group {
                    ui.blocks.forEachIndexed { i, b ->
                        if (i > 0) GroupDivider(indent = 52.dp)
                        GroupRow(
                            title = b.title, subtitle = b.detail + "\n" + when {
                                b.block.kind == com.id.mimikopi.core.session.BlockKind.CHECK -> if (b.done) "บันทึกผลทดสอบวันนี้แล้ว" else "จบแบบทดสอบเพื่อบันทึกผลจริง"
                                b.block.kind == com.id.mimikopi.core.session.BlockKind.REVIEW && b.done && b.playedSeconds == 0 -> "ไม่มีข้อทวนค้าง"
                                else -> "เล่นจริง ${practiceTime(b.playedSeconds)}"
                            },
                            leading = { StepMark(done = b.done, current = b == ui.next) },
                            titleColor = if (b.done) Tokens.Palette.InkMuted else Tokens.Palette.Ink,
                            onClick = { vm.open(b.block, onOpen) },
                        )
                    }
                }
                if (showPlan) GroupFooter("ทำเครื่องหมายเมื่อเล่นจริงถึง 60% ของเวลาที่จัดไว้ หรือทำแบบทดสอบ/ทวนเสร็จ การทำครบแผนไม่ใช่การผ่านระดับทักษะ")

                if (ui.fixes.isNotEmpty()) {
                    GroupHeader("สิ่งที่ควรแก้")
                    Group {
                        ui.fixes.forEachIndexed { i, m ->
                            if (i > 0) GroupDivider()
                            GroupRow(m.title, subtitle = m.fixText, onClick = { onOpen(fixTarget(m.fix)) })
                        }
                    }
                    GroupFooter("จากการเล่นและฝึก ${com.id.mimikopi.core.progress.Mistakes.DAYS} วันล่าสุด รายละเอียดอยู่ในหน้าพัฒนาการ")
                }

                GroupHeader("14 วันที่ผ่านมา")
                Group {
                    Column(Modifier.padding(16.dp)) {
                        DayStrip(ui.strip)
                        Text(
                            "สัปดาห์นี้ฝึก ${ui.weekDays} วัน · ${ui.weekMinutes} นาที" + if (ui.lastWeekMinutes > 0) " (สัปดาห์ก่อน ${ui.lastWeekMinutes} นาที)" else "",
                            fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
                GroupFooter("นับเฉพาะเวลาที่เล่นจริง ไม่นับเวลาที่เปิดแอพทิ้งไว้")

                GroupHeader("ระดับทักษะ")
                Group {
                    GroupRow(
                        "ภาพรวม",
                        subtitle = ui.overall?.let { "$it · วัดจากแบบทดสอบเท่านั้น" }
                            ?: "ยังไม่ได้วัด แผนแต่ละวันจะพาทดสอบทีละทักษะ (เหลือ ${ui.untested} ทักษะ)",
                        onClick = { onOpen(Open.Skills) },
                    )
                    ui.weakest?.let { w ->
                        GroupDivider()
                        GroupRow("เน้นวันนี้: ${w.skill.title}", subtitle = "ระดับ ${w.level} จาก ${w.maxLevel} · ทักษะที่ควรเน้นจากผลทดสอบล่าสุด", onClick = { onOpen(Open.Skill(w.skill)) })
                    }
                    GroupDivider()
                    GroupRow("พัฒนาการ", subtitle = "ระดับย้อนหลัง เวลาฝึกต่อสัปดาห์ หู และสิ่งที่ควรแก้", onClick = { onOpen(Open.Progress) })
                    GroupDivider()
                    GroupRow(
                        "เป้าหมาย: Heroic Polonaise",
                        subtitle = ui.goalSong ?: "แผนฝึกท่อนหลักและผลจริงรายช่วง",
                        onClick = { onOpen(Open.Goal) },
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** The next part in large type with the one button; when the plan is done, a quiet close. */
@Composable
private fun Hero(ui: TodayUi, onStart: (com.id.mimikopi.core.session.PlanBlock) -> Unit, onPiano: () -> Unit) {
    Group {
        Column(Modifier.padding(20.dp)) {
            val next = ui.next
            if (next != null) {
                Text(if (ui.blocks.none { it.done }) "เริ่มด้วย" else "ต่อไป", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.InkMuted)
                Text(next.title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 2.dp))
                Text(next.detail, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
                Text(next.block.kind.why, fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(18.dp))
                PrimaryButton("เริ่ม${next.title}", onClick = { onStart(next.block) })
            } else {
                Text("ทำครบทุกส่วนแล้ว", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text("วันนี้เล่นจริง ${practiceTime(ui.secondsDone)} พักได้ หรือเล่นต่ออิสระ ระดับทักษะเปลี่ยนเมื่อทำแบบทดสอบผ่านเท่านั้น", fontSize = 15.sp, lineHeight = 20.sp,
                    color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(16.dp))
                SecondaryButton("เล่นเปียโนอิสระ", onPiano)
            }
            Spacer(Modifier.height(18.dp))
            // Minutes practised against minutes planned: a plain bar, no reward.
            val f = if (ui.minutesPlanned == 0) 0f else (ui.secondsDone.toFloat() / (ui.minutesPlanned * 60)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Line)) {
                Box(Modifier.fillMaxWidth(f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Ink))
            }
            Text("เล่นจริง ${practiceTime(ui.secondsDone)} · แผน ${ui.minutesPlanned} นาที", fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** One square per day: empty, or filled darker with more minutes (up to an hour). */
@Composable
private fun DayStrip(days: List<Pair<java.time.LocalDate, Int>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        days.forEach { (d, m) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val f = (m / 60f).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(5.dp))
                        .background(if (m == 0) Tokens.Palette.Line else Tokens.Palette.Ink.copy(alpha = 0.35f + 0.65f * f)),
                )
                Text("${d.dayOfMonth}", fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

private fun practiceTime(seconds: Int): String = "${seconds / 60} นาที ${seconds % 60} วินาที"
