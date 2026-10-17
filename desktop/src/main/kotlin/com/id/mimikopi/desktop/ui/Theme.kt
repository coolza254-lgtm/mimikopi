package com.id.mimikopi.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens, the same as the Android app's (ui/theme/Tokens.kt): black and white only, OLED black by default or
 * the light palette; the system font for all text.
 */
object Tokens {
    var dark: Boolean by mutableStateOf(true)

    object Palette {
        val Paper: Color get() = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF)
        val Ink: Color get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF000000)
        val InkMuted: Color get() = if (dark) Color(0xFF9E9E9E) else Color(0xFF5E5E5E)
        val Dim: Color get() = if (dark) Color(0xFF6E6E6E) else Color(0xFF8A8A8A)
        val Faint: Color get() = if (dark) Color(0xFF4A4A4A) else Color(0xFFBDBDBD)
        val Line: Color get() = if (dark) Color(0xFF2B2B2B) else Color(0xFFDDDDDD)
        val Hair: Color get() = if (dark) Color(0xFF1F1F1F) else Color(0xFFEDEDED)
        val OnInk: Color get() = Paper
        val Group: Color get() = if (dark) Color(0xFF151515) else Color(0xFFF2F2F2)
        val Raised: Color get() = if (dark) Color(0xFF111111) else Color(0xFFF5F5F5)
        /** The one colour in the app: a confirmed connection (asked for by the user). */
        val Positive: Color get() = if (dark) Color(0xFF3DD68C) else Color(0xFF1E8E4E)
    }

    object Fonts {
        /** Windows' own standard font everywhere (Segoe UI, with the system's Thai and Japanese fonts), as on the phone. */
        val body: FontFamily = FontFamily.Default
        val display: FontFamily = FontFamily.Default
        val mono: FontFamily = FontFamily.Default
    }

    val cardRadius = 22.dp
    val minTouch = 48.dp

    // The rest of the Android tokens, used by the screens ported from the app (desktop/.../port).
    object Space {
        val xs = 4.dp
        val s = 8.dp
        val m = 16.dp
        val l = 24.dp
        val xl = 32.dp
    }

    object Radius {
        val card = 22.dp
        val button = 32.dp
        val chip = 999.dp
    }

    val border = 1.dp

    object Motion {
        const val fast = 140
        const val normal = 260
        const val slow = 420
    }
}

/** A font bundled as a resource (the Android app's res/font is on the classpath). */
fun resourceFont(file: String, weight: FontWeight = FontWeight.Normal) =
    Font(identity = "$file-${weight.weight}", data = Thread.currentThread().contextClassLoader.getResourceAsStream(file)!!.use { it.readBytes() }, weight = weight)

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

private val body = Tokens.Fonts.body
private val typography = Typography(
    headlineSmall = TextStyle(fontFamily = Tokens.Fonts.display, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontFamily = Tokens.Fonts.display, fontSize = 30.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
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

@Composable
fun MimikopiTheme(dark: Boolean, content: @Composable () -> Unit) {
    remember(dark) { Tokens.dark = dark; dark }
    MaterialTheme(
        colorScheme = colors(dark), typography = typography,
        shapes = Shapes(small = RoundedCornerShape(32.dp), medium = RoundedCornerShape(Tokens.cardRadius), large = RoundedCornerShape(Tokens.cardRadius)),
        content = content,
    )
}
