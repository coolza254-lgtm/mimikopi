package com.id.mimikopi.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.core.session.DailyPlan
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupHeader
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.Pill
import com.id.mimikopi.ui.components.SkillIcon
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.progress.fixTarget
import com.id.mimikopi.ui.theme.Tokens

/**
 * Today (Design J): no page header or date (the user asked for none). Today's basics as pastel cards in a grid, ticked from real practice, the next one ringed ("ต่อไป"); the minutes
 * chosen on a segmented rail; this week as circles that grow with the minutes played; what to fix, when there is any.
 */
@Composable
fun TodayScreen(onOpen: (Open) -> Unit, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit, vm: TodayViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = PageMargin)) {
            if (!ui.loaded) return@Column
            // No page title or date (the user asked for none): the day's basics come first.
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 40.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("พื้นฐานวันนี้", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                MinutesRail(DailyPlan.CHOICES, ui.minutesChosen, vm::choose)
            }
            BlockGrid(ui, onOpen = { b -> vm.open(b, onOpen) })
            if (ui.next == null && !ui.allDone) Text(
                "ส่วนที่เหลือตัดสินจังหวะ ต้องต่อเปียโนด้วยสาย ทำบนคอมแล้วนับในแผนเดียวกันหลังซิงค์",
                fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(start = 4.dp, top = 12.dp),
            )

            Spacer(Modifier.height(24.dp))
            WeekCard(ui)

            if (ui.fixes.isNotEmpty()) {
                GroupHeader("สิ่งที่ควรแก้")
                Group {
                    ui.fixes.forEachIndexed { i, m ->
                        if (i > 0) GroupDivider()
                        GroupRow(m.title, subtitle = m.fixText, onClick = { onOpen(fixTarget(m.fix)) })
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The minutes for today on a track rail: numbers only, the chosen one in a white capsule with "นาที". */
@Composable
private fun MinutesRail(choices: List<Int>, chosen: Int, onChoose: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Tokens.Palette.Track).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        choices.forEach { m ->
            val on = m == chosen
            val shape = RoundedCornerShape(15.dp)
            Box(
                Modifier.height(30.dp)
                    .then(if (on) Modifier.shadow(2.dp, shape, ambientColor = Tokens.Elevation.tint.copy(alpha = 0.12f), spotColor = Tokens.Elevation.tint.copy(alpha = 0.12f))
                        .clip(shape).background(Tokens.Palette.Group) else Modifier.clip(shape))
                    .clickable { onChoose(m) }.padding(horizontal = if (on) 12.dp else 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (on) "$m นาที" else "$m", fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
    }
}

/** A plan part's pastel and icon (fills only; never right or wrong). */
private fun look(kind: BlockKind): Pair<Tokens.SkillColor, Int> = when (kind) {
    BlockKind.WARMUP -> Tokens.Skill.Rhythm to R.drawable.ic_skill_hand
    BlockKind.LADDER -> Tokens.Skill.Rhythm to R.drawable.ic_skill_note
    BlockKind.REVIEW -> Tokens.Skill.Keys to R.drawable.ic_skill_staff
    BlockKind.SIGHT -> Tokens.Skill.Reading to R.drawable.ic_skill_staff
    BlockKind.CHECK -> Tokens.Skill.Reading to R.drawable.ic_nav_skills
    BlockKind.EAR -> Tokens.Skill.Ear to R.drawable.ic_skill_ear
    BlockKind.CHORDS -> Tokens.Skill.Chords to R.drawable.ic_nav_piano
    BlockKind.SOLO -> Tokens.Skill.Keys to R.drawable.ic_skill_hand
    BlockKind.SONG -> Tokens.Skill.Keys to R.drawable.ic_nav_songs
}

/** Today's parts two to a row, in plan order. */
@Composable
private fun BlockGrid(ui: TodayUi, onOpen: (com.id.mimikopi.core.session.PlanBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ui.blocks.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEach { b -> BlockCard(b, now = b == ui.next, onOpen = { onOpen(b.block) }, modifier = Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One part: its pastel, a white round icon, done (a tick) or next (ringed, "ต่อไป", minutes played of planned). */
@Composable
private fun BlockCard(b: BlockUi, now: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val (color, icon) = look(b.block.kind)
    val shape = RoundedCornerShape(Tokens.Radius.cardSmall)
    val played = b.playedSeconds / 60
    Column(
        modifier.clip(shape).background(color.fill)
            .then(if (now) Modifier.border(2.dp, Tokens.Palette.Accent, shape) else Modifier)
            .clickable(onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkillIcon(icon, color)
            Spacer(Modifier.weight(1f))
            when {
                b.done -> Box(Modifier.size(24.dp).clip(CircleShape).background(color.ink), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check_bold), contentDescription = "เสร็จแล้ว", tint = Tokens.Palette.OnAccent, modifier = Modifier.size(13.dp))
                }
                now -> Pill("ต่อไป", filled = true)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(b.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink)
            if (now && !b.done) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Tokens.Palette.Group)) {
                    Box(Modifier.fillMaxWidth((played.toFloat() / b.block.minutes.coerceAtLeast(1)).coerceIn(0f, 1f)).height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(color.ink))
                }
            }
            Text(
                when {
                    b.done -> "เสร็จแล้ว · ${b.block.minutes} นาที"
                    b.needsCable -> com.id.mimikopi.core.input.TimingInput.NEED_CABLE_SHORT
                    now -> "$played / ${b.block.minutes} นาที"
                    else -> "${b.block.minutes} นาที"
                },
                fontSize = 12.sp, color = Tokens.Palette.InkMuted,
            )
        }
    }
}

/** This week, Monday to Sunday: a circle per day that grows with the minutes (today in the accent, days to come dashed). */
@Composable
private fun WeekCard(ui: TodayUi) {
    val today = java.time.LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val minutes = ui.strip.toMap()
    val names = listOf("จ", "อ", "พ", "พฤ", "ศ", "ส", "อา")
    com.id.mimikopi.ui.components.PageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("สัปดาห์นี้", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
            Text("${ui.weekDays} วัน · ${ui.weekMinutes} นาที", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            for (i in 0 until 7) {
                val day = monday.plusDays(i.toLong())
                val m = minutes[day] ?: 0
                val isToday = day == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (day.isAfter(today)) {
                            Box(Modifier.size(14.dp).border(1.5.dp, Tokens.Palette.Dim, CircleShape))
                        } else {
                            val d = (12 + (m.coerceAtMost(60) / 60f) * 22).dp
                            Box(Modifier.size(if (m == 0) 10.dp else d).clip(CircleShape).background(
                                when { isToday -> Tokens.Palette.Accent; m >= 30 -> Tokens.Palette.AccentMidStrong; m > 0 -> Tokens.Palette.AccentMid; else -> Tokens.Palette.Track }))
                        }
                    }
                    Text(if (isToday) "วันนี้" else names[i], fontSize = 12.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                }
            }
        }
    }
}
