package com.id.mimikopi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/** Top of a tab: its name, whether the MIDI piano is ready (tap: MIDI settings) and the settings gear. */
@Composable
fun TabHeader(title: String, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit) {
    val ready = midiConnected()
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Black, fontSize = 26.sp, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
        Pill(
            stringResource(if (ready) R.string.midi_ready else R.string.midi_not_ready),
            if (ready) Tokens.Palette.GoodSoft else Tokens.Palette.MustardSoft,
            if (ready) Tokens.Palette.GoodInk else Tokens.Palette.MustardInk,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenMidi),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings_title), tint = Tokens.Palette.Ink, modifier = Modifier.size(24.dp))
        }
    }
}
