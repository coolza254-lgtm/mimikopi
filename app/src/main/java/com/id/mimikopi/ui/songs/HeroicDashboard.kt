package com.id.mimikopi.ui.songs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.playalong.*
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.theme.Tokens

/** One song, two study arrangements; all progress below is evidence for this excerpt only. */
@Composable
internal fun HeroicDashboard(
    progress: Map<String, PieceProgress>,
    onTarget: (String) -> Unit,
    onFree: (String, String) -> Unit,
    onSource: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val next = HeroicPlan.next(progress)
    val done = HeroicPlan.targets.count { it.completed(HeroicPlan.evidence(it, progress)) }
    Text("Heroic Polonaise", style = MaterialTheme.typography.headlineLarge, color = Tokens.Palette.Ink)
    Text("Chopin · Op.53 · เพลงเป้าหมายของฉัน", style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.InkMuted)
    Text("เริ่มจากท่อนหลัก 8 ห้อง (ต้นฉบับห้อง 17–24) ใน C แล้วค่อยเพิ่มคอร์ดใน A♭ · ทั้งสองเป็นฉบับย่อ ยังไม่ใช่เพลงเต็ม", color = Tokens.Palette.Ink)
    PageCard(title = "ฝึกต่อจากตรงนี้") {
        if (next != null) {
            val e = HeroicPlan.evidence(next, progress)
            Text(if (next.piece == HeroicPlan.EASY) "ฉบับง่ายใน C" else "ฉบับฝึกใน A♭", color = Tokens.Palette.InkMuted)
            Text(next.label, style = MaterialTheme.typography.titleLarge, color = Tokens.Palette.Ink)
            Text(next.goal, color = Tokens.Palette.Ink)
            Text(if (next.mode == PlayMode.WAIT) "อ่านจบช่วงโดยไม่กดเกิน · ปิดชื่อโน้ตเมื่อวัดผล" else "โน้ต ≥95% · จังหวะ ≥90% · ไม่กดเกิน · สะดุดไม่เกิน 1 ครั้ง · ความคลาดเคลื่อนไม่เกิน ±120 ms", color = Tokens.Palette.InkMuted)
            if (e.attempts > 0) EvidenceText(next, e)
            Button(onClick = { onTarget(next.id) }, modifier = Modifier.fillMaxWidth().testTag("heroic-next")) { Text("เริ่มช่วงนี้") }
        } else {
            Text("ทำครบแผนท่อนหลักแล้ว · ทบทวนและฟังน้ำหนักเสียงบนเปียโนจริง ก่อนขยายไปส่วนอื่นของเพลง", color = Tokens.Palette.Ink)
            Button(onClick = { onTarget("ab-whole") }, modifier = Modifier.testTag("heroic-review")) { Text("ทบทวนท่อนหลักใน A♭") }
        }
    }
    PageCard(title = "แผนรอบนี้ · ประมาณ 15 นาที") {
        Text("2 นาที · ฟังท่อนที่จะฝึกและนับ 1–2–3 ตามจังหวะ Polonaise", color = Tokens.Palette.Ink)
        Text("5 นาที · อ่านแยกมือทีละ 2 ห้องในโหมดรอ ถ้าติดให้ลดเหลือห้องเดียว", color = Tokens.Palette.Ink)
        Text("6 นาที · รวมมือช้า ๆ ที่ 48 bpm แล้ววนรอยต่อ ก่อนเป้าความเร็วสุดท้าย ค่อยเพิ่มครั้งละ 3–5 bpm", color = Tokens.Palette.Ink)
        Text("2 นาที · ลองช่วงเดิมโดยไม่เปิดชื่อโน้ต แล้วดูห้องที่ควรแก้จากผลจริง", color = Tokens.Palette.Ink)
        Text("เป็นเวลาที่แนะนำ ไม่ใช่เวลาฝึกที่บันทึกอัตโนมัติ", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
    }
    PageCard(title = "ผลของท่อนหลัก") {
        Text("ทำได้ตามเกณฑ์ $done จาก ${HeroicPlan.targets.size} เป้าหมายฝึก", modifier = Modifier.testTag("heroic-progress"), color = Tokens.Palette.Ink)
        Text("โหมดรอวัดการอ่านเท่านั้น · โหมดตามจังหวะต้องเล่นสะอาด 3 รอบติดใน 2 วันที่ต่างกัน ไม่ใช้ชื่อโน้ตหรือโน้ตไหลช่วย", color = Tokens.Palette.InkMuted)
        Text("ผลเดิมยังเก็บไว้ แต่ไม่ใช้ความเร็วดีที่สุดจากช่วงอื่นมาผ่านเป้าหมายนี้", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("heroic-plan-toggle")) { Text(if (expanded) "ย่อแผนและผลรายช่วง" else "ดูแผนและผลรายช่วง") }
    }
    if (expanded) {
        for ((i, t) in HeroicPlan.targets.withIndex()) {
            val e = HeroicPlan.evidence(t, progress)
            PageCard(title = "${i + 1}. ${if (t.piece == HeroicPlan.EASY) "C · เบสง่าย" else "A♭ · คอร์ด"} · ${t.label}",
                modifier = Modifier.testTag("heroic-target-${t.id}"), onClick = { onTarget(t.id) }) {
                Text(t.goal, color = Tokens.Palette.Ink)
                EvidenceText(t, e)
                Text("แตะเพื่อฝึกช่วงนี้ · เลือกทบทวนหรือข้ามช่วงได้", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            }
        }
    }
    PageCard(title = "ฝึกอิสระและดูโน้ต") {
        Text("เลือกมือ ช่วง และความเร็วเองได้ ผลจะนับในแผนเฉพาะเมื่อช่วง/มือ/โหมดตรงกับเป้าหมาย", color = Tokens.Palette.InkMuted)
        TextButton(onClick = { onFree(HeroicPlan.EASY, "RIGHT") }, modifier = Modifier.testTag("heroic-free-easy")) { Text("เปิดฉบับง่ายใน C") }
        TextButton(onClick = { onFree(HeroicPlan.THEME, "RIGHT") }) { Text("เปิดฉบับฝึกใน A♭") }
        TextButton(onClick = onSource) { Text("ที่มาและสิ่งที่เรียบเรียงใหม่") }
    }
    Text("ก้าวต่อไปของเพลงเต็ม: บทนำ ส่วนอื่น และเทคนิคอ็อกเทฟ ยังไม่รวมในผลแผนนี้ แอปไม่ได้วัดนิ้ว เพดัล น้ำหนักเสียง หรือรับรองว่าเล่นต้นฉบับได้", color = Tokens.Palette.InkMuted)
}

@Composable
private fun EvidenceText(t: HeroicPlan.Target, e: StudyEvidence) {
    fun percent(v: Double) = "${(v * 100).toInt()}%"
    Text(when {
        t.completed(e) -> if (t.mode == PlayMode.WAIT) "อ่านจบในโหมดรอแล้ว · ยังไม่ได้วัดจังหวะ" else "ทำได้ตามเกณฑ์ใน ${e.days.distinct().size} วัน"
        e.attempts == 0 -> "ยังไม่มีผลของช่วง/มือนี้"
        t.mode == PlayMode.WAIT -> "ลองแล้ว ${e.attempts} รอบ · ยังอ่านไม่ครบตามเกณฑ์"
        else -> "สะอาดต่อเนื่อง ${e.streak}/3 รอบ · วันที่ทำได้ ${e.days.distinct().size}/2 วัน"
    }, color = Tokens.Palette.Ink)
    if (e.lastDay != null) {
        Text("ล่าสุด ${e.lastDay} · โน้ต ${e.lastAccuracy?.let(::percent) ?: "—"}" +
            (e.lastTiming?.let { " · จังหวะ ${percent(it)}" } ?: " · ไม่ได้วัดจังหวะ") +
            (e.lastBpm?.let { " · $it bpm" } ?: ""), color = Tokens.Palette.InkMuted)
        e.bestCleanBpm?.let { Text("ความเร็วสะอาดของช่วง/มือนี้: $it bpm", color = Tokens.Palette.InkMuted) }
        if (e.troubleMeasures.isNotEmpty()) Text("ควรทวนห้อง ${e.troubleMeasures.joinToString { (it + 1).toString() }} ของฉบับฝึก", color = Tokens.Palette.InkMuted)
    }
}
