package com.id.mimikopi.ui.reading

import com.id.mimikopi.core.exercise.Generators
import com.id.mimikopi.core.exercise.NoteIdItem
import com.id.mimikopi.core.keyboard.KeyboardLayout
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.ui.practice.Prompt
import com.id.mimikopi.ui.reader.ReaderOptions
import com.id.mimikopi.ui.reader.constrainReadingChange
import com.id.mimikopi.ui.reader.newReadingOptions
import com.id.mimikopi.ui.reader.readingInputRange
import com.id.mimikopi.ui.reader.HandChoice
import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.score.Score
import org.junit.Test
import kotlin.test.*

class ReadingPracticeTest {
    @Test fun everyPracticePoolCanGenerateExactPitchQuestionsAndAllNamesAreValid() {
        for (clef in ReadingClef.entries) for (range in ReadingRange.entries) for (acc in listOf(false, true)) {
            val settings = ReadingPractice(clef, range, acc)
            val d = settings.drill()
            assertTrue(d.pool.isNotEmpty())
            assertTrue(d.problems().isEmpty(), d.problems().joinToString())
            val questions = Generators.noteId(d, 42)
            assertEquals(20, questions.size)
            assertFalse(questions.zipWithNext().any { it.first == it.second })
            for (q in questions) {
                assertTrue(q.accepts(q.pitch.midi))
                assertFalse(q.accepts(q.pitch.midi + 12))
                assertEquals(q, NoteIdItem.parse(q.srsId))
                if (!acc) assertEquals(0, q.pitch.accidental)
            }
            if (acc) assertTrue(d.pool.any { "b" in it.substringBefore(':') })
        }
        assertEquals(listOf(60, 62, 64, 65, 67), ReadingPractice().drill().pool.map { NoteIdItem.parse(it).pitch.midi })
    }

    @Test fun keyboardPagesReachEveryPitchWithoutShrinkingBelowOneOctave() {
        for (low in 0..127) for (high in low..127) {
            val pages = readingKeyPages(low..high)
            assertTrue((low..high).all { note -> pages.any { note in it } })
            assertTrue(pages.all { it.first >= 0 && it.last <= 127 && it.last - it.first <= 12 })
            assertTrue(pages.all { KeyboardLayout(it.first, it.last, 400f, 112f).whiteKeys.size <= 8 })
        }
    }

    @Test fun aMissDoesNotTurnLaterOriginalQuestionsOfTheSamePitchIntoUnscoredRetries() {
        val c = Prompt.Note(NoteIdItem(SpelledPitch.parse("C4"), Clef.TREBLE))
        val e = Prompt.Note(NoteIdItem(SpelledPitch.parse("E4"), Clef.TREBLE))
        val queue = ReadingPromptQueue()
        queue.addAll(listOf(c, e, c, e, c))
        assertFalse(queue.next()!!.retry)
        queue.repeatOnce(c)
        val rest = generateSequence { queue.next() }.toList()
        assertEquals(listOf(e, c, e, c, c), rest.map { it.prompt })
        assertEquals(listOf(false, false, false, false, true), rest.map { it.retry })
        queue.repeatOnce(c)
        assertNull(queue.next())
        queue.clear(); queue.addAll(listOf(c)); queue.repeatOnce(c)
        assertEquals(2, queue.size)
    }

    @Test fun melodyTrainingStartsUntimedAndPhonePagesCoverTheSelectedPartWithoutFollowingIndividualTargets() {
        for (level in 1..5) {
            val options = ReaderOptions().newReadingOptions(level)
            assertEquals(PlayMode.WAIT, options.mode)
            assertFalse(options.useRamp)
            assertFalse(options.playOtherHand)
            assertFalse(options.paperSize)
            assertEquals(if (level == 2) HandChoice.LEFT else if (level == 5) HandChoice.BOTH else HandChoice.RIGHT, options.hands)
            val score = Score.from(ScoreGenerators.sightReading(Drill(ExerciseType.E_READ_PIECE, "test", level = "L$level"), 4))
            val range = readingInputRange(score, options.hands.hands)
            val notes = score.events.filter { it.hand in options.hands.hands }.flatMap { it.pitches }.map { it.midi }
            assertTrue(notes.all { n -> readingKeyPages(range).any { n in it } })
            if (notes.isNotEmpty()) { assertEquals(notes.min(), range.first); assertEquals(notes.max(), range.last) }
        }
    }

    @Test fun freeReadingCanChangePaceWhileFixedTestsKeepTheirCriteriaAndViewsRemainEditable() {
        val fixed = ReaderOptions(mode = PlayMode.TEMPO)
        val requested = fixed.copy(mode = PlayMode.WAIT, hands = com.id.mimikopi.ui.reader.HandChoice.BOTH,
            falling = true, useRamp = false, pageView = false, paperSize = false)
        val assessment = fixed.constrainReadingChange(requested, fixed = true, freeReading = false)
        assertEquals(PlayMode.TEMPO, assessment.mode)
        assertEquals(fixed.hands, assessment.hands)
        assertEquals(fixed.falling, assessment.falling)
        assertEquals(fixed.useRamp, assessment.useRamp)
        assertFalse(assessment.pageView)
        val practice = fixed.constrainReadingChange(requested, fixed = true, freeReading = true)
        assertEquals(PlayMode.WAIT, practice.mode)
        assertEquals(fixed.hands, practice.hands)
        assertEquals(fixed.falling, practice.falling)
        assertEquals(requested, fixed.constrainReadingChange(requested, fixed = false, freeReading = false))
    }
}
