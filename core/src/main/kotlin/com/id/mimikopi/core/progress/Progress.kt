package com.id.mimikopi.core.progress

import com.id.mimikopi.core.exercise.NoteIdItem
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.random.Random

/** Lessons passed per skill track (learning-system.md 2.1): shown separately so a weak track is visible. */
data class TrackSummary(val track: String, val passed: Int, val total: Int) {
    val fraction: Double get() = if (total == 0) 0.0 else passed.toDouble() / total
}

object TrackProgress {
    fun summarize(lessons: List<Lesson>, records: Map<String, LessonRecord>): List<TrackSummary> =
        Lesson.TRACKS.toList().map { t ->
            val inTrack = lessons.filter { t in it.tracks }
            TrackSummary(t, inTrack.count { ProgressRules.isPassed(records[it.id], it) }, inTrack.size)
        }.filter { it.total > 0 }

    /** The track with the lowest share of passed lessons (ties: fewest passed), or null with no lessons. */
    fun weakest(summaries: List<TrackSummary>): TrackSummary? =
        summaries.minWithOrNull(compareBy<TrackSummary> { it.fraction }.thenBy { it.passed })

    data class StageSummary(val stage: Int, val passed: Int, val total: Int)

    fun stages(lessons: List<Lesson>, records: Map<String, LessonRecord>): List<StageSummary> =
        lessons.groupBy { it.stage }.toSortedMap().map { (stage, ls) ->
            StageSummary(stage, ls.count { ProgressRules.isPassed(records[it.id], it) }, ls.size)
        }
}

/**
 * One benchmark sitting (learning-system.md 12.1). Parts are filled in as they are done; chord changes join when
 * that feature exists.
 */
@Serializable
data class BenchmarkResult(
    val date: String,
    val noteIdPerMinute: Double? = null,
    val noteIdAccuracy: Double? = null,
    /** Highest sight-reading level (1-5) played clean in one go; 0 = none. */
    val sightReadingLevel: Int? = null,
    val pulseSdMs: Double? = null,
    /** Share right on the fixed ear-training set (E1-E4, 20 questions). */
    val earAccuracy: Double? = null,
) {
    val complete: Boolean get() = noteIdPerMinute != null && sightReadingLevel != null && pulseSdMs != null
}

/** Fixed benchmark sets: the same questions every time, never used by the practice drills (fixed seeds). */
object Benchmarks {
    const val INTERVAL_DAYS = 14L
    const val NOTE_ITEMS = 30
    const val PULSE_BPM = 70
    const val PULSE_BEATS = 32
    const val MAX_LEVEL = 5
    private const val SEED = 20261011L

    fun isDue(results: List<BenchmarkResult>, today: LocalDate): Boolean {
        val last = results.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull() ?: return true
        return !last.plusDays(INTERVAL_DAYS).isAfter(today)
    }

    fun nextDue(results: List<BenchmarkResult>): LocalDate? =
        results.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull()?.plusDays(INTERVAL_DAYS)

    /** 30 natural notes from C3 to C6 on the clef that suits them (Middle C on both), fixed order. */
    fun noteIdItems(): List<NoteIdItem> {
        val rnd = Random(SEED)
        val pool = (3 * 7..6 * 7).map { SpelledPitch(Letter.entries[it % 7], 0, it / 7) }
        val out = mutableListOf<NoteIdItem>()
        while (out.size < NOTE_ITEMS) {
            val p = pool[rnd.nextInt(pool.size)]
            val clef = when {
                p.midi < 60 -> Clef.BASS
                p.midi > 60 -> Clef.TREBLE
                else -> if (rnd.nextBoolean()) Clef.TREBLE else Clef.BASS
            }
            val item = NoteIdItem(p, clef)
            if (out.lastOrNull() != item) out += item
        }
        return out
    }

    /** The fixed sight-reading piece of a level (played once, tempo mode, 60 bpm). */
    fun sightReadingDrill(level: Int): Drill =
        Drill(ExerciseType.E_READ_PIECE, "Benchmark L$level", items = 1, level = "L$level", bpm = 60, mode = "TEMPO",
            hand = if (level == 2) "LEFT" else "RIGHT")

    fun sightReadingSeed(level: Int): Long = SEED + level

    fun notesPerMinute(correct: Int, totalMs: Long): Double = if (totalMs <= 0) 0.0 else correct * 60_000.0 / totalMs

    /** Updates (or starts) today's sitting. */
    fun update(results: List<BenchmarkResult>, today: LocalDate, change: (BenchmarkResult) -> BenchmarkResult): List<BenchmarkResult> {
        val d = today.toString()
        val current = results.firstOrNull { it.date == d } ?: BenchmarkResult(d)
        return (results.filter { it.date != d } + change(current)).sortedBy { it.date }
    }
}
