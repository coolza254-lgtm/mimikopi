package com.id.mimikopi.core

import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.SongEditor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongEditorTest {
    private val ms = 1_000_000L
    private fun play(e: SongEditor, vararg notes: Int) { var t = 0L; notes.forEach { e.noteOn(it, t); t += 500 * ms } }

    @Test
    fun notesAreAddedAndStruckTogetherMakeAChord() {
        val e = SongEditor(Section("s", "t"))
        e.noteOn(60, 0); e.noteOn(64, 20 * ms); e.noteOn(67, 600 * ms)
        assertEquals(listOf(listOf(60, 64), listOf(67)), e.section.steps.map { it.notes })
    }

    @Test
    fun insertModeKeepsAddingBeforeTheSelectedNote() {
        val e = SongEditor(Section("s", "t"))
        play(e, 60, 64)
        e.select(1); e.setInserting(true)
        e.noteOn(61, 10_000 * ms); e.noteOn(62, 11_000 * ms)
        assertEquals(listOf(60, 61, 62, 64), e.section.steps.map { it.notes.single() })
        assertEquals(3, e.selected)
    }

    @Test
    fun aNewPartCanBeStartedAndNamedWithAVideoTime() {
        val e = SongEditor(Section("s", "t"))
        play(e, 60, 62)
        e.toggleNewPart()
        e.noteOn(67, 10_000 * ms)
        assertEquals(listOf(2), e.section.breaks)
        e.setPart(1, "ฮุก", "1:05")
        assertEquals("ฮุก", e.section.phraseName(1))
        assertEquals(65, e.section.phraseTime(1))
        // More notes after naming: parts are no longer re-split automatically.
        play(e, 69, 71, 72, 74, 76, 77, 79)
        assertEquals(listOf(2), e.section.breaks)
        e.setPart(1, "", "")
        assertNull(e.section.phraseName(1))
    }

    @Test
    fun selectingAndReplacingThenUndo() {
        val e = SongEditor(Section("s", "t"))
        play(e, 60, 62, 64)
        e.select(0)
        e.noteOn(59, 10_000 * ms)
        assertEquals(listOf(59, 62, 64), e.section.steps.map { it.notes.single() })
        assertNull(e.selected)
        e.undo()
        assertEquals(2, e.section.steps.size)
    }
}
