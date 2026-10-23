package com.id.mimikopi.ui.reader

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.ui.theme.Tokens

/** One compact control row; the remaining viewport belongs to the music and required touch input. */
@Composable
internal fun ScorePlayerViewport(
    status: String, connected: Boolean, onStop: () -> Unit,
    keyboard: @Composable () -> Unit, score: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().keepScreenOn().background(Tokens.Palette.Paper)
        .windowInsetsPadding(WindowInsets.displayCutout).testTag("score-player")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("score-player-controls"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status + if (connected) " · MIDI" else "", color = Tokens.Palette.InkMuted,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Button(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp).testTag("reading-stop")) { Text(tr("หยุด", "Stop", "停止")) }
        }
        score(Modifier.weight(1f).fillMaxWidth().testTag("score-player-music"))
        if (!connected) keyboard()
    }
}
