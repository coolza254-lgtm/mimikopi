package com.id.mimikopi.ui.reader

import com.id.mimikopi.core.playalong.HeroicPlan
import com.id.mimikopi.core.playalong.PlayMode
import kotlin.test.*
import org.junit.Test

class HeroicReaderSetupTest {
    @Test fun everyTargetOverridesConflictingSavedOptionsButKeepsKeyboardAndPagePreferences() {
        val previous = ReaderUi(bpm = 150, section = 0..0, showNames = true,
            options = ReaderOptions(mode = PlayMode.TEMPO, hands = HandChoice.BOTH, falling = true,
                hideNotes = true, loopListen = true, playOtherHand = true, useRamp = true, metronome = false,
                showKeyboard = true, pageView = false, paperSize = false))
        for (t in HeroicPlan.targets) {
            val ui = previous.withStudyTarget(t)
            assertEquals(t.from..t.to, ui.section)
            assertEquals(t.bpm, ui.bpm)
            assertEquals(t.bpm, ui.targetBpm)
            assertEquals(t.hands, ui.options.hands.hands)
            assertEquals(t.mode, ui.options.mode)
            assertFalse(ui.showNames || ui.options.falling || ui.options.hideNotes || ui.options.loopListen || ui.options.playOtherHand || ui.options.useRamp)
            assertTrue(ui.options.metronome && ui.options.showKeyboard)
            assertFalse(ui.options.pageView || ui.options.paperSize)
        }
    }
}
