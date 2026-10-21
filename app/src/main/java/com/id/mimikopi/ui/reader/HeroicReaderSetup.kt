package com.id.mimikopi.ui.reader

import com.id.mimikopi.core.playalong.HeroicPlan

/** Apply a target after loading preferences, so saved practice options cannot silently alter its criteria. */
internal fun ReaderUi.withStudyTarget(t: HeroicPlan.Target): ReaderUi = copy(
    section = t.from..t.to, bpm = t.bpm, targetBpm = t.bpm, showNames = false,
    options = options.copy(mode = t.mode, hands = HandChoice.entries.first { it.hands == t.hands },
        useRamp = false, falling = false, hideNotes = false, playOtherHand = false, loopListen = false, metronome = true),
    message = "แผน Heroic · ${t.label} · ${t.goal}",
)
