package com.id.mimikopi.core

import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.exam.Band
import com.id.mimikopi.core.exam.ExamLog
import com.id.mimikopi.core.exam.ExamPart
import com.id.mimikopi.core.exam.ExamScales
import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.exam.PartMark
import com.id.mimikopi.core.exam.ScaleItem
import com.id.mimikopi.core.exam.ScaleKind
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.theory.Keys
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamTest {
    private fun item(kind: ScaleKind, tonic: String, octaves: Int = 1, together: Boolean = false) = ScaleItem(kind, tonic, octaves, together)
    private fun names(item: ScaleItem, right: Boolean = true) = ExamScales.ascending(item, right).map { it.toString() }

    @Test
    fun everyExamPieceIsInTheLibraryAtTheGradesLevel() {
        val dir = File("../content/pieces")
        Exams.GRADES.forEach { g ->
            assertTrue(g.pieces.size >= 2, "grade ${g.n} needs two pieces to choose")
            g.pieces.forEach { id ->
                val f = File(dir, "$id.json")
                assertTrue(f.exists(), "missing piece $id")
                val level = PieceFile.parse(f.readText()).level.removePrefix("L").toInt()
                val expected = if (g.n == 0) 1..2 else (g.n + 2)..(g.n + 2)
                assertTrue(level in expected, "$id is L$level, grade ${g.n} expects $expected")
            }
        }
    }

    @Test
    fun scalesAreSpelledAsTheKeyAndHarmonicMinorRaisesTheSeventh() {
        assertEquals(listOf("Eb4", "F4", "G4", "Ab4", "Bb4", "C5", "D5", "Eb5"), names(item(ScaleKind.MAJOR, "Eb")))
        assertEquals(listOf("A4", "B4", "C5", "D5", "E5", "F5", "G#5", "A5"), names(item(ScaleKind.MINOR, "A")))
        assertEquals(listOf("G4", "A4", "Bb4", "C5", "D5", "Eb5", "F#5", "G5"), names(item(ScaleKind.MINOR, "G")))
        assertEquals(listOf("C2", "E2", "G2", "C3", "E3", "G3", "C4"), names(item(ScaleKind.ARP_MAJOR, "C", 2), right = false))
        assertEquals(listOf("A4", "C5", "E5", "A5"), names(item(ScaleKind.ARP_MINOR, "A")))
        assertEquals(13, names(item(ScaleKind.CHROMATIC, "D")).size)
    }

    @Test
    fun fingeringIsTheStandardOne() {
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 5), ExamScales.fingers(item(ScaleKind.MAJOR, "C"), true))
        assertEquals(listOf(5, 4, 3, 2, 1, 3, 2, 1, 4, 3, 2, 1, 3, 2, 1), ExamScales.fingers(item(ScaleKind.MAJOR, "C", 2), false))
        assertEquals(listOf(1, 2, 3, 4, 1, 2, 3, 4), ExamScales.fingers(item(ScaleKind.MAJOR, "F"), true))
        assertEquals(listOf(2, 1, 2, 3, 1, 2, 3, 4), ExamScales.fingers(item(ScaleKind.MAJOR, "Bb"), true))
        assertEquals(listOf(3, 2, 1, 4, 3, 2, 1, 3), ExamScales.fingers(item(ScaleKind.MAJOR, "Eb"), false))
        assertEquals(listOf(3, 4, 1, 2, 3, 1, 2, 3), ExamScales.fingers(item(ScaleKind.MAJOR, "Ab"), true))
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 5), ExamScales.fingers(item(ScaleKind.ARP_MAJOR, "C", 2), true))
        assertEquals(listOf(5, 4, 2, 1, 4, 2, 1), ExamScales.fingers(item(ScaleKind.ARP_MAJOR, "C", 2), false))
        assertEquals(listOf(5, 3, 2, 1), ExamScales.fingers(item(ScaleKind.ARP_MAJOR, "D"), false))
        assertEquals(listOf(1, 3, 1, 3, 1, 2, 3, 1, 3, 1, 3, 1, 2), ExamScales.fingers(item(ScaleKind.CHROMATIC, "C"), true))
        assertEquals(listOf(1, 3, 2, 1, 3, 1, 3, 1, 3, 2, 1, 3, 1), ExamScales.fingers(item(ScaleKind.CHROMATIC, "D"), false))
    }

    @Test
    fun everyGradeScaleIsSupportedAndMakesAValidScore() {
        Exams.GRADES.forEach { g ->
            g.scales.forEach { s ->
                assertTrue(ExamScales.supported(s), "grade ${g.n}: ${s.id} has no standard fingering here")
                val score = Score.from(Exams.scalePractice(g, s))
                val notes = score.events.filter { !it.isRest }
                assertTrue(notes.all { it.fingers.size == it.pitches.size }, "${s.id}: every note has a finger")
                // Never the same finger twice in a row in one hand (scales and arpeggios move on every note).
                Hand.entries.forEach { h ->
                    val f = notes.filter { it.hand == h }.map { it.fingers.single() }
                    assertTrue(f.zipWithNext().none { (a, b) -> a == b }, "${s.id} ${h.name}: repeated finger in $f")
                }
                val ups = ExamScales.ascending(s, true)
                assertEquals(ups.first().letter, ups.last().letter, "${s.id} ends on its starting letter")
            }
        }
    }

    @Test
    fun descendingChromaticUsesFlats() {
        val g = Exams.grade(2)!!
        val s = g.scales.first { it.kind == ScaleKind.CHROMATIC }
        val rh = Score.from(Exams.scalePractice(g, s)).events.filter { it.hand == Hand.RIGHT && !it.isRest }.map { it.pitches.single() }
        val down = rh.drop(13)
        assertTrue(down.none { it.accidental > 0 } && down.any { it.accidental < 0 })
        assertTrue(rh.take(13).none { it.accidental < 0 })
    }

    @Test
    fun theScalesPartAsksTheGradesNumberWithAnArpeggio() {
        Exams.GRADES.forEach { g ->
            val asked = Exams.askedScales(g, 7L)
            assertEquals(g.ask, asked.size)
            assertTrue(asked.any { it.kind == ScaleKind.ARP_MAJOR || it.kind == ScaleKind.ARP_MINOR } || g.scales.none { it.kind.name.startsWith("ARP") })
            assertEquals(asked, Exams.askedScales(g, 7L))
            val piece = Exams.scalesPiece(g, 7L)
            val score = Score.from(piece)
            assertEquals(g.scaleBpm, score.tempo)
            assertEquals(asked.first().title(), piece.marks.first())
            assertEquals(asked.size, piece.marks.count { it.isNotEmpty() })
        }
        assertTrue((1..20).map { Exams.askedScales(Exams.grade(5)!!, it.toLong()) }.distinct().size > 5, "different exams ask different scales")
    }

    @Test
    fun popPiecesAreFixedPlayableAndEndOnTheTonic() {
        Exams.GRADES.forEach { g ->
            val p = Exams.pop(g)
            assertEquals(p, Exams.pop(g), "the same every time, so it can be prepared")
            val score = Score.from(p)
            assertEquals(g.pop.bars, score.measures)
            assertEquals(g.pop.key, score.keyFifths)
            val rh = score.events.filter { it.hand == Hand.RIGHT && !it.isRest }
            val lh = score.events.filter { it.hand == Hand.LEFT && !it.isRest }
            assertTrue(lh.isNotEmpty())
            assertEquals(Keys.tonic(g.pop.key).pc, rh.last().pitches.single().midi % 12)
            // The left hand never needs a key the right hand is holding.
            lh.forEach { l ->
                rh.filter { it.onset < l.end - 1e-9 && l.onset < it.end - 1e-9 }.forEach { r ->
                    assertTrue(l.pitches.none { lp -> r.pitches.any { it.midi == lp.midi } }, "grade ${g.n} m${l.measure + 1}: hands share a key")
                }
            }
            assertTrue(p.chords.isNotEmpty(), "chord symbols over the pop piece")
        }
    }

    @Test
    fun sightReadingAndAuralAreNewEachTimeButValid() {
        Exams.GRADES.forEach { g ->
            val a = Score.from(Exams.sightPiece(g, 1L))
            val b = Score.from(Exams.sightPiece(g, 2L))
            assertTrue(a.events.isNotEmpty() && b.events.isNotEmpty())
            val round = Exams.auralRound(g, Random(3))
            assertEquals(g.aural.count, round.size)
            assertTrue(round.all { it.item in g.aural.items })
            assertTrue(g.aural.items.all { it in EarCourse.ALL_ITEMS }, "grade ${g.n} aural uses course items")
            assertTrue(g.aural.items.toSet() == round.map { it.item }.toSet() || g.aural.items.size > g.aural.count)
        }
    }

    @Test
    fun marksFollowTheAnchors() {
        assertEquals(30, Exams.playedMarks(ExamPart.PIECE_1, 1.0, 1.0, 0))
        assertEquals(20, Exams.playedMarks(ExamPart.POP, 0.8, 0.8, 0))
        assertEquals(27, Exams.playedMarks(ExamPart.PIECE_2, 0.96, 0.9, 0))
        assertEquals(21, Exams.playedMarks(ExamPart.SCALES, 1.0, 1.0, 0))
        assertEquals(14, Exams.playedMarks(ExamPart.SCALES, 0.8, 0.8, 0))
        assertEquals(14, Exams.playedMarks(ExamPart.SIGHT, 0.7, 0.7, 0))
        assertEquals(21, Exams.playedMarks(ExamPart.SIGHT, 1.0, 1.0, 0))
        // Stops cost marks; a part played badly still earns a third; nothing played earns nothing.
        assertTrue(Exams.playedMarks(ExamPart.PIECE_1, 0.95, 0.9, 3) < Exams.playedMarks(ExamPart.PIECE_1, 0.95, 0.9, 0))
        assertEquals(10, Exams.playedMarks(ExamPart.PIECE_1, 0.2, 0.1, 5))
        assertEquals(0, Exams.playedMarks(ExamPart.PIECE_1, 0.0, 0.0, 0))
        assertEquals(12, Exams.auralMarks(8, 12))
        assertEquals(18, Exams.auralMarks(12, 12))
        assertEquals(Band.PASS, Exams.band(100))
        assertEquals(Band.BELOW, Exams.band(99))
        assertEquals(Band.MERIT, Exams.band(120))
        assertEquals(Band.DISTINCTION, Exams.band(130))
        assertEquals(Band.MERIT, Exams.partBand(ExamPart.AURAL, 15))
        assertEquals(150, ExamPart.entries.sumOf { it.max })
    }

    @Test
    fun aSittingTakesEachPartOnceAndOnlyCountsWhenFinished() {
        val s = ExamLog.start(2, listOf("ode-to-joy", "sakura-sakura"), 1_000, "2026-10-18", 5)
        var log = listOf(s)
        assertEquals(s, ExamLog.active(log, "2026-10-18"))
        assertNull(ExamLog.active(log, "2026-10-19"), "an exam is finished on the day it starts")
        log = ExamLog.record(log, s.id, ExamPart.PIECE_1, PartMark(24, 2_000, 0.95, 0.85, 0))
        log = ExamLog.record(log, s.id, ExamPart.PIECE_1, PartMark(30, 3_000, 1.0, 1.0, 0))
        assertEquals(24, log.single().mark(ExamPart.PIECE_1)!!.marks, "the first take stands")
        assertTrue(ExamLog.finished(log).isEmpty())
        ExamPart.entries.drop(1).forEachIndexed { i, p -> log = ExamLog.record(log, s.id, p, PartMark(p.pass, 4_000L + i)) }
        val done = log.single()
        assertTrue(done.complete)
        assertEquals(24 + 20 + 20 + 14 + 14 + 12, done.total)
        assertEquals(Band.PASS, Exams.band(done.total))
        assertEquals(done, ExamLog.best(log, 2))
        assertNull(ExamLog.active(log, "2026-10-18"))
        assertEquals(log, ExamLog.parse(ExamLog.encode(log)))

        val other = ExamLog.abandon(listOf(ExamLog.start(3, listOf("silent-night", "twinkle-twinkle"), 9_000, "2026-10-18", 1)), "9000-3")
        assertNull(ExamLog.active(other, "2026-10-18"))
    }

    @Test
    fun syncMergeKeepsTheEarlierTakeAndIsOrderFree() {
        val s = ExamLog.start(1, listOf("row-row-row", "yankee-doodle"), 1_000, "2026-10-18", 5)
        val a = ExamLog.record(listOf(s), s.id, ExamPart.SCALES, PartMark(15, 2_000))
        val b = ExamLog.record(ExamLog.record(listOf(s), s.id, ExamPart.SCALES, PartMark(19, 2_500)), s.id, ExamPart.AURAL, PartMark(14, 3_000))
        val ab = ExamLog.merge(a, b)
        assertEquals(ab, ExamLog.merge(b, a))
        assertEquals(ab, ExamLog.merge(ab, ab))
        assertEquals(15, ab.single().mark(ExamPart.SCALES)!!.marks)
        assertEquals(14, ab.single().mark(ExamPart.AURAL)!!.marks)
    }

    @Test
    fun examMaterialOpensAsPracticePieces() {
        assertNotNull(Exams.practicePiece("exam-pop-4"))
        val g = Exams.grade(3)!!
        assertEquals(g.scales.first().title(), Exams.practicePiece("exam-scale-3-${g.scales.first().id}")!!.title)
        assertNull(Exams.practicePiece("exam-scale-3-major-C-1-hs"), "only the grade's own scales")
        assertNull(Exams.practicePiece("fur-elise"))
        assertEquals(g.scales.first(), ScaleItem.parse(g.scales.first().id))
    }
}
