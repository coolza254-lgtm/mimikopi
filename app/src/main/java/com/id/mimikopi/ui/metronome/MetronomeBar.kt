package com.id.mimikopi.ui.metronome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/**
 * Compact metronome for practice screens (the host screen already holds the audio output).
 * Settings are shared with the full metronome screen.
 */
@Composable
fun MetronomeBar(vm: MetronomeViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = running, onClick = vm::toggle, label = { Text(stringResource(R.string.metronome_short, s.bpm)) })
        OutlinedButton(onClick = { vm.nudge(-1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("−") }
        OutlinedButton(onClick = { vm.nudge(1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("+") }
        OutlinedButton(onClick = vm::tap, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.metronome_tap)) }
        if (running) BeatDots(s, running, vm::tickCount, dotSize = 12)
    }
}
