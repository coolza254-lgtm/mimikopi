package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.playalong.HeroicPlan
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.playalong.StudyEvidence
import com.id.mimikopi.desktop.session.HandChoice
import com.id.mimikopi.desktop.session.ReaderArgs
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PageCard
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.theme.Tokens

/**
 * Songs: the private songs worked out by ear, the bundled library (public-domain pieces, easiest first, each with
 * its hands), then Heroic Polonaise as an extra with its study plan.
 */
@Composable
fun SongsScreen() {
    val app = LocalApp.current
    val progress by app.services.pieces.progress.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    Page {
        LargeTitle(tr("เพลง", "Songs", "曲"), tr("${app.services.pieces.pieces.size} เพลงในคลัง · เป้าหมาย: piano solo เพลง Hello! Project", "${app.services.pieces.pieces.size} pieces in the library · goal: Hello! Project songs as piano solo", "ライブラリに${app.services.pieces.pieces.size}曲 · 目標：Hello! Projectの曲をピアノソロで"))
        PageCard(title = tr("เพลงส่วนตัว · ฝึกจำจากวิดีโอ", "Private songs · recall from video", "個人曲 · 動画から暗記")) {
            Text(tr("แบ่งวลีจากลิงก์วิดีโอ ลองเล่นเองโดยไม่ดู จดจุดติด แล้วทวนข้ามวัน · ประเมินเอง · เก็บในเครื่องนี้เท่านั้น", "Split a video into phrases, play without looking, note sticking points and review across days · self-assessed · kept on this computer only", "動画をフレーズに分け、見ずに弾き、つまずきをメモして日をまたいで復習 · 自己評価 · このPCにのみ保存"), color = Tokens.Palette.InkMuted)
            TextButton(onClick = { app.nav.open(Screen.Recall()) }) { Text(tr("เปิดเพลงส่วนตัว", "Open private songs", "個人曲を開く")) }
        }
        PageCard(title = tr("โน้ตที่ซื้อ · อัดจากเปียโน", "Bought sheets · play them in", "購入した楽譜 · ピアノで入力")) {
            Text(tr("เล่นโน้ต piano solo ที่ซื้อเข้าแอปทีละช่วงทีละมือ แอปจัดลงจังหวะให้ แก้โน้ตผิดได้ แล้วฝึกแบบเล่นตามได้เหมือนเพลงในคลัง · เก็บในเครื่องนี้เท่านั้น", "Play a bought piano-solo sheet in a few bars and one hand at a time; the app puts it on the beat, you fix wrong notes, then practise it like a library piece · kept on this computer only", "購入したピアノソロ楽譜を数小節ずつ片手ずつ弾いて入力。アプリがリズムに合わせ、間違いを直して、ライブラリの曲のように練習できます · このPCにのみ保存"), color = Tokens.Palette.InkMuted)
            TextButton(onClick = { app.nav.open(Screen.Scores) }) { Text(tr("เปิดโน้ตที่ซื้อ", "Open bought sheets", "購入した楽譜を開く")) }
        }
        PageCard(title = tr("คลังเพลง · ง่ายไปยาก", "Library · easy to hard", "ライブラリ · やさしい順")) {
            Text(tr("เริ่มทีละมือในโหมดรอ แล้วค่อยรวมมือและเล่นตามจังหวะ · ผ่านเมื่อเล่นสะอาดใน 2 วันที่ต่างกัน", "Start hands separately in wait mode, then hands together in time · passed when played clean on 2 different days", "片手ずつ待ちモードで始め、次に両手でリズムに合わせて · 別々の2日にクリーンに弾けたら合格"), color = Tokens.Palette.InkMuted)
            for ((i, p) in app.services.pieces.pieces.filter { it.id !in HEROIC }.withIndex()) {
                val record = progress[p.id]
                val passed = HandChoice.entries.filter { h -> record != null && PieceRules.isPassed(record, h.name) }
                Text(
                    "%02d  %s · Lv.%s%s".format(i + 1, p.title, p.level.removePrefix("L"), if (passed.isEmpty()) "" else tr(" · ผ่าน ", " · passed ", " · 合格 ") + passed.joinToString { it.title }),
                    fontSize = 16.sp, fontWeight = if (open == p.id) FontWeight.SemiBold else FontWeight.Normal, color = Tokens.Palette.Ink,
                    modifier = Modifier.clickable { open = if (open == p.id) null else p.id },
                )
                if (open == p.id) Row {
                    for (h in HandChoice.entries) TextButton(onClick = { app.nav.open(Screen.Reader(ReaderArgs(pieceId = p.id, hand = h))) }) { Text(h.title) }
                }
            }
        }
        Text(tr("เพลงเสริม · Heroic Polonaise", "Extra piece · Heroic Polonaise", "追加曲 · 英雄ポロネーズ"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink)
        HeroicSection()
    }
}

private val HEROIC = setOf(HeroicPlan.EASY, HeroicPlan.THEME)

/** Heroic Polonaise, an extra song: its study plan (core HeroicPlan) and free practice of both editions. */
@Composable
private fun HeroicSection() {
    val app = LocalApp.current
    val progress by app.services.pieces.progress.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(false) }
    val next = HeroicPlan.next(progress)
    val done = HeroicPlan.targets.count { it.completed(HeroicPlan.evidence(it, progress)) }
    fun open(t: HeroicPlan.Target) = app.nav.open(Screen.Reader(ReaderArgs(pieceId = t.piece, target = t.id)))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Chopin · Op.53 · เริ่มจากท่อนหลัก 8 ห้อง (ต้นฉบับห้อง 17–24) ใน C แล้วค่อยเพิ่มคอร์ดใน A♭ · ทั้งสองเป็นฉบับย่อ ยังไม่ใช่เพลงเต็ม", "Chopin · Op.53 · start with the 8-bar main section (original bars 17–24) in C, then add chords in A♭ · both are shortened editions, not the full piece", "ショパン · Op.53 · Cの8小節のメイン部分（原曲17–24小節）から始め、次にA♭でコードを加える · どちらも短縮版で全曲ではありません"), color = Tokens.Palette.Ink)
        PageCard(title = tr("ฝึกต่อจากตรงนี้", "Continue from here", "ここから続ける")) {
            if (next != null) {
                val e = HeroicPlan.evidence(next, progress)
                Text(if (next.piece == HeroicPlan.EASY) tr("ฉบับง่ายใน C", "Easy edition in C", "Cのやさしい版") else tr("ฉบับฝึกใน A♭", "Practice edition in A♭", "A♭の練習版"), color = Tokens.Palette.InkMuted)
                Text(next.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink)
                Text(next.goal, color = Tokens.Palette.Ink)
                Text(if (next.mode == PlayMode.WAIT) tr("อ่านจบช่วงโดยไม่กดเกิน · ปิดชื่อโน้ตเมื่อวัดผล", "Read through the section without extra presses · note names off when measuring", "余分に押さずに区間を最後まで読む · 測定時は音名を消す")
                else tr("โน้ต ≥95% · จังหวะ ≥90% · ไม่กดเกิน · สะดุดไม่เกิน 1 ครั้ง · ความคลาดเคลื่อนไม่เกิน ±120 ms", "Notes ≥95% · timing ≥90% · no extra presses · at most 1 stumble · error within ±120 ms", "音 ≥95% · リズム ≥90% · 余分な押鍵なし · つまずき1回まで · 誤差±120 ms以内"), color = Tokens.Palette.InkMuted)
                if (e.attempts > 0) EvidenceText(next, e)
                PrimaryButton(tr("เริ่มช่วงนี้", "Start this section", "この区間を始める"), onClick = { open(next) })
            } else {
                Text(tr("ทำครบแผนท่อนหลักแล้ว · ทบทวนและฟังน้ำหนักเสียงบนเปียโนจริง ก่อนขยายไปส่วนอื่นของเพลง", "The main-section plan is complete · review and listen to the dynamics on a real piano before going on to other parts", "メイン部分のプラン完了 · 他の部分へ進む前に、本物のピアノで復習し音の強弱を聞きましょう"), color = Tokens.Palette.Ink)
                PrimaryButton(tr("ทบทวนท่อนหลักใน A♭", "Review the main section in A♭", "A♭のメイン部分を復習"), onClick = { HeroicPlan.target("ab-whole")?.let(::open) })
            }
        }
        PageCard(title = tr("แผนรอบนี้ · ประมาณ 15 นาที", "This round's plan · about 15 minutes", "この回のプラン · 約15分")) {
            Text(tr("2 นาที · ฟังท่อนที่จะฝึก (ปุ่มฟังตัวอย่าง) และนับ 1–2–3 ตามจังหวะ Polonaise", "2 min · listen to the section (example button) and count 1–2–3 in Polonaise rhythm", "2分 · 練習する部分を聞き（お手本ボタン）、ポロネーズのリズムで1–2–3と数える"))
            Text(tr("5 นาที · อ่านแยกมือทีละ 2 ห้องในโหมดรอ ถ้าติดให้ลดเหลือห้องเดียว", "5 min · read hands separately 2 bars at a time in wait mode; if stuck, drop to one bar", "5分 · 待ちモードで片手ずつ2小節ずつ読む。つまずいたら1小節に"))
            Text(tr("6 นาที · รวมมือช้า ๆ ที่ 48 bpm แล้ววนรอยต่อ ก่อนเป้าความเร็วสุดท้าย ค่อยเพิ่มครั้งละ 3–5 bpm", "6 min · hands together slowly at 48 bpm and loop the joins; before the final tempo, add 3–5 bpm at a time", "6分 · 48 bpmでゆっくり両手、つなぎ目をくり返す。最終テンポの前に3–5 bpmずつ上げる"))
            Text(tr("2 นาที · ลองช่วงเดิมโดยไม่เปิดชื่อโน้ต แล้วดูห้องที่ควรแก้จากผลจริง", "2 min · try the same section without note names, then see which bars to fix from the results", "2分 · 音名なしで同じ区間を弾き、結果から直す小節を確認"))
            Text(tr("เป็นเวลาที่แนะนำ ไม่ใช่เวลาฝึกที่บันทึกอัตโนมัติ", "Suggested times, not automatically recorded practice time", "おすすめの時間で、自動記録される練習時間ではありません"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }
        PageCard(title = tr("ผลของท่อนหลัก", "Main section results", "メイン部分の結果")) {
            Text(tr("ทำได้ตามเกณฑ์ $done จาก ${HeroicPlan.targets.size} เป้าหมายฝึก", "Criteria met for $done of ${HeroicPlan.targets.size} practice goals", "練習目標${HeroicPlan.targets.size}個中${done}個で基準達成"), color = Tokens.Palette.Ink)
            Text(tr("โหมดรอวัดการอ่านเท่านั้น · โหมดตามจังหวะต้องเล่นสะอาด 3 รอบติดใน 2 วันที่ต่างกัน ไม่ใช้ชื่อโน้ตหรือโน้ตไหลช่วย", "Wait mode measures reading only · in-time mode needs 3 clean rounds in a row on 2 different days, without note names or falling notes", "待ちモードは読譜のみ測定 · リズムモードは別々の2日に3回連続クリーン、音名や流れる音符なし"), color = Tokens.Palette.InkMuted)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) tr("ย่อแผนและผลรายช่วง", "Hide plan and section results", "プランと区間の結果を隠す") else tr("ดูแผนและผลรายช่วง", "See plan and section results", "プランと区間の結果を見る")) }
        }
        if (expanded) for ((i, t) in HeroicPlan.targets.withIndex()) {
            val e = HeroicPlan.evidence(t, progress)
            PageCard(title = "${i + 1}. ${if (t.piece == HeroicPlan.EASY) tr("C · เบสง่าย", "C · easy bass", "C · やさしいベース") else tr("A♭ · คอร์ด", "A♭ · chords", "A♭ · コード")} · ${t.label}", modifier = Modifier.clickable { open(t) }) {
                Text(t.goal, color = Tokens.Palette.Ink)
                EvidenceText(t, e)
                Text(tr("คลิกเพื่อฝึกช่วงนี้ · เลือกทบทวนหรือข้ามช่วงได้", "Click to practise this section · review or skip any section", "クリックでこの区間を練習 · 復習や飛ばすのも自由"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }
        }
        PageCard(title = tr("ฝึกอิสระและดูโน้ต", "Free practice and viewing the music", "自由練習と楽譜を見る")) {
            Text(tr("เลือกมือ ช่วง และความเร็วเองได้ ผลจะนับในแผนเฉพาะเมื่อช่วง/มือ/โหมดตรงกับเป้าหมาย", "Choose hand, section and tempo yourself; results count in the plan only when section/hand/mode match a goal", "手、区間、テンポを自由に選べます。区間・手・モードが目標と一致したときだけプランに数えます"), color = Tokens.Palette.InkMuted)
            TextButton(onClick = { app.nav.open(Screen.Reader(ReaderArgs(pieceId = HeroicPlan.EASY, hand = HandChoice.RIGHT))) }) { Text(tr("เปิดฉบับง่ายใน C", "Open the easy edition in C", "Cのやさしい版を開く")) }
            TextButton(onClick = { app.nav.open(Screen.Reader(ReaderArgs(pieceId = HeroicPlan.THEME, hand = HandChoice.RIGHT))) }) { Text(tr("เปิดฉบับฝึกใน A♭", "Open the practice edition in A♭", "A♭の練習版を開く")) }
            TextButton(onClick = { source = !source }) { Text(tr("ที่มาและสิ่งที่เรียบเรียงใหม่", "Source and what was re-arranged", "出典と編曲した点")) }
            if (source) listOf(HeroicPlan.EASY, HeroicPlan.THEME).forEach { id ->
                val f = app.services.pieces.pieceFile(id)
                Text(app.services.pieces.piece(id)?.title ?: id, fontWeight = FontWeight.SemiBold)
                Text(f?.let { "${it.composer} · ${it.source}" } ?: tr("ไม่พบไฟล์เพลง", "Song file not found", "曲のファイルが見つかりません"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }
        }
        Text(tr("ก้าวต่อไปของเพลงเต็ม: บทนำ ส่วนอื่น และเทคนิคอ็อกเทฟ ยังไม่รวมในผลแผนนี้ โปรแกรมไม่ได้วัดนิ้ว เพดัล น้ำหนักเสียง หรือรับรองว่าเล่นต้นฉบับได้", "Next steps for the full piece: the introduction, other sections and octave technique aren't in this plan's results yet. The app doesn't measure fingering, pedal or dynamics, or certify you can play the original", "全曲への次のステップ：序奏、他の部分、オクターブ奏法はまだこのプランの結果に入っていません。アプリは指使い、ペダル、強弱を測らず、原曲が弾けることを保証しません"),
            fontSize = 13.sp, color = Tokens.Palette.InkMuted)
    }
}

@Composable
fun EvidenceText(t: HeroicPlan.Target, e: StudyEvidence) {
    fun percent(v: Double) = "${(v * 100).toInt()}%"
    Text(when {
        t.completed(e) -> if (t.mode == PlayMode.WAIT) tr("อ่านจบในโหมดรอแล้ว · ยังไม่ได้วัดจังหวะ", "Read through in wait mode · timing not measured yet", "待ちモードで最後まで読めました · リズムはまだ測っていません") else tr("ทำได้ตามเกณฑ์ใน ${e.days.distinct().size} วัน", "Criteria met on ${e.days.distinct().size} days", "${e.days.distinct().size}日で基準達成")
        e.attempts == 0 -> tr("ยังไม่มีผลของช่วง/มือนี้", "No results for this section/hand yet", "この区間・手の結果はまだありません")
        t.mode == PlayMode.WAIT -> tr("ลองแล้ว ${e.attempts} รอบ · ยังอ่านไม่ครบตามเกณฑ์", "Tried ${e.attempts} rounds · not read through to the criteria yet", "${e.attempts}回挑戦 · まだ基準どおり最後まで読めていません")
        else -> tr("สะอาดต่อเนื่อง ${e.streak}/3 รอบ · วันที่ทำได้ ${e.days.distinct().size}/2 วัน", "Clean in a row ${e.streak}/3 · days done ${e.days.distinct().size}/2", "連続クリーン ${e.streak}/3回 · 達成日 ${e.days.distinct().size}/2日")
    }, color = Tokens.Palette.Ink)
    if (e.lastDay != null) {
        Text("ล่าสุด ${e.lastDay} · โน้ต ${e.lastAccuracy?.let(::percent) ?: "—"}" +
            (e.lastTiming?.let { tr(" · จังหวะ ${percent(it)}", " · timing ${percent(it)}", " · リズム ${percent(it)}") } ?: tr(" · ไม่ได้วัดจังหวะ", " · timing not measured", " · リズム未測定")) + (e.lastBpm?.let { " · $it bpm" } ?: ""), color = Tokens.Palette.InkMuted)
        e.bestCleanBpm?.let { Text(tr("ความเร็วสะอาดของช่วง/มือนี้: $it bpm", "Clean tempo for this section/hand: $it bpm", "この区間・手のクリーンなテンポ：$it bpm"), color = Tokens.Palette.InkMuted) }
        if (e.troubleMeasures.isNotEmpty()) Text(tr("ควรทวนห้อง ${e.troubleMeasures.joinToString { (it + 1).toString() }} ของฉบับฝึก", "Review bars ${e.troubleMeasures.joinToString { (it + 1).toString() }} of the practice edition", "練習版の${e.troubleMeasures.joinToString { (it + 1).toString() }}小節を復習"), color = Tokens.Palette.InkMuted)
    }
}
