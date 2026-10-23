package com.id.mimikopi.core.lesson

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A lesson as written in content/lessons/<id>.json, following the blueprint in
 * learning-system.md section 4 and the sample format of section 10.
 */
@Serializable
data class Lesson(
    val id: String,
    val stage: Int,
    val title: String,
    /** The can-do statement. */
    val goal: String,
    val minutes: Int,
    val tracks: List<String>,
    val prerequisites: List<String> = emptyList(),
    /** Short Thai explanation paragraphs (about one minute of reading). */
    val explain: List<String>,
    /** "Try with the app" steps: not scored. */
    val tryIt: List<TryStep>,
    val drills: List<Drill>,
    val pass: PassRule,
    val passText: String,
    val srsItems: List<String>,
    val tips: List<String>,
    /** Honest caveats shown to the user (e.g. unverified numbers). */
    val caveats: List<String> = emptyList(),
    /** Step ladder (M14): the full explanation of the topic, shown on request ("อ่านละเอียด"). */
    val more: List<String> = emptyList(),
    /** Step ladder: the stage-1-6 lesson drill this step came from ("1.5#1"), for migrating old progress. */
    val legacy: String? = null,
) {
    fun problems(): List<String> = buildList {
        if (!Regex("""^(\d+\.\d+|e\d+)$""").matches(id)) add("$id: bad id")
        if (title.isBlank() || goal.isBlank()) add("$id: missing title/goal")
        if (explain.isEmpty()) add("$id: no explanation")
        if (drills.isEmpty()) add("$id: no drills")
        if (tips.isEmpty()) add("$id: no tips")
        if (passText.isBlank()) add("$id: no pass text")
        if (tracks.any { it !in TRACKS }) add("$id: unknown track")
        if (pass.requiredDrills.any { it !in drills.indices }) add("$id: pass rule points to a missing drill")
        for (i in pass.requiredDrills) {
            val t = drills.getOrNull(i)?.type ?: continue
            val ok = when (t) {
                ExerciseType.E_KEYFIND -> true
                ExerciseType.E_NOTEID, ExerciseType.E_INTERVAL_READ -> pass.minAccuracy != null
                ExerciseType.E_PULSE -> pass.maxSdMs != null
                // A wait-mode run has no timing, so its clean runs are judged by first-try accuracy.
                ExerciseType.E_FINGER_PATTERN, ExerciseType.E_RHYTHM_TAP, ExerciseType.E_READ_PIECE ->
                    pass.cleanRuns != null && (drills[i].mode != "WAIT" || pass.minAccuracy != null)
                ExerciseType.E_PIECE -> pass.piecesPassed != null || pass.songsCompleted != null ||
                    (pass.cleanRuns != null && (drills[i].mode != "WAIT" || pass.minAccuracy != null))
                ExerciseType.E_CHORD_BUILD -> pass.minAccuracy != null
                ExerciseType.E_CHORD_CHANGE -> pass.minCount != null
                ExerciseType.E_COMP_PATTERN, ExerciseType.E_TRANSPOSE -> pass.cleanRuns != null && (drills[i].mode != "WAIT" || pass.minAccuracy != null)
            }
            if (!ok) add("$id: pass rule lacks the criterion for drill $i ($t)")
        }
        drills.forEach { d -> d.problems().forEach { add("$id/${d.title}: $it") } }
    }

    companion object {
        val TRACKS = setOf("KEYS", "READ_PITCH", "READ_RHYTHM", "HANDS", "THEORY", "EAR", "REPERTOIRE")
        private val json = Json { ignoreUnknownKeys = false }
        fun parse(text: String): Lesson = json.decodeFromString(serializer(), com.id.mimikopi.core.i18n.ContentL10n.translate(text))
    }
}

@Serializable
data class TryStep(
    val text: String,
    /** Optional app action: "highlight:C" plays and highlights every key of that letter; "count:C" asks how many there are. */
    val action: String? = null,
)

@Serializable
enum class ExerciseType {
    E_KEYFIND, E_NOTEID, E_PULSE,
    /** Play generated finger-number sequences in a five-finger position (learning-system.md 5.1). */
    E_FINGER_PATTERN,
    /** Read a generated rhythm and tap it on any key. */
    E_RHYTHM_TAP,
    /** Name the motion (repeat/step/skip or the interval) between written notes, then play them. */
    E_INTERVAL_READ,
    /** Sight-read a newly generated short piece at a level of section 5.2 (at most 3 runs each). */
    E_READ_PIECE,
    /** Play fixed pieces or exercises from content (scales, repertoire). */
    E_PIECE,
    /** Build the chord that is named (by symbol or roman numeral): quick and right. */
    E_CHORD_BUILD,
    /** Change between the chords of a progression for 60 seconds; count the correct changes. */
    E_CHORD_CHANGE,
    /** An accompaniment pattern of the 8.2 ladder under a progression (with a melody the app can play). */
    E_COMP_PATTERN,
    /** Read a short phrase in one key and play it in another. */
    E_TRANSPOSE;

    /** Exercises that run in the score reader with play-along judging. */
    val isScore: Boolean get() = this in setOf(E_FINGER_PATTERN, E_RHYTHM_TAP, E_READ_PIECE, E_PIECE, E_COMP_PATTERN, E_TRANSPOSE)
}

@Serializable
data class Drill(
    val type: ExerciseType,
    val title: String,
    val items: Int = 10,
    /** E_KEYFIND: letters asked. */
    val letters: List<String> = emptyList(),
    /** E_KEYFIND: true = a specific octave must be pressed; false = any key of that name. */
    val exactOctave: Boolean = false,
    /** E_NOTEID: pitches in the pool, e.g. "G4:treble". */
    val pool: List<String> = emptyList(),
    /** E_PULSE: tempo range and number of beats. */
    val bpmMin: Int = 60,
    val bpmMax: Int = 80,
    val beats: Int = 32,
    /** Score drills: hand(s) played — RIGHT, LEFT or BOTH. */
    val hand: String = "RIGHT",
    /** Five-finger position(s): C, G, D, F. Empty = C for finger patterns, the level's own for sight reading. */
    val positions: List<String> = emptyList(),
    /** Score drills: tempo, and WAIT or TEMPO mode. */
    val bpm: Int = 60,
    val mode: String = "TEMPO",
    val bars: Int = 4,
    val time: List<Int> = listOf(4, 4),
    /** E_RHYTHM_TAP: rhythm vocabulary (see RhythmCells) for the right hand / the left hand (BOTH). */
    val rhythms: List<String> = emptyList(),
    val rhythmsLeft: List<String> = emptyList(),
    /** E_READ_PIECE: sight-reading level L1..L5. */
    val level: String = "L1",
    /** E_READ_PIECE L5: hands take turns bar by bar instead of playing together. */
    val alternate: Boolean = false,
    /** Key signature for generated pieces (fifths); null = from the position. */
    val key: Int? = null,
    /** E_INTERVAL_READ: allowed staff steps between neighbours, notes per set, clef, range, answer kind. */
    val intervals: List<Int> = emptyList(),
    val notesPerSet: Int = 4,
    val clef: String = "treble",
    val low: String = "C4",
    val high: String = "G5",
    val answer: String = "MOTION",
    /** E_PIECE: ids of pieces (content/pieces) or exercises (content/exercises). */
    val pieces: List<String> = emptyList(),
    /** Stage 4-6: keys as fifths (chord drills, generated pieces; E_TRANSPOSE: the key it is written in). */
    val keys: List<Int> = emptyList(),
    /** E_CHORD_BUILD: chord qualities ("", "m", "dim", "aug", "7", "maj7", ...). */
    val qualities: List<String> = emptyList(),
    /** E_CHORD_BUILD by roman numeral ("I", "vi", "V7", "IVmaj7"). */
    val degrees: List<String> = emptyList(),
    /** E_CHORD_CHANGE: duration in seconds. */
    val seconds: Int = 60,
    /** E_COMP_PATTERN / E_CHORD_CHANGE: a progression name of 8.1 or roman numerals ("I V vi IV"). */
    val progression: String = "",
    /** E_COMP_PATTERN: step 1-7 of the accompaniment ladder (8.2). */
    val pattern: Int = 1,
    /** E_TRANSPOSE: keys to move to. */
    val targetKeys: List<Int> = emptyList(),
    /** Report sustain-pedal changes (learning-system.md 4.5); informative, never a pass condition. */
    val pedal: Boolean = false,
    /** L8 lead sheets: the app plays a bass line as a backing track. */
    val backing: Boolean = false,
) {
    /** Judges onset timing, so it needs a piano on a cable (learning-system.md 2.11). */
    val judgesTiming: Boolean get() = type == ExerciseType.E_PULSE || (type.isScore && mode == "TEMPO")

    fun problems(): List<String> = buildList {
        if (type.isScore || type == ExerciseType.E_INTERVAL_READ) {
            if (hand !in listOf("RIGHT", "LEFT", "BOTH")) add("bad hand")
            if (mode !in listOf("WAIT", "TEMPO")) add("bad mode")
            if (positions.any { it !in listOf("C", "G", "D", "F") }) add("bad positions")
            if (bpm !in 30..200) add("bad bpm")
        }
        when (type) {
            ExerciseType.E_KEYFIND -> if (letters.isEmpty() || letters.any { it !in listOf("C", "D", "E", "F", "G", "A", "B") }) add("bad letters")
            ExerciseType.E_NOTEID -> if (pool.isEmpty() || pool.any { !Regex("""^[A-G](#|b)?\d:(treble|bass)$""").matches(it) }) add("bad pool")
            ExerciseType.E_PULSE -> if (bpmMin !in 40..200 || bpmMax < bpmMin || beats < 8) add("bad pulse params")
            ExerciseType.E_FINGER_PATTERN -> if (hand == "BOTH") add("finger patterns are one hand at a time")
            ExerciseType.E_RHYTHM_TAP -> {
                if (rhythms.isEmpty()) add("no rhythms")
                if (hand == "BOTH" && rhythmsLeft.isEmpty()) add("no left-hand rhythms")
                if (time.size != 2 || time[1] != 4 || time[0] !in 2..4) add("rhythm drills use 2/4, 3/4 or 4/4")
            }
            ExerciseType.E_INTERVAL_READ -> {
                if (intervals.isEmpty() || intervals.any { it !in 0..7 }) add("bad intervals")
                if (notesPerSet !in 2..6) add("bad notesPerSet")
                if (clef !in listOf("treble", "bass")) add("bad clef")
                if (answer !in listOf("MOTION", "INTERVAL")) add("bad answer kind")
            }
            ExerciseType.E_READ_PIECE -> if (level !in (1..8).map { "L$it" }) add("bad level")
            ExerciseType.E_PIECE -> if (pieces.isEmpty()) add("no pieces")
            ExerciseType.E_CHORD_BUILD -> {
                if (degrees.isEmpty() && qualities.isEmpty()) add("no chords to build")
                if (qualities.any { it !in com.id.mimikopi.core.theory.ChordQualities.INTERVALS }) add("bad quality")
                if (keys.any { it !in -6..6 }) add("bad key")
            }
            ExerciseType.E_CHORD_CHANGE -> {
                if (progression.isBlank() || keys.isEmpty()) add("no progression or key")
                if (seconds !in 10..300) add("bad seconds")
            }
            ExerciseType.E_COMP_PATTERN -> {
                if (pattern !in 1..7) add("bad pattern")
                if (progression.isBlank() || keys.isEmpty()) add("no progression or key")
            }
            ExerciseType.E_TRANSPOSE -> if (keys.isEmpty() || targetKeys.isEmpty()) add("no keys")
        }
        if (items <= 0) add("no items")
    }
}

/** How a lesson is passed (learning-system.md 6.4 plus the lesson's own criterion). */
@Serializable
data class PassRule(
    /** Index into [Lesson.drills] whose result decides the pass. */
    val drill: Int,
    /** E_KEYFIND: every item correct on the first try, each within this time. */
    val maxSecondsPerItem: Double? = null,
    /** E_NOTEID: required fraction correct. */
    val minAccuracy: Double? = null,
    /** E_PULSE: max standard deviation of onsets after latency compensation. */
    val maxSdMs: Double? = null,
    /** Number of different days the criterion must be met on. */
    val days: Int = 2,
    /** Score drills: clean runs (learning-system.md 6.1) needed in one sitting. */
    val cleanRuns: Int? = null,
    /** True = the clean runs must come in a row; false = any clean runs in the sitting (sight reading). */
    val inRow: Boolean = true,
    /** Other drills that must also meet their criterion on the same day (stage tests, both hands). */
    val alsoDrills: List<Int> = emptyList(),
    /** E_PIECE: how many of the drill's pieces must be passed (piece rule: 3 clean runs at target on 2 days). */
    val piecesPassed: Int? = null,
    /** E_CHORD_CHANGE: correct changes needed in the time. */
    val minCount: Int? = null,
    /** Capstone B: songs in the library with all 7 pipeline days done and the linked score passed. */
    val songsCompleted: Int? = null,
) {
    val requiredDrills: List<Int> get() = (listOf(drill) + alsoDrills).distinct()
}
