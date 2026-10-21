package com.id.mimikopi.ui.ear

import com.id.mimikopi.core.ear.EarMidiAnswers
import com.id.mimikopi.core.ear.EarQuestion

/** Answer keys live in core so the phone and the computer map the same keys to the same answers. */
internal typealias EarMidiChoice = com.id.mimikopi.core.ear.EarMidiChoice

internal fun EarQuestion.midiChoices(): List<EarMidiChoice> = EarMidiAnswers.choices(this)

internal fun EarQuestion.midiChoice(midi: Int): String? = EarMidiAnswers.choice(this, midi)
