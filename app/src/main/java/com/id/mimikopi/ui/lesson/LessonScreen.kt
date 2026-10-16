package com.id.mimikopi.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.id.mimikopi.ui.components.Pill
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
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
import com.id.mimikopi.ui.components.KeyHint
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lesson.id, fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Tokens.Palette.Paper,
                    modifier = Modifier.background(Tokens.Palette.Ink, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            val days = record?.passDays?.size ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(lesson.pass.days) { d ->
                        Box(Modifier.size(28.dp, 8.dp).background(if (d < days) Tokens.Palette.Ink else Tokens.Palette.Line, RoundedCornerShape(4.dp)))
                    }
                }
                Text(statusText(status, days, lesson.pass.days, record?.helperDays?.size ?: 0), style = MaterialTheme.typography.labelMedium,
                    color = if (status == LessonStatus.PASSED || days > 0) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
            if (status == LessonStatus.LOCKED) Text(stringResource(R.string.lesson_locked, lesson.prerequisites.joinToString()),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        PageCard {
            var more by remember { mutableStateOf(false) }
            lesson.explain.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Tokens.Space.xs)) }
            // Steps keep their topic's full explanation one tap away.
            // Everything else (the full explanation, tips, honest caveats) stays one tap away.
            val extra = lesson.more.filter { it !in lesson.explain }.map { "• $it" } + lesson.tips.map { "เคล็ดลับ: $it" } + lesson.caveats.map { "ⓘ $it" }
            if (extra.isNotEmpty()) {
                if (more) extra.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = Tokens.Space.xs)) }
                TextButton(onClick = { more = !more }) { Text(stringResource(if (more) R.string.lesson_less else R.string.lesson_more)) }
            }
        }
        if (lesson.drills.size == 1) {
            Button(
                onClick = { onOpenDrill(lesson.id, 0) },
                enabled = status != LessonStatus.LOCKED,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + stringResource(R.string.lesson_start_step), style = MaterialTheme.typography.labelLarge)
            }
            Text(stringResource(R.string.lesson_pass, lesson.passText), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        }
        if (lesson.tryIt.isNotEmpty()) PageCard(title = stringResource(R.string.lesson_try)) {
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
            KeyHint(marked = marked, nameStyle = NoteNameStyle.BOTH)
        }
        if (lesson.drills.size > 1) PageCard(title = stringResource(R.string.lesson_drills)) {
            lesson.drills.forEachIndexed { i, d ->
                val isPass = i in lesson.pass.requiredDrills
                val enabled = status != LessonStatus.LOCKED
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Tokens.Space.s)
                        .heightIn(min = Tokens.minTouch)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Tokens.Palette.Paper)
                        .border(Tokens.border, if (isPass) Tokens.Palette.Ink else Tokens.Palette.Line, RoundedCornerShape(16.dp))
                        .clickable(enabled = enabled) { onOpenDrill(lesson.id, i) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isPass) Pill(stringResource(R.string.lesson_counts))
                    Text(d.title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Tokens.Palette.Ink else Tokens.Palette.InkMuted, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Tokens.Palette.InkMuted, modifier = Modifier.size(18.dp))
                }
            }
            Text(stringResource(R.string.lesson_pass, lesson.passText), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.s))
        }
    }
}

@Composable
fun statusText(status: LessonStatus, passDays: Int, needDays: Int, helperDays: Int): String = when (status) {
    LessonStatus.PASSED -> stringResource(R.string.lesson_status_passed)
    LessonStatus.LOCKED -> stringResource(R.string.lesson_status_locked)
    else -> stringResource(R.string.lesson_status_progress, passDays, needDays) + if (helperDays > 0) stringResource(R.string.lesson_status_helper, helperDays) else ""
}
