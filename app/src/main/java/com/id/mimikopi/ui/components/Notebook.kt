package com.id.mimikopi.ui.components

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
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
fun AnswerHaptics(correct: Boolean?, done: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(correct) {
        when (correct) {
            true -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            false -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
            null -> Unit
        }
        if (correct != null) playAnswerSound(context, if (done && correct) com.id.mimikopi.core.fx.AnswerSound.DONE
            else if (correct) com.id.mimikopi.core.fx.AnswerSound.CORRECT else com.id.mimikopi.core.fx.AnswerSound.WRONG)
    }
    AnswerBadge(correct, big = done)
}

/** Plays one of [com.id.mimikopi.core.fx.AnswerSound]'s short sounds on the app's piano, unless turned off. */
suspend fun playAnswerSound(context: android.content.Context, notes: List<Triple<Int, Int, Int>>) {
    if (!answerSoundOn(context)) return
    val piano = (context.applicationContext as com.id.mimikopi.MimikopiApp).piano
    kotlinx.coroutines.coroutineScope {
        for ((note, start, length) in notes) launch {
            kotlinx.coroutines.delay(start.toLong())
            piano.noteOn(note, com.id.mimikopi.core.fx.AnswerSound.VELOCITY)
            kotlinx.coroutines.delay(length.toLong())
            piano.noteOff(note)
        }
    }
}

fun answerSoundOn(context: android.content.Context): Boolean =
    context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).getBoolean(com.id.mimikopi.core.fx.AnswerSound.KEY, true)

fun setAnswerSound(context: android.content.Context, on: Boolean) =
    context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putBoolean(com.id.mimikopi.core.fx.AnswerSound.KEY, on).apply()

/**
 * The answer mark: a green ✓ (or a small ink ✕) pops in near the top of the screen and fades, in under half a
 * second, drawn in a popup so it never moves the page.
 */
@Composable
fun AnswerBadge(correct: Boolean?, big: Boolean = false) {
    if (correct == null) return
    val scale = remember(correct) { androidx.compose.animation.core.Animatable(0.4f) }
    val alpha = remember(correct) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(correct) {
        launch { alpha.animateTo(1f, androidx.compose.animation.core.tween(120)) }
        scale.animateTo(1.15f, androidx.compose.animation.core.tween(140))
        scale.animateTo(1f, androidx.compose.animation.core.tween(90))
        kotlinx.coroutines.delay(if (big) 900 else 450)
        alpha.animateTo(0f, androidx.compose.animation.core.tween(220))
    }
    val size = if (big) 72.dp else if (correct) 44.dp else 34.dp
    androidx.compose.ui.window.Popup(alignment = Alignment.TopCenter, offset = androidx.compose.ui.unit.IntOffset(0, 220)) {
        Box(
            Modifier.size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
                .clip(CircleShape).background(if (correct) Tokens.Palette.Pass else Tokens.Palette.Ink),
            contentAlignment = Alignment.Center,
        ) { Text(if (correct) "✓" else "✕", color = androidx.compose.ui.graphics.Color.White, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold) }
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
