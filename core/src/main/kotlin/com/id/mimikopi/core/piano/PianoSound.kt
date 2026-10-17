package com.id.mimikopi.core.piano

import kotlin.math.pow

/**
 * The app piano's sound as set on the piano page, each 0..1: volume, room (reverb) and warmth. Volume 0.6 is the
 * engine's usual level; the slider spans -18 dB to +12 dB around it (the output limiter keeps the top clean).
 * Stored in one settings row as "volume|reverb|warmth".
 */
data class PianoSound(val volume: Float = DEFAULT_VOLUME, val reverb: Float = 0.3f, val warmth: Float = 0.5f) {
    /** Loudness against the usual level, in dB. */
    val db: Float get() = volume.coerceIn(0f, 1f) * 30f - 18f
    /** The same as a linear factor (1 at the usual level). */
    val factor: Float get() = 10f.pow(db / 20f)

    fun encode(): String = "$volume|$reverb|$warmth"

    companion object {
        const val DEFAULT_VOLUME = 0.6f

        fun parse(text: String?): PianoSound {
            val v = text?.split('|')?.map { it.toFloatOrNull() } ?: return PianoSound()
            if (v.size != 3 || v.any { it == null }) return PianoSound()
            return PianoSound(v[0]!!.coerceIn(0f, 1f), v[1]!!.coerceIn(0f, 1f), v[2]!!.coerceIn(0f, 1f))
        }
    }
}
