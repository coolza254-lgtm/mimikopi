package com.id.mimikopi.desktop.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * The single design-token file. Everything visual (colour, type, spacing, radius) comes from here so the
 * design can be redone without touching screens. Look (Claude Design canvas "Mimikopi ดีไซน์ใหม่"): minimal pastel
 * from the app icon, warm white paper, ink text, one deep pink for the main action, soft pink for what is selected.
 * Correct and wrong also show a mark (glow and check, a cross), never colour alone.
 */
object Tokens {
    /** Which palette is shown: the light pastel one (default) or the dark one. Snapshot state, so screens redraw on change. */
    var dark: Boolean by mutableStateOf(false)

    object Palette {
        val Paper: Color get() = if (dark) Color(0xFF141113) else Color(0xFFFFF9F7) // background: warm white / warm black
        val Ink: Color get() = if (dark) Color(0xFFF6EEF1) else Color(0xFF2D2226) // text and the one thing to look at
        val InkMuted: Color get() = if (dark) Color(0xFFB3A5AB) else Color(0xFF74636A) // secondary text: ~5.3:1 on paper
        val Dim: Color get() = if (dark) Color(0xFF85777D) else Color(0xFFA8969D) // done or upcoming items, indices (large or non-essential)
        val Faint: Color get() = if (dark) Color(0xFF4F4347) else Color(0xFFD9C5CB) // decorative marks (dashed outlines)
        val Line: Color get() = if (dark) Color(0xFF33292D) else Color(0xFFEAD3D9) // card borders, empty part of bars
        val Hair: Color get() = if (dark) Color(0xFF241D20) else Color(0xFFF6E8EC) // list dividers
        /** Text and icons on an [Ink] fill. */
        val OnInk: Color get() = Paper
        /** Grouped lists and cards (white on the warm paper, with a [Line] border). */
        val Group: Color get() = if (dark) Color(0xFF1E181B) else Color(0xFFFFFFFF)
        /** Dialogs and menus: one step off the background. */
        val Raised: Color get() = if (dark) Color(0xFF1E181B) else Color(0xFFFFFFFF)
        /** The main action (one per screen): deep pink from the icon, white text ~5:1. */
        val Accent: Color get() = if (dark) Color(0xFFF0A0B3) else Color(0xFFB94A64)
        val OnAccent: Color get() = if (dark) Color(0xFF2D2226) else Color(0xFFFFFFFF)
        /** What is selected or current: a soft pink fill under [Ink] text. */
        val AccentSoft: Color get() = if (dark) Color(0xFF3A2530) else Color(0xFFFCEEF1)
        /** Brand pink and yellow, for decoration and progress (never for text). */
        val Pink: Color get() = Color(0xFFE8899E)
        val Yellow: Color get() = Color(0xFFF2BE52)
        /** Played right: notes and keys glow green for a moment. Text in it passes 4.5:1. */
        val Correct: Color get() = if (dark) Color(0xFF5CC795) else Color(0xFF23875E)
        val CorrectSoft: Color get() = if (dark) Color(0xFF1C3A2C) else Color(0xFFE6F5EE)
        /** Played wrong: a clear red cross. */
        val Wrong: Color get() = if (dark) Color(0xFFFF6B6E) else Color(0xFFE5484D)
        /** Early or late beyond the window: amber arrows. */
        val Timing: Color get() = if (dark) Color(0xFFF2BE52) else Color(0xFFB27A00)
        /** A confirmed connection. */
        val Positive: Color get() = Correct
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

    /** No shadows: surfaces are told apart by hairlines. */
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

    /** Minimum target for clicks and taps (the screen may be a touch screen on a music stand). */
    val minTouch = 56.dp
}
