package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.desktop.input.ComputerKeyboard
import com.id.mimikopi.desktop.session.HandChoice
import com.id.mimikopi.desktop.session.Phase
import com.id.mimikopi.desktop.session.ReaderArgs
import com.id.mimikopi.desktop.session.ReaderSession
import com.id.mimikopi.desktop.session.ReaderUi
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.components.FallingNote
import com.id.mimikopi.desktop.ui.components.FallingNotesView
import com.id.mimikopi.desktop.ui.components.FallingState
import com.id.mimikopi.desktop.ui.components.PianoKeys
import com.id.mimikopi.desktop.ui.components.fallingKeyRange
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
    com.id.mimikopi.desktop.ui.components.AnswerFx(ui.result?.clean, done = true)
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
        app.keys.onLeft = { if (r.ui.value.phase == Phase.PLAYING && !r.fixed) { r.replayFrom(back = true); true } else false }
        onDispose {
            app.keys.onLeft = null
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
            Text(tr("ไม่พบโน้ตของแบบฝึกนี้", "Notes for this drill not found", "この練習の楽譜が見つかりません"), modifier = Modifier.padding(24.dp))
            return@Column
        }
        if (!running) {
            when (ui.phase) {
                Phase.RESULT -> ResultBar(r, ui)
                else -> SetupBar(r, ui)
            }
        }
        val falling = ui.options.falling && !r.fixed
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (falling) FallingScore(r, ui, p.score, p.keyRange, running, midi, lowC, Modifier.fillMaxSize())
            else LiveScore(
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
                    Text(tr("ดูโน้ตก่อน $n วินาที", "Look at the music for $n s first", "まず楽譜を${n}秒見る"), fontSize = 20.sp, color = Tokens.Palette.Ink)
                }
            }
        }
        // Falling notes draw their own keys (playable with the mouse and the computer keyboard).
        if (!falling && !midi && ui.options.showKeyboard && ui.phase != Phase.RESULT) {
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
        SecondaryButton(tr("‹ กลับ", "‹ Back", "‹ 戻る"), onClick = app.nav::back)
        Text(r.title(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        SecondaryButton(if (app.isFullscreen()) tr("ออกจากเต็มจอ · F11", "Exit full screen · F11", "全画面を終了 · F11") else tr("เต็มจอ · F11", "Full screen · F11", "全画面 · F11"), onClick = app.toggleFullscreen)
    }
    ui.message?.let { m ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m, fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
            SecondaryButton(tr("ปิด", "Close", "閉じる"), onClick = r::dismissMessage)
        }
    }
}

@Composable
private fun PlayingBar(r: ReaderSession, ui: ReaderUi, midi: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val what = when (ui.phase) {
            Phase.LISTENING -> tr("กำลังเล่นตัวอย่าง", "Playing the example", "お手本を再生中")
            Phase.COUNT_IN -> tr("นับเข้า", "Count-in", "カウント")
            else -> if (ui.options.mode == PlayMode.WAIT) tr("โหมดรอ · เล่นตามโน้ตทีละตัว", "Wait mode · play note by note", "待ちモード · 1音ずつ弾く") else tr("ตามจังหวะ", "In time", "リズムに合わせて")
        }
        Text(tr("$what · ${ui.options.hands.title} · ${ui.bpm} bpm · ห้อง ${ui.section.first + 1}–${ui.section.last + 1}", "$what · ${ui.options.hands.title} · ${ui.bpm} bpm · bars ${ui.section.first + 1}–${ui.section.last + 1}", "$what · ${ui.options.hands.title} · ${ui.bpm} bpm · ${ui.section.first + 1}–${ui.section.last + 1}小節") + (if (midi) " · MIDI" else ""),
            fontSize = 14.sp, color = Tokens.Palette.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // Live accuracy, minimal and green, as in the approved falling-notes design.
        val judged = ui.results.values.filter { it != NoteResult.PENDING }
        if (judged.isNotEmpty()) {
            val good = judged.count { it == NoteResult.CORRECT || it == NoteResult.EARLY || it == NoteResult.LATE || it == NoteResult.CORRECTED }
            Text("${good * 100 / judged.size}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF4CB86E))
        }
        if (ui.phase == Phase.PLAYING && !r.fixed) {
            SecondaryButton(tr("‹ ย้อนห้อง", "‹ Back a bar", "‹ 1小節戻る"), onClick = { r.replayFrom(back = true) })
            SecondaryButton(tr("↻ เล่นห้องนี้ใหม่", "↻ Replay this bar", "↻ この小節から"), onClick = { r.replayFrom(back = false) })
        }
        if (ui.options.falling && !r.fixed) SecondaryButton(if (ui.options.fallingKeyLabels) tr("ชื่อบนคีย์: เปิด", "Names on keys: on", "鍵盤に音名：表示") else tr("ชื่อบนคีย์", "Names on keys", "鍵盤に音名"),
            onClick = { r.setFallingKeyLabels(!ui.options.fallingKeyLabels) })
        StopButton(r::stop)
    }
}

/**
 * Before a run: only what this kind of practice needs. A lesson step or a test has fixed settings, so it shows just
 * Start (and Listen where allowed); a song shows the mode, the hands and the tempo, with the rest under "more".
 */
@Composable
private fun SetupBar(r: ReaderSession, ui: ReaderUi) {
    val score = r.score ?: return
    val test = r.source is ReaderSession.Source.Benchmark || r.source is ReaderSession.Source.Check || r.source is ReaderSession.Source.Exam
    val canPace = !r.fixed || r.source is ReaderSession.Source.NewReading
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(if (r.nextLabelIsNew()) tr("เริ่มโน้ตชุดใหม่ · Space", "New music · Space", "新しい楽譜 · Space") else tr("เริ่ม · Space", "Start · Space", "開始 · Space"), onClick = r::start, modifier = Modifier.widthIn(min = 200.dp, max = 260.dp))
            if (!test) SecondaryButton(tr("ฟังตัวอย่าง", "Listen to an example", "お手本を聞く"), onClick = r::listen)
            if (canPace) Segmented(listOf(PlayMode.WAIT, PlayMode.TEMPO), ui.options.mode, { if (it == PlayMode.WAIT) tr("รอจนกดถูก", "Wait until right", "正しく押すまで待つ") else tr("ตามจังหวะ", "In time", "リズムに合わせて") },
                r::setMode, Modifier.width(260.dp))
            if (!r.fixed) Segmented(HandChoice.entries.filter { h -> h.hands.all { hand -> score.events.any { it.hand == hand && !it.isRest } } }.ifEmpty { listOf(HandChoice.RIGHT) },
                ui.options.hands, { it.title }, r::setHands, Modifier.width(260.dp))
            if (canPace && ui.options.mode == PlayMode.TEMPO) Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("−", onClick = { r.changeBpm(-5) })
                Text("${ui.bpm} bpm", fontSize = 16.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(horizontal = 10.dp))
                SecondaryButton("+", onClick = { r.changeBpm(5) })
            }
            Box(Modifier.weight(1f))
            if (!r.fixed) SecondaryButton(if (more) tr("ซ่อนตัวเลือก", "Hide options", "オプションを隠す") else tr("ตัวเลือกเพิ่ม", "More options", "その他のオプション"), onClick = { more = !more })
        }
        if (!r.fixed) {
            val whole = ui.section == (0 until score.measures)
            Text(if (whole) tr("ทั้งเพลง ${score.measures} ห้อง · คลิกห้องบนโน้ตเพื่อฝึกเฉพาะช่วง", "Whole piece ${score.measures} bars · click bars on the music to practise a section", "全曲${score.measures}小節 · 楽譜の小節をクリックして区間練習") else tr("ห้อง ${ui.section.first + 1}–${ui.section.last + 1}", "Bars ${ui.section.first + 1}–${ui.section.last + 1}", "${ui.section.first + 1}–${ui.section.last + 1}小節") +
                (if (ui.pendingSectionStart != null) tr(" · คลิกห้องสุดท้ายของช่วง", " · click the last bar of the section", " · 区間の最後の小節をクリック") else ""), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
        }
        if (more && !r.fixed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (ui.section != (0 until score.measures)) SecondaryButton(tr("ทั้งเพลง", "Whole piece", "全曲"), onClick = r::wholePiece)
            Toggle(tr("โน้ตตก", "Falling notes", "流れる音符"), ui.options.falling, r::setFalling)
            if (score.events.any { it.hand == Hand.LEFT && !it.isRest } && score.events.any { it.hand == Hand.RIGHT && !it.isRest })
                Toggle(tr("โปรแกรมเล่นมืออื่น", "App plays the other hand", "もう一方の手をアプリが演奏"), ui.options.playOtherHand, r::setPlayOtherHand)
            if (ui.options.mode == PlayMode.TEMPO) Toggle(tr("คลิก", "Click", "クリック"), ui.options.metronome, r::setMetronome)
            if (ui.options.mode == PlayMode.TEMPO) Toggle(tr("ความเร็วตามแผน", "Planned tempo", "プランのテンポ"), ui.options.useRamp, r::setUseRamp)
            if (ui.options.falling) Toggle(tr("ชื่อโน้ตบนคีย์", "Note names on keys", "鍵盤に音名"), ui.options.fallingKeyLabels, r::setFallingKeyLabels)
            else Toggle(tr("คีย์บนจอ", "On-screen keys", "画面の鍵盤"), ui.options.showKeyboard, r::setShowKeyboard)
            Toggle(tr("ชื่อโน้ต (นับเป็นตัวช่วย)", "Note names (counts as help)", "音名（補助ありとして数えます）"), ui.showNames, { r.toggleNames() })
        }
        if (ui.hotMeasures.isNotEmpty() && !r.fixed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("ห้องที่พลาดบ่อย:", "Bars often missed:", "よくミスする小節："), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
            ui.hotMeasures.take(3).forEach { m -> SecondaryButton(tr("ห้อง ${m + 1}", "Bar ${m + 1}", "${m + 1}小節"), onClick = { r.practiseMeasures(m, m) }) }
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
                Text(tr("แก้ก่อน: ${c.text}", "Fix first: ${c.text}", "まず直す：${c.text}"), fontSize = 16.sp, color = Tokens.Palette.Ink, modifier = Modifier.widthIn(max = 700.dp))
                if (c.action != null && !r.fixed) SecondaryButton(tr("ฝึกตรงนี้", "Practise here", "ここを練習"), onClick = { r.applyAction(c.action!!) })
            }
        }
        res.coach.filter { it != next }.forEach { Text("• ${it.text}", fontSize = 15.sp, color = Tokens.Palette.Ink) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.benchDone) PrimaryButton(tr("เสร็จ", "Done", "完了"), onClick = app.nav::back, modifier = Modifier.widthIn(max = 260.dp))
            else PrimaryButton(if (r.nextLabelIsNew()) tr("โน้ตชุดใหม่ (Space)", "New music (Space)", "新しい楽譜（Space）") else tr("เล่นอีกครั้ง (Space)", "Play again (Space)", "もう一度（Space）"), onClick = r::start, modifier = Modifier.widthIn(max = 260.dp))
            SecondaryButton(tr("ตั้งค่าใหม่", "Change settings", "設定を変える"), onClick = r::backToSetup)
            if (!r.fixed && ui.section != (0 until (r.score?.measures ?: 0))) SecondaryButton(tr("ลองเล่นทั้งเพลง", "Try the whole piece", "全曲を弾いてみる"), onClick = { r.wholePiece(); r.start() })
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

/**
 * The song as falling notes over a keyboard (as on the phone): bars coloured by hand fall to the keys, judged keys
 * light up hit / early / late / wrong. On the computer the keys also show the computer-keyboard letters while no MIDI
 * piano is connected, and they play with the mouse. A run in this mode does not count toward passing the piece.
 */
@Composable
private fun FallingScore(r: ReaderSession, ui: ReaderUi, score: Score, keyRange: IntRange, playing: Boolean, midi: Boolean, lowC: Int, modifier: Modifier) {
    val app = LocalApp.current
    val pressed by r.pressed.collectAsState()
    // Katakana do-re-mi on the bars (as in the tutorial videos), unless letter names are chosen.
    val barStyle = if (r.nameStyle == NoteNameStyle.LETTER) NoteNameStyle.LETTER else NoteNameStyle.SOLFEGE
    // Each heard note (ties merged) with its key in the judge's results, its hand and its bar (named once per style).
    val base = remember(score, barStyle) {
        val events = score.events.associateBy { it.id }
        score.sounding().map { n ->
            val ev = events.getValue(n.eventId)
            val i = ev.pitches.indexOfFirst { it.midi == n.midi }.coerceAtLeast(0)
            val pitch = ev.pitches.getOrNull(i) ?: SpelledPitch.fromMidiSharp(n.midi)
            Triple(NoteKey(n.eventId, i), n.hand, FallingNote(n.midi, n.onset, n.duration, left = n.hand == Hand.LEFT, label = pitch.name(barStyle)))
        }
    }
    val results = ui.results
    val hands = ui.options.hands.hands
    val notes = remember(base, results, hands) {
        base.map { (key, hand, note) ->
            val state = when (results[key]) {
                NoteResult.CORRECT, NoteResult.EARLY, NoteResult.LATE, NoteResult.CORRECTED -> FallingState.DONE
                NoteResult.MISSED -> FallingState.MISSED
                else -> if (hand in hands) FallingState.WAITING else FallingState.BACKGROUND
            }
            if (state == note.state) note else note.copy(state = state)
        }
    }
    val bars = remember(score) { (0..score.measures).map { score.measureStart(it) } }
    val range = remember(keyRange) { fallingKeyRange(keyRange.first, keyRange.last) }
    // The keyboard names only the notes of the piece's key (spelled as the key spells them), plus the computer key
    // letters while no MIDI piano is connected.
    val scale = remember(score) { com.id.mimikopi.core.score.KeySignature.scale(score.keyFifths) }
    val labels = remember(range, barStyle, midi, lowC, ui.options.fallingKeyLabels, scale) {
        range.associateWith { m ->
            listOfNotNull(
                scale[m % 12]?.name(barStyle)?.takeIf { ui.options.fallingKeyLabels },
                if (midi) null else ComputerKeyboard.labelFor(m, lowC),
            ).joinToString("\n").ifEmpty { null }
        }
    }
    FallingNotesView(
        notes = notes,
        cursor = r::cursorAt,
        animate = playing,
        keyRange = range,
        pressed = pressed,
        keyLabel = { m -> labels[m] },
        modifier = modifier,
        // About one bar on screen: tall bars that are easy to read, as in tutorial videos.
            window = score.beatsPerMeasure.coerceIn(3, 4).toDouble(),
        smooth = ui.options.mode == PlayMode.WAIT,
        lines = bars,
        feedback = ui.fallingFeedback,
        totalBeats = score.measureStart(score.measures),
        onKey = if (midi) null else { n, down -> app.services.hub.dispatch(keyEvent(n, down)) },
        caption = com.id.mimikopi.core.score.KeySignature.majorName(score.keyFifths),
    )
}
