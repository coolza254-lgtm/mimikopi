package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
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
import com.id.mimikopi.core.session.PlanContent
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
import com.id.mimikopi.desktop.ui.components.SkillIcon
import com.id.mimikopi.desktop.ui.theme.Tokens
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun ago(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (val d = ChronoUnit.DAYS.between(date, today)) {
    0L -> tr("วันนี้", "Today", "今日")
    1L -> tr("เมื่อวาน", "Yesterday", "昨日")
    else -> tr("$d วันก่อน", "$d days ago", "${d}日前")
}

private fun metricsText(r: CheckRecord): String = listOfNotNull(
    r.metrics.accuracy?.let { tr("ถูก ${SkillLevel.pct(it)}", "Right ${SkillLevel.pct(it)}", "正解 ${SkillLevel.pct(it)}") },
    r.metrics.timing?.let { tr("ตรงจังหวะ ${SkillLevel.pct(it)}", "On time ${SkillLevel.pct(it)}", "リズム ${SkillLevel.pct(it)}") },
    r.metrics.medianSec?.let { SkillLevel.sec(it) },
    r.metrics.count?.let { tr("เปลี่ยนคอร์ด $it ครั้ง", "$it chord changes", "コードチェンジ${it}回") },
).joinToString(" · ")

private fun statusLine(s: SkillStatus): String {
    if (s.locked) return tr("ยังไม่เปิด · ", "Not open yet · ", "まだ開いていません · ") + (SkillBook.opensText(s.skill) ?: "")
    val tested = s.lastTested ?: return tr("ยังไม่ได้วัด", "Not measured yet", "未測定")
    val parts = mutableListOf(tr("ระดับ ${s.level} จาก ${s.maxLevel}", "Level ${s.level} of ${s.maxLevel}", "レベル ${s.maxLevel}中${s.level}"), tr("ทดสอบ${ago(tested)}", "Tested${ago(tested)}", "テスト${ago(tested)}"))
    if (s.due) parts += tr("ถึงเวลาทดสอบ", "Time for a test", "テストの時期")
    return parts.joinToString(" · ")
}

/** The mark and pastel of each skill (Design J, as on the phone). */
internal fun look(skill: SkillId): Pair<String, Tokens.SkillColor> = when (skill) {
    SkillId.KEYS -> "▥" to Tokens.Skill.Keys
    SkillId.NOTES -> "≡" to Tokens.Skill.Reading
    SkillId.RHYTHM -> "♩" to Tokens.Skill.Rhythm
    SkillId.SIGHT -> "♫" to Tokens.Skill.Reading
    SkillId.TECH -> "∿" to Tokens.Skill.Chords
    SkillId.EAR -> "◉" to Tokens.Skill.Ear
    SkillId.CHORDS -> "♬" to Tokens.Skill.Chords
    SkillId.SOLO -> "♪♪" to Tokens.Skill.Keys
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
        LargeTitle(tr("ทักษะ", "Skills", "スキル"), overall?.let { tr("ภาพรวม: $it", "Overall: $it", "総合：$it") } ?: tr("ยังไม่ได้วัด", "Not measured yet", "未測定"))
        Group {
            statuses.forEachIndexed { i, s ->
                if (i > 0) GroupDivider()
                val (mark, color) = look(s.skill)
                GroupRow(s.skill.title, statusLine(s), onClick = { app.nav.open(Screen.Skill(s.skill)) },
                    leading = { SkillIcon(mark, color, size = 42.dp, filled = true) },
                    trailing = { Row(verticalAlignment = Alignment.CenterVertically) { LevelBars(s.level, s.maxLevel, width = 16.dp, color = color.ink); Text("  ›", color = Tokens.Palette.Dim) } })
            }
        }
        GroupFooter(tr("ระดับได้จากแบบทดสอบสั้นที่มีเกณฑ์ตายตัวเท่านั้น เวลาที่ฝึกหรือขั้นที่ผ่านไม่ทำให้ระดับขึ้น ", "Levels come only from short tests with fixed criteria; practice time or passed steps don't raise them. ", "レベルは基準の決まった短いテストだけで決まり、練習時間や合格した段階では上がりません。") +
            tr("ถ้าทดสอบระดับที่ถืออยู่ไม่ผ่านสองครั้งติด ระดับจะลดลง ภาพรวมเทียบกับระดับในโปรแกรมนี้ ไม่ใช่เกรดสอบดนตรี", "Failing the level you hold twice in a row lowers it. The overview compares with this app's levels, not music exam grades", "今のレベルのテストに2回続けて不合格だと下がります。概要はこのアプリのレベルとの比較で、音楽試験のグレードではありません"))
        GroupHeader(tr("ฝึกเอง", "Practise on my own", "自主練習"))
        val sight = statuses.firstOrNull { it.skill == SkillId.SIGHT }?.level ?: 0
        Group {
            GroupRow(tr("ขั้นหลัก", "Main step", "メインステップ"), tr("บทเรียนทีละขั้น: อ่านโน้ต จังหวะ นิ้ว เพลงสั้น", "Step-by-step lessons: reading, rhythm, fingers, short pieces", "段階レッスン：読譜、リズム、指、短い曲"), onClick = { app.nav.home(Tab.LESSONS) })
            GroupDivider()
            GroupRow(tr("ฝึกอ่านโน้ต", "Note reading", "読譜"), tr("ทีละตัว · เลือกกุญแจและช่วงเสียง", "One at a time · choose clef and range", "1音ずつ · 音部記号と音域を選ぶ"), onClick = { app.nav.open(Screen.Drill(DrillSource.NotePractice)) })
            GroupDivider()
            GroupRow(tr("ทวนโน้ตและคีย์", "Review notes and keys", "音とキーの復習"), tr("ตัวที่ถึงกำหนดทวน", "The ones due for review", "復習の時期が来たもの"), onClick = { app.nav.open(Screen.Drill(DrillSource.Review)) })
            GroupDivider()
            GroupRow(tr("อ่านโน้ตใหม่", "Sight-reading", "初見"), tr("โน้ตที่ไม่เคยเห็น เล่นครั้งแรก", "Music you have never seen, played for the first time", "初めて見る楽譜を初めて弾く"), onClick = { app.nav.open(Screen.Reader(ReaderArgs(newReading = sight.coerceIn(1, 5)))) })
            GroupDivider()
            GroupRow(tr("ฝึกหู", "Ear training", "耳トレ"), tr("โน้ต ทำนอง คอร์ด ทางเดินคอร์ด", "Notes, melodies, chords, progressions", "音、メロディ、コード、コード進行"), onClick = { app.nav.home(Tab.EAR) })
            GroupDivider()
            GroupRow(tr("คอร์ด", "Chords", "コード"), tr("กดคอร์ดตามชื่อ · เปลี่ยนคอร์ดตามเวลา · วิธีจับคอร์ด", "Chords by name · changes in time · voicings", "名前でコード · 時間内のチェンジ · ボイシング"), onClick = { app.nav.open(Screen.Chords) })
            statuses.firstOrNull { it.skill == SkillId.SOLO }?.let { solo ->
                val level = (solo.level + 1).coerceAtMost(solo.maxLevel)
                GroupDivider()
                GroupRow(tr("สองมือแบบ piano solo", "Two-hand piano solo", "両手ピアノソロ"), PlanContent.soloSummary(level) ?: "", onClick = { app.nav.open(Screen.Reader(ReaderArgs(solo = level))) })
            }
        }
        GroupFooter(tr("ฝึกส่วนไหนก็นับเวลาเข้าแผนวันนี้", "Any practice counts toward today's plan", "どの練習も今日のプランに数えます"))
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
                Text(tr("ระดับ", "Level", "レベル"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${s.level}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                    Text(tr(" จาก ${s.maxLevel}", " of ${s.maxLevel}", " / ${s.maxLevel}"), fontSize = 17.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                LevelBars(s.level, s.maxLevel, width = 22.dp)
                Text(if (s.level > 0) tr("ทำได้แล้ว: ${levels[s.level - 1].canDo}", "Can do: ${levels[s.level - 1].canDo}", "できること：${levels[s.level - 1].canDo}") else if (s.lastTested == null) tr("ยังไม่ได้วัด เริ่มจากทดสอบระดับ 1", "Not measured yet, start with the first test", "未測定。最初のテストから始めましょう") else tr("ยังไม่ผ่านระดับ 1", "First test not passed yet", "最初のテストはまだ不合格"),
                    fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp))
                if (s.dropped) Text(tr("ระดับลดลง เพราะทดสอบระดับที่เคยผ่านไม่ผ่านสองครั้งติด", "Went down after failing a level you had passed twice in a row", "合格していたレベルに2回続けて不合格だったため下がりました"), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                s.lastTested?.let { Text(tr("ทดสอบล่าสุด${ago(it, today)}", "Last test${ago(it, today)}", "最後のテスト${ago(it, today)}"), fontSize = 13.sp, color = Tokens.Palette.InkMuted) }
            }
        }
        GroupHeader(tr("ทดสอบถัดไป", "Next test", "次のテスト"))
        Group {
            Column(Modifier.padding(20.dp)) {
                val next = s.nextTest?.let { SkillBook.level(skill, it) }
                if (next != null) {
                    Text(if (next.level == s.level) tr("ยืนยันระดับ ${next.level}", "Confirm level ${next.level}", "レベル${next.level}を確認") else tr("ระดับ ${next.level}", "Level ${next.level}", "レベル${next.level}"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(next.canDo, fontSize = 15.sp, color = Tokens.Palette.Ink)
                    Text(tr("เกณฑ์ผ่าน: ${next.criteriaText()}", "To pass: ${next.criteriaText()}", "合格基準：${next.criteriaText()}"), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                    if (s.locked) SkillBook.opensText(skill)?.let { Text(tr("ยังไม่เปิดในแผนวันนี้ · $it ทดสอบเองก่อนได้", "Not open in today's plan yet · $it you can test yourself first", "まだ今日のプランにはありません · $it 先に自分でテストできます"), fontSize = 14.sp, color = Tokens.Palette.InkMuted) }
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(if (s.lockedToday) tr("พรุ่งนี้ลองใหม่ได้", "Try again tomorrow", "明日また挑戦できます") else tr("ทดสอบระดับ ${next.level}", "Test level ${next.level}", "レベル${next.level}のテスト"),
                        onClick = { app.nav.open(checkScreen(SkillBook.key(skill, next.level), next.run)) }, enabled = !s.lockedToday)
                } else {
                    Text(tr("ถือระดับสูงสุดอยู่", "At the top level", "最高レベルです"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(tr("ทดสอบซ้ำได้เมื่อครบ ${SkillBook.HOLD_DAYS} วันนับจากครั้งล่าสุด", "Re-test after ${SkillBook.HOLD_DAYS} days from the last one", "前回から${SkillBook.HOLD_DAYS}日たつと再テストできます"), fontSize = 15.sp, color = Tokens.Palette.InkMuted)
                }
            }
        }
        GroupFooter(tr("ทดสอบเมื่อพร้อม ไม่ผ่านระดับที่สูงกว่าไม่เสียอะไร ลองได้วันละ 2 ครั้งต่อระดับ ระหว่างทดสอบไม่มีตัวช่วย", "Test when ready. Failing a higher level costs nothing. Two tries a day per level, no helpers during tests", "準備ができたらテスト。上のレベルに落ちても失うものはありません。各レベル1日2回まで、テスト中は補助なし"))
        GroupHeader(tr("ผลทดสอบ", "Test results", "テスト結果"))
        val mine = checks.filter { it.skill == skill }
        Group {
            if (mine.isEmpty()) GroupRow(tr("ยังไม่เคยทดสอบ", "Not tested yet", "まだテストしていません"), tr("ผลทุกครั้งจะอยู่ตรงนี้ ทั้งที่ผ่านและไม่ผ่าน", "Every result shows here, passed or not", "合格も不合格もすべてここに表示されます"))
            mine.takeLast(12).asReversed().forEachIndexed { i, r ->
                if (i > 0) GroupDivider()
                GroupRow("ระดับ ${r.level} · ${if (r.passed) tr("ผ่าน", "Passed", "合格") else tr("ไม่ผ่าน", "Not passed", "不合格")}", metricsText(r).ifEmpty { null },
                    value = runCatching { ago(LocalDate.parse(r.date), today) }.getOrNull(),
                    titleColor = if (r.passed) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
        GroupHeader(tr("ทุกระดับ", "All steps", "すべての段階"))
        Group {
            levels.forEachIndexed { i, l ->
                if (i > 0) GroupDivider(52.dp)
                GroupRow(tr("ระดับ ${l.level} · ${l.canDo}", "Level ${l.level} · ${l.canDo}", "レベル${l.level} · ${l.canDo}"), tr("เกณฑ์: ${l.criteriaText()}", "Criteria: ${l.criteriaText()}", "基準：${l.criteriaText()}"),
                    leading = { StepMark(l.level <= s.level, l.level == s.nextTest && l.level > s.level) },
                    titleColor = if (l.level <= s.level) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
    }
}
