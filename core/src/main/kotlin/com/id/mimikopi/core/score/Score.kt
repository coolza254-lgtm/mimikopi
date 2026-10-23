package com.id.mimikopi.core.score

import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Hand { RIGHT, LEFT }

/**
 * A piano piece as stored in content/pieces/<id>.json (and user scores): one string per measure per hand, tokens
 * like "E4/q", "C4+E4+G4/h" (chord), "r/q" (rest), durations w h. h q. q e. e s; "~" after the duration ties to
 * the next note ("C4/h~"); "@1" or "@1+3+5" gives finger numbers. Optional per-measure chord symbols
 * ("C@0 Am@2": symbol@beat in quarters) and repeat barlines ("start", "end", "both").
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
    /** Per measure (may be shorter than the piece): "C@0 G7@2". */
    val chords: List<String> = emptyList(),
    /** Per measure: "", "start", "end" or "both". */
    val repeats: List<String> = emptyList(),
    val rhClef: String = "treble",
    val lhClef: String = "bass",
    /** Per measure: section mark shown above the staff ("Intro", "Chorus" ...), "" for none. */
    val marks: List<String> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = false; encodeDefaults = false }
        fun parse(text: String): PieceFile = json.decodeFromString(serializer(), com.id.mimikopi.core.i18n.ContentL10n.translate(text))
    }
}

/** A chord symbol above the staff at [beat] (quarter beats from the start of the piece). */
data class ChordSymbol(val beat: Double, val text: String)

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
    /** Tied to the next note of the same hand (held, not played again). */
    val tieToNext: Boolean = false,
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
    val chordSymbols: List<ChordSymbol> = emptyList(),
    val repeatStart: Set<Int> = emptySet(),
    val repeatEnd: Set<Int> = emptySet(),
    val rhClef: Clef = Clef.TREBLE,
    val lhClef: Clef = Clef.BASS,
    /** Section marks by measure. */
    val marks: Map<Int, String> = emptyMap(),
) {
    /** Measure length in quarter-note beats. */
    val measureBeats: Double get() = beatsPerMeasure * 4.0 / beatUnit
    val totalBeats: Double get() = measures * measureBeats

    fun clefFor(hand: Hand) = if (hand == Hand.RIGHT) rhClef else lhClef

    /**
     * Notes as heard: tied notes merged into one long note. (onset, duration, MIDI, hand, first event id), sorted by onset.
     */
    data class Sounding(val onset: Double, val duration: Double, val midi: Int, val hand: Hand, val eventId: Int)

    fun sounding(): List<Sounding> {
        val out = mutableListOf<Sounding>()
        for (hand in Hand.entries) {
            val evs = events.filter { it.hand == hand }.sortedBy { it.onset }
            val open = mutableMapOf<Int, Sounding>() // midi -> note being held through a tie
            for (e in evs) {
                val continuing = open.keys.toSet()
                val next = mutableMapOf<Int, Sounding>()
                for (p in e.pitches) {
                    val held = open[p.midi]
                    val note = if (held != null) held.copy(duration = held.duration + e.duration) else Sounding(e.onset, e.duration, p.midi, hand, e.id)
                    if (e.tieToNext) next[p.midi] = note else out += note
                }
                // Held notes the event did not continue end where they were.
                for (m in continuing) if (e.pitches.none { it.midi == m }) out += open.getValue(m)
                open.clear()
                open.putAll(next)
            }
            out += open.values
        }
        return out.sortedWith(compareBy({ it.onset }, { it.midi }))
    }

    /** Events whose notes continue a tie from the previous event (not played again). */
    fun tiedContinuations(): Set<Int> {
        val out = mutableSetOf<Int>()
        for (hand in Hand.entries) {
            val evs = events.filter { it.hand == hand }.sortedBy { it.onset }
            evs.zipWithNext().forEach { (a, b) -> if (a.tieToNext && !b.isRest && b.pitches.map { it.midi } == a.pitches.map { it.midi }) out += b.id }
        }
        return out
    }

    /** Measures [from]..[to] as a piece of their own (for printing systems); events keep their ids. */
    fun slice(from: Int, to: Int): Score {
        val shift = measureStart(from)
        return copy(
            measures = to - from + 1,
            events = events.filter { it.measure in from..to }.map { it.copy(measure = it.measure - from, onset = it.onset - shift) },
            chordSymbols = chordSymbols.filter { it.beat >= shift - 1e-9 && it.beat < measureStart(to + 1) - 1e-9 }.map { it.copy(beat = it.beat - shift) },
            repeatStart = repeatStart.filter { it in from..to }.map { it - from }.toSet(),
            repeatEnd = repeatEnd.filter { it in from..to }.map { it - from }.toSet(),
            marks = marks.filterKeys { it in from..to }.mapKeys { it.key - from },
        )
    }

    /** Measure order when playing with repeats (a forward repeat sign at the start if none is written). */
    fun playOrder(): List<Int> {
        val out = mutableListOf<Int>()
        var sectionStart = 0
        for (m in 0 until measures) {
            if (m in repeatStart) sectionStart = m
            out += m
            if (m in repeatEnd) {
                out += (sectionStart..m)
                sectionStart = m + 1
            }
        }
        return out
    }

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
                        var body = if (at >= 0) token.substring(0, at) else token
                        val tie = body.endsWith("~")
                        if (tie) body = body.dropLast(1)
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
                        require(!tie || pitches.isNotEmpty()) { "${file.id} m${m + 1}: a rest cannot be tied" }
                        events += ScoreEvent(nextId++, hand, m, t, dur, paired.map { pitches[it] }, if (fingers.isEmpty()) emptyList() else paired.map { fingers[it] }, tie)
                        t += dur
                    }
                    val used = t - m * measureBeats
                    require(kotlin.math.abs(used - measureBeats) < 1e-9) { "${file.id} ${hand.name} m${m + 1}: $used beats, expected $measureBeats" }
                }
            }
            val chords = file.chords.flatMapIndexed { m, text ->
                text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { tok ->
                    val i = tok.lastIndexOf('@')
                    require(i > 0) { "${file.id} m${m + 1}: bad chord symbol '$tok'" }
                    val beat = tok.substring(i + 1).toDouble()
                    require(beat >= 0 && beat < measureBeats) { "${file.id} m${m + 1}: chord beat out of the measure" }
                    ChordSymbol(m * measureBeats + beat, tok.substring(0, i))
                }
            }
            require(file.chords.size <= file.rh.size && file.repeats.size <= file.rh.size) { "${file.id}: more chord/repeat entries than measures" }
            val starts = file.repeats.withIndex().filter { it.value == "start" || it.value == "both" }.map { it.index }.toSet()
            val ends = file.repeats.withIndex().filter { it.value == "end" || it.value == "both" }.map { it.index }.toSet()
            require(file.repeats.all { it in setOf("", "start", "end", "both") }) { "${file.id}: bad repeat mark" }
            fun clef(name: String) = when (name) { "treble" -> Clef.TREBLE; "bass" -> Clef.BASS; else -> error("${file.id}: bad clef $name") }
            return Score(file.id, file.title, file.composer, file.source, file.level, file.key, file.time[0], file.time[1], file.tempo,
                file.rh.size, events.sortedWith(compareBy({ it.onset }, { it.hand })), chords, starts, ends, clef(file.rhClef), clef(file.lhClef),
                file.marks.withIndex().filter { it.value.isNotBlank() && it.index < file.rh.size }.associate { it.index to it.value.trim() })
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

    /**
     * The seven notes of the major key with [fifths], by pitch class (0 = C), spelled as the key spells them: E major
     * gives F♯ C♯ G♯ D♯, A♭ major gives B♭ E♭ A♭ D♭. A keyboard labels these keys and leaves the others blank.
     */
    fun scale(fifths: Int): Map<Int, SpelledPitch> = Letter.entries.associate { l ->
        val acc = accidentalFor(l, fifths)
        Math.floorMod(l.semitonesFromC + acc, 12) to SpelledPitch(l, acc, 4)
    }

    /** "E Major", "A♭ Major": the major key with [fifths] (a minor piece shares it with its relative major). */
    fun majorName(fifths: Int): String {
        val tonic = scale(fifths).getValue(Math.floorMod(fifths * 7, 12))
        return tonic.letter.name + SpelledPitch.accidentalSymbol(tonic.accidental) + " Major"
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
