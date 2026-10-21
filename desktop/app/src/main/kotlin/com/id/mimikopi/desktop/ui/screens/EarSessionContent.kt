package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.ear.EarAnswer
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarOption
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.desktop.ui.theme.Tokens
import com.id.mimikopi.desktop.session.EarAudioStage
import com.id.mimikopi.desktop.session.EarPhase
import com.id.mimikopi.desktop.session.EarUi
import com.id.mimikopi.core.ear.EarMidiAnswers

data class EarColors(val paper: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color, val accent: Color)
fun earColors() = if (Tokens.dark) EarColors(Color(0xFF20251E), Color(0xFF2C3329), Color(0xFFEDEEE8), Color(0xFFA9B29E), Color(0xFF424C3B), Color(0xFFBDD19C))
    else EarColors(Color(0xFFFBFAF7), Color(0xFFEEEEE6), Color(0xFF242B21), Color(0xFF6C7466), Color(0xFFDEDFD2), Color(0xFF5D6F48))

/** Layout B: the phase is visible, answers never compete with playback, and MIDI replaces touch answer controls. */
@Composable
fun EarSessionContent(
    ui: EarUi, midi: Boolean, onBack: () -> Unit, onChoose: (String) -> Unit,
    onReplay: () -> Unit, onCadence: () -> Unit, onUndo: () -> Unit, onNext: () -> Unit,
    onRetry: () -> Unit, result: @Composable () -> Unit,
) {
    val c = earColors()
    Column(Modifier.fillMaxSize().background(c.paper)
        .testTag("ear-session"), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 920.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ กลับ", color = c.ink) }
            Text(ui.title, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
            if (ui.questions.isNotEmpty() && ui.phase != EarPhase.DONE) Text("${ui.index + 1} / ${ui.questions.size}", color = c.muted, fontSize = 13.sp)
        }
        if (ui.questions.isNotEmpty() && ui.phase != EarPhase.DONE) {
            val progress = (ui.index + if (ui.phase == EarPhase.FEEDBACK) 1 else 0).toFloat() / ui.questions.size
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth().padding(horizontal = 24.dp).height(3.dp), color = c.accent, trackColor = c.line)
        }
        if (ui.phase == EarPhase.DONE) {
            Column(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) { result() }
            return@Column
        }
        val q = ui.question
        if (q == null || ui.phase == EarPhase.LOADING) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (ui.phase == EarPhase.LOADING) "เตรียมเสียงและโจทย์…" else "ยังไม่มีข้อให้ฝึกในหมวดนี้", color = c.muted) }
            return@Column
        }
        BoxWithConstraints(Modifier.weight(1f).widthIn(max = 920.dp).fillMaxWidth()) {
            if (maxWidth >= 700.dp) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { EarQuestionHeading(ui, midi, c, compact = true); EarStage(ui, c, compact = true) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { EarAnswerArea(ui, midi, c, onChoose, onUndo) }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)) {
                EarQuestionHeading(ui, midi, c)
                EarStage(ui, c)
                EarAnswerArea(ui, midi, c, onChoose, onUndo)
            }
        }
        ui.message?.let { message -> Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(message, color = c.ink, fontSize = 14.sp)
            TextButton(onClick = onRetry) { Text("ลองเปิดเสียงใหม่", color = c.accent) }
        } }
        EarFooter(ui, midi, c, onReplay, onCadence, onNext)
    }
}

@Composable
private fun EarQuestionHeading(ui: EarUi, midi: Boolean, c: EarColors, compact: Boolean = false) {
    val q = ui.question ?: return
    Text(if (midi) "● ต่อ MIDI · ใช้เปียโนตอบ" else "● ไม่มี MIDI · คลิกคำตอบหรือกดแป้นพิมพ์", color = c.muted, fontSize = 11.sp)
    Text(q.kind.title, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = if (compact) 8.dp else 18.dp))
    Text(earPrompt(q), color = c.ink, fontSize = if (compact) 24.sp else 27.sp, lineHeight = if (compact) 30.sp else 35.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    Text(when {
        ui.phase == EarPhase.FEEDBACK -> "ฟังคำตอบ แล้วไปข้อถัดไปเมื่อพร้อม"
        ui.phase != EarPhase.ANSWER -> "ฟังทีละช่วง · รอให้โจทย์จบก่อนตอบ"
        q.answer is EarAnswer.Notes -> "เล่นทำนองกลับ · อ็อกเทฟไหนก็ได้"
        q.answer is EarAnswer.Taps -> "เคาะจังหวะที่ฟังมา"
        midi && (q.kind == EarKind.DEGREE || q.kind == EarKind.BASS) -> "ฟังแล้วเล่นโน้ตกลับ · อ็อกเทฟไหนก็ได้"
        midi -> "กดคีย์ตามคำกำกับด้านล่างเพื่อเลือกคำตอบ"
        else -> "เลือกคำตอบที่ได้ยิน"
    }, color = c.muted, fontSize = 13.sp, lineHeight = if (compact) 18.sp else 21.sp, modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp))
}

@Composable
private fun EarStage(ui: EarUi, c: EarColors, compact: Boolean = false) {
    val q = ui.question ?: return
    val stages = if (q.cadence) listOf("คอร์ดนำ", "พัก", "โจทย์", "ตอบ", "เฉลย") else listOf("โจทย์", "ตอบ", "เฉลย")
    val active = when {
        ui.phase == EarPhase.FEEDBACK -> stages.lastIndex
        ui.phase == EarPhase.ANSWER || ui.audio == EarAudioStage.COUNT_IN -> stages.lastIndex - 1
        ui.audio == EarAudioStage.CADENCE || ui.audio == EarAudioStage.PREPARE -> 0
        ui.audio == EarAudioStage.BREATH -> if (q.cadence) 1 else 0
        else -> stages.lastIndex - 2
    }
    Row(Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 20.dp, bottom = if (compact) 8.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        stages.forEachIndexed { i, text ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, color = if (i == active) c.ink else c.muted, fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp).background(if (i == active) c.accent else c.line))
            }
        }
    }
    val status = when {
        ui.paused -> "พักการฝึก"
        ui.audio == EarAudioStage.COUNT_IN -> "เตรียมเคาะ · ${ui.countIn ?: 4}"
        ui.audio != EarAudioStage.IDLE -> ui.audio.label
        ui.phase == EarPhase.ANSWER -> if (q.answer is EarAnswer.Taps) "จังหวะ ${ui.beat} · เคาะแล้ว ${ui.taps} ครั้ง" else "ถึงตาคุณตอบ"
        ui.phase == EarPhase.FEEDBACK -> "เฉลยจบแล้ว · พร้อมไปต่อ (Space)"
        else -> "เตรียมฟัง"
    }
    Text(status, color = c.ink, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("ear-stage").semantics { contentDescription = status })
    if (!compact) Text(when {
        ui.audio == EarAudioStage.WAIT_RELEASE -> "แอปรอให้ปล่อยคีย์และแป้นเหยียบ เสียงถัดไปจะไม่เล่นแทรก"
        ui.audio == EarAudioStage.BREATH -> "คอร์ดนำจบแล้ว · เว้นจังหวะก่อนฟังโน้ต"
        ui.audio == EarAudioStage.FEEDBACK || ui.audio == EarAudioStage.FEEDBACK_GAP -> "ฟังโน้ตหรือคอร์ดเฉลยให้จบก่อนเริ่มข้อใหม่"
        ui.phase == EarPhase.ANSWER -> "ไม่ต้องรีบตอบ · ฟังโจทย์อีกครั้งได้".takeUnless { q.answer is EarAnswer.Taps } ?: "จังหวะ ${ui.beat} · เคาะแล้ว ${ui.taps} ครั้ง"
        else -> "แอปจะเปิดรับคำตอบเมื่อเสียงจบ"
    }, color = c.muted, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp, bottom = if (compact) 8.dp else 14.dp))
}

@Composable
private fun EarAnswerArea(ui: EarUi, midi: Boolean, c: EarColors, onChoose: (String) -> Unit, onUndo: () -> Unit) {
    val q = ui.question ?: return
    val enabled = ui.phase == EarPhase.ANSWER && ui.audio == EarAudioStage.IDLE && !ui.paused
    if (ui.phase == EarPhase.FEEDBACK) {
        Text(if (ui.right == true) "ถูก" else "ยังไม่ใช่", color = c.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("คำตอบ: ${q.reveal}", color = c.ink, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 6.dp))
        if (q.answer is EarAnswer.Notes && ui.right == false) Text("ที่เล่น: " + ui.played.joinToString(" ") { FunctionalEar.solfege(it) }, color = c.muted, fontSize = 13.sp)
        return
    }
    if (q.answer is EarAnswer.Notes) {
        val answer = q.answer as EarAnswer.Notes
        Text("ที่เล่น ${ui.played.size} / ${answer.places.size}", color = c.muted, fontSize = 12.sp)
        Text(ui.played.joinToString(" · ") { FunctionalEar.solfege(it) }.ifEmpty { "—" }, color = c.ink, fontSize = 21.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp))
        TextButton(onClick = onUndo, enabled = enabled && ui.played.isNotEmpty()) { Text("ลบตัวล่าสุด", color = if (enabled) c.accent else c.muted) }
    }
    if (midi) {
        Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(18.dp)).padding(20.dp).testTag("ear-midi-answer")) {
            Text(if (enabled) {
                when (q.answer) {
                    is EarAnswer.Taps -> "กดคีย์ใดก็ได้เพื่อเคาะ"
                    is EarAnswer.Notes -> "เล่นทำนองบนเปียโน"
                    else -> if (q.kind == EarKind.DEGREE || q.kind == EarKind.BASS) "เล่นโน้ตที่ได้ยินบนเปียโน" else "เลือกคำตอบด้วยคีย์เปียโน"
                }
            } else "ฟังให้จบก่อน", color = c.ink, fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            if (q.answer is EarAnswer.Choice && q.kind != EarKind.DEGREE && q.kind != EarKind.BASS) {
                EarMidiAnswers.choices(q).forEach { choice ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                        Text(choice.keyName, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(42.dp))
                        Text(choice.option.label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
                Text("ใช้คีย์ตามตัวอักษรข้างต้น · อ็อกเทฟไหนก็ได้", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            } else Text(if (enabled) "ไม่ต้องคลิก · โปรแกรมรอรับคำตอบจากเปียโน" else "ตอนนี้ยังไม่รับคำตอบจากคีย์ที่กด",
                color = c.muted, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
        }
        return
    }
    when (q.answer) {
        is EarAnswer.Choice -> EarOptions(q.options, enabled, c, onChoose)
        is EarAnswer.Notes -> EarOptions(ui.places.map { EarOption("deg:$it", FunctionalEar.solfege(it), FunctionalEar.degree(it)) }, enabled, c, onChoose)
        is EarAnswer.Taps -> Box(Modifier.fillMaxWidth().heightIn(min = 130.dp).background(c.surface, RoundedCornerShape(20.dp))
            .then(if (enabled) Modifier.clickable { onChoose("tap") } else Modifier.semantics { disabled() }).testTag("ear-tap"), contentAlignment = Alignment.Center) {
            Text(if (enabled) "คลิกหรือกดแป้นใดก็ได้เพื่อเคาะ · จังหวะ ${ui.beat}" else "รอฟังและนับเข้าให้จบ", color = c.ink, fontSize = 20.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(18.dp))
        }
    }
}

@Composable
private fun EarOptions(options: List<EarOption>, enabled: Boolean, c: EarColors, onChoose: (String) -> Unit) {
    val columns = if (options.size > 6) 3 else 2
    Column(Modifier.testTag("ear-touch-answers"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { o ->
                    Column(Modifier.weight(1f).heightIn(min = 66.dp).background(c.surface, RoundedCornerShape(12.dp))
                        .then(if (enabled) Modifier.clickable { onChoose(o.id) } else Modifier.semantics { disabled() })
                        .padding(horizontal = 10.dp, vertical = 12.dp).testTag("ear-option-${o.id}"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(o.label, color = if (enabled) c.ink else c.muted, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        o.sub?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.muted, fontSize = 11.sp, textAlign = TextAlign.Center) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EarFooter(ui: EarUi, midi: Boolean, c: EarColors, onReplay: () -> Unit, onCadence: () -> Unit, onNext: () -> Unit) {
    val q = ui.question ?: return
    val idle = ui.audio == EarAudioStage.IDLE && !ui.paused
    Column(Modifier.widthIn(max = 920.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        if (ui.phase == EarPhase.FEEDBACK) {
            if (midi && idle) Text(if (ui.index + 1 < ui.questions.size) "กด C บนเปียโน หรือ Space เพื่อไปข้อถัดไป" else "กด C บนเปียโนเพื่อดูผล", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = onNext, enabled = idle, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("ear-next"),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.paper, disabledContainerColor = c.line, disabledContentColor = c.muted)) {
                Text(if (idle) { if (ui.index + 1 < ui.questions.size) "ข้อถัดไป" else "ดูผล" } else "รอฟังเฉลยให้จบ")
            }
            if (q.after.isNotEmpty()) TextButton(onClick = onReplay, enabled = idle, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("ear-replay-feedback")) {
                Text("ฟังเฉลยอีกครั้ง", color = if (idle) c.accent else c.muted)
            }
        } else if (q.answer !is EarAnswer.Taps) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReplay, enabled = idle && ui.phase == EarPhase.ANSWER, modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("ear-replay")) {
                    Text("ฟังโจทย์อีกครั้ง", color = if (idle) c.ink else c.muted, fontSize = 13.sp)
                }
                if (q.cadence) OutlinedButton(onClick = onCadence, enabled = idle && ui.phase == EarPhase.ANSWER,
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("ear-cadence")) { Text("คอร์ดนำ + โจทย์", color = if (idle) c.ink else c.muted, fontSize = 13.sp) }
            }
        }
        Text(if (midi) "คีย์ใช้ตอบ · โปรแกรมไม่เล่นเสียงตามคีย์ที่กด จึงไม่ทับโจทย์หรือเฉลย" else "เสียงโจทย์และเฉลยเล่นทีละช่วง ไม่ซ้อนกับคำตอบ · แป้นพิมพ์ตอบได้เหมือนคีย์เปียโน", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun earPrompt(q: EarQuestion) = when (q.kind) {
    EarKind.DEGREE -> "โน้ตนี้คือตัวไหน?"
    EarKind.BASS -> "โน้ตเบส (ต่ำสุด) คือตัวไหน?"
    EarKind.QUALITY -> "คอร์ดนี้แบบไหน?"
    EarKind.PROGRESSION -> "ทางเดินคอร์ดนี้คืออะไร?"
    EarKind.KEY_CHANGE -> "รอบสองเปลี่ยนคีย์ไหม?"
    EarKind.MELODY -> "เล่นทำนองที่ได้ยินกลับ"
    EarKind.RHYTHM -> "เคาะจังหวะที่ได้ยินกลับ"
}
