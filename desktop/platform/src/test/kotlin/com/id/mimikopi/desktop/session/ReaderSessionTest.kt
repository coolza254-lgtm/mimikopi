package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.playalong.PlayAlongJudge
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.playalong.PlaySetup
import com.id.mimikopi.core.progress.HistoryAttempt
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.audio.NativeAudio
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.data.AppDirs
import com.id.mimikopi.desktop.data.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun services(): Services {
        val dirs = AppDirs(tmp.newFolder())
        return Services(dirs, Database.open(dirs.database), Piano(nullDevice = true), "test", startMidi = false)
    }

    /** The notes the judge will expect, in order (the same judge, built from the session's score and settings). */
    private fun expected(r: ReaderSession): List<List<Int>> {
        val u = r.ui.value
        val setup = PlaySetup(u.options.mode, u.options.hands.hands, u.section.first, u.section.last, u.bpm.toDouble(), 100, 0)
        return PlayAlongJudge(r.score!!, setup).expected.groupBy { it.beat }.toSortedMap().values.map { g -> g.map { it.midi } }
    }

    private fun press(s: Services, note: Int, source: InputSource = InputSource.COMPUTER_KEYS) {
        s.hub.dispatch(InputEvent.NoteOn(note, 90, System.nanoTime(), source))
        s.hub.dispatch(InputEvent.NoteOff(note, System.nanoTime(), source))
    }

    @Test
    fun wait_mode_lesson_run_is_judged_and_saved_for_the_lesson_history() = runTest {
        val s = services()
        val io = StandardTestDispatcher(testScheduler)
        val r = ReaderSession(s, ReaderArgs(lessonId = "1.5", drill = 0), backgroundScope, io)
        assertEquals(PlayMode.WAIT, r.ui.value.options.mode)
        assertEquals(HandChoice.RIGHT, r.ui.value.options.hands)
        r.onStart()
        val groups = expected(r)
        r.start()
        runCurrent()
        assertEquals(Phase.PLAYING, r.ui.value.phase)
        for (g in groups) {
            g.forEach { press(s, it) }
            runCurrent()
        }
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(Phase.RESULT, r.ui.value.phase)
        val res = assertNotNull(r.ui.value.result)
        assertTrue(res.headline.contains("100%"), res.headline)
        val row = s.db.lessonAttempts("1.5").single()
        val h = HistoryAttempt(row)
        assertEquals("1.5:E_FINGER_PATTERN:0", row.exerciseId)
        assertEquals("WAIT", h.mode)
        assertEquals(true, h.complete)
        assertTrue(h.comparable)
        assertEquals("COMPUTER_KEYS", h.value("inputSources"))
        assertEquals("1/2", Regex("""\d/2""").find(res.progressLine ?: "")?.value, res.progressLine)
        r.dispose()
        s.close()
    }

    @Test
    fun stopping_midway_keeps_the_effort_but_not_as_comparable_evidence() = runTest {
        val s = services()
        val io = StandardTestDispatcher(testScheduler)
        val r = ReaderSession(s, ReaderArgs(lessonId = "1.5", drill = 0), backgroundScope, io)
        r.onStart()
        val groups = expected(r)
        r.start()
        runCurrent()
        groups.take(2).flatten().forEach { press(s, it) }
        runCurrent()
        r.stop()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(Phase.RESULT, r.ui.value.phase)
        val h = HistoryAttempt(s.db.lessonAttempts("1.5").single())
        assertEquals(false, h.complete)
        assertFalse(h.comparable)
        r.dispose()
        s.close()
    }

    @Test
    fun heroic_target_fixes_section_hands_mode_and_tempo() = runTest {
        val s = services()
        val io = StandardTestDispatcher(testScheduler)
        val t = com.id.mimikopi.core.playalong.HeroicPlan.targets.first()
        val r = ReaderSession(s, ReaderArgs(pieceId = t.piece, target = t.id), backgroundScope, io)
        val u = r.ui.value
        assertEquals(t.from..t.to, u.section)
        assertEquals(t.bpm, u.bpm)
        assertEquals(t.mode, u.options.mode)
        assertEquals(t.hands, u.options.hands.hands)
        assertFalse(u.options.useRamp)
        r.dispose()
        s.close()
    }

    @Test
    fun tempo_mode_counts_in_on_the_audio_clock_and_judges_timed_notes() {
        assumeTrue(NativeAudio.available)
        val s = services()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val r = ReaderSession(s, ReaderArgs(lessonId = "1.6", drill = 0), scope)
            assertEquals(PlayMode.TEMPO, r.ui.value.options.mode)
            r.onStart()
            val u = r.ui.value
            val setup = PlaySetup(u.options.mode, u.options.hands.hands, u.section.first, u.section.last, u.bpm.toDouble(), 100, 0)
            val judge = PlayAlongJudge(r.score!!, setup)
            r.start()
            var clock: Pair<Long, Long>? = null
            val deadline = System.nanoTime() + 6_000_000_000L
            while (clock == null && System.nanoTime() < deadline) { Thread.sleep(5); clock = r.clockForTest }
            val (t0, quarter) = assertNotNull(clock, "the count-in fixed a downbeat")
            for (n in judge.expected.sortedBy { it.beat }) {
                val at = t0 + ((n.beat - judge.startBeat) * quarter).toLong()
                while (System.nanoTime() < at) Thread.sleep(0, 200_000)
                press(s, n.midi)
            }
            val end = System.nanoTime() + 8_000_000_000L
            while (r.ui.value.phase != Phase.RESULT && System.nanoTime() < end) Thread.sleep(20)
            Thread.sleep(300)
            val res = assertNotNull(r.ui.value.result, "phase ${r.ui.value.phase}")
            assertTrue(res.headline.contains("โน้ตถูก 100%"), res.headline)
            val h = HistoryAttempt(s.db.lessonAttempts("1.6").single())
            assertEquals("TEMPO", h.mode)
            assertNotNull(h.row.timingWithinTol)
            assertTrue(h.row.timingWithinTol!! >= 0.9, "notes sent on the beat are within tolerance: ${h.row.timingWithinTol}")
            r.dispose()
        } finally {
            scope.cancel()
            s.close()
        }
    }
}
