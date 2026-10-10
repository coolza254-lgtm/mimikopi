package com.id.mimikopi.ui.about

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import com.id.mimikopi.R
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens

/** Licences and attributions (CC BY requires naming the author, the licence and the changes made). */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.credits_title), onBack = onBack) {
        Credit(R.string.credit_piano_title, R.string.credit_piano_body)
        Credit(R.string.credit_oboe_title, R.string.credit_oboe_body)
        Credit(R.string.credit_font_title, R.string.credit_font_body)
        Credit(R.string.credit_logo_title, R.string.credit_logo_body)
        Credit(R.string.credit_libs_title, R.string.credit_libs_body)
    }
}

@Composable
private fun Credit(title: Int, body: Int) {
    PageCard(title = stringResource(title)) {
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.xs))
    }
}
