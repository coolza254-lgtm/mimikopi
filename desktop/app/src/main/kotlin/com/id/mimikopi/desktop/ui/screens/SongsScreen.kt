package com.id.mimikopi.desktop.ui.screens

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
        LargeTitle("เพลง", "${app.services.pieces.pieces.size} เพลงในคลัง · เป้าหมาย: piano solo เพลง Hello! Project")
        PageCard(title = "เพลงส่วนตัว · ฝึกจำจากวิดีโอ") {
            Text("แบ่งวลีจากลิงก์วิดีโอ ลองเล่นเองโดยไม่ดู จดจุดติด แล้วทวนข้ามวัน · ประเมินเอง · เก็บในเครื่องนี้เท่านั้น", color = Tokens.Palette.InkMuted)
            TextButton(onClick = { app.nav.open(Screen.Recall()) }) { Text("เปิดเพลงส่วนตัว") }
        }
        PageCard(title = "คลังเพลง · ง่ายไปยาก") {
            Text("เริ่มทีละมือในโหมดรอ แล้วค่อยรวมมือและเล่นตามจังหวะ · ผ่านเมื่อเล่นสะอาดใน 2 วันที่ต่างกัน", color = Tokens.Palette.InkMuted)
            for ((i, p) in app.services.pieces.pieces.filter { it.id !in HEROIC }.withIndex()) {
                val record = progress[p.id]
                val passed = HandChoice.entries.filter { h -> record != null && PieceRules.isPassed(record, h.name) }
                Text(
                    "%02d  %s · Lv.%s%s".format(i + 1, p.title, p.level.removePrefix("L"), if (passed.isEmpty()) "" else " · ผ่าน " + passed.joinToString { it.title }),
                    fontSize = 16.sp, fontWeight = if (open == p.id) FontWeight.SemiBold else FontWeight.Normal, color = Tokens.Palette.Ink,
                    modifier = Modifier.clickable { open = if (open == p.id) null else p.id },
                )
                if (open == p.id) Row {
                    for (h in HandChoice.entries) TextButton(onClick = { app.nav.open(Screen.Reader(ReaderArgs(pieceId = p.id, hand = h))) }) { Text(h.title) }
                }
            }
        }
        Text("เพลงเสริม · Heroic Polonaise", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink)
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
        Text("Chopin · Op.53 · เริ่มจากท่อนหลัก 8 ห้อง (ต้นฉบับห้อง 17–24) ใน C แล้วค่อยเพิ่มคอร์ดใน A♭ · ทั้งสองเป็นฉบับย่อ ยังไม่ใช่เพลงเต็ม", color = Tokens.Palette.Ink)
        PageCard(title = "ฝึกต่อจากตรงนี้") {
            if (next != null) {
                val e = HeroicPlan.evidence(next, progress)
                Text(if (next.piece == HeroicPlan.EASY) "ฉบับง่ายใน C" else "ฉบับฝึกใน A♭", color = Tokens.Palette.InkMuted)
                Text(next.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink)
                Text(next.goal, color = Tokens.Palette.Ink)
                Text(if (next.mode == PlayMode.WAIT) "อ่านจบช่วงโดยไม่กดเกิน · ปิดชื่อโน้ตเมื่อวัดผล"
                else "โน้ต ≥95% · จังหวะ ≥90% · ไม่กดเกิน · สะดุดไม่เกิน 1 ครั้ง · ความคลาดเคลื่อนไม่เกิน ±120 ms", color = Tokens.Palette.InkMuted)
                if (e.attempts > 0) EvidenceText(next, e)
                PrimaryButton("เริ่มช่วงนี้", onClick = { open(next) })
            } else {
                Text("ทำครบแผนท่อนหลักแล้ว · ทบทวนและฟังน้ำหนักเสียงบนเปียโนจริง ก่อนขยายไปส่วนอื่นของเพลง", color = Tokens.Palette.Ink)
                PrimaryButton("ทบทวนท่อนหลักใน A♭", onClick = { HeroicPlan.target("ab-whole")?.let(::open) })
            }
        }
        PageCard(title = "แผนรอบนี้ · ประมาณ 15 นาที") {
            Text("2 นาที · ฟังท่อนที่จะฝึก (ปุ่มฟังตัวอย่าง) และนับ 1–2–3 ตามจังหวะ Polonaise")
            Text("5 นาที · อ่านแยกมือทีละ 2 ห้องในโหมดรอ ถ้าติดให้ลดเหลือห้องเดียว")
            Text("6 นาที · รวมมือช้า ๆ ที่ 48 bpm แล้ววนรอยต่อ ก่อนเป้าความเร็วสุดท้าย ค่อยเพิ่มครั้งละ 3–5 bpm")
            Text("2 นาที · ลองช่วงเดิมโดยไม่เปิดชื่อโน้ต แล้วดูห้องที่ควรแก้จากผลจริง")
            Text("เป็นเวลาที่แนะนำ ไม่ใช่เวลาฝึกที่บันทึกอัตโนมัติ", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }
        PageCard(title = "ผลของท่อนหลัก") {
            Text("ทำได้ตามเกณฑ์ $done จาก ${HeroicPlan.targets.size} เป้าหมายฝึก", color = Tokens.Palette.Ink)
            Text("โหมดรอวัดการอ่านเท่านั้น · โหมดตามจังหวะต้องเล่นสะอาด 3 รอบติดใน 2 วันที่ต่างกัน ไม่ใช้ชื่อโน้ตหรือโน้ตไหลช่วย", color = Tokens.Palette.InkMuted)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "ย่อแผนและผลรายช่วง" else "ดูแผนและผลรายช่วง") }
        }
        if (expanded) for ((i, t) in HeroicPlan.targets.withIndex()) {
            val e = HeroicPlan.evidence(t, progress)
            PageCard(title = "${i + 1}. ${if (t.piece == HeroicPlan.EASY) "C · เบสง่าย" else "A♭ · คอร์ด"} · ${t.label}", modifier = Modifier.clickable { open(t) }) {
                Text(t.goal, color = Tokens.Palette.Ink)
                EvidenceText(t, e)
                Text("คลิกเพื่อฝึกช่วงนี้ · เลือกทบทวนหรือข้ามช่วงได้", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }
        }
        PageCard(title = "ฝึกอิสระและดูโน้ต") {
            Text("เลือกมือ ช่วง และความเร็วเองได้ ผลจะนับในแผนเฉพาะเมื่อช่วง/มือ/โหมดตรงกับเป้าหมาย", color = Tokens.Palette.InkMuted)
            TextButton(onClick = { app.nav.open(Screen.Reader(ReaderArgs(pieceId = HeroicPlan.EASY, hand = HandChoice.RIGHT))) }) { Text("เปิดฉบับง่ายใน C") }
            TextButton(onClick = { app.nav.open(Screen.Reader(ReaderArgs(pieceId = HeroicPlan.THEME, hand = HandChoice.RIGHT))) }) { Text("เปิดฉบับฝึกใน A♭") }
            TextButton(onClick = { source = !source }) { Text("ที่มาและสิ่งที่เรียบเรียงใหม่") }
            if (source) listOf(HeroicPlan.EASY, HeroicPlan.THEME).forEach { id ->
                val f = app.services.pieces.pieceFile(id)
                Text(app.services.pieces.piece(id)?.title ?: id, fontWeight = FontWeight.SemiBold)
                Text(f?.let { "${it.composer} · ${it.source}" } ?: "ไม่พบไฟล์เพลง", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }
        }
        Text("ก้าวต่อไปของเพลงเต็ม: บทนำ ส่วนอื่น และเทคนิคอ็อกเทฟ ยังไม่รวมในผลแผนนี้ โปรแกรมไม่ได้วัดนิ้ว เพดัล น้ำหนักเสียง หรือรับรองว่าเล่นต้นฉบับได้",
            fontSize = 13.sp, color = Tokens.Palette.InkMuted)
    }
}

@Composable
fun EvidenceText(t: HeroicPlan.Target, e: StudyEvidence) {
    fun percent(v: Double) = "${(v * 100).toInt()}%"
    Text(when {
        t.completed(e) -> if (t.mode == PlayMode.WAIT) "อ่านจบในโหมดรอแล้ว · ยังไม่ได้วัดจังหวะ" else "ทำได้ตามเกณฑ์ใน ${e.days.distinct().size} วัน"
        e.attempts == 0 -> "ยังไม่มีผลของช่วง/มือนี้"
        t.mode == PlayMode.WAIT -> "ลองแล้ว ${e.attempts} รอบ · ยังอ่านไม่ครบตามเกณฑ์"
        else -> "สะอาดต่อเนื่อง ${e.streak}/3 รอบ · วันที่ทำได้ ${e.days.distinct().size}/2 วัน"
    }, color = Tokens.Palette.Ink)
    if (e.lastDay != null) {
        Text("ล่าสุด ${e.lastDay} · โน้ต ${e.lastAccuracy?.let(::percent) ?: "—"}" +
            (e.lastTiming?.let { " · จังหวะ ${percent(it)}" } ?: " · ไม่ได้วัดจังหวะ") + (e.lastBpm?.let { " · $it bpm" } ?: ""), color = Tokens.Palette.InkMuted)
        e.bestCleanBpm?.let { Text("ความเร็วสะอาดของช่วง/มือนี้: $it bpm", color = Tokens.Palette.InkMuted) }
        if (e.troubleMeasures.isNotEmpty()) Text("ควรทวนห้อง ${e.troubleMeasures.joinToString { (it + 1).toString() }} ของฉบับฝึก", color = Tokens.Palette.InkMuted)
    }
}
