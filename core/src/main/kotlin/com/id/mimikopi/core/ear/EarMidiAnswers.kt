package com.id.mimikopi.core.ear

/** An answer card played from a piano key (shared by the phone and the computer). */
data class EarMidiChoice(val midi: Int, val option: EarOption) {
    val keyName: String get() = FunctionalEar.letter(midi, 0)
}

/** Degree/bass answers remain musical pitches. Other choice questions use explicit, stable answer keys. */
object EarMidiAnswers {
    private val KEYS = listOf(60, 62, 64, 65, 67, 69, 71, 61, 63, 66, 68, 70)

    fun choices(q: EarQuestion): List<EarMidiChoice> {
        if (q.kind == EarKind.DEGREE || q.kind == EarKind.BASS) return emptyList()
        require(q.options.size <= KEYS.size)
        return q.options.mapIndexed { i, option -> EarMidiChoice(KEYS[i], option) }
    }

    /** The answer id a key means for [q], or null when that key answers nothing. */
    fun choice(q: EarQuestion, midi: Int): String? {
        if (q.kind == EarKind.DEGREE || q.kind == EarKind.BASS) {
            val prefix = if (q.kind == EarKind.DEGREE) "deg" else "bass"
            val id = "$prefix:${Math.floorMod(midi - q.base, 12)}"
            return id.takeIf { key -> q.options.any { it.id == key } }
        }
        return choices(q).firstOrNull { Math.floorMod(it.midi, 12) == Math.floorMod(midi, 12) }?.option?.id
    }
}
