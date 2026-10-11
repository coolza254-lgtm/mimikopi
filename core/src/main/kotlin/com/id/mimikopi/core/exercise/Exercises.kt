package com.id.mimikopi.core.exercise

import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.PassRule
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** One key-finding question. [target] null means any key with that letter counts. */
data class KeyFindItem(val letter: Letter, val target: SpelledPitch?) {
    fun accepts(midi: Int): Boolean =
        if (target != null) midi == target.midi else midi % 12 == letter.semitonesFromC

    /** SRS item id (learning-system.md 10 section 5): key position by letter. */
    val srsId: String get() = "key:${letter.name}"
}

/** One note-reading question: a note on a staff; the answer is that exact key. */
data class NoteIdItem(val pitch: SpelledPitch, val clef: Clef) {
    fun accepts(midi: Int): Boolean = midi == pitch.midi
    val srsId: String get() = "note:$pitch:${clef.name.lowercase()}"

    companion object {
        fun parse(id: String): NoteIdItem {
            val (p, c) = id.removePrefix("note:").split(":")
            return NoteIdItem(SpelledPitch.parse(p), Clef.valueOf(c.uppercase()))
        }
    }
}

/**
 * Exercise generators (learning-system.md 5.1). Deterministic for a given seed so an attempt can be
 * regenerated from the stored parameters; never repeats the same item twice in a row.
 */
object Generators {
    /** Exact-octave targets stay around Middle C, where reading starts (C3..B5). */
    private val EXACT_RANGE = 48..83

    fun keyFind(drill: Drill, seed: Long): List<KeyFindItem> {
        val rnd = Random(seed)
        val letters = drill.letters.map { Letter.valueOf(it) }
        val out = mutableListOf<KeyFindItem>()
        while (out.size < drill.items) {
            val letter = letters[rnd.nextInt(letters.size)]
            val item = if (drill.exactOctave) {
                val candidates = EXACT_RANGE.filter { it % 12 == letter.semitonesFromC }
                KeyFindItem(letter, SpelledPitch.fromMidiSharp(candidates[rnd.nextInt(candidates.size)]))
            } else {
                KeyFindItem(letter, null)
            }
            if (out.lastOrNull() != item || letters.size == 1 && !drill.exactOctave) out += item
        }
        return out
    }

    /**
     * Note-reading items: [due] SRS items first (they need review), then random pool items,
     * avoiding immediate repeats.
     */
    fun noteId(drill: Drill, seed: Long, due: List<String> = emptyList()): List<NoteIdItem> {
        val rnd = Random(seed)
        val pool = drill.pool.map { NoteIdItem.parse("note:$it") }
        val out = mutableListOf<NoteIdItem>()
        due.map { NoteIdItem.parse(it) }.filter { it in pool }.take(drill.items).forEach { if (out.lastOrNull() != it) out += it }
        while (out.size < drill.items) {
            val item = pool[rnd.nextInt(pool.size)]
            if (pool.size == 1 || out.lastOrNull() != item) out += item
        }
        return out
    }

    fun pulseBpm(drill: Drill, seed: Long): Int = Random(seed).nextInt(drill.bpmMin, drill.bpmMax + 1)
}

/** One answered question. */
data class Answer(val correct: Boolean, val responseMs: Long, val firstTry: Boolean)

/** Results and pass checks (learning-system.md 6 and each lesson's own criterion). */
object Scoring {
    fun accuracy(answers: List<Answer>): Double =
        if (answers.isEmpty()) 0.0 else answers.count { it.correct && it.firstTry }.toDouble() / answers.size

    /** Lesson 1.1: every item right on the first try within the time limit. */
    fun keyFindPassed(answers: List<Answer>, rule: PassRule, required: Int): Boolean {
        val limit = ((rule.maxSecondsPerItem ?: 3.0) * 1000).toLong()
        return answers.size >= required && answers.all { it.correct && it.firstTry && it.responseMs <= limit }
    }

    fun noteIdPassed(answers: List<Answer>, rule: PassRule, required: Int): Boolean =
        answers.size >= required && accuracy(answers) >= (rule.minAccuracy ?: 0.9)

    data class PulseStats(val taps: Int, val meanMs: Double, val sdMs: Double, val within: Double)

    /**
     * Lesson 1.3: offsets of each tap from its beat after latency compensation (ms, + = late).
     * [toleranceMs] counts how many fell within the stage tolerance (for feedback).
     */
    fun pulse(offsetsMs: List<Double>, toleranceMs: Int): PulseStats {
        if (offsetsMs.isEmpty()) return PulseStats(0, 0.0, 0.0, 0.0)
        val mean = offsetsMs.average()
        val sd = sqrt(offsetsMs.sumOf { (it - mean) * (it - mean) } / offsetsMs.size)
        val within = offsetsMs.count { abs(it) <= toleranceMs }.toDouble() / offsetsMs.size
        return PulseStats(offsetsMs.size, mean, sd, within)
    }

    fun pulsePassed(stats: PulseStats, rule: PassRule, requiredBeats: Int): Boolean =
        stats.taps >= requiredBeats && stats.sdMs <= (rule.maxSdMs ?: 80.0)

    /** Matches taps (ns) to beats (ns): each tap to its nearest beat, latency subtracted. */
    fun tapOffsetsMs(beatNanos: List<Double>, tapNanos: List<Long>, latencyMs: Double, maxAbsMs: Double): List<Double> =
        if (beatNanos.isEmpty()) emptyList() else tapNanos.mapNotNull { tap ->
            val beat = beatNanos.minBy { abs(it - tap) }
            ((tap - beat) / 1e6 - latencyMs).takeIf { abs(it) <= maxAbsMs }
        }
}

/** E_INTERVAL_READ answers: MOTION = repeat/step/skip + direction (1.6); INTERVAL = 2nd..octave + direction (2.1). */
object IntervalQuiz {
    fun isCorrect(expected: com.id.mimikopi.core.music.Motion, answer: com.id.mimikopi.core.music.Motion, mode: String): Boolean =
        expected.direction == answer.direction && when (mode) {
            "INTERVAL" -> expected.staffSteps == answer.staffSteps
            else -> expected.kind == answer.kind
        }

    /** Interval numbers offered as answers for the allowed staff steps (a 3rd = 2 steps). */
    fun numbersFor(steps: List<Int>): List<Int> = steps.filter { it > 0 }.map { it + 1 }.distinct().sorted()
}
