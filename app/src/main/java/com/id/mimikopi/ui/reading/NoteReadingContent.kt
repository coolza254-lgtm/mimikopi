package com.id.mimikopi.ui.reading

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
        Text(if (connected) "● ต่อ MIDI · ตอบด้วยคีย์บนเปียโน" else "● มือถือ · แตะตอบได้ ไม่ต้องต่อเปียโน", color = Tokens.Palette.InkMuted)
        Text("มองโน้ต → ตอบ → ดูผล\nข้อที่พลาดจะกลับมาทวนท้ายรอบ", color = Tokens.Palette.Ink, fontSize = 16.sp, lineHeight = 25.sp)
        settings?.let { s ->
            Text("กุญแจ", color = Tokens.Palette.InkMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReadingClef.entries.forEach { c -> FilterChip(selected = s.clef == c, onClick = { onSettings(s.copy(clef = c)) }, label = { Text(c.title, fontSize = 12.sp) }) }
            }
            Text("ช่วงที่ฝึก", color = Tokens.Palette.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReadingRange.entries.forEach { r -> FilterChip(selected = s.range == r, onClick = { onSettings(s.copy(range = r)) }, label = { Text(r.title) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("รวม ♯ และ ♭", color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                Switch(s.accidentals, { onSettings(s.copy(accidentals = it)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30).forEach { n -> FilterChip(selected = s.items == n, onClick = { onSettings(s.copy(items = n)) }, label = { Text("$n ข้อ") }) }
            }
            Text("ฝึกอ่านชื่อด้วยปุ่มบนมือถือ หรือหาคีย์จริงเมื่อต่อ MIDI รอบนี้ไม่นับเป็นแบบทดสอบเลื่อนระดับ", color = Tokens.Palette.InkMuted, fontSize = 13.sp)
        }
        if (settings == null) Text("${ready.items} ข้อ · ต้องตอบเสียงและอ็อกเทฟให้ตรงกับโน้ต\nเปิดจุดอ้างอิงได้ แต่จะนับว่าใช้ตัวช่วย", color = Tokens.Palette.InkMuted, fontSize = 14.sp)
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("note-reading-start")) { Text("เริ่มฝึก · ${ready.items} ข้อ") }
    }
}

/** The same staff and feedback for both input methods; no target name appears before an answer. */
@Composable
internal fun NoteReadingQuestion(
    q: DrillUi.Question, connected: Boolean, names: Boolean, nameStyle: NoteNameStyle, range: IntRange,
    choices: List<SpelledPitch>?, pressed: Set<Int>, onKey: (Int, Boolean) -> Unit, onAnswer: (Int) -> Unit, onNext: () -> Unit, onNames: () -> Unit,
) {
    val item = (q.prompt as Prompt.Note).item
    fun pitchName(p: SpelledPitch) = p.name(nameStyle, true) + if (nameStyle == NoteNameStyle.SOLFEGE) " ($p)" else ""
    val color = when (q.feedback) { Feedback.CORRECT -> MaterialTheme.colorScheme.primary; Feedback.WRONG -> MaterialTheme.colorScheme.error; null -> Tokens.Palette.Ink }
    Column(Modifier.fillMaxSize().testTag("note-reading-question")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (q.retry) "ทวนข้อที่พลาด" else "ข้อ ${q.index} / ${q.total}", color = Tokens.Palette.InkMuted, modifier = Modifier.weight(1f))
            FilterChip(selected = names, onClick = onNames, label = { Text("จุดอ้างอิง") })
        }
        LinearProgressIndicator(progress = { q.firstAnswered.toFloat() / q.total.coerceAtLeast(1) }, color = Tokens.Palette.Ink, trackColor = Tokens.Palette.Line, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            val staff: @Composable () -> Unit = {
                Text(if (item.clef == Clef.TREBLE) "กุญแจซอล" else "กุญแจฟา", color = Tokens.Palette.InkMuted, fontSize = 13.sp)
                StaffView(item.clef, item.pitch, Modifier.fillMaxWidth().height(if (wide) 145.dp else 180.dp).testTag("reading-staff"), ink = color)
                if (names) Text(if (item.clef == Clef.TREBLE) "เส้นล่างสุด E4 · ช่องว่างจากล่าง F A C E\nโดกลาง C4 อยู่เส้นน้อยใต้บรรทัด" else "เส้นล่างสุด G2 · ช่องว่างจากล่าง A C E G\nโดกลาง C4 อยู่เส้นน้อยเหนือบรรทัด",
                    color = Tokens.Palette.InkMuted, fontSize = 13.sp, lineHeight = 21.sp)
            }
            val answer: @Composable () -> Unit = {
                when (q.feedback) {
                    Feedback.CORRECT -> Text("ถูก · ${pitchName(item.pitch)}", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Feedback.WRONG -> {
                        Text("คำตอบคือ ${pitchName(item.pitch)}", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        q.answeredMidi?.let { Text("คุณตอบ ${pitchName(SpelledPitch.fromMidiSharp(it))}", color = Tokens.Palette.InkMuted, fontSize = 14.sp) }
                        Text("ดูตำแหน่งบนบรรทัดอีกครั้ง", color = Tokens.Palette.InkMuted, fontSize = 14.sp)
                    }
                    null -> Text(if (connected) "เล่นโน้ตนี้บนเปียโน" else if (choices != null) "โน้ตนี้ชื่ออะไร?" else "กดคีย์ให้ตรงกับโน้ต", color = Tokens.Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!connected && choices != null) {
                    Spacer(Modifier.height(14.dp))
                    ReadingNameAnswers(choices, q.feedback == null, onAnswer)
                }
            }
            if (wide) Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { staff() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { answer() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { staff(); answer() }
        }
        if (q.feedback == Feedback.WRONG && q.canContinue) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 50.dp).testTag("reading-next")) { Text("เข้าใจแล้ว · ข้อถัดไป") }
            if (connected) Text("หรือปล่อยคีย์ แล้วกดโน้ตที่ถูกต้องเพื่อไปต่อ", color = Tokens.Palette.InkMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (choices == null || connected) ReadingKeyboard(connected, range, nameStyle, names, pressed, onKey,
            marked = if (q.feedback == Feedback.WRONG) setOf(item.pitch.midi) else emptySet(),
            enabled = q.feedback == null || q.canContinue)
        Text("ถูกครั้งแรก ${q.firstCorrect} / ${q.firstAnswered} · ${if (q.feedback == Feedback.CORRECT) "กำลังไปข้อถัดไป" else "ตอบเสียงและอ็อกเทฟให้ตรง"}",
            color = Tokens.Palette.InkMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}
