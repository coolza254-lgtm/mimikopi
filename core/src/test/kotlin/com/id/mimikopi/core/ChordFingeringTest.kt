package com.id.mimikopi.core

import com.id.mimikopi.core.theory.Chord
import com.id.mimikopi.core.theory.ChordFingering
import com.id.mimikopi.core.theory.PitchName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordFingeringTest {
    private fun chord(root: String, q: String) = Chord(PitchName.parse(root)!!, q)

    @Test
    fun cMajorUsesTheUsualFingerings() {
        val rh = ChordFingering.rightHand(chord("C", ""))
        assertEquals(listOf(listOf(60, 64, 67), listOf(64, 67, 72), listOf(67, 72, 76)), rh.map { it.notes })
        assertEquals(listOf(listOf(1, 3, 5), listOf(1, 2, 5), listOf(1, 3, 5)), rh.map { it.fingers })
        val lh = ChordFingering.leftHand(chord("C", ""))
        assertEquals(listOf(48, 52, 55), lh.first().notes)
        assertEquals(listOf(listOf(5, 3, 1), listOf(5, 3, 1), listOf(5, 2, 1)), lh.map { it.fingers })
    }

    @Test
    fun seventhsUseFourFingersAndTwoHandsPutTheRootLow() {
        val g7 = chord("G", "7")
        assertEquals(listOf(1, 2, 3, 5), ChordFingering.rightHand(g7).first().fingers)
        assertEquals(4, ChordFingering.leftHand(g7).size)
        val (left, right) = ChordFingering.twoHands(chord("A", "m"))
        assertEquals(listOf(57), left.notes)
        assertEquals(listOf(69, 72, 76), right.notes)
    }

    @Test
    fun everyBookChordMatchesItselfAndIsSpelledByThirds() {
        for (root in ChordFingering.ROOTS) for (q in ChordFingering.QUALITIES) {
            val c = Chord(root, q)
            (ChordFingering.rightHand(c) + ChordFingering.leftHand(c)).forEach { s ->
                assertTrue(Chord.matches(c, s.notes.toSet()), "${c.symbol} ${s.notes}")
                assertEquals(s.notes.size, s.fingers.size)
                assertTrue(s.notes.zipWithNext().all { (a, b) -> a < b })
            }
        }
        assertEquals("E♭ G B♭", ChordFingering.spelled(chord("Eb", "")).joinToString(" ") { it.display() })
        assertEquals("C E♭ G♭ B𝄫", ChordFingering.spelled(chord("C", "dim7")).joinToString(" ") { it.display() })
        assertEquals("C D E G", ChordFingering.spelled(chord("C", "add9")).joinToString(" ") { it.display() })
    }
}
