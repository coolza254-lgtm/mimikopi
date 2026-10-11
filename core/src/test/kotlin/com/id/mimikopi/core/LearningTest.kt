package com.id.mimikopi.core

import com.id.mimikopi.core.exercise.Answer
import com.id.mimikopi.core.exercise.Generators
import com.id.mimikopi.core.exercise.NoteIdItem
import com.id.mimikopi.core.exercise.Scoring
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.core.notation.Staff
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.core.session.SessionPlanner
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaffTest {
    private fun pos(p: String, c: Clef) = Staff.position(SpelledPitch.parse(p), c)

    @Test
    fun landmarkPositions() {
        assertEquals(-2, pos("G4", Clef.TREBLE)) // second line from the bottom
        assertEquals(-6, pos("C4", Clef.TREBLE)) // first ledger line below
        assertEquals(2, pos("F3", Clef.BASS)) // fourth line from the bottom
        assertEquals(6, pos("C4", Clef.BASS)) // first ledger line above
        assertEquals(-4, pos("E4", Clef.TREBLE)) // bottom line
    }

    @Test
    fun ledgerLinesAndStems() {
        assertEquals(listOf(-6), Staff.ledgerLines(-6))
        assertEquals(listOf(-6), Staff.ledgerLines(-7)) // B3 hangs below the C ledger line
        assertEquals(listOf(-6, -8), Staff.ledgerLines(-8))
        assertEquals(emptyList(), Staff.ledgerLines(-5)) // D4: space below the staff
        assertEquals(listOf(6), Staff.ledgerLines(6))
        assertTrue(Staff.stemUp(-2))
        assertFalse(Staff.stemUp(0))
    }
}

class LessonContentTest {
    private val dir = File("../content/lessons")
    private fun all() = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { Lesson.parse(it.readText()) }

    @Test
    fun allLessonFilesAreCompleteAndValid() {
        val lessons = all()
        val ids = lessons.map { it.id }.toSet()
        val expected = (listOf("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "1.10") +
            (1..9).map { "2.$it" } + (1..8).map { "3.$it" } + (1..8).map { "4.$it" } + (1..10).map { "5.$it" } +
            (1..7).map { "6.$it" }).toSet()
        assertEquals(expected, ids)
        // Prerequisites exist and come earlier in the order (no cycles).
        val order = lessons.sortedWith(compareBy({ it.stage }, { it.id.substringAfter('.').toInt() })).map { it.id }
        lessons.forEach { l -> l.prerequisites.forEach { p -> assertTrue(p in ids && order.indexOf(p) < order.indexOf(l.id), "${l.id} -> $p") } }
        lessons.forEach { assertEquals(emptyList(), it.problems(), it.id) }
        // SRS items must be ones the drills can produce.
        val l15 = lessons.first { it.id == "1.5" }
        val poolIds = l15.drills.flatMap { it.pool }.map { "note:$it" }.toSet()
        assertTrue(l15.srsItems.all { it in poolIds })
        // Every SRS item is a valid note item.
        lessons.flatMap { it.srsItems }.filter { it.startsWith("note:") }.forEach { com.id.mimikopi.core.exercise.NoteIdItem.parse(it) }
    }

    @Test
    fun everyDrillCanBeGeneratedAndPiecesExist() {
        val pieceIds = File("../content/pieces").listFiles()!!.map { it.nameWithoutExtension }.toSet()
        val exerciseIds = File("../content/exercises").listFiles()!!.map { it.nameWithoutExtension }.toSet()
        File("../content/exercises").listFiles()!!.forEach { f ->
            val p = com.id.mimikopi.core.score.PieceFile.parse(f.readText())
            assertEquals(f.nameWithoutExtension, p.id)
            assertTrue(p.id.startsWith("ex-"))
            com.id.mimikopi.core.score.ScoreLayout.of(com.id.mimikopi.core.score.Score.from(p))
        }
        for (l in all()) for (d in l.drills) for (seed in 0L until 20L) {
            when (d.type) {
                com.id.mimikopi.core.lesson.ExerciseType.E_FINGER_PATTERN ->
                    com.id.mimikopi.core.score.Score.from(com.id.mimikopi.core.exercise.ScoreGenerators.fingerPatterns(d, seed))
                com.id.mimikopi.core.lesson.ExerciseType.E_RHYTHM_TAP ->
                    com.id.mimikopi.core.score.Score.from(com.id.mimikopi.core.exercise.ScoreGenerators.rhythm(d, seed))
                com.id.mimikopi.core.lesson.ExerciseType.E_READ_PIECE ->
                    com.id.mimikopi.core.score.Score.from(com.id.mimikopi.core.exercise.ScoreGenerators.read(d, seed))
                com.id.mimikopi.core.lesson.ExerciseType.E_COMP_PATTERN ->
                    com.id.mimikopi.core.score.ScoreLayout.of(com.id.mimikopi.core.score.Score.from(com.id.mimikopi.core.exercise.ScoreGenerators.comp(d, seed)))
                com.id.mimikopi.core.lesson.ExerciseType.E_CHORD_BUILD ->
                    assertEquals(d.items, com.id.mimikopi.core.exercise.ChordDrills.build(d, seed).size, "${l.id}/${d.title}")
                com.id.mimikopi.core.lesson.ExerciseType.E_CHORD_CHANGE ->
                    assertTrue(com.id.mimikopi.core.exercise.ChordDrills.changes(d).isNotEmpty())
                com.id.mimikopi.core.lesson.ExerciseType.E_TRANSPOSE -> d.targetKeys.forEach { to ->
                    val (a, b) = com.id.mimikopi.core.theory.StageGenerators.transposePair(d.keys.first(), to, seed)
                    com.id.mimikopi.core.score.ScoreLayout.of(com.id.mimikopi.core.score.Score.from(b))
                    assertEquals(com.id.mimikopi.core.score.Score.from(a).events.size, com.id.mimikopi.core.score.Score.from(b).events.size)
                }
                com.id.mimikopi.core.lesson.ExerciseType.E_EAR ->
                    com.id.mimikopi.core.ear.EarLevel.valueOf(d.level)
                com.id.mimikopi.core.lesson.ExerciseType.E_INTERVAL_READ ->
                    assertEquals(d.items, com.id.mimikopi.core.exercise.ScoreGenerators.intervalSets(d, seed).size)
                com.id.mimikopi.core.lesson.ExerciseType.E_PIECE ->
                    d.pieces.forEach { assertTrue(it in pieceIds || it in exerciseIds || it in SPECIAL_PIECES, "${l.id}: missing piece $it") }
                else -> Unit
            }
        }
    }

    /** Placeholders the app fills in: the user's Capstone A score and the score linked to a library song. */
    private val SPECIAL_PIECES = setOf("capstone", "song")

    @Test
    fun noCopyrightedSongContent() {
        // learning-system.md 12.2: lesson content must not contain lyrics or notes of real songs.
        val text = listOf("lessons", "pieces", "exercises").flatMap { File("../content/$it").listFiles()!!.toList() }.joinToString("\n") { it.readText() }
        listOf("Morning Musume", "モーニング娘", "AKB48", "I WISH", "心のプラカード", "恋愛レボリューション").forEach {
            assertFalse(it in text, "found \"$it\" in lesson content")
        }
    }
}

class GeneratorTest {
    private val lessons = File("../content/lessons").listFiles()!!.associate { Lesson.parse(it.readText()).let { l -> l.id to l } }

    @Test
    fun keyFindIsDeterministicNoRepeatsAndInRange() {
        val round3 = lessons.getValue("1.1").drills[2]
        val a = Generators.keyFind(round3, 42)
        assertEquals(a, Generators.keyFind(round3, 42))
        assertEquals(10, a.size)
        a.zipWithNext().forEach { (x, y) -> assertTrue(x != y) }
        a.forEach { item -> assertTrue(item.target!!.midi in 48..83 && item.accepts(item.target!!.midi)) }
        repeat(1000) { seed ->
            Generators.keyFind(lessons.getValue("1.1").drills[1], seed.toLong()).forEach { assertTrue(it.target == null) }
        }
    }

    @Test
    fun anyOctaveAcceptsEveryKeyOfThatName() {
        val item = Generators.keyFind(lessons.getValue("1.1").drills[0], 1).first()
        val names = (21..108).filter { item.accepts(it) }
        assertEquals(names.size, (21..108).count { it % 12 == item.letter.semitonesFromC })
    }

    @Test
    fun noteIdPutsDueItemsFirstAndStaysInPool() {
        val test = lessons.getValue("1.5").drills[1]
        val items = Generators.noteId(test, 7, due = listOf("note:E3:bass", "note:B3:treble", "note:C6:treble"))
        assertEquals(20, items.size)
        assertEquals(NoteIdItem.parse("note:E3:bass"), items[0])
        assertEquals(NoteIdItem.parse("note:B3:treble"), items[1])
        val pool = test.pool.map { NoteIdItem.parse("note:$it") }.toSet()
        assertTrue(items.all { it in pool }) // C6 was not in the pool, ignored
        items.zipWithNext().forEach { (x, y) -> assertTrue(x != y) }
        assertEquals(ExerciseType.E_NOTEID, test.type)
    }
}

class ScoringAndProgressTest {
    private val lessons = File("../content/lessons").listFiles()!!.map { Lesson.parse(it.readText()) }.sortedBy { it.id }
    private fun byId(id: String) = lessons.first { it.id == id }

    @Test
    fun keyFindPassNeedsAllFirstTryWithinTime() {
        val rule = byId("1.1").pass
        val good = List(10) { Answer(true, 2500, true) }
        assertTrue(Scoring.keyFindPassed(good, rule, 10))
        assertFalse(Scoring.keyFindPassed(good.take(9) + Answer(true, 3500, true), rule, 10))
        assertFalse(Scoring.keyFindPassed(good.take(9) + Answer(true, 1000, false), rule, 10))
    }

    @Test
    fun pulseStatsAndPass() {
        val offsets = List(32) { if (it % 2 == 0) 50.0 else -50.0 }
        val s = Scoring.pulse(offsets, 150)
        assertEquals(50.0, s.sdMs, 1e-9)
        assertTrue(Scoring.pulsePassed(s, byId("1.3").pass, 32))
        assertFalse(Scoring.pulsePassed(Scoring.pulse(List(32) { if (it % 2 == 0) 100.0 else -100.0 }, 150), byId("1.3").pass, 32))
        // Latency is removed before judging; taps match their nearest beat.
        val beats = List(4) { it * 1e9 }
        assertEquals(listOf(0.0, 0.0, 0.0), Scoring.tapOffsetsMs(beats, listOf(120_000_000L, 1_120_000_000L, 2_120_000_000L), 120.0, 400.0).map { Math.round(it * 1000) / 1000.0 })
    }

    @Test
    fun passingNeedsTwoDaysAndHelpersDoNotCount() {
        val l11 = byId("1.1")
        var r = LessonRecord("1.1", attempts = 1)
        r = ProgressRules.recordPass(r, LocalDate.of(2026, 10, 12), helperUsed = false)
        assertFalse(ProgressRules.isPassed(r, l11))
        r = ProgressRules.recordPass(r, LocalDate.of(2026, 10, 12), helperUsed = false) // same day again
        assertFalse(ProgressRules.isPassed(r, l11))
        val withHelp = ProgressRules.recordPass(r, LocalDate.of(2026, 10, 13), helperUsed = true)
        assertFalse(ProgressRules.isPassed(withHelp, l11))
        r = ProgressRules.recordPass(r, LocalDate.of(2026, 10, 13), helperUsed = false)
        assertTrue(ProgressRules.isPassed(r, l11))
    }

    @Test
    fun unlockingFollowsPrerequisites() {
        val l15 = byId("1.5")
        assertEquals(LessonStatus.LOCKED, ProgressRules.status(l15, emptyMap()))
        val after = mapOf("1.1" to LessonRecord("1.1", passDays = setOf("2026-10-12"), attempts = 1))
        assertEquals(LessonStatus.AVAILABLE, ProgressRules.status(l15, after))
        assertEquals("1.1", ProgressRules.current(lessons, emptyMap())?.id)
    }
}

class SessionPlannerTest {
    private val sliceBlocks = setOf(BlockKind.REVIEW, BlockKind.RHYTHM, BlockKind.LESSON)

    @Test
    fun plansAddUpAndAlwaysIncludeFreePlay() {
        for (m in listOf(15, 30, 45, 60)) {
            val plan = SessionPlanner.plan(m, sliceBlocks)
            assertEquals(m, plan.sumOf { it.minutes }, "total for $m")
            assertEquals(BlockKind.FREE_PLAY, plan.last().kind)
            assertTrue(plan.all { it.minutes >= 1 })
        }
        assertEquals(15, SessionPlanner.plan(45, sliceBlocks).last().minutes)
        assertEquals(3, SessionPlanner.plan(30, sliceBlocks).last().minutes)
    }

    @Test
    fun fullTemplateAt30MinutesMatchesTheSpec() {
        val plan = SessionPlanner.plan(30, BlockKind.entries.toSet() - BlockKind.FREE_PLAY).associate { it.kind to it.minutes }
        // 27 minutes of blocks scaled from the 30-minute template (3,5,4,10,4,4) plus 3 free.
        assertEquals(30, plan.values.sum())
        assertTrue(plan.getValue(BlockKind.LESSON) >= 8)
    }
}
