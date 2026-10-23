package com.id.mimikopi.ui.reading

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.ui.components.StaffView
import com.id.mimikopi.ui.practice.DrillUi
import com.id.mimikopi.ui.practice.Feedback
import com.id.mimikopi.ui.practice.Prompt
import com.id.mimikopi.ui.theme.Tokens

@Composable
internal fun NoteReadingSetup(
    ready: DrillUi.Ready, connected: Boolean, settings: ReadingPractice?,
    onSettings: (ReadingPractice) -> Unit, onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("note-reading-setup"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(ready.title, color = Tokens.Palette.Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(if (connected) tr("● ต่อ MIDI · ตอบด้วยคีย์บนเปียโน", "● MIDI connected · answer on the piano keys", "● MIDI接続中 · ピアノの鍵盤で答える") else tr("● มือถือ · แตะตอบได้ ไม่ต้องต่อเปียโน", "● Phone · tap to answer, no piano needed", "● スマホ · タップで回答、ピアノ不要"), color = Tokens.Palette.InkMuted)
        Text(tr("มองโน้ต → ตอบ → ดูผล\nข้อที่พลาดจะกลับมาทวนท้ายรอบ", "Look at the note → answer → see the result\nMissed ones come back at the end of the round", "音符を見る → 答える → 結果を見る\n間違えた問題は最後にもう一度出ます"), color = Tokens.Palette.Ink, fontSize = 16.sp, lineHeight = 25.sp)
        settings?.let { s ->
            Text(tr("กุญแจ", "Clef", "音部記号"), color = Tokens.Palette.InkMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReadingClef.entries.forEach { c -> FilterChip(selected = s.clef == c, onClick = { onSettings(s.copy(clef = c)) }, label = { Text(c.title, fontSize = 12.sp) }) }
            }
            Text(tr("ช่วงที่ฝึก", "Range", "範囲"), color = Tokens.Palette.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReadingRange.entries.forEach { r -> FilterChip(selected = s.range == r, onClick = { onSettings(s.copy(range = r)) }, label = { Text(r.title) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("รวม ♯ และ ♭", "Include ♯ and ♭", "♯と♭を含める"), color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                Switch(s.accidentals, { onSettings(s.copy(accidentals = it)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30).forEach { n -> FilterChip(selected = s.items == n, onClick = { onSettings(s.copy(items = n)) }, label = { Text(tr("$n ข้อ", "$n questions", "${n}問")) }) }
            }
            Text(tr("ฝึกอ่านชื่อด้วยปุ่มบนมือถือ หรือหาคีย์จริงเมื่อต่อ MIDI รอบนี้ไม่นับเป็นแบบทดสอบเลื่อนระดับ", "Practise naming with phone buttons, or find the real key with MIDI. This round isn't a level test", "スマホのボタンで音名を、MIDI接続なら実際の鍵盤を探す練習。この回はレベルテストではありません"), color = Tokens.Palette.InkMuted, fontSize = 13.sp)
        }
        if (settings == null) Text(tr("${ready.items} ข้อ · ต้องตอบเสียงและอ็อกเทฟให้ตรงกับโน้ต\nเปิดจุดอ้างอิงได้ แต่จะนับว่าใช้ตัวช่วย", "${ready.items} questions · the pitch and octave must match the note\nYou can show the landmarks, but that counts as help", "${ready.items}問 · 音とオクターブを音符に合わせて答える\n目印を表示できますが補助ありとして数えます"), color = Tokens.Palette.InkMuted, fontSize = 14.sp)
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("note-reading-start")) { Text(tr("เริ่มฝึก · ${ready.items} ข้อ", "Start · ${ready.items} questions", "開始 · ${ready.items}問")) }
    }
}

/** The same staff and feedback for both input methods; no target name appears before an answer. */
@Composable
internal fun NoteReadingQuestion(
    q: DrillUi.Question, connected: Boolean, names: Boolean, nameStyle: NoteNameStyle, range: IntRange,
    choices: List<SpelledPitch>?, pressed: Set<Int>, onKey: (Int, Boolean) -> Unit, onAnswer: (Int) -> Unit, onNext: () -> Unit, onNames: () -> Unit, onStop: (() -> Unit)? = null,
) {
    val item = (q.prompt as Prompt.Note).item
    fun pitchName(p: SpelledPitch) = p.name(nameStyle, true) + if (nameStyle == NoteNameStyle.SOLFEGE) " ($p)" else ""
    val color = when (q.feedback) { Feedback.CORRECT -> MaterialTheme.colorScheme.primary; Feedback.WRONG -> MaterialTheme.colorScheme.error; null -> Tokens.Palette.Ink }
    Column(Modifier.fillMaxSize().testTag("note-reading-question")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (q.retry) tr("ทวนข้อที่พลาด", "Review the missed ones", "間違えた問題を復習") else tr("ข้อ ${q.index} / ${q.total}", "Question ${q.index} / ${q.total}", "${q.index} / ${q.total}問"), color = Tokens.Palette.InkMuted, modifier = Modifier.weight(1f))
            FilterChip(selected = names, onClick = onNames, label = { Text(tr("จุดอ้างอิง", "Landmarks", "目印")) })
            onStop?.let { Button(onClick = it, modifier = Modifier.padding(start = 8.dp).heightIn(min = 48.dp).testTag("reading-stop")) { Text(tr("หยุด", "Stop", "停止")) } }
        }
        LinearProgressIndicator(progress = { q.firstAnswered.toFloat() / q.total.coerceAtLeast(1) }, color = Tokens.Palette.Ink, trackColor = Tokens.Palette.Line, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > maxHeight
            val answerHeight = maxHeight * .55f
            val staff: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier) {
                Text(if (item.clef == Clef.TREBLE) tr("กุญแจซอล", "Treble clef", "ト音記号") else tr("กุญแจฟา", "Bass clef", "ヘ音記号"), color = Tokens.Palette.InkMuted, fontSize = 13.sp)
                StaffView(item.clef, item.pitch, Modifier.fillMaxWidth().weight(1f).testTag("reading-staff"), ink = color)
                if (names) Text(if (item.clef == Clef.TREBLE) tr("เส้นล่างสุด E4 · ช่องว่างจากล่าง F A C E\nโดกลาง C4 อยู่เส้นน้อยใต้บรรทัด", "Bottom line E4 · spaces from the bottom F A C E\nMiddle C (C4) sits on a ledger line below the staff", "第1線はE4 · 下からの間はF A C E\n中央のド（C4）は五線の下の加線") else tr("เส้นล่างสุด G2 · ช่องว่างจากล่าง A C E G\nโดกลาง C4 อยู่เส้นน้อยเหนือบรรทัด", "Bottom line G2 · spaces from the bottom A C E G\nMiddle C (C4) sits on a ledger line above the staff", "第1線はG2 · 下からの間はA C E G\n中央のド（C4）は五線の上の加線"),
                    color = Tokens.Palette.InkMuted, fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
            val answer: @Composable () -> Unit = {
                when (q.feedback) {
                    Feedback.CORRECT -> Text(tr("ถูก · ${pitchName(item.pitch)}", "Right · ${pitchName(item.pitch)}", "正解 · ${pitchName(item.pitch)}"), color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Feedback.WRONG -> {
                        Text(tr("คำตอบคือ ${pitchName(item.pitch)}", "The answer is ${pitchName(item.pitch)}", "答えは${pitchName(item.pitch)}"), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        q.answeredMidi?.let { Text(tr("คุณตอบ ${pitchName(SpelledPitch.fromMidiSharp(it))}", "You answered ${pitchName(SpelledPitch.fromMidiSharp(it))}", "あなたの答え ${pitchName(SpelledPitch.fromMidiSharp(it))}"), color = Tokens.Palette.InkMuted, fontSize = 14.sp) }
                        Text(tr("ดูตำแหน่งบนบรรทัดอีกครั้ง", "Look at its place on the staff again", "五線上の位置をもう一度見る"), color = Tokens.Palette.InkMuted, fontSize = 14.sp)
                    }
                    null -> Text(if (connected) tr("เล่นโน้ตนี้บนเปียโน", "Play this note on the piano", "この音をピアノで弾く") else if (choices != null) tr("โน้ตนี้ชื่ออะไร?", "What is this note?", "この音は何？") else tr("กดคีย์ให้ตรงกับโน้ต", "Press the key that matches the note", "音符に合った鍵盤を押す"), color = Tokens.Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!connected && choices != null) {
                    Spacer(Modifier.height(14.dp))
                    ReadingNameAnswers(choices, q.feedback == null, onAnswer)
                }
            }
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                staff(Modifier.weight(if (connected) 2f else 1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { answer() }
            } else Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                staff(Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.heightIn(max = answerHeight).verticalScroll(rememberScrollState())) { answer() }
            }
        }
        if (q.feedback == Feedback.WRONG && q.canContinue) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 50.dp).testTag("reading-next")) { Text(tr("เข้าใจแล้ว · ข้อถัดไป", "Got it · next", "わかった · 次へ")) }
            if (connected) Text(tr("หรือปล่อยคีย์ แล้วกดโน้ตที่ถูกต้องเพื่อไปต่อ", "Or release the key and press the right note to go on", "または鍵盤を離して正しい音を押すと次へ"), color = Tokens.Palette.InkMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (choices == null || connected) ReadingKeyboard(connected, range, nameStyle, names, pressed, onKey,
            marked = if (q.feedback == Feedback.WRONG) setOf(item.pitch.midi) else emptySet(),
            enabled = q.feedback == null || q.canContinue, performance = true)
        Text("ถูกครั้งแรก ${q.firstCorrect} / ${q.firstAnswered} · ${if (q.feedback == Feedback.CORRECT) tr("กำลังไปข้อถัดไป", "Going to the next one", "次の問題へ") else tr("ตอบเสียงและอ็อกเทฟให้ตรง", "Match the pitch and octave", "音とオクターブを合わせる")}",
            color = Tokens.Palette.InkMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
    }
}

/** Questions use the right pane; a touch keyboard appears only when the set actually asks for playing. */
@Composable
internal fun IntervalReadingQuestion(
    q: DrillUi.Interval, connected: Boolean, names: Boolean, nameStyle: NoteNameStyle, range: IntRange,
    pressed: Set<Int>, onKey: (Int, Boolean) -> Unit, onNames: () -> Unit, onStop: () -> Unit,
    answers: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("ชุด ${q.index} / ${q.total}", "Set ${q.index} / ${q.total}", "${q.index} / ${q.total}セット"), modifier = Modifier.weight(1f), color = Tokens.Palette.InkMuted)
            FilterChip(selected = names, onClick = onNames, label = { Text(tr("ชื่อบนคีย์", "Names on keys", "鍵盤に音名")) })
            Button(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp).testTag("reading-stop")) { Text(tr("หยุด", "Stop", "停止")) }
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            com.id.mimikopi.ui.components.StaffSequenceView(q.clef, q.notes, q.pair?.let { setOf(it, it + 1) } ?: emptySet(),
                if (q.pair == null) q.played else 0, Modifier.weight(2f).fillMaxHeight().testTag("interval-staff"))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (q.pair != null) tr("โน้ต ${q.pair + 1} ไป ${q.pair + 2} เคลื่อนอย่างไร?", "How does note ${q.pair + 1} move to ${q.pair + 2}?", "${q.pair + 1}番目から${q.pair + 2}番目の音はどう動く？") else tr("เล่นโน้ตชุดนี้ตามลำดับ", "Play these notes in order", "この音を順番に弾く"), style = MaterialTheme.typography.titleMedium)
                q.correction?.let { Text(tr("คำตอบ: $it", "Answer: $it", "答え：$it"), color = MaterialTheme.colorScheme.error) }
                if (q.feedback == Feedback.CORRECT) Text(tr("ถูก", "Right", "正解"), color = Tokens.Palette.Ink)
                if (q.feedback == Feedback.WRONG && q.pair == null) Text(tr("ลองอีกครั้ง", "Try again", "もう一度"), color = MaterialTheme.colorScheme.error)
                if (q.pair != null) answers()
            }
        }
        if (q.pair == null) ReadingKeyboard(connected, range, nameStyle, names, pressed, onKey, performance = true)
    }
}
