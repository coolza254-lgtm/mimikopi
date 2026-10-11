package com.id.mimikopi.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.delay

/** Today: pick 15/30/45/60 minutes, follow the blocks (section 3), always ending with free play. */
@Composable
fun TodayScreen(
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenDrill: (String, Int) -> Unit,
    onOpenReview: () -> Unit,
    onOpenPiano: () -> Unit,
    onOpenPieces: () -> Unit,
    vm: TodayViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active != null) { now = System.currentTimeMillis(); delay(1000) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    ScreenScaffold(title = stringResource(R.string.today_title), onBack = onBack) {
        PageCard {
            Text(stringResource(R.string.today_time), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                listOf(15, 30, 45, 60).forEach { m -> FilterChip(selected = ui.minutes == m, onClick = { vm.setMinutes(m) }, label = { Text(stringResource(R.string.today_minutes, m)) }) }
            }
            val a = active
            if (a == null) {
                Button(onClick = vm::startSession, modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s).heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.today_start))
                }
            } else {
                val elapsed = (now - a.startedAtMillis) / 1000
                Text(stringResource(R.string.today_timer, elapsed / 60, elapsed % 60, a.plannedMinutes), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = Tokens.Space.s))
                OutlinedButton(onClick = vm::finishSession, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.today_finish))
                }
            }
            if (ui.lightDay) Text(stringResource(R.string.today_light), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ui.plan.forEach { block ->
            PageCard(title = blockTitle(block.kind, block.minutes)) {
                when (block.kind) {
                    BlockKind.REVIEW -> {
                        Text(stringResource(R.string.block_review_body, ui.dueCount), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onOpenReview, enabled = ui.dueCount > 0, modifier = Modifier.padding(top = Tokens.Space.xs).heightIn(min = Tokens.minTouch)) {
                            Text(stringResource(R.string.block_start))
                        }
                    }
                    BlockKind.RHYTHM -> ui.rhythmLesson?.let { l ->
                        val idx = l.drills.indexOfFirst { it.type == ExerciseType.E_PULSE }
                        Text(stringResource(R.string.block_rhythm_body, l.id, l.title), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onOpenDrill(l.id, idx) }, modifier = Modifier.padding(top = Tokens.Space.xs).heightIn(min = Tokens.minTouch)) {
                            Text(stringResource(R.string.block_start))
                        }
                    }
                    BlockKind.LESSON -> ui.currentLesson?.let { l ->
                        Text(stringResource(R.string.block_lesson_body, l.id, l.title, l.goal), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onOpenLesson(l.id) }, modifier = Modifier.padding(top = Tokens.Space.xs).heightIn(min = Tokens.minTouch)) {
                            Text(stringResource(R.string.block_open_lesson))
                        }
                        OutlinedButton(onClick = onOpenPieces, modifier = Modifier.padding(top = Tokens.Space.xs).heightIn(min = Tokens.minTouch)) {
                            Text(stringResource(R.string.block_open_pieces))
                        }
                    }
                    BlockKind.FREE_PLAY -> {
                        Text(stringResource(R.string.block_free_body), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onOpenPiano, modifier = Modifier.padding(top = Tokens.Space.xs).heightIn(min = Tokens.minTouch)) {
                            Text(stringResource(R.string.block_open_piano))
                        }
                    }
                    else -> Unit
                }
            }
        }

        ui.stats?.let { s ->
            PageCard(title = stringResource(R.string.today_stats)) {
                Text(stringResource(R.string.today_stats_body, s.minutesToday, s.minutesThisWeek, s.minutesLastWeek, s.daysPracticed, s.totalMinutes),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        PageCard(title = stringResource(R.string.today_lessons)) {
            ui.lessons.forEach { (l, st) ->
                TextButton(onClick = { onOpenLesson(l.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${l.id} ${l.title} · ${stringResource(when (st) {
                        LessonStatus.PASSED -> R.string.lesson_status_passed
                        LessonStatus.LOCKED -> R.string.lesson_status_locked
                        LessonStatus.IN_PROGRESS -> R.string.lesson_status_in_progress
                        LessonStatus.AVAILABLE -> R.string.lesson_status_available
                    })}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                }
            }
            Text(stringResource(R.string.today_estimate_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    summary?.let { s ->
        AlertDialog(
            onDismissRequest = vm::dismissSummary,
            title = { Text(stringResource(R.string.summary_title)) },
            text = {
                Text(buildString {
                    append(stringResource(R.string.summary_minutes, s.minutes))
                    s.accuracyToday?.let { t ->
                        append("\n")
                        append(stringResource(R.string.summary_accuracy, (t * 100).toInt()))
                        s.accuracyBefore?.let { b -> append(stringResource(R.string.summary_accuracy_before, (b * 100).toInt())) }
                    }
                    append("\n")
                    append(stringResource(R.string.summary_due, s.dueTomorrow))
                    append("\n")
                    append(stringResource(R.string.summary_question))
                })
            },
            confirmButton = { TextButton(onClick = vm::dismissSummary) { Text(stringResource(R.string.drill_done)) } },
        )
    }
}

@Composable
private fun blockTitle(kind: BlockKind, minutes: Int): String = stringResource(when (kind) {
    BlockKind.WARMUP -> R.string.block_warmup
    BlockKind.REVIEW -> R.string.block_review
    BlockKind.RHYTHM -> R.string.block_rhythm
    BlockKind.LESSON -> R.string.block_lesson
    BlockKind.NEW_READING -> R.string.block_new_reading
    BlockKind.EAR -> R.string.block_ear
    BlockKind.FREE_PLAY -> R.string.block_free
}) + " · " + stringResource(R.string.today_minutes, minutes)
