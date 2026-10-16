package com.id.mimikopi.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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

/** The section practice tab: the user's sections, newest first, and a button to write a new one. */
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
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            TabHeader(stringResource(R.string.tab_sections), onOpenMidi, onOpenSettings)
            Text(stringResource(R.string.sections_intro), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { naming = true }, shape = RoundedCornerShape(Tokens.Radius.button), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("  " + stringResource(R.string.sections_new), style = MaterialTheme.typography.titleMedium)
                }
                sections.forEach { s ->
                    PageCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (s.stats.mastered) Pill(stringResource(R.string.sections_mastered), Tokens.Palette.GoodSoft, Tokens.Palette.GoodInk)
                        }
                        Text(stringResource(R.string.sections_summary, s.steps.size, s.phrases.size), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
                        val acc = s.stats.accuracy
                        if (acc != null) Text(
                            stringResource(R.string.sections_stats, s.stats.cleanFullRuns, s.stats.fullRuns, (acc * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Button(onClick = { onPractice(s.id) }, enabled = s.steps.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) {
                                Text(stringResource(R.string.sections_practice))
                            }
                            OutlinedButton(onClick = { onEdit(s.id) }, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.sections_edit)) }
                            TextButton(onClick = { deleting = s }, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.sections_delete)) }
                        }
                    }
                }
            }
        }
    }

    if (naming) {
        var title by remember { mutableStateOf("") }
        val fallback = stringResource(R.string.sections_default_title, sections.size + 1)
        AlertDialog(
            onDismissRequest = { naming = false },
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
            title = { Text(stringResource(R.string.sections_delete_q, s.title)) },
            confirmButton = { TextButton(onClick = { vm.delete(s.id); deleting = null }) { Text(stringResource(R.string.sections_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- note tiles

/** One step in large type: a note name ("F♯" with a small octave) or a chord (names stacked). */
@Composable
private fun StepTile(
    step: Step,
    flats: Boolean,
    background: Color,
    border: Color,
    textColor: Color,
    size: TextUnit,
    modifier: Modifier = Modifier,
    pressed: Set<Int> = emptySet(),
) {
    Column(
        modifier
            .background(background, RoundedCornerShape(14.dp))
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        step.notes.reversed().forEach { n ->
            val p = Spelling.pitch(n, flats)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    p.letter.name + com.id.mimikopi.core.music.SpelledPitch.accidentalSymbol(p.accidental),
                    fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Black, fontSize = if (step.notes.size > 1) size * 0.7f else size,
                    color = if (n in pressed) Tokens.Palette.Good else textColor,
                )
                Text("${p.octave}", fontSize = size * 0.32f, color = textColor.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
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
            color = if (selected == null) Tokens.Palette.InkMuted else Tokens.Palette.RoseDeep)

        if (s.steps.isEmpty()) PageCard {
            Text(stringResource(R.string.sections_empty), style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.InkMuted)
        }
        s.phrases.forEachIndexed { pi, range ->
            PageCard(title = stringResource(R.string.sections_phrase, pi + 1)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in range) {
                        val isSel = i == selected
                        StepTile(
                            s.steps[i], s.flats,
                            background = if (isSel) Tokens.Palette.MustardSoft else Tokens.Palette.Card,
                            border = if (isSel) Tokens.Palette.Mustard else Tokens.Palette.Line,
                            textColor = Tokens.Palette.Ink, size = 34.sp,
                            modifier = Modifier.clickable { vm.select(i) },
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
        Button(onClick = { onPractice(s.id) }, enabled = s.steps.isNotEmpty(), shape = RoundedCornerShape(Tokens.Radius.button), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
            Text(stringResource(R.string.sections_start_practice), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.sections_clear_q)) },
        confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text(stringResource(R.string.sections_clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---------------------------------------------------------------- practice

@OptIn(ExperimentalLayoutApi::class)
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
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back)) }
            Text(s?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::listen) { Text(stringResource(R.string.sections_listen)) }
            if (s != null) TextButton(onClick = { onEdit(s.id) }) { Text(stringResource(R.string.sections_edit)) }
        }
        if (s == null || s.steps.isEmpty()) return@Column
        val phrases = s.phrases
        // Part chooser: each phrase, then the whole section.
        if (phrases.size > 1) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            phrases.indices.forEach { i ->
                FilterChip(selected = ui.mode == SectionTrainer.Mode.Phrase(i), onClick = { vm.select(SectionTrainer.Mode.Phrase(i)) },
                    label = { Text(stringResource(R.string.sections_phrase, i + 1)) })
            }
            FilterChip(selected = ui.mode == SectionTrainer.Mode.Full, onClick = { vm.select(SectionTrainer.Mode.Full) },
                label = { Text(stringResource(R.string.sections_whole)) })
        }

        // Where you are and how many clean runs in a row (no rewards, just the count toward the goal).
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val mode = ui.mode
            Text(
                when (mode) {
                    is SectionTrainer.Mode.Phrase -> stringResource(R.string.sections_mode_phrase, mode.index + 1, phrases.size)
                    SectionTrainer.Mode.Full -> stringResource(R.string.sections_mode_full)
                },
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            repeat(ui.goal) { k ->
                Spacer(Modifier.size(14.dp).background(if (k < ui.streak) Tokens.Palette.Good else Tokens.Palette.Track, CircleShape))
            }
            Text(stringResource(R.string.sections_streak, ui.streak, ui.goal), style = MaterialTheme.typography.labelMedium, color = Tokens.Palette.InkMuted)
        }

        // The notes, as large as the screen allows: the phrase being played.
        val phraseIndex = phrases.indexOfFirst { ui.position in it }.coerceAtLeast(0)
        val range = phrases[phraseIndex]
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
            if (ui.mode == SectionTrainer.Mode.Full) Text(stringResource(R.string.sections_phrase, phraseIndex + 1), style = MaterialTheme.typography.labelLarge,
                color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in range) {
                    val done = i < ui.position
                    val now = i == ui.position
                    StepTile(
                        s.steps[i], s.flats,
                        background = when { now -> Tokens.Palette.RoseSoft; done -> Tokens.Palette.GoodSoft; else -> Tokens.Palette.Card },
                        border = when { now -> Tokens.Palette.RoseDeep; done -> Tokens.Palette.GoodSoft; else -> Tokens.Palette.Line },
                        textColor = if (done) Tokens.Palette.GoodInk else Tokens.Palette.Ink,
                        size = if (now) 56.sp else 46.sp,
                        pressed = if (now) ui.pressed else emptySet(),
                    )
                }
            }
        }

        val (message, color) = when (ui.last) {
            Outcome.WRONG -> stringResource(R.string.sections_wrong) to Tokens.Palette.Error
            Outcome.PHRASE_RUN_DONE -> stringResource(R.string.sections_phrase_run, ui.goal - ui.streak) to Tokens.Palette.GoodInk
            Outcome.PHRASE_PASSED -> stringResource(R.string.sections_phrase_passed) to Tokens.Palette.GoodInk
            Outcome.FULL_RUN_DONE -> (if (ui.streak > 0) stringResource(R.string.sections_full_clean, ui.goal - ui.streak) else stringResource(R.string.sections_full_mistakes)) to
                (if (ui.streak > 0) Tokens.Palette.GoodInk else Tokens.Palette.Off)
            Outcome.MASTERED -> stringResource(R.string.sections_mastered_now) to Tokens.Palette.GoodInk
            else -> stringResource(R.string.sections_play_hint) to Tokens.Palette.InkMuted
        }
        Text(message, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp))
        KeyHint(marked = emptySet(), nameStyle = NoteNameStyle.LETTER)
    }
}
