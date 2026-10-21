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
        assertEquals(setOf("heroic-polonaise-easy", "heroic-polonaise-theme"), File("../content/pieces").listFiles()!!.map { it.nameWithoutExtension }.toSet())
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


}
