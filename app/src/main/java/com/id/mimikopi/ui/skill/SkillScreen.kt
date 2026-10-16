package com.id.mimikopi.ui.skill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.ui.components.MeterBar
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.TabHeader
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.components.softShadow
import com.id.mimikopi.ui.theme.Tokens

/**
 * A skill tab (reading or ear training): one big button for the next thing to do, today's short list under it,
 * and the ladder. [onOpenStep] opens a step's page (first time), [onOpenDrill] its drill directly.
 */
@Composable
fun SkillScreen(
    skill: Skill,
    onOpenStep: (String) -> Unit,
    onOpenDrill: (String) -> Unit,
    onReview: () -> Unit,
    onOpenMidi: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: SkillViewModel = viewModel(key = skill.name),
) {
    LaunchedEffect(skill) { vm.bind(skill) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }

    val open: (TodayItem) -> Unit = { item ->
        when {
            item.kind == TodayKind.REVIEW -> onReview()
            item.lessonId == null -> Unit
            skill == Skill.EAR || item.tried -> onOpenDrill(item.lessonId)
            else -> onOpenStep(item.lessonId)
        }
    }

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            TabHeader(stringResource(if (skill == Skill.READING) R.string.tab_reading else R.string.tab_ear), onOpenMidi, onOpenSettings)
            Text(
                stringResource(if (skill == Skill.READING) R.string.skill_reading_goal else R.string.skill_ear_goal),
                style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (ui.loaded) NextCard(ui, open)
                if (ui.today.size > 1) PageCard(title = stringResource(R.string.skill_today)) {
                    ui.today.forEach { item -> TodayRow(item) { open(item) } }
                }
                PageCard {
                    Text(stringResource(R.string.skill_passed, ui.passed, ui.total), style = MaterialTheme.typography.titleMedium)
                    MeterBar(if (ui.total == 0) 0f else ui.passed.toFloat() / ui.total, Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(stringResource(R.string.skill_rule), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
                }
                val stage = ui.currentStage
                val rows = if (showAll || stage == null) ui.rows else ui.rows.filter { it.lesson.stage == stage }
                PageCard(title = if (showAll || stage == null) stringResource(R.string.skill_all_steps) else stringResource(R.string.ladder_stage, stage)) {
                    rows.forEach { row ->
                        LadderRowView(row) {
                            if (skill == Skill.EAR) onOpenDrill(row.lesson.id) else onOpenStep(row.lesson.id)
                        }
                    }
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(stringResource(if (showAll) R.string.skill_show_stage else R.string.skill_show_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun NextCard(ui: SkillUi, open: (TodayItem) -> Unit) {
    val next = ui.next
    val shape = RoundedCornerShape(Tokens.Radius.card)
    Column(
        Modifier.fillMaxWidth().softShadow(shape, Tokens.Elevation.raised).background(Tokens.Palette.Rose, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (next == null) {
            Text(stringResource(if (ui.finished) R.string.skill_finished else R.string.skill_done_today), fontFamily = Tokens.Fonts.display,
                fontWeight = FontWeight.Black, fontSize = 22.sp, color = Tokens.Palette.Ink)
            Text(stringResource(R.string.skill_done_body), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink)
            return@Column
        }
        Text(kindLabel(next.kind), style = MaterialTheme.typography.labelLarge, color = Tokens.Palette.RoseInk)
        Text(
            if (next.kind == TodayKind.REVIEW) stringResource(R.string.skill_review_title) else next.title,
            fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Tokens.Palette.Ink,
        )
        if (next.notYet) Text(stringResource(R.string.skill_not_yet), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.RoseInk)
        Button(
            onClick = { open(next) },
            shape = RoundedCornerShape(Tokens.Radius.button),
            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Palette.Ink, contentColor = Tokens.Palette.Paper),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(top = 6.dp),
        ) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(20.dp))
            Text("  " + stringResource(R.string.skill_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun kindLabel(kind: TodayKind): String = stringResource(
    when (kind) {
        TodayKind.CONFIRM -> R.string.skill_kind_confirm
        TodayKind.REVIEW -> R.string.skill_kind_review
        TodayKind.REPEAT -> R.string.skill_kind_repeat
        TodayKind.STEP -> R.string.skill_kind_step
    },
)

@Composable
private fun TodayRow(item: TodayItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = !item.done, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Dot(if (item.done) Tokens.Palette.Good else Tokens.Palette.Track, check = item.done)
        Column(Modifier.weight(1f)) {
            Text(kindLabel(item.kind), style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
            Text(if (item.kind == TodayKind.REVIEW) stringResource(R.string.skill_review_title) else item.title,
                style = MaterialTheme.typography.bodyLarge, color = if (item.done) Tokens.Palette.InkMuted else Tokens.Palette.Ink, maxLines = 2)
        }
    }
}

@Composable
private fun LadderRowView(row: LadderRow, onClick: () -> Unit) {
    val passed = row.passDays >= row.lesson.pass.days
    val (color, label) = when {
        passed -> Tokens.Palette.Good to R.string.ladder_passed
        row.passDays > 0 -> Tokens.Palette.Mustard to R.string.ladder_confirm
        row.current -> Tokens.Palette.Rose to R.string.ladder_now
        row.status == LessonStatus.LOCKED -> Tokens.Palette.Track to R.string.ladder_locked
        else -> Tokens.Palette.Track to R.string.ladder_open_step
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = row.status != LessonStatus.LOCKED, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(color, check = passed)
        Text(row.lesson.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2,
            fontWeight = if (row.current) FontWeight.Bold else FontWeight.Normal,
            color = if (row.status == LessonStatus.LOCKED) Tokens.Palette.InkMuted else Tokens.Palette.Ink)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
    }
}

@Composable
private fun Dot(color: Color, check: Boolean) {
    Box(Modifier.size(22.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        if (check) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
