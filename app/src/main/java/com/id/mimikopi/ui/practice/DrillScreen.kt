package com.id.mimikopi.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.exercise.KeyFindItem
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.KeyHint
import com.id.mimikopi.ui.components.StaffView
import com.id.mimikopi.ui.components.StaffSequenceView
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillScreen(onBack: () -> Unit, vm: DrillViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val showNames by vm.showNames.collectAsStateWithLifecycle()
    val pressed by vm.pressed.collectAsStateWithLifecycle()

    val answer = when (val s = ui) {
        is DrillUi.Question -> s.feedback
        is DrillUi.ChordAsk -> s.feedback
        is DrillUi.Interval -> s.feedback
        else -> null
    }
    com.id.mimikopi.ui.components.AnswerHaptics(answer?.let { it == Feedback.CORRECT })
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    Scaffold(
        modifier = Modifier.paper(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drill_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val s = ui) {
                DrillUi.Loading -> Unit
                is DrillUi.Ready -> Centered {
                    PageCard(title = s.title) {
                        Text(
                            stringResource(when (s.type) {
                                ExerciseType.E_KEYFIND -> R.string.drill_ready_keyfind
                                ExerciseType.E_NOTEID -> R.string.drill_ready_noteid
                                ExerciseType.E_PULSE -> R.string.drill_ready_pulse
                                ExerciseType.E_INTERVAL_READ -> R.string.drill_ready_interval
                                ExerciseType.E_CHORD_BUILD -> R.string.drill_ready_chord_build
                                ExerciseType.E_CHORD_CHANGE -> R.string.drill_ready_chord_change
                                else -> R.string.drill_ready_noteid
                            }, s.items),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = Tokens.Space.xs),
                        )
                        Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.m).heightIn(min = Tokens.minTouch)) {
                            Text(stringResource(R.string.drill_start))
                        }
                    }
                }
                is DrillUi.Question -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.m), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.index}/${s.total}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilterChip(selected = showNames, onClick = vm::toggleNames, label = { Text(stringResource(R.string.piano_show_names)) })
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).padding(Tokens.Space.m), contentAlignment = Alignment.Center) {
                        when (val p = s.prompt) {
                            is Prompt.Key -> Text(keyPrompt(p.item), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp), textAlign = TextAlign.Center)
                            is Prompt.Note -> StaffView(p.item.clef, p.item.pitch, Modifier.widthIn(max = 420.dp).fillMaxWidth().height(170.dp))
                        }
                        s.feedback?.let { f ->
                            Text(
                                stringResource(if (f == Feedback.CORRECT) R.string.drill_correct else R.string.drill_wrong),
                                color = if (f == Feedback.CORRECT) Tokens.Palette.GoodInk else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                    KeyHint(marked = if (s.feedback == Feedback.WRONG) s.prompt.answerKeys(vm.keyRange) else emptySet(), nameStyle = vm.nameStyle)
                }
                is DrillUi.Interval -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.m), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.index}/${s.total}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilterChip(selected = showNames, onClick = vm::toggleNames, label = { Text(stringResource(R.string.piano_show_names)) })
                    }
                    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Tokens.Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
                        StaffSequenceView(s.clef, s.notes, s.pair?.let { setOf(it, it + 1) } ?: emptySet(), if (s.pair == null) s.played else 0,
                            Modifier.widthIn(max = 520.dp).fillMaxWidth().height(150.dp))
                        Text(
                            if (s.pair != null) stringResource(R.string.drill_interval_ask, s.pair + 1, s.pair + 2) else stringResource(R.string.drill_interval_play),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        s.correction?.let { Text(stringResource(R.string.drill_interval_correction, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
                        if (s.feedback == Feedback.CORRECT) Text(stringResource(R.string.drill_correct), style = MaterialTheme.typography.bodyLarge)
                        if (s.feedback == Feedback.WRONG && s.pair == null) Text(stringResource(R.string.drill_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        if (s.pair != null) IntervalAnswers(s, vm)
                    }
                    KeyHint(marked = emptySet(), nameStyle = vm.nameStyle)
                }
                is DrillUi.ChordAsk -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.m), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (s.secondsLeft != null) stringResource(R.string.drill_chord_clock, s.secondsLeft, s.count ?: 0) else "${s.index}/${s.total}",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        )
                        FilterChip(selected = showNames, onClick = vm::toggleNames, label = { Text(stringResource(R.string.piano_show_names)) })
                    }
                    Column(Modifier.fillMaxWidth().weight(1f).padding(Tokens.Space.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        // The asked chord in a notebook card: the symbol or numeral big, the key underneath.
                        val parts = s.label.split(" ในคีย์ ", limit = 2)
                        Column(
                            Modifier
                                .background(Tokens.Palette.MustardSoft, RoundedCornerShape(28.dp))
                                .padding(bottom = 6.dp)
                                .background(Tokens.Palette.Card, RoundedCornerShape(28.dp))
                                .border(Tokens.border, Tokens.Palette.Line, RoundedCornerShape(28.dp))
                                .padding(horizontal = 36.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(parts[0], fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Black, fontSize = 56.sp, color = Tokens.Palette.Ink, textAlign = TextAlign.Center)
                            if (parts.size > 1) Text(stringResource(R.string.drill_chord_in_key, parts[1]), style = MaterialTheme.typography.titleMedium)
                        }
                        s.next?.let { Text(stringResource(R.string.drill_chord_next, it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (s.secondsLeft == null) Text(stringResource(R.string.drill_chord_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Tokens.Space.s))
                        s.feedback?.let { f ->
                            Text(
                                stringResource(if (f == Feedback.CORRECT) R.string.drill_correct else R.string.drill_chord_wrong),
                                color = if (f == Feedback.CORRECT) Tokens.Palette.GoodInk else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = Tokens.Space.m),
                            )
                        }
                    }
                    KeyHint(marked = s.answer, nameStyle = vm.nameStyle)
                }
                is DrillUi.Pulse -> Column(Modifier.fillMaxWidth().weight(1f).padding(Tokens.Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.bpm} BPM", style = MaterialTheme.typography.titleLarge)
                    Text(
                        s.countIn?.let { stringResource(R.string.drill_countin, it) } ?: stringResource(R.string.drill_beat, s.beat, s.beats),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(vertical = Tokens.Space.m),
                    )
                    Text(stringResource(R.string.drill_taps, s.taps), style = MaterialTheme.typography.bodyLarge)
                    TapPad(vm, Modifier.fillMaxWidth().weight(1f).padding(top = Tokens.Space.m))
                }
                is DrillUi.Result -> Centered {
                    PageCard(title = stringResource(R.string.drill_result)) {
                        Box(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s), contentAlignment = Alignment.Center) { com.id.mimikopi.ui.components.ResultBadge(s.passCriterionMet) }
                        Text(s.summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Tokens.Space.xs))
                        s.passCriterionMet?.let {
                            Text(
                                stringResource(if (it) R.string.drill_met else R.string.drill_not_met),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (it) Tokens.Palette.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Tokens.Space.s),
                            )
                        }
                        s.coach.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.s)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.padding(top = Tokens.Space.m)) {
                            OutlinedButton(onClick = vm::again, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.drill_again)) }
                            Button(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.drill_done)) }
                        }
                    }
                }
            }
        }
    }
}

/** Answer buttons: repeat / step / skip with direction, or interval numbers with direction. */
@Composable
private fun IntervalAnswers(s: DrillUi.Interval, vm: DrillViewModel) {
    val up = com.id.mimikopi.core.music.Direction.UP
    val down = com.id.mimikopi.core.music.Direction.DOWN
    val step = com.id.mimikopi.core.music.MotionKind.STEP
    val skip = com.id.mimikopi.core.music.MotionKind.SKIP
    fun m(kind: com.id.mimikopi.core.music.MotionKind, d: com.id.mimikopi.core.music.Direction, steps: Int) = com.id.mimikopi.core.music.Motion(kind, d, steps)
    val rows: List<List<Pair<String, com.id.mimikopi.core.music.Motion>>> = if (s.answerMode == "INTERVAL") {
        listOf(up, down).map { d -> s.numbers.map { n -> "${n} ${if (d == up) "ขึ้น" else "ลง"}" to m(if (n == 2) step else skip, d, n - 1) } }
    } else {
        listOf(
            listOf("ขั้นขึ้น" to m(step, up, 1), "ข้ามขึ้น" to m(skip, up, 2)),
            listOf("ขั้นลง" to m(step, down, 1), "ข้ามลง" to m(skip, down, 2)),
        )
    }
    val repeat = "ซ้ำ" to com.id.mimikopi.core.music.Motion(com.id.mimikopi.core.music.MotionKind.REPEAT, com.id.mimikopi.core.music.Direction.SAME, 0)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.xs), modifier = Modifier.padding(top = Tokens.Space.s)) {
        for ((i, row) in rows.withIndex()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                if (i == 0 && s.withRepeat) OutlinedButton(onClick = { vm.answerInterval(repeat.second) }, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(repeat.first) }
                row.forEach { (label, motion) ->
                    OutlinedButton(onClick = { vm.answerInterval(motion) }, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Tokens.Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.widthIn(max = 720.dp)) { content() }
    }
}

/** Large pad: tapping it is an input like a key (used for the pulse drill). */
@Composable
private fun TapPad(vm: DrillViewModel, modifier: Modifier) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as MimikopiApp
    Box(
        modifier
            .background(Tokens.Palette.RoseSoft, RoundedCornerShape(Tokens.Radius.card))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    app.inputHub.dispatch(InputEvent.NoteOn(60, 90, System.nanoTime(), InputSource.TOUCH))
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.changes.none { it.pressed }) break
                    }
                    app.inputHub.dispatch(InputEvent.NoteOff(60, System.nanoTime(), InputSource.TOUCH))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.drill_tap_pad), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(Tokens.Space.m))
    }
}

private fun dispatchTouch(vm: DrillViewModel, note: Int, down: Boolean) {
    val hub = (vm.getApplication<MimikopiApp>()).inputHub
    hub.dispatch(if (down) InputEvent.NoteOn(note, 80, System.nanoTime(), InputSource.TOUCH) else InputEvent.NoteOff(note, System.nanoTime(), InputSource.TOUCH))
}

/** Thai prompt for a key-finding item; exact-octave items are described relative to Middle C. */
private fun keyPrompt(item: KeyFindItem): String {
    val name = "${item.letter.name} (${item.letter.thaiSolfege})"
    val t = item.target ?: return "หา $name"
    return when {
        t.midi == 60 -> "หา Middle C (โดกลาง)"
        t.midi in 48..59 -> "หา $name ที่อยู่ต่ำกว่า Middle C"
        t.midi in 61..71 -> "หา $name ที่อยู่สูงกว่า Middle C"
        t.midi in 72..83 -> "หา $name ในอ็อกเทฟที่สูงกว่า Middle C อีกหนึ่ง (${t})"
        else -> "หา $t"
    }
}
