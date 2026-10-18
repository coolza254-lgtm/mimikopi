package com.id.mimikopi.desktop.port.components

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/components/Notebook.kt): imports changed, other changes marked "Desktop:".

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.id.mimikopi.desktop.port.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.desktop.ui.Tokens

/** A hairline between sections. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Tokens.Palette.Hair))
}

/** A card on black: a thin border, no fill, no shadow; an optional title in the serif. */
@Composable
fun PageCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    /** Strong border (white) for the item in focus. */
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = Tokens.Palette.Paper, contentColor = Tokens.Palette.Ink)
    val border = BorderStroke(Tokens.border, accent ?: Tokens.Palette.Line)
    val shape = RoundedCornerShape(Tokens.Radius.card)
    val elevation = CardDefaults.cardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    val interaction = remember { MutableInteractionSource() }
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
    val base = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(onClick = onClick, modifier = base.pressScale(interaction), shape = shape, colors = colors, border = border, elevation = elevation,
            interactionSource = interaction, content = inner)
    } else {
        Card(modifier = base, shape = shape, colors = colors, border = border, elevation = elevation, content = inner)
    }
}

/** Presses shrink the surface a little: a tactile, quiet response. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(Tokens.Motion.fast), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** The screen background: OLED black. */
fun Modifier.paper(): Modifier = this.background(Tokens.Palette.Paper)

/**
 * Small label. [filled]: white with black text (a state worth seeing, e.g. "mastered"); otherwise a thin outline.
 * [dashed]: a quiet dashed-looking outline for unverified facts.
 */
@Composable
fun Pill(text: String, filled: Boolean = false, modifier: Modifier = Modifier, dashed: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = Tokens.Fonts.mono,
        color = if (filled) Tokens.Palette.OnInk else if (dashed) Tokens.Palette.InkMuted else Tokens.Palette.Ink,
        modifier = modifier
            .then(if (filled) Modifier.background(Tokens.Palette.Ink, shape) else Modifier.border(1.dp, if (dashed) Tokens.Palette.Dim else Tokens.Palette.Line, shape))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** A thin progress line (0..1): white on the dark track. */
@Composable
fun MeterBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Tokens.Palette.Ink, height: Dp = 2.dp) {
    // Bars fill in smoothly when they appear or change.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val value by animateFloatAsState(if (shown) fraction.coerceIn(0f, 1f) else 0f, tween(Tokens.Motion.slow, easing = FastOutSlowInEasing), label = "meter")
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(Tokens.Palette.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/** A light tap on the hand for right / wrong answers (once per change). */
@Composable
@Suppress("UNUSED_PARAMETER")
fun AnswerHaptics(correct: Boolean?) {
    // Desktop: a computer does not vibrate.
}

/** Round badge for a result: a white disc with a tick when the criterion was met, an outlined repeat mark otherwise. */
@Composable
fun ResultBadge(met: Boolean?) {
    val good = met == true
    Box(
        Modifier.size(64.dp)
            .then(if (good) Modifier.background(Tokens.Palette.Ink, CircleShape) else Modifier.border(1.5.dp, Tokens.Palette.Ink, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (good) R.drawable.ic_check else R.drawable.ic_review),
            contentDescription = null,
            tint = if (good) Tokens.Palette.OnInk else Tokens.Palette.Ink,
            modifier = Modifier.size(30.dp),
        )
    }
}
