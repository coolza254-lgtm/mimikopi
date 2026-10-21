package com.id.mimikopi.desktop.input

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.desktop.audio.NoteOutput
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InputHubTest {
    private class Recorder : NoteOutput {
        val on = mutableListOf<Int>()
        override fun noteOn(note: Int, velocity: Int) { on += note }
        override fun noteOff(note: Int) = Unit
        override fun sustain(down: Boolean) = Unit
    }

    private fun press(hub: InputHub, note: Int, source: InputSource) {
        hub.dispatch(InputEvent.NoteOn(note, 90, System.nanoTime(), source))
        hub.dispatch(InputEvent.NoteOff(note, System.nanoTime(), source))
    }

    @Test
    fun midiUsesThePianosOwnSpeakerUnlessAskedWhileClicksAndTypingSound() {
        val r = Recorder()
        val hub = InputHub(r)
        press(hub, 60, InputSource.USB_MIDI)
        press(hub, 62, InputSource.COMPUTER_KEYS)
        press(hub, 64, InputSource.TOUCH)
        assertEquals(listOf(62, 64), r.on)
        hub.soundThrough = true
        press(hub, 65, InputSource.USB_MIDI)
        assertEquals(listOf(62, 64, 65), r.on)
    }

    @Test
    fun earAnswersNeverSoundWhileSilencedButStillReachJudging() {
        val r = Recorder()
        val hub = InputHub(r)
        hub.soundThrough = true
        val seen = mutableListOf<Int>()
        val lease = hub.silenceInput()
        for (s in InputSource.entries) {
            hub.dispatch(InputEvent.NoteOn(60, 90, System.nanoTime(), s))
            seen += hub.pressed.value
            hub.dispatch(InputEvent.NoteOff(60, System.nanoTime(), s))
        }
        assertTrue(r.on.isEmpty(), "no source sounds during an ear round")
        assertEquals(InputSource.entries.size, seen.size)
        lease.close()
        lease.close()
        press(hub, 67, InputSource.COMPUTER_KEYS)
        assertEquals(listOf(67), r.on, "sound returns after the round, and a double close is harmless")
    }

    @Test
    fun computerKeyboardMapsTwoRowsAndShiftsOctaves() {
        val low = ComputerKeyboard.DEFAULT_LOW_C
        assertEquals(low, ComputerKeyboard.noteFor(KeyEvent.VK_Z, low))
        assertEquals(low + 1, ComputerKeyboard.noteFor(KeyEvent.VK_S, low))
        assertEquals(low + 12, ComputerKeyboard.noteFor(KeyEvent.VK_Q, low))
        assertEquals(low + 28, ComputerKeyboard.noteFor(KeyEvent.VK_P, low))
        assertNull(ComputerKeyboard.noteFor(KeyEvent.VK_SPACE, low), "Space stops, it never plays")
        assertNull(ComputerKeyboard.noteFor(KeyEvent.VK_ESCAPE, low))
        assertEquals(low + 12, ComputerKeyboard.shiftOctave(low, up = true))
        assertEquals(24, ComputerKeyboard.shiftOctave(24, up = false))
        assertEquals("Z", ComputerKeyboard.labelFor(low, low))
    }
}
