package com.id.mimikopi.core.editor

import com.id.mimikopi.core.interchange.MidiFile
import com.id.mimikopi.core.score.Hand

/** One key played while recording: the MIDI note and when it was pressed and released (ms, the input's clock). */
data class PlayedNote(val midi: Int, val onMs: Long, val offMs: Long)

/**
 * Entering a bought piano-solo sheet by playing it (learning-system.md 2.12, step 3): the player plays a few bars
 * of one hand along with the metronome, and the take is snapped to a grid on the [Timeline], where wrong notes can
 * then be fixed. Bought sheets stay on the PC as private scores; nothing here is shared.
 */
object Recording {
    /** Grid choices, in sixteenths: quarter, eighth, sixteenth. */
    val GRIDS = listOf(4, 2, 1)

    /**
     * Snaps a take to the grid. [startMs] is beat one of the first recorded bar; [bpm] counts quarter notes, as
     * everywhere in the app; [grid] is the smallest note value in sixteenths. Keys pressed together (on the same
     * grid point) make a chord; a note lasts until it is released or the next note starts, at least one grid
     * step. Times come back in sixteenths from the take's first bar.
     */
    fun transcribe(notes: List<PlayedNote>, startMs: Long, bpm: Double, grid: Int, keyFifths: Int): List<TimedNote> {
        require(bpm > 0 && grid in GRIDS)
        val msPer16 = 60_000.0 / bpm / 4
        fun snap(ms: Long): Int = Math.round((ms - startMs) / msPer16 / grid).toInt() * grid
        val groups = notes.filter { snap(it.onMs) >= 0 }.groupBy { snap(it.onMs) }.toSortedMap()
        val list = groups.map { (start, g) ->
            val len = maxOf(grid, g.minOf { snap(it.offMs) } - start)
            TimedNote(start, len, g.map { MidiFile.spell(it.midi, keyFifths) }.distinctBy { it.midi }.sortedBy { it.midi })
        }
        return list.mapIndexed { i, n -> list.getOrNull(i + 1)?.let { nx -> if (n.end > nx.start) n.copy(length = nx.start - n.start) else n } ?: n }
    }

    /**
     * Puts a transcribed take into [timeline]: [hand]'s notes in measures [fromMeasure]..[toMeasure] are replaced by
     * [take] (cut at the end of the range), the other hand and the rest of the piece stay as they were.
     */
    fun place(timeline: Timeline, hand: Hand, fromMeasure: Int, toMeasure: Int, take: List<TimedNote>): Timeline {
        require(fromMeasure in 0..toMeasure)
        val from = fromMeasure * timeline.measureLength
        val to = (toMeasure + 1) * timeline.measureLength
        var t = timeline.write(hand, from, to - from, emptyList())
        for (n in take) {
            val start = from + n.start
            if (start >= to) continue
            t = t.write(hand, start, minOf(n.length, to - start), n.pitches)
        }
        return t
    }
}
