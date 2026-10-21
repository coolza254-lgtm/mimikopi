package com.id.mimikopi.ui.ear

import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.EarStep
import com.id.mimikopi.piano.ScheduledNotes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** The audible phases are separate from grading, so replays cannot accept answers midway through audio. */
enum class EarAudioStage(val label: String) {
    IDLE(""), PREPARE("เตรียมฟัง"), CADENCE("ฟังคอร์ดนำ"), BREATH("พักก่อนฟังโจทย์"),
    QUESTION("ฟังโจทย์ให้จบ"), COUNT_IN("เตรียมเคาะตามจังหวะ"),
    WAIT_RELEASE("ปล่อยคีย์และแป้นเหยียบ"), FEEDBACK_GAP("เว้นจังหวะก่อนเฉลย"), FEEDBACK("ฟังเฉลย"), TAIL("รอเสียงจบ"),
}

internal data class EarAudioPart(val stage: EarAudioStage, val steps: List<EarStep>) {
    val durationMs: Long get() = steps.sumOf { it.ms.toLong() }
}

/** Silence belongs to playback, not to the exercise content or the scoring tempo. */
internal data class EarAudioPlan(val parts: List<EarAudioPart>) {
    val durationMs: Long get() = parts.sumOf { it.durationMs }
    companion object {
        const val CADENCE_GAP_MS = 900
        const val ANSWER_GAP_MS = 350
        const val FEEDBACK_GAP_MS = 500L
        const val RELEASE_QUIET_MS = 450L
        fun rest(stage: EarAudioStage, ms: Int) = EarAudioPart(stage, listOf(EarStep(emptyList(), ms)))
        fun question(q: EarQuestion, cadence: Boolean, rhythm: Boolean = false): EarAudioPlan {
            val parts = mutableListOf(rest(EarAudioStage.PREPARE, 250))
            if (cadence) {
                // Replace the old 350 ms trailing rest with one deliberate, visible breathing interval.
                val chords = EarCourse.cadenceSteps(q.tonic).dropLastWhile { it.notes.isEmpty() && !it.click }
                parts += EarAudioPart(EarAudioStage.CADENCE, chords)
                parts += rest(EarAudioStage.BREATH, CADENCE_GAP_MS)
            }
            parts += EarAudioPart(EarAudioStage.QUESTION, q.sound)
            parts += rest(EarAudioStage.TAIL, if (rhythm) 600 else ANSWER_GAP_MS)
            return EarAudioPlan(parts)
        }
        fun feedback(q: EarQuestion, audible: Boolean = true) = EarAudioPlan(
            if (audible && q.after.isNotEmpty()) listOf(EarAudioPart(EarAudioStage.FEEDBACK, q.after), rest(EarAudioStage.TAIL, 450))
            else emptyList(),
        )
    }
}

/** One playback owner. Busy requests are rejected, not layered; stop invalidates all pending completion work. */
internal class EarAudioPlayer(
    private val scope: CoroutineScope,
    private val notes: ScheduledNotes,
    private val nowNanos: () -> Long = System::nanoTime,
    private val onStage: (EarAudioStage) -> Unit,
) {
    private var job: Job? = null
    private var generation = 0L
    val busy: Boolean get() = job?.isActive == true

    fun play(plan: EarAudioPlan, initial: EarAudioStage = EarAudioStage.PREPARE,
             prepare: suspend () -> Unit = {}, finish: suspend () -> Unit = {}): Boolean {
        if (busy) return false
        val ticket = ++generation
        onStage(initial)
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                prepare()
                val base = nowNanos()
                var offset = 0L
                for (part in plan.parts) {
                    var t = base + offset * 1_000_000
                    for (step in part.steps) {
                        val len = step.ms * 1_000_000L
                        if (step.click) {
                            notes.noteOn(t, 96, step.velocity)
                            notes.noteOff(t + 60_000_000, 96)
                        } else {
                            step.notes.forEach { notes.noteOn(t, it, step.velocity) }
                            step.notes.forEach { notes.noteOff(t + (len - 40_000_000).coerceAtLeast(1), it) }
                        }
                        t += len
                    }
                    offset += part.durationMs
                }
                offset = 0
                for (part in plan.parts) {
                    waitUntil(base + offset * 1_000_000)
                    onStage(part.stage)
                    offset += part.durationMs
                }
                waitUntil(base + plan.durationMs * 1_000_000)
                finish()
            } finally {
                if (ticket == generation) {
                    notes.clear()
                    onStage(EarAudioStage.IDLE)
                }
            }
        }
        job!!.start()
        return true
    }

    private suspend fun waitUntil(at: Long) {
        val remaining = at - nowNanos()
        if (remaining > 0) delay((remaining + 999_999) / 1_000_000)
    }

    fun stop() {
        generation++
        job?.cancel()
        job = null
        notes.clear()
        onStage(EarAudioStage.IDLE)
    }
}

/** Wait for a continuous released interval, restarting if another key or the pedal is pressed. */
internal suspend fun awaitEarQuiet(released: kotlinx.coroutines.flow.Flow<Boolean>, quietMs: Long,
                                  onHeld: (Boolean) -> Unit = {}) {
    while (true) {
        released.first { free -> if (!free) onHeld(true); free }
        onHeld(false)
        val interrupted = kotlinx.coroutines.withTimeoutOrNull(quietMs) { released.first { !it }; true }
        if (interrupted != true) return
    }
}
