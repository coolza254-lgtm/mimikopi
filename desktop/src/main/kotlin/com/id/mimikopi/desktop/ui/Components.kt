package com.id.mimikopi.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.Services

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Tokens.Palette.Hair))
}

/** A card on black: a thin border, no fill; an optional serif title. */
@Composable
fun PageCard(modifier: Modifier = Modifier, title: String? = null, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Tokens.cardRadius)
    Column(modifier.fillMaxWidth().border(1.dp, accent ?: Tokens.Palette.Line, shape).padding(horizontal = 22.dp, vertical = 20.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
fun SmallCaps(text: String, color: Color = Tokens.Palette.InkMuted) {
    Text(text, fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, letterSpacing = 1.5.sp, color = color)
}

@Composable
fun Pill(text: String, filled: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text, fontFamily = Tokens.Fonts.mono, fontSize = 11.sp,
        color = if (filled) Tokens.Palette.OnInk else Tokens.Palette.Ink,
        modifier = (if (filled) Modifier.background(Tokens.Palette.Ink, shape) else Modifier.border(1.dp, Tokens.Palette.Line, shape)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** A thin progress line (0..1). */
@Composable
fun MeterBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 2.dp) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val value by animateFloatAsState(if (shown) fraction.coerceIn(0f, 1f) else 0f, tween(420, easing = FastOutSlowInEasing))
    Box(modifier.height(height).background(Tokens.Palette.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value).background(Tokens.Palette.Ink))
    }
}

/** "07/20" with a filling line, inside a Row. */
@Composable
fun RowScope.DrillProgress(index: Int, total: Int) {
    MeterBar(if (total == 0) 0f else index.toFloat() / total, Modifier.weight(1f))
    Text("%02d/%02d".format(index, total), fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, modifier = Modifier.padding(start = 14.dp))
}

/** True while a MIDI keyboard is connected and (as far as the app can tell) switched on. */
@Composable
fun midiReady(): Boolean {
    val devices by Services.midi.devices.collectAsState()
    return devices.any { it.ready }
}

/** Without a keyboard nothing can be practised: say so, with the way to fix it. */
@Composable
fun NoKeyboardBanner(onOpenMidi: () -> Unit, modifier: Modifier = Modifier) {
    if (midiReady()) return
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).border(1.dp, Tokens.Palette.Ink, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onOpenMidi).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ยังไม่ได้ต่อคีย์บอร์ด MIDI (หรือปิดเครื่องอยู่)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("ดูการเชื่อมต่อ →", style = MaterialTheme.typography.labelLarge)
    }
}

/** A large circle button (listen / start). */
@Composable
fun BigCircle(label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(148.dp).clip(CircleShape).border(1.5.dp, Tokens.Palette.Ink, CircleShape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("▶", fontSize = 30.sp, color = Tokens.Palette.Ink)
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp), maxLines = 2)
    }
}

/** Page header for inner screens: a back link and the title in the serif. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("←", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 1)
        trailing()
    }
}

@Composable
fun BigNumber(text: String) {
    Text(text, fontFamily = Tokens.Fonts.mono, fontWeight = FontWeight.Light, fontSize = 96.sp, lineHeight = 100.sp, letterSpacing = (-3).sp)
}
