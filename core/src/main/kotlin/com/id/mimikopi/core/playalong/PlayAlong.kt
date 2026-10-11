package com.id.mimikopi.core.playalong

import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.practice.OnsetJudgement
import com.id.mimikopi.core.practice.RunSummary
import com.id.mimikopi.core.practice.TimingRules
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.core.score.Score
import kotlin.math.abs

/**
 * WAIT: the cursor waits on each chord until the right keys are down (reading without time pressure).
 * TEMPO: the cursor moves at the set tempo and every note is judged early / on time / late / missed.
 */
enum class PlayMode { WAIT, TEMPO }

enum class NoteResult {
    PENDING,
    /** Right key, inside the timing tolerance (tempo mode) or first try (wait mode). */
    CORRECT,
    EARLY,
    LATE,
    MISSED,
    /** Wait mode: played after a wrong key on the same chord. */
    CORRECTED,
}

data class PlaySetup(
    val mode: PlayMode,
    /** Hands the player plays; the app plays the others (or keeps them silent). */
    val hands: Set<Hand>,
    val fromMeasure: Int,
    /** Inclusive. */
    val toMeasure: Int,
    val bpm: Double,
    val toleranceMs: Int,
    /** Calibrated input latency for the current route, subtracted before judging. */
    val latencyMs: Long = 0,
) {
    init {
        require(hands.isNotEmpty()) { "at least one hand must be played" }
        require(fromMeasure in 0..toMeasure) { "bad section $fromMeasure..$toMeasure" }
        require(bpm > 0 && toleranceMs > 0)
    }
}

data class ExpectedNote(val key: NoteKey, val pitch: SpelledPitch, val beat: Double, val hand: Hand, val measure: Int) {
    val midi: Int get() = pitch.midi
}

data class NoteOutcome(val result: NoteResult, val offsetMs: Long? = null)

data class WrongPress(val midi: Int, val measure: Int, val beat: Double)

/** What the screen shows right after a key press. */
sealed interface Feedback {
    data class Hit(val key: NoteKey, val result: NoteResult, val offsetMs: Long?) : Feedback
    data class Wrong(val press: WrongPress) : Feedback
    /** A key that does not count either way (repeat of an already played chord note, or outside the run). */
    data object Ignored : Feedback
}

/**
 * Judges one run through a section. Deterministic and clock-free: every call passes the event time in
 * milliseconds on the same timeline as [start]. Time [start]s at the downbeat of the first measure
 * (after the count-in).
 */
class PlayAlongJudge(val score: Score, val setup: PlaySetup) {
    val startBeat: Double = score.measureStart(setup.fromMeasure)
    val endBeat: Double = score.measureStart(setup.toMeasure + 1)
    val beatMs: Double = TimingRules.beatMs(setup.bpm)

    /** Notes the player must play, sorted by beat. */
    val expected: List<ExpectedNote> = score.events
        .filter { !it.isRest && it.hand in setup.hands && it.onset >= startBeat - 1e-9 && it.onset < endBeat - 1e-9 }
        .flatMap { e -> e.pitches.mapIndexed { i, p -> ExpectedNote(NoteKey(e.id, i), p, e.onset, e.hand, e.measure) } }
        .sortedWith(compareBy({ it.beat }, { it.midi }))

    /** Expected notes grouped by onset (a chord or two hands together form one group). */
    val groups: List<List<ExpectedNote>> = expected.groupBy { it.beat }.values.toList()

    /** Notes the app plays for the hands the player does not play. */
    val accompaniment: List<ExpectedNote> = score.events
        .filter { !it.isRest && it.hand !in setup.hands && it.onset >= startBeat - 1e-9 && it.onset < endBeat - 1e-9 }
        .flatMap { e -> e.pitches.mapIndexed { i, p -> ExpectedNote(NoteKey(e.id, i), p, e.onset, e.hand, e.measure) } }
        .sortedBy { it.beat }

    /** Tempo-mode matching window: a press further than this from every unplayed note is a wrong note. */
    val matchWindowMs: Double = maxOf(setup.toleranceMs * 2.0, beatMs * 0.5)

    private val outcomes = LinkedHashMap<NoteKey, NoteOutcome>().apply { expected.forEach { put(it.key, NoteOutcome(NoteResult.PENDING)) } }
    private val hitTimes = HashMap<NoteKey, Long>()
    private val wrongs = mutableListOf<WrongPress>()
    private var startMs: Long? = null

    // wait mode state
    private var groupIndex = 0
    private val downInGroup = mutableSetOf<NoteKey>()
    private var groupHadWrong = false
    private val groupDoneMs = mutableListOf<Long>()

    val isStarted: Boolean get() = startMs != null

    fun start(atMs: Long) {
        check(startMs == null) { "already started" }
        startMs = atMs
    }

    fun outcome(key: NoteKey): NoteOutcome = outcomes[key] ?: NoteOutcome(NoteResult.PENDING)
    fun outcomes(): Map<NoteKey, NoteOutcome> = outcomes.toMap()
    fun wrongPresses(): List<WrongPress> = wrongs.toList()

    fun expectedTimeMs(beat: Double): Double = (startMs ?: 0L) + (beat - startBeat) * beatMs

    /** Wait mode: the chord the player must play now, or null when the section is done. */
    fun currentGroup(): List<ExpectedNote>? = groups.getOrNull(groupIndex)

    /** Wait mode: keys of the current chord already held correctly. */
    fun heldInCurrentGroup(): Set<NoteKey> = downInGroup.toSet()

    /** Cursor position in beats. Tempo mode follows the clock; wait mode sits on the current chord. */
    fun cursorBeat(nowMs: Long): Double = when (setup.mode) {
        PlayMode.TEMPO -> startMs?.let { (startBeat + (nowMs - it) / beatMs).coerceIn(startBeat, endBeat) } ?: startBeat
        PlayMode.WAIT -> currentGroup()?.first()?.beat ?: endBeat
    }

    /** True once every note has a result (wait mode) or the clock passed the end plus the window (tempo mode). */
    fun isFinished(nowMs: Long): Boolean = when (setup.mode) {
        PlayMode.WAIT -> groupIndex >= groups.size
        PlayMode.TEMPO -> startMs != null && nowMs - setup.latencyMs > expectedTimeMs(endBeat) + matchWindowMs
    }

    fun onNoteOn(midi: Int, atMs: Long): Feedback {
        if (startMs == null) return Feedback.Ignored
        return when (setup.mode) {
            PlayMode.TEMPO -> tempoPress(midi, atMs)
            PlayMode.WAIT -> waitPress(midi, atMs)
        }
    }

    /** Tempo mode: marks notes whose window has passed as missed. Returns the newly missed keys. */
    fun advance(nowMs: Long): List<NoteKey> {
        if (setup.mode != PlayMode.TEMPO || startMs == null) return emptyList()
        val t = nowMs - setup.latencyMs
        val missed = mutableListOf<NoteKey>()
        for (n in expected) {
            if (expectedTimeMs(n.beat) + matchWindowMs >= t) break
            if (outcomes[n.key]?.result == NoteResult.PENDING) {
                outcomes[n.key] = NoteOutcome(NoteResult.MISSED)
                missed += n.key
            }
        }
        return missed
    }

    private fun tempoPress(midi: Int, atMs: Long): Feedback {
        val t = atMs - setup.latencyMs
        val candidate = expected
            .filter { it.midi == midi && outcomes[it.key]?.result == NoteResult.PENDING }
            .minByOrNull { abs(t - expectedTimeMs(it.beat)) }
            ?.takeIf { abs(t - expectedTimeMs(it.beat)) <= matchWindowMs }
        if (candidate == null) {
            val beat = (startBeat + (t - startMs!!) / beatMs).coerceIn(startBeat, endBeat - 1e-6)
            if (t < expectedTimeMs(startBeat) - matchWindowMs) return Feedback.Ignored
            val press = WrongPress(midi, score.measureOf(beat), beat)
            wrongs += press
            return Feedback.Wrong(press)
        }
        val (offset, judgement) = TimingRules().judge(expectedTimeMs(candidate.beat).toLong(), atMs, setup.latencyMs, setup.toleranceMs)
        val result = when (judgement) {
            OnsetJudgement.ON_TIME -> NoteResult.CORRECT
            OnsetJudgement.EARLY -> NoteResult.EARLY
            OnsetJudgement.LATE -> NoteResult.LATE
        }
        outcomes[candidate.key] = NoteOutcome(result, offset)
        hitTimes[candidate.key] = t
        return Feedback.Hit(candidate.key, result, offset)
    }

    private fun waitPress(midi: Int, atMs: Long): Feedback {
        val group = currentGroup() ?: return Feedback.Ignored
        val match = group.firstOrNull { it.midi == midi && it.key !in downInGroup }
        if (match == null) {
            if (group.any { it.midi == midi }) return Feedback.Ignored
            val press = WrongPress(midi, group.first().measure, group.first().beat)
            wrongs += press
            groupHadWrong = true
            return Feedback.Wrong(press)
        }
        downInGroup += match.key
        hitTimes[match.key] = atMs
        val result = if (groupHadWrong) NoteResult.CORRECTED else NoteResult.CORRECT
        if (downInGroup.size == group.size) {
            for (n in group) outcomes[n.key] = NoteOutcome(result)
            groupDoneMs += atMs
            groupIndex++
            downInGroup.clear()
            groupHadWrong = false
        }
        return Feedback.Hit(match.key, result, null)
    }

    /** Releasing a key in wait mode before the whole chord is down un-holds it (chords must be held together). */
    fun onNoteOff(midi: Int) {
        if (setup.mode != PlayMode.WAIT) return
        val group = currentGroup() ?: return
        group.firstOrNull { it.midi == midi && it.key in downInGroup }?.let { downInGroup -= it.key }
    }

    /** Ends the run (finished or stopped early). Pending notes count as missed. */
    fun result(): PlayResult {
        val final = outcomes.mapValues { (_, o) -> if (o.result == NoteResult.PENDING) NoteOutcome(NoteResult.MISSED) else o }
        return PlayResultBuilder(this, final, wrongs.toList(), stumbles(final)).build()
    }

    /**
     * Stops longer than one beat (learning-system.md 6.1). Wait mode: the time between two chords exceeded
     * the written gap by more than a beat. Tempo mode: the player went silent for more than a beat of
     * written notes (a run of fully missed chords spanning more than one beat).
     */
    private fun stumbles(final: Map<NoteKey, NoteOutcome>): Int {
        var count = 0
        when (setup.mode) {
            PlayMode.WAIT -> {
                for (i in 1 until groupDoneMs.size) {
                    val writtenGap = (groups[i][0].beat - groups[i - 1][0].beat) * beatMs
                    if (groupDoneMs[i] - groupDoneMs[i - 1] > writtenGap + beatMs) count++
                }
            }
            PlayMode.TEMPO -> {
                var runStart: Double? = null
                for (g in groups) {
                    val allMissed = g.all { final[it.key]?.result == NoteResult.MISSED }
                    if (allMissed) {
                        if (runStart == null) runStart = g[0].beat
                    } else {
                        if (runStart != null && g[0].beat - runStart > 1.0 + 1e-9) count++
                        runStart = null
                    }
                }
                if (runStart != null && endBeat - runStart > 1.0 + 1e-9) count++
            }
        }
        return count
    }
}

fun Score.measureOf(beat: Double): Int = (beat / measureBeats).toInt().coerceIn(0, measures - 1)

data class MeasureStat(val measure: Int, val expected: Int, val errors: Int)

data class PlayResult(
    val setup: PlaySetup,
    val outcomes: Map<NoteKey, NoteOutcome>,
    val wrongPresses: List<WrongPress>,
    val stumbles: Int,
    val summary: RunSummary?,
    /** Wait mode does not judge timing, so its runs never count toward the tempo ramp or a clean pass. */
    val timingJudged: Boolean,
    val perMeasure: List<MeasureStat>,
    /** Measures with errors, worst first (at most three; learning-system.md 6.8). */
    val problemMeasures: List<Int>,
    /** Mean compensated offset of the notes that were hit (tempo mode), negative = early. */
    val meanOffsetMs: Double?,
    val errorsByHand: Map<Hand, Int>,
    /** Pitches that went wrong at least twice in this run, most frequent first. */
    val troubleNotes: List<SpelledPitch>,
) {
    fun count(result: NoteResult): Int = outcomes.values.count { it.result == result }
}

private class PlayResultBuilder(
    private val judge: PlayAlongJudge,
    private val outcomes: Map<NoteKey, NoteOutcome>,
    private val wrongs: List<WrongPress>,
    private val stumbles: Int,
) {
    fun build(): PlayResult {
        val setup = judge.setup
        val expected = judge.expected
        val tempo = setup.mode == PlayMode.TEMPO
        val hits = outcomes.values.count { it.result in HIT }
        val correct = if (tempo) (hits - wrongs.size).coerceAtLeast(0) else outcomes.values.count { it.result == NoteResult.CORRECT }
        val within = if (tempo) outcomes.values.count { it.result == NoteResult.CORRECT }.coerceAtMost(correct) else correct
        val summary = if (expected.isEmpty()) null else RunSummary(expected.size, correct, within, stumbles)

        val bad = expected.filter { outcomes[it.key]?.result != NoteResult.CORRECT }
        val perMeasure = (setup.fromMeasure..setup.toMeasure).map { m ->
            MeasureStat(m, expected.count { it.measure == m }, bad.count { it.measure == m } + wrongs.count { it.measure == m })
        }
        val problems = perMeasure.filter { it.errors > 0 }
            .sortedWith(compareByDescending<MeasureStat> { it.errors.toDouble() / maxOf(1, it.expected) }.thenBy { it.measure })
            .take(3).map { it.measure }
        val offsets = outcomes.values.filter { it.result in HIT }.mapNotNull { it.offsetMs }
        val byHand = Hand.entries.associateWith { h -> bad.count { it.hand == h } }
        val trouble = bad.filter { outcomes[it.key]?.result != NoteResult.EARLY && outcomes[it.key]?.result != NoteResult.LATE }
            .groupBy { it.pitch }.filter { it.value.size >= 2 }
            .entries.sortedByDescending { it.value.size }.map { it.key }
        return PlayResult(setup, outcomes, wrongs, stumbles, summary, tempo, perMeasure, problems,
            if (tempo && offsets.isNotEmpty()) offsets.average() else null, byHand, trouble)
    }

    companion object {
        val HIT = setOf(NoteResult.CORRECT, NoteResult.EARLY, NoteResult.LATE)
    }
}
