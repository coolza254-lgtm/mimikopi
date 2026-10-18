package com.id.mimikopi.desktop.port.reader

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/reader/ReaderScreen.kt): imports changed, other changes marked "Desktop:".

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.id.mimikopi.desktop.port.painterResource
import com.id.mimikopi.desktop.port.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.id.mimikopi.desktop.port.Lifecycle
import com.id.mimikopi.desktop.port.LifecycleEventEffect
import com.id.mimikopi.desktop.port.collectAsStateWithLifecycle
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.desktop.port.components.FallingNote
import com.id.mimikopi.desktop.port.components.FallingNotesView
import com.id.mimikopi.desktop.port.components.FallingState
import com.id.mimikopi.desktop.port.components.DEFAULT_KEY_RANGE
import com.id.mimikopi.desktop.port.components.KeyHint
import com.id.mimikopi.desktop.port.components.midiConnected
import com.id.mimikopi.desktop.port.components.rememberTouchPlayer
import com.id.mimikopi.desktop.ui.Tokens

/**
 * Reader / play-along screen, laid out for landscape on the music stand: a thin top bar, the score strip,
 * one row of controls (or the result). Played from the MIDI piano only; while playing the notes take the full height.
 */
@Composable
fun ReaderScreen(onBack: () -> Unit, vm: ReaderViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    com.id.mimikopi.desktop.port.components.AnswerHaptics(ui.result?.clean)
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
        val playing = ui.phase == Phase.PLAYING || ui.phase == Phase.COUNT_IN || ui.phase == Phase.LISTENING
        // While playing the title bar steps aside so the notes get the whole height (stop is in the controls).
        if (!playing) Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
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

        if (ui.options.falling && !vm.fixed) FallingScore(
            piece = piece!!,
            ui = ui,
            vm = vm,
            playing = playing,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) else if (ui.options.pageView) PagedScoreView(
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
            paper = ui.options.paperSize,
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
        )

        ui.message?.let { msg ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::dismissMessage) { Text(stringResource(R.string.reader_done)) }
            }
        }

        when (ui.phase) {
            Phase.IDLE -> if (vm.fixed) FixedControls(ui, vm, onOptions = { showOptions = true }) else SetupControls(ui, vm, onOptions = { showOptions = true })
            Phase.COUNT_IN, Phase.PLAYING, Phase.LISTENING -> RunControls(ui, vm)
            Phase.RESULT -> ui.result?.let { ResultPanel(it, vm, onBack) }
        }

        // Falling notes draw their own keys (playable without a piano).
        if (ui.phase != Phase.RESULT) KeyHint(
            marked = emptySet(), nameStyle = vm.nameStyle, range = piece?.keyRange ?: DEFAULT_KEY_RANGE, showNames = ui.showNames,
            keyboard = !(ui.options.falling && !vm.fixed),
        )
    }

    if (showOptions) OptionsDialog(ui.options, vm, onDismiss = { showOptions = false })
}

/** The song as falling notes over a keyboard (the piece's range), names on the bars and on the white keys. */
@Composable
private fun FallingScore(piece: ReaderViewModel.LoadedPiece, ui: ReaderUi, vm: ReaderViewModel, playing: Boolean, modifier: Modifier) {
    val score = piece.score
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    val barStyle = if (vm.nameStyle == NoteNameStyle.SOLFEGE) NoteNameStyle.SOLFEGE else NoteNameStyle.LETTER
    // Each heard note (ties merged) with its key in the judge's results, its hand and its bar (named once per style).
    val base = remember(score, barStyle) {
        val events = score.events.associateBy { it.id }
        score.sounding().map { n ->
            val ev = events.getValue(n.eventId)
            val i = ev.pitches.indexOfFirst { it.midi == n.midi }.coerceAtLeast(0)
            val p = ev.pitches.getOrNull(i) ?: SpelledPitch.fromMidiSharp(n.midi)
            Triple(NoteKey(n.eventId, i), n.hand, FallingNote(n.midi, n.onset, n.duration, left = n.hand == Hand.LEFT, label = p.name(barStyle)))
        }
    }
    // Only the states change while playing: rebuilt when a note is judged or the hands change, not on every key press.
    val results = ui.results
    val hands = ui.options.hands.hands
    val notes = remember(base, results, hands) {
        base.map { (key, hand, note) ->
            val state = when (results[key]) {
                NoteResult.CORRECT, NoteResult.EARLY, NoteResult.LATE, NoteResult.CORRECTED -> FallingState.DONE
                NoteResult.MISSED -> FallingState.MISSED
                else -> if (hand in hands) FallingState.WAITING else FallingState.BACKGROUND
            }
            if (state == note.state) note else note.copy(state = state)
        }
    }
    val bars = remember(score) { (0..score.measures).map { score.measureStart(it) } }
    val keyRange = piece.keyRange
    val keyNames = remember(keyRange, barStyle) { keyRange.associateWith { SpelledPitch.fromMidiSharp(it).name(barStyle) } }
    val wrongMarks = ui.wrongMarks
    val marks = remember(wrongMarks) { wrongMarks.map { it.midi to it.atNanos } }
    val touch = rememberTouchPlayer()
    val connected = midiConnected()
    FallingNotesView(
        notes = notes,
        cursor = vm::cursorAt,
        animate = playing,
        keyRange = keyRange,
        pressed = pressed,
        keyLabel = { m -> keyNames[m] },
        modifier = modifier,
        window = (score.beatsPerMeasure * 2.0).coerceAtLeast(6.0),
        smooth = ui.options.mode == PlayMode.WAIT,
        lines = bars,
        marks = marks,
        onKey = if (connected) null else touch,
    )
}

@Composable
private fun SetupControls(ui: ReaderUi, vm: ReaderViewModel, onOptions: () -> Unit) {
    val score = vm.score ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s)) {
        if (ui.packMissing) Text(stringResource(R.string.reader_pack_missing), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
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
            FilterChip(selected = ui.options.falling, onClick = { vm.setFalling(!ui.options.falling) }, label = { Text(stringResource(R.string.reader_falling)) })
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
            // Only the hint that is needed right now: the second tap of a section choice.
            if (ui.pendingSectionStart != null) Text(
                stringResource(R.string.reader_tap_end_hint),
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
        if (ui.packMissing) Text(stringResource(R.string.reader_pack_missing), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        ui.previewLeft?.let { left ->
            Text(stringResource(R.string.reader_exam_preview, left), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = vm::start) { Text(stringResource(if (vm.nextLabelIsNew()) R.string.reader_new_piece else R.string.reader_start)) }
            val test = vm.source is ReaderViewModel.Source.Benchmark || vm.source is ReaderViewModel.Source.Check || vm.source is ReaderViewModel.Source.Exam
            if (!test) OutlinedButton(onClick = vm::listen) { Text(stringResource(R.string.reader_listen)) }
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

/** While playing: one small floating pill (status, then a white stop button) so the notes keep the height. */
@Composable
private fun RunControls(ui: ReaderUi, vm: ReaderViewModel) {
    Box(Modifier.fillMaxWidth().padding(bottom = Tokens.Space.s), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .border(1.dp, Tokens.Palette.Line, androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val status = when {
                ui.phase == Phase.COUNT_IN -> stringResource(R.string.reader_count_in, ui.countIn ?: 0)
                ui.phase == Phase.LISTENING -> stringResource(R.string.reader_listening, ui.bpm)
                ui.options.mode == PlayMode.WAIT -> stringResource(R.string.reader_playing_wait)
                else -> stringResource(R.string.reader_playing_tempo, ui.bpm)
            }
            Text(status, fontFamily = Tokens.Fonts.mono, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, maxLines = 1)
            Button(onClick = vm::stop, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) { Text(stringResource(R.string.reader_stop)) }
        }
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
        Text(r.headline, style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.Ink)
        if (r.details.isNotEmpty()) Text(r.details, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        r.progressLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink) }
        for (line in r.coach) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• " + line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                line.action?.let { a -> TextButton(onClick = { vm.applyAction(a) }) { Text(actionLabel(a)) } }
            }
        }
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
                OptionRow(R.string.reader_opt_page, R.string.reader_opt_page_body, o.pageView, vm::setPageView)
                if (o.pageView) OptionRow(R.string.reader_opt_paper, R.string.reader_opt_paper_body, o.paperSize, vm::setPaperSize)
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
