package com.id.mimikopi.ui.skills

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.Pill
import com.id.mimikopi.ui.components.SkillIcon
import com.id.mimikopi.ui.components.softShadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.progress.SkillStatus
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupFooter
import com.id.mimikopi.ui.components.GroupHeader
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.LargeTitle
import com.id.mimikopi.ui.components.LevelBars
import com.id.mimikopi.ui.components.MidiPill
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.components.StepMark
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.Open
import java.time.LocalDate

/**
 * Where each skill stands, from tests only: a row per skill with its level as bars, the honest overall line, and
 * the practice rooms for practising any part outside the day's plan.
 */
@Composable
fun SkillsScreen(onOpen: (Open) -> Unit, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit, vm: SkillsViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Page {
        LargeTitle("ทักษะ", "วัดจากแบบทดสอบเท่านั้น ไม่ใช่เวลาที่ฝึก") {
            MidiPill(onOpenMidi)
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "ตั้งค่า", tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
            }
        }
        if (!ui.loaded) return@Page
        Column(Modifier.padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(0.dp))
            OverallCard(ui, onOpen)
            Group {
                ui.statuses.forEachIndexed { i, s ->
                    if (i > 0) GroupDivider(indent = 72.dp)
                    SkillRow(s, onClick = { onOpen(Open.Skill(s.skill)) })
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCard("พัฒนาการ", "ระดับย้อนหลัง เวลาฝึก", Modifier.weight(1f).fillMaxHeight()) { onOpen(Open.Progress) }
                SmallCard("ผลสอบเดิม", "สอบเกรดแบบ ABRSM", Modifier.weight(1f).fillMaxHeight()) { onOpen(Open.Exams) }
            }

            Column {
                GroupHeader("ฝึกเอง")
                val sight = ui.statuses.firstOrNull { it.skill == SkillId.SIGHT }?.level ?: 0
                Group {
                    GroupRow("ขั้นหลัก", subtitle = "บทเรียนทีละขั้น: อ่านโน้ต จังหวะ นิ้ว เพลงสั้น", onClick = { onOpen(Open.Ladder) })
                    GroupDivider()
                    GroupRow("ฝึกอ่านโน้ต", subtitle = "ทีละตัวหรือทำนอง · ใช้มือถือหรือ MIDI", onClick = { onOpen(Open.Reading) })
                    GroupDivider()
                    GroupRow("ทวนโน้ตและคีย์", subtitle = "ตัวที่ถึงกำหนดทวน", onClick = { onOpen(Open.Review) })
                    GroupDivider()
                    GroupRow("อ่านโน้ตใหม่", subtitle = "โน้ตที่ไม่เคยเห็น เล่นครั้งแรก", onClick = { onOpen(Open.Sight(sight.coerceIn(1, 5))) })
                    GroupDivider()
                    GroupRow("ฝึกหู", subtitle = "โน้ต ทำนอง คอร์ด ทางเดินคอร์ด เพื่อแกะเพลงเอง", onClick = { onOpen(Open.EarHome) })
                    GroupDivider()
                    GroupRow("คอร์ด", subtitle = "กดคอร์ด เปลี่ยนคอร์ด และวิธีจับ", onClick = { onOpen(Open.ChordHome) })
                    GroupDivider()
                    GroupRow("เครื่องเคาะจังหวะ", onClick = { onOpen(Open.Metronome) })
                    GroupDivider()
                    GroupRow("เป้าหมาย: เพลงไอดอล", subtitle = "แต่ละเพลงต้องการอะไร ยังขาดอะไร", onClick = { onOpen(Open.Goal) })
                }
                GroupFooter(
                    "ฝึกส่วนไหนก็นับเวลาเข้าแผนวันนี้ ระดับขึ้นจากแบบทดสอบสั้นที่มีเกณฑ์ตายตัวเท่านั้น " +
                        "ถ้าทดสอบระดับที่ถืออยู่ไม่ผ่านสองครั้งติด ระดับจะลดลง",
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** The overview card: a ring of skills measured, the last 30 days of tests, and the one test to take next. */
@Composable
private fun OverallCard(ui: SkillsUi, onOpen: (Open) -> Unit) {
    val today = LocalDate.now()
    val measured = ui.statuses.count { it.lastTested != null }
    val total = ui.statuses.size
    val month = ui.records.filter { r -> runCatching { LocalDate.parse(r.date) >= today.minusDays(30) }.getOrDefault(false) }
    val next = ui.statuses.filter { it.nextTest != null && !it.lockedToday }.let { open -> open.firstOrNull { it.due } ?: open.firstOrNull() }
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CountRing(measured, total)
            Column(Modifier.weight(1f)) {
                Text("ภาพรวม", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Accent)
                Text("วัดแล้ว $measured จาก $total ทักษะ", fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text(
                    if (month.isEmpty()) "30 วันล่าสุดยังไม่ได้ทดสอบ" else "30 วันล่าสุด: ทดสอบ ${month.size} ครั้ง ผ่าน ${month.count { it.passed }} ครั้ง",
                    fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted,
                )
                ui.overall?.let { Text("ระดับรวม: $it", fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted) }
            }
        }
        if (next != null) {
            val level = next.nextTest!!
            val book = SkillBook.level(next.skill, level)
            Row(
                Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.box)).background(Tokens.Palette.Paper)
                    .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ทดสอบถัดไป", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
                    Text("${next.skill.title} · ระดับ $level", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                }
                PrimaryButton("ทดสอบ", onClick = { onOpen(Open.Check(SkillBook.key(next.skill, level), book.run)) }, modifier = Modifier.width(112.dp))
            }
        }
    }
}

@Composable
private fun CountRing(count: Int, total: Int, size: Dp = 64.dp) {
    val track = Tokens.Palette.AccentSoft
    val accent = Tokens.Palette.Accent
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke))
            if (total > 0 && count > 0) drawArc(accent, -90f, 360f * count / total, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$count/$total", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
    }
}

/** One skill in the list: its pastel icon, its level as bars in its colour, and the level or that a test is due. */
@Composable
private fun SkillRow(s: SkillStatus, onClick: () -> Unit) {
    val (icon, color) = look(s.skill)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkillIcon(icon, color, size = 42.dp, filled = true)
        Column(Modifier.weight(1f)) {
            Text(s.skill.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            if (s.lastTested == null) {
                Text("ยังไม่ได้วัด", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            } else {
                LevelBars(s.level, s.maxLevel, width = 20.dp, color = color.ink, gap = 4.dp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        when {
            s.lastTested != null && s.due && !s.lockedToday -> Pill("ถึงเวลาทดสอบ")
            s.lastTested != null -> Text("ระดับ ${s.level}", fontSize = 14.sp, color = Tokens.Palette.InkMuted)
            else -> Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Tokens.Palette.Dim, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SmallCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Tokens.Radius.cardSmall)
    Column(
        modifier.softShadow(shape, small = true).clip(shape).background(Tokens.Palette.Group).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The icon and pastel of each skill (Design J). */
internal fun look(skill: SkillId): Pair<Int, Tokens.SkillColor> = when (skill) {
    SkillId.KEYS -> R.drawable.ic_nav_piano to Tokens.Skill.Keys
    SkillId.NOTES -> R.drawable.ic_skill_staff to Tokens.Skill.Reading
    SkillId.RHYTHM -> R.drawable.ic_skill_note to Tokens.Skill.Rhythm
    SkillId.SIGHT -> R.drawable.ic_skill_note to Tokens.Skill.Reading
    SkillId.TECH -> R.drawable.ic_skill_hand to Tokens.Skill.Chords
    SkillId.EAR -> R.drawable.ic_skill_ear to Tokens.Skill.Ear
    SkillId.CHORDS -> R.drawable.ic_nav_piano to Tokens.Skill.Chords
}

/**
 * One skill: the level held and what it means, the next test with its fixed criteria and one button, where to
 * practise it, every test taken and every level of the skill.
 */
@Composable
fun SkillDetailScreen(skill: SkillId, onBack: () -> Unit, onOpen: (Open) -> Unit, vm: SkillsViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.statuses.firstOrNull { it.skill == skill }
    val levels = SkillBook.levels(skill)
    val today = LocalDate.now()

    Page {
        LargeTitle(skill.title, skill.about, onBack = onBack)
        if (s == null) return@Page
        Column(Modifier.padding(horizontal = PageMargin)) {
            Spacer(Modifier.height(16.dp))
            Group {
                Column(Modifier.padding(20.dp)) {
                    Text("ระดับ", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.InkMuted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${s.level}", fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                        Text(" จาก ${s.maxLevel}", fontSize = 17.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    LevelBars(s.level, s.maxLevel, width = 22.dp, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        if (s.level > 0) "ทำได้แล้ว: ${levels[s.level - 1].canDo}" else if (s.lastTested == null) "ยังไม่ได้วัด เริ่มจากทดสอบระดับ 1" else "ยังไม่ผ่านระดับ 1",
                        fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp),
                    )
                    if (s.dropped) Text("ระดับลดลง เพราะทดสอบระดับที่เคยผ่านไม่ผ่านสองครั้งติด", fontSize = 14.sp, lineHeight = 19.sp,
                        color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                    s.lastTested?.let { Text("ทดสอบล่าสุด${SkillsViewModel.ago(it, today)}", fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp)) }
                }
            }

            GroupHeader("ทดสอบถัดไป")
            Group {
                Column(Modifier.padding(20.dp)) {
                    val next = s.nextTest?.let { SkillBook.level(skill, it) }
                    if (next != null) {
                        Text(if (next.level == s.level) "ยืนยันระดับ ${next.level}" else "ระดับ ${next.level}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                        Text(next.canDo, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 4.dp))
                        Text("เกณฑ์ผ่าน: ${next.criteriaText()}", fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton(
                            if (s.lockedToday) "พรุ่งนี้ลองใหม่ได้" else "ทดสอบระดับ ${next.level}",
                            onClick = { onOpen(Open.Check(SkillBook.key(skill, next.level), next.run)) },
                            enabled = !s.lockedToday,
                        )
                        if (s.lockedToday) Text("วันนี้ลองระดับนี้ครบ 2 ครั้งแล้ว ฝึกก่อนแล้วค่อยลองพรุ่งนี้", fontSize = 13.sp, lineHeight = 18.sp,
                            color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text("ถือระดับสูงสุดอยู่", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                        Text("ทดสอบซ้ำได้เมื่อครบ ${SkillBook.HOLD_DAYS} วันนับจากครั้งล่าสุด เพื่อดูว่ายังทำได้อยู่", fontSize = 15.sp, lineHeight = 20.sp,
                            color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            GroupFooter("ทดสอบเมื่อพร้อม ไม่ผ่านระดับที่สูงกว่าไม่เสียอะไร ลองได้วันละ 2 ครั้งต่อระดับ ระหว่างทดสอบไม่มีตัวช่วย")

            GroupHeader("ฝึกเรื่องนี้")
            Group {
                practice(skill, s).forEachIndexed { i, (title, open) ->
                    if (i > 0) GroupDivider()
                    GroupRow(title, onClick = { onOpen(open) })
                }
            }

            GroupHeader("ผลทดสอบ")
            val mine = ui.records.filter { it.skill == skill }
            Group {
                if (mine.isEmpty()) GroupRow("ยังไม่เคยทดสอบ", subtitle = "ผลทุกครั้งจะอยู่ตรงนี้ ทั้งที่ผ่านและไม่ผ่าน")
                mine.takeLast(12).asReversed().forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    GroupRow(
                        "ระดับ ${r.level} · ${if (r.passed) "ผ่าน" else "ไม่ผ่าน"}",
                        subtitle = SkillsViewModel.metricsText(r).ifEmpty { null },
                        value = runCatching { SkillsViewModel.ago(LocalDate.parse(r.date), today) }.getOrNull(),
                        titleColor = if (r.passed) Tokens.Palette.Ink else Tokens.Palette.InkMuted,
                    )
                }
            }
            val month = mine.filter { r -> runCatching { LocalDate.parse(r.date) >= today.minusDays(30) }.getOrDefault(false) }
            if (month.isNotEmpty()) GroupFooter("30 วันล่าสุด: ทดสอบ ${month.size} ครั้ง ผ่าน ${month.count { it.passed }} ครั้ง")

            GroupHeader("ทุกระดับ")
            Group {
                levels.forEachIndexed { i, l ->
                    if (i > 0) GroupDivider(indent = 52.dp)
                    GroupRow(
                        "ระดับ ${l.level} · ${l.canDo}",
                        subtitle = "เกณฑ์: ${l.criteriaText()}",
                        leading = { StepMark(done = l.level <= s.level, current = l.level == s.nextTest && l.level > s.level) },
                        titleColor = if (l.level <= s.level) Tokens.Palette.Ink else Tokens.Palette.InkMuted,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) { content() }
    }
}

/** Where to practise a skill between tests. */
private fun practice(skill: SkillId, s: SkillStatus): List<Pair<String, Open>> {
    val ladder = "ขั้นหลัก (บทเรียนทีละขั้น)" to Open.Ladder
    val review = "ทวนโน้ตและคีย์" to Open.Review
    val metronome = "เครื่องเคาะจังหวะ" to Open.Metronome
    return when (skill) {
        SkillId.KEYS -> listOf(review, ladder)
        SkillId.NOTES -> listOf("ฝึกอ่านโน้ต: ทีละตัวและทำนอง" to Open.Reading, review, ladder)
        SkillId.RHYTHM, SkillId.TECH -> listOf(ladder, metronome)
        SkillId.SIGHT -> listOf("ฝึกอ่านโน้ต: ทีละตัวและทำนอง" to Open.Reading, "อ่านโน้ตใหม่ ระดับ L${s.level.coerceIn(1, 5)}" to Open.Sight(s.level.coerceIn(1, 5)), ladder)
        SkillId.EAR -> listOf("ฝึกหู (รอบปรับตามจุดอ่อน)" to Open.Ear(), "หน้าฝึกหู: เส้นทางและภาพรวม" to Open.EarHome)
        SkillId.CHORDS -> listOf("คอร์ด: ฝึกกด เปลี่ยนคอร์ด วิธีจับ" to Open.ChordHome)
    }
}
