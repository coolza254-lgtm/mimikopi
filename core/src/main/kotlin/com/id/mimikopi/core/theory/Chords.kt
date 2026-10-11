package com.id.mimikopi.core.theory

import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch

/** A pitch class with its spelling (letter + accidental), no octave. */
data class PitchName(val letter: Letter, val accidental: Int) {
    val pc: Int get() = Math.floorMod(letter.semitonesFromC + accidental, 12)
    override fun toString(): String = letter.name + when (accidental) { 1 -> "#"; -1 -> "b"; 2 -> "##"; -2 -> "bb"; else -> "" }
    fun display(): String = letter.name + SpelledPitch.accidentalSymbol(accidental)
    fun at(octave: Int) = SpelledPitch(letter, accidental, octave)

    companion object {
        fun parse(s: String): PitchName? {
            val m = Regex("""^([A-G])(##|#|bb|b|♯|♭)?$""").matchEntire(s) ?: return null
            val acc = when (m.groupValues[2]) { "#", "♯" -> 1; "b", "♭" -> -1; "##" -> 2; "bb" -> -2; else -> 0 }
            return PitchName(Letter.valueOf(m.groupValues[1]), acc)
        }
    }
}

/** Major keys by fifths (-6..6), with their tonic and spelling preference. */
object Keys {
    private val TONICS = mapOf(
        -6 to "Gb", -5 to "Db", -4 to "Ab", -3 to "Eb", -2 to "Bb", -1 to "F", 0 to "C",
        1 to "G", 2 to "D", 3 to "A", 4 to "E", 5 to "B", 6 to "F#",
    )

    fun tonic(fifths: Int): PitchName = PitchName.parse(TONICS.getValue(fifths.coerceIn(-6, 6)))!!

    /** Name for display, e.g. "E♭ เมเจอร์" is built by the UI from this. */
    fun name(fifths: Int): String = tonic(fifths).display()

    fun fifthsOf(tonic: PitchName): Int? = TONICS.entries.firstOrNull { PitchName.parse(it.value) == tonic }?.key

    private val MAJOR = listOf(0, 2, 4, 5, 7, 9, 11)

    /** The seven spelled notes of a major key. */
    fun scale(fifths: Int): List<PitchName> {
        val t = tonic(fifths)
        return (0 until 7).map { i ->
            val letter = Letter.entries[(t.letter.ordinal + i) % 7]
            val target = Math.floorMod(t.pc + MAJOR[i], 12)
            var acc = target - letter.semitonesFromC
            while (acc > 6) acc -= 12
            while (acc < -6) acc += 12
            PitchName(letter, acc)
        }
    }

    /** Spells a pitch class: as in the key when it belongs to it, else sharps in sharp keys and C, flats in flat keys. */
    fun spell(pc: Int, fifths: Int): PitchName {
        val p = Math.floorMod(pc, 12)
        scale(fifths).firstOrNull { it.pc == p }?.let { return it }
        val sharp = SpelledPitch.fromMidiSharp(60 + p)
        if (fifths >= 0) return PitchName(sharp.letter, sharp.accidental)
        val up = Letter.entries[(sharp.letter.ordinal + 1) % 7]
        return PitchName(up, -1)
    }

    /** Spells a MIDI note in a key (octave follows the letter, so B#3 / Cb4 come out right). */
    fun spellMidi(midi: Int, fifths: Int): SpelledPitch {
        val n = spell(midi % 12, fifths)
        val base = n.letter.semitonesFromC + n.accidental
        val octave = Math.floorDiv(midi - base, 12) - 1
        return SpelledPitch(n.letter, n.accidental, octave)
    }

    fun inKey(pc: Int, fifths: Int): Boolean = scale(fifths).any { it.pc == Math.floorMod(pc, 12) }
}

/** Chord qualities: suffix as written -> intervals from the root. */
object ChordQualities {
    val INTERVALS: Map<String, List<Int>> = linkedMapOf(
        "" to listOf(0, 4, 7), "m" to listOf(0, 3, 7), "dim" to listOf(0, 3, 6), "aug" to listOf(0, 4, 8),
        "sus2" to listOf(0, 2, 7), "sus4" to listOf(0, 5, 7), "6" to listOf(0, 4, 7, 9), "m6" to listOf(0, 3, 7, 9),
        "7" to listOf(0, 4, 7, 10), "maj7" to listOf(0, 4, 7, 11), "m7" to listOf(0, 3, 7, 10), "m7b5" to listOf(0, 3, 6, 10),
        "dim7" to listOf(0, 3, 6, 9), "7sus4" to listOf(0, 5, 7, 10), "mM7" to listOf(0, 3, 7, 11), "aug7" to listOf(0, 4, 8, 10),
        "add9" to listOf(0, 4, 7, 14), "madd9" to listOf(0, 3, 7, 14), "9" to listOf(0, 4, 7, 10, 14), "maj9" to listOf(0, 4, 7, 11, 14),
        "m9" to listOf(0, 3, 7, 10, 14), "7b9" to listOf(0, 4, 7, 10, 13), "11" to listOf(0, 4, 7, 10, 14, 17), "13" to listOf(0, 4, 7, 10, 14, 21),
    )
    /** Other ways people write the same suffix. */
    val ALIASES = mapOf(
        "M" to "", "maj" to "", "min" to "m", "-" to "m", "°" to "dim", "o" to "dim", "+" to "aug", "sus" to "sus4",
        "M7" to "maj7", "△7" to "maj7", "Maj7" to "maj7", "△" to "maj7", "-7" to "m7", "min7" to "m7", "ø" to "m7b5", "ø7" to "m7b5",
        "m7-5" to "m7b5", "o7" to "dim7", "°7" to "dim7", "M9" to "maj9", "7#5" to "aug7", "+7" to "aug7", "7(b9)" to "7b9", "7-9" to "7b9",
        "add2" to "add9", "mmaj7" to "mM7", "m(maj7)" to "mM7",
    )

    /** Thai names for the lesson UI. */
    val NAMES = mapOf(
        "" to "เมเจอร์", "m" to "ไมเนอร์", "dim" to "ดิมินิช", "aug" to "ออกเมนเต็ด", "sus2" to "ซัส 2", "sus4" to "ซัส 4",
        "6" to "ซิกซ์", "m6" to "ไมเนอร์ซิกซ์", "7" to "โดมิแนนท์ 7", "maj7" to "เมเจอร์ 7", "m7" to "ไมเนอร์ 7", "m7b5" to "ฮาล์ฟดิมินิช (m7♭5)",
        "dim7" to "ดิมินิช 7", "7sus4" to "7 ซัส 4", "mM7" to "ไมเนอร์เมเจอร์ 7", "aug7" to "ออกเมนเต็ด 7", "add9" to "แอด 9", "madd9" to "ไมเนอร์แอด 9",
        "9" to "ไนน์", "maj9" to "เมเจอร์ 9", "m9" to "ไมเนอร์ 9", "7b9" to "7♭9", "11" to "อีเลฟเวนท์", "13" to "เทอร์ทีนท์",
    )
}

/** A parsed chord symbol such as "Am7", "F/A", "Bbmaj9" or "G7sus4". */
data class Chord(val root: PitchName, val quality: String, val bass: PitchName? = null) {
    val intervals: List<Int> get() = ChordQualities.INTERVALS.getValue(quality)
    val pitchClasses: Set<Int> get() = (intervals.map { Math.floorMod(root.pc + it, 12) } + listOfNotNull(bass?.pc)).toSet()
    /** The notes that must be played (bass included); extensions above the 9th may drop the 5th. */
    val essential: Set<Int> get() {
        val all = pitchClasses
        return if (intervals.size >= 5) all - Math.floorMod(root.pc + 7, 12) else all
    }
    val isMinorThird: Boolean get() = 3 in intervals
    val isSeventh: Boolean get() = intervals.any { it == 10 || it == 11 } || quality == "dim7"

    val symbol: String get() = root.toString() + quality + (bass?.let { "/$it" } ?: "")
    fun display(): String = root.display() + quality.replace("b", "♭").replace("#", "♯") + (bass?.let { "/" + it.display() } ?: "")

    /**
     * Close root-position voicing with the root in [low, low+11] (MIDI), plus the bass an octave below when a slash
     * bass is written.
     */
    fun voicing(low: Int = 55): List<Int> {
        var r = root.pc
        while (r < low) r += 12
        while (r >= low + 12) r -= 12
        val upper = intervals.map { r + it }
        val b = bass?.let { var x = it.pc; while (x >= r) x -= 12; while (x < r - 12) x += 12; x }
        return (listOfNotNull(b) + upper).sorted()
    }

    /** All inversions of the close voicing within an octave window starting at [low]. */
    fun inversions(low: Int = 55): List<List<Int>> {
        val base = intervals.filter { it < 12 }.map { Math.floorMod(root.pc + it, 12) }
        return base.indices.map { k ->
            val order = base.drop(k) + base.take(k)
            val out = mutableListOf<Int>()
            var prev = low - 1
            for (pc in order) {
                var n = pc
                while (n <= prev) n += 12
                while (n - 12 > prev) n -= 12
                out += n
                prev = n
            }
            out
        }
    }

    /** The inversion that moves least from [previous] (smooth voice leading, learning-system.md 5.2). */
    fun closestTo(previous: List<Int>, low: Int = 52): List<Int> {
        if (previous.isEmpty()) return inversions(low + 3).first()
        val center = previous.average()
        val candidates = (listOf(low, low + 6, low + 12)).flatMap { inversions(it) }.distinct()
        return candidates.minBy { c -> c.sumOf { n -> previous.minOf { kotlin.math.abs(it - n) } } + kotlin.math.abs(c.average() - center) }
    }

    fun transpose(semitones: Int, toFifths: Int): Chord =
        Chord(Keys.spell(root.pc + semitones, toFifths), quality, bass?.let { Keys.spell(it.pc + semitones, toFifths) })

    companion object {
        private val PATTERN = Regex("""^([A-G](?:##|#|bb|b|♯|♭)?)([^/]*)(?:/([A-G](?:##|#|bb|b|♯|♭)?))?$""")

        fun parse(text: String): Chord? {
            val m = PATTERN.matchEntire(text.trim().replace(" ", "")) ?: return null
            val root = PitchName.parse(m.groupValues[1]) ?: return null
            var suffix = m.groupValues[2].replace("♭", "b").replace("♯", "#")
            suffix = ChordQualities.ALIASES[suffix] ?: suffix
            if (suffix !in ChordQualities.INTERVALS) return null
            val bass = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { PitchName.parse(it) ?: return null }
            return Chord(root, suffix, bass)
        }

        /** Does the set of held keys sound this chord (any inversion, any octave, nothing extra)? */
        fun matches(chord: Chord, held: Set<Int>): Boolean {
            val pcs = held.map { Math.floorMod(it, 12) }.toSet()
            if (!pcs.containsAll(chord.essential) || !chord.pitchClasses.containsAll(pcs)) return false
            // A slash chord needs its bass at the bottom.
            return chord.bass == null || Math.floorMod(held.min(), 12) == chord.bass.pc
        }
    }
}

/** Roman-numeral analysis in a major key (learning-system.md 5.4, 6.3). */
object Roman {
    private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII")
    private val DIATONIC_QUALITY = listOf("", "m", "m", "", "", "m", "dim")
    private val MAJOR = listOf(0, 2, 4, 5, 7, 9, 11)

    data class Analysis(val numeral: String, val diatonic: Boolean, val secondaryDominant: Boolean = false, val borrowed: Boolean = false)

    fun analyze(chord: Chord, fifths: Int): Analysis {
        val tonicPc = Keys.tonic(fifths).pc
        val rel = Math.floorMod(chord.root.pc - tonicPc, 12)
        val degree = MAJOR.indexOf(rel)
        val suffix = chord.quality.let { q ->
            when {
                q == "dim" -> "°"
                q == "m7b5" -> "ø7"
                q == "dim7" -> "°7"
                q.startsWith("m") && q != "maj7" && q != "maj9" -> q.removePrefix("m")
                else -> q
            }
        }
        val minorLike = chord.isMinorThird
        val inversion = chord.bass?.let { b -> "/" + (MAJOR.indexOf(Math.floorMod(b.pc - tonicPc, 12)).takeIf { it >= 0 }?.let { "${it + 1}" } ?: b.display()) } ?: ""
        if (degree >= 0) {
            val base = NUMERALS[degree].let { if (minorLike || chord.quality == "dim" || chord.quality == "m7b5" || chord.quality == "dim7") it.lowercase() else it }
            val diatonic = chord.pitchClasses.all { Keys.inKey(it, fifths) }
            // A major or dominant chord on a degree that is minor in the key: secondary dominant of the chord a 5th below.
            val dominantType = !minorLike && 4 in chord.intervals && (chord.quality == "" || chord.quality == "7" || chord.quality == "7b9" || chord.quality == "9")
            if (!diatonic && dominantType && degree != 4) {
                val target = MAJOR.indexOf(Math.floorMod(rel - 7, 12))
                if (target >= 0) {
                    return Analysis("V${if (chord.quality.isEmpty()) "" else chord.quality}/${NUMERALS[target].let { if (DIATONIC_QUALITY[target] == "") it else it.lowercase() }}$inversion", false, secondaryDominant = true)
                }
            }
            return Analysis(base + suffix + inversion, diatonic)
        }
        // Chromatic root: written as a flat degree (bVII, bVI, bIII ...) - typical borrowed chords.
        val flatDegree = MAJOR.indexOf(Math.floorMod(rel + 1, 12))
        val name = "♭" + NUMERALS[flatDegree].let { if (minorLike) it.lowercase() else it } + suffix + inversion
        return Analysis(name, false, borrowed = true)
    }

    /** Diatonic chord of a degree (0-6) as a symbol in a key, triad or seventh. */
    fun chordOf(degree: Int, fifths: Int, seventh: Boolean = false): Chord {
        val scale = Keys.scale(fifths)
        val q = if (!seventh) DIATONIC_QUALITY[degree] else listOf("maj7", "m7", "m7", "maj7", "7", "m7", "m7b5")[degree]
        return Chord(scale[degree], q)
    }
}

/** Key finding from played notes (Krumhansl-Kessler profiles), for the "find the key" step of 9.5. */
object KeyFinder {
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    data class Candidate(val tonicPc: Int, val minor: Boolean, val score: Double)

    /** Ranks the 24 keys for a pitch-class weight vector (counts or durations). Best first. */
    fun rank(weights: DoubleArray): List<Candidate> {
        require(weights.size == 12)
        fun corr(profile: DoubleArray, shift: Int): Double {
            val x = DoubleArray(12) { weights[(it + shift) % 12] }
            val mx = x.average()
            val my = profile.average()
            var num = 0.0; var dx = 0.0; var dy = 0.0
            for (i in 0 until 12) { num += (x[i] - mx) * (profile[i] - my); dx += (x[i] - mx) * (x[i] - mx); dy += (profile[i] - my) * (profile[i] - my) }
            return if (dx == 0.0 || dy == 0.0) 0.0 else num / kotlin.math.sqrt(dx * dy)
        }
        return (0 until 12).flatMap { t -> listOf(Candidate(t, false, corr(MAJOR, t)), Candidate(t, true, corr(MINOR, t))) }.sortedByDescending { it.score }
    }

    fun rankNotes(midis: List<Int>): List<Candidate> {
        val w = DoubleArray(12)
        midis.forEach { w[Math.floorMod(it, 12)] += 1.0 }
        return rank(w)
    }

    /** The relative major's fifths for a candidate (minor keys share their relative major's signature). */
    fun fifthsOf(c: Candidate): Int {
        val majorPc = if (c.minor) Math.floorMod(c.tonicPc + 3, 12) else c.tonicPc
        return (-6..6).minBy { f -> if (Keys.tonic(f).pc == majorPc) 0 else 1 }
    }
}
