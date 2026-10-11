package com.id.mimikopi.core.notation

import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import kotlinx.serialization.Serializable

/** Clefs used so far. [middleLine] is the pitch on the staff's middle (third) line. */
@Serializable
enum class Clef(val middleLine: SpelledPitch) {
    TREBLE(SpelledPitch(Letter.B, 0, 4)),
    BASS(SpelledPitch(Letter.D, 0, 3)),
}

/**
 * Staff geometry in "staff steps": 0 = middle line, +1 = next space up, +2 = next line up.
 * The five lines are at -4, -2, 0, 2, 4. Pure layout decisions; drawing lives in the UI.
 */
object Staff {
    const val TOP_LINE = 4
    const val BOTTOM_LINE = -4

    fun position(pitch: SpelledPitch, clef: Clef): Int = pitch.diatonicIndex - clef.middleLine.diatonicIndex

    /** Ledger line positions needed for a note at [position] (lines only, nearest first). */
    fun ledgerLines(position: Int): List<Int> = when {
        position >= TOP_LINE + 2 -> (TOP_LINE + 2..position step 2).toList()
        position <= BOTTOM_LINE - 2 -> (BOTTOM_LINE - 2 downTo position step 2).toList()
        else -> emptyList()
    }

    /** Standard rule: notes on or above the middle line take a down stem. */
    fun stemUp(position: Int): Boolean = position < 0

    /** True when the note sits on a line (even position), false when in a space. */
    fun onLine(position: Int): Boolean = position % 2 == 0
}
