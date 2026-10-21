package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.ear.EarAnswer
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.audio.NativeAudio
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.data.AppDirs
import com.id.mimikopi.desktop.data.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real-time run of the first questions of an ear round: answers and "next" only count when nothing plays. */
class EarSessionTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun waitFor(timeoutMs: Long = 15_000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    @Test
    fun answersAreRefusedWhileTheQuestionOrTheExplanationPlays() {
        assumeTrue(NativeAudio.available)
        val dirs = AppDirs(tmp.newFolder())
        val s = Services(dirs, Database.open(dirs.database), Piano(nullDevice = true), "test", startMidi = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val e = EarSession(s, EarArgs(), scope)
            e.onStart()
            waitFor(what = "the first question") { e.ui.value.phase == EarPhase.LISTEN && e.ui.value.audio != EarAudioStage.IDLE }
            var checked = 0
            repeat(2) {
                val q = e.ui.value.question!!
                // While the question sounds, an answer does nothing.
                answer(e, q.answer)
                assertTrue(e.ui.value.results.size == checked, "no result while the question plays")
                if (q.answer is EarAnswer.Taps) return@repeat
                waitFor(what = "the answer gate") { e.ui.value.phase == EarPhase.ANSWER && e.ui.value.audio == EarAudioStage.IDLE }
                answer(e, q.answer)
                checked++
                assertEquals(EarPhase.FEEDBACK, e.ui.value.phase)
                assertEquals(true, e.ui.value.results.last().right)
                // "Next" during the explanation is refused, so it can never be cut off or layered.
                val index = e.ui.value.index
                if (e.ui.value.audio != EarAudioStage.IDLE) {
                    e.next()
                    assertEquals(index, e.ui.value.index)
                }
                waitFor(what = "the explanation to end") { e.ui.value.audio == EarAudioStage.IDLE }
                e.next()
                waitFor(what = "the next question") { e.ui.value.index == index + 1 || e.ui.value.phase == EarPhase.DONE || e.ui.value.phase == EarPhase.LOADING }
                if (e.ui.value.phase != EarPhase.LISTEN) return@repeat
                waitFor(what = "the next question's sound") { e.ui.value.audio != EarAudioStage.IDLE || e.ui.value.phase != EarPhase.LISTEN }
            }
            assertTrue(checked >= 1)
            e.dispose()
        } finally {
            scope.cancel()
            s.close()
        }
    }

    private fun answer(e: EarSession, a: EarAnswer) {
        when (a) {
            is EarAnswer.Choice -> e.choose(a.id)
            is EarAnswer.Notes -> a.places.forEach { e.choose("note:$it") }
            is EarAnswer.Taps -> Unit
        }
    }
}
