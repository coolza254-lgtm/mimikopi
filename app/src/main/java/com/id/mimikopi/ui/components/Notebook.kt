package com.id.mimikopi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/** A hairline between sections. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Tokens.Palette.Hair))
}

/**
 * A card (Design J): white, corner 32, lifted by a soft rose shadow instead of a line; an optional section title.
 * [accent] draws a 2 dp ring in that colour for the item in focus ("ต่อไป").
 */
@Composable
fun PageCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.Radius.card)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .softShadow(shape)
            .clip(shape).background(Tokens.Palette.Group)
            .then(if (accent != null) Modifier.border(2.dp, accent, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.Ink)
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/** The design's card shadow: 0 8 28 rgba(120,80,92,.08); [small] for cards in a grid (.06). */
fun Modifier.softShadow(shape: androidx.compose.ui.graphics.Shape, small: Boolean = false): Modifier =
    this.shadow(if (small) Tokens.Elevation.cardSmall else Tokens.Elevation.card, shape,
        ambientColor = Tokens.Elevation.tint.copy(alpha = if (small) 0.06f else 0.08f),
        spotColor = Tokens.Elevation.tint.copy(alpha = if (small) 0.10f else 0.14f))

/** Presses shrink the surface a little: a tactile, quiet response. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(Tokens.Motion.fast), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** The screen background: the cream page of Design J. */
fun Modifier.paper(): Modifier = this.background(Tokens.Palette.Paper)

/**
 * A small capsule label. [filled]: accent with white text (a state worth seeing, e.g. "ต่อไป"); otherwise accentSoft
 * with accentDeep text. [dashed]: a quiet outline for unverified facts.
 */
@Composable
fun Pill(text: String, filled: Boolean = false, modifier: Modifier = Modifier, dashed: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = when {
            filled -> Tokens.Palette.OnAccent
            dashed -> Tokens.Palette.InkMuted
            else -> Tokens.Palette.AccentDeep
        },
        modifier = modifier
            .then(when {
                filled -> Modifier.background(Tokens.Palette.Accent, shape)
                dashed -> Modifier.border(1.dp, Tokens.Palette.Dim, shape)
                else -> Modifier.background(Tokens.Palette.AccentSoft, shape)
            })
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A progress rail (0..1): the accent on the track, rounded. */
@Composable
fun MeterBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Tokens.Palette.Accent, height: Dp = 6.dp) {
    // Bars fill in smoothly when they appear or change.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val value by animateFloatAsState(if (shown) fraction.coerceIn(0f, 1f) else 0f, tween(Tokens.Motion.slow, easing = FastOutSlowInEasing), label = "meter")
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(Tokens.Palette.Track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/** A light tap on the hand for right / wrong answers (once per change). */
@Composable
fun AnswerHaptics(correct: Boolean?) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(correct) {
        when (correct) {
            true -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            false -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
            null -> Unit
        }
    }
}

/** Round badge for a result: an accent disc with a tick when the criterion was met, an outlined repeat mark otherwise. */
@Composable
fun ResultBadge(met: Boolean?) {
    val good = met == true
    Box(
        Modifier.size(64.dp)
            .then(if (good) Modifier.background(Tokens.Palette.Accent, CircleShape) else Modifier.background(Tokens.Palette.AccentSoft, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (good) R.drawable.ic_check else R.drawable.ic_review),
            contentDescription = null,
            tint = if (good) Tokens.Palette.OnAccent else Tokens.Palette.AccentDeep,
            modifier = Modifier.size(30.dp),
        )
    }
}
