package com.id.mimikopi.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * The single design-token file. Everything visual (colour, type, spacing, radius) comes from here so the
 * design can be redone without touching screens. M15 look (Claude Design canvas "ミミコピ OLED ขาวดำ"): pure
 * black (OLED pixels off) and white only; importance comes from white fills, grey levels, line weight and space.
 * Right and wrong are told by words, marks and haptics, never by colour.
 */
object Tokens {
    /** Which palette is shown: OLED black (default) or the light one. Snapshot state, so screens redraw on change. */
    var dark: Boolean by mutableStateOf(true)

    object Palette {
        val Paper: Color get() = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF) // background: OLED black / paper white
        val Ink: Color get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF000000) // text, filled buttons, the one thing to look at
        val InkMuted: Color get() = if (dark) Color(0xFF9E9E9E) else Color(0xFF5E5E5E) // secondary text: ~7.6:1 / ~6.5:1
        val Dim: Color get() = if (dark) Color(0xFF6E6E6E) else Color(0xFF8A8A8A) // done or upcoming items, indices (large or non-essential)
        val Faint: Color get() = if (dark) Color(0xFF4A4A4A) else Color(0xFFBDBDBD) // decorative marks (played notes, dashed outlines)
        val Line: Color get() = if (dark) Color(0xFF2B2B2B) else Color(0xFFDDDDDD) // card borders, empty part of bars
        val Hair: Color get() = if (dark) Color(0xFF1F1F1F) else Color(0xFFEDEDED) // list dividers
        /** Text and icons on an [Ink] fill. */
        val OnInk: Color get() = Paper
        /** Grouped lists (rounded blocks of rows on the page), like the system settings. */
        val Group: Color get() = if (dark) Color(0xFF151515) else Color(0xFFF2F2F2)
        /** Dialogs and menus: one step off the background. */
        val Raised: Color get() = if (dark) Color(0xFF111111) else Color(0xFFF5F5F5)
        /** The one colour in the app: a confirmed connection (asked for by the user). */
        val Positive: Color get() = if (dark) Color(0xFF3DD68C) else Color(0xFF1E8E4E)
    }

    object Fonts {
        /**
         * The phone's own standard font everywhere (the user asked for a plain one): Thai, Latin and Japanese come from
         * the system, in the weights it has. The names stay so screens say what a text is for.
         */
        val body: FontFamily = FontFamily.Default
        val display: FontFamily = FontFamily.Default
        val mono: FontFamily = FontFamily.Default
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
