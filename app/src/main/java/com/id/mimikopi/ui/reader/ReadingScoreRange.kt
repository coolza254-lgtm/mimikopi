package com.id.mimikopi.ui.reader

import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.Score

/** Use the whole selected part, never the next target, to choose the phone's available octave pages. */
internal fun readingInputRange(score: Score, hands: Set<Hand>): IntRange {
    val selected = score.events.filter { it.hand in hands }.flatMap { it.pitches }.map { it.midi }
    val notes = selected.ifEmpty { score.events.flatMap { it.pitches }.map { it.midi } }
    return if (notes.isEmpty()) 60..72 else notes.min()..notes.max()
}
