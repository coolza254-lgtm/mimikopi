package com.id.mimikopi.ui.skills

import com.id.mimikopi.core.i18n.tr
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
import androidx.compose.foundation.layout.Box
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
        LargeTitle(tr("ทักษะ", "Skills", "スキル"), tr("วัดจากแบบทดสอบเท่านั้น ไม่ใช่เวลาที่ฝึก", "Measured only by tests, not by practice time", "練習時間ではなくテストだけで測ります")) {
            MidiPill(onOpenMidi)
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = tr("ตั้งค่า", "Settings", "設定"), tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
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
                SmallCard(tr("พัฒนาการ", "Progress", "上達"), tr("ระดับย้อนหลัง เวลาฝึก", "Level history · practice time", "レベルの推移 · 練習時間"), Modifier.weight(1f).fillMaxHeight()) { onOpen(Open.Progress) }
                SmallCard(tr("ผลสอบเดิม", "Earlier exam results", "以前の試験結果"), tr("สอบเกรดแบบ ABRSM", "ABRSM-style grade exams", "ABRSM式グレード試験"), Modifier.weight(1f).fillMaxHeight()) { onOpen(Open.Exams) }
            }

            Column {
                GroupHeader(tr("ฝึกเอง", "Practise on my own", "自主練習"))
                val sight = ui.statuses.firstOrNull { it.skill == SkillId.SIGHT }?.level ?: 0
                Group {
                    GroupRow(tr("ขั้นหลัก", "Main step", "メインステップ"), subtitle = tr("บทเรียนทีละขั้น: อ่านโน้ต จังหวะ นิ้ว เพลงสั้น", "Step-by-step lessons: reading, rhythm, fingers, short pieces", "段階レッスン：読譜、リズム、指、短い曲"), onClick = { onOpen(Open.Ladder) })
                    GroupDivider()
                    GroupRow(tr("ฝึกอ่านโน้ต", "Note reading", "読譜"), subtitle = tr("ทีละตัวหรือทำนอง · ใช้มือถือหรือ MIDI", "One note or a melody · touch or MIDI", "1音ずつかメロディ · タッチかMIDI"), onClick = { onOpen(Open.Reading) })
                    GroupDivider()
                    GroupRow(tr("ทวนโน้ตและคีย์", "Review notes and keys", "音とキーの復習"), subtitle = tr("ตัวที่ถึงกำหนดทวน", "The ones due for review", "復習の時期が来たもの"), onClick = { onOpen(Open.Review) })
                    GroupDivider()
                    GroupRow(tr("อ่านโน้ตใหม่", "Sight-reading", "初見"), subtitle = tr("โน้ตที่ไม่เคยเห็น เล่นครั้งแรก", "Music you have never seen, played for the first time", "初めて見る楽譜を初めて弾く"), onClick = { onOpen(Open.Sight(sight.coerceIn(1, 5))) })
                    GroupDivider()
                    GroupRow(tr("ฝึกหู", "Ear training", "耳トレ"), subtitle = tr("โน้ต ทำนอง คอร์ด ทางเดินคอร์ด เพื่อแกะเพลงเอง", "Notes, melodies, chords and progressions, to work out songs yourself", "音、メロディ、コード、コード進行。自分で耳コピするために"), onClick = { onOpen(Open.EarHome) })
                    GroupDivider()
                    GroupRow(tr("คอร์ด", "Chords", "コード"), subtitle = tr("กดคอร์ด เปลี่ยนคอร์ด และวิธีจับ", "Pressing chords, changing them and voicings", "コードの押さえ方、切り替え、ボイシング"), onClick = { onOpen(Open.ChordHome) })
                    GroupDivider()
                    GroupRow(tr("เครื่องเคาะจังหวะ", "Metronome", "メトロノーム"), onClick = { onOpen(Open.Metronome) })
                    GroupDivider()
                    GroupRow(tr("เป้าหมาย: เพลงไอดอล", "Goal: idol songs", "目標：アイドル曲"), subtitle = tr("แต่ละเพลงต้องการอะไร ยังขาดอะไร", "What each song needs and what is still missing", "曲ごとに必要なこと、まだ足りないこと"), onClick = { onOpen(Open.Goal) })
                }
                GroupFooter(
                    tr("ฝึกส่วนไหนก็นับเวลาเข้าแผนวันนี้ ระดับขึ้นจากแบบทดสอบสั้นที่มีเกณฑ์ตายตัวเท่านั้น ", "Any practice counts toward today's plan. Levels go up only from short tests with fixed criteria. ", "どの練習も今日のプランの時間に入ります。レベルは基準の決まった短いテストでのみ上がります。") +
                        tr("ถ้าทดสอบระดับที่ถืออยู่ไม่ผ่านสองครั้งติด ระดับจะลดลง", "Failing the level you hold twice in a row lowers it", "今のレベルのテストに2回続けて不合格だとレベルが下がります"),
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
    val next = ui.statuses.filter { it.nextTest != null && !it.lockedToday && !it.locked }.let { open -> open.firstOrNull { it.due } ?: open.firstOrNull() }
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CountRing(measured, total)
            Column(Modifier.weight(1f)) {
                Text(tr("ภาพรวม", "Overview", "概要"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Accent)
                Text(tr("วัดแล้ว $measured จาก $total ทักษะ", "$measured of $total skills measured", "${total}個中${measured}個のスキルを測定済み"), fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text(
                    if (month.isEmpty()) tr("30 วันล่าสุดยังไม่ได้ทดสอบ", "No tests in the last 30 days", "この30日間テストなし") else tr("30 วันล่าสุด: ทดสอบ ${month.size} ครั้ง ผ่าน ${month.count { it.passed }} ครั้ง", "Last 30 days: ${month.size} tests, ${month.count { it.passed }} passed", "この30日間：テスト${month.size}回、合格${month.count { it.passed }}回"),
                    fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted,
                )
                ui.overall?.let { Text(tr("ระดับรวม: $it", "Overall: $it", "総合：$it"), fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted) }
            }
        }
        val level = next?.nextTest
        val book = if (next != null && level != null) SkillBook.level(next.skill, level) else null
        if (next != null && level != null && book != null) {
            Row(
                Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.box)).background(Tokens.Palette.Paper)
                    .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("ทดสอบถัดไป", "Next test", "次のテスト"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
                    Text(next.skill.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                }
                PrimaryButton(tr("ทดสอบ", "Test", "テスト"), onClick = { onOpen(Open.Check(SkillBook.key(next.skill, level), book.run)) }, modifier = Modifier.width(112.dp))
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
            if (s.locked) {
                Text(tr("ยังไม่เปิด", "Not open yet", "まだ開いていません"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            } else if (s.lastTested == null) {
                Text(tr("ยังไม่ได้วัด", "Not measured yet", "未測定"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            } else {
                LevelBars(s.level, s.maxLevel, width = 20.dp, color = color.ink, gap = 4.dp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        when {
            s.lastTested != null && s.due && !s.lockedToday -> Pill(tr("ถึงเวลาทดสอบ", "Time for a test", "テストの時期"))
            s.lastTested != null -> Text("●".repeat(s.level) + "○".repeat(s.maxLevel - s.level), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
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
    SkillId.SOLO -> R.drawable.ic_skill_hand to Tokens.Skill.Keys
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
                    Text(tr("ระดับ", "Level", "レベル"), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.InkMuted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${s.level}", fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                        Text(tr(" จาก ${s.maxLevel}", " of ${s.maxLevel}", " / ${s.maxLevel}"), fontSize = 17.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    LevelBars(s.level, s.maxLevel, width = 22.dp, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        if (s.level > 0) tr("ทำได้แล้ว: ${levels[s.level - 1].canDo}", "Can do: ${levels[s.level - 1].canDo}", "できること：${levels[s.level - 1].canDo}") else if (s.lastTested == null) tr("ยังไม่ได้วัด เริ่มจากแบบทดสอบแรก", "Not measured yet, start with the first test", "未測定。最初のテストから始めましょう") else tr("ยังไม่ผ่านแบบทดสอบแรก", "First test not passed yet", "最初のテストはまだ不合格"),
                        fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp),
                    )
                    if (s.dropped) Text(tr("ระดับลดลง เพราะทดสอบระดับที่เคยผ่านไม่ผ่านสองครั้งติด", "Went down after failing a level you had passed twice in a row", "合格していたレベルに2回続けて不合格だったため下がりました"), fontSize = 14.sp, lineHeight = 19.sp,
                        color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                    s.lastTested?.let { Text(tr("ทดสอบล่าสุด${SkillsViewModel.ago(it, today)}", "Last test${SkillsViewModel.ago(it, today)}", "最後のテスト${SkillsViewModel.ago(it, today)}"), fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp)) }
                }
            }

            GroupHeader(tr("ทดสอบถัดไป", "Next test", "次のテスト"))
            Group {
                Column(Modifier.padding(20.dp)) {
                    val next = s.nextTest?.let { SkillBook.level(skill, it) }
                    if (next != null) {
                        Text(if (next.level == s.level) tr("ยืนยัน: ${next.canDo}", "Confirm: ${next.canDo}", "確認：${next.canDo}") else next.canDo, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                        Text(next.canDo, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 4.dp))
                        Text(tr("เกณฑ์ผ่าน: ${next.criteriaText()}", "To pass: ${next.criteriaText()}", "合格基準：${next.criteriaText()}"), fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                        if (s.locked) SkillBook.opensText(skill)?.let {
                            Text(tr("ยังไม่เปิดในแผนวันนี้ · $it ทดสอบเองก่อนได้", "Not open in today's plan yet · $it you can test yourself first", "まだ今日のプランにはありません · $it 先に自分でテストできます"), fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton(
                            if (s.lockedToday) tr("พรุ่งนี้ลองใหม่ได้", "Try again tomorrow", "明日また挑戦できます") else tr("ทดสอบ", "Test", "テスト"),
                            onClick = { onOpen(Open.Check(SkillBook.key(skill, next.level), next.run)) },
                            enabled = !s.lockedToday,
                        )
                        if (s.lockedToday) Text(tr("วันนี้ลองระดับนี้ครบ 2 ครั้งแล้ว ฝึกก่อนแล้วค่อยลองพรุ่งนี้", "You've tried this twice today. Practise and try again tomorrow", "今日はこのテストを2回受けました。練習して明日また"), fontSize = 13.sp, lineHeight = 18.sp,
                            color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text(tr("ถือระดับสูงสุดอยู่", "At the top level", "最高レベルです"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                        Text(tr("ทดสอบซ้ำได้เมื่อครบ ${SkillBook.HOLD_DAYS} วันนับจากครั้งล่าสุด เพื่อดูว่ายังทำได้อยู่", "Re-test after ${SkillBook.HOLD_DAYS} days from the last one, to check you still can", "前回から${SkillBook.HOLD_DAYS}日たつと再テストでき、まだできるか確認できます"), fontSize = 15.sp, lineHeight = 20.sp,
                            color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            GroupFooter(tr("ทดสอบเมื่อพร้อม ไม่ผ่านระดับที่สูงกว่าไม่เสียอะไร ลองได้วันละ 2 ครั้งต่อระดับ ระหว่างทดสอบไม่มีตัวช่วย", "Test when ready. Failing a higher level costs nothing. Two tries a day per level, no helpers during tests", "準備ができたらテスト。上のレベルに落ちても失うものはありません。各レベル1日2回まで、テスト中は補助なし"))

            GroupHeader(tr("ฝึกเรื่องนี้", "Practise this", "これを練習"))
            Group {
                practice(skill, s).forEachIndexed { i, (title, open) ->
                    if (i > 0) GroupDivider()
                    GroupRow(title, onClick = { onOpen(open) })
                }
            }

            GroupHeader(tr("ผลทดสอบ", "Test results", "テスト結果"))
            val mine = ui.records.filter { it.skill == skill }
            Group {
                if (mine.isEmpty()) GroupRow(tr("ยังไม่เคยทดสอบ", "Not tested yet", "まだテストしていません"), subtitle = tr("ผลทุกครั้งจะอยู่ตรงนี้ ทั้งที่ผ่านและไม่ผ่าน", "Every result shows here, passed or not", "合格も不合格もすべてここに表示されます"))
                mine.takeLast(12).asReversed().forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    GroupRow(
                        "${levels.getOrNull(r.level - 1)?.canDo ?: ""} · ${if (r.passed) tr("ผ่าน", "Passed", "合格") else tr("ไม่ผ่าน", "Not passed", "不合格")}",
                        subtitle = SkillsViewModel.metricsText(r).ifEmpty { null },
                        value = runCatching { SkillsViewModel.ago(LocalDate.parse(r.date), today) }.getOrNull(),
                        titleColor = if (r.passed) Tokens.Palette.Ink else Tokens.Palette.InkMuted,
                    )
                }
            }
            val month = mine.filter { r -> runCatching { LocalDate.parse(r.date) >= today.minusDays(30) }.getOrDefault(false) }
            if (month.isNotEmpty()) GroupFooter(tr("30 วันล่าสุด: ทดสอบ ${month.size} ครั้ง ผ่าน ${month.count { it.passed }} ครั้ง", "Last 30 days: ${month.size} tests, ${month.count { it.passed }} passed", "この30日間：テスト${month.size}回、合格${month.count { it.passed }}回"))

            GroupHeader(tr("ทุกระดับ", "All steps", "すべての段階"))
            Group {
                levels.forEachIndexed { i, l ->
                    if (i > 0) GroupDivider(indent = 52.dp)
                    GroupRow(
                        l.canDo,
                        subtitle = tr("เกณฑ์: ${l.criteriaText()}", "Criteria: ${l.criteriaText()}", "基準：${l.criteriaText()}"),
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
    val ladder = tr("ขั้นหลัก (บทเรียนทีละขั้น)", "Main step (step-by-step lessons)", "メインステップ（段階レッスン）") to Open.Ladder
    val review = tr("ทวนโน้ตและคีย์", "Review notes and keys", "音とキーの復習") to Open.Review
    val metronome = tr("เครื่องเคาะจังหวะ", "Metronome", "メトロノーム") to Open.Metronome
    return when (skill) {
        SkillId.KEYS -> listOf(review, ladder)
        SkillId.NOTES -> listOf(tr("ฝึกอ่านโน้ต: ทีละตัวและทำนอง", "Note reading: single notes and melodies", "読譜：1音とメロディ") to Open.Reading, review, ladder)
        SkillId.RHYTHM, SkillId.TECH -> listOf(ladder, metronome)
        SkillId.SIGHT -> listOf(tr("ฝึกอ่านโน้ต: ทีละตัวและทำนอง", "Note reading: single notes and melodies", "読譜：1音とメロディ") to Open.Reading, tr("อ่านโน้ตใหม่", "Sight-reading", "初見") to Open.Sight(s.level.coerceIn(1, 5)), ladder)
        SkillId.EAR -> listOf(tr("ฝึกหู (รอบปรับตามจุดอ่อน)", "Ear training (adapts to weak spots)", "耳トレ（苦手に合わせて出題）") to Open.Ear(), tr("หน้าฝึกหู: เส้นทางและภาพรวม", "Ear training page: path and overview", "耳トレページ：コースと概要") to Open.EarHome)
        SkillId.CHORDS -> listOf(tr("คอร์ด: ฝึกกด เปลี่ยนคอร์ด วิธีจับ", "Chords: pressing, changing, voicings", "コード：押さえ方、切り替え、ボイシング") to Open.ChordHome)
        SkillId.SOLO -> listOf(
            tr("สองมือแบบรอ: เรื่องถัดไป", "Two hands in wait mode: the next step", "両手・待ちモード：次のステップ") to Open.Solo((s.level + 1).coerceAtMost(s.maxLevel)),
            tr("คอร์ด: ฝึกกด เปลี่ยนคอร์ด วิธีจับ", "Chords: pressing, changing, voicings", "コード：押さえ方、切り替え、ボイシング") to Open.ChordHome,
        )
    }
}
