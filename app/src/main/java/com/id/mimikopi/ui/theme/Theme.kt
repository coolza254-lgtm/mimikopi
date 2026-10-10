package com.id.mimikopi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Rose fails contrast for text on cream, so text on/near rose surfaces uses Ink.
private val colors = lightColorScheme(
    primary = Tokens.Palette.Rose,
    onPrimary = Tokens.Palette.Ink,
    primaryContainer = Tokens.Palette.RoseSoft,
    onPrimaryContainer = Tokens.Palette.Ink,
    secondary = Tokens.Palette.Mustard,
    onSecondary = Tokens.Palette.Ink,
    secondaryContainer = Tokens.Palette.MustardSoft,
    onSecondaryContainer = Tokens.Palette.Ink,
    background = Tokens.Palette.Paper,
    onBackground = Tokens.Palette.Ink,
    surface = Tokens.Palette.Paper,
    onSurface = Tokens.Palette.Ink,
    surfaceContainer = Tokens.Palette.Card,
    surfaceContainerLow = Tokens.Palette.Card,
    surfaceVariant = Tokens.Palette.Card,
    onSurfaceVariant = Tokens.Palette.InkMuted,
    outline = Tokens.Palette.Line,
    outlineVariant = Tokens.Palette.Line,
    error = Tokens.Palette.Error,
)

// System font: on One UI it covers Thai and Japanese. Sizes are in sp so they follow the
// user's font-size setting. Thai needs generous line height for vowels and tone marks.
private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
)

private val shapes = Shapes(
    small = RoundedCornerShape(Tokens.Radius.button),
    medium = RoundedCornerShape(Tokens.Radius.card),
    large = RoundedCornerShape(Tokens.Radius.card),
)

@Composable
fun MimikopiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
