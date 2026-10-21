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
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.ui.components.FallingNote
import com.id.mimikopi.ui.components.FallingNotesView
import com.id.mimikopi.ui.components.FallingState
import com.id.mimikopi.ui.components.DEFAULT_KEY_RANGE
import com.id.mimikopi.ui.components.midiConnected
import com.id.mimikopi.ui.components.rememberTouchPlayer
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.reading.ReadingScoreSetup
import com.id.mimikopi.ui.reading.ReadingKeyboard

/**
 * Reader / play-along screen, laid out for landscape on the music stand: a thin top bar, the score strip,
 * simple setup and a focused player. Answers come from MIDI or the touch keyboard; while playing the notes take the full height.
 */
@Composable
fun ReaderScreen(onBack: () -> Unit, onOpenMidi: () -> Unit, vm: ReaderViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    com.id.mimikopi.ui.components.AnswerHaptics(ui.result?.clean)
    val piece by vm.piece.collectAsStateWithLifecycle()
    var showOptions by remember { mutableStateOf(false) }
    val connected = midiConnected()
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    val touch = rememberTouchPlayer()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    val score = piece?.score
    val layout = piece?.layout
    // The screen stays on while a run or the exam's look-first countdown is going: a screen that turns itself off
    // would stop the run (and an exam take would not count).
    val playing = ui.phase == Phase.PLAYING || ui.phase == Phase.COUNT_IN || ui.phase == Phase.LISTENING
    val busy = playing || ui.previewLeft != null
    val fullscreen = busy && piece != null
    val stopPlayer: () -> Unit = if (ui.previewLeft != null) onBack else vm::stop
    ReaderLandscape((playing || ui.previewLeft != null) && !(ui.options.falling && !vm.fixed))
    ReaderFullscreen(fullscreen)
    BackHandler(enabled = fullscreen) { stopPlayer() }
    if (fullscreen) {
        if (ui.options.falling && !vm.fixed) {
            FallingScore(piece!!, ui, vm, playing, Modifier.fillMaxSize().keepScreenOn(), fullscreen = true)
        } else {
            ScorePlayerViewport(ui.previewLeft?.let { "ดูโน้ตก่อนเริ่ม · เหลือ $it วินาที" } ?: runStatus(ui), connected, stopPlayer,
                keyboard = {
                    if (ui.previewLeft == null) ReadingKeyboard(false, if (vm.transposeKey != null) piece!!.keyRange else readingInputRange(score!!, ui.options.hands.hands),
                        vm.nameStyle, ui.showNames, pressed, touch,
                        enabled = ui.phase == Phase.PLAYING, twoHands = ui.options.hands == HandChoice.BOTH, performance = true)
                }) { mod ->
                ScoreView(score!!, layout!!, vm::cursorAt, playing, ui.results, ui.wrongMarks,
                    ui.section.takeUnless { it.first == 0 && it.last == score.measures - 1 }, ui.hotMeasures,
                    ui.showNames, vm.nameStyle, vm::onMeasureTap, mod, everyBarNumber = true)
            }
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .then(if (busy) Modifier.keepScreenOn() else Modifier)
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
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
            if (ui.phase != Phase.RESULT) FilterChip(selected = ui.showNames, onClick = vm::toggleNames, label = { Text(stringResource(R.string.reader_names)) })
            Spacer(Modifier.width(Tokens.Space.s))
        }
        if (score == null || layout == null) return@Column

        if (ui.phase == Phase.IDLE && !ui.options.falling) {
            val test = vm.source is ReaderViewModel.Source.Benchmark || vm.source is ReaderViewModel.Source.Check || vm.source is ReaderViewModel.Source.Exam
            ReadingScoreSetup(ui, connected, canSetPace = !vm.fixed || vm.source is ReaderViewModel.Source.NewReading,
                canSetHands = !vm.fixed, canListen = !test, vm::setMode, vm::setHands, vm::changeBpm,
                vm::start, vm::listen, { showOptions = true }, onOpenMidi, { vm.setFalling(true) },
                countInClicks = com.id.mimikopi.core.score.MeterPulse.of(score).countInClicks,
                rhythmHint = if (score.beatUnit == 8 && score.beatsPerMeasure >= 6 && score.beatsPerMeasure % 3 == 0)
                    "${score.beatsPerMeasure / 3} จังหวะหลักต่อห้อง · เล่น 3 โน้ตต่อหนึ่งจังหวะ · ตัวเลข bpm คิดตามโน้ตดำ" else null) { mod ->
                PagedScoreView(score, vm::cursorAt, false, ui.results, ui.wrongMarks,
                    loop = ui.section.takeUnless { it.first == 0 && it.last == score.measures - 1 }, hotMeasures = ui.hotMeasures,
                    showNames = ui.showNames, nameStyle = vm.nameStyle, onMeasureTap = vm::onMeasureTap,
                    modifier = mod)
            }
            return@Column
        }

        if (ui.phase == Phase.RESULT) {
            ui.result?.let {
                ResultPanel(it, ui, vm, onBack, onOpenMidi, Modifier.weight(1f)) {
                    Text(
                        if (vm.fixed) "โน้ตในรอบนี้ · เลื่อนดูผลได้" else "โน้ตในรอบนี้ · แตะบาร์เพื่อเลือกฝึกเฉพาะจุด",
                        fontSize = 15.sp, color = Tokens.Palette.InkMuted,
                    )
                    PagedScoreView(
                        score = score, cursorBeat = vm::cursorAt, playing = false,
                        results = ui.results, wrongMarks = ui.wrongMarks,
                        loop = ui.section, hotMeasures = ui.hotMeasures,
                        showNames = ui.showNames, nameStyle = vm.nameStyle,
                        onMeasureTap = { m -> if (!vm.fixed) vm.practiseMeasures(m, m) },
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                    )
                }
            }
            return@Column
        }

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
            Phase.RESULT -> Unit
        }

        // Falling notes draw their own keys (playable without a piano).
        if (ui.phase != Phase.RESULT && !(ui.options.falling && !vm.fixed)) ReadingKeyboard(
            connected, if (vm.transposeKey != null) piece?.keyRange ?: DEFAULT_KEY_RANGE else readingInputRange(score, ui.options.hands.hands),
            vm.nameStyle, ui.showNames, pressed, touch,
            enabled = ui.phase != Phase.LISTENING && ui.phase != Phase.COUNT_IN,
            twoHands = ui.options.hands == HandChoice.BOTH,
        )
    }

    if (showOptions) OptionsDialog(ui.options, vm, onDismiss = { showOptions = false })
}

/** The song as falling notes; key names can be hidden independently of the bar labels. */
@Composable
private fun FallingScore(piece: ReaderViewModel.LoadedPiece, ui: ReaderUi, vm: ReaderViewModel, playing: Boolean, modifier: Modifier, fullscreen: Boolean = false) {
    val score = piece.score
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    // Katakana do-re-mi on the bars (as in the tutorial videos), unless letter names are chosen.
    val barStyle = if (vm.nameStyle == NoteNameStyle.LETTER) NoteNameStyle.LETTER else NoteNameStyle.SOLFEGE
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
    // The keyboard names only the notes of the piece's key, spelled as the key spells them; the other keys stay blank.
    val keyNames = remember(keyRange, barStyle, score) {
        val scale = com.id.mimikopi.core.score.KeySignature.scale(score.keyFifths)
        keyRange.mapNotNull { m -> scale[m % 12]?.let { m to it.name(barStyle) } }.toMap()
    }
    val touch = rememberTouchPlayer()
    val connected = midiConnected()
    FallingViewport(fullscreen, ui.options.fallingKeyLabels, vm::setFallingKeyLabels,
        status = runStatus(ui), onStop = vm::stop, modifier = modifier) { canvasModifier ->
        FallingNotesView(
            notes = notes,
            cursor = vm::cursorAt,
            animate = playing,
            keyRange = keyRange,
            pressed = pressed,
            keyLabel = { m -> if (ui.options.fallingKeyLabels) keyNames[m] else null },
            modifier = canvasModifier,
            // About one bar on screen: tall bars that are easy to read, as in tutorial videos.
            window = score.beatsPerMeasure.coerceIn(3, 4).toDouble(),
            smooth = ui.options.mode == PlayMode.WAIT,
            lines = bars,
            feedback = ui.fallingFeedback,
            totalBeats = score.measureStart(score.measures),
            onKey = if (connected) null else touch,
            caption = com.id.mimikopi.core.score.KeySignature.majorName(score.keyFifths),
        )
    }
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
            // The section shows only once one is chosen (tap bars on the music); the whole piece needs no label.
            if (ui.section != (0 until score.measures)) {
                Text(
                    if (ui.section.first == ui.section.last) stringResource(R.string.reader_section_one, ui.section.first + 1)
                    else stringResource(R.string.reader_section, ui.section.first + 1, ui.section.last + 1),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = vm::wholePiece) { Text(stringResource(R.string.reader_whole_piece)) }
            }
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

@Composable
private fun runStatus(ui: ReaderUi): String = when {
    ui.phase == Phase.COUNT_IN -> stringResource(R.string.reader_count_in, ui.countIn ?: 0)
    ui.phase == Phase.LISTENING -> stringResource(R.string.reader_listening, ui.bpm)
    ui.options.mode == PlayMode.WAIT -> stringResource(R.string.reader_playing_wait)
    else -> stringResource(R.string.reader_playing_tempo, ui.bpm)
}

/** Stop remains visible even on a narrow screen or with large system text. */
@Composable
private fun RunControls(ui: ReaderUi, vm: ReaderViewModel) {
    com.id.mimikopi.ui.reading.ReadingRunBar(runStatus(ui), vm::stop)
}

@Composable
private fun ResultPanel(
    r: ResultUi, ui: ReaderUi, vm: ReaderViewModel, onBack: () -> Unit, onOpenMidi: () -> Unit,
    modifier: Modifier = Modifier, scoreReview: @Composable () -> Unit,
) {
    var details by remember(r) { mutableStateOf(false) }
    val next = r.nextStep
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.l, vertical = Tokens.Space.m),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ผลรอบนี้", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
        Text(r.headline, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        next?.let { Text(it.text, fontSize = 19.sp, lineHeight = 28.sp, color = Tokens.Palette.Ink) }
        r.progressLine?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Tokens.Palette.InkMuted) }
        val action = next?.action
        com.id.mimikopi.ui.components.PrimaryButton(
            text = when {
                vm.benchDone -> stringResource(R.string.reader_done)
                action != null -> actionLabel(action, ui.options.mode == PlayMode.TEMPO)
                else -> stringResource(if (vm.nextLabelIsNew()) R.string.reader_new_piece else R.string.reader_again)
            },
            onClick = {
                when {
                    vm.benchDone -> onBack()
                    action != null -> vm.applyAction(action)
                    else -> vm.start()
                }
            },
        )
        if (!vm.benchDone) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (action != null) TextButton(onClick = vm::start) { Text("เล่นแบบเดิมอีกครั้ง") }
                if (!vm.fixed && ui.section != (0 until (vm.score?.measures ?: 0))) {
                    TextButton(onClick = { vm.wholePiece(); vm.start() }) { Text("ลองเล่นทั้งเพลง") }
                }
                TextButton(onClick = vm::backToSetup) { Text(stringResource(R.string.reader_back_to_setup)) }
                TextButton(onClick = onBack) { Text("จบการฝึก") }
            }
        }
        TextButton(onClick = { details = !details }) { Text(if (details) "ซ่อนรายละเอียด" else "ดูตัวเลขและคำแนะนำทั้งหมด") }
        if (details) {
            if (r.details.isNotEmpty()) Text(r.details, fontSize = 15.sp, lineHeight = 23.sp, color = Tokens.Palette.InkMuted)
            r.coach.filter { it != next }.forEach {
                Text(it.text, fontSize = 15.sp, lineHeight = 23.sp, color = Tokens.Palette.InkMuted)
            }
            scoreReview()
        }
    }
}

@Composable
private fun actionLabel(a: ResultAction, timed: Boolean): String = when (a) {
    is ResultAction.Loop -> "ฝึก" + (if (a.from == a.to) stringResource(R.string.reader_section_one, a.from + 1) else stringResource(R.string.reader_section, a.from + 1, a.to + 1)) + if (timed) " · ${a.bpm.coerceAtLeast(30)} bpm" else ""
    is ResultAction.Hands -> "ฝึก" + stringResource(if (a.choice == HandChoice.LEFT) R.string.reader_hand_left else R.string.reader_hand_right) + if (timed) " · ${a.bpm.coerceAtLeast(30)} bpm" else ""
    is ResultAction.Tempo -> "ฝึกที่ " + stringResource(R.string.reader_bpm, a.bpm.coerceAtLeast(30))
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
                if (!fixed) {
                    TextButton(onClick = { vm.wholePiece(); onDismiss() }) { Text("ฝึกทั้งเพลง") }
                    TextButton(onClick = { vm.setFalling(!o.falling); onDismiss() }) { Text(if (o.falling) "อ่านโน้ตบนบรรทัด" else "แสดงโน้ตไหล") }
                }
                OptionRow(R.string.reader_opt_other_hand, R.string.reader_opt_other_hand_body, o.playOtherHand, vm::setPlayOtherHand)
                if (!fixed) OptionRow(R.string.reader_opt_metronome, R.string.reader_opt_metronome_body, o.metronome, vm::setMetronome)
                if (!fixed) OptionRow(R.string.reader_opt_ramp, R.string.reader_opt_ramp_body, o.useRamp, vm::setUseRamp)
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
