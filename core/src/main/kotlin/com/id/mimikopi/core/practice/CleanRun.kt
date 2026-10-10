package com.id.mimikopi.core.practice

import com.id.mimikopi.core.config.CleanRunConfig

/** Outcome counts of one play-through of an exercise or section. */
data class RunSummary(
    val expectedNotes: Int,
    val correctNotes: Int,
    val notesWithinTiming: Int,
    /** Stops longer than one beat. */
    val stumbles: Int,
) {
    init {
        require(expectedNotes > 0) { "a run needs at least one expected note" }
        require(correctNotes in 0..expectedNotes && notesWithinTiming in 0..expectedNotes && stumbles >= 0)
    }

    val noteAccuracy: Double get() = correctNotes.toDouble() / expectedNotes
    val fractionWithinTiming: Double get() = notesWithinTiming.toDouble() / expectedNotes
}

/** "Clean run" from learning-system.md 6.1. */
fun RunSummary.isClean(config: CleanRunConfig = CleanRunConfig()): Boolean =
    noteAccuracy >= config.minNoteAccuracy &&
        fractionWithinTiming >= config.minFractionWithinTiming &&
        stumbles <= config.maxStumbles
