package com.id.mimikopi.core

import com.id.mimikopi.core.playalong.*
import com.id.mimikopi.core.score.*
import com.id.mimikopi.core.sync.ProgressMerge
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.*
import org.junit.Test

class HeroicPlanTest {
    private val day = LocalDate.of(2026, 10, 21)
    private fun score(id: String) = Score.from(PieceFile.parse(File("../content/pieces/$id.json").readText()))
    private fun played(t: HeroicPlan.Target, bpm: Int = t.bpm, wrong: Boolean = false, hit: Boolean = true): PlayResult {
        val s = score(t.piece)
        val j = PlayAlongJudge(s, PlaySetup(t.mode, t.hands, t.from, t.to, bpm.toDouble(), 120))
        j.start(0)
        if (wrong) j.onNoteOn(0, 0)
        if (hit) for (group in j.groups) {
            val ms = ((group.first().beat - j.startBeat) * 60000 / bpm).toLong()
            for (n in group) j.onNoteOn(n.midi, ms)
            for (n in group) j.onNoteOff(n.midi)
        }
        j.advance(((j.endBeat - j.startBeat) * 60000 / bpm).toLong() + 1000)
        return j.result()
    }
    @Test fun everyTargetResolvesToNonemptyMusicWithTheExactHandsAndSection() {
        assertEquals(30, HeroicPlan.targets.size)
        assertEquals(30, HeroicPlan.targets.map { it.id }.distinct().size)
        for (t in HeroicPlan.targets) {
            val s = score(t.piece)
            assertTrue(t.from in 0 until s.measures && t.to in t.from until s.measures)
            assertTrue(s.events.any { !it.isRest && it.hand in t.hands && it.measure in t.from..t.to })
        }
    }
    @Test fun readingAndTempoHaveSeparateEvidenceAndOldWholePieceResultsCannotPassTargets() {
        val t = HeroicPlan.target("c-0-right")!!
        var p = PieceProgress(t.piece, runs = 200, passDays = listOf("2026-10-19", "2026-10-20"), bestCleanBpm = 180)
        assertEquals(t, HeroicPlan.next(mapOf(p.pieceId to p)))
        p = HeroicPlan.record(p, played(t), false, false, day)
        assertTrue(t.completed(p.studyTargets[t.id]!!))
        assertNull(p.studyTargets[t.id]!!.lastTiming)
        assertEquals(setOf(t.id), p.studyTargets.keys)
        assertEquals("c-0-left", HeroicPlan.next(mapOf(p.pieceId to p))!!.id)
    }
    @Test fun tempoRequiresThreeConsecutiveCleanRunsOnTwoDistinctDays() {
        val t = HeroicPlan.target("c-0-together")!!
        var p = PieceProgress(t.piece)
        repeat(6) { p = HeroicPlan.record(p, played(t), false, false, day) }
        assertFalse(t.completed(p.studyTargets[t.id]!!))
        assertEquals(listOf(day.toString()), p.studyTargets[t.id]!!.days)
        repeat(2) { p = HeroicPlan.record(p, played(t), false, false, day.plusDays(1)) }
        assertFalse(t.completed(p.studyTargets[t.id]!!))
        p = HeroicPlan.record(p, played(t), false, false, day.plusDays(1))
        assertTrue(t.completed(p.studyTargets[t.id]!!))
        assertEquals(1.0, p.studyTargets[t.id]!!.lastTiming)
    }
    @Test fun nextTargetMovesOnAfterTodaysSuccessAndReturnsTomorrowForRetention() {
        val p = PieceProgress(HeroicPlan.EASY, studyTargets = HeroicPlan.targets.take(3).associate {
            it.id to StudyEvidence(days = listOf(day.toString()))
        })
        val progress = mapOf(p.pieceId to p)
        assertEquals("c-2-right", HeroicPlan.next(progress, day)!!.id)
        assertEquals("c-0-together", HeroicPlan.next(progress, day.plusDays(1))!!.id)
    }

    @Test fun cleanStreakMustBeBuiltWithinOneDayRatherThanCarriedOvernight() {
        val t = HeroicPlan.target("c-0-together")!!
        var p = PieceProgress(t.piece)
        repeat(2) { p = HeroicPlan.record(p, played(t), false, false, day) }
        p = HeroicPlan.record(p, played(t), false, false, day.plusDays(1))
        assertEquals(1, p.studyTargets[t.id]!!.streak)
        assertTrue(p.studyTargets[t.id]!!.days.isEmpty())
        repeat(2) { p = HeroicPlan.record(p, played(t), false, false, day.plusDays(1)) }
        assertEquals(listOf(day.plusDays(1).toString()), p.studyTargets[t.id]!!.days)
    }

    @Test fun helpersSlowTempoInterruptionsWrongKeysAndIdlePlaybackNeverPass() {
        val t = HeroicPlan.target("c-0-together")!!
        for ((r, helper, interrupted) in listOf(Triple(played(t), true, false), Triple(played(t, 40), false, false),
            Triple(played(t), false, true), Triple(played(t, wrong = true), false, false))) {
            var p = PieceProgress(t.piece)
            repeat(8) { p = HeroicPlan.record(p, r, helper, interrupted, day.plusDays(it.toLong())) }
            assertTrue(p.studyTargets[t.id]!!.days.isEmpty())
            assertEquals(0, p.studyTargets[t.id]!!.streak)
        }
        val p = PieceProgress(t.piece)
        assertEquals(p, HeroicPlan.record(p, played(t, hit = false), false, false, day))
        val read = HeroicPlan.target("c-0-right")!!
        assertTrue(HeroicPlan.record(PieceProgress(read.piece), played(read), true, false, day).studyTargets[read.id]!!.days.isEmpty())
        assertTrue(HeroicPlan.record(PieceProgress(read.piece), played(read, wrong = true), false, false, day).studyTargets[read.id]!!.days.isEmpty())
    }
    @Test fun slowerWholeSectionCannotClaimTargetTempoAndDifferentSectionsDoNotShareResults() {
        val t = HeroicPlan.target("c-whole-slow")!!
        var p = PieceProgress(t.piece)
        repeat(6) { p = HeroicPlan.record(p, played(t), false, false, day.plusDays((it / 3).toLong())) }
        assertTrue(t.completed(p.studyTargets[t.id]!!))
        assertFalse(HeroicPlan.target("c-whole")!!.completed(p.studyTargets["c-whole"]!!))
        assertNull(p.studyTargets["c-0-together"])
        assertEquals(48, p.studyTargets[t.id]!!.bestCleanBpm)
    }
    @Test fun failedRunBreaksStreakAndBackupSyncUnionsEvidenceWithoutDoublingAttempts() {
        val t = HeroicPlan.target("c-0-together")!!
        var p = PieceProgress(t.piece)
        repeat(2) { p = HeroicPlan.record(p, played(t), false, false, day) }
        p = HeroicPlan.record(p, played(t, wrong = true), false, false, day)
        assertEquals(0, p.studyTargets[t.id]!!.streak)
        assertEquals(p, Json.decodeFromString(PieceProgress.serializer(), Json.encodeToString(PieceProgress.serializer(), p)))
        val a = mapOf(t.piece to p.copy(studyTargets = mapOf(t.id to StudyEvidence(attempts = 4, days = listOf("2026-10-20"), bestCleanBpm = 48))))
        val b = mapOf(t.piece to p.copy(studyTargets = mapOf(t.id to StudyEvidence(attempts = 3, days = listOf("2026-10-21"), bestCleanBpm = 54))))
        val merged = ProgressMerge.pieces(a, b)
        assertEquals(merged, ProgressMerge.pieces(b, a))
        assertEquals(merged, ProgressMerge.pieces(merged, merged))
        assertEquals(2, merged[t.piece]!!.studyTargets[t.id]!!.days.size)
        assertEquals(4, merged[t.piece]!!.studyTargets[t.id]!!.attempts)
        assertEquals(54, merged[t.piece]!!.studyTargets[t.id]!!.bestCleanBpm)
        assertEquals(PieceProgress("old"), Json.decodeFromString(PieceProgress.serializer(), """{"pieceId":"old"}"""))
    }
}
