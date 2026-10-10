package com.id.mimikopi.core

import com.id.mimikopi.core.music.Direction
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.Motion
import com.id.mimikopi.core.music.MotionKind
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.music.isBlackKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PitchTest {
    @Test
    fun middleCIsMidi60() {
        assertEquals(60, SpelledPitch.parse("C4").midi)
        assertEquals(69, SpelledPitch.parse("A4").midi)
        assertEquals(21, SpelledPitch.parse("A0").midi) // lowest piano key
        assertEquals(108, SpelledPitch.parse("C8").midi) // highest piano key
    }

    @Test
    fun enharmonicsShareMidiButNotStaffPosition() {
        val cSharp = SpelledPitch.parse("C#4")
        val dFlat = SpelledPitch.parse("Db4")
        assertEquals(cSharp.midi, dFlat.midi)
        assertEquals(1, dFlat.diatonicIndex - cSharp.diatonicIndex)
    }

    @Test
    fun octaveBoundaryIsAtC() {
        assertEquals(59, SpelledPitch.parse("B3").midi)
        assertEquals(60, SpelledPitch.parse("B#3").midi)
        assertEquals(59, SpelledPitch.parse("Cb4").midi)
    }

    @Test
    fun parseAndToStringRoundTrip() {
        for (text in listOf("C4", "F#3", "Bb2", "E##5", "Gbb1", "A0")) {
            assertEquals(text, SpelledPitch.parse(text).toString())
        }
        assertFailsWith<IllegalArgumentException> { SpelledPitch.parse("H4") }
        assertFailsWith<IllegalArgumentException> { SpelledPitch.parse("C") }
    }

    @Test
    fun fromMidiSharpCoversAll128Notes() {
        for (midi in 0..127) {
            val p = SpelledPitch.fromMidiSharp(midi)
            assertEquals(midi, p.midi)
            assertTrue(p.accidental in 0..1)
        }
    }

    @Test
    fun namesInBothStyles() {
        val fSharp = SpelledPitch(Letter.F, 1, 4)
        assertEquals("F♯", fSharp.name(NoteNameStyle.LETTER))
        assertEquals("ฟา♯", fSharp.name(NoteNameStyle.SOLFEGE))
        assertEquals("F♯4 / ฟา♯", fSharp.name(NoteNameStyle.BOTH, withOctave = true))
        assertEquals("ซอล", Letter.G.thaiSolfege)
    }

    @Test
    fun blackKeys() {
        assertEquals(listOf(61, 63, 66, 68, 70), (60..71).filter(::isBlackKey))
        assertFalse(isBlackKey(64))
    }

    @Test
    fun motionIsMeasuredOnTheStaff() {
        fun m(a: String, b: String) = Motion.between(SpelledPitch.parse(a), SpelledPitch.parse(b))
        assertEquals(Motion(MotionKind.REPEAT, Direction.SAME, 0), m("E4", "E4"))
        assertEquals(Motion(MotionKind.STEP, Direction.UP, 1), m("E4", "F4"))
        assertEquals(Motion(MotionKind.SKIP, Direction.DOWN, 2), m("G4", "E4"))
        assertEquals(5, m("C4", "G4").intervalNumber) // a 5th
        // C#4 -> Db4 is the same key but a step on the staff.
        assertEquals(MotionKind.STEP, m("C#4", "Db4").kind)
        assertEquals(MotionKind.STEP, m("B3", "C4").kind)
    }
}
