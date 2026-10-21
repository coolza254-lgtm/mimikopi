package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.music.Direction
import com.id.mimikopi.core.music.Motion
import com.id.mimikopi.core.music.MotionKind
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.practice.ReadingClef
import com.id.mimikopi.core.practice.ReadingRange
import com.id.mimikopi.desktop.session.DrillSession
import com.id.mimikopi.desktop.session.DrillSource
import com.id.mimikopi.desktop.session.DrillUi
import com.id.mimikopi.desktop.session.Feedback
import com.id.mimikopi.desktop.session.Prompt
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.components.PianoKeys
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.Segmented
import com.id.mimikopi.desktop.ui.components.StaffSequenceView
import com.id.mimikopi.desktop.ui.components.StaffView
import com.id.mimikopi.desktop.ui.components.StopButton
import com.id.mimikopi.desktop.ui.theme.Tokens

/**
 * Runs one drill full-window: the question large in the middle, the answer from the MIDI piano, the computer
 * keyboard or a click on the on-screen keys (hidden while a MIDI piano is connected, so the keys never give the
 * answer away and the question gets the room).
 */
@Composable
fun DrillScreen(source: DrillSource, via: String?) {
    val app = LocalApp.current
    val s = app.services
    val scope = rememberCoroutineScope()
    val session = remember(source) { DrillSession(s, source, scope, via) }
    val ui by session.ui.collectAsState()
    val showNames by session.showNames.collectAsState()
    val pressed by session.pressed.collectAsState()
    val ports by s.midi.ports.collectAsState()
    val lowC by s.keysLowC.collectAsState()
    val midi = ports.any { it.open }
    DisposableEffect(session) {
        session.onStart()
        app.keys.onSpace = {
            when (session.ui.value) {
                is DrillUi.Ready -> session.start()
                is DrillUi.Result -> session.again()
                else -> session.stop()
            }
        }
        app.keys.onEscape = {
            val running = session.ui.value !is DrillUi.Ready && session.ui.value !is DrillUi.Result
            if (running) session.stop()
            running
        }
        onDispose {
            app.keys.onSpace = null
            app.keys.onEscape = null
            session.stop()
            session.onStop()
        }
    }
    val running = ui !is DrillUi.Ready && ui !is DrillUi.Result && ui != DrillUi.Loading
    Column(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!running) SecondaryButton("‹ กลับ", onClick = app.nav::back)
            Text(session.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
            if (ui is DrillUi.Question || ui is DrillUi.Interval) {
                SecondaryButton(if (showNames) "ชื่อโน้ต: เปิด (นับเป็นตัวช่วย)" else "ชื่อโน้ต", onClick = session::toggleNames)
            }
            if (running) StopButton(session::stop)
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (val u = ui) {
                    DrillUi.Loading -> Text("กำลังเตรียม…", color = Tokens.Palette.InkMuted)
                    is DrillUi.Ready -> ReadyPanel(session, u, midi)
                    is DrillUi.Question -> QuestionPanel(session, u, showNames)
                    is DrillUi.Pulse -> PulsePanel(u)
                    is DrillUi.Interval -> IntervalPanel(session, u, showNames)
                    is DrillUi.ChordAsk -> ChordPanel(u)
                    is DrillUi.Result -> ResultPanel(session, u)
                }
            }
        }
        // The answer keys: only without a MIDI piano, and only while they are the way to answer.
        val keysFor = ui is DrillUi.Question || ui is DrillUi.Interval || ui is DrillUi.ChordAsk || ui is DrillUi.Pulse || ui is DrillUi.Ready
        if (!midi && keysFor) {
            val q = ui as? DrillUi.Question
            val marked = if (q?.feedback == Feedback.WRONG) q.prompt.answerKeys(session.keyRange) else (ui as? DrillUi.ChordAsk)?.answer.orEmpty()
            PianoKeys(
                range = keyboardRange(session.keyRange),
                pressed = pressed,
                onKey = { n, down -> s.hub.dispatch(keyEvent(n, down)) },
                marked = marked,
                labels = if (showNames) s.nameStyle.value else null,
                computerKeysLowC = lowC,
                height = 170.dp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            Text("ไม่ได้ต่อ MIDI: คลิกคีย์ หรือกดแป้นพิมพ์ตามตัวอักษรบนคีย์ ([ ] เลื่อนอ็อกเทฟ)", fontSize = 12.sp, color = Tokens.Palette.Dim,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        }
    }
}

/** At least two octaves from a C, so the on-screen keys look like a keyboard. */
fun keyboardRange(r: IntRange): IntRange {
    val lo = r.first - r.first % 12
    val hi = maxOf(r.last, lo + 23).let { if (it % 12 == 0) it else it - it % 12 + 12 }
    return lo..hi
}

@Composable
private fun ReadyPanel(session: DrillSession, u: DrillUi.Ready, midi: Boolean) {
    Spacer(Modifier.height(24.dp))
    Text(u.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, textAlign = TextAlign.Center)
    Text(when (u.type) {
        ExerciseType.E_KEYFIND -> "จะมีชื่อโน้ตขึ้นมาทีละตัว ${u.items} ข้อ ให้กดคีย์นั้นให้เร็วที่สุดโดยไม่ต้องนับ"
        ExerciseType.E_NOTEID -> "จะมีโน้ตบนบรรทัดทีละตัว ${u.items} ข้อ ให้กดคีย์เสียงนั้นที่อ็อกเทฟตรงกัน"
        ExerciseType.E_PULSE -> "ฟังคลิกนับเข้า 4 ครั้ง แล้วกดคีย์ใดก็ได้ตรงกับคลิกทุกครั้ง (${u.bpm} bpm)"
        ExerciseType.E_INTERVAL_READ -> "ตอบระยะระหว่างโน้ตแต่ละคู่ แล้วเล่นทั้งชุด"
        ExerciseType.E_CHORD_BUILD -> "กดคอร์ดตามชื่อที่ขึ้นมา (ตำแหน่งใดก็ได้)"
        ExerciseType.E_CHORD_CHANGE -> "เปลี่ยนคอร์ดตามลำดับให้ได้มากที่สุดในเวลาที่กำหนด"
        else -> ""
    }, fontSize = 17.sp, color = Tokens.Palette.InkMuted, textAlign = TextAlign.Center)
    if (session.notePractice) {
        val settings by session.readingSettings.collectAsState()
        Segmented(ReadingClef.entries, settings.clef, { it.title }, { session.setReadingPractice(settings.copy(clef = it)) }, Modifier.widthIn(max = 520.dp))
        Segmented(ReadingRange.entries, settings.range, { it.title }, { session.setReadingPractice(settings.copy(range = it)) }, Modifier.widthIn(max = 520.dp))
        Segmented(listOf(false, true), settings.accidentals, { if (it) "มี ♯/♭" else "ไม่มี ♯/♭" }, { session.setReadingPractice(settings.copy(accidentals = it)) }, Modifier.widthIn(max = 520.dp))
    }
    Text(if (midi) "ใช้เปียโน MIDI ตอบ · คีย์บนจอถูกซ่อน" else "ยังไม่ได้ต่อ MIDI · ตอบด้วยคีย์บนจอหรือแป้นพิมพ์", fontSize = 14.sp, color = Tokens.Palette.Dim)
    PrimaryButton("เริ่ม (Space)", onClick = session::start, modifier = Modifier.widthIn(max = 360.dp))
}

@Composable
private fun QuestionPanel(session: DrillSession, q: DrillUi.Question, showNames: Boolean) {
    Text("ข้อ ${q.index}/${q.total}" + (if (q.retry) " · ถามซ้ำ (ไม่นับคะแนนใหม่)" else "") + " · ถูกตั้งแต่ครั้งแรก ${q.firstCorrect}/${q.firstAnswered}",
        fontSize = 15.sp, color = Tokens.Palette.InkMuted)
    when (val p = q.prompt) {
        is Prompt.Key -> {
            val name = p.item.target?.let { session.noteLabel(it) } ?: "${p.item.letter.name}  ${p.item.letter.solfege}"
            Text(name, fontSize = 120.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            Text(if (p.item.target != null) "กดคีย์นี้ที่อ็อกเทฟนี้" else "กดคีย์ชื่อนี้ตัวไหนก็ได้", fontSize = 18.sp, color = Tokens.Palette.InkMuted)
        }
        is Prompt.Note -> {
            StaffView(p.item.clef, p.item.pitch, Modifier.widthIn(max = 640.dp).fillMaxWidth().height(320.dp))
            if (showNames) Text(session.noteLabel(p.item.pitch), fontSize = 28.sp, color = Tokens.Palette.InkMuted)
        }
    }
    when (q.feedback) {
        Feedback.CORRECT -> Text("ถูก", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        Feedback.WRONG -> {
            val played = q.answeredMidi?.let { SpelledPitch.fromMidiSharp(it).name(NoteNameStyle.BOTH, withOctave = true) } ?: ""
            Text("ยังไม่ใช่ · คุณกด $played", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            if (q.canContinue) {
                Text("ดูตำแหน่งที่ถูกบนคีย์ แล้วกดคีย์ที่ถูกเพื่อไปต่อ", fontSize = 16.sp, color = Tokens.Palette.InkMuted)
                SecondaryButton("ไปข้อต่อไป", onClick = session::continueReading)
            }
        }
        null -> Unit
    }
}

@Composable
private fun PulsePanel(u: DrillUi.Pulse) {
    Spacer(Modifier.height(40.dp))
    Text("${u.bpm} bpm", fontSize = 22.sp, color = Tokens.Palette.InkMuted)
    if (u.countIn != null) Text("นับเข้า ${u.countIn}", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
    else Text("${u.beat}/${u.beats}", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
    Text("กดแล้ว ${u.taps} ครั้ง", fontSize = 18.sp, color = Tokens.Palette.InkMuted)
}

@Composable
private fun IntervalPanel(session: DrillSession, q: DrillUi.Interval, showNames: Boolean) {
    Text("ชุด ${q.index}/${q.total}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
    StaffSequenceView(q.clef, q.notes, q.pair?.let { setOf(it, it + 1) } ?: emptySet(), if (q.pair == null) q.played else 0,
        Modifier.widthIn(max = 820.dp).fillMaxWidth().height(260.dp))
    if (showNames) Text(q.notes.joinToString("  ") { session.noteLabel(it) }, fontSize = 18.sp, color = Tokens.Palette.InkMuted)
    val pair = q.pair
    if (pair != null) {
        Text("โน้ตคู่ที่ ${pair + 1}: ไปอย่างไร", fontSize = 20.sp, color = Tokens.Palette.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q.answerMode == "INTERVAL") {
                if (q.withRepeat) SecondaryButton("ซ้ำ", onClick = { session.answerInterval(Motion(MotionKind.REPEAT, Direction.SAME, 0)) })
                for (n in q.numbers.filter { it > 1 }) for (d in listOf(Direction.UP, Direction.DOWN)) {
                    SecondaryButton("ระยะ $n ${if (d == Direction.UP) "ขึ้น" else "ลง"}", onClick = {
                        session.answerInterval(Motion(if (n == 2) MotionKind.STEP else MotionKind.SKIP, d, n - 1))
                    })
                }
            } else {
                if (q.withRepeat) SecondaryButton("ซ้ำ", onClick = { session.answerInterval(Motion(MotionKind.REPEAT, Direction.SAME, 0)) })
                SecondaryButton("ขั้นขึ้น", onClick = { session.answerInterval(Motion(MotionKind.STEP, Direction.UP, 1)) })
                SecondaryButton("ขั้นลง", onClick = { session.answerInterval(Motion(MotionKind.STEP, Direction.DOWN, 1)) })
                SecondaryButton("ข้ามขึ้น", onClick = { session.answerInterval(Motion(MotionKind.SKIP, Direction.UP, 2)) })
                SecondaryButton("ข้ามลง", onClick = { session.answerInterval(Motion(MotionKind.SKIP, Direction.DOWN, 2)) })
            }
        }
    } else {
        Text("เล่นทั้งชุดตามลำดับ (เล่นแล้ว ${q.played}/${q.notes.size})", fontSize = 20.sp, color = Tokens.Palette.Ink)
    }
    when (q.feedback) {
        Feedback.CORRECT -> Text("ถูก", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Feedback.WRONG -> Text("ยังไม่ใช่" + (q.correction?.let { " · ที่ถูกคือ $it" } ?: ""), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        null -> Unit
    }
}

@Composable
private fun ChordPanel(q: DrillUi.ChordAsk) {
    if (q.secondsLeft != null) Text("เหลือ ${q.secondsLeft} วินาที · ถูก ${q.count ?: 0} ครั้ง", fontSize = 18.sp, color = Tokens.Palette.InkMuted)
    else Text("ข้อ ${q.index}/${q.total}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
    Text(q.label, fontSize = 110.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
    q.next?.let { Text("ถัดไป: $it", fontSize = 22.sp, color = Tokens.Palette.InkMuted) }
    when (q.feedback) {
        Feedback.CORRECT -> Text("ถูก", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Feedback.WRONG -> Text("ยังไม่ใช่ · ดูตำแหน่งที่ถูกบนคีย์", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        null -> Unit
    }
}

@Composable
private fun ResultPanel(session: DrillSession, r: DrillUi.Result) {
    val app = LocalApp.current
    Spacer(Modifier.height(16.dp))
    Text(r.summary, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, textAlign = TextAlign.Center)
    when (r.passCriterionMet) {
        true -> Text(if (r.lessonPassed) "ผ่านบทนี้แล้ว" else "ถึงเกณฑ์ของวันนี้", fontSize = 20.sp, color = Tokens.Palette.Ink)
        false -> Text("ยังไม่ถึงเกณฑ์", fontSize = 20.sp, color = Tokens.Palette.Ink)
        null -> Unit
    }
    Column(Modifier.widthIn(max = 700.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        r.coach.forEach { Text("• $it", fontSize = 17.sp, color = Tokens.Palette.Ink) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("ฝึกอีกรอบ (Space)", onClick = session::again, modifier = Modifier.widthIn(max = 300.dp))
        SecondaryButton("กลับไปดูประวัติบทนี้", onClick = app.nav::back)
    }
}
