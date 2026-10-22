package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.progress.Mistakes
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.core.session.DailyPlan
import com.id.mimikopi.core.session.PlanBlock
import com.id.mimikopi.core.session.PlanContent
import com.id.mimikopi.core.session.PracticeLog
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.learning.ProgressStore
import com.id.mimikopi.desktop.session.DrillSource
import com.id.mimikopi.desktop.session.EarArgs
import com.id.mimikopi.desktop.session.ReaderArgs
import com.id.mimikopi.desktop.ui.AppContext
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.Tab
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.theme.Tokens
import java.time.LocalDate

private val DAYS = listOf("จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์", "อาทิตย์")
private val MONTHS_LONG = listOf("มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม")
private const val DEFAULT_MINUTES = 20

/** Today's plan: stored once a day for the minutes chosen (the same rules as the phone), rebuilt when it no longer fits. */
private fun todayPlan(s: Services, minutes: Int, today: LocalDate): List<PlanBlock> {
    val stored = DailyPlan.parse(s.db.get(ProgressStore.PLAN))
    if (stored != null && stored.date == today.toString() && stored.minutes == minutes) return stored.blocks
    val input = PlanContent.input(minutes, s.learning.lessons, s.learning.records.value, s.progress.checks.value, s.learning.readingDueItems().size, today)
    return DailyPlan.build(input).also { s.db.put(ProgressStore.PLAN, DailyPlan.encode(DailyPlan.Stored(today.toString(), minutes, it))) }
}

/** What a part of the plan opens on the computer. */
private fun open(app: AppContext, b: PlanBlock) {
    val s = app.services
    when (b.kind) {
        BlockKind.WARMUP, BlockKind.REVIEW -> app.nav.open(Screen.Drill(DrillSource.Review))
        BlockKind.CHECK -> SkillBook.parseKey(b.target)?.let { app.nav.open(checkScreen(b.target!!, it.run)) }
        BlockKind.LADDER -> {
            val today = LocalDate.now()
            val step = ProgressRules.awaitingConfirmation(s.learning.lessons, s.learning.records.value, today).firstOrNull()
                ?: ProgressRules.nextToLearn(s.learning.lessons, s.learning.records.value)
            if (step != null) app.nav.open(Screen.Lesson(step.id)) else app.nav.home(Tab.LESSONS)
        }
        BlockKind.SIGHT -> app.nav.open(Screen.Reader(ReaderArgs(newReading = b.target?.toIntOrNull() ?: 1)))
        BlockKind.CHORDS -> {
            val level = SkillBook.status(SkillId.CHORDS, s.progress.checks.value, LocalDate.now()).level
            s.db.put(ProgressStore.CHORDS_TODAY, com.id.mimikopi.core.exercise.ChordPractice.encode(PlanContent.chords(level)))
            app.nav.open(Screen.Drill(DrillSource.Chords(true)))
        }
        BlockKind.EAR -> app.nav.open(Screen.Ear(EarArgs()))
        BlockKind.SONG -> app.nav.home(Tab.SONGS)
    }
}

/** One line under a part's title: what it holds today. */
private fun detail(s: Services, b: PlanBlock): String = when (b.kind) {
    BlockKind.WARMUP, BlockKind.REVIEW -> "ทีละตัว ช้า ๆ ให้ตาและมือเข้ากัน"
    BlockKind.CHECK -> SkillBook.parseKey(b.target)?.let { "${it.skill.title} ระดับ ${it.level} · เกณฑ์ตายตัว" } ?: ""
    BlockKind.LADDER -> (ProgressRules.awaitingConfirmation(s.learning.lessons, s.learning.records.value, LocalDate.now()).firstOrNull()?.let { "ยืนยัน ${it.id} ${it.title}" }
        ?: ProgressRules.nextToLearn(s.learning.lessons, s.learning.records.value)?.let { "${it.id} ${it.title}" } ?: "ทบทวนขั้นที่ผ่านแล้ว")
    BlockKind.SIGHT -> "โน้ตที่ไม่เคยเห็น ระดับ ${b.target} · เล่นครั้งเดียวแล้วไปต่อ"
    BlockKind.CHORDS -> PlanContent.chords(SkillBook.status(SkillId.CHORDS, s.progress.checks.value, LocalDate.now()).level).summary()
    BlockKind.EAR -> EarProgress.currentStage(EarProgress.parse(s.db.get(EarProgress.KEY))).let { "ขั้น ${it.n} ${it.title}" }
    BlockKind.SONG -> "เพลงจากคลัง ฝึกแบบชีตเต็มแผ่น"
}

/**
 * Today: one card with the day's plan, easy to hard, done parts ticked from what was really played, one start button
 * on the part to do now; the time for today can be changed. At the side, the last seven days and what still mixes up.
 */
@Composable
fun TodayScreen() {
    val app = LocalApp.current
    val s = app.services
    val log by s.progress.log.collectAsState()
    val checks by s.progress.checks.collectAsState()
    val records by s.learning.records.collectAsState()
    val mistakes by s.progress.mistakes.collectAsState()
    val today = LocalDate.now()
    remember(today) { s.learning.reopenFailedConfirmations(today) }
    var minutes by remember { mutableIntStateOf(s.db.get(ProgressStore.MINUTES)?.toIntOrNull()?.takeIf { it in DailyPlan.CHOICES } ?: DEFAULT_MINUTES) }
    val plan = remember(minutes, today, records, checks) { todayPlan(s, minutes, today) }
    val day = PracticeLog.on(log, today)
    val reviewDue = remember(records) { s.learning.readingDueItems().size }
    val done = plan.map { b ->
        val takenToday = b.target?.let { key -> SkillBook.parseKey(key)?.let { l -> checks.any { it.skill == l.skill && it.level == l.level && it.date == today.toString() } } } ?: false
        DailyPlan.isDone(b, day, takenToday, reviewDue)
    }
    val current = done.indexOfFirst { !it }
    val week = PracticeLog.lastDays(log, today, 7)
    val ear = remember(log) { EarProgress.parse(s.db.get(EarProgress.KEY)) }
    val fixes = remember(mistakes, ear) { Mistakes.summarize(mistakes, ear, today).take(2) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
        val wide = maxWidth > 1000.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 1040.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column {
                        Text("วัน${DAYS[today.dayOfWeek.value - 1]} ${today.dayOfMonth} ${MONTHS_LONG[today.monthValue - 1]}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
                        Text(if (current < 0) "ฝึกครบแผนวันนี้แล้ว" else "วันนี้ฝึก $minutes นาที", fontSize = 38.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                    }
                    PlanCard(plan, done, current) { b -> PlanRow(s, b, done[plan.indexOf(b)], plan.indexOf(b) == current, plan.indexOf(b) + 1) { open(app, b) } }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("เวลาวันนี้", fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                        DailyPlan.CHOICES.forEach { m ->
                            val on = m == minutes
                            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Tokens.Palette.AccentSoft else Tokens.Palette.Paper)
                                .border(1.dp, if (on) Tokens.Palette.Accent else Tokens.Palette.Line, RoundedCornerShape(14.dp))
                                .clickable { minutes = m; s.db.put(ProgressStore.MINUTES, m.toString()) }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Text("$m นาที", fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = Tokens.Palette.Ink)
                            }
                        }
                    }
                    if (!wide) Summary(week, day.total, fixes)
                }
                if (wide) Column(Modifier.width(300.dp).padding(top = 76.dp)) { Summary(week, day.total, fixes) }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: List<PlanBlock>, done: List<Boolean>, current: Int, row: @Composable (PlanBlock) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tokens.Palette.Group)
        .border(1.dp, Tokens.Palette.Hair, RoundedCornerShape(24.dp)).padding(8.dp)) {
        plan.forEach { row(it) }
    }
}

@Composable
private fun PlanRow(s: Services, b: PlanBlock, done: Boolean, now: Boolean, n: Int, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (now) Tokens.Palette.AccentSoft else Tokens.Palette.Group)
            .clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = if (now) 20.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(when { done -> Tokens.Palette.CorrectSoft; now -> Tokens.Palette.Accent; else -> Tokens.Palette.Group })
                .then(if (!done && !now) Modifier.border(1.5.dp, Tokens.Palette.Line, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else "$n", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = when { done -> Tokens.Palette.Correct; now -> Tokens.Palette.OnAccent; else -> Tokens.Palette.InkMuted })
        }
        Column(Modifier.weight(1f)) {
            if (now) Text("${b.kind.title} · ${b.minutes} นาที", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Accent)
            Text(if (now) detail(s, b) else b.kind.title, fontSize = if (now) 21.sp else 17.sp, lineHeight = if (now) 29.sp else 24.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.SemiBold,
                color = if (done) Tokens.Palette.InkMuted else Tokens.Palette.Ink, textDecoration = if (done) TextDecoration.LineThrough else null)
            Text(if (now) b.kind.why else detail(s, b), fontSize = 14.sp, color = Tokens.Palette.InkMuted, maxLines = 1)
        }
        if (now) PrimaryButton("เริ่ม", onOpen, Modifier.width(140.dp))
        else Text(if (done) "เสร็จ" else "${b.minutes} นาที", fontSize = 15.sp, fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
            color = if (done) Tokens.Palette.Correct else Tokens.Palette.InkMuted)
    }
}

@Composable
private fun Summary(week: List<Pair<LocalDate, Int>>, secondsToday: Int, fixes: List<com.id.mimikopi.core.progress.Mistake>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SideCard {
            Text("7 วันนี้", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted)
            val max = week.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, (_, m) ->
                    Box(Modifier.weight(1f).fillMaxHeight(if (m == 0) 0.08f else (m.toFloat() / max).coerceIn(0.12f, 1f)).clip(RoundedCornerShape(6.dp))
                        .background(when { m == 0 -> Tokens.Palette.Hair; i == week.lastIndex -> Tokens.Palette.Accent; else -> Tokens.Palette.Pink.copy(alpha = 0.45f) }))
                }
            }
            Text("ฝึก ${week.count { it.second > 0 }} วัน · ${week.sumOf { it.second }} นาที · วันนี้ ${secondsToday / 60} นาที", fontSize = 14.sp, color = Tokens.Palette.Ink)
        }
        SideCard {
            Text("ยังสับสนบ่อย", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted)
            if (fixes.isEmpty()) Text("ยังไม่พบจุดที่ผิดซ้ำ", fontSize = 15.sp, color = Tokens.Palette.Ink)
            fixes.forEach { m ->
                Column {
                    Text(m.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink)
                    Text(m.fixText, fontSize = 13.sp, color = Tokens.Palette.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun SideCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tokens.Palette.Group)
        .border(1.dp, Tokens.Palette.Hair, RoundedCornerShape(24.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
