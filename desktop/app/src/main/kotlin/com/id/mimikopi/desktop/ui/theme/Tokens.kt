package com.id.mimikopi.desktop.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The single design-token file, the same values as the phone's (Android ui/theme/Tokens.kt). Look: Design J
 * "มินิมอลกลมกล่อม" (docs/design-J): a creamy pink background, white cards lifted by soft shadows instead of lines, one
 * dusty-rose accent for the main action and what is current, and equal-lightness pastels per skill used only as fills.
 * Right and wrong are told by words, marks and the accent, never by green and red.
 */
object Tokens {
    /** Which palette is shown: the light one (the design) or the dark one. Snapshot state, so screens redraw on change. */
    var dark: Boolean by mutableStateOf(false)

    object Palette {
        val Paper: Color get() = if (dark) Color(0xFF141113) else Color(0xFFF8F3F1)
        val Ink: Color get() = if (dark) Color(0xFFF6EEF1) else Color(0xFF2E2428)
        val InkMuted: Color get() = if (dark) Color(0xFFB3A5AB) else Color(0xFF7A6A70)
        val Dim: Color get() = if (dark) Color(0xFF85777D) else Color(0xFFB9A9AE)
        /** Rails of segmented controls and progress, empty slots. */
        val Track: Color get() = if (dark) Color(0xFF2C2327) else Color(0xFFEFE5E2)
        val Faint: Color get() = Track
        val Line: Color get() = Track
        val Hair: Color get() = if (dark) Color(0xFF241D20) else Color(0xFFF2E9E6)
        val OnInk: Color get() = Paper
        /** Cards, secondary buttons, the sidebar. */
        val Group: Color get() = if (dark) Color(0xFF1E181B) else Color(0xFFFFFFFF)
        val Raised: Color get() = Group
        val Accent: Color get() = if (dark) Color(0xFFE29AB8) else Color(0xFFA8577A)
        val OnAccent: Color get() = if (dark) Color(0xFF2E2428) else Color(0xFFFFFFFF)
        val AccentDeep: Color get() = if (dark) Color(0xFFF3C6D8) else Color(0xFF8A4363)
        val AccentSoft: Color get() = if (dark) Color(0xFF3A2530) else Color(0xFFF3DDE5)
        val AccentMid: Color get() = if (dark) Color(0xFF6A3E52) else Color(0xFFE7BFCF)
        val AccentMidStrong: Color get() = if (dark) Color(0xFF9A5A77) else Color(0xFFD08FAA)
        val Pink: Color get() = Color(0xFFE8899E)
        val Yellow: Color get() = Color(0xFFF2BE52)
        /** Played right: the accent with a tick. Played wrong: ink with a cross. Early or late: warm brown with an arrow. */
        val Correct: Color get() = Accent
        val CorrectSoft: Color get() = AccentSoft
        val Wrong: Color get() = Ink
        val Timing: Color get() = if (dark) Color(0xFFE2B98C) else Color(0xFF8A5A2B)
        /** A connected MIDI piano: the one colour outside the pink family. */
        val Positive: Color get() = Color(0xFF5E9E7E)
    }

    data class SkillColor(val fill: Color, val ink: Color)

    object Skill {
        val Rhythm get() = if (dark) SkillColor(Color(0xFF3A2724), Color(0xFFF0C2B8)) else SkillColor(Color(0xFFF6E3DF), Color(0xFF8A4B42))
        val Reading get() = if (dark) SkillColor(Color(0xFF30263A), Color(0xFFD8BFE6)) else SkillColor(Color(0xFFEEE2F1), Color(0xFF6E4A80))
        val Ear get() = if (dark) SkillColor(Color(0xFF382C1F), Color(0xFFEACBA6)) else SkillColor(Color(0xFFF5E7D8), Color(0xFF8A5A2B))
        val Chords get() = if (dark) SkillColor(Color(0xFF203029), Color(0xFFB5D6C6)) else SkillColor(Color(0xFFDFEAE4), Color(0xFF3F6B5C))
        val Keys get() = if (dark) SkillColor(Color(0xFF222A38), Color(0xFFBCC8E2)) else SkillColor(Color(0xFFE3E8F2), Color(0xFF4A5D80))
    }

    /** The answer keyboard: a dark case, white keys fading at the bottom, graded black keys, a felt line on top. */
    object Piano {
        val Case = Color(0xFF3A2F33)
        val BlackTop = Color(0xFF4A3D42)
        val BlackBottom = Color(0xFF2A2124)
        val WhiteTop = Color(0xFFFFFFFF)
        val WhiteBottom = Color(0xFFF6EFED)
        val Felt: Color get() = Palette.Accent
    }

    object Fonts {
        private fun font(file: String, weight: FontWeight) = androidx.compose.ui.text.platform.Font(
            identity = file,
            data = Tokens::class.java.classLoader.getResourceAsStream("fonts/$file")!!.use { it.readBytes() },
            weight = weight,
        )

        /** Thai and Latin: IBM Plex Sans Thai Looped, weights 400 to 700. */
        val body: FontFamily = FontFamily(
            font("plex_thai_looped_regular.ttf", FontWeight.Normal),
            font("plex_thai_looped_medium.ttf", FontWeight.Medium),
            font("plex_thai_looped_semibold.ttf", FontWeight.SemiBold),
            font("plex_thai_looped_bold.ttf", FontWeight.Bold),
        )
        val display: FontFamily = body
        val mono: FontFamily = body
        /** ドレミ and the name ミミコピ: Zen Maru Gothic Bold (kana only). */
        val kana: FontFamily = FontFamily(font("zen_maru_gothic_bold.ttf", FontWeight.Bold))
    }

    object Space {
        val xs = 4.dp
        val s = 8.dp
        val m = 16.dp
        val l = 24.dp
        val xl = 32.dp
    }

    object Radius {
        val card = 32.dp
        val cardSmall = 26.dp
        val box = 20.dp
        val button = 28.dp
        val chip = 999.dp
    }

    val border = 1.dp

    /** Soft shadows instead of borders. */
    object Elevation {
        val card = 10.dp
        val cardSmall = 6.dp
        val raised = 14.dp
        val keyboard = 12.dp
        val tint = Color(0xFF78505C)
    }

    /** Motion: short, eased, consistent. */
    object Motion {
        const val fast = 140
        const val normal = 260
        const val slow = 420
    }

    /** Minimum target for clicks and taps (the screen may be a touch screen on a music stand). */
    val minTouch = 56.dp
}
