package com.id.mimikopi.core.score

import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Staff
import kotlin.math.abs
import kotlin.math.sqrt

/** Identifies one written note: an event and the index of the pitch inside it (chords have several). */
data class NoteKey(val eventId: Int, val pitchIndex: Int)

/**
 * Engraving of a [Score] as one long grand-staff system, in staff-space units (1 = distance between two
 * staff lines). x grows to the right, y grows downward; the treble staff's middle line is y = [TREBLE_Y],
 * the bass staff's middle line is y = [BASS_Y]. The UI scales by its staff-space size and scrolls the strip
 * under a fixed cursor, which suits landscape play-along better than wrapped systems.
 *
 * Glyphs are SMuFL code points (Bravura). Scope: what stages 1-4 need — one voice per staff, chords,
 * dots, flags (no beams yet), accidentals with measure carry, key and time signatures, ledger lines.
 */
data class ScoreLayout(
    val width: Double,
    val glyphs: List<Glyph>,
    val lines: List<Line>,
    val measures: List<MeasureBox>,
    /** (beat, x) pairs in increasing order; the cursor interpolates between them. */
    val anchors: List<Pair<Double, Double>>,
) {
    sealed interface Mark { val eventId: Int? }

    data class Glyph(
        val x: Double,
        val y: Double,
        val codepoint: Char,
        override val eventId: Int? = null,
        /** Set on noteheads only, for per-note colouring. */
        val note: NoteKey? = null,
    ) : Mark

    data class Line(
        val x1: Double, val y1: Double, val x2: Double, val y2: Double,
        val thickness: Double,
        override val eventId: Int? = null,
    ) : Mark

    data class MeasureBox(val index: Int, val x0: Double, val x1: Double, val startBeat: Double, val endBeat: Double)

    /** Cursor x for a beat position (clamped to the piece). */
    fun xAt(beat: Double): Double {
        if (anchors.isEmpty()) return 0.0
        if (beat <= anchors.first().first) return anchors.first().second
        if (beat >= anchors.last().first) return anchors.last().second
        var lo = 0
        var hi = anchors.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (anchors[mid].first <= beat) lo = mid else hi = mid
        }
        val (b0, x0) = anchors[lo]
        val (b1, x1) = anchors[hi]
        return if (b1 == b0) x0 else x0 + (x1 - x0) * (beat - b0) / (b1 - b0)
    }

    /** Measure under an x position, or null outside the music. */
    fun measureAtX(x: Double): Int? = measures.firstOrNull { x >= it.x0 && x < it.x1 }?.index

    fun noteheads(): List<Glyph> = glyphs.filter { it.note != null }

    companion object {
        const val TREBLE_Y = 0.0
        const val BASS_Y = 10.0
        const val TOP_Y = TREBLE_Y - 2.0
        const val BOTTOM_Y = BASS_Y + 2.0

        const val NOTEHEAD_WIDTH = 1.18
        const val WHOLE_NOTEHEAD_WIDTH = 1.688
        const val STEM_LENGTH = 3.5
        const val STEM_THICKNESS = 0.12
        const val STAFF_LINE_THICKNESS = 0.13
        const val LEDGER_THICKNESS = 0.16
        const val BARLINE_THICKNESS = 0.16
        const val THICK_BARLINE = 0.5

        // SMuFL code points
        const val G_CLEF = ''
        const val F_CLEF = ''
        const val TIME_DIGIT_0 = ''
        const val HEAD_WHOLE = ''
        const val HEAD_HALF = ''
        const val HEAD_BLACK = ''
        const val DOT = ''
        const val FLAG_8_UP = ''
        const val FLAG_8_DOWN = ''
        const val FLAT = ''
        const val NATURAL = ''
        const val SHARP = ''
        const val DOUBLE_SHARP = ''
        const val DOUBLE_FLAT = ''
        const val REST_WHOLE = ''
        const val REST_HALF = ''
        const val REST_QUARTER = ''
        const val REST_EIGHTH = ''

        fun staffY(hand: Hand): Double = if (hand == Hand.RIGHT) TREBLE_Y else BASS_Y

        /** y of a staff position (0 = middle line, +1 = one step up). */
        fun yOf(hand: Hand, position: Int): Double = staffY(hand) - position / 2.0

        fun accidentalGlyph(alter: Int): Char = when (alter) {
            -2 -> DOUBLE_FLAT; -1 -> FLAT; 0 -> NATURAL; 1 -> SHARP; 2 -> DOUBLE_SHARP
            else -> error("accidental out of range: $alter")
        }

        fun of(score: Score): ScoreLayout = Engraver(score).run()
    }
}

private class Engraver(private val score: Score) {
    private val glyphs = mutableListOf<ScoreLayout.Glyph>()
    private val lines = mutableListOf<ScoreLayout.Line>()
    private val measures = mutableListOf<ScoreLayout.MeasureBox>()
    private val anchors = mutableListOf<Pair<Double, Double>>()

    fun run(): ScoreLayout {
        var x = prefix()
        val byMeasure = score.events.groupBy { it.measure }
        for (m in 0 until score.measures) {
            val start = score.measureStart(m)
            val end = start + score.measureBeats
            val events = byMeasure[m].orEmpty()
            val x0 = x
            x += 1.2
            // Accidental state per hand: (letter, octave) -> alteration, starting from the key signature.
            val carry = mutableMapOf<Hand, MutableMap<Pair<Letter, Int>, Int>>()
            val onsets = events.map { it.onset }.distinct().sorted()
            for ((i, onset) in onsets.withIndex()) {
                val group = events.filter { it.onset == onset }
                val needsAccidental = group.any { e -> e.pitches.any { p -> shownAccidental(carry, e.hand, p, dryRun = true) != null } }
                if (needsAccidental) x += 1.1
                anchors += onset to x
                for (e in group) engraveEvent(e, x, carry, wholeMeasureRest = e.isRest && e.duration == score.measureBeats)
                val next = onsets.getOrNull(i + 1) ?: end
                x += gapFor(next - onset)
                if (group.any { it.duration % 1.0 != 0.0 && it.duration > 1.0 }) x += 0.4 // room for dots
            }
            if (onsets.isEmpty()) x += 4.0
            measures += ScoreLayout.MeasureBox(m, x0, x, start, end)
            anchors += end to x
            val last = m == score.measures - 1
            if (last) {
                lines += ScoreLayout.Line(x - 0.75, ScoreLayout.TOP_Y, x - 0.75, ScoreLayout.BOTTOM_Y, ScoreLayout.BARLINE_THICKNESS)
                lines += ScoreLayout.Line(x - 0.25, ScoreLayout.TOP_Y, x - 0.25, ScoreLayout.BOTTOM_Y, ScoreLayout.THICK_BARLINE)
            } else {
                lines += ScoreLayout.Line(x, ScoreLayout.TOP_Y, x, ScoreLayout.BOTTOM_Y, ScoreLayout.BARLINE_THICKNESS)
            }
        }
        val width = x
        for (hand in Hand.entries) {
            for (k in -2..2) {
                val y = ScoreLayout.staffY(hand) + k
                lines += ScoreLayout.Line(0.0, y, width, y, ScoreLayout.STAFF_LINE_THICKNESS)
            }
        }
        lines += ScoreLayout.Line(0.0, ScoreLayout.TOP_Y, 0.0, ScoreLayout.BOTTOM_Y, ScoreLayout.BARLINE_THICKNESS)
        return ScoreLayout(width, glyphs, lines, measures, anchors.distinctBy { it.first })
    }

    /** Clefs, key signature and time signature; returns the x where the first measure starts. */
    private fun prefix(): Double {
        glyphs += ScoreLayout.Glyph(0.8, ScoreLayout.yOf(Hand.RIGHT, -2), ScoreLayout.G_CLEF)
        glyphs += ScoreLayout.Glyph(0.8, ScoreLayout.yOf(Hand.LEFT, 2), ScoreLayout.F_CLEF)
        var x = 4.2
        val keyGlyph = if (score.keyFifths > 0) ScoreLayout.SHARP else ScoreLayout.FLAT
        val count = abs(score.keyFifths)
        for (hand in Hand.entries) {
            KeySignature.positions(score.keyFifths, score.clefFor(hand)).forEachIndexed { i, pos ->
                glyphs += ScoreLayout.Glyph(x + i * 1.0, ScoreLayout.yOf(hand, pos), keyGlyph)
            }
        }
        x += count * 1.0 + if (count > 0) 0.6 else 0.0
        for (hand in Hand.entries) {
            digits(score.beatsPerMeasure, x, ScoreLayout.yOf(hand, 2))
            digits(score.beatUnit, x, ScoreLayout.yOf(hand, -2))
        }
        val digitsWide = maxOf(score.beatsPerMeasure.toString().length, score.beatUnit.toString().length)
        return x + digitsWide * 1.8 + 0.6
    }

    private fun digits(n: Int, x: Double, y: Double) {
        n.toString().forEachIndexed { i, c ->
            glyphs += ScoreLayout.Glyph(x + i * 1.8, y, ScoreLayout.TIME_DIGIT_0 + (c - '0'))
        }
    }

    /** Horizontal room after an onset, growing with the time until the next onset (sub-linear, like engravers do). */
    private fun gapFor(beats: Double): Double = 1.6 + 2.4 * sqrt(beats)

    private fun shownAccidental(
        carry: MutableMap<Hand, MutableMap<Pair<Letter, Int>, Int>>,
        hand: Hand,
        p: SpelledPitch,
        dryRun: Boolean,
    ): Int? {
        val state = carry.getOrPut(hand) { mutableMapOf() }
        val key = p.letter to p.octave
        val current = state[key] ?: KeySignature.accidentalFor(p.letter, score.keyFifths)
        if (current == p.accidental) return null
        if (!dryRun) state[key] = p.accidental
        return p.accidental
    }

    private fun engraveEvent(
        e: ScoreEvent,
        x: Double,
        carry: MutableMap<Hand, MutableMap<Pair<Letter, Int>, Int>>,
        wholeMeasureRest: Boolean,
    ) {
        val hand = e.hand
        if (e.isRest) {
            engraveRest(e, x, wholeMeasureRest)
            return
        }
        val clef = score.clefFor(hand)
        val positions = e.pitches.map { Staff.position(it, clef) }
        val stemUp = (positions.max() + positions.min()) < 0
        val head = when {
            e.duration >= 4.0 -> ScoreLayout.HEAD_WHOLE
            e.duration >= 2.0 -> ScoreLayout.HEAD_HALF
            else -> ScoreLayout.HEAD_BLACK
        }
        val headWidth = if (head == ScoreLayout.HEAD_WHOLE) ScoreLayout.WHOLE_NOTEHEAD_WIDTH else ScoreLayout.NOTEHEAD_WIDTH
        // Seconds inside a chord: alternate noteheads to the other side of the stem.
        val offsets = DoubleArray(positions.size)
        val order = positions.indices.sortedBy { positions[it] }
        var prevShifted = false
        for (k in 1 until order.size) {
            val adjacent = positions[order[k]] - positions[order[k - 1]] == 1
            if (adjacent && !prevShifted) {
                if (stemUp) offsets[order[k]] = ScoreLayout.NOTEHEAD_WIDTH else offsets[order[k - 1]] = -ScoreLayout.NOTEHEAD_WIDTH
                prevShifted = true
            } else {
                prevShifted = false
            }
        }
        val accidentals = e.pitches.map { shownAccidental(carry, hand, it, dryRun = false) }
        var accColumn = 0
        for (k in order.reversed()) {
            val alter = accidentals[k] ?: continue
            val minOffset = offsets.minOrNull() ?: 0.0
            glyphs += ScoreLayout.Glyph(x + minOffset - 1.1 - accColumn * 0.9, ScoreLayout.yOf(hand, positions[k]),
                ScoreLayout.accidentalGlyph(alter), e.id)
            accColumn = (accColumn + 1) % 2
        }
        for (k in positions.indices) {
            val pos = positions[k]
            val hx = x + offsets[k]
            glyphs += ScoreLayout.Glyph(hx, ScoreLayout.yOf(hand, pos), head, e.id, NoteKey(e.id, k))
            for (ledger in Staff.ledgerLines(pos)) {
                val y = ScoreLayout.yOf(hand, ledger)
                lines += ScoreLayout.Line(hx - 0.4, y, hx + headWidth + 0.4, y, ScoreLayout.LEDGER_THICKNESS, e.id)
            }
            if (isDotted(e.duration)) {
                val dotPos = if (Staff.onLine(pos)) pos + 1 else pos
                glyphs += ScoreLayout.Glyph(x + offsets.max() + headWidth + 0.35, ScoreLayout.yOf(hand, dotPos), ScoreLayout.DOT, e.id)
            }
        }
        if (e.duration < 4.0) {
            val anchorPos = if (stemUp) positions.min() else positions.max()
            val farPos = if (stemUp) positions.max() else positions.min()
            val stemX = if (stemUp) x + ScoreLayout.NOTEHEAD_WIDTH - ScoreLayout.STEM_THICKNESS / 2 else x + ScoreLayout.STEM_THICKNESS / 2
            val y1 = ScoreLayout.yOf(hand, anchorPos)
            var y2 = ScoreLayout.yOf(hand, farPos) + if (stemUp) -ScoreLayout.STEM_LENGTH else ScoreLayout.STEM_LENGTH
            // Notes far outside the staff: stems reach the middle line.
            val mid = ScoreLayout.staffY(hand)
            if (stemUp && y2 > mid) y2 = mid
            if (!stemUp && y2 < mid) y2 = mid
            lines += ScoreLayout.Line(stemX, y1, stemX, y2, ScoreLayout.STEM_THICKNESS, e.id)
            if (e.duration < 1.0) {
                glyphs += ScoreLayout.Glyph(stemX - ScoreLayout.STEM_THICKNESS / 2, y2,
                    if (stemUp) ScoreLayout.FLAG_8_UP else ScoreLayout.FLAG_8_DOWN, e.id)
            }
        }
    }

    private fun engraveRest(e: ScoreEvent, x: Double, wholeMeasureRest: Boolean) {
        val hand = e.hand
        val (glyph, pos) = when {
            wholeMeasureRest || e.duration >= 4.0 -> ScoreLayout.REST_WHOLE to 2
            e.duration >= 2.0 -> ScoreLayout.REST_HALF to 0
            e.duration >= 1.0 -> ScoreLayout.REST_QUARTER to 0
            else -> ScoreLayout.REST_EIGHTH to 0
        }
        glyphs += ScoreLayout.Glyph(x, ScoreLayout.yOf(hand, pos), glyph, e.id)
        if (!wholeMeasureRest && isDotted(e.duration)) {
            glyphs += ScoreLayout.Glyph(x + 1.5, ScoreLayout.yOf(hand, 1), ScoreLayout.DOT, e.id)
        }
    }

    private fun isDotted(duration: Double): Boolean = duration == 3.0 || duration == 1.5
}
