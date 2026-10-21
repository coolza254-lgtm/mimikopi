package com.id.mimikopi.core

import com.id.mimikopi.core.recall.*
import java.time.LocalDate
import kotlin.test.*
import org.junit.Test

class PrivateRecallTest {
    private val day = LocalDate.of(2026, 10, 21)
    private val phrase = PrivatePhrase("phrase-a", "วลี A", 42, 48, "เริ่มจากเสียงที่ฟังไว้")
    private fun attempt(id: String, date: LocalDate = day, hand: RecallHand = RecallHand.RIGHT,
        outcome: RecallOutcome = RecallOutcome.INDEPENDENT, revision: Int = 1, cold: Boolean = false) =
        RecallAttempt(id, date.toString(), hand, outcome, revision, presses = 3, unaidedConfirmed = outcome == RecallOutcome.INDEPENDENT, beforeViewing = cold)
    @Test fun onlyCanonicalPublicYoutubeLinksAreOpenedAndTrackingIsDropped() {
        val expected = "https://www.youtube.com/watch?v=abcdefghijk"
        assertEquals(expected, VideoReference.canonical("https://youtu.be/abcdefghijk?si=tracking&t=99"))
        assertEquals(expected, VideoReference.canonical("https://m.youtube.com/watch?v=abcdefghijk&feature=share"))
        assertEquals("$expected&t=42s", VideoReference.at(expected, 42))
        for (url in listOf("javascript:alert(1)", "file:///private", "https://youtube.com.evil/watch?v=abcdefghijk", "https://name@youtube.com/watch?v=abcdefghijk", "https://youtube.com:999/watch?v=abcdefghijk", "http://youtu.be/abcdefghijk")) assertNull(VideoReference.canonical(url))
        assertEquals("", VideoReference.canonical(""))
    }
    @Test fun timeEntryRejectsBadOrReversedRanges() {
        assertEquals(42, VideoReference.seconds("0:42"))
        assertEquals(3661, VideoReference.seconds("1:01:01"))
        assertEquals("1:01:01", VideoReference.clock(3661))
        for (s in listOf("-1", "1:99", "1.5", "abc", "10000000000000000", "")) assertNull(VideoReference.seconds(s))
        assertFailsWith<IllegalArgumentException> { PrivateRecall.validate(PrivateSong("s", "เพลง", phrases = listOf(phrase.copy(endSeconds = 40)))) }
    }
    @Test fun recallIsSeparatedByHandAndReferenceRevisionRatherThanSharedAcrossTheSong() {
        val p = PrivateRecall.record(phrase, attempt("a"))
        assertEquals(listOf(day.toString()), PrivateRecall.independentDays(p, RecallHand.RIGHT))
        assertTrue(PrivateRecall.independentDays(p, RecallHand.LEFT).isEmpty())
        val changed = PrivateRecall.editPhrase(p, "ชื่อใหม่", 43, 48, phrase.cue)
        assertEquals(2, changed.revision)
        assertEquals(p.attempts, changed.attempts)
        assertTrue(PrivateRecall.current(changed, RecallHand.RIGHT).isEmpty())
        assertNull(PrivateRecall.due(changed, RecallHand.RIGHT))
        assertEquals(1, PrivateRecall.editPhrase(p, "ชื่ออย่างเดียว", 42, 48, phrase.cue).revision)
    }
    @Test fun sameDayRepeatsNeverBecomeEvidenceOfRecallAcrossDays() {
        var p = phrase
        repeat(10) { p = PrivateRecall.record(p, attempt("r-$it")) }
        assertEquals(1, PrivateRecall.independentDays(p, RecallHand.RIGHT).size)
        assertEquals(day.plusDays(1), PrivateRecall.due(p, RecallHand.RIGHT))
        p = PrivateRecall.record(p, attempt("next-day", day.plusDays(1), cold = true))
        assertEquals(day.plusDays(2), PrivateRecall.due(p, RecallHand.RIGHT))
        p = PrivateRecall.record(p, attempt("third-day", day.plusDays(4), cold = true))
        assertEquals(day.plusDays(7), PrivateRecall.due(p, RecallHand.RIGHT))
    }
    @Test fun needingHelpBringsReviewCloserWithoutDeletingEarlierIndependentDays() {
        var p = phrase
        repeat(3) { p = PrivateRecall.record(p, attempt("a-$it", day.plusDays(it.toLong()))) }
        p = PrivateRecall.record(p, attempt("help", day.plusDays(3), outcome = RecallOutcome.HELPED))
        assertEquals(3, PrivateRecall.independentDays(p, RecallHand.RIGHT).size)
        assertEquals(day.plusDays(4), PrivateRecall.due(p, RecallHand.RIGHT))
        p = PrivateRecall.record(p, attempt("retry", day.plusDays(3)))
        assertEquals(day.plusDays(4), PrivateRecall.due(p, RecallHand.RIGHT))
    }
    @Test fun rollingDetailedHistoryDoesNotEraseLongTermRecallEvidence() {
        var p = phrase
        repeat(100) { p = PrivateRecall.record(p, attempt("day-$it", day.plusDays(it.toLong()), cold = true)) }
        assertEquals(PrivateRecall.KEEP_ATTEMPTS, p.attempts.size)
        assertEquals(100, PrivateRecall.independentDays(p, RecallHand.RIGHT).size)
        assertEquals(day.plusDays(106), PrivateRecall.due(p, RecallHand.RIGHT))
        val song = PrivateSong("s", "เพลงของฉัน", phrases = listOf(p))
        assertEquals(song, PrivateRecall.import(PrivateRecall.export(song)))
    }
    @Test fun emptyOrAssistedRunsCannotClaimIndependentRecall() {
        assertFailsWith<IllegalArgumentException> { PrivateRecall.record(phrase, attempt("none").copy(presses = 0)) }
        assertFailsWith<IllegalArgumentException> { PrivateRecall.record(phrase, attempt("hint").copy(hintUsed = true)) }
        assertFailsWith<IllegalArgumentException> { PrivateRecall.record(phrase, attempt("not-confirmed").copy(unaidedConfirmed = false)) }
        assertEquals(1, PrivateRecall.record(phrase, attempt("external").copy(presses = 0, externalPiano = true)).attempts.size)
        assertFailsWith<IllegalArgumentException> { PrivateRecall.record(phrase, attempt("old", revision = 2)) }
    }
    @Test fun guidedDailyPracticeIsDifferentFromFirstTrialBeforeViewingAcrossDays() {
        var p = phrase
        repeat(4) { p = PrivateRecall.record(p, attempt("guided-$it", day.plusDays(it.toLong()))) }
        assertEquals(4, PrivateRecall.independentDays(p, RecallHand.RIGHT).size)
        assertTrue(PrivateRecall.rememberedDays(p, RecallHand.RIGHT).isEmpty())
        assertEquals(day.plusDays(4), PrivateRecall.due(p, RecallHand.RIGHT))
        p = PrivateRecall.record(p, attempt("cold", day.plusDays(4), cold = true))
        assertEquals(1, PrivateRecall.rememberedDays(p, RecallHand.RIGHT).size)
        assertFailsWith<IllegalArgumentException> { PrivateRecall.record(p, attempt("again", day.plusDays(4), cold = true)) }
    }

    @Test fun backupRoundTripPreservesCueAndEvidenceAndRejectsWrongVersions() {
        val song = PrivateSong("song-a", "เพลงของฉัน", phrases = listOf(PrivateRecall.record(phrase, attempt("run-a"))))
        val text = PrivateRecall.export(song)
        assertEquals(song, PrivateRecall.import(text))
        assertFailsWith<IllegalArgumentException> { PrivateRecall.import(text.replace("\"version\":1", "\"version\":9")) }
        assertFailsWith<IllegalArgumentException> { PrivateRecall.import("x".repeat(PrivateRecall.MAX_BYTES + 1)) }
        assertFailsWith<IllegalArgumentException> { PrivateRecall.validate(song.copy(phrases = listOf(phrase, phrase))) }
    }
    @Test fun reviewRecommendationPrefersDueMaterialThenUntriedMaterial() {
        val done = PrivateRecall.record(phrase, attempt("r"))
        val fresh = phrase.copy(id = "b", title = "วลี B")
        val song = PrivateSong("s", "เพลง", phrases = listOf(done, fresh))
        assertEquals("b", PrivateRecall.next(song, RecallHand.RIGHT, day)?.id)
        assertEquals("phrase-a", PrivateRecall.next(song, RecallHand.RIGHT, day.plusDays(1))?.id)
        assertEquals("phrase-a", PrivateRecall.next(song, RecallHand.LEFT, day)?.id)
    }
}
