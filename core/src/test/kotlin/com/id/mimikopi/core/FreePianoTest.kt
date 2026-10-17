package com.id.mimikopi.core

import com.id.mimikopi.core.input.Transposer
import com.id.mimikopi.core.piano.PianoSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FreePianoTest {
    @Test
    fun aKeyReleasedAfterTheTransposeChangedSilencesThePitchItStarted() {
        val t = Transposer()
        t.semitones = 2
        assertEquals(62, t.press(60))
        t.semitones = -3
        assertEquals(62, t.release(60))
        assertNull(t.release(60))
        assertEquals(57, t.press(60))
        assertEquals(listOf(57), t.clear())
        assertNull(t.release(60))
    }

    @Test
    fun transposeStaysOnTheKeyboardAndWithinAnOctave() {
        val t = Transposer()
        t.semitones = 40
        assertEquals(Transposer.RANGE, t.semitones)
        assertNull(t.press(120))
        assertNull(t.press(-1))
        assertNull(t.release(200))
    }

    @Test
    fun soundSettingsRoundTripAndTheDefaultIsTheUsualLevel() {
        assertEquals(1f, PianoSound().factor, 1e-4f)
        val s = PianoSound(0.2f, 0.7f, 0.1f)
        assertEquals(s, PianoSound.parse(s.encode()))
        assertEquals(PianoSound(), PianoSound.parse("junk"))
        assertEquals(PianoSound(), PianoSound.parse(null))
        assertEquals(1f, PianoSound.parse("3|0.5|0.5").volume)
        assertEquals(-18f, PianoSound(0f).db, 1e-4f)
        assertEquals(12f, PianoSound(1f).db, 1e-4f)
    }
}
