package com.id.mimikopi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.id.mimikopi.R

/**
 * The single design-token file. Everything visual (colour, type, spacing, radius) comes from here so the
 * design can be redone without touching screens. Values follow the M13 redesign (Claude Design canvas
 * "ミミコピ หน้าตาใหม่"): cream notebook paper, dusty rose, warm mustard, dark warm ink.
 */
object Tokens {
    object Palette {
        val Paper = Color(0xFFFFF6E9) // cream paper background
        val Card = Color(0xFFFFFDF8) // slightly lighter page for cards
        val Dot = Color(0xFFEADCC8) // notebook dot grid
        val Rose = Color(0xFFE8A0A8) // primary (fills and large shapes only)
        val RoseSoft = Color(0xFFF8DADD)
        val RoseDeep = Color(0xFFA3485A) // rose for text and small marks: ~5.3:1 on Paper
        val RoseInk = Color(0xFF7E3242) // text on RoseSoft
        val Mustard = Color(0xFFF2C14E) // accent
        val MustardSoft = Color(0xFFFBE7B0)
        val MustardInk = Color(0xFF5C4510) // text on Mustard / MustardSoft
        val Ink = Color(0xFF3B2F2F) // body text: ~11:1 on Paper
        val InkMuted = Color(0xFF6E5E58) // secondary text: ~5.6:1 on Paper
        val Line = Color(0xFFEADCC8) // hairlines, card borders
        val Track = Color(0xFFF3EADB) // empty part of bars
        val Error = Color(0xFFB33A3A)
        val Good = Color(0xFF3E7D5A) // correct: ~4.9:1 on Paper
        val GoodSoft = Color(0xFFE4F0E8)
        val GoodInk = Color(0xFF2F6B4B)
        val Off = Color(0xFFA35A00) // early/late note: ~5:1 on Paper
        val ChartMark = Color(0xFFA3485A) // single-series chart marks: deep rose
    }

    object Fonts {
        /** Body and UI text: Thai looped (traditional, easy for a Thai reader) with Latin. */
        val body = FontFamily(
            Font(R.font.plex_thai_regular, FontWeight.Normal),
            Font(R.font.plex_thai_semibold, FontWeight.SemiBold),
            Font(R.font.plex_thai_bold, FontWeight.Bold),
        )

        /** Wordmark, big numbers and lesson ids (subset: Latin, digits, ミコピ). */
        val display = FontFamily(
            Font(R.font.zen_maru_bold, FontWeight.Bold),
            Font(R.font.zen_maru_black, FontWeight.Black),
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
        val card = 20.dp
        val button = 16.dp
        val chip = 999.dp
    }

    val border = 1.5.dp

    /** Minimum touch target; larger than Material's 48dp because the phone sits on a music stand. */
    val minTouch = 56.dp
}
