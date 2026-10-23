package com.id.mimikopi.ui.ear

import com.id.mimikopi.core.i18n.tr
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
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.R
import com.id.mimikopi.core.ear.EarAnswer
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarOption
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.ui.theme.Tokens

internal data class EarColors(val paper: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color, val accent: Color)
internal fun earColors() = if (Tokens.dark) EarColors(Color(0xFF20251E), Color(0xFF2C3329), Color(0xFFEDEEE8), Color(0xFFA9B29E), Color(0xFF424C3B), Color(0xFFBDD19C))
    else EarColors(Color(0xFFFBFAF7), Color(0xFFEEEEE6), Color(0xFF242B21), Color(0xFF6C7466), Color(0xFFDEDFD2), Color(0xFF5D6F48))

/** Layout B: the phase is visible, answers never compete with playback, and MIDI replaces touch answer controls. */
@Composable
internal fun EarSessionContent(
    ui: EarUi, midi: Boolean, onBack: () -> Unit, onChoose: (String) -> Unit,
    onReplay: () -> Unit, onCadence: () -> Unit, onUndo: () -> Unit, onNext: () -> Unit,
    onRetry: () -> Unit, result: @Composable () -> Unit,
) {
    val c = earColors()
    com.id.mimikopi.ui.components.AnswerHaptics(ui.right.takeIf { ui.phase == EarPhase.FEEDBACK })
    Column(Modifier.fillMaxSize().background(c.paper).then(if (ui.phase != EarPhase.DONE) Modifier.keepScreenOn() else Modifier)
        .testTag("ear-session"), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 920.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), tr("กลับ", "Back", "戻る"), tint = c.ink) }
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
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (ui.phase == EarPhase.LOADING) tr("เตรียมเสียงและโจทย์…", "Preparing sounds and questions…", "音と問題を準備中…") else tr("ยังไม่มีข้อให้ฝึกในหมวดนี้", "Nothing to practise in this category yet", "このカテゴリーにはまだ練習する問題がありません"), color = c.muted) }
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
        if (ui.message != null) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(ui.message, color = c.ink, fontSize = 14.sp)
            TextButton(onClick = onRetry) { Text(tr("ลองเปิดเสียงใหม่", "Try starting the sound again", "音をもう一度開始"), color = c.accent) }
        }
        EarFooter(ui, midi, c, onReplay, onCadence, onNext)
    }
}

@Composable
private fun EarQuestionHeading(ui: EarUi, midi: Boolean, c: EarColors, compact: Boolean = false) {
    val q = ui.question ?: return
    Text(if (midi) tr("● ต่อ MIDI · ใช้เปียโนตอบ", "● MIDI connected · answer on the piano", "● MIDI接続中 · ピアノで答える") else tr("● มือถือ · แตะคำตอบ", "● Phone · tap the answer", "● スマホ · 答えをタップ"), color = c.muted, fontSize = 11.sp)
    Text(q.kind.title, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = if (compact) 8.dp else 18.dp))
    Text(earPrompt(q), color = c.ink, fontSize = if (compact) 24.sp else 27.sp, lineHeight = if (compact) 30.sp else 35.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    Text(when {
        ui.phase == EarPhase.FEEDBACK -> tr("ฟังคำตอบ แล้วไปข้อถัดไปเมื่อพร้อม", "Hear the answer, then go on when ready", "答えを聞いて、準備ができたら次へ")
        ui.phase != EarPhase.ANSWER -> tr("ฟังทีละช่วง · รอให้โจทย์จบก่อนตอบ", "Listen part by part · wait for the question to end before answering", "区切りごとに聞く · 問題が終わってから答える")
        q.answer is EarAnswer.Notes -> tr("เล่นทำนองกลับ · อ็อกเทฟไหนก็ได้", "Play the melody back · any octave", "メロディを弾き返す · オクターブは自由")
        q.answer is EarAnswer.Taps -> tr("เคาะจังหวะที่ฟังมา", "Tap the rhythm you heard", "聞いたリズムをタップ")
        midi && (q.kind == EarKind.DEGREE || q.kind == EarKind.BASS) -> tr("ฟังแล้วเล่นโน้ตกลับ · อ็อกเทฟไหนก็ได้", "Listen and play the note back · any octave", "聞いて音を弾き返す · オクターブは自由")
        midi -> tr("กดคีย์ตามคำกำกับด้านล่างเพื่อเลือกคำตอบ", "Press the keys shown below to choose an answer", "下に示した鍵盤を押して答えを選ぶ")
        else -> tr("เลือกคำตอบที่ได้ยิน", "Choose what you heard", "聞こえた答えを選ぶ")
    }, color = c.muted, fontSize = 13.sp, lineHeight = if (compact) 18.sp else 21.sp, modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp))
}

@Composable
private fun EarStage(ui: EarUi, c: EarColors, compact: Boolean = false) {
    val q = ui.question ?: return
    val stages = if (q.cadence) listOf(tr("คอร์ดนำ", "Lead-in", "導入"), tr("พัก", "Pause", "休み"), tr("โจทย์", "Question", "問題"), tr("ตอบ", "Answer", "回答"), tr("เฉลย", "Solution", "答え")) else listOf(tr("โจทย์", "Question", "問題"), tr("ตอบ", "Answer", "回答"), tr("เฉลย", "Solution", "答え"))
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
        ui.paused -> tr("พักการฝึก", "Pause practice", "練習を一時停止")
        ui.audio == EarAudioStage.COUNT_IN -> tr("เตรียมเคาะ · ${ui.countIn ?: 4}", "Get ready to tap · ${ui.countIn ?: 4}", "タップの準備 · ${ui.countIn ?: 4}")
        ui.audio != EarAudioStage.IDLE -> ui.audio.label
        ui.phase == EarPhase.ANSWER -> if (q.answer is EarAnswer.Taps) tr("จังหวะ ${ui.beat} · เคาะแล้ว ${ui.taps} ครั้ง", "Beat ${ui.beat} · ${ui.taps} taps", "${ui.beat}拍目 · タップ${ui.taps}回") else tr("ถึงตาคุณตอบ", "Your turn to answer", "あなたの番です")
        ui.phase == EarPhase.FEEDBACK -> tr("เฉลยจบแล้ว · พร้อมไปต่อ", "Answer finished · ready to go on", "答えが終わりました · 次へ進めます")
        else -> tr("เตรียมฟัง", "Get ready to listen", "聞く準備")
    }
    Text(status, color = c.ink, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("ear-stage").semantics { contentDescription = status })
    if (!compact) Text(when {
        ui.audio == EarAudioStage.WAIT_RELEASE -> tr("แอปรอให้ปล่อยคีย์และแป้นเหยียบ เสียงถัดไปจะไม่เล่นแทรก", "The app waits for you to release the keys and pedal so the next sound doesn't overlap", "次の音が重ならないよう、鍵盤とペダルを離すまでアプリが待ちます")
        ui.audio == EarAudioStage.BREATH -> tr("คอร์ดนำจบแล้ว · เว้นจังหวะก่อนฟังโน้ต", "Lead-in finished · a short pause before the note", "導入が終わりました · 音の前に一呼吸")
        ui.audio == EarAudioStage.FEEDBACK || ui.audio == EarAudioStage.FEEDBACK_GAP -> tr("ฟังโน้ตหรือคอร์ดเฉลยให้จบก่อนเริ่มข้อใหม่", "Let the answer note or chord finish before the next question", "次の問題の前に答えの音やコードを最後まで聞く")
        ui.phase == EarPhase.ANSWER -> tr("ไม่ต้องรีบตอบ · ฟังโจทย์อีกครั้งได้", "No need to hurry · you can hear the question again", "急がなくて大丈夫 · 問題はもう一度聞けます").takeUnless { q.answer is EarAnswer.Taps } ?: tr("จังหวะ ${ui.beat} · เคาะแล้ว ${ui.taps} ครั้ง", "Beat ${ui.beat} · ${ui.taps} taps", "${ui.beat}拍目 · タップ${ui.taps}回")
        else -> tr("แอปจะเปิดรับคำตอบเมื่อเสียงจบ", "The app takes answers once the sound ends", "音が終わると回答を受け付けます")
    }, color = c.muted, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp, bottom = if (compact) 8.dp else 14.dp))
}

@Composable
private fun EarAnswerArea(ui: EarUi, midi: Boolean, c: EarColors, onChoose: (String) -> Unit, onUndo: () -> Unit) {
    val q = ui.question ?: return
    val enabled = ui.phase == EarPhase.ANSWER && ui.audio == EarAudioStage.IDLE && !ui.paused
    if (ui.phase == EarPhase.FEEDBACK) {
        Text(if (ui.right == true) tr("ถูก", "Right", "正解") else tr("ยังไม่ใช่", "Not quite", "ちがいます"), color = c.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(tr("คำตอบ: ${q.reveal}", "Answer: ${q.reveal}", "答え：${q.reveal}"), color = c.ink, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 6.dp))
        if (q.answer is EarAnswer.Notes && ui.right == false) Text(tr("ที่เล่น: ", "Played: ", "弾いた音：") + ui.played.joinToString(" ") { FunctionalEar.solfege(it) }, color = c.muted, fontSize = 13.sp)
        return
    }
    if (q.answer is EarAnswer.Notes) {
        val answer = q.answer as EarAnswer.Notes
        Text(tr("ที่เล่น ${ui.played.size} / ${answer.places.size}", "Played ${ui.played.size} / ${answer.places.size}", "弾いた音 ${ui.played.size} / ${answer.places.size}"), color = c.muted, fontSize = 12.sp)
        Text(ui.played.joinToString(" · ") { FunctionalEar.solfege(it) }.ifEmpty { "—" }, color = c.ink, fontSize = 21.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp))
        TextButton(onClick = onUndo, enabled = enabled && ui.played.isNotEmpty()) { Text(tr("ลบตัวล่าสุด", "Delete the last one", "最後の1つを消す"), color = if (enabled) c.accent else c.muted) }
    }
    if (midi) {
        Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(18.dp)).padding(20.dp).testTag("ear-midi-answer")) {
            Text(if (enabled) {
                when (q.answer) {
                    is EarAnswer.Taps -> tr("กดคีย์ใดก็ได้เพื่อเคาะ", "Press any key to tap", "どの鍵盤でもタップできます")
                    is EarAnswer.Notes -> tr("เล่นทำนองบนเปียโน", "Play the melody on the piano", "ピアノでメロディを弾く")
                    else -> if (q.kind == EarKind.DEGREE || q.kind == EarKind.BASS) tr("เล่นโน้ตที่ได้ยินบนเปียโน", "Play the note you heard on the piano", "聞こえた音をピアノで弾く") else tr("เลือกคำตอบด้วยคีย์เปียโน", "Choose the answer with piano keys", "ピアノの鍵盤で答えを選ぶ")
                }
            } else tr("ฟังให้จบก่อน", "Listen to the end first", "まず最後まで聞く"), color = c.ink, fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            if (q.answer is EarAnswer.Choice && q.kind != EarKind.DEGREE && q.kind != EarKind.BASS) {
                q.midiChoices().forEach { choice ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                        Text(choice.keyName, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(42.dp))
                        Text(choice.option.label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
                Text(tr("ใช้คีย์ตามตัวอักษรข้างต้น · อ็อกเทฟไหนก็ได้", "Use the keys by the letters above · any octave", "上の文字の鍵盤を使う · オクターブは自由"), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            } else Text(if (enabled) tr("ไม่ต้องแตะหน้าจอ · แอปรอรับคำตอบจากเปียโน", "No need to touch the screen · the app waits for the piano", "画面に触れなくて大丈夫 · ピアノからの回答を待ちます") else tr("ตอนนี้ยังไม่รับคำตอบจากคีย์ที่กด", "Key presses aren't taken as answers right now", "今は鍵盤を押しても回答になりません"),
                color = c.muted, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
        }
        return
    }
    when (q.answer) {
        is EarAnswer.Choice -> EarOptions(q.options, enabled, c, onChoose)
        is EarAnswer.Notes -> EarOptions(ui.places.map { EarOption("deg:$it", FunctionalEar.solfege(it), FunctionalEar.degree(it)) }, enabled, c, onChoose)
        is EarAnswer.Taps -> Box(Modifier.fillMaxWidth().heightIn(min = 130.dp).background(c.surface, RoundedCornerShape(20.dp))
            .then(if (enabled) Modifier.clickable { onChoose("tap") } else Modifier.semantics { disabled() }).testTag("ear-tap"), contentAlignment = Alignment.Center) {
            Text(if (enabled) tr("แตะเพื่อเคาะ · จังหวะ ${ui.beat}", "Tap to beat · beat ${ui.beat}", "タップしてリズム · ${ui.beat}拍目") else tr("รอฟังและนับเข้าให้จบ", "Listen and wait for the count-in to finish", "聞いてカウントが終わるまで待つ"), color = c.ink, fontSize = 20.sp,
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
            if (midi && idle) Text(if (ui.index + 1 < ui.questions.size) tr("กด C บนเปียโนเพื่อไปข้อถัดไป หรือแตะปุ่มด้านล่าง", "Press C on the piano for the next question, or tap the button below", "ピアノのCで次の問題へ、または下のボタンをタップ") else tr("กด C บนเปียโนเพื่อดูผล", "Press C on the piano to see the result", "ピアノのCで結果を見る"), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = onNext, enabled = idle, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("ear-next"),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.paper, disabledContainerColor = c.line, disabledContentColor = c.muted)) {
                Text(if (idle) { if (ui.index + 1 < ui.questions.size) tr("ข้อถัดไป", "Next question", "次の問題") else tr("ดูผล", "See result", "結果を見る") } else tr("รอฟังเฉลยให้จบ", "Wait for the answer to finish", "答えが終わるまで待つ"))
            }
            if (q.after.isNotEmpty()) TextButton(onClick = onReplay, enabled = idle, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("ear-replay-feedback")) {
                Text(tr("ฟังเฉลยอีกครั้ง", "Hear the answer again", "答えをもう一度聞く"), color = if (idle) c.accent else c.muted)
            }
        } else if (q.answer !is EarAnswer.Taps) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReplay, enabled = idle && ui.phase == EarPhase.ANSWER, modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("ear-replay")) {
                    Text(tr("ฟังโจทย์อีกครั้ง", "Hear the question again", "問題をもう一度聞く"), color = if (idle) c.ink else c.muted, fontSize = 13.sp)
                }
                if (q.cadence) OutlinedButton(onClick = onCadence, enabled = idle && ui.phase == EarPhase.ANSWER,
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("ear-cadence")) { Text(tr("คอร์ดนำ + โจทย์", "Lead-in + question", "導入＋問題"), color = if (idle) c.ink else c.muted, fontSize = 13.sp) }
            }
        }
        Text(if (midi) tr("คีย์ใช้ตอบ · แอปไม่เล่นเสียงตามคีย์ที่กด", "Keys are for answering · the app doesn't sound the keys you press", "鍵盤は回答用 · 押した鍵盤の音は鳴りません") else tr("เสียงโจทย์และเฉลยเล่นทีละช่วง ไม่ซ้อนกับคำตอบ", "Question and answer sounds play one at a time, never over your answer", "問題と答えの音は1つずつ鳴り、あなたの回答と重なりません"), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun earPrompt(q: EarQuestion) = when (q.kind) {
    EarKind.DEGREE -> tr("โน้ตนี้คือตัวไหน?", "Which note is this?", "この音はどれ？")
    EarKind.BASS -> tr("โน้ตเบส (ต่ำสุด) คือตัวไหน?", "Which is the bass (lowest) note?", "ベース（最低音）はどれ？")
    EarKind.QUALITY -> tr("คอร์ดนี้แบบไหน?", "What kind of chord is this?", "これはどんなコード？")
    EarKind.PROGRESSION -> tr("ทางเดินคอร์ดนี้คืออะไร?", "What progression is this?", "このコード進行は何？")
    EarKind.KEY_CHANGE -> tr("รอบสองเปลี่ยนคีย์ไหม?", "Does the second time change key?", "2回目で転調する？")
    EarKind.MELODY -> tr("เล่นทำนองที่ได้ยินกลับ", "Play back the melody you heard", "聞こえたメロディを弾き返す")
    EarKind.RHYTHM -> tr("เคาะจังหวะที่ได้ยินกลับ", "Tap back the rhythm you heard", "聞こえたリズムをタップで返す")
}
