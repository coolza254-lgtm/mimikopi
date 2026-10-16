package com.id.mimikopi.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.ui.theme.Tokens

/** True while a MIDI piano is connected and open (USB or Bluetooth). */
@Composable
fun midiConnected(): Boolean {
    val app = LocalContext.current.applicationContext as MimikopiApp
    val devices by app.midiInput.devices.collectAsState()
    return devices.any { it.open }
}

/**
 * Where the on-screen keyboard used to be (M15: the app is played only from a MIDI piano). Keys the app points out
 * (an answer after a mistake, a given first note) are named in large type; without a piano it says to connect one.
 */
@Composable
fun KeyHint(marked: Set<Int>, nameStyle: NoteNameStyle, modifier: Modifier = Modifier) {
    if (!midiConnected()) {
        Text(
            stringResource(R.string.midi_needed),
            style = MaterialTheme.typography.titleMedium,
            color = Tokens.Palette.RoseDeep,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
    if (marked.isNotEmpty()) Text(
        marked.sorted().joinToString("  ·  ") { SpelledPitch.fromMidiSharp(it).name(nameStyle, withOctave = true) },
        style = MaterialTheme.typography.headlineSmall,
        color = Tokens.Palette.RoseDeep,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
