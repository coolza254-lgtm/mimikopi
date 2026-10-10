package com.id.mimikopi.ui.about

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
import androidx.compose.ui.unit.dp
import com.id.mimikopi.BuildConfig
import com.id.mimikopi.R
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.components.WavyDivider
import com.id.mimikopi.ui.theme.Tokens

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        Image(
            painter = painterResource(R.drawable.wordmark),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth(),
        )
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WavyDivider()
        PageCard(title = stringResource(R.string.about_limits_title)) {
            listOf(
                R.string.about_limit_2, R.string.about_limit_3,
                R.string.about_limit_4, R.string.about_limit_1, R.string.about_limit_6,
            ).forEach { Bullet(stringResource(it)) }
        }
        PageCard(title = stringResource(R.string.about_credits_title)) {
            listOf(R.string.about_credit_logo, R.string.about_credit_font, R.string.about_credit_libs)
                .forEach { Bullet(stringResource(it)) }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.s))
}
