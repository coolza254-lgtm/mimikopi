package com.id.mimikopi.core

import com.id.mimikopi.core.recall.PrivatePhrase
import com.id.mimikopi.core.recall.PrivateRecall
import com.id.mimikopi.core.recall.PrivateSong
import com.id.mimikopi.core.recall.RecallAttempt
import com.id.mimikopi.core.recall.RecallHand
import com.id.mimikopi.core.recall.RecallOutcome
import com.id.mimikopi.core.recall.TargetSong
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TargetSongTest {
    private fun phrase(id: String) = PrivatePhrase(id, "ช่วง $id", 10, 20)
    private var n = 0
    private fun PrivatePhrase.played(day: String, hand: RecallHand = RecallHand.RIGHT, cold: Boolean = true, outcome: RecallOutcome = RecallOutcome.INDEPENDENT) =
        PrivateRecall.record(this, RecallAttempt("a${n++}", day, hand, outcome, revision, presses = 5,
            unaidedConfirmed = outcome == RecallOutcome.INDEPENDENT, beforeViewing = cold && outcome == RecallOutcome.INDEPENDENT))

    @Test
    fun theChosenSongWinsAndTheFirstOneStandsInOtherwise() {
        val a = PrivateSong("a", "I WISH")
        val b = PrivateSong("b", "心のプラカード")
        assertEquals(b, TargetSong.pick(listOf(a, b), "b"))
        assertEquals(a, TargetSong.pick(listOf(a, b), "gone"))
        assertNull(TargetSong.pick(emptyList(), null))
    }

    @Test
    fun aPhraseIsLearnedAfterTwoDaysPlayedBeforeLooking() {
        val p = phrase("1")
        assertEquals(TargetSong.State.NEW, TargetSong.state(p))
        val once = p.played("2026-10-20")
        assertEquals(TargetSong.State.PRACTISING, TargetSong.state(once))
        assertEquals(TargetSong.State.PRACTISING, TargetSong.state(once.played("2026-10-20", cold = false)))
        val twice = once.played("2026-10-22")
        assertEquals(TargetSong.State.LEARNED, TargetSong.state(twice))
        assertEquals(0.5f, TargetSong.fraction(PrivateSong("s", "s", phrases = listOf(twice, phrase("2")))))
    }

    @Test
    fun nextIsWhatIsDueElseTheFirstPhraseNotLearned() {
        val day = LocalDate.parse("2026-10-22")
        val learned = phrase("1").played("2026-10-19").played("2026-10-21")
        val stuck = phrase("2").played("2026-10-21", hand = RecallHand.LEFT, outcome = RecallOutcome.STUCK)
        val song = PrivateSong("s", "s", phrases = listOf(learned, stuck, phrase("3")))
        // Phrase 2 was stuck yesterday with the left hand: due again today.
        val due = TargetSong.next(song, day)!!
        assertEquals("2", due.phrase.id); assertEquals(RecallHand.LEFT, due.hand); assertEquals(true, due.due)
        // Nothing due: the first phrase not learned.
        val later = TargetSong.next(song.copy(phrases = listOf(learned.copy(attempts = learned.attempts), phrase("3"))), LocalDate.parse("2026-10-13"))!!
        assertEquals("3", later.phrase.id)
        assertEquals(RecallHand.RIGHT, later.hand)
    }
}
