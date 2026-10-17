package com.id.mimikopi.core

import com.id.mimikopi.core.section.Hand
import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.Step
import com.id.mimikopi.core.section.VideoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongNotebookTest {
    private fun st(vararg n: Int) = Step(n.toList())
    private val song = Section(
        "s", "เพลง", steps = listOf(st(60), st(62), st(64), st(48, 67), st(65), st(64)), breaks = listOf(3),
        phraseNames = mapOf(0 to "อินโทร", 3 to "ฮุก"), phraseTimes = mapOf(3 to 83),
    )

    @Test
    fun insertingAndDeletingKeepNamesAndTimesWithTheirParts() {
        val ins = song.withStepInserted(1, st(61))
        assertEquals(listOf(4), ins.breaks)
        assertEquals("ฮุก", ins.phraseName(1))
        assertEquals(83, ins.phraseTime(1))
        // Inserted at a part's first note: the new note starts that part.
        val atStart = song.withStepInserted(3, st(66))
        assertEquals(listOf(3), atStart.breaks)
        assertEquals(listOf(66), atStart.steps[3].notes)
        val del = song.withStepDeleted(0)
        assertEquals(listOf(2), del.breaks)
        assertEquals("ฮุก", del.phraseName(1))
        assertEquals("อินโทร", del.phraseName(0))
        // The hook's only notes deleted one by one: its name goes with the part.
        var gone = song
        repeat(3) { gone = gone.withStepDeleted(3) }
        assertEquals(1, gone.phrases.size)
        assertNull(gone.phraseTimes[3])
    }

    @Test
    fun handsSplitAtMiddleCAndDropEmptySteps() {
        assertTrue(song.bothHands)
        val left = song.forHand(Hand.LEFT)
        assertEquals(listOf(listOf(48)), left.steps.map { it.notes })
        assertEquals(listOf(1), left.sourcePhrase)
        val right = song.forHand(Hand.RIGHT)
        assertEquals(6, right.steps.size)
        assertEquals(listOf(67), right.steps[3].notes)
        assertEquals(listOf(0..2, 3..5), right.phrases)
        assertFalse(song.copy(split = 40).bothHands)
    }

    @Test
    fun videoTimesAndLinks() {
        assertEquals(83, VideoTime.parse("1:23"))
        assertEquals(3723, VideoTime.parse("1:02:03"))
        assertEquals(45, VideoTime.parse("45"))
        assertNull(VideoTime.parse("1:x"))
        assertEquals("1:23", VideoTime.format(83))
        assertEquals("1:02:03", VideoTime.format(3723))
        assertEquals("https://youtu.be/abc", VideoTime.link("youtu.be/abc"))
        assertNull(VideoTime.link("not a link"))
        assertEquals("https://youtu.be/abc?t=83s", VideoTime.at("https://youtu.be/abc", 83))
        assertEquals("https://www.youtube.com/watch?v=abc&t=10s", VideoTime.at("https://www.youtube.com/watch?v=abc&t=5s", 10))
        assertEquals("https://www.youtube.com/watch?v=abc", VideoTime.at("https://www.youtube.com/watch?v=abc", null))
    }
}
