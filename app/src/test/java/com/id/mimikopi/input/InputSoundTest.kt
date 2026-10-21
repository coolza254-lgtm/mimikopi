package com.id.mimikopi.input

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.piano.NoteOutput
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import kotlin.test.*

class InputSoundTest {
    private class Output : NoteOutput {
        val events = mutableListOf<String>()
        override fun noteOn(note: Int, velocity: Int) { events += "on:$note" }
        override fun noteOff(note: Int) { events += "off:$note" }
        override fun sustain(down: Boolean) { events += "pedal:$down" }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun earLeaseSilencesEveryInputSourceButKeepsAnswersAndKeyReleaseEvents() = runTest {
        val out = Output()
        val hub = InputHub(out)
        val received = mutableListOf<InputEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { hub.events.collect { received += it } }
        val lease = hub.silenceInput()
        out.events.clear()
        // A scheduled question at the same pitch must not be stopped by a silent answer's NoteOff.
        out.noteOn(60, 90)
        for (source in InputSource.entries) {
            hub.dispatch(InputEvent.NoteOn(60, 90, 1, source))
            assertEquals(setOf(60), hub.pressed.value)
            hub.dispatch(InputEvent.Sustain(true, 2, source))
            assertTrue(hub.sustainPressed.value)
            hub.dispatch(InputEvent.NoteOff(60, 3, source))
            hub.dispatch(InputEvent.Sustain(false, 4, source))
        }
        assertEquals(listOf("on:60"), out.events)
        assertEquals(InputSource.entries.size * 4, received.size)
        assertTrue(hub.pressed.value.isEmpty())
        assertFalse(hub.sustainPressed.value)
        lease.close()
        hub.dispatch(InputEvent.NoteOn(62, 80, 5, InputSource.TOUCH))
        assertEquals("on:62", out.events.last())
    }

    @Test fun nestedLeasesRestoreTheLatestPreferenceAndClosingTwiceCannotUnmuteAnotherOwner() {
        val out = Output(); val hub = InputHub(out)
        hub.dispatch(InputEvent.NoteOn(60, 80, 0, InputSource.USB_MIDI))
        val first = hub.silenceInput()
        assertContains(out.events, "off:60")
        val second = hub.silenceInput()
        first.close(); first.close()
        out.events.clear()
        hub.dispatch(InputEvent.NoteOn(64, 80, 1, InputSource.TOUCH))
        assertTrue(out.events.isEmpty())
        hub.soundThrough = false
        second.close()
        hub.dispatch(InputEvent.NoteOn(65, 80, 2, InputSource.BLE_MIDI))
        assertTrue(out.events.isEmpty())
        hub.dispatch(InputEvent.NoteOn(67, 80, 3, InputSource.TOUCH))
        assertEquals(listOf("on:67"), out.events)
    }
}
