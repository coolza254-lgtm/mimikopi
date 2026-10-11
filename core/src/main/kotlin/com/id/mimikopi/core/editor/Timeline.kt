package com.id.mimikopi.core.editor

import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score

/** Everything about a score that is not its notes. */
data class ScoreMeta(
    val id: String,
    val title: String,
    val composer: String = "",
    val source: String = "User score",
    val level: String = "",
    val key: Int = 0,
    val time: List<Int> = listOf(4, 4),
    val tempo: Int = 80,
    val rhClef: String = "treble",
    val lhClef: String = "bass",
)

/** A note or chord on the timeline, in sixteenth notes from the start of the piece. Gaps are rests. */
data class TimedNote(
    val start: Int,
    val length: Int,
    val pitches: List<SpelledPitch>,
    /** Tied to the next note that starts where this one ends. */
    val tie: Boolean = false,
    val fingers: List<Int> = emptyList(),
) {
    val end: Int get() = start + length
}

/** A note or rest as written inside one measure (after splitting at bar lines). */
data class BarredEvent(val start: Int, val length: Int, val pitches: List<SpelledPitch>, val tie: Boolean, val fingers: List<Int>) {
    val isRest: Boolean get() = pitches.isEmpty()
}

/**
 * The editor's model (PROMPT.md section 4): notes on a continuous timeline per hand. Every edit returns a new
 * Timeline; [toPieceFile] re-bars it, splitting notes at bar lines with ties and filling gaps with rests, so a
 * measure can never be over- or under-full. Imports (MusicXML, MIDI) build a Timeline too.
 */
data class Timeline(
    val meta: ScoreMeta,
    val measures: Int,
    val rh: List<TimedNote> = emptyList(),
    val lh: List<TimedNote> = emptyList(),
    /** Chord symbols: (start in 16ths, text). */
    val chords: List<Pair<Int, String>> = emptyList(),
    val repeatStart: Set<Int> = emptySet(),
    val repeatEnd: Set<Int> = emptySet(),
) {
    init {
        require(measures >= 1) { "a score needs at least one measure" }
        require(meta.time.size == 2 && meta.time[1] in listOf(2, 4, 8) && meta.time[0] in 1..16) { "bad time signature" }
        require(meta.time[0] * 16 % meta.time[1] == 0) { "time signature must be a whole number of 16ths" }
    }

    /** Measure length in sixteenths. */
    val measureLength: Int get() = meta.time[0] * 16 / meta.time[1]
    val totalLength: Int get() = measures * measureLength

    fun notes(hand: Hand): List<TimedNote> = if (hand == Hand.RIGHT) rh else lh

    private fun withNotes(hand: Hand, notes: List<TimedNote>): Timeline {
        val sorted = notes.sortedBy { it.start }
        val needed = maxOf(measures, (sorted.maxOfOrNull { it.end } ?: 0).let { (it + measureLength - 1) / measureLength })
        return if (hand == Hand.RIGHT) copy(rh = sorted, measures = needed) else copy(lh = sorted, measures = needed)
    }

    /** The note sounding at [time] in [hand], if any. */
    fun noteAt(hand: Hand, time: Int): TimedNote? = notes(hand).firstOrNull { time >= it.start && time < it.end }

    /**
     * Writes a note (or a rest, with empty [pitches]) of [length] at [start], replacing whatever was there
     * (overwrite entry, as in score editors). Notes cut short lose their tie. Grows the score if needed.
     */
    fun write(hand: Hand, start: Int, length: Int, pitches: List<SpelledPitch>, tie: Boolean = false): Timeline {
        require(start >= 0 && length > 0)
        val end = start + length
        val kept = notes(hand).mapNotNull { n ->
            when {
                n.end <= start || n.start >= end -> n
                n.start < start -> n.copy(length = start - n.start, tie = false)
                else -> null
            }
        }
        val added = if (pitches.isEmpty()) emptyList() else listOf(TimedNote(start, length, pitches.distinctBy { it.midi }.sortedBy { it.midi }, tie))
        return withNotes(hand, kept + added)
    }

    /** Adds a pitch to the note starting at [start] (building a chord). */
    fun addPitch(hand: Hand, start: Int, pitch: SpelledPitch): Timeline {
        val n = notes(hand).firstOrNull { it.start == start } ?: return this
        if (n.pitches.any { it.midi == pitch.midi }) return this
        return withNotes(hand, notes(hand).map { if (it === n) it.copy(pitches = (it.pitches + pitch).sortedBy { p -> p.midi }, fingers = emptyList()) else it })
    }

    /** Removes the note covering [time] (it becomes a rest). */
    fun clear(hand: Hand, time: Int): Timeline = withNotes(hand, notes(hand).filter { !(time >= it.start && time < it.end) })

    fun toggleTie(hand: Hand, time: Int): Timeline {
        val n = noteAt(hand, time) ?: return this
        return withNotes(hand, notes(hand).map { if (it === n) it.copy(tie = !it.tie) else it })
    }

    /** Changes the length of the note covering [time], overwriting what follows (shortening leaves a rest). */
    fun setLength(hand: Hand, time: Int, length: Int): Timeline {
        val n = noteAt(hand, time) ?: return this
        return write(hand, n.start, length, n.pitches, n.tie)
    }

    /** Sets or removes ([text] null or blank) the chord symbol at [start]. */
    fun setChord(start: Int, text: String?): Timeline {
        val rest = chords.filter { it.first != start }
        return copy(chords = if (text.isNullOrBlank()) rest else (rest + (start to text.trim().replace(" ", ""))).sortedBy { it.first })
    }

    fun toggleRepeatStart(measure: Int) = copy(repeatStart = if (measure in repeatStart) repeatStart - measure else repeatStart + measure)
    fun toggleRepeatEnd(measure: Int) = copy(repeatEnd = if (measure in repeatEnd) repeatEnd - measure else repeatEnd + measure)

    /** Inserts an empty measure before [measure]; later material moves right. */
    fun insertMeasure(measure: Int): Timeline {
        val at = measure * measureLength
        fun shift(n: TimedNote) = if (n.start >= at) n.copy(start = n.start + measureLength) else n
        return copy(
            measures = measures + 1,
            rh = rh.map(::shift), lh = lh.map(::shift),
            chords = chords.map { if (it.first >= at) it.first + measureLength to it.second else it },
            repeatStart = repeatStart.map { if (it >= measure) it + 1 else it }.toSet(),
            repeatEnd = repeatEnd.map { if (it >= measure) it + 1 else it }.toSet(),
        )
    }

    /** Deletes [measure]; later material moves left. Notes reaching into it are cut at its start. */
    fun deleteMeasure(measure: Int): Timeline {
        if (measures <= 1) return this
        val from = measure * measureLength
        val to = from + measureLength
        fun fix(list: List<TimedNote>) = list.mapNotNull { n ->
            when {
                n.end <= from -> n
                n.start >= to -> n.copy(start = n.start - measureLength)
                n.start < from -> n.copy(length = from - n.start, tie = false)
                else -> null
            }
        }
        return copy(
            measures = measures - 1, rh = fix(rh), lh = fix(lh),
            chords = chords.filter { it.first < from || it.first >= to }.map { if (it.first >= to) it.first - measureLength to it.second else it },
            repeatStart = repeatStart.filter { it != measure }.map { if (it > measure) it - 1 else it }.toSet(),
            repeatEnd = repeatEnd.filter { it != measure }.map { if (it > measure) it - 1 else it }.toSet(),
        )
    }

    fun withMeta(m: ScoreMeta): Timeline {
        // A new time signature keeps the notes where they are in time; the measure count follows.
        val t = copy(meta = m)
        val needed = maxOf(1, ((maxOf(rh.maxOfOrNull { it.end } ?: 0, lh.maxOfOrNull { it.end } ?: 0)) + t.measureLength - 1) / t.measureLength)
        val oldLength = totalLength
        return t.copy(measures = maxOf(needed, (oldLength + t.measureLength - 1) / t.measureLength))
    }

    /** The hand's notes and rests as written, measure by measure (notes split at bar lines with ties). */
    fun barred(hand: Hand): List<BarredEvent> {
        val out = mutableListOf<BarredEvent>()
        val notes = notes(hand)
        for (m in 0 until measures) {
            val ms = m * measureLength
            val me = ms + measureLength
            var t = ms
            for (n in notes.filter { it.end > ms && it.start < me }) {
                val s = maxOf(n.start, ms)
                if (s > t) rests(t, s - t, out)
                val e = minOf(n.end, me)
                val tieOut = if (n.end > me) true else n.tie
                pieces(s, e - s, n.pitches, tieOut, if (s == n.start) n.fingers else emptyList(), out)
                t = e
            }
            if (t < me) rests(t, me - t, out)
        }
        return out
    }

    /**
     * Rests: a whole empty measure is one rest; otherwise the largest plain (undotted) rests that start on a multiple
     * of their own length, so rests line up with the beats (E4/q~ E4/s r/e. r/h -> r/s r/e r/h).
     */
    private fun rests(start: Int, length: Int, out: MutableList<BarredEvent>) {
        val rel = start % measureLength
        if (rel == 0 && length == measureLength && measureLength in TOKENS) {
            out += BarredEvent(start, length, emptyList(), false, emptyList())
            return
        }
        var s = start
        var left = length
        while (left > 0) {
            val r = s % measureLength
            val l = listOf(16, 8, 4, 2, 1).first { it <= left && r % it == 0 }
            out += BarredEvent(s, l, emptyList(), false, emptyList())
            s += l
            left -= l
        }
    }

    private fun pieces(start: Int, length: Int, pitches: List<SpelledPitch>, tieAtEnd: Boolean, fingers: List<Int>, out: MutableList<BarredEvent>) {
        val parts = decompose(length)
        var s = start
        parts.forEachIndexed { i, l ->
            val last = i == parts.size - 1
            out += BarredEvent(s, l, pitches, if (last) tieAtEnd else true, if (i == 0) fingers else emptyList())
            s += l
        }
    }

    /** Re-bars the timeline into the piece format. */
    fun toPieceFile(): PieceFile {
        fun bars(hand: Hand): List<String> {
            val evs = barred(hand)
            return (0 until measures).map { m ->
                evs.filter { it.start / measureLength == m }.joinToString(" ") { e ->
                    val dur = TOKENS.getValue(e.length)
                    val head = if (e.isRest) "r" else e.pitches.joinToString("+")
                    val fingers = if (e.fingers.isNotEmpty() && e.fingers.size == e.pitches.size) "@" + e.fingers.joinToString("+") else ""
                    "$head/$dur${if (e.tie && !e.isRest) "~" else ""}$fingers"
                }
            }
        }
        val chordBars = (0 until measures).map { m ->
            chords.filter { it.first / measureLength == m }.joinToString(" ") { (s, text) -> "$text@${fmt((s - m * measureLength) / 4.0)}" }
        }
        val repeatBars = (0 until measures).map { m ->
            when {
                m in repeatStart && m in repeatEnd -> "both"
                m in repeatStart -> "start"
                m in repeatEnd -> "end"
                else -> ""
            }
        }
        return PieceFile(
            meta.id, meta.title, meta.composer, meta.source, meta.level, meta.key, meta.time, meta.tempo,
            bars(Hand.RIGHT), bars(Hand.LEFT),
            chords = if (chordBars.all { it.isEmpty() }) emptyList() else chordBars.dropLastWhile { it.isEmpty() },
            repeats = if (repeatBars.all { it.isEmpty() }) emptyList() else repeatBars.dropLastWhile { it.isEmpty() },
            rhClef = meta.rhClef, lhClef = meta.lhClef,
        )
    }

    fun toScore(): Score = Score.from(toPieceFile())

    companion object {
        /** Written lengths in sixteenths and their tokens. */
        val TOKENS = mapOf(16 to "w", 12 to "h.", 8 to "h", 6 to "q.", 4 to "q", 3 to "e.", 2 to "e", 1 to "s")
        private val LENGTHS = TOKENS.keys.sortedDescending()

        /** Splits a length into written lengths (largest first), e.g. 5 -> 4 + 1. */
        fun decompose(length: Int): List<Int> {
            val out = mutableListOf<Int>()
            var left = length
            while (left > 0) {
                val l = LENGTHS.first { it <= left }
                out += l
                left -= l
            }
            return out
        }

        private fun fmt(beat: Double) = if (beat == kotlin.math.floor(beat)) beat.toInt().toString() else beat.toString()

        fun empty(meta: ScoreMeta, measures: Int = 4) = Timeline(meta, measures)

        /** Reads a piece back into a timeline (ties kept as tie flags). */
        fun fromPiece(file: PieceFile): Timeline {
            val score = Score.from(file)
            fun notes(hand: Hand) = score.events.filter { it.hand == hand && !it.isRest }.map {
                TimedNote((it.onset * 4).toInt(), (it.duration * 4).toInt(), it.pitches, it.tieToNext, it.fingers)
            }
            val meta = ScoreMeta(file.id, file.title, file.composer, file.source, file.level, file.key, file.time, file.tempo, file.rhClef, file.lhClef)
            return Timeline(meta, score.measures, notes(Hand.RIGHT), notes(Hand.LEFT),
                score.chordSymbols.map { (it.beat * 4).toInt() to it.text }, score.repeatStart, score.repeatEnd)
        }
    }
}
