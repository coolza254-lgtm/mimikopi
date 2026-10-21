package com.id.mimikopi.ui.reader

import com.id.mimikopi.core.playalong.PlayMode

/** A short corrective run. Its temporary tempo must not be overwritten by the piece's ramp. */
internal fun ResultAction.prepare(ui: ReaderUi, measures: Int): ReaderUi {
    require(measures > 0)
    val reset = ui.copy(
        phase = Phase.IDLE, result = null, results = emptyMap(), wrongMarks = emptyList(), fallingFeedback = emptyList(),
        pendingSectionStart = null, message = null,
        options = ui.options.copy(useRamp = false),
    )
    return when (this) {
        is ResultAction.Loop -> {
            val first = from.coerceIn(0, measures - 1)
            reset.copy(section = first..to.coerceIn(first, measures - 1), bpm = bpm.coerceAtLeast(30))
        }
        is ResultAction.Hands -> reset.copy(options = reset.options.copy(hands = choice), bpm = bpm.coerceAtLeast(30))
        is ResultAction.Tempo -> reset.copy(options = reset.options.copy(mode = PlayMode.TEMPO), bpm = bpm.coerceAtLeast(30))
    }
}
