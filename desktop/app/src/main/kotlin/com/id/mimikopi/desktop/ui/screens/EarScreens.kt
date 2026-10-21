package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.desktop.session.EarArgs
import com.id.mimikopi.desktop.session.EarAudioStage
import com.id.mimikopi.desktop.session.EarPhase
import com.id.mimikopi.desktop.session.EarSession
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.components.Group
import com.id.mimikopi.desktop.ui.components.GroupDivider
import com.id.mimikopi.desktop.ui.components.GroupFooter
import com.id.mimikopi.desktop.ui.components.GroupHeader
import com.id.mimikopi.desktop.ui.components.GroupRow
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.LevelBars
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.StepMark
import com.id.mimikopi.desktop.ui.theme.Tokens
import java.time.LocalDate

/** The ear course home: the next round, 30 days of first-try accuracy, each kind of listening, the 10-stage path. */
@Composable
fun EarHomeScreen() {
    val app = LocalApp.current
    val s = app.services
    val checks by s.progress.checks.collectAsState()
    val state = remember { EarProgress.parse(s.db.get(EarProgress.KEY)) }
    val today = LocalDate.now()
    val stage = EarProgress.currentStage(state)
    val level = SkillBook.status(SkillId.EAR, checks, today).level
    val daily = remember(state) { EarProgress.daily(state, today, 30).toMap() }
    val days = (29 downTo 0).map { daily[today.minusDays(it.toLong())] }
    val nextKinds = remember(state) { EarProgress.plan(state, System.currentTimeMillis(), kotlin.random.Random(today.toEpochDay())).map { EarCourse.kindOf(it) }.distinct() }
    val overview = remember(state) { EarProgress.overview(state) }
    val weakest = remember(state) { EarProgress.weakest(state) }
    Page {
        LargeTitle("ฝึกหู", "ระดับหู $level จาก 8 · ขั้น ${stage.n} จาก 10")
        Group {
            Column(Modifier.padding(20.dp)) {
                Text("รอบถัดไป · ขั้น ${stage.n}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.InkMuted)
                Text(stage.title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text(stage.about, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.InkMuted)
                Text("${EarProgress.ROUND} ข้อ ราว 4 นาที: " + nextKinds.joinToString(" · ") { it.title }, fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(18.dp))
                PrimaryButton("เริ่มฝึก", { app.nav.open(Screen.Ear(EarArgs())) })
            }
        }
        GroupFooter("โจทย์ → เว้นช่วง → ตอบ → เฉลย เล่นทีละช่วงเสมอ คีย์ที่กดตอบไม่ส่งเสียงทับโจทย์หรือเฉลย")
        GroupHeader("30 วันล่าสุด")
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    days.forEach { a ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                            if (a == null) Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.dp)).background(Tokens.Palette.Line))
                            else Box(Modifier.fillMaxWidth().fillMaxHeight(a.toFloat().coerceIn(0.06f, 1f)).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Ink))
                        }
                    }
                }
                val heard = days.filterNotNull()
                Text(if (heard.isEmpty()) "ยังไม่ได้ฝึกหูในช่วงนี้" else "ฝึก ${heard.size} วัน · ถูกครั้งแรกเฉลี่ย ${(heard.average() * 100).toInt()}%",
                    fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 12.dp))
            }
        }
        GroupHeader("หูของฉันตอนนี้")
        Group {
            overview.entries.toList().forEachIndexed { i, (kind, items) ->
                if (i > 0) GroupDivider()
                val known = items.count { it.stat.mastered }
                val asked = items.sumOf { it.stat.asked }
                val right = items.sumOf { it.stat.right }
                GroupRow(kind.title, "แม่นแล้ว $known จาก ${items.size}" + if (asked > 0) " · ถูกครั้งแรก ${right * 100 / asked}%" else " · ยังไม่ได้ฝึก",
                    onClick = { app.nav.open(Screen.Ear(EarArgs(focus = kind))) }, trailing = { LevelBars(known, items.size, width = 7.dp) })
            }
        }
        if (weakest.isNotEmpty()) {
            GroupHeader("ยังพลาดบ่อย")
            Group {
                weakest.forEachIndexed { i, w ->
                    if (i > 0) GroupDivider()
                    GroupRow(w.label, "${w.kind.title} · ถูก ${w.stat.right} จาก ${w.stat.asked} ครั้ง", onClick = { app.nav.open(Screen.Ear(EarArgs(focus = w.kind))) })
                }
            }
        }
        GroupHeader("เส้นทาง 10 ขั้น")
        Group {
            EarCourse.STAGES.forEachIndexed { i, st ->
                if (i > 0) GroupDivider(52.dp)
                val (known, total) = EarProgress.stageProgress(state, st)
                val open = st.n <= stage.n
                GroupRow("ขั้น ${st.n} · ${st.title}",
                    if (open) "แม่นแล้ว $known จาก $total · " + st.adds.joinToString(" ") { EarCourse.label(it) } else st.about,
                    leading = { StepMark(known == total, st.n == stage.n && known < total) },
                    titleColor = if (open) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
        GroupFooter("ตัวเลขทั้งหมดนับเฉพาะคำตอบครั้งแรก · ระดับหูวัดจากแบบทดสอบในหน้าทักษะ")
    }
}

/** One ear round, full-window. Space goes on after the explanation; Esc leaves (the round pauses). */
@Composable
fun EarSessionScreen(args: EarArgs) {
    val app = LocalApp.current
    val s = app.services
    val scope = rememberCoroutineScope()
    val e = remember(args) { EarSession(s, args, scope) }
    val ui by e.ui.collectAsState()
    val ports by s.midi.ports.collectAsState()
    DisposableEffect(e) {
        e.onStart()
        app.keys.onSpace = {
            val u = e.ui.value
            when (u.phase) {
                EarPhase.FEEDBACK -> e.next()
                EarPhase.ANSWER -> if (u.audio == EarAudioStage.IDLE) e.replay()
                else -> Unit
            }
        }
        onDispose {
            app.keys.onSpace = null
            e.dispose()
        }
    }
    EarSessionContent(ui, ports.any { it.open }, app.nav::back, e::choose, e::replay, e::replayCadence, e::undo, e::next, e::retryAudio) {
        Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
            ui.message?.let {
                Text(it, fontSize = 17.sp, color = Tokens.Palette.Ink)
                Spacer(Modifier.height(24.dp))
                PrimaryButton("เสร็จ", app.nav::back)
                return@Column
            }
            val sum = ui.summary ?: return@Column
            sum.verdict.firstOrNull()?.let { Text(it, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink) }
            sum.verdict.drop(1).forEach { Text(it, fontSize = 15.sp, color = Tokens.Palette.InkMuted) }
            val pct = if (sum.asked == 0) 0 else sum.right * 100 / sum.asked
            Text("$pct%", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            Text("ถูกตั้งแต่ครั้งแรก ${sum.right} จาก ${sum.asked} ข้อ", fontSize = 16.sp, color = Tokens.Palette.InkMuted)
            sum.byKind.forEach { (k, rn) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(k.title, fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                    Text("${rn.first}/${rn.second}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
                }
            }
            if (sum.better.isNotEmpty()) Text("แม่นขึ้น: " + sum.better.joinToString(" · "), fontSize = 15.sp, color = Tokens.Palette.Ink)
            if (sum.missed.isNotEmpty()) Text("ยังพลาด: " + sum.missed.joinToString(" · ") + " (รอบหน้าจะมาบ่อยขึ้น)", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
            Spacer(Modifier.height(28.dp))
            PrimaryButton(if (ui.test) "เสร็จ" else "อีกรอบ", if (ui.test) app.nav::back else e::again)
            if (!ui.test) SecondaryButton("เสร็จ", app.nav::back)
        }
    }
}
