package com.id.mimikopi.ui.ladder

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.launch

class LadderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MimikopiApp).learning
    val records = repo.records
    val steps: List<Lesson> get() = repo.lessons
    val ear: List<Lesson> get() = repo.earRungs
    init { viewModelScope.launch { repo.load() } }
}

/** The whole step ladder (and the ear ladder): where you are, what is passed and what waits for confirmation. */
@Composable
fun LadderScreen(onBack: () -> Unit, onOpenStep: (String) -> Unit, vm: LadderViewModel = viewModel()) {
    val records by vm.records.collectAsStateWithLifecycle()
    var showEar by remember { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.ladder_title), onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showEar, onClick = { showEar = false }, label = { Text(stringResource(R.string.ladder_tab_steps)) })
            FilterChip(selected = showEar, onClick = { showEar = true }, label = { Text(stringResource(R.string.ladder_tab_ear)) })
        }
        Text(stringResource(R.string.ladder_legend), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        val list = if (showEar) vm.ear else vm.steps
        val current = ProgressRules.nextToLearn(list, records)?.id
        list.groupBy { it.stage }.forEach { (stage, ls) ->
            PageCard(title = stringResource(R.string.ladder_stage, stage)) {
                ls.forEach { l ->
                    val r = records[l.id]
                    val days = r?.passDays?.size ?: 0
                    val status = ProgressRules.status(l, records)
                    val (color, label) = when {
                        days >= l.pass.days -> Tokens.Palette.Good to R.string.ladder_passed
                        days > 0 -> Tokens.Palette.Mustard to R.string.ladder_confirm
                        l.id == current -> Tokens.Palette.Rose to R.string.ladder_now
                        status == LessonStatus.LOCKED -> Tokens.Palette.Track to R.string.ladder_locked
                        else -> Tokens.Palette.Track to R.string.ladder_open_step
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(enabled = status != LessonStatus.LOCKED && !l.id.startsWith("e")) { onOpenStep(l.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(22.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                            if (days >= l.pass.days) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                        }
                        Text(l.id, style = MaterialTheme.typography.labelMedium, color = Tokens.Palette.InkMuted)
                        Text(l.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
                    }
                }
            }
        }
    }
}
