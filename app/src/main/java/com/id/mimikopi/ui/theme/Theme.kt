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
    // Filled buttons are dark ink on cream (the design's main action), rose stays decorative.
    primary = Tokens.Palette.Ink,
    onPrimary = Tokens.Palette.Paper,
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

// IBM Plex Sans Thai Looped (bundled, OFL); Japanese song titles fall back to the system font.
// Sizes are in sp so they follow the user's font-size setting. Thai needs generous line height.
private val body = Tokens.Fonts.body
private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = body, fontSize = 26.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = body, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
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

@Composable
fun MimikopiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
