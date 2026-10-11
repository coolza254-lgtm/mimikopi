package com.id.mimikopi.core

import com.id.mimikopi.core.exercise.HandPosition
import com.id.mimikopi.core.exercise.IntervalQuiz
import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.PassRule
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.lesson.TryStep
import com.id.mimikopi.core.music.Direction
import com.id.mimikopi.core.music.Motion
import com.id.mimikopi.core.music.MotionKind
import com.id.mimikopi.core.progress.BenchmarkResult
import com.id.mimikopi.core.progress.Benchmarks
import com.id.mimikopi.core.progress.TrackProgress
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SightReadingGeneratorTest {
    /** learning-system.md 12.2: 1,000 generated pieces per level stay inside the level's rules. */
    @Test
    fun thousandPiecesPerLevelFollowTheirLevel() {
        for ((name, spec) in ScoreGenerators.LEVELS) {
            for (seed in 0L until 1000L) {
                val drill = Drill(ExerciseType.E_READ_PIECE, "t", level = name, alternate = seed % 2 == 0L)
                val file = ScoreGenerators.sightReading(drill, seed)
                val score = Score.from(file) // also checks every bar adds up
                assertTrue(score.measures in spec.barCounts, "$name/$seed bars")
                assertTrue(score.beatsPerMeasure in spec.times, "$name/$seed metre")
                val melodyHand = if (spec.hands == "LEFT") Hand.LEFT else Hand.RIGHT
                val melody = score.events.filter { it.hand == melodyHand && !it.isRest }
                assertTrue(melody.isNotEmpty())
                // Notes stay in the allowed positions; at most one move (L4), at the middle.
                val positions = spec.positions
                val half = score.measures / 2
                val posOf = { m: Int ->
                    val bar = melody.filter { it.measure == m }.flatMap { it.pitches }
                    positions.filter { p -> bar.all { it in p.keys(melodyHand == Hand.RIGHT) } }
                }
                for (m in 0 until score.measures) assertTrue(posOf(m).isNotEmpty() || melody.none { it.measure == m }, "$name/$seed m$m outside positions")
                // Steps between neighbours stay within the level (except across the L4 move).
                melody.zipWithNext().forEach { (a, b) ->
                    val step = abs(a.pitches[0].diatonicIndex - b.pitches[0].diatonicIndex)
                    val acrossMove = spec.moves && a.measure < half && b.measure >= half
                    assertTrue(step in spec.steps || acrossMove, "$name/$seed step $step")
                }
                // Durations come from the level's rhythms; it ends on the tonic.
                val allowed = spec.bars.values.flatten().plus(spec.endings.values.flatten()).flatMap { it.split(" ") }
                    .flatMap { com.id.mimikopi.core.exercise.RhythmCells.CELLS.getValue(it) }.map { abs(it) }.toSet() + setOf(2.0, 4.0, 3.0)
                assertTrue(score.events.all { it.duration in allowed }, "$name/$seed durations")
                assertEquals(ScoreGenerators.tonic(score.keyFifths), melody.last().pitches[0].letter, "$name/$seed tonic")
                // No three repeated notes in a row.
                melody.windowed(3).forEach { w -> assertFalse(w.all { it.pitches == w[0].pitches }, "$name/$seed repeats") }
                if (seed < 50) ScoreLayout.of(score)
            }
        }
    }

    @Test
    fun levelFiveTakesTurnsWhenAlternating() {
        val f = ScoreGenerators.sightReading(Drill(ExerciseType.E_READ_PIECE, "t", level = "L5", alternate = true), 7)
        val s = Score.from(f)
        for (m in 0 until s.measures - 1) {
            val rh = s.events.any { it.hand == Hand.RIGHT && it.measure == m && !it.isRest }
            val lh = s.events.any { it.hand == Hand.LEFT && it.measure == m && !it.isRest }
            assertTrue(rh != lh, "bar $m: exactly one hand plays")
        }
    }
}

class OtherGeneratorsTest {
    @Test
    fun fingerPatternsMapFingersToThePosition() {
        for (hand in listOf("RIGHT", "LEFT")) for (seed in 0L until 200L) {
            val d = Drill(ExerciseType.E_FINGER_PATTERN, "t", items = 8, hand = hand, positions = listOf("C"), bpm = 60)
            val s = Score.from(ScoreGenerators.fingerPatterns(d, seed))
            val right = hand == "RIGHT"
            val notes = s.events.filter { !it.isRest }
            assertEquals(32, notes.size)
            for (e in notes) {
                assertEquals(HandPosition.C.keys(right)[HandPosition.C.indexOf(e.fingers.single(), right)], e.pitches.single())
            }
            notes.zipWithNext().forEach { (a, b) -> assertTrue(a.fingers != b.fingers) }
        }
        // Left hand: little finger on C3, thumb on G3.
        assertEquals(0, HandPosition.C.indexOf(5, right = false))
        assertEquals(4, HandPosition.C.indexOf(1, right = false))
    }

    @Test
    fun rhythmBarsAddUpAndUseOnlyTheVocabulary() {
        for (seed in 0L until 300L) {
            val d = Drill(ExerciseType.E_RHYTHM_TAP, "t", rhythms = listOf("q", "h", "ee", "qr", "ssss", "e.s", "re"), rhythmsLeft = listOf("h", "q"),
                hand = "BOTH", bars = 4, time = listOf(if (seed % 2 == 0L) 4 else 3, 4))
            val s = Score.from(ScoreGenerators.rhythm(d, seed))
            assertEquals(4, s.measures)
            assertTrue(s.events.filter { it.hand == Hand.RIGHT && !it.isRest }.all { it.pitches.single().toString() == "B4" })
            assertTrue(s.events.filter { it.hand == Hand.LEFT }.all { it.duration in setOf(1.0, 2.0) })
            if (seed < 20) ScoreLayout.of(s)
        }
    }

    @Test
    fun beamsReplaceFlagsInsideABeat() {
        val s = Score.from(PieceFile("b", "b", "c", "Original", "L1", 0, listOf(2, 4), 60, listOf("C5/e C5/e C5/s C5/s C5/e"), listOf("r/h")))
        val l = ScoreLayout.of(s)
        assertTrue(l.glyphs.none { it.codepoint in setOf(ScoreLayout.FLAG_8_UP, ScoreLayout.FLAG_8_DOWN, ScoreLayout.FLAG_16_UP, ScoreLayout.FLAG_16_DOWN) })
        val beams = l.lines.filter { it.thickness == ScoreLayout.BEAM_THICKNESS && it.eventId != null }
        assertEquals(3, beams.size) // two primary beams (one per beat) + one secondary for the 16ths
    }

    @Test
    fun fingersAreParsedAndLabelled() {
        val s = Score.from(PieceFile("f", "f", "c", "Original", "L1", 0, listOf(4, 4), 60, listOf("C4/q@1 E4+G4/q@3+5 r/h"), listOf("C3/w@5")))
        assertEquals(listOf(3, 5), s.events.first { it.pitches.size == 2 }.fingers)
        assertEquals(4, ScoreLayout.of(s).labels.size)
    }

    @Test
    fun intervalSetsStayInRangeAndUseAllowedSteps() {
        val d = Drill(ExerciseType.E_INTERVAL_READ, "t", items = 50, intervals = listOf(0, 1, 2), notesPerSet = 4, low = "C4", high = "G5")
        val sets = ScoreGenerators.intervalSets(d, 3)
        assertEquals(50, sets.size)
        for (set in sets) {
            assertEquals(4, set.size)
            assertTrue(set.all { it.midi in 60..79 && it.accidental == 0 })
            set.zipWithNext().forEach { (a, b) -> assertTrue(abs(a.diatonicIndex - b.diatonicIndex) in 0..2) }
        }
        val third = Motion(MotionKind.SKIP, Direction.UP, 2)
        assertTrue(IntervalQuiz.isCorrect(third, Motion(MotionKind.SKIP, Direction.UP, 4), "MOTION"))
        assertFalse(IntervalQuiz.isCorrect(third, Motion(MotionKind.SKIP, Direction.UP, 4), "INTERVAL"))
        assertEquals(listOf(2, 3, 4, 5, 8), IntervalQuiz.numbersFor(listOf(1, 2, 3, 4, 7)))
    }
}

class ExamAndProgressTest {
    private fun lesson(id: String, tracks: List<String>, also: List<Int> = emptyList()) = Lesson(
        id, 1, "t", "g", 10, tracks, emptyList(), listOf("e"), listOf(TryStep("x")),
        listOf(Drill(ExerciseType.E_NOTEID, "a", pool = listOf("C4:treble")), Drill(ExerciseType.E_PULSE, "b")),
        PassRule(0, minAccuracy = 0.9, maxSdMs = 80.0, alsoDrills = also), "p", emptyList(), listOf("tip"),
    )

    @Test
    fun examNeedsEveryRequiredDrillOnTheSameDay() {
        val l = lesson("1.10", listOf("KEYS"), also = listOf(1))
        assertTrue(l.problems().isEmpty(), l.problems().toString())
        val d1 = LocalDate.of(2026, 10, 1)
        var r = LessonRecord(l.id)
        r = ProgressRules.recordDrillMet(r, l, 0, d1, false)
        assertTrue(r.passDays.isEmpty())
        r = ProgressRules.recordDrillMet(r, l, 1, d1.plusDays(1), false)
        assertTrue(r.passDays.isEmpty(), "drills met on different days")
        r = ProgressRules.recordDrillMet(r, l, 0, d1.plusDays(1), false)
        assertEquals(setOf("2026-10-02"), r.passDays)
        val helped = ProgressRules.recordDrillMet(r, l, 1, d1.plusDays(2), true)
        assertEquals(r.drillDays, helped.drillDays)
    }

    @Test
    fun tracksAndWeakestTrack() {
        val a = lesson("1.1", listOf("KEYS"))
        val b = lesson("1.2", listOf("READ_RHYTHM"))
        val c = lesson("1.3", listOf("READ_RHYTHM"))
        val records = mapOf("1.1" to LessonRecord("1.1", passDays = setOf("2026-10-01", "2026-10-02")))
        val t = TrackProgress.summarize(listOf(a, b, c), records)
        assertEquals(2, t.size)
        assertEquals("READ_RHYTHM", TrackProgress.weakest(t)!!.track)
        assertEquals(listOf(TrackProgress.StageSummary(1, 1, 3)), TrackProgress.stages(listOf(a, b, c), records))
    }

    @Test
    fun benchmarkScheduleAndFixedSets() {
        val today = LocalDate.of(2026, 10, 20)
        assertTrue(Benchmarks.isDue(emptyList(), today))
        val r = listOf(BenchmarkResult("2026-10-10"))
        assertFalse(Benchmarks.isDue(r, today))
        assertTrue(Benchmarks.isDue(r, LocalDate.of(2026, 10, 24)))
        assertEquals(Benchmarks.noteIdItems(), Benchmarks.noteIdItems())
        assertEquals(30, Benchmarks.noteIdItems().size)
        val updated = Benchmarks.update(r, today) { it.copy(pulseSdMs = 40.0) }
        assertEquals(2, updated.size)
        assertEquals(40.0, updated.last().pulseSdMs)
        for (level in 1..5) Score.from(ScoreGenerators.sightReading(Benchmarks.sightReadingDrill(level), Benchmarks.sightReadingSeed(level)))
        assertEquals(30.0, Benchmarks.notesPerMinute(15, 30_000))
    }
}
