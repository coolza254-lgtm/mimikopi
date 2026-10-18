package com.id.mimikopi.core.exam

import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.PitchName

/** What a scale requirement asks for. Minor scales are harmonic (one form up and down). */
enum class ScaleKind(val title: String) {
    MAJOR("สเกลเมเจอร์"),
    MINOR("สเกลไมเนอร์ฮาร์โมนิก"),
    ARP_MAJOR("อาร์เพจจิโอเมเจอร์"),
    ARP_MINOR("อาร์เพจจิโอไมเนอร์"),
    /** C major, hands moving apart from an octave apart and back. */
    CONTRARY("สเกลสวนทาง"),
    CHROMATIC("สเกลโครมาติก"),
}

/**
 * One scale or arpeggio of a grade: kind, starting note ("C", "Bb", "A"), octaves (1 or 2), and hands together or
 * separately (right hand first, then left).
 */
data class ScaleItem(val kind: ScaleKind, val tonic: String, val octaves: Int, val together: Boolean) {
    val id: String get() = "${kind.name.lowercase()}-$tonic-$octaves-${if (together) "ht" else "hs"}"

    fun title(): String {
        val name = PitchName.parse(tonic)!!.display()
        val hands = if (kind == ScaleKind.CONTRARY || together) "สองมือพร้อมกัน" else "ทีละมือ"
        return "${kind.title} $name · $hands · $octaves อ็อกเทฟ"
    }

    companion object {
        fun parse(id: String): ScaleItem? {
            val p = id.split('-')
            if (p.size != 4) return null
            val kind = ScaleKind.entries.firstOrNull { it.name.lowercase() == p[0] } ?: return null
            val octaves = p[2].toIntOrNull()?.takeIf { it in 1..2 } ?: return null
            if (PitchName.parse(p[1]) == null) return null
            return ScaleItem(kind, p[1], octaves, p[3] == "ht")
        }
    }
}

/**
 * Scales and arpeggios as scores with standard fingering (the finger numbers are shown on the notes). Right hand from
 * the tonic in octave 4; the left hand an octave lower for one octave and two octaves lower for two, so neither hand
 * runs far into ledger lines. Up and back down, eighth notes (quarters when [quarters]), the last note filling its bar.
 */
object ExamScales {
    /** Relative major (key signature) of the harmonic minors used. */
    private val MINOR_KEYS = mapOf("A" to 0, "E" to 1, "B" to 2, "D" to -1, "G" to -2, "C" to -3, "F" to -4)

    /** Key signature (fifths) the item is written in. */
    fun keyOf(item: ScaleItem): Int = when (item.kind) {
        ScaleKind.MAJOR, ScaleKind.ARP_MAJOR -> Keys.fifthsOf(PitchName.parse(item.tonic)!!) ?: 0
        ScaleKind.MINOR, ScaleKind.ARP_MINOR -> MINOR_KEYS.getValue(item.tonic)
        ScaleKind.CONTRARY, ScaleKind.CHROMATIC -> 0
    }

    /** The seven notes of the scale from the tonic (harmonic minor raises the 7th). */
    private fun degrees(item: ScaleItem): List<PitchName> = when (item.kind) {
        ScaleKind.MAJOR, ScaleKind.ARP_MAJOR, ScaleKind.CONTRARY -> Keys.scale(keyOf(item))
        ScaleKind.MINOR, ScaleKind.ARP_MINOR -> {
            val major = Keys.scale(MINOR_KEYS.getValue(item.tonic))
            val natural = (0 until 7).map { major[(it + 5) % 7] }
            natural.mapIndexed { i, p -> if (i == 6) p.copy(accidental = p.accidental + 1) else p }
        }
        ScaleKind.CHROMATIC -> error("chromatic has no degrees")
    }

    /** Ascending pitches from [start] through the degree pattern [steps] (indices into the scale, may pass 7). */
    private fun climb(scale: List<PitchName>, start: SpelledPitch, steps: List<Int>): List<SpelledPitch> {
        val base = start.diatonicIndex
        return steps.map { d ->
            val name = scale[d % 7]
            val index = base + (name.letter.ordinal - start.letter.ordinal).let { if (it < 0) it + 7 else it } + (d / 7) * 7
            SpelledPitch(name.letter, name.accidental, index / 7)
        }
    }

    private fun startPitch(tonic: PitchName, octave: Int) = SpelledPitch(tonic.letter, tonic.accidental, octave)

    /** Ascending notes of one hand from its start (both directions are built from this). */
    fun ascending(item: ScaleItem, right: Boolean): List<SpelledPitch> {
        val tonic = PitchName.parse(item.tonic)!!
        val octave = if (right) 4 else if (item.octaves == 2) 2 else 3
        return when (item.kind) {
            ScaleKind.MAJOR, ScaleKind.MINOR, ScaleKind.CONTRARY ->
                climb(degrees(item), startPitch(tonic, octave), (0..7 * item.octaves).toList())
            ScaleKind.ARP_MAJOR, ScaleKind.ARP_MINOR ->
                climb(degrees(item), startPitch(tonic, octave), (0 until item.octaves).flatMap { o -> listOf(0, 2, 4).map { it + 7 * o } } + 7 * item.octaves)
            ScaleKind.CHROMATIC -> {
                val first = startPitch(tonic, octave).midi
                (0..12 * item.octaves).map { SpelledPitch.fromMidiSharp(first + it) }
            }
        }
    }

    /** Descending chromatic notes are spelled with flats. */
    private fun flat(p: SpelledPitch): SpelledPitch =
        if (p.accidental == 1) SpelledPitch(Letter.entries[p.letter.ordinal + 1], -1, p.octave) else p

    /**
     * Fingers for the ascending notes (descending uses the same finger on each note). Major and minor: the usual
     * groups of three and four with the thumb under; arpeggios 1-2-3-5 / 5-4-2-1 (5-3-2-1 with a black-key third);
     * chromatic 3 on black keys, thumb on white keys, 2 on F and C (right) or E and B (left).
     */
    fun fingers(item: ScaleItem, right: Boolean): List<Int> {
        val n = ascending(item, right).size
        return when (item.kind) {
            ScaleKind.MAJOR, ScaleKind.MINOR, ScaleKind.CONTRARY -> {
                val (start, cycle, top) = scaleFingering(item, right)
                (0 until n - 1).map { i -> if (i == 0 && start != null) start else cycle[i % 7] } + top
            }
            ScaleKind.ARP_MAJOR, ScaleKind.ARP_MINOR -> {
                val notes = ascending(item, right)
                // Right: 1 2 3 (1 2 3) 5. Left: 5 4 2 (1 4 2) 1, with 3 for 4 when the third is a black key over a white root.
                if (right) List(item.octaves) { listOf(1, 2, 3) }.flatten() + 5
                else {
                    val reach = if (notes[1].accidental != 0 && notes[0].accidental == 0) 3 else 4
                    listOf(5, reach, 2) + List(item.octaves - 1) { listOf(1, reach, 2) }.flatten() + 1
                }
            }
            ScaleKind.CHROMATIC -> ascending(item, right).mapIndexed { i, p ->
                when {
                    p.accidental != 0 -> 3
                    i == 0 -> 1
                    right && (p.letter == Letter.F || p.letter == Letter.C) -> 2
                    !right && (p.letter == Letter.E || p.letter == Letter.B) -> 2
                    else -> 1
                }
            }
        }
    }

    /** (first finger when it differs from the cycle, 7-note cycle, finger on the top note). */
    private fun scaleFingering(item: ScaleItem, right: Boolean): Triple<Int?, List<Int>, Int> {
        val tonic = item.tonic
        val major = item.kind != ScaleKind.MINOR
        return if (right) when {
            tonic == "F" -> Triple(null, listOf(1, 2, 3, 4, 1, 2, 3), 4)
            major && tonic == "Bb" -> Triple(2, listOf(4, 1, 2, 3, 1, 2, 3), 4)
            major && tonic == "Eb" -> Triple(null, listOf(3, 1, 2, 3, 4, 1, 2), 3)
            major && tonic == "Ab" -> Triple(null, listOf(3, 4, 1, 2, 3, 1, 2), 3)
            else -> Triple(null, listOf(1, 2, 3, 1, 2, 3, 4), 5)
        } else when {
            major && tonic in setOf("Bb", "Eb", "Ab") -> Triple(null, listOf(3, 2, 1, 4, 3, 2, 1), 3)
            else -> Triple(5, listOf(1, 4, 3, 2, 1, 3, 2), 1)
        }
    }

    /** Supported items: the keys whose fingering above is the standard one. */
    fun supported(item: ScaleItem): Boolean = when (item.kind) {
        ScaleKind.MAJOR -> item.tonic in setOf("C", "G", "D", "A", "E", "F", "Bb", "Eb", "Ab")
        ScaleKind.MINOR -> item.tonic in setOf("A", "E", "D", "G", "C")
        ScaleKind.ARP_MAJOR -> item.tonic in setOf("C", "G", "D", "A", "E", "F")
        ScaleKind.ARP_MINOR -> item.tonic in setOf("A", "E", "D", "G", "C")
        ScaleKind.CONTRARY -> item.tonic == "C" && item.octaves == 1
        ScaleKind.CHROMATIC -> item.tonic in setOf("C", "D") && !item.together
    }

    // ---------------- scores ----------------

    /** One hand's run up and down as (pitch, finger, sixteenths) with the last note filling its bar. */
    private fun run(item: ScaleItem, right: Boolean, quarters: Boolean): List<Triple<SpelledPitch, Int, Int>> {
        val up = ascending(item, right)
        val f = fingers(item, right)
        val down = up.dropLast(1).reversed().let { if (item.kind == ScaleKind.CHROMATIC) it.map(::flat) else it }
        val downF = f.dropLast(1).reversed()
        val notes = up.zip(f) + down.zip(downF)
        val step = if (quarters) 4 else 2
        val body = notes.dropLast(1).map { (p, fi) -> Triple(p, fi, step) }
        val used = body.sumOf { it.third } % 16
        val last = notes.last()
        return body + Triple(last.first, last.second, if (used == 0) 16 else 16 - used)
    }

    /** Contrary motion: the left hand mirrors the right, starting an octave below. */
    private fun contraryLeft(item: ScaleItem, quarters: Boolean): List<Triple<SpelledPitch, Int, Int>> {
        val right = run(item, true, quarters)
        val scale = degrees(item)
        val start = SpelledPitch(Letter.C, 0, 3)
        // Down from C3 through the scale and back.
        val downSteps = (0..7).map { d -> climb(scale, SpelledPitch(Letter.C, 0, 2), listOf(7 - d)).single() }
        val path = downSteps + downSteps.dropLast(1).reversed()
        require(path.first() == start)
        return right.mapIndexed { i, (_, fi, len) -> Triple(path[i], fi, len) }
    }

    private const val REST_BAR = "r/w"

    /** Sixteenths -> bar strings for one hand (notes never cross a barline: the runs are built to fit). */
    private fun bars(notes: List<Triple<SpelledPitch, Int, Int>>): List<String> {
        val out = mutableListOf<String>()
        val bar = mutableListOf<String>()
        var used = 0
        for ((p, f, len) in notes) {
            bar += "$p/${TOKENS.getValue(len)}@$f"
            used += len
            if (used == 16) { out += bar.joinToString(" "); bar.clear(); used = 0 }
        }
        require(used == 0) { "run does not fill its last bar" }
        return out
    }

    private val TOKENS = mapOf(2 to "e", 4 to "q", 8 to "h", 12 to "h.", 16 to "w")

    /** Both hands' bars for one item (hands separately: right hand, then left hand). */
    fun itemBars(item: ScaleItem, quarters: Boolean): Pair<List<String>, List<String>> {
        val r = bars(run(item, true, quarters))
        return when {
            item.kind == ScaleKind.CONTRARY -> r to bars(contraryLeft(item, quarters))
            item.together -> r to bars(run(item, false, quarters))
            else -> {
                val l = bars(run(item, false, quarters))
                (r + List(l.size) { REST_BAR }) to (List(r.size) { REST_BAR } + l)
            }
        }
    }

    /** A score of several items in a row with a bar of rest between them, marked with each item's name. */
    fun piece(id: String, title: String, items: List<ScaleItem>, bpm: Int, quarters: Boolean, level: String): PieceFile {
        val rh = mutableListOf<String>()
        val lh = mutableListOf<String>()
        val marks = mutableListOf<String>()
        items.forEachIndexed { i, item ->
            if (i > 0) { rh += REST_BAR; lh += REST_BAR; marks += "" }
            val (r, l) = itemBars(item, quarters)
            marks += listOf(item.title()) + List(r.size - 1) { "" }
            rh += r
            lh += l
        }
        val key = items.map { keyOf(it) }.distinct().singleOrNull() ?: 0
        return PieceFile(id, title, "สร้างโดยแอพ", "Generated by the app (scales with standard fingering)", level, key, listOf(4, 4), bpm, rh, lh,
            marks = marks.dropLastWhile { it.isEmpty() })
    }
}
