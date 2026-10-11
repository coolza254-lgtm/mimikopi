package com.id.mimikopi.ui.ear

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.ear.EarLevel
import com.id.mimikopi.core.ear.EarStats
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class EarHubUi(val stage: Int = 1, val stats: List<EarStats.ItemStat> = emptyList(), val due: Int = 0)

class EarViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val _ui = MutableStateFlow(EarHubUi())
    val ui: StateFlow<EarHubUi> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val l = services.learning
            l.load()
            val stage = ProgressRules.current(l.lessons, l.records.value)?.stage ?: l.lessons.maxOfOrNull { it.stage } ?: 1
            _ui.value = EarHubUi(stage, l.earStats().sortedBy { it.itemId }, l.earDueItems().size)
        }
    }
}

/** Ear-training hub: mixed practice (recommended), each level, and accuracy per kind of item (11.4). */
@Composable
fun EarScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onTranscribe: () -> Unit, vm: EarViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    ScreenScaffold(title = stringResource(R.string.ear_title), onBack = onBack) {
        Text(stringResource(R.string.ear_intro), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { onOpen("mix") }, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
            Text(stringResource(R.string.ear_mix, EarStats.unlocked(ui.stage).joinToString { it.name }))
        }
        if (ui.due > 0) Text(stringResource(R.string.ear_due, ui.due), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        PageCard(title = stringResource(R.string.ear_levels)) {
            EarLevel.entries.forEach { level ->
                val open = level.unlockStage <= ui.stage
                val acc = ui.stats.filter { level in EarStats.levelsOf(it.itemId) }.let { s ->
                    val total = s.sumOf { it.total }
                    if (total == 0) null else s.sumOf { it.correct }.toDouble() / total
                }
                Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("${level.name} · ${levelName(level)}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            (if (open) "" else stringResource(R.string.ear_locked, level.unlockStage) + " · ") +
                                (acc?.let { stringResource(R.string.ear_accuracy, (it * 100).roundToInt()) } ?: stringResource(R.string.ear_no_data)),
                            style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted,
                        )
                    }
                    if (open) Button(onClick = { onOpen(level.name) }) { Text(stringResource(R.string.ear_practise)) }
                    else OutlinedButton(onClick = { onOpen(level.name) }) { Text(stringResource(R.string.ear_try_early)) }
                }
            }
        }
        PageCard(title = stringResource(R.string.ear_stats)) {
            if (ui.stats.isEmpty()) Text(stringResource(R.string.ear_no_data), style = MaterialTheme.typography.bodyMedium)
            ui.stats.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(EarDrillViewModel.itemLabel(s.itemId), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${s.correct}/${s.total} (${(s.accuracy * 100).roundToInt()}%)", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(R.string.ear_stats_note), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted,
                modifier = Modifier.padding(top = Tokens.Space.s))
        }
        PageCard(title = stringResource(R.string.ear_transcribe_title), onClick = onTranscribe) {
            Text(stringResource(R.string.ear_transcribe_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun levelName(level: EarLevel): String = stringResource(when (level) {
    EarLevel.E1 -> R.string.ear_e1
    EarLevel.E2 -> R.string.ear_e2
    EarLevel.E3 -> R.string.ear_e3
    EarLevel.E4 -> R.string.ear_e4
    EarLevel.E5 -> R.string.ear_e5
    EarLevel.E6 -> R.string.ear_e6
    EarLevel.E7 -> R.string.ear_e7
    EarLevel.E8 -> R.string.ear_e8
    EarLevel.E9 -> R.string.ear_e9
})
