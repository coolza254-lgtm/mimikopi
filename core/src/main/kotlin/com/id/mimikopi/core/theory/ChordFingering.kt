package com.id.mimikopi.core.theory

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.music.Letter

/** One way to hold a chord: the keys (MIDI, low to high) and the finger on each (1 = thumb ... 5 = little finger). */
data class HandShape(val notes: List<Int>, val fingers: List<Int>, val title: String)

/**
 * How to hold a chord with each hand, for the chord book: close voicings (root position and inversions) near Middle C
 * for the right hand and an octave lower for the left, with the usual beginner fingerings, plus the common two-hand
 * pattern (left hand plays the root, right hand the chord). Chords of up to four different notes; add9 is held close
 * (the 9th as a 2nd).
 */
object ChordFingering {
    /** Qualities the chord book offers (four notes at most, so one hand can hold them). */
    val QUALITIES = listOf("", "m", "dim", "aug", "sus2", "sus4", "6", "m6", "7", "maj7", "m7", "m7b5", "dim7", "7sus4", "mM7", "add9", "madd9")

    /** Roots in their usual spelling. */
    val ROOTS: List<PitchName> = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").map { PitchName.parse(it)!! }

    private val INVERSION_TITLES = listOf(tr("ตำแหน่งพื้นฐาน (รากอยู่ล่าง)", "Root position (root at the bottom)", "基本形（ルートが一番下）"), tr("พลิกครั้งที่ 1", "First inversion", "第1転回形"), tr("พลิกครั้งที่ 2", "Second inversion", "第2転回形"), tr("พลิกครั้งที่ 3", "Third inversion", "第3転回形"))

    /** The chord's notes inside one octave, from the root (add9's 9th becomes a 2nd). */
    fun closeIntervals(chord: Chord): List<Int> = chord.intervals.map { Math.floorMod(it, 12) }.distinct().sorted()

    /** The chord tones spelled from the root (C E♭ G♭ B𝄫 for Cdim7), in [closeIntervals] order. */
    fun spelled(chord: Chord): List<PitchName> = closeIntervals(chord).map { iv ->
        val steps = when (iv) {
            0 -> 0; 1, 2 -> 1; 3, 4 -> 2; 5 -> 3; 6 -> if (chord.quality.startsWith("dim") || chord.quality == "m7b5") 4 else 3
            7 -> 4; 8 -> if (chord.quality.startsWith("aug")) 4 else 5
            9 -> if (chord.quality == "dim7") 6 else 5
            else -> 6
        }
        val letter = Letter.entries[(chord.root.letter.ordinal + steps) % 7]
        var acc = Math.floorMod(chord.root.pc + iv, 12) - letter.semitonesFromC
        while (acc > 6) acc -= 12
        while (acc < -6) acc += 12
        PitchName(letter, acc.coerceIn(-2, 2))
    }

    /** Close voicings with the root in [low, low+11], then each inversion (lowest note moved up an octave). */
    private fun voicings(chord: Chord, low: Int): List<List<Int>> {
        val root = low + Math.floorMod(chord.root.pc - low, 12)
        val base = closeIntervals(chord).map { root + it }
        return base.indices.map { k -> (base.drop(k) + base.take(k).map { it + 12 }) }
    }

    /** Right hand: root position and inversions around Middle C. */
    fun rightHand(chord: Chord): List<HandShape> =
        voicings(chord, 60).mapIndexed { k, notes -> HandShape(notes, rightFingers(notes), INVERSION_TITLES[k]) }

    /** Left hand: the same shapes an octave lower. */
    fun leftHand(chord: Chord): List<HandShape> =
        voicings(chord, 48).mapIndexed { k, notes -> HandShape(notes, leftFingers(notes), INVERSION_TITLES[k]) }

    /** Both hands, the usual first way: left hand the root (little finger), right hand the chord in root position. */
    fun twoHands(chord: Chord): Pair<HandShape, HandShape> {
        val bass = 48 + Math.floorMod(chord.root.pc, 12)
        return HandShape(listOf(bass), listOf(5), tr("มือซ้าย: กดรากคอร์ด", "Left hand: the root", "左手：ルート")) to rightHand(chord).first().copy(title = tr("มือขวา: กดคอร์ด", "Right hand: the chord", "右手：コード"))
    }

    /**
     * Right hand, low to high: thumb on the lowest, little finger on the highest. In three-note shapes the middle
     * note takes 2 when the top gap is wide (a 4th or more, as in first inversion) and 3 otherwise; four-note shapes
     * use 1 2 3 5.
     */
    fun rightFingers(notes: List<Int>): List<Int> = when (notes.size) {
        1 -> listOf(1)
        2 -> listOf(1, 5)
        3 -> listOf(1, if (notes[2] - notes[1] >= 5) 2 else 3, 5)
        else -> listOf(1, 2, 3, 5)
    }

    /**
     * Left hand, low to high: little finger on the lowest, thumb on the highest. In three-note shapes the middle note
     * takes 2 when the bottom gap is wide (a 4th or more, as in second inversion), 4 when it is a step, 3 otherwise;
     * four-note shapes use 5 3 2 1 (5 4 2 1 when the bottom two are a step apart).
     */
    fun leftFingers(notes: List<Int>): List<Int> = when (notes.size) {
        1 -> listOf(5)
        2 -> listOf(5, 1)
        3 -> {
            val gap = notes[1] - notes[0]
            listOf(5, if (gap >= 5) 2 else if (gap <= 2) 4 else 3, 1)
        }
        else -> listOf(5, if (notes[1] - notes[0] <= 2) 4 else 3, 2, 1)
    }
}
