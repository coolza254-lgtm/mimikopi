package com.id.mimikopi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Design J: filled buttons in the dusty-rose accent with white text, selected chips accentSoft; cards are white on the cream page.
private fun colors(dark: Boolean): ColorScheme {
    val p = Tokens.Palette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.Accent, onPrimary = p.OnAccent, primaryContainer = p.AccentSoft, onPrimaryContainer = p.Ink,
        secondary = p.Accent, onSecondary = p.OnAccent, secondaryContainer = p.AccentSoft, onSecondaryContainer = p.Ink,
        tertiary = p.Accent, onTertiary = p.OnAccent,
        background = p.Paper, onBackground = p.Ink, surface = p.Paper, onSurface = p.Ink,
        surfaceContainer = p.Paper, surfaceContainerLow = p.Paper, surfaceContainerLowest = p.Paper,
        surfaceContainerHigh = p.Raised, surfaceContainerHighest = p.Raised,
        surfaceBright = p.Group,
        surfaceVariant = p.Paper, onSurfaceVariant = p.InkMuted,
        outline = p.Line, outlineVariant = p.Hair, error = p.Wrong, onError = p.OnAccent,
    )
}

// IBM Plex Sans Thai Looped for all text (Tokens.Fonts), sizes and weights from Design J: page title 28-30/700,
// song title in a card 25-28/700, section heading 17/700, list title 15-16/600, body 14/400, secondary 12-13, chip 11-13/600.
// Sizes are in sp so they follow the user's font-size setting. Looped Thai needs generous line height.
private val body = Tokens.Fonts.body
private val typography = Typography(
    displaySmall = TextStyle(fontFamily = body, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = body, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = body, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = body, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = body, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = body, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = body, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
)

private val shapes = Shapes(
    small = RoundedCornerShape(Tokens.Radius.button),
    medium = RoundedCornerShape(Tokens.Radius.card),
    large = RoundedCornerShape(Tokens.Radius.card),
)

/** [dark]: the dark palette (not yet designed in Design J); otherwise the light one. The palette tokens follow it. */
@Composable
fun MimikopiTheme(dark: Boolean, content: @Composable () -> Unit) {
    // Set before anything below reads a token in this composition.
    remember(dark) { Tokens.dark = dark; dark }
    MaterialTheme(colorScheme = colors(dark), typography = typography, shapes = shapes, content = content)
}
