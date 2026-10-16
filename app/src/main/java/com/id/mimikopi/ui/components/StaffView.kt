package com.id.mimikopi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.id.mimikopi.R
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.core.notation.Staff
import com.id.mimikopi.ui.theme.Tokens

/** Bravura (SMuFL, SIL OFL 1.1): glyphs follow standard engraving; decoration never changes them. */
val Bravura = FontFamily(Font(R.font.bravura))

private object Smufl {
    const val G_CLEF = ""
    const val F_CLEF = ""
    const val NOTEHEAD_WHOLE = ""
    const val SHARP = ""
    const val FLAT = ""

    // From Bravura's metadata (in staff spaces): engraving defaults used for the drawing below.
    const val STAFF_LINE = 0.13f
    const val LEDGER_LINE = 0.16f
    const val LEDGER_EXTENSION = 0.4f
    const val WHOLE_NOTE_WIDTH = 1.688f
}

/**
 * One staff with a clef and (optionally) one whole note: the reading prompt for note drills.
 * Sizes derive from the staff space so it scales with the box; positions come from core's [Staff].
 */
@Composable
fun StaffView(clef: Clef, note: SpelledPitch?, modifier: Modifier = Modifier, ink: Color = Tokens.Palette.Ink) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        // Room for ledger lines: staff (4 spaces) plus 3 spaces above and below.
        val space = minOf(size.height / 10f, size.width / 9f)
        val middleY = size.height / 2f
        fun y(position: Int) = middleY - position * space / 2f
        val left = space * 0.5f
        val right = size.width - space * 0.5f
        for (line in listOf(-4, -2, 0, 2, 4)) {
            drawLine(ink, Offset(left, y(line)), Offset(right, y(line)), strokeWidth = Smufl.STAFF_LINE * space)
        }
        val clefGlyph = if (clef == Clef.TREBLE) Smufl.G_CLEF else Smufl.F_CLEF
        // SMuFL: the G clef's origin sits on the G line (position -2), the F clef's on the F line (+2).
        glyph(measurer, clefGlyph, left + space * 0.4f, y(if (clef == Clef.TREBLE) -2 else 2), space, ink)
        if (note != null) {
            val pos = Staff.position(note, clef)
            val x = left + (right - left) * 0.58f
            val width = Smufl.WHOLE_NOTE_WIDTH * space
            for (ledger in Staff.ledgerLines(pos)) {
                drawLine(ink, Offset(x - Smufl.LEDGER_EXTENSION * space, y(ledger)), Offset(x + width + Smufl.LEDGER_EXTENSION * space, y(ledger)),
                    strokeWidth = Smufl.LEDGER_LINE * space)
            }
            when (note.accidental) {
                1 -> glyph(measurer, Smufl.SHARP, x - space * 1.4f, y(pos), space, ink)
                -1 -> glyph(measurer, Smufl.FLAT, x - space * 1.3f, y(pos), space, ink)
            }
            glyph(measurer, Smufl.NOTEHEAD_WHOLE, x, y(pos), space, ink)
        }
    }
}

/** Draws a SMuFL glyph with its origin (baseline, left) at (x, baselineY). Font size = 4 staff spaces. */
private fun DrawScope.glyph(measurer: TextMeasurer, text: String, x: Float, baselineY: Float, space: Float, color: Color) {
    val style = TextStyle(fontFamily = Bravura, fontSize = (space * 4f).toSp(), color = color)
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(x, baselineY - layout.firstBaseline))
}

/**
 * One staff with a row of whole notes (interval reading). Notes in [highlight] are drawn in rose (the pair being
 * asked); the first [played] notes turn green as they are played.
 */
@Composable
fun StaffSequenceView(clef: Clef, notes: List<SpelledPitch>, highlight: Set<Int>, played: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val ink = Tokens.Palette.Ink
    Canvas(modifier) {
        val space = minOf(size.height / 11f, size.width / (5f + notes.size * 3.2f))
        val middleY = size.height / 2f
        fun y(position: Int) = middleY - position * space / 2f
        val left = space * 0.5f
        val right = size.width - space * 0.5f
        for (line in listOf(-4, -2, 0, 2, 4)) {
            drawLine(ink, Offset(left, y(line)), Offset(right, y(line)), strokeWidth = Smufl.STAFF_LINE * space)
        }
        glyph(measurer, if (clef == Clef.TREBLE) Smufl.G_CLEF else Smufl.F_CLEF, left + space * 0.4f, y(if (clef == Clef.TREBLE) -2 else 2), space, ink)
        val start = left + space * 4.5f
        val step = (right - start - space * 2f) / maxOf(1, notes.size - 1).toFloat()
        notes.forEachIndexed { i, p ->
            val pos = Staff.position(p, clef)
            val x = start + i * step
            val color = when {
                i < played -> Tokens.Palette.Dim
                i in highlight -> Tokens.Palette.Ink
                else -> ink
            }
            val width = Smufl.WHOLE_NOTE_WIDTH * space
            for (ledger in Staff.ledgerLines(pos)) {
                drawLine(ink, Offset(x - Smufl.LEDGER_EXTENSION * space, y(ledger)), Offset(x + width + Smufl.LEDGER_EXTENSION * space, y(ledger)),
                    strokeWidth = Smufl.LEDGER_LINE * space)
            }
            glyph(measurer, Smufl.NOTEHEAD_WHOLE, x, y(pos), space, color)
        }
    }
}
