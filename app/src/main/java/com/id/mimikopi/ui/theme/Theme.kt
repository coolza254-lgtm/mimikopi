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

// Black and white only (M15). Filled buttons are ink with paper text; everything else is lines and type.
private fun colors(dark: Boolean): ColorScheme {
    val p = Tokens.Palette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.Ink, onPrimary = p.OnInk, primaryContainer = p.Ink, onPrimaryContainer = p.OnInk,
        secondary = p.Ink, onSecondary = p.OnInk, secondaryContainer = p.Ink, onSecondaryContainer = p.OnInk,
        tertiary = p.Ink, onTertiary = p.OnInk,
        background = p.Paper, onBackground = p.Ink, surface = p.Paper, onSurface = p.Ink,
        surfaceContainer = p.Paper, surfaceContainerLow = p.Paper, surfaceContainerLowest = p.Paper,
        surfaceContainerHigh = p.Raised, surfaceContainerHighest = p.Raised,
        surfaceVariant = p.Paper, onSurfaceVariant = p.InkMuted,
        outline = p.Ink, outlineVariant = p.Line, error = p.Ink, onError = p.OnInk,
    )
}

// The system font for all text (Tokens.Fonts); headings differ by size and weight only.
// Sizes are in sp so they follow the user's font-size setting. Thai needs generous line height.
private val body = Tokens.Fonts.body
private val typography = Typography(
    headlineSmall = TextStyle(fontFamily = Tokens.Fonts.display, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontFamily = Tokens.Fonts.display, fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = Tokens.Fonts.display, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = body, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = body, fontSize = 11.sp, lineHeight = 14.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(Tokens.Radius.button),
    medium = RoundedCornerShape(Tokens.Radius.card),
    large = RoundedCornerShape(Tokens.Radius.card),
)

/** [dark]: OLED black; otherwise the light palette. The palette tokens follow it. */
@Composable
fun MimikopiTheme(dark: Boolean, content: @Composable () -> Unit) {
    // Set before anything below reads a token in this composition.
    remember(dark) { Tokens.dark = dark; dark }
    MaterialTheme(colorScheme = colors(dark), typography = typography, shapes = shapes, content = content)
}
