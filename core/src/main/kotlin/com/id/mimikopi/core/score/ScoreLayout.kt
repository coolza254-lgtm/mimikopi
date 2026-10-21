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
 * dots, flags, beams inside a beat, accidentals with measure carry, key and time signatures, ledger
 * lines, finger numbers.
 */
data class ScoreLayout(
    val width: Double,
    val glyphs: List<Glyph>,
    val lines: List<Line>,
    val measures: List<MeasureBox>,
    /** (beat, x) pairs in increasing order; the cursor interpolates between them. */
    val anchors: List<Pair<Double, Double>>,
    /** Text marks (finger numbers), drawn with the UI font; (x, y) is the centre of the text. */
    val labels: List<Label> = emptyList(),
    val ties: List<Tie> = emptyList(),
) {
    data class Label(val x: Double, val y: Double, val text: String, val eventId: Int, val kind: LabelKind = LabelKind.FINGER)

    enum class LabelKind { FINGER, CHORD, MARK }

    /** A tie: an arc from (x1, y) to (x2, y), bulging up when [above]. */
    data class Tie(val x1: Double, val x2: Double, val y: Double, val above: Boolean, val eventId: Int)

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

    /** Beat position under an x position (inverse of [xAt]). */
    fun beatAtX(x: Double): Double {
        if (anchors.isEmpty()) return 0.0
        if (x <= anchors.first().second) return anchors.first().first
        if (x >= anchors.last().second) return anchors.last().first
        val i = anchors.indexOfFirst { it.second > x }
        val (b0, x0) = anchors[i - 1]
        val (b1, x1) = anchors[i]
        return if (x1 == x0) b0 else b0 + (b1 - b0) * (x - x0) / (x1 - x0)
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
        const val FLAG_16_UP = '\uE242'
        const val FLAG_16_DOWN = '\uE243'
        const val BEAM_THICKNESS = 0.5
        const val BEAM_SPACING = 0.75
        const val FLAT = ''
        const val NATURAL = ''
        const val SHARP = ''
        const val DOUBLE_SHARP = ''
        const val DOUBLE_FLAT = ''
        const val REST_WHOLE = ''
        const val REST_HALF = ''
        const val REST_QUARTER = ''
        const val REST_EIGHTH = ''
        const val REST_16TH = '\uE4E7'

        fun staffY(hand: Hand): Double = if (hand == Hand.RIGHT) TREBLE_Y else BASS_Y

        /** y of a staff position (0 = middle line, +1 = one step up). */
        fun yOf(hand: Hand, position: Int): Double = staffY(hand) - position / 2.0

        fun accidentalGlyph(alter: Int): Char = when (alter) {
            -2 -> DOUBLE_FLAT; -1 -> FLAT; 0 -> NATURAL; 1 -> SHARP; 2 -> DOUBLE_SHARP
            else -> error("accidental out of range: $alter")
        }

        fun of(score: Score): ScoreLayout = Engraver(score).run()

        /**
         * One line (system) of [score] spread to [width] staff spaces, as engraved music fills its lines: only the gaps
         * between note columns grow, glyphs keep their size. A line already as wide as [width] is left as it is. Without
         * [finalBar] the last bar ends with a plain bar line (a line that the piece continues after).
         */
        fun justified(score: Score, width: Double, finalBar: Boolean = true): ScoreLayout {
            val natural = Engraver(score, 1.0, finalBar).run()
            val doubled = Engraver(score, 2.0, finalBar).run().width
            if (natural.width >= width || doubled <= natural.width) return natural
            // Width is linear in the spacing factor: only the gaps scale.
            val factor = 1.0 + (width - natural.width) / (doubled - natural.width)
            return Engraver(score, factor, finalBar).run()
        }

        /**
         * Measure ranges that fit systems (lines) of [width] staff spaces, for printing. Each system repeats the clef,
         * key and time signature prefix; a measure wider than a whole line gets a line of its own.
         */
        fun systems(score: Score, width: Double): List<IntRange> {
            val full = of(score)
            val prefix = full.measures.firstOrNull()?.x0 ?: 0.0
            val out = mutableListOf<IntRange>()
            var start = 0
            var used = prefix
            for (m in full.measures) {
                val w = m.x1 - m.x0
                if (m.index > start && used + w > width) {
                    out += start until m.index
                    start = m.index
                    used = prefix
                }
                used += w
            }
            out += start until score.measures
            return out
        }
    }
}

/** [spacing] scales the gaps between note columns (1 = natural spacing); [finalBar] ends the last bar with the final double bar. */
private class Engraver(private val score: Score, private val spacing: Double = 1.0, private val finalBar: Boolean = true) {
    private val glyphs = mutableListOf<ScoreLayout.Glyph>()
    private val lines = mutableListOf<ScoreLayout.Line>()
    private val measures = mutableListOf<ScoreLayout.MeasureBox>()
    private val anchors = mutableListOf<Pair<Double, Double>>()
    private val labels = mutableListOf<ScoreLayout.Label>()

    /** A beamed group's shared stem direction and (flat) beam line. */
    private data class BeamInfo(val stemUp: Boolean, val beamY: Double)
    private val beamInfo = mutableMapOf<Int, BeamInfo>()
    private val stemXs = mutableMapOf<Int, Double>()

    fun run(): ScoreLayout {
        var x = prefix()
        val byMeasure = score.events.groupBy { it.measure }
        for (m in 0 until score.measures) {
            val start = score.measureStart(m)
            val end = start + score.measureBeats
            val events = byMeasure[m].orEmpty()
            val x0 = x
            x += 1.2
            if (m in score.repeatStart) {
                // Forward repeat: thick, thin, dots.
                lines += ScoreLayout.Line(x0 + 0.25, ScoreLayout.TOP_Y, x0 + 0.25, ScoreLayout.BOTTOM_Y, ScoreLayout.THICK_BARLINE)
                lines += ScoreLayout.Line(x0 + 0.85, ScoreLayout.TOP_Y, x0 + 0.85, ScoreLayout.BOTTOM_Y, ScoreLayout.BARLINE_THICKNESS)
                repeatDots(x0 + 1.15)
                x += 1.6
            }
            // Accidental state per hand: (letter, octave) -> alteration, starting from the key signature.
            val carry = mutableMapOf<Hand, MutableMap<Pair<Letter, Int>, Int>>()
            val onsets = events.map { it.onset }.distinct().sorted()
            val groups = beamGroups(events)
            for (g in groups) beamInfo.putAll(g.associate { it.id to beamFor(g) })
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
            if (onsets.isEmpty()) x += 4.0 * spacing
            for (g in groups) drawBeams(g)
            val repeatEnd = m in score.repeatEnd
            if (repeatEnd) x += 1.6
            measures += ScoreLayout.MeasureBox(m, x0, x, start, end)
            anchors += end to x
            val last = m == score.measures - 1
            if (repeatEnd) {
                // Backward repeat: dots, thin, thick.
                repeatDots(x - 1.6)
                lines += ScoreLayout.Line(x - 0.85, ScoreLayout.TOP_Y, x - 0.85, ScoreLayout.BOTTOM_Y, ScoreLayout.BARLINE_THICKNESS)
                lines += ScoreLayout.Line(x - 0.25, ScoreLayout.TOP_Y, x - 0.25, ScoreLayout.BOTTOM_Y, ScoreLayout.THICK_BARLINE)
            } else if (last && finalBar) {
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
        val finalAnchors = anchors.distinctBy { it.first }
        val partial = ScoreLayout(width, glyphs, lines, measures, finalAnchors, labels)
        for (c in score.chordSymbols) labels += ScoreLayout.Label(partial.xAt(c.beat) + 0.6, ScoreLayout.TOP_Y - 4.8, c.text, -1, ScoreLayout.LabelKind.CHORD)
        for ((m, text) in score.marks) measures.getOrNull(m)?.let { box -> labels += ScoreLayout.Label(box.x0 + 0.4, ScoreLayout.TOP_Y - 7.0, text, -1, ScoreLayout.LabelKind.MARK) }
        return ScoreLayout(width, glyphs, lines, measures, finalAnchors, labels, ties())
    }

    private fun repeatDots(x: Double) {
        for (hand in Hand.entries) for (pos in listOf(1, -1)) glyphs += ScoreLayout.Glyph(x, ScoreLayout.yOf(hand, pos), ScoreLayout.DOT)
    }

    /** Ties from each tied note to the same pitch in the next event of its hand, curving away from the stem. */
    private fun ties(): List<ScoreLayout.Tie> {
        val out = mutableListOf<ScoreLayout.Tie>()
        for (hand in Hand.entries) {
            val evs = score.events.filter { it.hand == hand }.sortedBy { it.onset }
            for ((a, b) in evs.zipWithNext()) {
                if (!a.tieToNext) continue
                val headsA = heads[a.id] ?: continue
                val headsB = heads[b.id] ?: continue
                val up = stemUps[a.id] ?: true
                for ((midi, xa, y) in headsA) {
                    val xb = headsB.firstOrNull { it.first == midi }?.second ?: continue
                    val off = if (up) 0.55 else -0.55
                    out += ScoreLayout.Tie(xa + ScoreLayout.NOTEHEAD_WIDTH * 0.8, xb + ScoreLayout.NOTEHEAD_WIDTH * 0.2, y + off, !up, a.id)
                }
            }
        }
        return out
    }

    private val heads = mutableMapOf<Int, MutableList<Triple<Int, Double, Double>>>()
    private val stemUps = mutableMapOf<Int, Boolean>()

    /** Clefs, key signature and time signature; returns the x where the first measure starts. */
    private fun prefix(): Double {
        for (hand in Hand.entries) {
            // SMuFL: the G clef's origin sits on the G line (position -2), the F clef's on the F line (+2).
            if (score.clefFor(hand) == com.id.mimikopi.core.notation.Clef.TREBLE) glyphs += ScoreLayout.Glyph(0.8, ScoreLayout.yOf(hand, -2), ScoreLayout.G_CLEF)
            else glyphs += ScoreLayout.Glyph(0.8, ScoreLayout.yOf(hand, 2), ScoreLayout.F_CLEF)
        }
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
    private fun gapFor(beats: Double): Double = spacing * (1.6 + 2.4 * sqrt(beats))

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
        val beam = beamInfo[e.id]
        val stemUp = beam?.stemUp ?: ((positions.max() + positions.min()) < 0)
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
            heads.getOrPut(e.id) { mutableListOf() } += Triple(e.pitches[k].midi, hx, ScoreLayout.yOf(hand, pos))
            stemUps[e.id] = stemUp
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
            if (beam != null) y2 = beam.beamY
            lines += ScoreLayout.Line(stemX, y1, stemX, y2, ScoreLayout.STEM_THICKNESS, e.id)
            stemXs[e.id] = stemX
            if (e.duration < 1.0 && beam == null) {
                val glyph = when {
                    e.duration <= 0.25 -> if (stemUp) ScoreLayout.FLAG_16_UP else ScoreLayout.FLAG_16_DOWN
                    else -> if (stemUp) ScoreLayout.FLAG_8_UP else ScoreLayout.FLAG_8_DOWN
                }
                glyphs += ScoreLayout.Glyph(stemX - ScoreLayout.STEM_THICKNESS / 2, y2, glyph, e.id)
            }
        }
        if (e.fingers.isNotEmpty()) {
            val cx = x + ScoreLayout.NOTEHEAD_WIDTH / 2
            if (hand == Hand.RIGHT) {
                val top = minOf(ScoreLayout.TOP_Y, ScoreLayout.yOf(hand, positions.max()), if (stemUp) ScoreLayout.yOf(hand, positions.max()) - ScoreLayout.STEM_LENGTH else ScoreLayout.TOP_Y)
                e.fingers.indices.sortedBy { positions[it] }.forEachIndexed { i, k -> labels += ScoreLayout.Label(cx, top - 1.3 - i * 1.3, e.fingers[k].toString(), e.id) }
            } else {
                val bottom = maxOf(ScoreLayout.BOTTOM_Y, ScoreLayout.yOf(hand, positions.min()), if (!stemUp) ScoreLayout.yOf(hand, positions.min()) + ScoreLayout.STEM_LENGTH else ScoreLayout.BOTTOM_Y)
                e.fingers.indices.sortedByDescending { positions[it] }.forEachIndexed { i, k -> labels += ScoreLayout.Label(cx, bottom + 1.3 + i * 1.3, e.fingers[k].toString(), e.id) }
            }
        }
    }

    private fun engraveRest(e: ScoreEvent, x: Double, wholeMeasureRest: Boolean) {
        val hand = e.hand
        val (glyph, pos) = when {
            wholeMeasureRest || e.duration >= 4.0 -> ScoreLayout.REST_WHOLE to 2
            e.duration >= 2.0 -> ScoreLayout.REST_HALF to 0
            e.duration >= 1.0 -> ScoreLayout.REST_QUARTER to 0
            e.duration >= 0.5 -> ScoreLayout.REST_EIGHTH to 0
            else -> ScoreLayout.REST_16TH to 0
        }
        glyphs += ScoreLayout.Glyph(x, ScoreLayout.yOf(hand, pos), glyph, e.id)
        if (!wholeMeasureRest && isDotted(e.duration)) {
            glyphs += ScoreLayout.Glyph(x + 1.5, ScoreLayout.yOf(hand, 1), ScoreLayout.DOT, e.id)
        }
    }

    private fun isDotted(duration: Double): Boolean = duration == 3.0 || duration == 1.5 || duration == 0.75

    /**
     * Notes shorter than a quarter are beamed with their neighbours inside the same beat (same hand);
     * a rest or a longer note breaks the group. Groups of one keep their flag.
     */
    private fun beamGroups(events: List<ScoreEvent>): List<List<ScoreEvent>> {
        val out = mutableListOf<List<ScoreEvent>>()
        for (hand in Hand.entries) {
            val current = mutableListOf<ScoreEvent>()
            var beat = -1
            fun flush() {
                if (current.size >= 2) out += current.toList()
                current.clear()
            }
            // Compound metres (6/8, 9/8, 12/8) beam by the dotted quarter.
            val group = if (score.beatUnit == 8 && score.beatsPerMeasure % 3 == 0) 1.5 else 1.0
            for (e in events.filter { it.hand == hand }.sortedBy { it.onset }) {
                val b = kotlin.math.floor((e.onset + 1e-9) / group).toInt()
                val beamable = !e.isRest && e.duration < 1.0 && e.onset + e.duration <= (b + 1) * group + 1e-9
                if (!beamable || b != beat) flush()
                beat = b
                if (beamable) current += e
            }
            flush()
        }
        return out
    }

    private fun beamFor(group: List<ScoreEvent>): BeamInfo {
        val clef = score.clefFor(group.first().hand)
        val hand = group.first().hand
        val all = group.flatMap { e -> e.pitches.map { Staff.position(it, clef) } }
        val up = (all.max() + all.min()) < 0
        val y = if (up) {
            group.minOf { e -> ScoreLayout.yOf(hand, e.pitches.maxOf { Staff.position(it, clef) }) } - ScoreLayout.STEM_LENGTH
        } else {
            group.maxOf { e -> ScoreLayout.yOf(hand, e.pitches.minOf { Staff.position(it, clef) }) } + ScoreLayout.STEM_LENGTH
        }
        return BeamInfo(up, y)
    }

    private fun drawBeams(group: List<ScoreEvent>) {
        val info = beamInfo[group.first().id] ?: return
        val xs = group.map { stemXs[it.id] ?: return }
        val toward = if (info.stemUp) 1.0 else -1.0 // from the beam line toward the noteheads
        fun beam(x1: Double, x2: Double, level: Int) {
            val y = info.beamY + toward * (ScoreLayout.BEAM_THICKNESS / 2 + level * ScoreLayout.BEAM_SPACING)
            lines += ScoreLayout.Line(x1 - ScoreLayout.STEM_THICKNESS / 2, y, x2 + ScoreLayout.STEM_THICKNESS / 2, y, ScoreLayout.BEAM_THICKNESS, group.first().id)
        }
        beam(xs.first(), xs.last(), 0)
        val sixteenth = group.map { it.duration <= 0.25 + 1e-9 }
        for (i in group.indices) {
            if (!sixteenth[i]) continue
            val prev = i > 0 && sixteenth[i - 1]
            val next = i < group.size - 1 && sixteenth[i + 1]
            when {
                next -> beam(xs[i], xs[i + 1], 1)
                prev -> Unit // drawn by the previous note
                i == group.size - 1 -> beam(xs[i] - 1.0, xs[i], 1)
                else -> beam(xs[i], xs[i] + 1.0, 1)
            }
        }
    }
}
