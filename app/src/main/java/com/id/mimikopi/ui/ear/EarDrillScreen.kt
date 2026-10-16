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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarDrillScreen(onBack: () -> Unit, vm: EarDrillViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    com.id.mimikopi.ui.components.AnswerHaptics((ui as? EarUi.Asking)?.correct)
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

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
                EarUi.Loading -> Unit
                is EarUi.Ready -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Tokens.Space.m)) {
                    PageCard(title = s.title) {
                        Text(stringResource(R.string.ear_ready, s.count, s.levels.joinToString { it.name }), style = MaterialTheme.typography.bodyLarge)
                        if (s.packMissing) Text(stringResource(R.string.reader_pack_missing), color = Tokens.Palette.Error, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.m).heightIn(min = Tokens.minTouch)) {
                            Text(stringResource(R.string.drill_start))
                        }
                    }
                }
                is EarUi.Asking -> {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.m),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                        Text("${s.index}/${s.total} · ${s.q.level.name}", style = MaterialTheme.typography.titleMedium)
                        Text(s.prompt, style = MaterialTheme.typography.titleMedium)
                        s.rhythmState?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                            if (s.q is EarQuestion.Rhythm) {
                                if (s.correct == null && s.rhythmState == null) Button(onClick = vm::startRhythm, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.ear_start_rhythm)) }
                            } else {
                                OutlinedButton(onClick = vm::play, modifier = Modifier.heightIn(min = Tokens.minTouch)) {
                                    Text(stringResource(if (s.correct == null) R.string.ear_listen_again else R.string.ear_listen_answer))
                                }
                            }
                            if (s.entered.isNotEmpty() && s.correct == null) TextButton(onClick = vm::retryEntry) { Text(stringResource(R.string.ear_clear)) }
                        }
                        if (s.correct == null && s.choices.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                                s.choices.forEach { c -> OutlinedButton(onClick = { vm.answer(c.value) }, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(c.label) } }
                            }
                        }
                        if (s.entered.isNotEmpty()) Text(stringResource(R.string.ear_entered, s.entered.size), style = MaterialTheme.typography.bodyMedium)
                        s.correct?.let { ok ->
                            Text(stringResource(if (ok) R.string.drill_correct else R.string.drill_wrong),
                                color = if (ok) Tokens.Palette.Good else Tokens.Palette.Error, style = MaterialTheme.typography.titleLarge)
                        }
                        s.correction?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = if (s.correct == true) Tokens.Palette.Ink else Tokens.Palette.Error) }
                        if (s.correct != null) {
                            Text(stringResource(R.string.ear_play_it), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
                            Button(onClick = vm::next, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.ear_next)) }
                        }
                    }
                    KeyHint(marked = s.marked, nameStyle = vm.nameStyle)
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

private fun dispatch(vm: EarDrillViewModel, note: Int, velocity: Int) {
    val hub = vm.getApplication<MimikopiApp>().inputHub
    hub.dispatch(if (velocity > 0) InputEvent.NoteOn(note, velocity, System.nanoTime(), InputSource.TOUCH) else InputEvent.NoteOff(note, System.nanoTime(), InputSource.TOUCH))
}
