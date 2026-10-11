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
) {
    fun problems(): List<String> = buildList {
        if (!Regex("""^\d+\.\d+$""").matches(id)) add("$id: bad id")
        if (title.isBlank() || goal.isBlank()) add("$id: missing title/goal")
        if (explain.isEmpty()) add("$id: no explanation")
        if (tryIt.isEmpty()) add("$id: no try-with-app steps")
        if (drills.isEmpty()) add("$id: no drills")
        if (tips.isEmpty()) add("$id: no tips")
        if (passText.isBlank()) add("$id: no pass text")
        if (tracks.any { it !in TRACKS }) add("$id: unknown track")
        if (pass.drill !in drills.indices) add("$id: pass rule points to a missing drill")
        drills.forEach { d -> d.problems().forEach { add("$id/${d.title}: $it") } }
    }

    companion object {
        val TRACKS = setOf("KEYS", "READ_PITCH", "READ_RHYTHM", "HANDS", "THEORY", "EAR", "REPERTOIRE")
        private val json = Json { ignoreUnknownKeys = false }
        fun parse(text: String): Lesson = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class TryStep(
    val text: String,
    /** Optional app action: "highlight:C" plays and highlights every key of that letter; "count:C" asks how many there are. */
    val action: String? = null,
)

@Serializable
enum class ExerciseType { E_KEYFIND, E_NOTEID, E_PULSE }

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
) {
    fun problems(): List<String> = buildList {
        when (type) {
            ExerciseType.E_KEYFIND -> if (letters.isEmpty() || letters.any { it !in listOf("C", "D", "E", "F", "G", "A", "B") }) add("bad letters")
            ExerciseType.E_NOTEID -> if (pool.isEmpty() || pool.any { !Regex("""^[A-G](#|b)?\d:(treble|bass)$""").matches(it) }) add("bad pool")
            ExerciseType.E_PULSE -> if (bpmMin !in 40..200 || bpmMax < bpmMin || beats < 8) add("bad pulse params")
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
)
