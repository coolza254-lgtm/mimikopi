package com.id.mimikopi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/**
 * Top of a tab: its name in the serif, a one-line purpose under it, whether the MIDI piano is ready (a filled dot;
 * tap: MIDI settings) and the settings button.
 */
@Composable
fun TabHeader(title: String, subtitle: String?, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit) {
    val ready = midiConnected()
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
            Row(
                Modifier.heightIn(min = 44.dp).clickable(onClick = onOpenMidi).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Spacer(
                    Modifier.size(7.dp).then(
                        if (ready) Modifier.background(Tokens.Palette.Ink, CircleShape)
                        else Modifier.border(1.dp, Tokens.Palette.InkMuted, CircleShape),
                    ),
                )
                Text(stringResource(if (ready) R.string.midi_ready else R.string.midi_not_ready), fontFamily = Tokens.Fonts.mono, fontSize = 12.sp,
                    color = if (ready) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings_title), tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
            }
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted)
        // Without a keyboard nothing can be practised: say so once, at the top, with the way to fix it.
        if (!ready) Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, end = 16.dp).heightIn(min = 52.dp)
                .border(1.dp, Tokens.Palette.Ink, androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                .clickable(onClick = onOpenMidi).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.midi_banner), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.midi_banner_action), style = MaterialTheme.typography.labelLarge)
        }
    }
}

