package com.id.mimikopi.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.width
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.SectionTrainer
import com.id.mimikopi.core.section.SectionTrainer.Outcome
import com.id.mimikopi.core.section.Spelling
import com.id.mimikopi.core.section.Step
import com.id.mimikopi.ui.components.KeyHint
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.Pill
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.components.TabHeader
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.core.music.NoteNameStyle
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- list (tab)

/** The section practice tab: a dashed "write a new section" card, then the user's sections, newest first. */
@Composable
fun SectionsScreen(
    onPractice: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenMidi: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: SectionListViewModel = viewModel(),
) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Section?>(null) }

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            TabHeader(stringResource(R.string.tab_sections), stringResource(R.string.sections_subtitle), onOpenMidi, onOpenSettings)
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NewSectionCard { naming = true }
                sections.forEach { s -> SectionCard(s, onPractice = { onPractice(s.id) }, onEdit = { onEdit(s.id) }, onDelete = { deleting = s }) }
            }
        }
    }

    if (naming) {
        var title by remember { mutableStateOf("") }
        val fallback = stringResource(R.string.sections_default_title, sections.size + 1)
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = Color(0xFF111111),
            title = { Text(stringResource(R.string.sections_name_title)) },
            text = { OutlinedTextField(value = title, onValueChange = { title = it.take(60) }, singleLine = true, placeholder = { Text(fallback) }) },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    scope.launch { onEdit(vm.create(title.ifBlank { fallback })) }
                }) { Text(stringResource(R.string.sections_create)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Color(0xFF111111),
            title = { Text(stringResource(R.string.sections_delete_q, s.title)) },
            confirmButton = { TextButton(onClick = { vm.delete(s.id); deleting = null }) { Text(stringResource(R.string.sections_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun NewSectionCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(Tokens.Radius.card)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 92.dp).clip(shape)
            .drawBehind {
                drawRoundRect(Tokens.Palette.Dim, cornerRadius = CornerRadius(Tokens.Radius.card.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(48.dp).background(Tokens.Palette.Ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = Tokens.Palette.OnInk, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(stringResource(R.string.sections_new), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.sections_new_body), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        }
    }
}

@Composable
private fun SectionCard(s: Section, onPractice: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(Tokens.Radius.card)
    Column(
        Modifier.fillMaxWidth().border(1.dp, Tokens.Palette.Line, shape).padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            Text(s.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2)
            if (s.stats.mastered) Pill(stringResource(R.string.sections_mastered))
        }
        // The first notes, so a section is recognised at a glance; a bar marks each phrase start.
        if (s.steps.isNotEmpty()) Text(
            s.steps.take(14).mapIndexed { i, st -> (if (i > 0 && i in s.breaks) "│ " else "") + st.notes.joinToString("+") { s.name(it) } }
                .joinToString(" ") + if (s.steps.size > 14) " …" else "",
            fontFamily = Tokens.Fonts.mono, fontSize = 14.sp, color = Tokens.Palette.InkMuted, maxLines = 1,
        )
        // One segment per phrase: white once the phrase has been played cleanly.
        if (s.phrases.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            s.phrases.indices.forEach { i ->
                val done = s.stats.mastered || (s.stats.phraseClean[i] ?: 0) >= 3
                Box(Modifier.weight(1f).height(3.dp).background(if (done) Tokens.Palette.Ink else Tokens.Palette.Line))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val acc = s.stats.accuracy
            Text(
                stringResource(R.string.sections_meta, s.steps.size, s.phrases.size) + (acc?.let { " · ${(it * 100).toInt()}%" } ?: ""),
                fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.sections_delete), color = Tokens.Palette.InkMuted) }
            TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.sections_edit), color = Tokens.Palette.InkMuted) }
            Button(onClick = onPractice, enabled = s.steps.isNotEmpty(), shape = RoundedCornerShape(24.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.sections_practice) + " →", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ---------------------------------------------------------------- note tiles

/** One step in large mono type: a note name with a small octave, or a chord (names stacked). */
@Composable
private fun StepTile(
    step: Step,
    flats: Boolean,
    color: Color,
    size: TextUnit,
    modifier: Modifier = Modifier,
    pressed: Set<Int> = emptySet(),
) {
    Column(modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        step.notes.reversed().forEach { n ->
            val p = Spelling.pitch(n, flats)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    p.letter.name + com.id.mimikopi.core.music.SpelledPitch.accidentalSymbol(p.accidental),
                    fontFamily = Tokens.Fonts.mono, fontWeight = FontWeight.Medium, fontSize = if (step.notes.size > 1) size * 0.7f else size,
                    color = if (n in pressed) Tokens.Palette.Dim else color,
                )
                Text("${p.octave}", fontFamily = Tokens.Fonts.mono, fontSize = size * 0.32f, color = color, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- recorder / editor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionEditScreen(onBack: () -> Unit, onPractice: (String) -> Unit, vm: SectionEditViewModel = viewModel()) {
    val section by vm.section.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    ScreenScaffold(title = stringResource(R.string.sections_edit_title), onBack = onBack) {
        val s = section ?: return@ScreenScaffold
        OutlinedTextField(value = s.title, onValueChange = vm::rename, singleLine = true, label = { Text(stringResource(R.string.sections_name_title)) }, modifier = Modifier.fillMaxWidth())
        KeyHint(marked = emptySet(), nameStyle = NoteNameStyle.LETTER)
        Text(stringResource(if (selected == null) R.string.sections_record_help else R.string.sections_replace_help), style = MaterialTheme.typography.bodyMedium,
            color = if (selected == null) Tokens.Palette.InkMuted else Tokens.Palette.Ink)

        if (s.steps.isEmpty()) PageCard {
            Text(stringResource(R.string.sections_empty), style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.InkMuted)
        }
        s.phrases.forEachIndexed { pi, range ->
            PageCard(title = stringResource(R.string.sections_phrase, pi + 1)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in range) {
                        val isSel = i == selected
                        val tileShape = RoundedCornerShape(14.dp)
                        StepTile(
                            s.steps[i], s.flats,
                            color = if (isSel) Tokens.Palette.OnInk else Tokens.Palette.Ink, size = 32.sp,
                            modifier = Modifier.clip(tileShape)
                                .then(if (isSel) Modifier.background(Tokens.Palette.Ink) else Modifier.border(1.dp, Tokens.Palette.Line, tileShape))
                                .clickable { vm.select(i) }.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        val sel = selected
        if (sel != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(onClick = vm::deleteSelected, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.sections_delete_note)) }
            if (sel != 0) OutlinedButton(onClick = vm::togglePhraseStart, modifier = Modifier.heightIn(min = Tokens.minTouch)) {
                Text(stringResource(if (sel in s.breaks) R.string.sections_join_phrase else R.string.sections_split_here))
            }
            TextButton(onClick = { vm.select(sel) }, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.action_cancel)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(onClick = vm::undo, enabled = s.steps.isNotEmpty(), modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.sections_undo)) }
            OutlinedButton(onClick = vm::listen, enabled = s.steps.isNotEmpty(), modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.sections_listen)) }
            OutlinedButton(onClick = vm::autoPhrases, enabled = s.steps.size > 6, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.sections_auto_phrases)) }
            FilterChip(selected = s.flats, onClick = { vm.setFlats(!s.flats) }, label = { Text(stringResource(R.string.sections_flats)) })
            TextButton(onClick = { confirmClear = true }, enabled = s.steps.isNotEmpty(), modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.sections_clear)) }
        }
        Button(onClick = { onPractice(s.id) }, enabled = s.steps.isNotEmpty(), shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.sections_start_practice), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        containerColor = Color(0xFF111111),
        title = { Text(stringResource(R.string.sections_clear_q)) },
        confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text(stringResource(R.string.sections_clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---------------------------------------------------------------- practice

/**
 * Practising a section, landscape-first: the note to play very large in the middle with a line under it, the notes
 * already played small and dark on the left, the next ones fading out on the right. A wrong note restarts the phrase.
 */
@Composable
fun SectionPracticeScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: SectionPracticeViewModel = viewModel()) {
    val section by vm.section.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }
    LaunchedEffect(ui.wrongCount) { if (ui.wrongCount > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    val s = section
    Column(Modifier.fillMaxSize().paper().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back)) }
            Text(s?.title ?: "", style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (s != null && s.phrases.size > 1) Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 16.dp)) {
                s.phrases.indices.forEach { i -> PartTab(stringResource(R.string.sections_phrase, i + 1), ui.mode == SectionTrainer.Mode.Phrase(i)) { vm.select(SectionTrainer.Mode.Phrase(i)) } }
                PartTab(stringResource(R.string.sections_whole), ui.mode == SectionTrainer.Mode.Full) { vm.select(SectionTrainer.Mode.Full) }
            } else Spacer(Modifier.weight(1f))
            if (s != null) TextButton(onClick = { onEdit(s.id) }) { Text(stringResource(R.string.sections_edit), color = Tokens.Palette.InkMuted) }
            OutlinedButton(onClick = vm::listen, shape = RoundedCornerShape(22.dp), modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.sections_listen)) }
        }
        if (s == null || s.steps.isEmpty()) return@Column
        val phrases = s.phrases
        val range = phrases[phrases.indexOfFirst { ui.position in it }.coerceAtLeast(0)]
        val before = (range.first until ui.position).toList().takeLast(3)
        val after = ((ui.position + 1)..range.last).toList().take(3)

        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        ) {
            before.forEach { i -> StepTile(s.steps[i], s.flats, color = Tokens.Palette.Faint, size = 36.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StepTile(s.steps[ui.position], s.flats, color = Tokens.Palette.Ink, size = 132.sp, pressed = ui.pressed)
                Box(Modifier.width(64.dp).height(3.dp).background(Tokens.Palette.Ink))
            }
            after.forEachIndexed { k, i ->
                StepTile(s.steps[i], s.flats, color = listOf(Tokens.Palette.Ink.copy(alpha = 0.8f), Tokens.Palette.InkMuted, Tokens.Palette.Dim)[k], size = 52.sp)
            }
        }

        val message = when (ui.last) {
            Outcome.WRONG -> stringResource(R.string.sections_wrong)
            Outcome.PHRASE_RUN_DONE -> stringResource(R.string.sections_phrase_run, ui.goal - ui.streak)
            Outcome.PHRASE_PASSED -> stringResource(R.string.sections_phrase_passed)
            Outcome.FULL_RUN_DONE -> if (ui.streak > 0) stringResource(R.string.sections_full_clean, ui.goal - ui.streak) else stringResource(R.string.sections_full_mistakes)
            Outcome.MASTERED -> stringResource(R.string.sections_mastered_now)
            else -> stringResource(R.string.sections_play_hint)
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // A wrong note is the one message shown inverted, so it is seen from the piano without colour.
            Text(
                message, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false)
                    .then(if (ui.last == Outcome.WRONG) Modifier.background(Tokens.Palette.Ink, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp) else Modifier),
                color = if (ui.last == Outcome.WRONG) Tokens.Palette.OnInk else Tokens.Palette.Ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                when (val mode = ui.mode) {
                    is SectionTrainer.Mode.Phrase -> stringResource(R.string.sections_mode_phrase, mode.index + 1, phrases.size)
                    SectionTrainer.Mode.Full -> stringResource(R.string.sections_whole)
                },
                fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = Tokens.Palette.InkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(ui.goal) { k -> Box(Modifier.width(26.dp).height(4.dp).background(if (k < ui.streak) Tokens.Palette.Ink else Tokens.Palette.Line)) }
            }
            Text("${ui.streak}/${ui.goal}", fontFamily = Tokens.Fonts.mono, fontSize = 13.sp)
        }
        KeyHint(marked = emptySet(), nameStyle = NoteNameStyle.LETTER)
    }
}

@Composable
private fun PartTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 10.dp)
            .drawBehind { if (selected) drawRect(Tokens.Palette.Ink, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx())) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
    }
}
