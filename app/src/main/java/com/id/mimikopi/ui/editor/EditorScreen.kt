package com.id.mimikopi.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.ui.components.PianoKeyboard
import com.id.mimikopi.ui.reader.ScoreView
import com.id.mimikopi.ui.theme.Tokens

/** Sheet editor, landscape-first: top bar, the score strip with the selection, two tool rows, the keyboard. */
@Composable
fun EditorScreen(onBack: () -> Unit, onPlayAlong: (String) -> Unit, vm: EditorViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    var chordDialog by remember { mutableStateOf(false) }
    var metaDialog by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
            Text(ui.timeline?.meta?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::undo, enabled = ui.canUndo) { Text(stringResource(R.string.editor_undo)) }
            TextButton(onClick = vm::redo, enabled = ui.canRedo) { Text(stringResource(R.string.editor_redo)) }
            TextButton(onClick = { if (ui.playing) vm.stopPlayback() else vm.play() }) { Text(stringResource(if (ui.playing) R.string.reader_stop else R.string.editor_play)) }
            TextButton(onClick = { metaDialog = true }) { Text(stringResource(R.string.editor_settings)) }
            ui.timeline?.meta?.id?.let { id -> TextButton(onClick = { onPlayAlong(id) }) { Text(stringResource(R.string.editor_play_along)) } }
        }
        ui.message?.let { m ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text(m, color = Tokens.Palette.Error, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::dismissMessage) { Text(stringResource(R.string.reader_done)) }
            }
        }
        val score = ui.score
        val layout = ui.layout
        if (score != null && layout != null) {
            ScoreView(
                score = score, layout = layout, cursorBeat = { 0.0 }, following = false,
                results = emptyMap(), wrongMarks = emptyList(), loop = null, hotMeasures = emptyList(),
                showNames = false, nameStyle = NoteNameStyle.BOTH, onMeasureTap = {},
                modifier = Modifier.weight(1f).fillMaxWidth(),
                editorCursor = ui.cursor / 4.0 to ui.hand,
                onScoreTap = vm::onScoreTap,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        // Row 1: what to write.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs), verticalAlignment = Alignment.CenterVertically) {
            NoteLength.entries.forEach { l -> FilterChip(selected = ui.length == l, onClick = { vm.setLength(l) }, label = { Text(l.label) }) }
            FilterChip(selected = ui.dotted, onClick = vm::toggleDot, label = { Text(stringResource(R.string.editor_dot)) })
            TextButton(onClick = vm::rest) { Text(stringResource(R.string.editor_rest)) }
            TextButton(onClick = vm::toggleTie) { Text(stringResource(R.string.editor_tie)) }
            FilterChip(selected = ui.chordMode, onClick = vm::toggleChordMode, label = { Text(stringResource(R.string.editor_chord_mode)) })
            Spacer(Modifier.width(Tokens.Space.s))
            listOf(1 to "♯", -1 to "♭", 0 to "♮").forEach { (a, label) -> FilterChip(selected = ui.accidental == a, onClick = { vm.setAccidental(a) }, label = { Text(label) }) }
            TextButton(onClick = { vm.transpose(1) }) { Text(stringResource(R.string.editor_up)) }
            TextButton(onClick = { vm.transpose(-1) }) { Text(stringResource(R.string.editor_down)) }
        }
        // Row 2: where and how.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::previous) { Text("◀") }
            TextButton(onClick = vm::next) { Text("▶") }
            FilterChip(selected = ui.hand == Hand.RIGHT, onClick = { vm.setHand(Hand.RIGHT) }, label = { Text(stringResource(R.string.reader_hand_right)) })
            FilterChip(selected = ui.hand == Hand.LEFT, onClick = { vm.setHand(Hand.LEFT) }, label = { Text(stringResource(R.string.reader_hand_left)) })
            TextButton(onClick = vm::delete) { Text(stringResource(R.string.editor_delete)) }
            TextButton(onClick = { chordDialog = true }) { Text(stringResource(R.string.editor_chord_symbol)) }
            TextButton(onClick = vm::insertMeasure) { Text(stringResource(R.string.editor_add_measure)) }
            TextButton(onClick = vm::deleteMeasure) { Text(stringResource(R.string.editor_delete_measure)) }
            TextButton(onClick = vm::toggleRepeatStart) { Text(stringResource(R.string.editor_repeat_start)) }
            TextButton(onClick = vm::toggleRepeatEnd) { Text(stringResource(R.string.editor_repeat_end)) }
            FilterChip(selected = ui.stepInput, onClick = vm::toggleStepInput, label = { Text(stringResource(R.string.editor_step_input)) })
            FilterChip(selected = ui.touchEntry, onClick = vm::toggleTouchEntry, label = { Text(stringResource(R.string.editor_touch_entry)) })
        }
        PianoKeyboard(
            lowNote = 36, highNote = 84, pressed = pressed, showNames = false, nameStyle = NoteNameStyle.BOTH,
            onNoteOn = { n, depth -> dispatch(vm, n, (40 + depth * 80).toInt().coerceIn(1, 127)) },
            onNoteOff = { n -> dispatch(vm, n, 0) },
            modifier = Modifier.fillMaxWidth().height(110.dp).padding(horizontal = Tokens.Space.s, vertical = Tokens.Space.xs),
        )
    }

    if (chordDialog) {
        var text by remember { mutableStateOf(vm.chordSymbolAtCursor() ?: "") }
        AlertDialog(
            onDismissRequest = { chordDialog = false },
            title = { Text(stringResource(R.string.editor_chord_symbol)) },
            text = {
                Column {
                    Text(stringResource(R.string.editor_chord_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = text, onValueChange = { text = it.take(12) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.setChordSymbol(text); chordDialog = false }) { Text(stringResource(R.string.reader_done)) } },
            dismissButton = { TextButton(onClick = { vm.setChordSymbol(null); chordDialog = false }) { Text(stringResource(R.string.editor_remove)) } },
        )
    }
    if (metaDialog) vm.meta()?.let { m -> MetaDialog(m, onDismiss = { metaDialog = false }, onSave = { vm.updateMeta(it); metaDialog = false }) }
}

/** Title, composer, key, time signature, tempo and clefs. Also used when creating a score. */
@Composable
fun MetaDialog(meta: ScoreMeta, onDismiss: () -> Unit, onSave: (ScoreMeta) -> Unit) {
    var title by remember { mutableStateOf(meta.title) }
    var composer by remember { mutableStateOf(meta.composer) }
    var key by remember { mutableStateOf(meta.key) }
    var time by remember { mutableStateOf(meta.time) }
    var tempo by remember { mutableStateOf(meta.tempo.toString()) }
    var lhClef by remember { mutableStateOf(meta.lhClef) }
    var rhClef by remember { mutableStateOf(meta.rhClef) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text(stringResource(R.string.editor_title)) }, singleLine = true)
                OutlinedTextField(value = composer, onValueChange = { composer = it.take(80) }, label = { Text(stringResource(R.string.editor_composer)) }, singleLine = true)
                Text(stringResource(R.string.editor_key), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                    KEYS.forEach { (f, name) -> FilterChip(selected = key == f, onClick = { key = f }, label = { Text(name) }) }
                }
                Text(stringResource(R.string.editor_time), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                    listOf(listOf(4, 4), listOf(3, 4), listOf(2, 4), listOf(6, 8)).forEach { ts ->
                        FilterChip(selected = time == ts, onClick = { time = ts }, label = { Text("${ts[0]}/${ts[1]}") })
                    }
                }
                OutlinedTextField(value = tempo, onValueChange = { tempo = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.editor_tempo)) }, singleLine = true)
                Text(stringResource(R.string.editor_clefs), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                    FilterChip(selected = rhClef == "treble", onClick = { rhClef = if (rhClef == "treble") "bass" else "treble" },
                        label = { Text(stringResource(R.string.editor_rh_clef, if (rhClef == "treble") "กุญแจซอล" else "กุญแจฟา")) })
                    FilterChip(selected = lhClef == "bass", onClick = { lhClef = if (lhClef == "bass") "treble" else "bass" },
                        label = { Text(stringResource(R.string.editor_lh_clef, if (lhClef == "bass") "กุญแจฟา" else "กุญแจซอล")) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(meta.copy(title = title.ifBlank { meta.title }, composer = composer, key = key, time = time,
                    tempo = (tempo.toIntOrNull() ?: meta.tempo).coerceIn(30, 240), rhClef = rhClef, lhClef = lhClef))
            }) { Text(stringResource(R.string.reader_done)) }
        },
    )
}

private val KEYS = listOf(0 to "C", 1 to "G", 2 to "D", 3 to "A", 4 to "E", -1 to "F", -2 to "B♭", -3 to "E♭", -4 to "A♭")

private fun dispatch(vm: EditorViewModel, note: Int, velocity: Int) {
    val hub = vm.getApplication<MimikopiApp>().inputHub
    hub.dispatch(if (velocity > 0) InputEvent.NoteOn(note, velocity, System.nanoTime(), InputSource.TOUCH) else InputEvent.NoteOff(note, System.nanoTime(), InputSource.TOUCH))
}
