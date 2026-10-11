package com.id.mimikopi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The single design-token file. Everything visual (colour, spacing, radius) comes from here so the
 * design can be redone later without touching screens. Brand colours were sampled from
 * docs/brand/icon-source.png and wordmark-source.png.
 */
object Tokens {
    object Palette {
        val Paper = Color(0xFFFEF5E6) // cream paper background
        val Card = Color(0xFFFFFBF4) // slightly lighter page for cards
        val Rose = Color(0xFFE6889E) // primary (decorative + large shapes only)
        val RoseSoft = Color(0xFFF6D3DB)
        val Mustard = Color(0xFFF3BE52) // accent
        val MustardSoft = Color(0xFFFBE7BC)
        val Ink = Color(0xFF3A3030) // body text: ~11:1 on Paper
        val InkMuted = Color(0xFF6B5E5E) // secondary text: ~5.6:1 on Paper
        val Line = Color(0xFFEAD9C6) // hairlines, dividers
        val Error = Color(0xFFB3261E)
        val Good = Color(0xFF2E7D4F) // correct note: ~5:1 on Paper
        val Off = Color(0xFFA35A00) // early/late note: ~5:1 on Paper
        val ChartMark = Color(0xFFA8475F) // single-series chart marks: deep rose, ~5:1 on Paper
    }

    object Space {
        val xs = 4.dp
        val s = 8.dp
        val m = 16.dp
        val l = 24.dp
        val xl = 32.dp
    }

    object Radius {
        val card = 20.dp
        val button = 16.dp
    }

    /** Minimum touch target; larger than Material's 48dp because the phone sits on a music stand. */
    val minTouch = 56.dp
}
