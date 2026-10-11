package com.id.mimikopi.core.exercise

import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.theory.Chord
import com.id.mimikopi.core.theory.ChordQualities
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.StageGenerators
import kotlin.random.Random

/** A chord to play: the chord, how it is asked ("Am" or "vi ในคีย์ C"), and its key. */
data class ChordItem(val chord: Chord, val label: String, val fifths: Int)

/** E_CHORD_BUILD and E_CHORD_CHANGE material (learning-system.md 5.1). Deterministic per seed. */
object ChordDrills {
    /**
     * E_CHORD_BUILD: [Drill.items] chords, by name (roots from the scales of [Drill.keys], qualities from
     * [Drill.qualities]) or by roman numeral in a key ([Drill.degrees]); never the same chord twice in a row.
     */
    fun build(drill: Drill, seed: Long): List<ChordItem> {
        val rnd = Random(seed)
        val keys = drill.keys.ifEmpty { listOf(0) }
        val out = mutableListOf<ChordItem>()
        var guard = 0
        while (out.size < drill.items && guard++ < 1000) {
            val fifths = keys[rnd.nextInt(keys.size)]
            val item = if (drill.degrees.isNotEmpty()) {
                val d = drill.degrees[rnd.nextInt(drill.degrees.size)]
                val c = StageGenerators.degreeChord(d, fifths)
                ChordItem(c, "$d ในคีย์ ${Keys.name(fifths)}", fifths)
            } else {
                val root = Keys.scale(fifths)[rnd.nextInt(7)]
                val q = drill.qualities.ifEmpty { listOf("", "m") }.let { it[rnd.nextInt(it.size)] }
                val c = Chord(root, q)
                ChordItem(c, c.display(), fifths)
            }
            if (out.lastOrNull()?.chord != item.chord) out += item
        }
        return out
    }

    /** E_CHORD_CHANGE: the progression's chords in the first key (looped by the runner for [Drill.seconds] seconds). */
    fun changes(drill: Drill): List<ChordItem> {
        val fifths = drill.keys.firstOrNull() ?: 0
        val degrees = progressionDegrees(drill.progression)
        return degrees.map { d -> StageGenerators.degreeChord(d, fifths).let { ChordItem(it, it.display(), fifths) } }
    }

    /** A named progression of 8.1 or roman numerals separated by spaces ("I V vi IV"). */
    fun progressionDegrees(text: String): List<String> =
        StageGenerators.PROGRESSIONS.firstOrNull { it.name == text }?.degrees ?: text.split(Regex("""[\s\-–]+""")).filter { it.isNotBlank() }

    fun qualityName(q: String) = ChordQualities.NAMES[q] ?: q
}
