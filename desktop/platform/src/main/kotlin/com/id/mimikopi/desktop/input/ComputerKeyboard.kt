package com.id.mimikopi.desktop.input

import java.awt.event.KeyEvent

/**
 * Playing notes on the computer keyboard when no piano is connected. Two rows like a piano: the bottom letter row
 * (Z X C V B N M ,) is the white keys of the lower octave with S D G H J as its black keys; the top letter row
 * (Q W E R T Y U I O P) continues an octave up with 2 3 5 6 7 9 0 as black keys. [ and ] move both rows an octave.
 * Space and Esc are never notes: they stop a run.
 */
object ComputerKeyboard {
    /** Semitones above the lower row's C, by AWT key code. */
    private val OFFSETS: Map<Int, Int> = mapOf(
        KeyEvent.VK_Z to 0, KeyEvent.VK_S to 1, KeyEvent.VK_X to 2, KeyEvent.VK_D to 3, KeyEvent.VK_C to 4,
        KeyEvent.VK_V to 5, KeyEvent.VK_G to 6, KeyEvent.VK_B to 7, KeyEvent.VK_H to 8, KeyEvent.VK_N to 9,
        KeyEvent.VK_J to 10, KeyEvent.VK_M to 11, KeyEvent.VK_COMMA to 12, KeyEvent.VK_L to 13, KeyEvent.VK_PERIOD to 14,
        KeyEvent.VK_SEMICOLON to 15, KeyEvent.VK_SLASH to 16,
        KeyEvent.VK_Q to 12, KeyEvent.VK_2 to 13, KeyEvent.VK_W to 14, KeyEvent.VK_3 to 15, KeyEvent.VK_E to 16,
        KeyEvent.VK_R to 17, KeyEvent.VK_5 to 18, KeyEvent.VK_T to 19, KeyEvent.VK_6 to 20, KeyEvent.VK_Y to 21,
        KeyEvent.VK_7 to 22, KeyEvent.VK_U to 23, KeyEvent.VK_I to 24, KeyEvent.VK_9 to 25, KeyEvent.VK_O to 26,
        KeyEvent.VK_0 to 27, KeyEvent.VK_P to 28,
    )

    const val OCTAVE_DOWN = KeyEvent.VK_OPEN_BRACKET
    const val OCTAVE_UP = KeyEvent.VK_CLOSE_BRACKET
    const val DEFAULT_LOW_C = 48
    const val VELOCITY = 90

    fun noteFor(keyCode: Int, lowC: Int): Int? = OFFSETS[keyCode]?.let { lowC + it }?.takeIf { it in 21..108 }

    /** The key label for a note, for showing on the on-screen keys ("Z", "2"...), or null when no key plays it. */
    fun labelFor(note: Int, lowC: Int): String? {
        val offset = note - lowC
        val code = OFFSETS.entries.filter { it.value == offset }.minByOrNull { if (it.key in TOP_ROW) 1 else 0 }?.key ?: return null
        return KeyEvent.getKeyText(code)
    }

    private val TOP_ROW = setOf(KeyEvent.VK_Q, KeyEvent.VK_2, KeyEvent.VK_W, KeyEvent.VK_3, KeyEvent.VK_E)

    fun shiftOctave(lowC: Int, up: Boolean): Int = (lowC + if (up) 12 else -12).coerceIn(24, 84)
}
