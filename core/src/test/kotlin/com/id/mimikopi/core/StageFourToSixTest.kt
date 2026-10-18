package com.id.mimikopi.core

import com.id.mimikopi.core.exercise.ChordDrills
import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.interchange.MusicXml
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.practice.PedalCheck
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import com.id.mimikopi.core.theory.Accompaniment
import com.id.mimikopi.core.theory.Chord
import com.id.mimikopi.core.theory.KeyFinder
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.Roman
import com.id.mimikopi.core.theory.StageGenerators
import com.id.mimikopi.core.theory.Templates
import com.id.mimikopi.core.theory.Transpose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordTheoryTest {
    @Test
    fun parsingSpellingAndMatching() {
        val am7 = Chord.parse("Am7")!!
        assertEquals(setOf(9, 0, 4, 7), am7.pitchClasses)
        assertEquals("maj7", Chord.parse("FM7")!!.quality)
        assertEquals("m7b5", Chord.parse("Bø")!!.quality)
        val slash = Chord.parse("G/B")!!
        assertEquals(11, slash.bass!!.pc)
        assertNull(Chord.parse("H7"))
        assertNull(Chord.parse("Cxyz"))
        assertTrue(Chord.matches(am7, setOf(57, 60, 64, 67)))
        assertTrue(Chord.matches(am7, setOf(48, 55, 57, 64))) // any inversion and octave
        assertFalse(Chord.matches(am7, setOf(57, 60, 64))) // the 7th is missing
        assertFalse(Chord.matches(Chord.parse("C")!!, setOf(60, 64, 67, 69)))
        assertTrue(Chord.matches(slash, setOf(47, 55, 62, 67)))
        assertFalse(Chord.matches(slash, setOf(55, 59, 62))) // B must be the lowest note
        // A 13th chord may leave out its 5th.
        assertTrue(Chord.matches(Chord.parse("G13")!!, setOf(43, 53, 57, 59, 64)))
        assertEquals("Bb", Keys.tonic(-2).toString())
        assertEquals(listOf("Eb", "F", "G", "Ab", "Bb", "C", "D"), Keys.scale(-3).map { it.toString() })
        assertEquals("F#", Keys.spell(6, 2).toString())
        assertEquals("Gb", Keys.spell(6, -3).toString())
        assertEquals("Cb4", Keys.spellMidi(59, -6).toString()) // B-natural is C-flat in G-flat major, same octave as C4
        assertEquals("B3", Keys.spellMidi(59, 0).toString())
    }

    @Test
    fun romanNumerals() {
        fun r(c: String, k: Int = 0) = Roman.analyze(Chord.parse(c)!!, k)
        assertEquals("IV", r("F").numeral)
        assertEquals("vi7", r("Am7").numeral)
        assertEquals("IVmaj7", r("Fmaj7").numeral)
        assertEquals("V7/vi", r("E7").numeral)
        assertTrue(r("E7").secondaryDominant)
        assertEquals("V7/V", r("D7").numeral)
        assertEquals("♭VImaj7", r("Abmaj7").numeral)
        assertTrue(r("Abmaj7").borrowed)
        assertEquals("I/3", r("C/E").numeral)
        assertEquals("vi", r("Em", 1).numeral)
        assertEquals("viiø7", r("Bm7b5").numeral)
        assertTrue(r("G7").diatonic)
        assertEquals(Chord.parse("Am")!!, StageGenerators.degreeChord("vi", 0))
        assertEquals(Chord.parse("G7")!!, StageGenerators.degreeChord("V7", 0))
        assertEquals(Chord.parse("Fmaj7")!!, StageGenerators.degreeChord("IVmaj7", 0))
        assertEquals(Chord.parse("Gm7")!!, StageGenerators.degreeChord("ii7", -1))
    }

    @Test
    fun voiceLeadingAndKeyFinding() {
        val c = Chord.parse("C")!!.voicing()
        val f = Chord.parse("F")!!.closestTo(c)
        assertTrue(f.sumOf { n -> c.minOf { kotlin.math.abs(it - n) } } <= 3, "C -> F moves little: $c -> $f")
        val g = KeyFinder.rankNotes(listOf(67, 69, 71, 72, 74, 76, 78, 79, 67, 71, 74, 79, 66))
        assertEquals(7, g.first().tonicPc)
        assertFalse(g.first().minor)
        assertEquals(1, KeyFinder.fifthsOf(g.first()))
        val aMinor = KeyFinder.rankNotes(listOf(57, 60, 64, 57, 64, 59, 62, 60, 57, 69, 72, 68, 57))
        assertEquals(9, aMinor.first().tonicPc)
        assertTrue(aMinor.first().minor)
    }
}

class StageGeneratorTest {
    @Test
    fun accompanimentLadderFillsEveryBar() {
        val chords = StageGenerators.progression(StageGenerators.PROGRESSIONS[5], -3, 8)
        for (level in 1..7) {
            val lh = Accompaniment.leftHand(chords.mapIndexed { i, c -> i * 16 to c }, level, 8 * 16)
            assertTrue(lh.isNotEmpty())
            assertTrue(lh.all { it.end <= 128 && it.pitches.all { p -> p.midi in 38..70 } }, "level $level range")
            for (bar in 0 until 8) assertTrue(lh.any { it.start / 16 == bar }, "level $level bar $bar")
            for (seed in 0L until 5L) {
                val d = Drill(ExerciseType.E_COMP_PATTERN, "t", pattern = level, progression = "ทางหลวง (王道進行)", keys = listOf(0, -3, 4), bars = 8, bpm = 80)
                val s = Score.from(ScoreGenerators.comp(d, seed))
                assertEquals(8, s.measures)
                assertEquals(8, s.chordSymbols.size)
                ScoreLayout.of(s)
            }
        }
    }

    @Test
    fun levelsSixToEight() {
        for (level in 6..8) for (seed in 0L until 300L) {
            val d = Drill(ExerciseType.E_READ_PIECE, "t", level = "L$level", bpm = 80)
            val s = Score.from(ScoreGenerators.read(d, seed))
            assertTrue(s.keyFifths in -4..4)
            val rh = s.events.filter { it.hand == Hand.RIGHT && !it.isRest }
            assertTrue(rh.isNotEmpty())
            when (level) {
                6 -> assertTrue(s.events.any { it.hand == Hand.LEFT && !it.isRest } && (s.beatsPerMeasure to s.beatUnit) in setOf(4 to 4, 6 to 8))
                7 -> assertTrue(s.events.any { it.hand == Hand.LEFT && it.pitches.size >= 3 }, "L7 left-hand triads")
                8 -> {
                    assertTrue(s.chordSymbols.isNotEmpty() && s.events.none { it.hand == Hand.LEFT && !it.isRest }, "L8 lead sheet")
                    assertTrue(rh.all { it.pitches.all { p -> p.midi in 60..74 } }, "melody C4-D5")
                }
            }
            assertEquals(Keys.tonic(s.keyFifths).pc, rh.last().pitches.last().midi % 12, "ends on the tonic (L$level)")
            if (seed < 10) ScoreLayout.of(s)
        }
    }

    @Test
    fun transposeAndChordDrills() {
        val (orig, moved) = StageGenerators.transposePair(0, 3, 7)
        val a = Score.from(orig).sounding().map { it.midi }
        val b = Score.from(moved).sounding().map { it.midi }
        assertEquals(a.map { it - 3 }, b) // C -> A is down a minor third
        assertEquals(3, Score.from(moved).keyFifths)
        val t = Transpose.piece(Templates.capstoneA("c", "Mine", 0, 90, 2), 3, -3)
        assertEquals("Abmaj7", Score.from(t).chordSymbols.first().text)
        val build = ChordDrills.build(Drill(ExerciseType.E_CHORD_BUILD, "t", items = 20, keys = listOf(0, 1), qualities = listOf("", "m", "dim")), 3)
        assertEquals(20, build.size)
        build.zipWithNext().forEach { (x, y) -> assertTrue(x.chord != y.chord) }
        val roman = ChordDrills.build(Drill(ExerciseType.E_CHORD_BUILD, "t", items = 10, keys = listOf(-1), degrees = listOf("ii7", "V7")), 1)
        assertTrue(roman.all { it.chord.symbol in setOf("Gm7", "C7") && "ในคีย์" in it.label })
        val changes = ChordDrills.changes(Drill(ExerciseType.E_CHORD_CHANGE, "t", progression = "I V vi IV", keys = listOf(1)))
        assertEquals(listOf("G", "D", "Em", "C"), changes.map { it.chord.symbol })
    }

    @Test
    fun capstoneTemplateAndMarks() {
        val s = Score.from(Templates.capstoneA("c", "My song", 0, 92, 3))
        assertEquals(32, s.measures)
        assertEquals("Intro", s.marks[0])
        assertEquals("Chorus", s.marks[16])
        assertEquals("Fmaj7", s.chordSymbols.first().text)
        assertTrue(s.events.none { it.hand == Hand.RIGHT && !it.isRest })
        assertTrue(ScoreLayout.of(s).labels.any { it.kind == ScoreLayout.LabelKind.MARK && it.text == "Verse" })
        val back = MusicXml.read(MusicXml.write(s).toByteArray(), "c").toScore()
        assertEquals(s.marks, back.marks)
        assertEquals(s.chordSymbols.map { it.text }, back.chordSymbols.map { it.text })
    }

    @Test
    fun pedalChanges() {
        val chords = listOf(0L, 2000L, 4000L)
        val good = PedalCheck.analyze(chords, listOf(10L to true, 2050L to false, 2200L to true, 4100L to false, 4300L to true))
        assertEquals(2, good.good)
        assertTrue(good.sawPedal)
        assertEquals(0, PedalCheck.analyze(chords, emptyList()).good)
        assertFalse(PedalCheck.analyze(chords, emptyList()).sawPedal)
    }
}

class ChordScoringTest {
    private val rule = com.id.mimikopi.core.lesson.PassRule(drill = 0, minAccuracy = 0.9, maxSecondsPerItem = 6.0, minCount = 14)
    private fun a(ok: Boolean, ms: Long = 2000) = com.id.mimikopi.core.exercise.Answer(ok, ms, firstTry = true)

    @Test
    fun chordBuildCountsOnlyFastFirstTryAnswers() {
        val s = com.id.mimikopi.core.exercise.Scoring
        assertTrue(s.chordBuildPassed(List(9) { a(true) } + a(false), rule, 10))
        assertFalse(s.chordBuildPassed(List(8) { a(true) } + a(true, 7000) + a(false), rule, 10))
        assertFalse(s.chordBuildPassed(List(9) { a(true) }, rule, 10)) // too few items
        assertTrue(s.chordChangePassed(14, rule))
        assertFalse(s.chordChangePassed(13, rule))
    }
}
