package com.id.mimikopi.core

import com.id.mimikopi.core.editor.PlayedNote
import com.id.mimikopi.core.editor.Recording
import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.score.Hand
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingTest {
    // 60 bpm: a quarter is 1000 ms, a sixteenth 250 ms.
    @Test fun snapsSlightlyEarlyAndLateNotesToTheGrid() {
        val take = listOf(PlayedNote(60, 1030, 1960), PlayedNote(62, 1980, 2490), PlayedNote(64, 2520, 4000))
        val n = Recording.transcribe(take, startMs = 1000, bpm = 60.0, grid = 2, keyFifths = 0)
        assertEquals(listOf(0, 4, 6), n.map { it.start })
        assertEquals(listOf(4, 2, 6), n.map { it.length })
        assertEquals("C4", n[0].pitches.single().toString())
    }

    @Test fun keysTogetherMakeAChordAndFlatKeysSpellFlats() {
        val take = listOf(PlayedNote(63, 1000, 2000), PlayedNote(67, 1040, 2000), PlayedNote(70, 980, 2000))
        val n = Recording.transcribe(take, 1000, 60.0, 4, keyFifths = -3).single()
        assertEquals(listOf("Eb4", "G4", "Bb4"), n.pitches.map { it.toString() })
    }

    @Test fun placeReplacesOnlyThatHandAndRange() {
        var t = Timeline.empty(ScoreMeta("x", "x"), measures = 3)
        t = Recording.place(t, Hand.RIGHT, 0, 2, Recording.transcribe(listOf(PlayedNote(72, 0, 12000)), 0, 60.0, 4, 0))
        t = Recording.place(t, Hand.LEFT, 1, 1, Recording.transcribe(listOf(PlayedNote(48, 0, 1000)), 0, 60.0, 4, 0))
        t = Recording.place(t, Hand.RIGHT, 1, 1, Recording.transcribe(listOf(PlayedNote(76, 0, 1000)), 0, 60.0, 4, 0))
        assertEquals(listOf(0 to 16, 16 to 4), t.rh.map { it.start to it.length })
        assertEquals(listOf(16), t.lh.map { it.start })
        assertEquals(3, t.toPieceFile().rh.size)
    }
}
