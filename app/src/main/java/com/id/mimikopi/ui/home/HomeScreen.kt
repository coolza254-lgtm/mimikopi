package com.id.mimikopi.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.id.mimikopi.R
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.components.WavyDivider
import com.id.mimikopi.ui.theme.Tokens

@Composable
fun HomeScreen(onOpenDiagnostics: () -> Unit, onOpenAbout: () -> Unit, onOpenSettings: () -> Unit, onOpenPiano: () -> Unit, onOpenMetronome: () -> Unit, onOpenMidi: () -> Unit, onOpenToday: () -> Unit, onOpenReader: () -> Unit) {
    ScreenScaffold(title = "", onBack = null) {
        Image(
            painter = painterResource(R.drawable.wordmark),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().padding(top = Tokens.Space.s),
        )
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        WavyDivider()
        PageCard(title = stringResource(R.string.home_today_title), onClick = onOpenToday) {
            Text(stringResource(R.string.home_today_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PageCard(title = stringResource(R.string.home_reader_title), onClick = onOpenReader) {
            Text(stringResource(R.string.home_reader_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PageCard(title = stringResource(R.string.home_piano_title), onClick = onOpenPiano) {
            Text(stringResource(R.string.home_piano_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PageCard(title = stringResource(R.string.home_metronome_title), onClick = onOpenMetronome) {
            Text(stringResource(R.string.home_metronome_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PageCard(title = stringResource(R.string.home_midi_title), onClick = onOpenMidi) {
            Text(stringResource(R.string.home_midi_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PageCard(title = stringResource(R.string.home_settings_title), onClick = onOpenSettings) {
            Text(stringResource(R.string.home_settings_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PageCard(title = stringResource(R.string.home_diagnostics_title), onClick = onOpenDiagnostics) {
            Text(stringResource(R.string.home_diagnostics_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PageCard(title = stringResource(R.string.home_about_title), onClick = onOpenAbout) {
            Text(stringResource(R.string.home_about_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
