package com.id.mimikopi.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.ui.components.MeterBar
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.Pill
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.delay

/**
 * Today on the step ladder: one list, top to bottom. The big button always starts the next unfinished part;
 * nothing to plan or choose. Practice time is not limited.
 */
@Composable
fun TodayScreen(
    onBack: (() -> Unit)?,
    onOpenLesson: (String) -> Unit,
    onOpenDrill: (String, Int) -> Unit,
    onOpenReview: () -> Unit,
    onOpenPiano: () -> Unit,
    onOpenPieces: () -> Unit,
    onOpenNewReading: (Int) -> Unit,
    onOpenEar: () -> Unit,
    onOpenSong: (String) -> Unit = {},
    onOpenBench: () -> Unit = {},
    onOpenLadder: () -> Unit = {},
    vm: TodayViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active != null) { now = System.currentTimeMillis(); delay(1000) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val open: (FlowItem) -> Unit = { item ->
        vm.opened()
        when (item.kind) {
            FlowKind.STEP -> item.lessonId?.let(onOpenLesson)
            FlowKind.CONFIRM, FlowKind.REPEAT, FlowKind.EAR -> item.lessonId?.let { onOpenDrill(it, 0) }
            FlowKind.REVIEW -> onOpenReview()
            FlowKind.BENCH -> onOpenBench()
        }
    }

    ScreenScaffold(title = stringResource(R.string.today_title), onBack = onBack) {
        // Where you are on the ladder.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ladder_step), style = MaterialTheme.typography.titleMedium)
                Text("${(ui.stepsMet + 1).coerceAtMost(ui.stepsTotal)}", fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Black, fontSize = 34.sp, color = Tokens.Palette.Ink)
                Text("/ ${ui.stepsTotal}", style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.ladder_ear, ui.earMet, ui.earTotal), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            MeterBar(if (ui.stepsTotal == 0) 0f else ui.stepsMet.toFloat() / ui.stepsTotal, Modifier.fillMaxWidth())
        }

        // The one button.
        val next = ui.next
        val optional = ui.items.firstOrNull { it.optional }
        when {
            next != null -> Button(onClick = { open(next) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (ui.items.any { it.done }) R.string.flow_continue else R.string.flow_start), style = MaterialTheme.typography.labelLarge)
            }
            ui.allDone -> Column(
                Modifier.fillMaxWidth().background(Tokens.Palette.GoodSoft, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.flow_all_done), style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.GoodInk)
                if (optional != null) OutlinedButton(onClick = { open(optional) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.flow_more_step))
                }
            }
        }

        // The parts of today, in order.
        Column {
            ui.items.forEachIndexed { i, item ->
                FlowRow(i, item, last = i == ui.items.lastIndex) { open(item) }
            }
        }

        val a = active
        if (a != null) {
            val minutes = (now - a.startedAtMillis) / 60_000
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.flow_minutes, minutes), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = vm::finishSession, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.flow_finish))
                }
            }
        }

        ui.stats?.let { s ->
            PageCard(title = stringResource(R.string.today_stats)) {
                Text(stringResource(R.string.today_stats_body, s.minutesToday, s.minutesThisWeek, s.minutesLastWeek, s.daysPracticed, s.totalMinutes),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        TextButton(onClick = onOpenLadder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ladder_open)) }
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
                })
            },
            confirmButton = { TextButton(onClick = vm::dismissSummary) { Text(stringResource(R.string.drill_done)) } },
        )
    }
}

/** One part of today on the dashed line: a tick when done, its number otherwise. */
@Composable
private fun FlowRow(index: Int, item: FlowItem, last: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 12.dp).size(36.dp)
                    .background(if (item.done) Tokens.Palette.Good else Tokens.Palette.Card, CircleShape)
                    .border(if (item.done) 0.dp else 2.dp, Tokens.Palette.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.flow_done), tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                else Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = Tokens.Palette.InkMuted)
            }
            if (!last) Canvas(Modifier.weight(1f).width(2.dp)) {
                drawLine(Tokens.Palette.Rose, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = size.width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Tokens.Palette.Card)
                .border(Tokens.border, if (!item.done && !item.optional) Tokens.Palette.Rose else Tokens.Palette.Line, RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill(stringResource(kindLabel(item.kind)), if (item.kind == FlowKind.STEP || item.kind == FlowKind.EAR) Tokens.Palette.MustardSoft else Tokens.Palette.RoseSoft,
                if (item.kind == FlowKind.STEP || item.kind == FlowKind.EAR) Tokens.Palette.MustardInk else Tokens.Palette.RoseInk)
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = if (item.done) Tokens.Palette.InkMuted else Tokens.Palette.Ink)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            if (item.notYet) Text(stringResource(R.string.flow_not_yet), style = MaterialTheme.typography.labelMedium, color = Tokens.Palette.RoseDeep)
        }
    }
}

private fun kindLabel(kind: FlowKind): Int = when (kind) {
    FlowKind.CONFIRM -> R.string.flow_kind_confirm
    FlowKind.REVIEW -> R.string.flow_kind_review
    FlowKind.REPEAT -> R.string.flow_kind_repeat
    FlowKind.STEP -> R.string.flow_kind_step
    FlowKind.EAR -> R.string.flow_kind_ear
    FlowKind.BENCH -> R.string.flow_kind_bench
}
