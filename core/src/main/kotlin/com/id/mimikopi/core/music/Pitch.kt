package com.id.mimikopi.core.music

/** The seven natural note letters, in ascending order within an octave. */
enum class Letter(val semitonesFromC: Int) {
    C(0), D(2), E(4), F(5), G(7), A(9), B(11);

    /** Solfège syllable (fixed do) in katakana, as the user learnt it: ド レ ミ ファ ソ ラ シ. */
    val solfege: String
        get() = when (this) {
            C -> "ド"; D -> "レ"; E -> "ミ"; F -> "ファ"; G -> "ソ"; A -> "ラ"; B -> "シ"
        }
}

/** How note names are shown to the user (learning-system.md 2.3 item 3). */
enum class NoteNameStyle { LETTER, SOLFEGE, BOTH }

/**
 * A spelled pitch: letter + accidental + octave (scientific pitch notation, Middle C = C4 = MIDI 60).
 * Spelling matters for notation (C♯4 and D♭4 sit on different staff positions), so this is the
 * primary type; [midi] is derived.
 */
data class SpelledPitch(val letter: Letter, val accidental: Int, val octave: Int) {
    init {
        require(accidental in -2..2) { "accidental out of range: $accidental" }
    }

    val midi: Int get() = (octave + 1) * 12 + letter.semitonesFromC + accidental

    /** Diatonic position counted in letter steps from C0. Staff placement and step/skip use this. */
    val diatonicIndex: Int get() = octave * 7 + letter.ordinal

    fun name(style: NoteNameStyle, withOctave: Boolean = false): String {
        val acc = accidentalSymbol(accidental)
        val letterName = letter.name + acc + if (withOctave) octave.toString() else ""
        val solfege = letter.solfege + acc
        return when (style) {
            NoteNameStyle.LETTER -> letterName
            NoteNameStyle.SOLFEGE -> solfege
            NoteNameStyle.BOTH -> "$letterName / $solfege"
        }
    }

    override fun toString(): String = letter.name + ASCII_ACCIDENTALS.getValue(accidental) + octave

    companion object {
        private val ASCII_ACCIDENTALS = mapOf(-2 to "bb", -1 to "b", 0 to "", 1 to "#", 2 to "##")
        private val PATTERN = Regex("""^([A-Ga-g])(##|#|bb|b|)(-?\d)$""")

        /** Parses ASCII spellings such as "C4", "F#3", "Bb2", "E##5". */
        fun parse(text: String): SpelledPitch {
            val m = PATTERN.matchEntire(text.trim()) ?: throw IllegalArgumentException("not a pitch: $text")
            val (l, acc, oct) = m.destructured
            val accidental = ASCII_ACCIDENTALS.entries.first { it.value == acc }.key
            return SpelledPitch(Letter.valueOf(l.uppercase()), accidental, oct.toInt())
        }

        /** Natural-or-sharp spelling of a MIDI note number (for input events that carry no spelling). */
        fun fromMidiSharp(midi: Int): SpelledPitch {
            require(midi in 0..127) { "midi out of range: $midi" }
            val pc = midi % 12
            val octave = midi / 12 - 1
            val natural = Letter.entries.lastOrNull { it.semitonesFromC <= pc }!!
            return SpelledPitch(natural, pc - natural.semitonesFromC, octave)
        }

        fun accidentalSymbol(accidental: Int): String = when (accidental) {
            -2 -> "𝄫"; -1 -> "♭"; 0 -> ""; 1 -> "♯"; 2 -> "𝄪"
            else -> error("accidental out of range")
        }
    }
}

/** True for MIDI notes that are black keys on a piano keyboard. */
fun isBlackKey(midi: Int): Boolean = (midi % 12) in setOf(1, 3, 6, 8, 10)
