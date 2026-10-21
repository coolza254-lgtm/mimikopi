package com.id.mimikopi.ui.skill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.ui.components.Hairline
import com.id.mimikopi.ui.components.MeterBar
import com.id.mimikopi.ui.components.TabHeader
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens

/**
 * A skill tab (reading or ear training), laid out like a page of a book: the next step's number in large type, its
 * name, one white button; today's short numbered list; the ladder one tap away. [onOpenStep] opens a step's page
 * (first time), [onOpenDrill] its drill directly.
 */
@Composable
fun SkillScreen(
    skill: Skill,
    onOpenStep: (String) -> Unit,
    onOpenDrill: (String) -> Unit,
    onReview: () -> Unit,
    onOpenMidi: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)? = null,
    vm: SkillViewModel = viewModel(key = skill.name),
) {
    LaunchedEffect(skill) { vm.bind(skill) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showLadder by remember { mutableStateOf(false) }

    val open: (TodayItem) -> Unit = { item ->
        val lessonId = item.lessonId
        when {
            item.kind == TodayKind.REVIEW -> onReview()
            lessonId == null -> Unit
            item.tried -> onOpenDrill(lessonId)
            else -> onOpenStep(lessonId)
        }
    }

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            TabHeader("ขั้นหลัก", stringResource(R.string.skill_reading_goal), onOpenMidi, onOpenSettings, onBack)
            Column(Modifier.padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(40.dp))
                if (ui.loaded) Hero(ui, open)
                Spacer(Modifier.height(36.dp))
                if (ui.today.size > 1) TodayList(ui.today, open)
                ProgressRow(ui, showLadder) { showLadder = !showLadder }
                if (showLadder) Ladder(ui, skill, onOpenStep, onOpenDrill)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SmallCaps(text: String, color: androidx.compose.ui.graphics.Color = Tokens.Palette.InkMuted) {
    Text(text, fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, letterSpacing = 1.5.sp, color = color)
}

@Composable
private fun Hero(ui: SkillUi, open: (TodayItem) -> Unit) {
    val next = ui.next
    if (next == null) {
        SmallCaps(stringResource(R.string.skill_kind_done))
        Text(stringResource(if (ui.finished) R.string.skill_finished else R.string.skill_done_today), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.skill_done_body), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
        return
    }
    val row = ui.rows.firstOrNull { it.lesson.id == next.lessonId }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallCaps(kindLabel(next.kind))
        Box(Modifier.weight(1f).height(1.dp).background(Tokens.Palette.Line))
        if (row != null) SmallCaps(stringResource(R.string.ladder_stage, row.lesson.stage))
    }
    if (row != null) Text(
        row.lesson.id, fontFamily = Tokens.Fonts.mono, fontWeight = FontWeight.Light, fontSize = 96.sp, lineHeight = 100.sp,
        letterSpacing = (-3).sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        if (next.kind == TodayKind.REVIEW) stringResource(R.string.skill_review_title) else next.title,
        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        stringResource(if (next.notYet) R.string.skill_not_yet else if (next.kind == TodayKind.STEP) R.string.skill_rule_short else R.string.skill_kind_hint),
        style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp),
    )
    Button(
        onClick = { open(next) },
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Tokens.Palette.Ink, contentColor = Tokens.Palette.OnInk),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 28.dp, end = 10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp).heightIn(min = 64.dp),
    ) {
        Text(stringResource(if (next.kind == TodayKind.STEP) R.string.skill_start_step else R.string.skill_start), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).background(Tokens.Palette.OnInk, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Tokens.Palette.Ink, modifier = Modifier.size(20.dp))
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
private fun TodayList(items: List<TodayItem>, open: (TodayItem) -> Unit) {
    val next = items.firstOrNull { !it.done }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(Modifier.weight(1f)) { SmallCaps(stringResource(R.string.skill_today)) }
        SmallCaps("${items.count { it.done }} / ${items.size}")
    }
    items.forEachIndexed { i, item ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = !item.done) { open(item) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("%02d".format(i + 1), fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = if (item.done) Tokens.Palette.Dim else Tokens.Palette.Ink)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    if (item.kind == TodayKind.REVIEW) stringResource(R.string.skill_review_title) else item.title,
                    style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                    color = if (item.done) Tokens.Palette.InkMuted else Tokens.Palette.Ink,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                )
                Text(kindLabel(item.kind), style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
            }
            Text(
                stringResource(if (item.done) R.string.skill_done else if (item == next) R.string.skill_next else R.string.skill_later),
                style = MaterialTheme.typography.labelMedium,
                color = if (item == next) Tokens.Palette.Ink else Tokens.Palette.InkMuted,
            )
        }
        Hairline()
    }
}

@Composable
private fun ProgressRow(ui: SkillUi, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.skill_passed, ui.passed, ui.total), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        MeterBar(if (ui.total == 0) 0f else ui.passed.toFloat() / ui.total, Modifier.width(90.dp))
        Text(stringResource(if (open) R.string.skill_hide_ladder else R.string.skill_show_ladder), style = MaterialTheme.typography.labelMedium, color = Tokens.Palette.InkMuted)
    }
}

@Composable
private fun Ladder(ui: SkillUi, skill: Skill, onOpenStep: (String) -> Unit, onOpenDrill: (String) -> Unit) {
    var lastStage = -1
    ui.rows.forEach { row ->
        if (row.lesson.stage != lastStage) {
            lastStage = row.lesson.stage
            Spacer(Modifier.height(20.dp))
            SmallCaps(stringResource(R.string.ladder_stage, row.lesson.stage))
            Spacer(Modifier.height(4.dp))
            Hairline()
        }
        val passed = row.passDays >= row.lesson.pass.days
        val locked = row.status == LessonStatus.LOCKED
        val label = when {
            passed -> R.string.ladder_passed
            row.passDays > 0 -> R.string.ladder_confirm
            row.current -> R.string.ladder_now
            locked -> R.string.ladder_locked
            else -> R.string.ladder_open_step
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(enabled = !locked) { onOpenStep(row.lesson.id) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(row.lesson.id, fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = if (locked) Tokens.Palette.Dim else Tokens.Palette.InkMuted,
                modifier = Modifier.width(40.dp))
            Text(row.lesson.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                fontWeight = if (row.current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (locked) Tokens.Palette.Dim else Tokens.Palette.Ink)
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall,
                color = if (row.current || passed) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
        }
        Hairline()
    }
}
