package com.id.mimikopi.ui.ear

import com.id.mimikopi.core.ear.*
import com.id.mimikopi.piano.ScheduledNotes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.random.Random
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EarAudioTest {
    private fun question() = EarCourse.question("deg:4", listOf("deg:0", "deg:2", "deg:4"), KeyPool.C_ONLY, false, 0.0, Random(2))
    private class Notes : ScheduledNotes {
        data class Hit(val at: Long, val note: Int, val on: Boolean)
        val hits = mutableListOf<Hit>()
        var clears = 0
        override fun noteOn(atNanos: Long, note: Int, velocity: Int) { hits += Hit(atNanos, note, true) }
        override fun noteOff(atNanos: Long, note: Int) { hits += Hit(atNanos, note, false) }
        override fun clear() { clears++; hits.clear() }
    }

    @Test fun cadenceAndTargetHaveAnExplicitBreathWithoutChangingQuestionNotesOrRhythm() {
        val q = question()
        val plan = EarAudioPlan.question(q, true)
        val cadence = plan.parts.single { it.stage == EarAudioStage.CADENCE }
        val gap = plan.parts.single { it.stage == EarAudioStage.BREATH }
        assertEquals(4, cadence.steps.size)
        assertEquals(900, gap.steps.single().ms)
        assertTrue(gap.steps.single().notes.isEmpty())
        assertEquals(q.sound, plan.parts.single { it.stage == EarAudioStage.QUESTION }.steps)
        assertEquals(350, plan.parts.last().steps.single().ms)
        for (item in EarCourse.ALL_ITEMS) {
            val any = EarCourse.question(item, EarCourse.ALL_ITEMS, KeyPool.ANY, true, 1.0, Random(3))
            assertEquals(any.sound, EarAudioPlan.question(any, any.cadence, any.answer is EarAnswer.Taps).parts.single { it.stage == EarAudioStage.QUESTION }.steps)
        }
    }

    @Test fun replayCannotLayerOrCompleteAnAnswerBeforeTheQuestionAndTailEnd() = runTest {
        val notes = Notes(); val stages = mutableListOf<EarAudioStage>(); var answered = 0
        val p = EarAudioPlayer(this, notes, { testScheduler.currentTime * 1_000_000 }, stages::add)
        val plan = EarAudioPlan.question(question(), true)
        assertTrue(p.play(plan, finish = { answered++ }))
        assertFalse(p.play(plan))
        runCurrent()
        assertEquals(0, answered)
        val target = notes.hits.first { it.note == 64 && it.on && it.at > 3_000_000_000 }.at
        assertEquals((250L + 4 * 650 + 900) * 1_000_000, target)
        advanceTimeBy(plan.durationMs - 1); runCurrent()
        assertEquals(0, answered)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, answered)
        assertEquals(EarAudioStage.IDLE, stages.last())
        assertFalse(p.busy)
    }

    @Test fun cancellationDuringHeldKeyFeedbackCannotLeakAudioOrAdvanceTheQuestionLater() = runTest {
        val notes = Notes(); val released = CompletableDeferred<Unit>(); var done = false
        val p = EarAudioPlayer(this, notes, { currentTime * 1_000_000 }, {})
        p.play(EarAudioPlan.feedback(question()), EarAudioStage.WAIT_RELEASE, prepare = { released.await() }, finish = { done = true })
        runCurrent(); assertTrue(notes.hits.isEmpty())
        p.stop(); released.complete(Unit); advanceUntilIdle()
        assertFalse(done); assertTrue(notes.hits.isEmpty())
        assertFalse(p.busy)
        assertTrue(p.play(EarAudioPlan.question(question(), false)))
        advanceUntilIdle()
    }

    @Test fun aNewKeyOrPedalPressRestartsTheQuietIntervalBeforeFeedback() = runTest {
        val released = MutableStateFlow(false); var ready = false
        val waiter = launch { awaitEarQuiet(released, 450); ready = true }
        runCurrent(); advanceTimeBy(1000); assertFalse(ready)
        released.value = true; runCurrent(); advanceTimeBy(300)
        released.value = false; runCurrent(); advanceTimeBy(200); assertFalse(ready)
        released.value = true; runCurrent(); advanceTimeBy(449); assertFalse(ready)
        advanceTimeBy(1); runCurrent(); assertTrue(ready)
        waiter.join()
    }

    @Test fun everyTheoryChoiceHasAnUnambiguousMidiKeyAndPitchAnswersWorkInAnyOctave() {
        for (item in EarCourse.ALL_ITEMS) {
            val q = EarCourse.question(item, EarCourse.ALL_ITEMS, KeyPool.ANY, true, 1.0, Random(8))
            if (q.answer !is EarAnswer.Choice) continue
            if (q.kind == EarKind.DEGREE || q.kind == EarKind.BASS) {
                for (o in q.options) {
                    val m = q.base + o.id.substringAfter(':').toInt()
                    assertEquals(o.id, q.midiChoice(m - 12))
                    assertEquals(o.id, q.midiChoice(m + 12))
                }
            } else {
                val choices = q.midiChoices()
                assertEquals(q.options.size, choices.map { it.midi % 12 }.toSet().size)
                choices.forEach { assertEquals(it.option.id, q.midiChoice(it.midi - 12)) }
            }
        }
    }
}
