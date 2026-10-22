package com.id.mimikopi.core

import com.id.mimikopi.core.input.PianoNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PianoNamesTest {
    @Test
    fun theBadgerIsCalledByItsNameNotWhatItReports() {
        assertEquals("Badger Playon", PianoNames.display("Dight Piano MiDi", emptyMap()))
        assertEquals("Badger Playon", PianoNames.display(" Dight Piano MiDi ", emptyMap()))
        assertEquals("Digital piano", PianoNames.display("Digital piano", emptyMap()))
    }

    @Test
    fun theUsersOwnNameWinsAndBlankGoesBackToTheDefault() {
        var own = PianoNames.rename(emptyMap(), "Dight Piano MiDi", "  เปียโน   ชมพู ")
        assertEquals("เปียโน ชมพู", PianoNames.display("Dight Piano MiDi", own))
        own = PianoNames.rename(own, "Dight Piano MiDi", "")
        assertEquals("Badger Playon", PianoNames.display("Dight Piano MiDi", own))
        assertTrue(own.isEmpty())
        // Naming it its default stores nothing.
        assertTrue(PianoNames.rename(emptyMap(), "USB-MIDI", "USB-MIDI").isEmpty())
        assertEquals(PianoNames.MAX_LENGTH, PianoNames.rename(emptyMap(), "x", "a".repeat(99))["x"]!!.length)
    }

    @Test
    fun namesRoundTripAndBadTextIsIgnored() {
        val own = mapOf("USB-MIDI" to "Badger Playon (สาย)")
        assertEquals(own, PianoNames.decode(PianoNames.encode(own)))
        assertEquals(emptyMap(), PianoNames.decode("not json"))
        assertEquals(emptyMap(), PianoNames.decode(null))
    }
}
