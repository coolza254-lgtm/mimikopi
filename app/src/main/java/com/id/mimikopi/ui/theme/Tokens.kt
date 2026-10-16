package com.id.mimikopi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.id.mimikopi.R

/**
 * The single design-token file. Everything visual (colour, type, spacing, radius) comes from here so the
 * design can be redone without touching screens. M15 look (Claude Design canvas "ミミコピ OLED ขาวดำ"): pure
 * black (OLED pixels off) and white only; importance comes from white fills, grey levels, line weight and space.
 * Right and wrong are told by words, marks and haptics, never by colour.
 */
object Tokens {
    object Palette {
        val Paper = Color(0xFF000000) // background: OLED black
        val Ink = Color(0xFFFFFFFF) // text, filled buttons, the one thing to look at
        val InkMuted = Color(0xFF9E9E9E) // secondary text: ~7.6:1 on black
        val Dim = Color(0xFF6E6E6E) // done or upcoming items, indices: large or non-essential text only
        val Faint = Color(0xFF4A4A4A) // decorative marks (played notes, dashed outlines)
        val Line = Color(0xFF2B2B2B) // card borders, empty part of bars
        val Hair = Color(0xFF1F1F1F) // list dividers
        /** Text and icons on a white fill. */
        val OnInk = Color(0xFF000000)
    }

    object Fonts {
        /** Body and UI text: IBM Plex Sans Thai (clean, modern Thai with Latin). */
        val body = FontFamily(
            Font(R.font.sans_regular, FontWeight.Normal),
            Font(R.font.sans_medium, FontWeight.Medium),
            Font(R.font.sans_semibold, FontWeight.SemiBold),
            Font(R.font.sans_semibold, FontWeight.Bold),
        )

        /** Headings and titles: Noto Serif Thai, like a printed book. */
        val display = FontFamily(
            Font(R.font.serif_medium, FontWeight.Medium),
            Font(R.font.serif_semibold, FontWeight.SemiBold),
            Font(R.font.serif_semibold, FontWeight.Bold),
            Font(R.font.serif_semibold, FontWeight.Black),
        )

        /** Numbers, step ids and note names: IBM Plex Mono, so they line up. */
        val mono = FontFamily(
            Font(R.font.mono_light, FontWeight.Light),
            Font(R.font.mono_regular, FontWeight.Normal),
            Font(R.font.mono_medium, FontWeight.Medium),
            Font(R.font.mono_medium, FontWeight.SemiBold),
            Font(R.font.mono_medium, FontWeight.Bold),
        )
    }

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

    /** No shadows on black: surfaces are told apart by hairlines. */
    object Elevation {
        val card = 0.dp
        val raised = 0.dp
    }

    /** Motion: short, eased, consistent. */
    object Motion {
        const val fast = 140
        const val normal = 260
        const val slow = 420
    }

    /** Minimum touch target; larger than Material's 48dp because the phone sits on a music stand. */
    val minTouch = 56.dp
}
