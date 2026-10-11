package com.id.mimikopi.core.score

import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Hand { RIGHT, LEFT }

/**
 * A piano piece as stored in content/pieces/<id>.json: one string per measure per hand, tokens like
 * "E4/q", "C4+E4+G4/h" (chord), "r/q" (rest), durations w h. h q e (whole, dotted half, half, quarter,
 * eighth). Hand-writable on purpose; MusicXML import/export comes with the editor (M10).
 */
@Serializable
data class PieceFile(
    val id: String,
    val title: String,
    val composer: String,
    /** Where the music comes from and why it may be bundled (public domain / original). */
    val source: String,
    val level: String,
    /** Key signature as fifths: -1 = F major, 0 = C, 1 = G ... */
    val key: Int = 0,
    val time: List<Int> = listOf(4, 4),
    val tempo: Int = 80,
    val rh: List<String>,
    val lh: List<String>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = false }
        fun parse(text: String): PieceFile = json.decodeFromString(serializer(), text)
    }
}

/** One written event: a note/chord (pitches non-empty) or a rest (pitches empty). */
data class ScoreEvent(
    val id: Int,
    val hand: Hand,
    val measure: Int,
    /** Onset from the start of the piece, in quarter-note beats. */
    val onset: Double,
    val duration: Double,
    val pitches: List<SpelledPitch>,
    /** Written finger numbers (1-5), parallel to [pitches]; empty when none are written. */
    val fingers: List<Int> = emptyList(),
) {
    val isRest: Boolean get() = pitches.isEmpty()
    val end: Double get() = onset + duration
}

data class Score(
    val id: String,
    val title: String,
    val composer: String,
    val source: String,
    val level: String,
    val keyFifths: Int,
    val beatsPerMeasure: Int,
    val beatUnit: Int,
    val tempo: Int,
    val measures: Int,
    val events: List<ScoreEvent>,
) {
    /** Measure length in quarter-note beats. */
    val measureBeats: Double get() = beatsPerMeasure * 4.0 / beatUnit
    val totalBeats: Double get() = measures * measureBeats

    fun clefFor(hand: Hand) = if (hand == Hand.RIGHT) Clef.TREBLE else Clef.BASS

    fun measureStart(measure: Int): Double = measure * measureBeats

    companion object {
        val DURATIONS = mapOf("w" to 4.0, "h." to 3.0, "h" to 2.0, "q." to 1.5, "q" to 1.0, "e." to 0.75, "e" to 0.5, "s" to 0.25)

        /** Parses and checks a piece: every measure must add up exactly to the time signature. */
        fun from(file: PieceFile): Score {
            require(file.rh.size == file.lh.size) { "${file.id}: hands have different measure counts" }
            require(file.time.size == 2 && file.time[0] in 1..12 && file.time[1] in listOf(2, 4, 8)) { "${file.id}: bad time signature" }
            require(file.key in -7..7) { "${file.id}: bad key" }
            val measureBeats = file.time[0] * 4.0 / file.time[1]
            val events = mutableListOf<ScoreEvent>()
            var nextId = 0
            for ((hand, measures) in listOf(Hand.RIGHT to file.rh, Hand.LEFT to file.lh)) {
                measures.forEachIndexed { m, text ->
                    var t = m * measureBeats
                    for (token in text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                        val at = token.indexOf('@')
                        val body = if (at >= 0) token.substring(0, at) else token
                        val slash = body.lastIndexOf('/')
                        require(slash > 0) { "${file.id} m${m + 1}: bad token '$token'" }
                        val dur = DURATIONS[body.substring(slash + 1)] ?: error("${file.id} m${m + 1}: bad duration in '$token'")
                        val head = body.substring(0, slash)
                        val pitches = if (head == "r") emptyList() else head.split("+").map { SpelledPitch.parse(it) }
                        val fingers = if (at >= 0) token.substring(at + 1).split("+").map { it.toInt() } else emptyList()
                        require(fingers.isEmpty() || (fingers.size == pitches.size && fingers.all { it in 1..5 })) {
                            "${file.id} m${m + 1}: fingers in '$token' must match the notes (1-5)"
                        }
                        // Fingers follow the pitches as written; keep them paired when sorting a chord.
                        val paired = pitches.indices.sortedBy { pitches[it].midi }
                        events += ScoreEvent(nextId++, hand, m, t, dur, paired.map { pitches[it] }, if (fingers.isEmpty()) emptyList() else paired.map { fingers[it] })
                        t += dur
                    }
                    val used = t - m * measureBeats
                    require(kotlin.math.abs(used - measureBeats) < 1e-9) { "${file.id} ${hand.name} m${m + 1}: $used beats, expected $measureBeats" }
                }
            }
            return Score(file.id, file.title, file.composer, file.source, file.level, file.key, file.time[0], file.time[1], file.tempo,
                file.rh.size, events.sortedWith(compareBy({ it.onset }, { it.hand })))
        }
    }
}

/** Key signature helpers (standard engraving order and positions). */
object KeySignature {
    private val SHARP_ORDER = listOf(Letter.F, Letter.C, Letter.G, Letter.D, Letter.A, Letter.E, Letter.B)
    private val FLAT_ORDER = SHARP_ORDER.reversed()

    /** Accidental the key gives each letter (+1 sharp, -1 flat, 0 natural). */
    fun accidentalFor(letter: Letter, fifths: Int): Int = when {
        fifths > 0 && letter in SHARP_ORDER.take(fifths) -> 1
        fifths < 0 && letter in FLAT_ORDER.take(-fifths) -> -1
        else -> 0
    }

    /** Staff positions of the key signature glyphs, in drawing order. */
    fun positions(fifths: Int, clef: Clef): List<Int> {
        val trebleSharps = listOf(4, 1, 5, 2, -1, 3, 0)
        val trebleFlats = listOf(0, 3, -1, 2, -2, 1, -3)
        val shift = if (clef == Clef.BASS) -2 else 0
        return when {
            fifths > 0 -> trebleSharps.take(fifths).map { it + shift }
            fifths < 0 -> trebleFlats.take(-fifths).map { it + shift }
            else -> emptyList()
        }
    }
}
