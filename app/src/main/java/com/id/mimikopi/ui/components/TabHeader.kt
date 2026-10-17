package com.id.mimikopi.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/**
 * Top of a main page: the large title with a one-line purpose under it, whether the MIDI piano is ready (tap: MIDI
 * settings) and the settings button; [onBack] adds the back arrow on pages opened from another page.
 */
@Composable
fun TabHeader(title: String, subtitle: String?, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit, onBack: (() -> Unit)? = null) {
    LargeTitle(title, subtitle, onBack) {
        MidiPill(onOpenMidi)
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings_title), tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
        }
    }
}
