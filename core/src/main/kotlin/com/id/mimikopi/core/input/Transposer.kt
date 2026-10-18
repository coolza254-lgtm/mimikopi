package com.id.mimikopi.core.input

/**
 * The piano page's transpose for the app's sound: which pitch is sounding for each played key, so a key released
 * after the transpose changed still silences the pitch it started. Keys, judging and highlights keep the played note.
 */
class Transposer {
    @Volatile var semitones: Int = 0
        set(value) { field = value.coerceIn(-RANGE, RANGE) }
    private val sounding = IntArray(128) { -1 }

    /** The pitch to sound for a key struck now (remembered for its release), or null when it falls off the keyboard. */
    @Synchronized
    fun press(note: Int): Int? {
        if (note !in 0..127) return null
        val n = note + semitones
        if (n !in 0..127) return null
        sounding[note] = n
        return n
    }

    /** The pitch to silence for a released key: the one it started, or null when it started none. */
    @Synchronized
    fun release(note: Int): Int? {
        if (note !in 0..127) return null
        val n = sounding[note]
        sounding[note] = -1
        return n.takeIf { it >= 0 }
    }

    /** Every pitch still sounding, forgotten (to silence them all). */
    @Synchronized
    fun clear(): List<Int> = sounding.filter { it >= 0 }.also { sounding.fill(-1) }

    companion object {
        const val RANGE = 12
    }
}
