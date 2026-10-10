package com.id.mimikopi.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
fun HomeScreen(onOpenDiagnostics: () -> Unit, onOpenAbout: () -> Unit, onOpenSettings: () -> Unit) {
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
        PageCard(title = stringResource(R.string.home_status_title)) {
            Spacer(Modifier.height(Tokens.Space.xs))
            Text(stringResource(R.string.home_status_body), style = MaterialTheme.typography.bodyMedium)
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
