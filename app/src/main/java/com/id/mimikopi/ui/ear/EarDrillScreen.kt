package com.id.mimikopi.ui.ear

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.KeyHint
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarDrillScreen(onBack: () -> Unit, vm: EarDrillViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    com.id.mimikopi.ui.components.AnswerHaptics((ui as? EarUi.Asking)?.correct)
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    val s0 = ui
    if (s0 is EarUi.Asking) {
        AskingView(s0, vm, onBack)
        return
    }
    Scaffold(
        modifier = Modifier.paper(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ear_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val s = ui) {
                EarUi.Loading, is EarUi.Asking -> Unit
                is EarUi.Ready -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Tokens.Space.m)) {
                    PageCard(title = s.title) {
                        Text(stringResource(R.string.ear_ready, s.count, s.levels.joinToString { it.name }), style = MaterialTheme.typography.bodyLarge)
                        if (s.packMissing) Text(stringResource(R.string.reader_pack_missing), color = Tokens.Palette.InkMuted, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = vm::start, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.m).heightIn(min = 64.dp)) {
                            Text(stringResource(R.string.drill_start), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                is EarUi.Done -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Tokens.Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
                    PageCard(title = stringResource(R.string.drill_result), modifier = Modifier.widthIn(max = 720.dp)) {
                        Text(s.headline, style = MaterialTheme.typography.titleMedium)
                        s.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.xs)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.padding(top = Tokens.Space.m)) {
                            OutlinedButton(onClick = vm::start, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.drill_again)) }
                            Button(onClick = onBack, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.drill_done)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One question, laid out like the design: a thin progress line, the task in the serif, a large round "listen"
 * button, then the answer: note slots for a melody (the given first note filled, played notes outlined, the rest
 * dashed) or the choice buttons.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AskingView(s: EarUi.Asking, vm: EarDrillViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().paper().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
            com.id.mimikopi.ui.components.MeterBar(s.index.toFloat() / s.total, Modifier.weight(1f))
            Text("%02d/%02d".format(s.index, s.total), fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(top = 28.dp))
            s.rhythmState?.let { Text(it, fontFamily = Tokens.Fonts.mono, fontSize = 40.sp, modifier = Modifier.padding(top = 24.dp)) }
            Spacer(Modifier.height(32.dp))
            val listenLabel = stringResource(if (s.correct == null) R.string.ear_listen_again else R.string.ear_listen_answer)
            if (s.q is EarQuestion.Rhythm) {
                if (s.correct == null && s.rhythmState == null) BigCircle(stringResource(R.string.ear_start_rhythm), vm::startRhythm)
            } else {
                BigCircle(listenLabel, vm::play)
            }
            Spacer(Modifier.height(32.dp))
            val melody = (s.q as? EarQuestion.Melody)?.notes
            if (melody != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    melody.indices.forEach { i ->
                        val note = if (i == 0) melody.first() else s.entered.getOrNull(i - 1)
                        NoteSlot(note, filled = i == 0)
                    }
                }
                if (s.correct == false) {
                    Text(stringResource(R.string.ear_answer_row), fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { melody.forEach { NoteSlot(it, filled = true) } }
                }
                if (s.entered.isNotEmpty() && s.correct == null) TextButton(onClick = vm::retryEntry, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.ear_clear)) }
            } else if (s.correct == null && s.choices.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.choices.forEach { c ->
                        OutlinedButton(onClick = { vm.answer(c.value) }, shape = RoundedCornerShape(26.dp), modifier = Modifier.heightIn(min = 52.dp)) {
                            Text(c.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            s.correct?.let { ok ->
                // Right or wrong without colour: the word, and a wrong answer is shown inverted.
                Text(
                    stringResource(if (ok) R.string.drill_correct else R.string.drill_wrong),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (ok) Tokens.Palette.Ink else Tokens.Palette.OnInk,
                    modifier = Modifier.padding(top = 24.dp)
                        .then(if (ok) Modifier else Modifier.background(Tokens.Palette.Ink, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 2.dp)),
                )
            }
            if (melody == null) s.correction?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp)) }
        }
        if (s.correct != null) {
            Button(
                onClick = vm::next, shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 64.dp),
            ) { Text(stringResource(R.string.ear_next), style = MaterialTheme.typography.titleMedium) }
        }
        if ((s.q as? EarQuestion.Melody) == null) KeyHint(marked = s.marked, nameStyle = vm.nameStyle) else KeyHint(marked = emptySet(), nameStyle = vm.nameStyle)
    }
}

@Composable
private fun BigCircle(label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(148.dp).clip(CircleShape).border(1.5.dp, Tokens.Palette.Ink, CircleShape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = Tokens.Palette.Ink, modifier = Modifier.size(34.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp), maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun NoteSlot(note: Int?, filled: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(60.dp, 78.dp).clip(shape).then(
            when {
                filled -> Modifier.background(Tokens.Palette.Ink)
                note != null -> Modifier.border(1.5.dp, Tokens.Palette.Ink, shape)
                else -> Modifier.border(1.dp, Tokens.Palette.Faint, shape)
            },
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (note != null) {
            val p = com.id.mimikopi.core.music.SpelledPitch.fromMidiSharp(note)
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text(p.letter.name + com.id.mimikopi.core.music.SpelledPitch.accidentalSymbol(p.accidental), fontFamily = Tokens.Fonts.mono, fontWeight = FontWeight.Medium,
                    fontSize = 26.sp, color = if (filled) Tokens.Palette.OnInk else Tokens.Palette.Ink)
                Text("${p.octave}", fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = if (filled) Tokens.Palette.OnInk else Tokens.Palette.Ink,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

private fun dispatch(vm: EarDrillViewModel, note: Int, velocity: Int) {
    val hub = vm.getApplication<MimikopiApp>().inputHub
    hub.dispatch(if (velocity > 0) InputEvent.NoteOn(note, velocity, System.nanoTime(), InputSource.TOUCH) else InputEvent.NoteOff(note, System.nanoTime(), InputSource.TOUCH))
}
