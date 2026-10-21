package com.id.mimikopi.ui.reader

import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import com.id.mimikopi.pieces.ClassicalRepertoire
import java.io.File
import kotlin.test.*
import org.junit.Test

class ClassicalRepertoireTest {
    private fun score(id: String) = Score.from(PieceFile.parse(File("../content/pieces/$id.json").readText()))

    @Test fun everyStepResolvesToPlayableMusicAndKeepsItsIndependentProgressId() {
        val steps = ClassicalRepertoire.steps
        assertEquals(steps.size, steps.map { it.id }.distinct().size)
        for (step in steps) {
            val s = score(step.id)
            assertEquals(step.id, s.id)
            assertTrue(step.startHand in setOf("LEFT", "RIGHT"))
            assertTrue(s.events.any { it.hand.name == step.startHand && !it.isRest })
            val layout = ScoreLayout.of(s)
            assertEquals(s.events.sumOf { it.pitches.size }, layout.noteheads().size)
            for (hand in Hand.entries) for (m in 0 until s.measures) {
                assertEquals(s.measureBeats, s.events.filter { it.hand == hand && it.measure == m }.sumOf { it.duration })
            }
        }
    }

    @Test fun beginnerOdeUsesOnlyFiveAdjacentWhiteKeysAndNoFastNotes() {
        val melody = score("ode-to-joy-first").events.filter { it.hand == Hand.RIGHT && !it.isRest }
        assertEquals(setOf(60, 62, 64, 65, 67), melody.flatMap { it.pitches.map { p -> p.midi } }.toSet())
        assertTrue(melody.all { it.duration >= 1.0 && it.pitches.size == 1 })
    }

    @Test fun chopinVariantsPreserveTheSameMelodyAndIncreaseOnlyAccompanimentWork() {
        val easy = score("chopin-prelude-e-minor-easy")
        val chords = score("chopin-prelude-e-minor-chords")
        fun melody(s: Score) = s.sounding().filter { it.hand == Hand.RIGHT }.map { Triple(it.onset, it.duration, it.midi) }
        assertEquals(melody(easy), melody(chords))
        assertTrue(easy.events.filter { it.hand == Hand.LEFT }.all { it.pitches.size <= 1 })
        assertTrue(chords.events.any { it.hand == Hand.LEFT && it.pitches.size == 3 && it.duration == .5 })
        assertEquals(9, easy.measures) // Eight source measures plus a rest-padded pickup measure.
        assertTrue(easy.events.filter { it.hand == Hand.LEFT }.all { it.pitches.isEmpty() || it.duration >= 2 })
    }

    @Test fun adaptedCreativeCommonsEditionsCarryAttributionLicenseAndModificationNotice() {
        val canon = score("canon-in-d-study").source
        assertTrue("Jim Paterson" in canon && "2009" in canon && "Anonymous" in canon)
        assertTrue("https://creativecommons.org/licenses/by/3.0/" in canon)
        val mozart = score("mozart-k545-opening").source
        assertTrue("Alejandro Sierra" in mozart && "2013" in mozart)
        assertTrue("https://creativecommons.org/licenses/by-sa/3.0/" in mozart)
        for (id in listOf("ode-to-joy-first", "brahms-lullaby", "canon-in-d-study", "chopin-prelude-e-minor-easy", "chopin-prelude-e-minor-chords", "mozart-k545-opening")) {
            val s = score(id)
            assertTrue("แอป" in s.source && "https://github.com/MutopiaProject/" in s.source)
            assertTrue("Public domain composition" in s.source)
        }
    }
    @Test fun heroicArrangementsUseTheSameSourcePhraseWithExplicitSimplification() {
        val easy = score("heroic-polonaise-easy")
        val study = score("heroic-polonaise-theme")
        assertEquals(8, easy.measures)
        assertEquals(easy.measures, study.measures)
        assertEquals(3.0, easy.measureBeats)
        assertEquals(0, easy.keyFifths)
        assertEquals(-4, study.keyFifths)
        val right = easy.events.filter { it.hand == Hand.RIGHT }
        assertTrue(right.all { it.pitches.size == 1 && it.duration >= .5 })
        assertTrue(right.flatMap { it.pitches }.all { it.midi in 60..76 })
        assertTrue(right.flatMap { it.pitches }.any { it.midi == 70 }) // Bb remains after transposition.
        assertTrue(easy.events.filter { it.hand == Hand.LEFT }.all { it.pitches.size == 1 && it.duration == 3.0 })
        assertEquals(listOf(73, 77), study.events.first { it.hand == Hand.RIGHT }.pitches.map { it.midi })
        assertTrue(study.events.any { it.hand == Hand.RIGHT && it.pitches.size >= 2 && it.duration == .25 })
    }

    @Test fun heroicDataLicenseRetainsTheInstitutesRequiredAttribution() {
        for (id in listOf("heroic-polonaise-easy", "heroic-polonaise-theme")) {
            val source = score(id).source
            for (notice in listOf("First Editions of Fryderyk Chopin's Music",
                "Copyright 2017-2021 The Fryderyk Chopin Institute", "https://nifc.pl",
                "https://chopinscores.org", "https://github.com/pl-wnifc/humdrum-chopin-first-editions",
                "https://creativecommons.org/licenses/by/4.0/", "Wojciech Jakubiak-Danek", "Mateusz Mrugas", "17–24")) {
                assertTrue(notice in source, "$id: missing $notice")
            }
        }
    }

    @Test fun moonlightIntroPreservesThreeNotesPerPulseAndAddsOctaveBassInTheOriginalKey() {
        val easy = score("moonlight-intro-easy")
        val originalKey = score("moonlight-intro-c-sharp")
        for (s in listOf(easy, originalKey)) {
            assertEquals(4, s.measures)
            assertEquals(6.0, s.measureBeats)
            assertEquals(12, s.beatsPerMeasure)
            assertEquals(8, s.beatUnit)
            val right = s.events.filter { it.hand == Hand.RIGHT }
            assertEquals(48, right.size)
            right.forEachIndexed { i, e ->
                assertEquals(i * .5, e.onset)
                assertEquals(.5, e.duration)
                assertEquals(1, e.pitches.size)
            }
            val bass = s.sounding().filter { it.hand == Hand.LEFT && it.onset == 0.0 }
            assertTrue(bass.all { it.duration == 6.0 }, "Initial bass must sustain the whole compound bar")
        }
        val easyPitches = easy.events.filter { it.hand == Hand.RIGHT }.flatMap { it.pitches }.map { it.midi }
        val keyPitches = originalKey.events.filter { it.hand == Hand.RIGHT }.flatMap { it.pitches }.map { it.midi }
        assertEquals(keyPitches.map { it + 8 }, easyPitches)
        assertTrue(originalKey.events.filter { it.hand == Hand.LEFT }.all { it.pitches.size == 2 })
        assertTrue(easy.events.filter { it.hand == Hand.LEFT }.all { it.pitches.size == 1 })
    }

    @Test fun moonlightThemeSeparatesMelodyFromContinuousLeftHandAndRetainsShareAlikeCredit() {
        val theme = score("moonlight-first-theme")
        assertEquals(8, theme.measures)
        assertEquals(96, theme.events.count { it.hand == Hand.LEFT })
        assertTrue(theme.events.filter { it.hand == Hand.LEFT }.all { it.duration == .5 && it.pitches.size == 1 })
        assertEquals(4.5, theme.sounding().first { it.hand == Hand.RIGHT }.onset)
        for (id in listOf("moonlight-intro-easy", "moonlight-intro-c-sharp", "moonlight-first-theme")) {
            val source = score(id).source
            for (credit in listOf("Stewart Holmes", "2007", "Mutopia", "https://creativecommons.org/licenses/by-sa/2.5/", "12/8", "ไม่ใช่ท่อนเต็ม")) {
                assertTrue(credit in source, "$id: missing $credit")
            }
        }
    }

}
