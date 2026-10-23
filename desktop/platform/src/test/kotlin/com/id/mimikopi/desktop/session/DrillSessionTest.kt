package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.progress.AttemptHistory
import com.id.mimikopi.core.progress.HistoryAttempt
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.data.AppDirs
import com.id.mimikopi.desktop.data.Database
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DrillSessionTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun services(): Services {
        val dirs = AppDirs(tmp.newFolder())
        return Services(dirs, Database.open(dirs.database), Piano(nullDevice = true), "test", startMidi = false)
    }

    @Test
    fun lesson_1_1_keyfind_round_is_judged_saved_and_shown_in_history() = runTest {
        val s = services()
        val io = StandardTestDispatcher(testScheduler)
        val drill = DrillSession(s, DrillSource.LessonDrill("1.1", 0), backgroundScope, io = io)
        runCurrent()
        assertIs<DrillUi.Ready>(drill.ui.value)
        drill.onStart()
        drill.start()
        runCurrent()
        var answered = 0
        while (true) {
            val q = drill.ui.value as? DrillUi.Question ?: break
            // Answer right away with the computer keyboard: any key of the asked letter counts.
            val key = q.prompt.answerKeys(drill.keyRange).first()
            s.hub.dispatch(InputEvent.NoteOn(key, 90, System.nanoTime(), InputSource.COMPUTER_KEYS))
            s.hub.dispatch(InputEvent.NoteOff(key, System.nanoTime(), InputSource.COMPUTER_KEYS))
            runCurrent()
            assertEquals(Feedback.CORRECT, (drill.ui.value as DrillUi.Question).feedback)
            advanceTimeBy(700)
            runCurrent()
            answered++
        }
        assertEquals(10, answered)
        val result = assertIs<DrillUi.Result>(drill.ui.value)
        assertTrue(result.summary.contains("100%"), result.summary)
        assertEquals(true, result.passCriterionMet)
        assertEquals(false, result.lessonPassed, "one day is not enough: the rule asks for two days")

        val rows = s.db.lessonAttempts("1.1")
        assertEquals(1, rows.size)
        val h = HistoryAttempt(rows.single())
        assertTrue(h.comparable)
        assertEquals("COMPUTER_KEYS", h.value("inputSources"))
        assertEquals(10, s.db.events(rows.single().id).size)
        assertEquals(1, s.learning.records.value["1.1"]?.passDays?.size)
        drill.onStop()
        s.close()
    }

    @Test
    fun two_matching_rounds_give_a_comparison_and_survive_a_restart() = runTest {
        val dirs = AppDirs(tmp.newFolder())
        val io = StandardTestDispatcher(testScheduler)
        Services(dirs, Database.open(dirs.database), Piano(nullDevice = true), "test", startMidi = false).use { s ->
            repeat(2) { round ->
                val drill = DrillSession(s, DrillSource.LessonDrill("1.1", 0), backgroundScope, io = io)
                runCurrent()
                drill.onStart()
                drill.start()
                runCurrent()
                var n = 0
                while (true) {
                    val q = drill.ui.value as? DrillUi.Question ?: break
                    // First round: one wrong answer first.
                    val wrong = round == 0 && n == 0
                    val key = if (wrong) drill.keyRange.first { !q.prompt.accepts(it) } else q.prompt.answerKeys(drill.keyRange).first()
                    s.hub.dispatch(InputEvent.NoteOn(key, 90, System.nanoTime(), InputSource.COMPUTER_KEYS))
                    runCurrent()
                    advanceTimeBy(1500)
                    runCurrent()
                    n++
                }
                drill.onStop()
            }
        }
        Services(dirs, Database.open(dirs.database), Piano(nullDevice = true), "test", startMidi = false).use { s ->
            val rows = s.db.lessonAttempts("1.1").map(::HistoryAttempt)
            assertEquals(2, rows.size)
            val c = assertNotNull(AttemptHistory.comparison(rows))
            assertTrue(c.first.row.noteAccuracy!! < 1.0)
            assertEquals(1.0, c.latest.row.noteAccuracy)
        }
    }
}
