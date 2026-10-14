package com.id.mimikopi.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.ui.components.PianoKeyboard
import com.id.mimikopi.ui.theme.Tokens

/**
 * Reader / play-along screen, laid out for landscape on the music stand: a thin top bar, the score strip,
 * one row of controls (or the result), and the on-screen keyboard (can be hidden when a piano is connected).
 */
@Composable
fun ReaderScreen(onBack: () -> Unit, vm: ReaderViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    com.id.mimikopi.ui.components.AnswerHaptics(ui.result?.clean)
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    val piece by vm.piece.collectAsStateWithLifecycle()
    var showOptions by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    val score = piece?.score
    val layout = piece?.layout
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(
                if (score == null) stringResource(R.string.reader_not_found) else vm.title(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FilterChip(selected = ui.showNames, onClick = vm::toggleNames, label = { Text(stringResource(R.string.reader_names)) })
            Spacer(Modifier.width(Tokens.Space.s))
        }
        if (score == null || layout == null) return@Column

        val playing = ui.phase == Phase.PLAYING || ui.phase == Phase.COUNT_IN || ui.phase == Phase.LISTENING
        if (ui.options.pageView) PagedScoreView(
            score = score,
            cursorBeat = vm::cursorAt,
            playing = playing,
            results = ui.results,
            wrongMarks = ui.wrongMarks,
            loop = if (ui.section.first == 0 && ui.section.last == score.measures - 1) null else ui.section,
            hotMeasures = ui.hotMeasures,
            showNames = ui.showNames,
            nameStyle = vm.nameStyle,
            onMeasureTap = vm::onMeasureTap,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            hideNotes = ui.options.hideNotes && ui.phase != Phase.RESULT,
        ) else ScoreView(
            score = score,
            layout = layout,
            cursorBeat = vm::cursorAt,
            following = playing,
            results = ui.results,
            wrongMarks = ui.wrongMarks,
            loop = if (ui.section.first == 0 && ui.section.last == score.measures - 1) null else ui.section,
            hotMeasures = ui.hotMeasures,
            showNames = ui.showNames,
            nameStyle = vm.nameStyle,
            onMeasureTap = vm::onMeasureTap,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            hideNotes = ui.options.hideNotes && ui.phase != Phase.RESULT,
        )

        ui.message?.let { msg ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Error, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::dismissMessage) { Text(stringResource(R.string.reader_done)) }
            }
        }

        when (ui.phase) {
            Phase.IDLE -> if (vm.fixed) FixedControls(ui, vm, onOptions = { showOptions = true }) else SetupControls(ui, vm, onOptions = { showOptions = true })
            Phase.COUNT_IN, Phase.PLAYING, Phase.LISTENING -> RunControls(ui, vm)
            Phase.RESULT -> ui.result?.let { ResultPanel(it, vm, onBack) }
        }

        if (ui.options.showKeyboard && ui.phase != Phase.RESULT) {
            PianoKeyboard(
                lowNote = piece?.keyRange?.first ?: 48,
                highNote = piece?.keyRange?.last ?: 72,
                pressed = pressed,
                showNames = ui.showNames,
                nameStyle = vm.nameStyle,
                onNoteOn = { n, depth -> dispatchTouch(vm, n, (40 + depth * 80).toInt().coerceIn(1, 127)) },
                onNoteOff = { n -> dispatchTouch(vm, n, 0) },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = Tokens.Space.s, vertical = Tokens.Space.xs),
            )
        }
    }

    if (showOptions) OptionsDialog(ui.options, vm, onDismiss = { showOptions = false })
}

@Composable
private fun SetupControls(ui: ReaderUi, vm: ReaderViewModel, onOptions: () -> Unit) {
    val score = vm.score ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s)) {
        if (ui.packMissing) Text(stringResource(R.string.reader_pack_missing), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.Error)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = vm::start) { Text(stringResource(R.string.reader_start)) }
            OutlinedButton(onClick = vm::listen) { Text(stringResource(R.string.reader_listen)) }
            FilterChip(selected = ui.options.mode == PlayMode.WAIT, onClick = { vm.setMode(PlayMode.WAIT) }, label = { Text(stringResource(R.string.reader_mode_wait)) })
            FilterChip(selected = ui.options.mode == PlayMode.TEMPO, onClick = { vm.setMode(PlayMode.TEMPO) }, label = { Text(stringResource(R.string.reader_mode_tempo)) })
            Spacer(Modifier.width(Tokens.Space.xs))
            for ((choice, label) in listOf(HandChoice.RIGHT to R.string.reader_hand_right, HandChoice.LEFT to R.string.reader_hand_left, HandChoice.BOTH to R.string.reader_hand_both)) {
                FilterChip(selected = ui.options.hands == choice, onClick = { vm.setHands(choice) }, label = { Text(stringResource(label)) })
            }
            Spacer(Modifier.width(Tokens.Space.xs))
            val manualTempo = !(ui.options.mode == PlayMode.TEMPO && ui.options.useRamp)
            TextButton(onClick = { vm.changeBpm(-5) }, enabled = manualTempo) { Text(stringResource(R.string.reader_slower)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.reader_bpm, ui.bpm), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.reader_bpm_target, ui.targetBpm), style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
            }
            TextButton(onClick = { vm.changeBpm(5) }, enabled = manualTempo) { Text(stringResource(R.string.reader_faster)) }
            Text(
                if (ui.section.first == ui.section.last) stringResource(R.string.reader_section_one, ui.section.first + 1)
                else stringResource(R.string.reader_section, ui.section.first + 1, ui.section.last + 1),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = vm::wholePiece) { Text(stringResource(R.string.reader_whole_piece)) }
            TextButton(onClick = onOptions) { Text(stringResource(R.string.reader_more)) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (ui.pendingSectionStart != null) R.string.reader_tap_end_hint else R.string.reader_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Tokens.Palette.InkMuted,
            )
            if (ui.hotMeasures.isNotEmpty()) {
                Text(stringResource(R.string.reader_hot), style = MaterialTheme.typography.bodySmall)
                for (m in ui.hotMeasures.filter { it < score.measures }) {
                    TextButton(onClick = { vm.practiseMeasures(m, m) }) { Text(stringResource(R.string.reader_hot_measure, m + 1)) }
                }
            }
        }
    }
}

/** Drills, new reading and benchmarks: settings come from the exercise; only start / listen / options. */
@Composable
private fun FixedControls(ui: ReaderUi, vm: ReaderViewModel, onOptions: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s)) {
        if (ui.packMissing) Text(stringResource(R.string.reader_pack_missing), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.Error)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = vm::start) { Text(stringResource(if (vm.nextLabelIsNew()) R.string.reader_new_piece else R.string.reader_start)) }
            if (vm.source !is ReaderViewModel.Source.Benchmark) OutlinedButton(onClick = vm::listen) { Text(stringResource(R.string.reader_listen)) }
            val handLabel = when (ui.options.hands) {
                HandChoice.RIGHT -> R.string.reader_hand_right
                HandChoice.LEFT -> R.string.reader_hand_left
                HandChoice.BOTH -> R.string.reader_hand_both
            }
            Text(
                stringResource(R.string.reader_fixed_summary,
                    stringResource(if (ui.options.mode == PlayMode.WAIT) R.string.reader_mode_wait else R.string.reader_mode_tempo),
                    stringResource(handLabel), ui.bpm),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = onOptions) { Text(stringResource(R.string.reader_more)) }
        }
    }
}

@Composable
private fun RunControls(ui: ReaderUi, vm: ReaderViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = vm::stop) { Text(stringResource(R.string.reader_stop)) }
        val status = when {
            ui.phase == Phase.COUNT_IN -> stringResource(R.string.reader_count_in, ui.countIn ?: 0)
            ui.phase == Phase.LISTENING -> stringResource(R.string.reader_listening, ui.bpm)
            ui.options.mode == PlayMode.WAIT -> stringResource(R.string.reader_playing_wait)
            else -> stringResource(R.string.reader_playing_tempo, ui.bpm)
        }
        Text(
            status,
            style = if (ui.phase == Phase.COUNT_IN) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultPanel(r: ResultUi, vm: ReaderViewModel, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.m, vertical = Tokens.Space.xs),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.xs),
    ) {
        Text(r.headline, style = MaterialTheme.typography.titleMedium, color = if (r.clean == true) Tokens.Palette.Good else MaterialTheme.colorScheme.onSurface)
        if (r.details.isNotEmpty()) Text(r.details, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        r.progressLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Good) }
        for (line in r.coach) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• " + line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                line.action?.let { a -> TextButton(onClick = { vm.applyAction(a) }) { Text(actionLabel(a)) } }
            }
        }
        Text(stringResource(R.string.reader_legend), style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
            if (vm.benchDone) {
                Button(onClick = onBack) { Text(stringResource(R.string.reader_done)) }
            } else {
                Button(onClick = vm::start) { Text(stringResource(if (vm.nextLabelIsNew()) R.string.reader_new_piece else R.string.reader_again)) }
                OutlinedButton(onClick = vm::backToSetup) { Text(stringResource(R.string.reader_back_to_setup)) }
            }
        }
    }
}

@Composable
private fun actionLabel(a: ResultAction): String = when (a) {
    is ResultAction.Loop -> if (a.from == a.to) stringResource(R.string.reader_section_one, a.from + 1) else stringResource(R.string.reader_section, a.from + 1, a.to + 1)
    is ResultAction.Hands -> stringResource(if (a.choice == HandChoice.LEFT) R.string.reader_hand_left else R.string.reader_hand_right)
    is ResultAction.Tempo -> stringResource(R.string.reader_bpm, a.bpm)
}

@Composable
private fun OptionsDialog(o: ReaderOptions, vm: ReaderViewModel, onDismiss: () -> Unit) {
    val fixed = vm.fixed
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_done)) } },
        title = { Text(stringResource(R.string.reader_options_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                OptionRow(R.string.reader_opt_other_hand, R.string.reader_opt_other_hand_body, o.playOtherHand, vm::setPlayOtherHand)
                if (!fixed) OptionRow(R.string.reader_opt_metronome, R.string.reader_opt_metronome_body, o.metronome, vm::setMetronome)
                if (!fixed) OptionRow(R.string.reader_opt_ramp, R.string.reader_opt_ramp_body, o.useRamp, vm::setUseRamp)
                OptionRow(R.string.reader_opt_keyboard, R.string.reader_opt_keyboard_body, o.showKeyboard, vm::setShowKeyboard)
                OptionRow(R.string.reader_opt_page, R.string.reader_opt_page_body, o.pageView, vm::setPageView)
                if (!fixed) OptionRow(R.string.reader_opt_hide, R.string.reader_opt_hide_body, o.hideNotes, vm::setHideNotes)
                if (!fixed) OptionRow(R.string.reader_opt_loop, R.string.reader_opt_loop_body, o.loopListen, vm::setLoopListen)
            }
        },
    )
}

@Composable
private fun OptionRow(title: Int, body: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        }
        Box(Modifier.padding(start = Tokens.Space.s)) { Switch(checked = checked, onCheckedChange = onChange) }
    }
}

private fun dispatchTouch(vm: ReaderViewModel, note: Int, velocity: Int) {
    val hub = vm.getApplication<MimikopiApp>().inputHub
    hub.dispatch(
        if (velocity > 0) InputEvent.NoteOn(note, velocity, System.nanoTime(), InputSource.TOUCH)
        else InputEvent.NoteOff(note, System.nanoTime(), InputSource.TOUCH),
    )
}
