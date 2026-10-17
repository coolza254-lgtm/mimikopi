package com.id.mimikopi.desktop.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.SectionTrainer
import com.id.mimikopi.core.section.SectionTrainer.Outcome
import com.id.mimikopi.core.section.Spelling
import com.id.mimikopi.core.section.Step
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.port.components.FallingNote
import com.id.mimikopi.desktop.port.components.FallingNotesView
import com.id.mimikopi.desktop.port.components.FallingState
import com.id.mimikopi.desktop.port.components.fallingKeyRange
import com.id.mimikopi.desktop.port.components.KeyHint
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.desktop.port.components.midiConnected
import com.id.mimikopi.desktop.port.components.rememberTouchPlayer
import androidx.compose.runtime.rememberUpdatedState
import com.id.mimikopi.desktop.ui.BackHeader
import com.id.mimikopi.desktop.ui.NoKeyboardBanner
import com.id.mimikopi.desktop.ui.Pill
import com.id.mimikopi.desktop.ui.Tokens

/** The section practice tab: "write a new section", then the sections, newest first. */
@Composable
fun SectionsHome(onPractice: (String) -> Unit, onEdit: (String) -> Unit, onOpenMidi: () -> Unit) {
    val sections by Services.sections.sections.collectAsState()
    var deleting by remember { mutableStateOf<Section?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ฝึกท่อน", style = MaterialTheme.typography.headlineMedium)
        Text("ท่อนที่ชอบ จดด้วยการเล่น ฝึกทีละวลี", style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted)
        NoKeyboardBanner(onOpenMidi)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 92.dp).clip(RoundedCornerShape(Tokens.cardRadius))
                .drawBehind { drawRoundRect(Tokens.Palette.Dim, cornerRadius = CornerRadius(Tokens.cardRadius.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))) }
                .clickable {
                    val repo = Services.sections
                    val s = Section(repo.newId(), "ท่อนที่ ${sections.size + 1}", createdAtMillis = System.currentTimeMillis())
                    repo.save(s)
                    onEdit(s.id)
                }.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(48.dp).background(Tokens.Palette.Ink, CircleShape), contentAlignment = Alignment.Center) { Text("+", color = Tokens.Palette.OnInk, fontSize = 24.sp) }
            Column {
                Text("จดท่อนใหม่", style = MaterialTheme.typography.titleMedium)
                Text("เล่นบนคีย์บอร์ด แอพจดโน้ตให้เอง", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            }
        }
        if (sections.isEmpty()) Text("ยังไม่มีท่อน ลองเลือกท่อนสั้นๆ ที่ชอบ 4–8 โน้ตก่อน เช่นท่อนฮุก", style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted)
        sections.forEach { s ->
            Column(Modifier.fillMaxWidth().border(1.dp, Tokens.Palette.Line, RoundedCornerShape(Tokens.cardRadius)).padding(start = 22.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Text(s.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (s.stats.mastered) Pill("เล่นได้แล้ว")
                }
                if (s.steps.isNotEmpty()) Text(
                    s.steps.take(16).mapIndexed { i, st -> (if (i > 0 && i in s.breaks) "│ " else "") + st.notes.joinToString("+") { s.name(it) } }.joinToString(" ") + if (s.steps.size > 16) " …" else "",
                    fontFamily = Tokens.Fonts.mono, fontSize = 14.sp, color = Tokens.Palette.InkMuted, maxLines = 1,
                )
                if (s.phrases.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.phrases.indices.forEach { i -> Box(Modifier.weight(1f).height(3.dp).background(if (s.stats.mastered || (s.stats.phraseClean[i] ?: 0) >= 3) Tokens.Palette.Ink else Tokens.Palette.Line)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.steps.size} โน้ต · ${s.phrases.size} วลี" + (s.stats.accuracy?.let { " · ${(it * 100).toInt()}%" } ?: ""), fontFamily = Tokens.Fonts.mono, fontSize = 12.sp,
                        color = Tokens.Palette.InkMuted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { deleting = s }) { Text("ลบ", color = Tokens.Palette.InkMuted) }
                    TextButton(onClick = { onEdit(s.id) }) { Text("แก้โน้ต", color = Tokens.Palette.InkMuted) }
                    Button(onClick = { onPractice(s.id) }, enabled = s.steps.isNotEmpty()) { Text("ฝึก →") }
                }
            }
        }
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = Tokens.Palette.Raised,
            title = { Text("ลบ \"${s.title}\" ?") },
            confirmButton = { TextButton(onClick = { Services.sections.delete(s.id); deleting = null }) { Text("ลบ") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("ยกเลิก") } },
        )
    }
}

@Composable
private fun StepTile(step: Step, flats: Boolean, color: Color, size: TextUnit, modifier: Modifier = Modifier, pressed: Set<Int> = emptySet()) {
    Column(modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        step.notes.reversed().forEach { n ->
            val p = Spelling.pitch(n, flats)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(p.letter.name + SpelledPitch.accidentalSymbol(p.accidental), fontFamily = Tokens.Fonts.mono, fontWeight = FontWeight.Medium,
                    fontSize = if (step.notes.size > 1) size * 0.7f else size, color = if (n in pressed) Tokens.Palette.Dim else color)
                Text("${p.octave}", fontFamily = Tokens.Fonts.mono, fontSize = size * 0.32f, color = color, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionEditScreen(id: String, onBack: () -> Unit, onPractice: (String) -> Unit, onOpenMidi: () -> Unit) {
    val model = remember(id) { SectionEditModel(id) }
    DisposableEffect(model) { onDispose { model.dispose() } }
    val section by model.section.collectAsState()
    val selected by model.selected.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val s = section ?: return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("จดโน้ต", onBack)
        OutlinedTextField(value = s.title, onValueChange = model::rename, singleLine = true, label = { Text("ชื่อท่อน") }, modifier = Modifier.width(420.dp))
        NoKeyboardBanner(onOpenMidi)
        // Without a keyboard the section can be recorded from the on-screen keys (C3 to C6, wider if it goes beyond).
        val recordRange = remember(s.steps) { (s.steps.flatMap { it.notes } + listOf(48, 84)).let { all -> fallingKeyRange(all.min(), all.max()) } }
        KeyHint(marked = emptySet(), nameStyle = NoteNameStyle.LETTER, range = recordRange, keysHeight = 130.dp)
        Text(if (selected == null) "เล่นบนคีย์บอร์ดทีละโน้ต แอพจดให้เอง กดพร้อมกันจะเป็นคอร์ด ไม่ต้องตรงจังหวะ คลิกโน้ตเพื่อแก้" else "เล่นโน้ตใหม่บนคีย์บอร์ดเพื่อแทนตัวที่เลือก",
            style = MaterialTheme.typography.bodyMedium, color = if (selected == null) Tokens.Palette.InkMuted else Tokens.Palette.Ink)
        if (s.steps.isEmpty()) Text("ยังไม่มีโน้ต เริ่มเล่นได้เลย", style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.InkMuted)
        s.phrases.forEachIndexed { pi, range ->
            Column(Modifier.fillMaxWidth().border(1.dp, Tokens.Palette.Line, RoundedCornerShape(Tokens.cardRadius)).padding(20.dp)) {
                Text("วลี ${pi + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in range) {
                        val isSel = i == selected
                        val shape = RoundedCornerShape(14.dp)
                        StepTile(s.steps[i], s.flats, color = if (isSel) Tokens.Palette.OnInk else Tokens.Palette.Ink, size = 32.sp,
                            modifier = Modifier.clip(shape).then(if (isSel) Modifier.background(Tokens.Palette.Ink) else Modifier.border(1.dp, Tokens.Palette.Line, shape))
                                .clickable { model.select(i) }.padding(horizontal = 4.dp))
                    }
                }
            }
        }
        val sel = selected
        if (sel != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = model::deleteSelected) { Text("ลบตัวนี้") }
            if (sel != 0) OutlinedButton(onClick = model::togglePhraseStart) { Text(if (sel in s.breaks) "รวมกับวลีก่อนหน้า" else "เริ่มวลีใหม่ตรงนี้") }
            TextButton(onClick = { model.select(sel) }) { Text("ยกเลิก") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = model::undo, enabled = s.steps.isNotEmpty()) { Text("ลบตัวล่าสุด") }
            OutlinedButton(onClick = model::listen, enabled = s.steps.isNotEmpty()) { Text("ฟัง") }
            OutlinedButton(onClick = model::autoPhrases, enabled = s.steps.size > 6) { Text("แบ่งวลีอัตโนมัติ") }
            FilterChip(selected = s.flats, onClick = { model.setFlats(!s.flats) }, label = { Text("แสดงเป็น ♭") })
            TextButton(onClick = { confirmClear = true }, enabled = s.steps.isNotEmpty()) { Text("ล้างทั้งหมด") }
        }
        Button(onClick = { onPractice(s.id) }, enabled = s.steps.isNotEmpty(), modifier = Modifier.width(360.dp).heightIn(min = 60.dp)) { Text("เริ่มฝึกท่อนนี้", style = MaterialTheme.typography.titleMedium) }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, containerColor = Tokens.Palette.Raised,
        title = { Text("ล้างโน้ตทั้งหมดของท่อนนี้?") },
        confirmButton = { TextButton(onClick = { model.clear(); confirmClear = false }) { Text("ล้างทั้งหมด") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("ยกเลิก") } },
    )
}

/** The note to play very large in the middle; played notes small and dark on the left, the next ones fading on the right. */
@Composable
fun SectionPracticeScreen(id: String, onBack: () -> Unit, onEdit: (String) -> Unit, onOpenMidi: () -> Unit) {
    val model = remember(id) { SectionPracticeModel(id) }
    DisposableEffect(model) { onDispose { model.dispose() } }
    val section by model.section.collectAsState()
    val ui by model.ui.collectAsState()
    val falling by model.falling.collectAsState()
    val keys by Services.hub.pressed.collectAsState()
    val s = section ?: return
    Column(Modifier.fillMaxWidth()) {
        BackHeader(s.title, onBack) {
            TextButton(onClick = { model.setFalling(!falling) }) { Text(if (falling) "ตัวใหญ่" else "โน้ตตก", color = Tokens.Palette.InkMuted) }
            TextButton(onClick = { onEdit(s.id) }) { Text("แก้โน้ต", color = Tokens.Palette.InkMuted) }
            OutlinedButton(onClick = model::listen) { Text("ฟัง") }
        }
        if (s.steps.isEmpty()) return@Column
        val phrases = s.phrases
        if (phrases.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            phrases.indices.forEach { i -> PartTab("วลี ${i + 1}", ui.mode == SectionTrainer.Mode.Phrase(i)) { model.select(SectionTrainer.Mode.Phrase(i)) } }
            PartTab("ทั้งท่อน", ui.mode == SectionTrainer.Mode.Full) { model.select(SectionTrainer.Mode.Full) }
        }
        NoKeyboardBanner(onOpenMidi, Modifier.padding(top = 14.dp))
        val range = phrases[phrases.indexOfFirst { ui.position in it }.coerceAtLeast(0)]
        val before = (range.first until ui.position).toList().takeLast(3)
        val after = ((ui.position + 1)..range.last).toList().take(3)
        if (falling) SectionFalling(s, ui.mode, ui.position, ui.pressed, keys, Modifier.fillMaxWidth().padding(vertical = 12.dp).height(480.dp))
        else Row(Modifier.fillMaxWidth().heightIn(min = 320.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
            before.forEach { i -> StepTile(s.steps[i], s.flats, Tokens.Palette.Faint, 40.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StepTile(s.steps[ui.position], s.flats, Tokens.Palette.Ink, 150.sp, pressed = ui.pressed)
                Box(Modifier.width(72.dp).height(3.dp).background(Tokens.Palette.Ink))
            }
            after.forEachIndexed { k, i -> StepTile(s.steps[i], s.flats, listOf(Tokens.Palette.Ink.copy(alpha = 0.8f), Tokens.Palette.InkMuted, Tokens.Palette.Dim)[k], 56.sp) }
        }
        val message = when (ui.last) {
            Outcome.WRONG -> "ผิด เริ่มวลีนี้ใหม่จากโน้ตแรก"
            Outcome.PHRASE_RUN_DONE -> "ไม่ผิดเลย อีก ${ui.goal - ui.streak} รอบติดกันจะไปวลีถัดไป"
            Outcome.PHRASE_PASSED -> "ผ่านวลีนี้แล้ว ต่อวลีถัดไป"
            Outcome.FULL_RUN_DONE -> if (ui.streak > 0) "เล่นจบไม่ผิดเลย อีก ${ui.goal - ui.streak} รอบติดกัน" else "เล่นจบแล้ว แต่มีที่ผิด รอบนี้ไม่นับ เริ่มใหม่ได้เลย"
            Outcome.MASTERED -> "เล่นทั้งท่อนไม่ผิด 3 รอบติดกันแล้ว ฝึกต่อได้เรื่อยๆ ให้ติดมือ"
            else -> "เล่นโน้ตตัวใหญ่ตรงกลาง"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = MaterialTheme.typography.titleMedium, color = if (ui.last == Outcome.WRONG) Tokens.Palette.OnInk else Tokens.Palette.Ink,
                modifier = if (ui.last == Outcome.WRONG) Modifier.background(Tokens.Palette.Ink, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp) else Modifier)
            Spacer(Modifier.weight(1f))
            Text(when (val m = ui.mode) { is SectionTrainer.Mode.Phrase -> "วลี ${m.index + 1} จาก ${phrases.size}"; SectionTrainer.Mode.Full -> "ทั้งท่อน" },
                fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = Tokens.Palette.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(ui.goal) { k -> Box(Modifier.width(26.dp).height(4.dp).background(if (k < ui.streak) Tokens.Palette.Ink else Tokens.Palette.Line)) } }
            Text("${ui.streak}/${ui.goal}", fontFamily = Tokens.Fonts.mono, fontSize = 13.sp)
        }
        // Falling notes draw their own keys (playable with the mouse without a keyboard).
        KeyHint(marked = emptySet(), nameStyle = NoteNameStyle.LETTER, range = sectionKeyRange(s), keyboard = !falling, modifier = Modifier.padding(top = 12.dp))
    }
}

/** The section as falling notes (as on the phone): the step being waited for sits on the line above the keyboard. */
@Composable
private fun SectionFalling(s: Section, mode: SectionTrainer.Mode, position: Int, stepPressed: Set<Int>, keys: Set<Int>, modifier: Modifier) {
    val range = when (mode) {
        is SectionTrainer.Mode.Phrase -> s.phrases.getOrElse(mode.index) { s.steps.indices }
        SectionTrainer.Mode.Full -> s.steps.indices
    }
    val notes = s.steps.flatMapIndexed { i, step ->
        step.notes.map { m ->
            FallingNote(
                m, i.toDouble(), 0.82, left = false, label = s.name(m),
                state = when {
                    i !in range -> FallingState.BACKGROUND
                    i < position || (i == position && m in stepPressed) -> FallingState.DONE
                    else -> FallingState.WAITING
                },
            )
        }
    }
    val keyRange = remember(s.steps) { sectionKeyRange(s) }
    val at by rememberUpdatedState(position.toDouble())
    val touch = rememberTouchPlayer()
    val connected = midiConnected()
    FallingNotesView(
        notes = notes, cursor = { at }, animate = true, keyRange = keyRange, pressed = keys, keyLabel = { m -> s.name(m) },
        modifier = modifier, window = 6.0, smooth = true, lines = s.phrases.map { it.first - 0.09 },
        onKey = if (connected) null else touch,
    )
}

/** The keys a section uses, in whole octaves (two at least). */
private fun sectionKeyRange(s: Section): IntRange = s.steps.flatMap { it.notes }.let { all -> fallingKeyRange(all.min(), all.max()) }

@Composable
private fun PartTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
        Box(Modifier.padding(top = 4.dp).width(28.dp).height(2.dp).background(if (selected) Tokens.Palette.Ink else Color.Transparent))
    }
}
