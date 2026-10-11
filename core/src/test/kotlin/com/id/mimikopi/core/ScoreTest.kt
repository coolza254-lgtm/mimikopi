package com.id.mimikopi.core

import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.core.playalong.Advice
import com.id.mimikopi.core.playalong.Coach
import com.id.mimikopi.core.playalong.Feedback
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.playalong.PlayAlongJudge
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.playalong.PlaySetup
import com.id.mimikopi.core.playalong.PieceProgress
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.config.TempoRampConfig
import java.time.LocalDate
import com.id.mimikopi.core.practice.isClean
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.KeySignature
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun piece(rh: List<String>, lh: List<String>, key: Int = 0, time: List<Int> = listOf(4, 4), tempo: Int = 60) =
    Score.from(PieceFile("t", "T", "c", "s", "L1", key, time, tempo, rh, lh))

class ScoreParseTest {
    @Test
    fun parsesTokensChordsRestsAndOnsets() {
        val s = piece(listOf("C4/q E4+G4/q r/h", "D4/q. E4/e F4/h"), listOf("C3/w", "G3/h. r/q"))
        assertEquals(2, s.measures)
        val rh = s.events.filter { it.hand == Hand.RIGHT }
        assertEquals(listOf(0.0, 1.0, 2.0, 4.0, 5.5, 6.0), rh.map { it.onset })
        assertEquals(2, rh[1].pitches.size)
        assertTrue(rh[2].isRest)
        assertEquals(1.5, rh[3].duration)
        assertEquals(8.0, s.totalBeats)
    }

    @Test
    fun rejectsMeasuresThatDoNotAddUp() {
        assertFailsWith<IllegalArgumentException> { piece(listOf("C4/q C4/q C4/q"), listOf("C3/w")) }
        assertFailsWith<IllegalArgumentException> { piece(listOf("C4/w"), listOf("C3/w", "C3/w")) }
        assertFailsWith<IllegalStateException> { piece(listOf("C4/x C4/h."), listOf("C3/w")) }
    }

    @Test
    fun threeFourMeasureLength() {
        val s = piece(listOf("C4/h."), listOf("C3/h."), time = listOf(3, 4))
        assertEquals(3.0, s.measureBeats)
    }

    @Test
    fun keySignatureAccidentalsAndPositions() {
        assertEquals(-1, KeySignature.accidentalFor(Letter.B, -1))
        assertEquals(0, KeySignature.accidentalFor(Letter.E, -1))
        assertEquals(1, KeySignature.accidentalFor(Letter.F, 1))
        assertEquals(0, KeySignature.accidentalFor(Letter.C, 1))
        assertEquals(listOf(4), KeySignature.positions(1, Clef.TREBLE)) // F5 = top line
        assertEquals(listOf(2), KeySignature.positions(1, Clef.BASS)) // F3 = 4th line
        assertEquals(listOf(0), KeySignature.positions(-1, Clef.TREBLE)) // B4 = middle line
    }
}

class BundledPiecesTest {
    private val files = File("../content/pieces").listFiles()!!.filter { it.name.endsWith(".json") }

    @Test
    fun allPiecesParseAndAreSafeToBundle() {
        assertTrue(files.size >= 5)
        for (f in files) {
            val p = PieceFile.parse(f.readText())
            assertEquals(f.nameWithoutExtension, p.id)
            val s = Score.from(p)
            assertTrue(s.events.any { it.hand == Hand.LEFT && !it.isRest })
            // Only public-domain or original material may be bundled (PROMPT.md).
            assertTrue("Public domain" in p.source || "Original" in p.source, "${p.id}: source must say public domain or original")
            for (banned in listOf("Morning Musume", "AKB", "Hello! Project", "Berryz", "℃-ute", "ANGERME", "Juice=Juice")) {
                assertFalse(banned in f.readText(), "${p.id} mentions $banned")
            }
            // Layout must succeed and place every notehead.
            val layout = ScoreLayout.of(s)
            assertEquals(s.events.sumOf { it.pitches.size }, layout.noteheads().size)
        }
    }
}

class ScoreLayoutTest {
    @Test
    fun anchorsIncreaseAndCursorInterpolates() {
        val s = piece(listOf("C4/q D4/q E4/h", "F4/w"), listOf("C3/w", "C3/w"))
        val l = ScoreLayout.of(s)
        val xs = l.anchors.map { it.second }
        assertEquals(xs.sorted(), xs)
        assertEquals(l.anchors.map { it.first }.sorted(), l.anchors.map { it.first })
        val x0 = l.xAt(0.0)
        val x1 = l.xAt(1.0)
        assertTrue(l.xAt(0.5) > x0 && l.xAt(0.5) < x1)
        assertEquals(2, l.measures.size)
        assertEquals(0, l.measureAtX(l.xAt(0.5)))
        assertEquals(1, l.measureAtX(l.xAt(4.5)))
        assertNull(l.measureAtX(-1.0))
    }

    @Test
    fun middleCGetsALedgerLineInTrebleAndStemsFollowPosition() {
        val s = piece(listOf("C4/q A5/q C4/h"), listOf("C3/w"))
        val l = ScoreLayout.of(s)
        val c4 = s.events.first { it.hand == Hand.RIGHT }
        // One ledger line for C4 (position -6) in the treble staff.
        val ledgers = l.lines.filter { it.eventId == c4.id && it.y1 == it.y2 }
        assertEquals(1, ledgers.size)
        assertEquals(ScoreLayout.yOf(Hand.RIGHT, -6), ledgers[0].y1)
        val stem = l.lines.first { it.eventId == c4.id && it.x1 == it.x2 }
        assertTrue(stem.y2 < stem.y1, "low note: stem goes up")
        val a5 = s.events.filter { it.hand == Hand.RIGHT }[1]
        val stemA5 = l.lines.first { it.eventId == a5.id && it.x1 == it.x2 }
        assertTrue(stemA5.y2 > stemA5.y1, "high note: stem goes down")
    }

    @Test
    fun accidentalsCarryThroughTheMeasureAndRespectTheKey() {
        // F major: Bb from the key needs no sign; B natural needs one, repeated B natural in the same bar does not,
        // and the next bar shows the key again (Bb, no sign).
        val s = piece(listOf("Bb4/q B4/q B4/q Bb4/q", "Bb4/w"), listOf("F3/w", "F3/w"), key = -1)
        val l = ScoreLayout.of(s)
        val rh = s.events.filter { it.hand == Hand.RIGHT }
        fun signs(id: Int) = l.glyphs.filter { it.eventId == id && it.codepoint in setOf(ScoreLayout.FLAT, ScoreLayout.NATURAL, ScoreLayout.SHARP) }
        assertEquals(0, signs(rh[0].id).size)
        assertEquals(listOf(ScoreLayout.NATURAL), signs(rh[1].id).map { it.codepoint })
        assertEquals(0, signs(rh[2].id).size)
        assertEquals(listOf(ScoreLayout.FLAT), signs(rh[3].id).map { it.codepoint })
        assertEquals(0, signs(rh[4].id).size)
    }

    @Test
    fun noteheadsDotsFlagsAndRests() {
        val s = piece(listOf("C5/h. r/e D5/e"), listOf("r/w"))
        val l = ScoreLayout.of(s)
        val cps = l.glyphs.map { it.codepoint }
        assertTrue(ScoreLayout.HEAD_HALF in cps)
        assertTrue(ScoreLayout.DOT in cps)
        assertTrue(ScoreLayout.REST_EIGHTH in cps)
        assertTrue(ScoreLayout.FLAG_8_DOWN in cps)
        assertTrue(ScoreLayout.REST_WHOLE in cps)
    }

    @Test
    fun secondsInAChordSitOnBothSidesOfTheStem() {
        val s = piece(listOf("C4+D4/w"), listOf("C3/w"))
        val heads = ScoreLayout.of(s).noteheads()
        assertEquals(2, heads.map { it.x }.distinct().size)
    }
}

class PlayAlongTest {
    private val score = piece(listOf("C4/q D4/q E4/q F4/q", "G4/w"), listOf("C3/w", "C3/h G3/h"))
    private val beat = 1000L // 60 bpm

    private fun tempoJudge(hands: Set<Hand> = setOf(Hand.RIGHT), latency: Long = 0) =
        PlayAlongJudge(score, PlaySetup(PlayMode.TEMPO, hands, 0, 1, 60.0, 150, latency)).also { it.start(10_000) }

    @Test
    fun tempoModeJudgesEachNote() {
        val j = tempoJudge()
        assertEquals(5, j.expected.size)
        assertEquals(3, j.accompaniment.size)
        val c = j.onNoteOn(60, 10_000 + 20)
        assertIs<Feedback.Hit>(c); assertEquals(NoteResult.CORRECT, c.result)
        val d = j.onNoteOn(62, 11_000 - 300) as Feedback.Hit
        assertEquals(NoteResult.EARLY, d.result); assertEquals(-300L, d.offsetMs)
        val e = j.onNoteOn(64, 12_000 + 250) as Feedback.Hit
        assertEquals(NoteResult.LATE, e.result)
        assertIs<Feedback.Wrong>(j.onNoteOn(70, 13_000))
        // F4 never played; G4 on time.
        assertEquals(NoteResult.CORRECT, (j.onNoteOn(67, 14_000) as Feedback.Hit).result)
        assertEquals(1, j.advance(15_000).size)
        assertTrue(j.isFinished(20_000))
        val r = j.result()
        assertEquals(NoteResult.MISSED, r.outcomes.values.toList()[3].result)
        val summary = assertNotNull(r.summary)
        assertEquals(5, summary.expectedNotes)
        assertEquals(3, summary.correctNotes) // 4 hits minus 1 wrong press
        assertEquals(2, summary.notesWithinTiming)
        assertFalse(summary.isClean())
        assertEquals(listOf(0), r.problemMeasures)
    }

    @Test
    fun latencyIsSubtractedBeforeJudging() {
        val j = tempoJudge(latency = 120)
        val hit = j.onNoteOn(60, 10_000 + 120 + 10) as Feedback.Hit
        assertEquals(NoteResult.CORRECT, hit.result)
        assertEquals(10L, hit.offsetMs)
    }

    @Test
    fun cleanTempoRunIsClean() {
        val j = tempoJudge(setOf(Hand.RIGHT, Hand.LEFT))
        for (n in j.expected) j.onNoteOn(n.midi, j.expectedTimeMs(n.beat).toLong() + 15)
        val r = j.result()
        assertTrue(r.summary!!.isClean())
        assertEquals(0, r.stumbles)
        assertEquals(listOf(Advice.Clean), Coach.advise(r, 55))
    }

    @Test
    fun silenceLongerThanABeatIsAStumble() {
        val j = tempoJudge()
        j.onNoteOn(60, 10_000)
        // D4 and E4 missed (2 beats of silence), F4 and G4 played.
        j.onNoteOn(65, 13_000)
        j.onNoteOn(67, 14_000)
        assertEquals(1, j.result().stumbles)
    }

    @Test
    fun waitModeWaitsAndMarksCorrectedChords() {
        val s = piece(listOf("C4/q E4+G4/q D4/h", "C4/w"), listOf("C3/w", "C3/w"))
        val j = PlayAlongJudge(s, PlaySetup(PlayMode.WAIT, setOf(Hand.RIGHT), 0, 1, 60.0, 150))
        j.start(0)
        assertEquals(0.0, j.cursorBeat(99_999))
        assertIs<Feedback.Hit>(j.onNoteOn(60, 500))
        assertEquals(1.0, j.cursorBeat(0))
        // Chord: E4 then a wrong key then G4.
        j.onNoteOn(64, 1_000)
        assertIs<Feedback.Wrong>(j.onNoteOn(65, 1_100))
        assertEquals(1.0, j.cursorBeat(0))
        j.onNoteOn(67, 1_200)
        assertEquals(2.0, j.cursorBeat(0))
        // Long pause before D4: a stumble.
        j.onNoteOn(62, 6_000)
        j.onNoteOn(60, 8_000)
        assertTrue(j.isFinished(0))
        val r = j.result()
        assertEquals(NoteResult.CORRECTED, r.outcomes.values.toList()[1].result)
        assertFalse(r.timingJudged)
        assertEquals(1, r.stumbles)
        assertEquals(3, r.summary!!.correctNotes)
    }

    @Test
    fun waitModeChordNeedsKeysHeldTogether() {
        val s = piece(listOf("C4+E4/w"), listOf("C3/w"))
        val j = PlayAlongJudge(s, PlaySetup(PlayMode.WAIT, setOf(Hand.RIGHT), 0, 0, 60.0, 150))
        j.start(0)
        j.onNoteOn(60, 0)
        j.onNoteOff(60)
        j.onNoteOn(64, 100)
        assertFalse(j.isFinished(0))
        j.onNoteOn(60, 200)
        assertTrue(j.isFinished(0))
    }

    @Test
    fun sectionAndHandFilter() {
        val j = PlayAlongJudge(score, PlaySetup(PlayMode.TEMPO, setOf(Hand.LEFT), 1, 1, 60.0, 150))
        assertEquals(listOf(48, 55), j.expected.map { it.midi })
        assertEquals(4.0, j.startBeat)
        assertEquals(listOf(67), j.accompaniment.map { it.midi })
    }

    @Test
    fun coachSuggestsLeftHandAloneAndChaining() {
        val s = piece(listOf("C4/w", "D4/w", "E4/w"), listOf("C3/q D3/q E3/q F3/q", "G3/q F3/q E3/q D3/q", "C3/w"))
        val j = PlayAlongJudge(s, PlaySetup(PlayMode.TEMPO, setOf(Hand.LEFT, Hand.RIGHT), 0, 2, 60.0, 150))
        j.start(0)
        for (n in j.expected) {
            val wrongLeft = n.hand == Hand.LEFT && n.measure in 0..1 && n.beat % 2.0 == 1.0
            if (!wrongLeft) j.onNoteOn(n.midi, j.expectedTimeMs(n.beat).toLong())
        }
        val advice = Coach.advise(j.result(), 50)
        assertTrue(Advice.HandAlone(Hand.LEFT) in advice, "$advice")
        assertTrue(Advice.Chain(0, 1) in advice, "$advice")
    }

    @Test
    fun coachSpotsConsistentlyLatePlaying() {
        val j = tempoJudge()
        for (n in j.expected) j.onNoteOn(n.midi, j.expectedTimeMs(n.beat).toLong() + 200)
        val advice = Coach.advise(j.result(), 55)
        assertTrue(advice.any { it is Advice.ConsistentlyOff && !it.early }, "$advice")
    }

    @Test
    fun spelledPitchesSurviveForTroubleNotes() {
        val s = piece(listOf("F#4/q F#4/q F#4/q G4/q"), listOf("C3/w"))
        val j = PlayAlongJudge(s, PlaySetup(PlayMode.TEMPO, setOf(Hand.RIGHT), 0, 0, 60.0, 150))
        j.start(0)
        j.onNoteOn(67, 3_000)
        val r = j.result()
        assertEquals(listOf(SpelledPitch(Letter.F, 1, 4)), r.troubleNotes)
    }
}

class PieceRulesTest {
    private val score = piece(listOf("C4/q D4/q E4/q F4/q", "G4/w"), listOf("C3/w", "C3/w"), tempo = 60)
    private val cfg = TempoRampConfig()

    private fun run(hands: Set<Hand>, bpm: Double, clean: Boolean, mode: PlayMode = PlayMode.TEMPO, to: Int = 1): com.id.mimikopi.core.playalong.PlayResult {
        val j = PlayAlongJudge(score, PlaySetup(mode, hands, 0, to, bpm, 150))
        j.start(0)
        for ((i, n) in j.expected.withIndex()) {
            if (!clean && i % 2 == 0) continue
            j.onNoteOn(n.midi, j.expectedTimeMs(n.beat).toLong())
        }
        return j.result()
    }

    @Test
    fun threeCleanRunsAtTargetOnTwoDaysPassThePiece() {
        val both = setOf(Hand.LEFT, Hand.RIGHT)
        var p = PieceProgress("t")
        val day1 = LocalDate.of(2026, 10, 1)
        repeat(3) { p = PieceRules.afterRun(p, score, run(both, 60.0, true), false, false, cfg, day = day1).progress }
        assertEquals(listOf("2026-10-01"), p.passDays)
        assertFalse(PieceRules.isPassed(p))
        // A run with the name helper does not count.
        repeat(3) { p = PieceRules.afterRun(p, score, run(both, 60.0, true), true, false, cfg, day = day1.plusDays(1)).progress }
        assertEquals(1, p.passDays.size)
        repeat(2) { p = PieceRules.afterRun(p, score, run(both, 60.0, true), false, false, cfg, day = day1.plusDays(2)).progress }
        val last = PieceRules.afterRun(p, score, run(both, 60.0, true), false, false, cfg, day = day1.plusDays(2))
        assertTrue(last.passedToday)
        assertTrue(last.piecePassed)
    }

    @Test
    fun rampStepsUpPerSectionAndWaitModeOnlyTracksProblemSpots() {
        var p = PieceProgress("t")
        val key = PieceRules.rampKey(setOf(Hand.RIGHT), 0, 1)
        val start = PieceRules.ramp(p, key, 100, cfg).currentBpm
        assertEquals(50, start)
        var newBpm: Int? = null
        repeat(3) {
            val u = PieceRules.afterRun(p, score, run(setOf(Hand.RIGHT), start.toDouble(), true), false, true, cfg)
            p = u.progress; newBpm = u.newBpm ?: newBpm
        }
        // score.tempo = 60 is the target here: 30 -> 40 (min start) -> 45
        assertNotNull(newBpm)
        val w = PieceRules.afterRun(p, score, run(setOf(Hand.RIGHT), 60.0, false, PlayMode.WAIT), false, true, cfg)
        assertEquals(p.ramps, w.progress.ramps)
        val hot = PieceRules.afterRun(w.progress, score, run(setOf(Hand.RIGHT), 60.0, false), false, false, cfg).progress
        assertTrue(PieceRules.hotMeasures(hot).isNotEmpty())
    }
}
