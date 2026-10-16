package com.id.mimikopi.core

import com.id.mimikopi.core.section.Phrases
import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.SectionStats
import com.id.mimikopi.core.section.SectionTrainer
import com.id.mimikopi.core.section.SectionTrainer.Mode
import com.id.mimikopi.core.section.SectionTrainer.Outcome
import com.id.mimikopi.core.section.Spelling
import com.id.mimikopi.core.section.Step
import com.id.mimikopi.core.section.StepRecorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SectionTest {
    private fun steps(vararg n: Int) = n.map { Step(listOf(it)) }

    @Test
    fun autoBreaksMakeEvenPhrases() {
        assertEquals(emptyList(), Phrases.autoBreaks(6))
        assertEquals(listOf(5, 10), Phrases.autoBreaks(14))
        assertEquals(listOf(4), Phrases.autoBreaks(7))
        assertEquals(listOf(0..4, 5..9, 10..13), Phrases.ranges(14, listOf(5, 10)))
        assertEquals(listOf(0..2), Phrases.ranges(3, listOf(0, 3, 7)))
    }

    @Test
    fun breaksFollowDeletedSteps() {
        assertEquals(listOf(3), Phrases.afterDelete(listOf(4), 1))
        assertEquals(listOf(4), Phrases.afterDelete(listOf(1, 5), 0).let { it }) // 1 -> 0 dropped, 5 -> 4
        assertEquals(listOf(2, 5), Phrases.toggle(listOf(5), 2))
        assertEquals(listOf(5), Phrases.toggle(listOf(2, 5), 2))
    }

    @Test
    fun recorderGroupsChordsByOnset() {
        val r = StepRecorder(chordWindowNanos = 60_000_000)
        assertEquals(Step(listOf(60)) to false, r.noteOn(60, 0))
        assertEquals(Step(listOf(60, 64)) to true, r.noteOn(64, 20_000_000))
        assertEquals(Step(listOf(60, 64, 67)) to true, r.noteOn(67, 50_000_000))
        assertEquals(Step(listOf(62)) to false, r.noteOn(62, 400_000_000))
        // The same key twice quickly is a repeated note, not a chord.
        assertEquals(Step(listOf(62)) to false, r.noteOn(62, 420_000_000))
    }

    @Test
    fun spellingWithSharpsOrFlats() {
        assertEquals("C♯4", Spelling.name(61, flats = false))
        assertEquals("D♭4", Spelling.name(61, flats = true))
        assertEquals("B♭3", Spelling.name(58, flats = true))
        assertEquals("E4", Spelling.name(64, flats = true))
    }

    @Test
    fun wrongNoteRestartsThePhrase() {
        val t = SectionTrainer(steps(60, 62, 64, 65, 67, 69), Phrases.ranges(6, listOf(3)))
        assertEquals(Outcome.STEP_DONE, t.press(60))
        assertEquals(Outcome.STEP_DONE, t.press(62))
        assertEquals(Outcome.WRONG, t.press(63))
        assertEquals(0, t.position)
        assertEquals(1, t.wrongNotes)
    }

    @Test
    fun phrasesThenFullThenMastered() {
        val t = SectionTrainer(steps(60, 62, 64, 65), Phrases.ranges(4, listOf(2)), cleanTarget = 2)
        fun run(vararg n: Int) = n.map { t.press(it) }.last()
        assertEquals(Outcome.PHRASE_RUN_DONE, run(60, 62))
        assertEquals(Outcome.PHRASE_PASSED, run(60, 62))
        assertEquals(Mode.Phrase(1), t.mode)
        assertEquals(2, t.position)
        run(64, 65)
        assertEquals(Outcome.PHRASE_PASSED, run(64, 65))
        assertEquals(Mode.Full, t.mode)
        assertEquals(0, t.position)
        // A mistake in the second phrase goes back to that phrase only; the run is no longer clean.
        t.press(60); t.press(62); t.press(64)
        assertEquals(Outcome.WRONG, t.press(67))
        assertEquals(2, t.position)
        assertEquals(Outcome.FULL_RUN_DONE, run(64, 65))
        assertEquals(0, t.streak)
        assertEquals(Outcome.FULL_RUN_DONE, run(60, 62, 64, 65))
        assertEquals(Outcome.MASTERED, run(60, 62, 64, 65))
        assertTrue(t.mastered)
        val stats = t.drainInto(SectionStats(), "2026-10-16")
        assertEquals(3, stats.fullRuns)
        assertEquals(2, stats.cleanFullRuns)
        assertTrue(stats.mastered)
        assertEquals(mapOf(0 to 2, 1 to 2), stats.phraseClean)
        assertEquals(0, t.fullRuns)
    }

    @Test
    fun chordsNeedEveryNoteInAnyOrder() {
        val t = SectionTrainer(listOf(Step(listOf(60, 64, 67)), Step(listOf(62))), Phrases.ranges(2, emptyList()), cleanTarget = 1)
        assertEquals(Outcome.NOTE_OK, t.press(67))
        assertEquals(Outcome.IGNORED, t.press(67))
        assertEquals(Outcome.NOTE_OK, t.press(60))
        assertEquals(Outcome.STEP_DONE, t.press(64))
        assertEquals(Outcome.MASTERED, t.press(62))
    }

    @Test
    fun sectionsRoundTripAsJson() {
        val s = Section("a", "ท่อนฮุก", steps(60, 62), breaks = listOf(1), flats = true, stats = SectionStats(fullRuns = 2))
        assertEquals(listOf(s), Section.parseList(Section.toJson(listOf(s))))
        assertFalse(Section.toJson(listOf(Section("b", "x"))).contains("stats"))
    }
}
