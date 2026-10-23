package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
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
    com.id.mimikopi.desktop.ui.components.AnswerFx(when (val s = ui) {
        is DrillUi.Question -> s.feedback
        is DrillUi.ChordAsk -> s.feedback
        is DrillUi.Interval -> s.feedback
        else -> null
    }?.let { it == com.id.mimikopi.desktop.session.Feedback.CORRECT })
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
            if (!running) SecondaryButton(tr("‹ กลับ", "‹ Back", "‹ 戻る"), onClick = app.nav::back)
            Text(session.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
            if (ui is DrillUi.Question || ui is DrillUi.Interval) {
                SecondaryButton(if (showNames) tr("ชื่อโน้ต: เปิด (นับเป็นตัวช่วย)", "Note names: on (counts as help)", "音名：表示（補助ありとして数えます）") else tr("ชื่อโน้ต", "Note names", "音名"), onClick = session::toggleNames)
            }
            if (running) StopButton(session::stop)
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (val u = ui) {
                    DrillUi.Loading -> Text(tr("กำลังเตรียม…", "Preparing…", "準備中…"), color = Tokens.Palette.InkMuted)
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
            Text(tr("ไม่ได้ต่อ MIDI: คลิกคีย์ หรือกดแป้นพิมพ์ตามตัวอักษรบนคีย์ ([ ] เลื่อนอ็อกเทฟ)", "No MIDI: click the keys or type the letters on them ([ ] moves the octave)", "MIDIなし：鍵盤をクリックするか、鍵盤の文字をキーボードで入力（[ ]でオクターブ移動）"), fontSize = 12.sp, color = Tokens.Palette.Dim,
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
        ExerciseType.E_KEYFIND -> tr("จะมีชื่อโน้ตขึ้นมาทีละตัว ${u.items} ข้อ ให้กดคีย์นั้นให้เร็วที่สุดโดยไม่ต้องนับ", "Note names appear one at a time, ${u.items} questions; press that key as fast as you can without counting", "音名が1つずつ${u.items}問出ます。数えずにできるだけ速くその鍵盤を押す")
        ExerciseType.E_NOTEID -> tr("จะมีโน้ตบนบรรทัดทีละตัว ${u.items} ข้อ ให้กดคีย์เสียงนั้นที่อ็อกเทฟตรงกัน", "Notes appear on the staff one at a time, ${u.items} questions; press that pitch at the same octave", "五線に音符が1つずつ${u.items}問出ます。同じオクターブのその音を押す")
        ExerciseType.E_PULSE -> tr("ฟังคลิกนับเข้า 4 ครั้ง แล้วกดคีย์ใดก็ได้ตรงกับคลิกทุกครั้ง (${u.bpm} bpm)", "Hear 4 count-in clicks, then press any key with every click (${u.bpm} bpm)", "カウントのクリックを4回聞いてから、クリックごとにどの鍵盤でも押す（${u.bpm} bpm）")
        ExerciseType.E_INTERVAL_READ -> tr("ตอบระยะระหว่างโน้ตแต่ละคู่ แล้วเล่นทั้งชุด", "Name the interval between each pair of notes, then play the whole set", "各音の間の音程を答えてからセット全体を弾く")
        ExerciseType.E_CHORD_BUILD -> tr("กดคอร์ดตามชื่อที่ขึ้นมา (ตำแหน่งใดก็ได้)", "Press the chord whose name appears (any position)", "出てきたコード名のコードを押す（形は自由）")
        ExerciseType.E_CHORD_CHANGE -> tr("เปลี่ยนคอร์ดตามลำดับให้ได้มากที่สุดในเวลาที่กำหนด", "Change chords in order as many times as you can in the time", "時間内にできるだけ多く順番にコードを替える")
        else -> ""
    }, fontSize = 17.sp, color = Tokens.Palette.InkMuted, textAlign = TextAlign.Center)
    if (session.notePractice) {
        val settings by session.readingSettings.collectAsState()
        Segmented(ReadingClef.entries, settings.clef, { it.title }, { session.setReadingPractice(settings.copy(clef = it)) }, Modifier.widthIn(max = 520.dp))
        Segmented(ReadingRange.entries, settings.range, { it.title }, { session.setReadingPractice(settings.copy(range = it)) }, Modifier.widthIn(max = 520.dp))
        Segmented(listOf(false, true), settings.accidentals, { if (it) tr("มี ♯/♭", "With ♯/♭", "♯/♭あり") else tr("ไม่มี ♯/♭", "No ♯/♭", "♯/♭なし") }, { session.setReadingPractice(settings.copy(accidentals = it)) }, Modifier.widthIn(max = 520.dp))
    }
    Text(if (midi) tr("ใช้เปียโน MIDI ตอบ · คีย์บนจอถูกซ่อน", "Answer with the MIDI piano · on-screen keys are hidden", "MIDIピアノで答える · 画面の鍵盤は非表示") else tr("ยังไม่ได้ต่อ MIDI · ตอบด้วยคีย์บนจอหรือแป้นพิมพ์", "No MIDI · answer with on-screen keys or the keyboard", "MIDIなし · 画面の鍵盤かキーボードで答える"), fontSize = 14.sp, color = Tokens.Palette.Dim)
    val timed = u.type == ExerciseType.E_PULSE
    if (timed && !midi) Text(com.id.mimikopi.core.input.TimingInput.NEED_CABLE, fontSize = 15.sp, color = Tokens.Palette.Ink, textAlign = TextAlign.Center)
    PrimaryButton(tr("เริ่ม (Space)", "Start (Space)", "開始（Space）"), onClick = session::start, enabled = !timed || midi, modifier = Modifier.widthIn(max = 360.dp))
}

@Composable
private fun QuestionPanel(session: DrillSession, q: DrillUi.Question, showNames: Boolean) {
    Text(tr("ข้อ ${q.index}/${q.total}", "Question ${q.index}/${q.total}", "${q.index}/${q.total}問") + (if (q.retry) tr(" · ถามซ้ำ (ไม่นับคะแนนใหม่)", " · asked again (no new score)", " · 再出題（新しく採点しません）") else "") + tr(" · ถูกตั้งแต่ครั้งแรก ${q.firstCorrect}/${q.firstAnswered}", " · right first time ${q.firstCorrect}/${q.firstAnswered}", " · 1回目で正解 ${q.firstCorrect}/${q.firstAnswered}"),
        fontSize = 15.sp, color = Tokens.Palette.InkMuted)
    when (val p = q.prompt) {
        is Prompt.Key -> {
            val name = p.item.target?.let { session.noteLabel(it) } ?: "${p.item.letter.name}  ${p.item.letter.solfege}"
            Text(name, fontSize = 120.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            Text(if (p.item.target != null) tr("กดคีย์นี้ที่อ็อกเทฟนี้", "Press this key at this octave", "このオクターブのこの鍵盤を押す") else tr("กดคีย์ชื่อนี้ตัวไหนก็ได้", "Press any key with this name", "この名前の鍵盤ならどれでも"), fontSize = 18.sp, color = Tokens.Palette.InkMuted)
        }
        is Prompt.Note -> {
            StaffView(p.item.clef, p.item.pitch, Modifier.widthIn(max = 640.dp).fillMaxWidth().height(320.dp))
            if (showNames) Text(session.noteLabel(p.item.pitch), fontSize = 28.sp, color = Tokens.Palette.InkMuted)
        }
    }
    when (q.feedback) {
        Feedback.CORRECT -> Text(tr("ถูก", "Right", "正解"), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        Feedback.WRONG -> {
            val played = q.answeredMidi?.let { SpelledPitch.fromMidiSharp(it).name(NoteNameStyle.BOTH, withOctave = true) } ?: ""
            Text(tr("ยังไม่ใช่ · คุณกด $played", "Not quite · you pressed $played", "ちがいます · 押したのは$played"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            if (q.canContinue) {
                Text(tr("ดูตำแหน่งที่ถูกบนคีย์ แล้วกดคีย์ที่ถูกเพื่อไปต่อ", "See the right place on the keys, then press it to go on", "鍵盤の正しい位置を見て、押すと次へ"), fontSize = 16.sp, color = Tokens.Palette.InkMuted)
                SecondaryButton(tr("ไปข้อต่อไป", "Next question", "次の問題へ"), onClick = session::continueReading)
            }
        }
        null -> Unit
    }
}

@Composable
private fun PulsePanel(u: DrillUi.Pulse) {
    Spacer(Modifier.height(40.dp))
    Text("${u.bpm} bpm", fontSize = 22.sp, color = Tokens.Palette.InkMuted)
    if (u.countIn != null) Text(tr("นับเข้า ${u.countIn}", "Count-in ${u.countIn}", "カウント ${u.countIn}"), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
    else Text("${u.beat}/${u.beats}", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
    Text(tr("กดแล้ว ${u.taps} ครั้ง", "${u.taps} presses", "${u.taps}回押しました"), fontSize = 18.sp, color = Tokens.Palette.InkMuted)
}

@Composable
private fun IntervalPanel(session: DrillSession, q: DrillUi.Interval, showNames: Boolean) {
    Text(tr("ชุด ${q.index}/${q.total}", "Set ${q.index}/${q.total}", "${q.index}/${q.total}セット"), fontSize = 15.sp, color = Tokens.Palette.InkMuted)
    StaffSequenceView(q.clef, q.notes, q.pair?.let { setOf(it, it + 1) } ?: emptySet(), if (q.pair == null) q.played else 0,
        Modifier.widthIn(max = 820.dp).fillMaxWidth().height(260.dp))
    if (showNames) Text(q.notes.joinToString("  ") { session.noteLabel(it) }, fontSize = 18.sp, color = Tokens.Palette.InkMuted)
    val pair = q.pair
    if (pair != null) {
        Text(tr("โน้ตคู่ที่ ${pair + 1}: ไปอย่างไร", "Pair ${pair + 1}: how does it move?", "${pair + 1}組目：どう動く？"), fontSize = 20.sp, color = Tokens.Palette.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q.answerMode == "INTERVAL") {
                if (q.withRepeat) SecondaryButton(tr("ซ้ำ", "Same", "同音"), onClick = { session.answerInterval(Motion(MotionKind.REPEAT, Direction.SAME, 0)) })
                for (n in q.numbers.filter { it > 1 }) for (d in listOf(Direction.UP, Direction.DOWN)) {
                    SecondaryButton("ระยะ $n ${if (d == Direction.UP) tr("ขึ้น", "Up", "上") else tr("ลง", "Down", "下")}", onClick = {
                        session.answerInterval(Motion(if (n == 2) MotionKind.STEP else MotionKind.SKIP, d, n - 1))
                    })
                }
            } else {
                if (q.withRepeat) SecondaryButton(tr("ซ้ำ", "Same", "同音"), onClick = { session.answerInterval(Motion(MotionKind.REPEAT, Direction.SAME, 0)) })
                SecondaryButton(tr("ขั้นขึ้น", "Step up", "順次上行"), onClick = { session.answerInterval(Motion(MotionKind.STEP, Direction.UP, 1)) })
                SecondaryButton(tr("ขั้นลง", "Step down", "順次下行"), onClick = { session.answerInterval(Motion(MotionKind.STEP, Direction.DOWN, 1)) })
                SecondaryButton(tr("ข้ามขึ้น", "Leap up", "跳躍上行"), onClick = { session.answerInterval(Motion(MotionKind.SKIP, Direction.UP, 2)) })
                SecondaryButton(tr("ข้ามลง", "Leap down", "跳躍下行"), onClick = { session.answerInterval(Motion(MotionKind.SKIP, Direction.DOWN, 2)) })
            }
        }
    } else {
        Text(tr("เล่นทั้งชุดตามลำดับ (เล่นแล้ว ${q.played}/${q.notes.size})", "Play the whole set in order (played ${q.played}/${q.notes.size})", "セット全体を順に弾く（${q.played}/${q.notes.size}）"), fontSize = 20.sp, color = Tokens.Palette.Ink)
    }
    when (q.feedback) {
        Feedback.CORRECT -> Text(tr("ถูก", "Right", "正解"), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Feedback.WRONG -> Text(tr("ยังไม่ใช่", "Not quite", "ちがいます") + (q.correction?.let { tr(" · ที่ถูกคือ $it", " · the right one is $it", " · 正しくは$it") } ?: ""), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        null -> Unit
    }
}

@Composable
private fun ChordPanel(q: DrillUi.ChordAsk) {
    if (q.secondsLeft != null) Text(tr("เหลือ ${q.secondsLeft} วินาที · ถูก ${q.count ?: 0} ครั้ง", "${q.secondsLeft} s left · right ${q.count ?: 0} times", "残り${q.secondsLeft}秒 · 正解${q.count ?: 0}回"), fontSize = 18.sp, color = Tokens.Palette.InkMuted)
    else Text(tr("ข้อ ${q.index}/${q.total}", "Question ${q.index}/${q.total}", "${q.index}/${q.total}問"), fontSize = 15.sp, color = Tokens.Palette.InkMuted)
    Text(q.label, fontSize = 110.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
    q.next?.let { Text(tr("ถัดไป: $it", "Next: $it", "次：$it"), fontSize = 22.sp, color = Tokens.Palette.InkMuted) }
    when (q.feedback) {
        Feedback.CORRECT -> Text(tr("ถูก", "Right", "正解"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Feedback.WRONG -> Text(tr("ยังไม่ใช่ · ดูตำแหน่งที่ถูกบนคีย์", "Not quite · see the right place on the keys", "ちがいます · 鍵盤の正しい位置を見て"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        null -> Unit
    }
}

@Composable
private fun ResultPanel(session: DrillSession, r: DrillUi.Result) {
    val app = LocalApp.current
    Spacer(Modifier.height(16.dp))
    Text(r.summary, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, textAlign = TextAlign.Center)
    when (r.passCriterionMet) {
        true -> Text(if (r.lessonPassed) tr("ผ่านบทนี้แล้ว", "Lesson passed", "レッスン合格") else tr("ถึงเกณฑ์ของวันนี้", "Today's criteria met", "今日の基準を達成"), fontSize = 20.sp, color = Tokens.Palette.Ink)
        false -> Text(tr("ยังไม่ถึงเกณฑ์", "Criteria not met yet", "まだ基準に届いていません"), fontSize = 20.sp, color = Tokens.Palette.Ink)
        null -> Unit
    }
    Column(Modifier.widthIn(max = 700.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        r.coach.forEach { Text("• $it", fontSize = 17.sp, color = Tokens.Palette.Ink) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton(tr("ฝึกอีกรอบ (Space)", "Practise again (Space)", "もう一回練習（Space）"), onClick = session::again, modifier = Modifier.widthIn(max = 300.dp))
        SecondaryButton(tr("กลับไปดูประวัติบทนี้", "Back to this lesson's history", "このレッスンの履歴へ戻る"), onClick = app.nav::back)
    }
}
