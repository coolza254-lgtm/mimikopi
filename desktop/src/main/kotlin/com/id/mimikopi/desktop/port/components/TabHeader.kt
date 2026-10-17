package com.id.mimikopi.desktop.port.components

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/components/TabHeader.kt): imports changed, other changes marked "Desktop:".

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.id.mimikopi.desktop.port.painterResource
import com.id.mimikopi.desktop.port.stringResource
import androidx.compose.ui.unit.dp
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.desktop.ui.Tokens

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
