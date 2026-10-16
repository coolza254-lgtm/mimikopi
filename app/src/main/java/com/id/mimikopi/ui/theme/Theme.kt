package com.id.mimikopi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Black and white only (M15). Filled buttons are white with black text; everything else is lines and type.
private val colors = darkColorScheme(
    primary = Tokens.Palette.Ink,
    onPrimary = Tokens.Palette.OnInk,
    primaryContainer = Tokens.Palette.Ink,
    onPrimaryContainer = Tokens.Palette.OnInk,
    secondary = Tokens.Palette.Ink,
    onSecondary = Tokens.Palette.OnInk,
    secondaryContainer = Tokens.Palette.Ink,
    onSecondaryContainer = Tokens.Palette.OnInk,
    tertiary = Tokens.Palette.Ink,
    onTertiary = Tokens.Palette.OnInk,
    background = Tokens.Palette.Paper,
    onBackground = Tokens.Palette.Ink,
    surface = Tokens.Palette.Paper,
    onSurface = Tokens.Palette.Ink,
    surfaceContainer = Tokens.Palette.Paper,
    surfaceContainerLow = Tokens.Palette.Paper,
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF161616),
    surfaceVariant = Tokens.Palette.Paper,
    onSurfaceVariant = Tokens.Palette.InkMuted,
    outline = Tokens.Palette.Ink,
    outlineVariant = Tokens.Palette.Line,
    error = Tokens.Palette.Ink,
    onError = Tokens.Palette.OnInk,
)

// IBM Plex Sans Thai for text, Noto Serif Thai for headings (bundled, OFL); other scripts fall back to the system font.
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

@Composable
fun MimikopiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
