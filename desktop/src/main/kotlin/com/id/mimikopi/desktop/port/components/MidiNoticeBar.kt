package com.id.mimikopi.desktop.port.components

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/components/MidiNoticeBar.kt): imports changed, other changes marked "Desktop:".

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.id.mimikopi.desktop.port.painterResource
import androidx.compose.ui.unit.dp
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.core.input.MidiNotice
import com.id.mimikopi.desktop.ui.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * The short notice at the top of every screen when a keyboard is plugged in, pulled out, or a Bluetooth link
 * changes. A connection gets the green tick (the app's one colour); a problem stays a little longer. Tapping it
 * opens the MIDI page.
 */
@Composable
fun MidiNoticeBar(notices: StateFlow<MidiNotice?>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val notice by notices.collectAsState()
    var shown by remember { mutableStateOf<MidiNotice?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        // A notice from before this screen existed (the window was recreated) is not told again.
        if (System.nanoTime() - n.atNanos > 3_000_000_000L) return@LaunchedEffect
        shown = n
        visible = true
        delay(if (n.isProblem) 6_000 else 3_500)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        val n = shown ?: return@AnimatedVisibility
        val connected = !n.isProblem
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 560.dp).heightIn(min = 48.dp)
                .background(Tokens.Palette.Paper, RoundedCornerShape(24.dp))
                .border(1.dp, if (connected) Tokens.Palette.Positive else Tokens.Palette.Ink, RoundedCornerShape(24.dp))
                .clickable { visible = false; onOpen() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(22.dp).then(
                    if (connected) Modifier.background(Tokens.Palette.Positive, CircleShape) else Modifier.border(1.5.dp, Tokens.Palette.Ink, CircleShape),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (connected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Text(n.text(), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink)
        }
    }
}
