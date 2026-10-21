package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.desktop.session.HandChoice
import com.id.mimikopi.desktop.session.Phase
import com.id.mimikopi.desktop.session.ReaderArgs
import com.id.mimikopi.desktop.session.ReaderSession
import com.id.mimikopi.desktop.session.ReaderUi
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.components.PianoKeys
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.Segmented
import com.id.mimikopi.desktop.ui.components.StopButton
import com.id.mimikopi.desktop.ui.score.LiveScore
import com.id.mimikopi.desktop.ui.theme.Tokens

/**
 * The score player. While playing, the window belongs to the music: one slim bar (what runs, Stop) and the score as
 * large as the window allows; the on-screen keys appear only without a MIDI piano. Setup and results sit above the
 * same large score, and clicking a bar picks the section to practise.
 */
@Composable
fun ReaderScreen(args: ReaderArgs) {
    val app = LocalApp.current
    val s = app.services
    val scope = rememberCoroutineScope()
    val r = remember(args) { ReaderSession(s, args, scope) }
    val ui by r.ui.collectAsState()
    val piece by r.piece.collectAsState()
    val pressed by r.pressed.collectAsState()
    val ports by s.midi.ports.collectAsState()
    val lowC by s.keysLowC.collectAsState()
    val nameStyle by s.nameStyle.collectAsState()
    val midi = ports.any { it.open }
    DisposableEffect(r) {
        r.onStart()
        app.keys.onSpace = {
            when (r.ui.value.phase) {
                Phase.IDLE -> r.start()
                Phase.RESULT -> if (!r.benchDone) r.start()
                else -> r.stop()
            }
        }
        app.keys.onEscape = {
            val running = r.ui.value.phase == Phase.PLAYING || r.ui.value.phase == Phase.COUNT_IN || r.ui.value.phase == Phase.LISTENING
            if (running) r.stop()
            running
        }
        onDispose {
            app.keys.onSpace = null
            app.keys.onEscape = null
            r.dispose()
        }
    }
    val p = piece
    val running = ui.phase == Phase.PLAYING || ui.phase == Phase.COUNT_IN || ui.phase == Phase.LISTENING
    Column(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
        if (running) PlayingBar(r, ui, midi) else TopBar(r, ui, midi)
        if (p == null) {
            Text("ไม่พบโน้ตของแบบฝึกนี้", modifier = Modifier.padding(24.dp))
            return@Column
        }
        if (!running) {
            when (ui.phase) {
                Phase.RESULT -> ResultBar(r, ui)
                else -> SetupBar(r, ui)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LiveScore(
                score = p.score,
                cursorBeat = r::cursorAt,
                playing = running,
                results = ui.results,
                wrongMarks = ui.wrongMarks,
                loop = if (ui.section != (0 until p.score.measures)) ui.section else null,
                hotMeasures = ui.hotMeasures,
                showNames = ui.showNames,
                nameStyle = nameStyle,
                onMeasureTap = r::onMeasureTap,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                hideNotes = ui.options.hideNotes && ui.phase != Phase.RESULT,
            )
            ui.countIn?.let { n ->
                Box(Modifier.align(Alignment.TopEnd).padding(24.dp).clip(RoundedCornerShape(24.dp)).background(Tokens.Palette.Ink).padding(horizontal = 28.dp, vertical = 8.dp)) {
                    Text("$n", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.OnInk)
                }
            }
            ui.previewLeft?.let { n ->
                Box(Modifier.align(Alignment.TopEnd).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Tokens.Palette.Group).padding(16.dp)) {
                    Text("ดูโน้ตก่อน $n วินาที", fontSize = 20.sp, color = Tokens.Palette.Ink)
                }
            }
        }
        if (!midi && ui.options.showKeyboard && ui.phase != Phase.RESULT) {
            PianoKeys(
                range = keyboardRange(p.keyRange),
                pressed = pressed,
                onKey = { n, down -> s.hub.dispatch(keyEvent(n, down)) },
                labels = if (ui.showNames) nameStyle else null,
                computerKeysLowC = lowC,
                height = 140.dp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TopBar(r: ReaderSession, ui: ReaderUi, midi: Boolean) {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("‹ กลับ", onClick = app.nav::back)
        Text(r.title(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Text(if (midi) "MIDI · คีย์บนจอซ่อน" else "ไม่มี MIDI · คลิก/แป้นพิมพ์", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        SecondaryButton(if (app.isFullscreen()) "ออกจากเต็มจอ (F11)" else "เต็มจอ (F11)", onClick = app.toggleFullscreen)
    }
    ui.message?.let { m ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m, fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
            SecondaryButton("ปิด", onClick = r::dismissMessage)
        }
    }
}

@Composable
private fun PlayingBar(r: ReaderSession, ui: ReaderUi, midi: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val what = when (ui.phase) {
            Phase.LISTENING -> "กำลังเล่นตัวอย่าง"
            Phase.COUNT_IN -> "นับเข้า"
            else -> if (ui.options.mode == PlayMode.WAIT) "โหมดรอ · เล่นตามโน้ตทีละตัว" else "ตามจังหวะ"
        }
        Text("$what · ${ui.options.hands.title} · ${ui.bpm} bpm · ห้อง ${ui.section.first + 1}–${ui.section.last + 1}" + (if (midi) " · MIDI" else ""),
            fontSize = 14.sp, color = Tokens.Palette.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (ui.phase != Phase.LISTENING) SecondaryButton(if (ui.showNames) "ชื่อโน้ต: เปิด" else "ชื่อโน้ต", onClick = r::toggleNames)
        StopButton(r::stop)
    }
}

@Composable
private fun SetupBar(r: ReaderSession, ui: ReaderUi) {
    val score = r.score ?: return
    val test = r.source is ReaderSession.Source.Benchmark || r.source is ReaderSession.Source.Check || r.source is ReaderSession.Source.Exam
    val canPace = !r.fixed || r.source is ReaderSession.Source.NewReading
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(listOf(PlayMode.WAIT, PlayMode.TEMPO), ui.options.mode, { if (it == PlayMode.WAIT) "รอ (อ่านทีละตัว)" else "ตามจังหวะ" },
                r::setMode, Modifier.width(300.dp), enabled = canPace)
            Segmented(HandChoice.entries.filter { h -> h.hands.all { hand -> score.events.any { it.hand == hand } } }.ifEmpty { listOf(HandChoice.RIGHT) },
                ui.options.hands, { it.title }, r::setHands, Modifier.width(300.dp), enabled = !r.fixed)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("−", onClick = { r.changeBpm(-5) }, enabled = canPace && ui.options.mode == PlayMode.TEMPO)
                Text("${ui.bpm} bpm", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 10.dp))
                SecondaryButton("+", onClick = { r.changeBpm(5) }, enabled = canPace && ui.options.mode == PlayMode.TEMPO)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val whole = ui.section == (0 until score.measures)
            Text(if (whole) "ทั้งเพลง ${score.measures} ห้อง" else "ห้อง ${ui.section.first + 1}–${ui.section.last + 1}" +
                (if (ui.pendingSectionStart != null) " · คลิกห้องสุดท้ายของช่วง" else ""), fontSize = 15.sp)
            if (!r.fixed && !whole) SecondaryButton("ทั้งเพลง", onClick = r::wholePiece)
            if (!r.fixed) Text("คลิกห้องบนโน้ตเพื่อเลือกช่วง", fontSize = 13.sp, color = Tokens.Palette.Dim)
            if (!r.fixed && ui.options.mode == PlayMode.TEMPO) Toggle("ความเร็วตามแผน", ui.options.useRamp, r::setUseRamp)
            if (r.source !is ReaderSession.Source.Exam && score.events.any { it.hand == Hand.LEFT } && score.events.any { it.hand == Hand.RIGHT })
                Toggle("โปรแกรมเล่นมืออื่น", ui.options.playOtherHand, r::setPlayOtherHand)
            if (ui.options.mode == PlayMode.TEMPO) Toggle("คลิก", ui.options.metronome, r::setMetronome)
            Toggle("คีย์บนจอ", ui.options.showKeyboard, r::setShowKeyboard)
        }
        if (ui.hotMeasures.isNotEmpty() && !r.fixed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("จุดที่พลาดบ่อย:", fontSize = 14.sp, color = Tokens.Palette.InkMuted)
            ui.hotMeasures.take(4).forEach { m -> SecondaryButton("ห้อง ${m + 1}", onClick = { r.practiseMeasures(m, m) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(if (r.nextLabelIsNew()) "เริ่มโน้ตชุดใหม่ (Space)" else "เริ่ม (Space)", onClick = r::start, modifier = Modifier.widthIn(max = 300.dp))
            if (!test) SecondaryButton("ฟังตัวอย่าง", onClick = r::listen)
            SecondaryButton(if (ui.showNames) "ชื่อโน้ต: เปิด (นับเป็นตัวช่วย)" else "แสดงชื่อโน้ต", onClick = r::toggleNames)
        }
    }
}

@Composable
private fun ResultBar(r: ReaderSession, ui: ReaderUi) {
    val app = LocalApp.current
    val res = ui.result ?: return
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(res.headline, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        if (res.details.isNotEmpty()) Text(res.details, fontSize = 15.sp, color = Tokens.Palette.InkMuted)
        res.progressLine?.let { Text(it, fontSize = 17.sp, color = Tokens.Palette.Ink) }
        val next = res.nextStep
        next?.let { c ->
            Row(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Tokens.Palette.Ink, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("แก้ก่อน: ${c.text}", fontSize = 16.sp, color = Tokens.Palette.Ink, modifier = Modifier.widthIn(max = 700.dp))
                if (c.action != null && !r.fixed) SecondaryButton("ฝึกตรงนี้", onClick = { r.applyAction(c.action!!) })
                if (c.checkLatency) SecondaryButton("วัดความหน่วง", onClick = { app.nav.open(com.id.mimikopi.desktop.ui.Screen.Latency) })
            }
        }
        res.coach.filter { it != next }.forEach { Text("• ${it.text}", fontSize = 15.sp, color = Tokens.Palette.Ink) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.benchDone) PrimaryButton("เสร็จ", onClick = app.nav::back, modifier = Modifier.widthIn(max = 260.dp))
            else PrimaryButton(if (r.nextLabelIsNew()) "โน้ตชุดใหม่ (Space)" else "เล่นอีกครั้ง (Space)", onClick = r::start, modifier = Modifier.widthIn(max = 260.dp))
            SecondaryButton("ตั้งค่าใหม่", onClick = r::backToSetup)
            if (!r.fixed && ui.section != (0 until (r.score?.measures ?: 0))) SecondaryButton("ลองเล่นทั้งเพลง", onClick = { r.wholePiece(); r.start() })
        }
    }
}

@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Switch(checked = on, onCheckedChange = onChange)
        Text(label, fontSize = 14.sp, color = Tokens.Palette.Ink)
    }
}
