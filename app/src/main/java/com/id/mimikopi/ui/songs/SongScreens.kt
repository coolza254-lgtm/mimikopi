package com.id.mimikopi.ui.songs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.songs.Song
import com.id.mimikopi.core.songs.SongAnalysis
import com.id.mimikopi.core.songs.SongSection
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens

private val KEY_CHOICES = (-6..6).toList()

@Composable
fun SongListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: SongListViewModel = viewModel()) {
    val ladder by vm.ladder.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.songs_title), onBack = onBack) {
        Text(stringResource(R.string.songs_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.songs_add)) }
        ladder.forEachIndexed { i, (song, a) ->
            PageCard(title = "${i + 1}. ${song.title}", onClick = { onOpen(song.id) }) {
                if (song.artist.isNotBlank()) Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.songs_difficulty, a.score), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.xs))
                Text(
                    if (a.missing.isEmpty()) stringResource(R.string.songs_ready)
                    else stringResource(R.string.songs_missing, a.missing.size, a.missing.map { it.lessonId }.distinct().joinToString()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.songs_pipeline, song.pipeline.count { it.done }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (a.unverified.isNotEmpty()) Text(stringResource(R.string.songs_unverified, a.unverified.joinToString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (adding) {
        var title by remember { mutableStateOf("") }
        var artist by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.songs_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                    OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text(stringResource(R.string.song_title)) }, singleLine = true)
                    OutlinedTextField(value = artist, onValueChange = { artist = it.take(80) }, label = { Text(stringResource(R.string.song_artist)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { adding = false; vm.add(title, artist, onOpen) }) { Text(stringResource(R.string.songs_create)) } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun SongScreen(
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onEditScore: (String) -> Unit,
    onPlayScore: (String) -> Unit,
    vm: SongViewModel = viewModel(),
) {
    val song by vm.song.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val scores by vm.userScores.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val s = song
    ScreenScaffold(title = s?.title ?: "", onBack = onBack) {
        if (s == null) return@ScreenScaffold
        message?.let { m ->
            PageCard { Text(m, style = MaterialTheme.typography.bodyMedium); TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.action_ok)) } }
        }
        analysis?.let { AnalysisCard(it, onOpenLesson) }
        FactsCard(s, vm)
        PageCard(title = stringResource(R.string.song_sections)) {
            Text(stringResource(R.string.song_sections_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.sections.forEachIndexed { i, sec -> SectionEditor(s, i, sec, vm) }
            OutlinedButton(onClick = vm::addSection, modifier = Modifier.padding(top = Tokens.Space.s).heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.song_add_section)) }
        }
        PageCard(title = stringResource(R.string.song_pipeline)) {
            s.pipeline.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = day.done, onCheckedChange = { vm.setPipelineDay(day.day, it) })
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.song_day, day.day, Song.PIPELINE.getOrElse(day.day - 1) { "" }), style = MaterialTheme.typography.bodyMedium)
                        day.date?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (vm.isComplete(s)) Text(stringResource(R.string.song_complete), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Tokens.Space.s))
        }
        ScoreCard(s, scores.values.map { it.id to it.title }, vm, onEditScore, onPlayScore)
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.song_delete)) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.song_delete)) },
            text = { Text(stringResource(R.string.song_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(R.string.song_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AnalysisCard(a: SongAnalysis, onOpenLesson: (String) -> Unit) {
    PageCard(title = stringResource(R.string.songs_difficulty, a.score)) {
        if (a.parts.isEmpty()) Text(stringResource(R.string.song_no_data), style = MaterialTheme.typography.bodyMedium)
        a.parts.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
        if (a.unverified.isNotEmpty()) Text(stringResource(R.string.songs_unverified, a.unverified.joinToString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(if (a.missing.isEmpty()) R.string.songs_ready else R.string.song_missing_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Tokens.Space.s))
        a.missing.forEach { r ->
            TextButton(onClick = { onOpenLesson(r.lessonId) }) { Text(stringResource(R.string.song_requirement, r.lessonId, r.reason)) }
        }
        if (a.romans.isNotEmpty()) Text(stringResource(R.string.song_romans, a.romans.joinToString(" ")), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Tokens.Space.xs))
    }
}

@Composable
private fun FactsCard(s: Song, vm: SongViewModel) {
    PageCard(title = stringResource(R.string.song_facts)) {
        OutlinedTextField(value = s.title, onValueChange = { v -> vm.edit { it.copy(title = v.take(80)) } }, label = { Text(stringResource(R.string.song_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s.artist, onValueChange = { v -> vm.edit { it.copy(artist = v.take(80)) } }, label = { Text(stringResource(R.string.song_artist)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s.year, onValueChange = { v -> vm.edit { it.copy(year = v.take(10)) } }, label = { Text(stringResource(R.string.song_year)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.song_key), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Tokens.Space.s))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
            FilterChip(selected = s.key == null, onClick = { vm.edit { it.copy(key = null) } }, label = { Text(stringResource(R.string.song_unknown)) })
            KEY_CHOICES.forEach { f -> FilterChip(selected = s.key == f, onClick = { vm.edit { it.copy(key = f) } }, label = { Text(Keys.name(f)) }) }
        }
        Verified(s.keyVerified) { v -> vm.edit { it.copy(keyVerified = v) } }
        OutlinedTextField(value = s.bpm?.toString() ?: "", onValueChange = { v -> vm.edit { it.copy(bpm = v.filter(Char::isDigit).take(3).toIntOrNull()) } },
            label = { Text(stringResource(R.string.song_bpm)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Verified(s.bpmVerified) { v -> vm.edit { it.copy(bpmVerified = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
            OutlinedTextField(value = s.rangeLow ?: "", onValueChange = { v -> vm.edit { it.copy(rangeLow = v.take(4).ifBlank { null }) } },
                label = { Text(stringResource(R.string.song_range_low)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = s.rangeHigh ?: "", onValueChange = { v -> vm.edit { it.copy(rangeHigh = v.take(4).ifBlank { null }) } },
                label = { Text(stringResource(R.string.song_range_high)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Verified(s.rangeVerified) { v -> vm.edit { it.copy(rangeVerified = v) } }
        OutlinedTextField(value = s.reportedChords.joinToString(", "), onValueChange = { v -> vm.edit { it.copy(reportedChords = v.split(',').map(String::trim).filter(String::isNotEmpty)) } },
            label = { Text(stringResource(R.string.song_reported)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s.notes, onValueChange = { v -> vm.edit { it.copy(notes = v.take(2000)) } }, label = { Text(stringResource(R.string.song_notes)) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Verified(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(if (checked) R.string.song_verified else R.string.song_unverified), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionEditor(s: Song, i: Int, sec: SongSection, vm: SongViewModel) {
    Column(Modifier.padding(top = Tokens.Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = sec.name, onValueChange = { vm.updateSection(i, sec.copy(name = it.take(30))) }, label = { Text(stringResource(R.string.song_section_name)) },
                singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.removeSection(i) }) { Text(stringResource(R.string.song_remove)) }
        }
        OutlinedTextField(value = sec.chords, onValueChange = { vm.updateSection(i, sec.copy(chords = it.take(400))) }, label = { Text(stringResource(R.string.song_section_chords)) },
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
            FilterChip(selected = sec.key == null, onClick = { vm.updateSection(i, sec.copy(key = null)) }, label = { Text(stringResource(R.string.song_section_same_key)) })
            KEY_CHOICES.forEach { f -> FilterChip(selected = sec.key == f, onClick = { vm.updateSection(i, sec.copy(key = f)) }, label = { Text(Keys.name(f)) }) }
        }
        vm.romans(s, sec).takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.song_romans, it), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ScoreCard(s: Song, scores: List<Pair<String, String>>, vm: SongViewModel, onEdit: (String) -> Unit, onPlay: (String) -> Unit) {
    var keys by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var transposing by remember { mutableStateOf(false) }
    PageCard(title = stringResource(R.string.song_score)) {
        val linked = s.scoreId?.let { id -> scores.firstOrNull { it.first == id } }
        Text(linked?.second ?: stringResource(R.string.song_no_score), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = Tokens.Space.xs), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
            if (linked == null) Button(onClick = { vm.createScore(onEdit) }) { Text(stringResource(R.string.song_new_score)) }
            else {
                Button(onClick = { onEdit(linked.first) }) { Text(stringResource(R.string.song_edit_score)) }
                OutlinedButton(onClick = { onPlay(linked.first) }) { Text(stringResource(R.string.song_play_score)) }
                OutlinedButton(onClick = { keys = vm.findKey() }) { Text(stringResource(R.string.song_find_key)) }
                OutlinedButton(onClick = { transposing = true }) { Text(stringResource(R.string.song_transpose)) }
            }
            OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.song_link_score)) }
        }
        keys?.let { list ->
            if (list.isEmpty()) Text(stringResource(R.string.song_key_too_few), style = MaterialTheme.typography.bodySmall)
            list.forEachIndexed { i, (f, name) ->
                TextButton(onClick = { vm.useKey(f); keys = null }) { Text(stringResource(R.string.song_key_candidate, i + 1, name)) }
            }
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.song_link_score)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (scores.isEmpty()) Text(stringResource(R.string.song_no_user_scores))
                    scores.forEach { (id, title) -> TextButton(onClick = { vm.linkScore(id); picking = false }) { Text(title) } }
                    if (s.scoreId != null) TextButton(onClick = { vm.linkScore(null); picking = false }) { Text(stringResource(R.string.song_unlink)) }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (transposing) {
        AlertDialog(
            onDismissRequest = { transposing = false },
            title = { Text(stringResource(R.string.song_transpose)) },
            text = {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                    KEY_CHOICES.forEach { f -> FilterChip(selected = false, onClick = { transposing = false; vm.transposedCopy(f) {} }, label = { Text(Keys.name(f)) }) }
                }
            },
            confirmButton = { TextButton(onClick = { transposing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
