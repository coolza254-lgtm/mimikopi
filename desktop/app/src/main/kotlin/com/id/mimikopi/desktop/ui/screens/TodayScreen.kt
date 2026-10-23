package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
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
import com.id.mimikopi.desktop.ui.components.softShadow
import com.id.mimikopi.desktop.ui.components.Segmented
import androidx.compose.runtime.mutableStateOf
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

private val DAYS = listOf(tr("จันทร์", "Monday", "月曜日"), tr("อังคาร", "Tuesday", "火曜日"), tr("พุธ", "Wednesday", "水曜日"), tr("พฤหัสบดี", "Thursday", "木曜日"), tr("ศุกร์", "Friday", "金曜日"), tr("เสาร์", "Saturday", "土曜日"), tr("อาทิตย์", "Sunday", "日曜日"))
private val MONTHS_LONG = listOf(tr("มกราคม", "January", "1月"), tr("กุมภาพันธ์", "February", "2月"), tr("มีนาคม", "March", "3月"), tr("เมษายน", "April", "4月"), tr("พฤษภาคม", "May", "5月"), tr("มิถุนายน", "June", "6月"), tr("กรกฎาคม", "July", "7月"), tr("สิงหาคม", "August", "8月"), tr("กันยายน", "September", "9月"), tr("ตุลาคม", "October", "10月"), tr("พฤศจิกายน", "November", "11月"), tr("ธันวาคม", "December", "12月"))
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
        BlockKind.SOLO -> app.nav.open(Screen.Reader(ReaderArgs(solo = b.target?.toIntOrNull() ?: 1)))
        BlockKind.EAR -> app.nav.open(Screen.Ear(EarArgs()))
        BlockKind.SONG -> app.nav.home(Tab.SONGS)
    }
}

/** One line under a part's title: what it holds today. */
private fun detail(s: Services, b: PlanBlock): String = when (b.kind) {
    BlockKind.WARMUP, BlockKind.REVIEW -> tr("ทีละตัว ช้า ๆ ให้ตาและมือเข้ากัน", "One at a time, slowly, eyes and hands together", "1つずつゆっくり、目と手を合わせて")
    BlockKind.CHECK -> SkillBook.parseKey(b.target)?.let { tr("${it.skill.title} ระดับ ${it.level} · เกณฑ์ตายตัว", "${it.skill.title} level ${it.level} · fixed criteria", "${it.skill.title} レベル${it.level} · 固定の基準") } ?: ""
    BlockKind.LADDER -> (ProgressRules.awaitingConfirmation(s.learning.lessons, s.learning.records.value, LocalDate.now()).firstOrNull()?.let { tr("ยืนยัน ${it.id} ${it.title}", "Confirm ${it.title}", "${it.title}を確認") }
        ?: ProgressRules.nextToLearn(s.learning.lessons, s.learning.records.value)?.let { "${it.id} ${it.title}" } ?: tr("ทบทวนขั้นที่ผ่านแล้ว", "Review passed steps", "合格した段階を復習"))
    BlockKind.SIGHT -> tr("โน้ตที่ไม่เคยเห็น ระดับ ${b.target} · เล่นครั้งเดียวแล้วไปต่อ", "Music you've never seen · play once and move on", "初めて見る楽譜 · 1回弾いて次へ")
    BlockKind.CHORDS -> PlanContent.chords(SkillBook.status(SkillId.CHORDS, s.progress.checks.value, LocalDate.now()).level).summary()
    BlockKind.SOLO -> b.target?.toIntOrNull()?.let { PlanContent.soloSummary(it) } ?: ""
    BlockKind.EAR -> EarProgress.currentStage(EarProgress.parse(s.db.get(EarProgress.KEY))).let { tr("ขั้น ${it.n} ${it.title}", "${it.title}", "${it.title}") }
    BlockKind.SONG -> tr("เพลงจากคลัง ฝึกแบบชีตเต็มแผ่น", "A library piece, full-sheet practice", "ライブラリの曲、楽譜全体で練習")
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
    // Parts that judge timing wait for a piano on a cable (learning-system.md 2.11).
    val ports by s.midi.ports.collectAsState()
    val cable = remember(ports) { s.hasCable() }
    val ladderTimed = remember(records, today) { PlanContent.ladderTimed(s.learning.lessons, records, today) }
    val waits = plan.map { !cable && DailyPlan.needsCable(it, ladderTimed) }
    val current = plan.indices.firstOrNull { !done[it] && !waits[it] } ?: -1
    val week = PracticeLog.lastDays(log, today, 7)
    val ear = remember(log) { EarProgress.parse(s.db.get(EarProgress.KEY)) }
    val fixes = remember(mistakes, ear) { Mistakes.summarize(mistakes, ear, today).take(2) }
    val songs by s.privateSongs.songs.collectAsState()
    var target by remember { mutableStateOf(s.db.get(com.id.mimikopi.core.recall.TargetSong.SETTING_KEY)) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
        val wide = maxWidth > 1000.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 1040.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column {
                        Text(tr("วัน${DAYS[today.dayOfWeek.value - 1]} ${today.dayOfMonth} ${MONTHS_LONG[today.monthValue - 1]}", "${DAYS[today.dayOfWeek.value - 1]} ${today.dayOfMonth} ${MONTHS_LONG[today.monthValue - 1]}", "${MONTHS_LONG[today.monthValue - 1]}${today.dayOfMonth}日（${DAYS[today.dayOfWeek.value - 1]}）"), fontSize = 15.sp, color = Tokens.Palette.InkMuted)
                        Text(when {
                            current >= 0 -> tr("วันนี้ฝึก $minutes นาที", "$minutes min practised today", "今日の練習 ${minutes}分")
                            done.all { it } -> tr("ฝึกครบแผนวันนี้แล้ว", "Today's plan is complete", "今日のプランを完了")
                            else -> tr("ต่อเปียโนด้วยสายเพื่อทำส่วนที่เหลือ", "Connect the piano with a cable to do the rest", "残りはケーブルでピアノをつないで")
                        }, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                    }
                    TargetCard(songs, target, { id -> target = id; s.db.put(com.id.mimikopi.core.recall.TargetSong.SETTING_KEY, id) },
                        { song, hand -> app.nav.open(Screen.Recall(song, hand)) })
                    Text(tr("พื้นฐานวันนี้", "Today's basics", "今日の基礎"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 8.dp))
                    PlanCard(plan, done, current) { b -> PlanRow(s, b, done[plan.indexOf(b)], plan.indexOf(b) == current, plan.indexOf(b) + 1, waits[plan.indexOf(b)]) { open(app, b) } }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("เวลาฝึกพื้นฐาน", "Basic practice time", "基礎練習の時間"), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                        Segmented(DailyPlan.CHOICES, minutes, { if (it == minutes) tr("$it นาที", "$it min", "${it}分") else "$it" },
                            { m -> minutes = m; s.db.put(ProgressStore.MINUTES, m.toString()) }, Modifier.widthIn(max = 420.dp))
                    }
                    if (!wide) Summary(week, day.total, fixes)
                }
                if (wide) Column(Modifier.width(320.dp).padding(top = 52.dp)) { Summary(week, day.total, fixes) }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: List<PlanBlock>, done: List<Boolean>, current: Int, row: @Composable (PlanBlock) -> Unit) {
    Column(Modifier.fillMaxWidth().softShadow(RoundedCornerShape(Tokens.Radius.cardSmall)).clip(RoundedCornerShape(Tokens.Radius.cardSmall))
        .background(Tokens.Palette.Group).padding(8.dp)) {
        plan.forEach { row(it) }
    }
}

@Composable
private fun PlanRow(s: Services, b: PlanBlock, done: Boolean, now: Boolean, n: Int, needsCable: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (now) Tokens.Palette.AccentSoft else Tokens.Palette.Group)
            .clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = if (now) 20.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(when { done -> Tokens.Palette.Accent; now -> Tokens.Palette.AccentSoft; else -> Tokens.Palette.Group })
                .then(if (!done && !now) Modifier.border(1.5.dp, Tokens.Palette.Track, CircleShape) else if (now) Modifier.border(2.dp, Tokens.Palette.Accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else "$n", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = when { done -> Tokens.Palette.OnAccent; now -> Tokens.Palette.AccentDeep; else -> Tokens.Palette.InkMuted })
        }
        Column(Modifier.weight(1f)) {
            if (now) Text(tr("${b.kind.title} · ${b.minutes} นาที", "${b.kind.title} · ${b.minutes} min", "${b.kind.title} · ${b.minutes}分"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Accent)
            Text(if (now) detail(s, b) else b.kind.title, fontSize = if (now) 21.sp else 17.sp, lineHeight = if (now) 29.sp else 24.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.SemiBold,
                color = if (done) Tokens.Palette.InkMuted else Tokens.Palette.Ink, textDecoration = if (done) TextDecoration.LineThrough else null)
            Text(when { now -> b.kind.why; needsCable && !done -> com.id.mimikopi.core.input.TimingInput.NEED_CABLE_SHORT; else -> detail(s, b) },
                fontSize = 14.sp, color = Tokens.Palette.InkMuted, maxLines = 1)
        }
        if (now) PrimaryButton(tr("เริ่ม", "Start", "開始"), onOpen, Modifier.width(140.dp))
        else Text(if (done) tr("เสร็จ", "Done", "完了") else tr("${b.minutes} นาที", "${b.minutes} min", "${b.minutes}分"), fontSize = 15.sp, fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
            color = if (done) Tokens.Palette.Accent else Tokens.Palette.InkMuted)
    }
}

@Composable
private fun Summary(week: List<Pair<LocalDate, Int>>, secondsToday: Int, fixes: List<com.id.mimikopi.core.progress.Mistake>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SideCard {
            Text(tr("7 วันนี้", "These 7 days", "この7日間"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted)
            val max = week.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, (_, m) ->
                    Box(Modifier.weight(1f).fillMaxHeight(if (m == 0) 0.08f else (m.toFloat() / max).coerceIn(0.12f, 1f)).clip(RoundedCornerShape(6.dp))
                        .background(when { m == 0 -> Tokens.Palette.Track; i == week.lastIndex -> Tokens.Palette.Accent; else -> Tokens.Palette.AccentMid }))
                }
            }
            Text(tr("ฝึก ${week.count { it.second > 0 }} วัน · ${week.sumOf { it.second }} นาที · วันนี้ ${secondsToday / 60} นาที", "Practised ${week.count { it.second > 0 }} days · ${week.sumOf { it.second }} min · today ${secondsToday / 60} min", "${week.count { it.second > 0 }}日練習 · ${week.sumOf { it.second }}分 · 今日${secondsToday / 60}分"), fontSize = 14.sp, color = Tokens.Palette.Ink)
        }
        SideCard {
            Text(tr("ยังสับสนบ่อย", "Still often confused", "まだよく混同する"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted)
            if (fixes.isEmpty()) Text(tr("ยังไม่พบจุดที่ผิดซ้ำ", "No repeated mistakes found yet", "繰り返すミスはまだ見つかっていません"), fontSize = 15.sp, color = Tokens.Palette.Ink)
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
    Column(Modifier.fillMaxWidth().softShadow(RoundedCornerShape(Tokens.Radius.card)).clip(RoundedCornerShape(Tokens.Radius.card))
        .background(Tokens.Palette.Group).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
