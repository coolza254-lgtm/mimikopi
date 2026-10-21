package com.id.mimikopi.ui.ear

import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarOption
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.FunctionalEar

internal data class EarMidiChoice(val midi: Int, val option: EarOption) {
    val keyName: String get() = FunctionalEar.letter(midi, 0)
}

/** Degree/bass answers remain musical pitches. Other choice questions use explicit, stable answer keys. */
internal fun EarQuestion.midiChoices(): List<EarMidiChoice> {
    if (kind == EarKind.DEGREE || kind == EarKind.BASS) return emptyList()
    val keys = listOf(60, 62, 64, 65, 67, 69, 71, 61, 63, 66, 68, 70)
    require(options.size <= keys.size)
    return options.mapIndexed { i, option -> EarMidiChoice(keys[i], option) }
}

internal fun EarQuestion.midiChoice(midi: Int): String? {
    if (kind == EarKind.DEGREE || kind == EarKind.BASS) {
        val prefix = if (kind == EarKind.DEGREE) "deg" else "bass"
        val id = "$prefix:${Math.floorMod(midi - base, 12)}"
        return id.takeIf { key -> options.any { it.id == key } }
    }
    return midiChoices().firstOrNull { Math.floorMod(it.midi, 12) == Math.floorMod(midi, 12) }?.option?.id
}
