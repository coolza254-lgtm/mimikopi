package com.id.mimikopi.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.PianoKeyboard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.components.StaffView
import com.id.mimikopi.ui.theme.Tokens

/** A lesson laid out like learning-system.md section 10: explain → try with the app → drills → pass rule → tips. */
@Composable
fun LessonScreen(onBack: () -> Unit, onOpenDrill: (String, Int) -> Unit, vm: LessonViewModel = viewModel()) {
    val lesson = vm.lesson
    val record by vm.record.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val marked by vm.marked.collectAsStateWithLifecycle()
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    val namedLetter by vm.namedLetter.collectAsStateWithLifecycle()
    val keyboard by vm.keyboard.collectAsStateWithLifecycle()
    val countAnswer by vm.countAnswer.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    ScreenScaffold(title = lesson?.let { "${it.id} ${it.title}" } ?: "", onBack = onBack) {
        if (lesson == null) {
            Text(stringResource(R.string.lesson_missing))
            return@ScreenScaffold
        }
        PageCard {
            Text(stringResource(R.string.lesson_goal, lesson.goal), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.lesson_meta, lesson.minutes, lesson.tracks.joinToString()), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Tokens.Space.xs))
            Text(statusText(status, record?.passDays?.size ?: 0, lesson.pass.days, record?.helperDays?.size ?: 0),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.xs))
            if (status == LessonStatus.LOCKED) Text(stringResource(R.string.lesson_locked, lesson.prerequisites.joinToString()),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        PageCard(title = stringResource(R.string.lesson_explain)) {
            lesson.explain.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Tokens.Space.xs)) }
        }
        PageCard(title = stringResource(R.string.lesson_try)) {
            lesson.tryIt.forEachIndexed { i, step ->
                Text("${i + 1}. ${step.text}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Tokens.Space.s))
                val (kind, arg) = step.action?.split(":", limit = 2)?.let { it[0] to it.getOrNull(1) } ?: (null to null)
                when (kind) {
                    "highlight" -> OutlinedButton(onClick = { vm.runAction(step.action, 48..83) }, modifier = Modifier.heightIn(min = Tokens.minTouch)) {
                        Text(stringResource(R.string.lesson_show_keys, arg ?: ""))
                    }
                    "count" -> {
                        val letter = arg?.let { runCatching { Letter.valueOf(it) }.getOrNull() } ?: Letter.C
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                            KeyboardSize.entries.forEach { k -> FilterChip(selected = keyboard == k, onClick = { vm.setKeyboard(k) }, label = { Text(stringResource(R.string.lesson_keys, k.keys)) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                            (5..9).forEach { n -> OutlinedButton(onClick = { vm.answerCount(letter, n) }) { Text("$n") } }
                        }
                        countAnswer?.let { (actual, ok) ->
                            Text(stringResource(if (ok) R.string.lesson_count_ok else R.string.lesson_count_no, actual, keyboard.keys, letter.name),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    "show" -> {
                        val parts = arg?.split(":")
                        val pitch = parts?.getOrNull(0)?.let { runCatching { SpelledPitch.parse(it) }.getOrNull() }
                        val clef = parts?.getOrNull(1)?.let { runCatching { Clef.valueOf(it.uppercase()) }.getOrNull() }
                        if (pitch != null && clef != null) {
                            StaffView(clef, pitch, Modifier.widthIn(max = 360.dp).fillMaxWidth().height(130.dp))
                            OutlinedButton(onClick = { vm.runAction("play:$pitch", 48..83) }) { Text(stringResource(R.string.lesson_listen)) }
                        }
                    }
                }
            }
            PianoKeyboard(
                lowNote = 48, highNote = 83,
                pressed = pressed, showNames = false, nameStyle = NoteNameStyle.BOTH,
                onNoteOn = { n, _ -> vm.touch(n, true) }, onNoteOff = { n -> vm.touch(n, false) },
                marked = marked,
                namedNotes = namedLetter?.let { l -> pressed.filter { it % 12 == l.semitonesFromC }.toSet() } ?: emptySet(),
                modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = Tokens.Space.s),
            )
        }
        PageCard(title = stringResource(R.string.lesson_drills)) {
            lesson.drills.forEachIndexed { i, d ->
                val isPass = i in lesson.pass.requiredDrills
                Button(
                    onClick = { onOpenDrill(lesson.id, i) },
                    enabled = status != LessonStatus.LOCKED,
                    modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s).heightIn(min = Tokens.minTouch),
                ) { Text(if (isPass) "★ ${d.title}" else d.title) }
            }
            Text(stringResource(R.string.lesson_pass, lesson.passText), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.s))
        }
        PageCard(title = stringResource(R.string.lesson_tips)) {
            lesson.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.xs)) }
            lesson.caveats.forEach { Text("ⓘ $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Tokens.Space.xs)) }
        }
    }
}

@Composable
fun statusText(status: LessonStatus, passDays: Int, needDays: Int, helperDays: Int): String = when (status) {
    LessonStatus.PASSED -> stringResource(R.string.lesson_status_passed)
    LessonStatus.LOCKED -> stringResource(R.string.lesson_status_locked)
    else -> stringResource(R.string.lesson_status_progress, passDays, needDays) + if (helperDays > 0) stringResource(R.string.lesson_status_helper, helperDays) else ""
}
