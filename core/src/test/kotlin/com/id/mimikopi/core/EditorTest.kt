package com.id.mimikopi.core

import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.interchange.ImportException
import com.id.mimikopi.core.interchange.MidiFile
import com.id.mimikopi.core.interchange.MusicXml
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.playalong.PlayAlongJudge
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.playalong.PlaySetup
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun p(s: String) = SpelledPitch.parse(s)

class TimelineTest {
    private val meta = ScoreMeta("u1", "Test")

    @Test
    fun overwriteEntryKeepsMeasuresFull() {
        var t = Timeline.empty(meta, 1)
        t = t.write(Hand.RIGHT, 0, 4, listOf(p("C4")))
        t = t.write(Hand.RIGHT, 4, 4, listOf(p("D4")))
        t = t.write(Hand.RIGHT, 8, 8, listOf(p("E4")))
        assertEquals(listOf("C4/q D4/q E4/h"), t.toPieceFile().rh)
        assertEquals(listOf("r/w"), t.toPieceFile().lh)
        // Overwriting D4 with a half note eats the start of E4 (which is removed) and leaves a rest.
        t = t.write(Hand.RIGHT, 4, 8, listOf(p("F4")))
        assertEquals(listOf("C4/q F4/h r/q"), t.toPieceFile().rh)
        // A note crossing the bar line is split and tied; the score grows.
        t = t.write(Hand.RIGHT, 12, 8, listOf(p("G4")))
        val f = t.toPieceFile()
        assertEquals(2, f.rh.size)
        assertEquals("C4/q F4/h G4/q~", f.rh[0])
        assertEquals("G4/q r/q r/h", f.rh[1])
        Score.from(f)
    }

    @Test
    fun chordsTiesRestsAndMeasures() {
        var t = Timeline.empty(meta, 2).write(Hand.LEFT, 0, 16, listOf(p("C3")))
        t = t.addPitch(Hand.LEFT, 0, p("G3")).toggleTie(Hand.LEFT, 3)
        t = t.write(Hand.LEFT, 16, 16, listOf(p("C3"), p("G3")))
        t = t.setChord(0, "C").setChord(8, "G7").toggleRepeatStart(0).toggleRepeatEnd(1)
        val f = t.toPieceFile()
        assertEquals(listOf("C3+G3/w~", "C3+G3/w"), f.lh)
        assertEquals(listOf("C@0 G7@2"), f.chords)
        assertEquals(listOf("start", "end"), f.repeats)
        val s = Score.from(f)
        assertEquals(1, s.sounding().count { it.midi == 48 })
        assertEquals(8.0, s.sounding().first { it.midi == 48 }.duration)
        assertEquals(listOf(0, 1, 0, 1), s.playOrder())
        // Insert and delete measures.
        val ins = t.insertMeasure(0)
        assertEquals(3, ins.measures)
        assertEquals(16, ins.lh.first().start)
        assertEquals(setOf(1), ins.repeatStart)
        val del = ins.deleteMeasure(0)
        assertEquals(t.lh, del.lh)
        // Clearing makes a rest; odd lengths are written as tied pieces.
        assertEquals(listOf("r/w", "C3+G3/w"), t.clear(Hand.LEFT, 0).toPieceFile().lh)
        assertEquals(listOf(4, 1), Timeline.decompose(5))
        val odd = Timeline.empty(meta, 1).write(Hand.RIGHT, 0, 5, listOf(p("E4")))
        assertEquals(listOf("E4/q~ E4/s r/s r/e r/h"), odd.toPieceFile().rh)
    }

    @Test
    fun piecesSurviveTheRoundTripThroughTheTimeline() {
        for (f in File("../content/pieces").listFiles()!! + File("../content/exercises").listFiles()!!) {
            val piece = PieceFile.parse(f.readText())
            val back = Timeline.fromPiece(piece).toPieceFile()
            assertEquals(Score.from(piece).sounding(), Score.from(back).sounding(), f.name)
        }
    }
}

class InterchangeTest {
    private val files = File("../content/pieces").listFiles()!! + File("../content/exercises").listFiles()!!

    @Test
    fun musicXmlRoundTripKeepsEveryNote() {
        for (f in files) {
            val piece = PieceFile.parse(f.readText())
            val score = Score.from(piece)
            val xml = MusicXml.write(score)
            val back = MusicXml.read(xml.toByteArray(), "x").toScore()
            assertEquals(score.sounding().map { Triple(it.onset, it.duration, it.midi) }, back.sounding().map { Triple(it.onset, it.duration, it.midi) }, f.name)
            assertEquals(score.events.flatMap { it.pitches }.toSet(), back.events.flatMap { it.pitches }.toSet(), "${f.name} spelling")
            assertEquals(score.keyFifths, back.keyFifths)
            assertEquals(score.beatsPerMeasure, back.beatsPerMeasure)
            assertEquals(score.tempo, back.tempo)
            assertEquals(score.title, back.title)
        }
    }

    @Test
    fun musicXmlKeepsChordSymbolsRepeatsTiesAndClefs() {
        val t = Timeline.empty(ScoreMeta("a", "A & B", "me", key = -1, time = listOf(3, 4), tempo = 96, lhClef = "treble"), 2)
            .write(Hand.RIGHT, 0, 20, listOf(p("Bb4"))).setChord(0, "F").setChord(4, "Bb/D").setChord(12, "C7")
            .toggleRepeatStart(0).toggleRepeatEnd(1)
        val score = t.toScore()
        val back = MusicXml.read(MusicXml.write(score).toByteArray(), "a").toScore()
        assertEquals(score.chordSymbols, back.chordSymbols)
        assertEquals(setOf(0), back.repeatStart)
        assertEquals(setOf(1), back.repeatEnd)
        assertEquals(score.sounding(), back.sounding())
        assertEquals(com.id.mimikopi.core.notation.Clef.TREBLE, back.lhClef)
        assertEquals("A & B", back.title)
    }

    @Test
    fun musicXmlFromOtherProgramsWithVoicesPickupAndBackup() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1"><movement-title>Pickup</movement-title>
<part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
<part id="P1">
<measure number="0" implicit="yes"><attributes><divisions>2</divisions><key><fifths>1</fifths></key><time><beats>4</beats><beat-type>4</beat-type></time><staves>2</staves>
<clef number="1"><sign>G</sign><line>2</line></clef><clef number="2"><sign>F</sign><line>4</line></clef></attributes>
<note><pitch><step>D</step><octave>4</octave></pitch><duration>2</duration><voice>1</voice><type>quarter</type><staff>1</staff></note>
</measure>
<measure number="1">
<note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice><type>half</type><staff>1</staff></note>
<note><chord/><pitch><step>B</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice><type>half</type><staff>1</staff></note>
<note><pitch><step>F</step><alter>1</alter><octave>4</octave></pitch><duration>4</duration><voice>1</voice><type>half</type><staff>1</staff></note>
<backup><duration>8</duration></backup>
<note><pitch><step>E</step><octave>5</octave></pitch><duration>8</duration><voice>2</voice><type>whole</type><staff>1</staff></note>
<backup><duration>8</duration></backup>
<note><pitch><step>G</step><octave>2</octave></pitch><duration>8</duration><voice>5</voice><type>whole</type><staff>2</staff></note>
</measure></part></score-partwise>"""
        val s = MusicXml.read(xml.toByteArray(), "p").toScore()
        assertEquals("Pickup", s.title)
        assertEquals(1, s.keyFifths)
        // The pickup sits at the end of the first measure; the second voice (E5) is left out.
        assertEquals(listOf(3.0 to 62, 4.0 to 67, 4.0 to 71, 6.0 to 66), s.sounding().filter { it.hand == Hand.RIGHT }.map { it.onset to it.midi })
        assertTrue(MusicXml.lastWarnings >= 1)
        assertEquals(listOf(4.0 to 43), s.sounding().filter { it.hand == Hand.LEFT }.map { it.onset to it.midi })
    }

    @Test
    fun badFilesGiveReadableErrors() {
        assertFailsWith<ImportException> { MusicXml.read("not xml".toByteArray(), "x") }
        assertFailsWith<ImportException> { MidiFile.read(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), "x") }
        val triplet = """<score-partwise><part-list/><part id="P1"><measure number="1"><attributes><divisions>3</divisions></attributes>
            <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note></measure></part></score-partwise>"""
        assertFailsWith<ImportException> { MusicXml.read(triplet.toByteArray(), "t") }
    }

    @Test
    fun midiRoundTripKeepsNotesAndHands() {
        for (f in files) {
            val score = Score.from(PieceFile.parse(f.readText()))
            if (score.repeatEnd.isNotEmpty()) continue
            val back = MidiFile.read(MidiFile.write(score), "m").toScore()
            fun key(s: Score) = s.sounding().map { listOf(it.onset, it.duration, it.midi.toDouble(), it.hand.ordinal.toDouble()) }
            // Hands come back by track; an empty left hand leaves one note track, split at Middle C.
            if (score.events.any { it.hand == Hand.LEFT && !it.isRest } && score.events.any { it.hand == Hand.RIGHT && !it.isRest }) {
                assertEquals(key(score), key(back), f.name)
            } else {
                assertEquals(score.sounding().map { it.onset to it.midi }, back.sounding().map { it.onset to it.midi }, f.name)
            }
            assertEquals(score.tempo, back.tempo)
            assertEquals(score.beatsPerMeasure, back.beatsPerMeasure)
        }
    }

    @Test
    fun midiExportPlaysRepeats() {
        val t = Timeline.empty(ScoreMeta("r", "R"), 2).write(Hand.RIGHT, 0, 16, listOf(p("C4"))).write(Hand.RIGHT, 16, 16, listOf(p("D4")))
            .toggleRepeatEnd(1)
        val back = MidiFile.read(MidiFile.write(t.toScore()), "r").toScore()
        assertEquals(listOf(60, 62, 60, 62), back.sounding().map { it.midi })
    }
}

class NotationExtrasTest {
    @Test
    fun tiesChordSymbolsRepeatsAndSystems() {
        val t = Timeline.empty(ScoreMeta("n", "N"), 8).write(Hand.RIGHT, 0, 24, listOf(p("C5"), p("E5")))
            .setChord(0, "Am").toggleRepeatStart(0).toggleRepeatEnd(1)
        val s = t.toScore()
        val l = ScoreLayout.of(s)
        assertEquals(2, l.ties.size) // two notes tied across the bar line
        assertTrue(l.labels.any { it.kind == ScoreLayout.LabelKind.CHORD && it.text == "Am" })
        assertTrue(l.lines.count { it.thickness == ScoreLayout.THICK_BARLINE } >= 3)
        val systems = ScoreLayout.systems(s, 60.0)
        assertEquals((0 until 8).toList(), systems.flatMap { it.toList() })
        assertTrue(systems.size >= 2)
        val slice = s.slice(2, 3)
        assertEquals(2, slice.measures)
        ScoreLayout.of(slice)
    }

    @Test
    fun tiedContinuationsAreNotExpectedAgain() {
        val s = Timeline.empty(ScoreMeta("t", "T"), 2).write(Hand.RIGHT, 0, 24, listOf(p("C4"))).toScore()
        val j = PlayAlongJudge(s, PlaySetup(PlayMode.WAIT, setOf(Hand.RIGHT), 0, 1, 60.0, 150))
        assertEquals(1, j.expected.size)
    }
}
