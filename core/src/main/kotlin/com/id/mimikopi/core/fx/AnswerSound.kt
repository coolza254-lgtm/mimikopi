package com.id.mimikopi.core.fx

/**
 * The small sounds for answers (user-approved design, 2026-10-23): soft, short and quieter than the piano. Played
 * on the app's piano so no extra sound files ship. Notes are (MIDI note, start ms, length ms).
 */
object AnswerSound {
    const val VELOCITY = 34

    /** "Ting": two notes up, high and light. */
    val CORRECT = listOf(Triple(88, 0, 90), Triple(95, 70, 140))

    /** "Tup": one low, soft note, never a buzzer. */
    val WRONG = listOf(Triple(45, 0, 120))

    /** End of a round: ド ミ ソ once. */
    val DONE = listOf(Triple(72, 0, 120), Triple(76, 110, 120), Triple(79, 220, 260))

    /** The settings row (both apps): "0" turns the answer sounds off. */
    const val KEY = "fx.sound"
}
