package com.id.mimikopi.core.ear

import com.id.mimikopi.core.exercise.RhythmCells
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.score.PieceFile
import kotlin.random.Random

/**
 * Ear-training levels (learning-system.md 11.1). [unlockStage] = the stage whose lessons open the level; levels are
 * practised mixed, not finished one by one.
 */
enum class EarLevel(val unlockStage: Int) {
    E1(1), // higher / lower, melodic direction
    E2(1), // intervals up to a 5th, ascending
    E3(2), // major / minor triads
    E4(2), // intervals up to an octave, up / down / together
    E5(3), // melodic dictation, steps then skips
    E6(2), // rhythm echo, 1-2 bars
    E7(5), // 4-chord progressions by roman numeral
    E8(5), // sevenths and bass lines
    E9(6), // mixed: key, progression and melody of a short phrase
}

enum class Compare { HIGHER, LOWER, SAME }
enum class Dir { UP, DOWN, SAME }
enum class IntervalPlay { UP, DOWN, TOGETHER }
enum class TriadQuality(val intervals: List<Int>) { MAJOR(listOf(0, 4, 7)), MINOR(listOf(0, 3, 7)) }
enum class SeventhQuality(val intervals: List<Int>) { MAJ7(listOf(0, 4, 7, 11)), MIN7(listOf(0, 3, 7, 10)), DOM7(listOf(0, 4, 7, 10)) }

/** Diatonic triads of a major key by roman numeral (vii° is left out: rare in the target music). */
enum class Degree(val step: Int, val minor: Boolean) {
    I(0, false), ii(1, true), iii(2, true), IV(3, false), V(4, false), vi(5, true);
}

/** One sound to play: [atMs] from the start of the question, MIDI note, length, velocity. */
data class Sound(val atMs: Long, val midi: Int, val durMs: Long, val velocity: Int = 80)

/** Major keys used for tonal questions: tonic MIDI note in octave 3/4. */
enum class EarKey(val tonic: Int, val label: String) { C(60, "C"), G(55, "G"), F(53, "F"), D(62, "D") }

private val MAJOR_SCALE = listOf(0, 2, 4, 5, 7, 9, 11)

/** MIDI of scale degree [degree] (0 = tonic, may exceed 6 / go below 0) in a major key. */
fun scaleNote(tonic: Int, degree: Int): Int {
    val octave = Math.floorDiv(degree, 7)
    val d = Math.floorMod(degree, 7)
    return tonic + octave * 12 + MAJOR_SCALE[d]
}

/** A triad on [degree] in close position near the middle of the keyboard, plus its root an octave or two lower. */
fun chordNotes(key: EarKey, degree: Degree, bassStep: Int = 0): Pair<List<Int>, Int> {
    // Root position, close voicing, root between E3 and D#4 so every chord sits around Middle C.
    var shift = 0
    while (scaleNote(key.tonic, degree.step) + shift > 63) shift -= 12
    while (scaleNote(key.tonic, degree.step) + shift < 52) shift += 12
    val voiced = listOf(0, 2, 4).map { scaleNote(key.tonic, degree.step + it) + shift }
    val bassNote = scaleNote(key.tonic, degree.step + bassStep)
    var bass = bassNote
    while (bass >= 52) bass -= 12
    while (bass < 36) bass += 12
    return voiced to bass
}

/** A question; [itemIds] feed spaced repetition and the per-type statistics (11.4). */
sealed interface EarQuestion {
    val level: EarLevel
    val itemIds: List<String>
    /** What the app plays (and replays). */
    fun sounds(): List<Sound>

    data class Higher(val first: Int, val second: Int) : EarQuestion {
        override val level get() = EarLevel.E1
        override val itemIds get() = listOf("ear:cmp")
        val answer: Compare get() = when { second > first -> Compare.HIGHER; second < first -> Compare.LOWER; else -> Compare.SAME }
        override fun sounds() = listOf(Sound(0, first, 700), Sound(900, second, 900))
    }

    /** Melodic direction of 3-4 notes: one answer per neighbouring pair. */
    data class Direction(val notes: List<Int>) : EarQuestion {
        override val level get() = EarLevel.E1
        override val itemIds get() = listOf("ear:dir")
        val answers: List<Dir> get() = notes.zipWithNext { a, b -> if (b > a) Dir.UP else if (b < a) Dir.DOWN else Dir.SAME }
        override fun sounds() = notes.mapIndexed { i, n -> Sound(i * 650L, n, 600) }
    }

    data class Interval(override val level: EarLevel, val root: Int, val semitones: Int, val play: IntervalPlay) : EarQuestion {
        override val itemIds get() = listOf("ear:int:$semitones")
        val target: Int get() = if (play == IntervalPlay.DOWN) root - semitones else root + semitones
        override fun sounds() = when (play) {
            IntervalPlay.TOGETHER -> listOf(Sound(0, root, 1400), Sound(0, target, 1400))
            else -> listOf(Sound(0, root, 800), Sound(900, target, 1000))
        }
    }

    data class Triad(val root: Int, val quality: TriadQuality) : EarQuestion {
        override val level get() = EarLevel.E3
        override val itemIds get() = listOf("ear:triad:${quality.name.lowercase()}")
        val notes: List<Int> get() = quality.intervals.map { root + it }
        /** Played together, then broken upwards, then together again. */
        override fun sounds() = notes.map { Sound(0, it, 1100) } + notes.mapIndexed { i, n -> Sound(1300 + i * 450L, n, 420) } +
            notes.map { Sound(2800, it, 1300) }
    }

    /** Melodic dictation: the first note is given; play the rest back. */
    data class Melody(override val level: EarLevel, val key: EarKey, val notes: List<Int>, val skips: Boolean) : EarQuestion {
        override val itemIds get() = listOf(if (skips) "ear:mel:skip" else "ear:mel:step")
        override fun sounds(): List<Sound> {
            // Tonic chord first so the key is heard, then the melody.
            val (chord, _) = chordNotes(key, Degree.I)
            return chord.map { Sound(0, it, 900, 60) } + notes.mapIndexed { i, n -> Sound(1300 + i * 600L, n, 560) }
        }
    }

    /** Rhythm echo: listen to [bars] bars, then tap them back with the click. */
    data class Rhythm(val piece: PieceFile, val bars: Int) : EarQuestion {
        override val level get() = EarLevel.E6
        override val itemIds get() = listOf("ear:rhy:$bars")
        override fun sounds() = emptyList<Sound>() // played against the metronome by the app
    }

    /** Four chords in a key; the first is named, the others are answered by roman numeral. */
    data class Progression(override val level: EarLevel, val key: EarKey, val degrees: List<Degree>, val name: String?) : EarQuestion {
        override val itemIds get() = degrees.drop(1).map { "ear:deg:${it.name}" }.distinct()
        override fun sounds(): List<Sound> = degrees.flatMapIndexed { i, d ->
            val (chord, bass) = chordNotes(key, d)
            val t = i * 1400L
            chord.map { Sound(t, it, 1300, 70) } + Sound(t, bass, 1300, 78)
        }
    }

    data class Seventh(val root: Int, val quality: SeventhQuality) : EarQuestion {
        override val level get() = EarLevel.E8
        override val itemIds get() = listOf("ear:7th:${quality.name.lowercase()}")
        val notes: List<Int> get() = quality.intervals.map { root + it }
        override fun sounds() = notes.map { Sound(0, it, 1300) } + notes.mapIndexed { i, n -> Sound(1500 + i * 420L, n, 400) }
    }

    /** Bass line under four chords (some with the 3rd or 5th in the bass): answer the bass's scale degree (1-7). */
    data class BassLine(val key: EarKey, val chords: List<Pair<Degree, Int>>) : EarQuestion {
        override val level get() = EarLevel.E8
        override val itemIds get() = listOf("ear:bass")
        /** Scale degree (1-7) of each bass note. */
        val bassDegrees: List<Int> get() = chords.map { (d, b) -> Math.floorMod(d.step + b, 7) + 1 }
        override fun sounds(): List<Sound> = chords.flatMapIndexed { i, (d, b) ->
            val (chord, bass) = chordNotes(key, d, b)
            val t = i * 1500L
            chord.map { Sound(t, it, 1400, 55) } + Sound(t, bass, 1400, 90)
        }
    }

    /** E9: a two-bar phrase (melody over four chords): find the key note, the chords, then the melody. */
    data class Mixed(val key: EarKey, val degrees: List<Degree>, val melody: List<Int>) : EarQuestion {
        override val level get() = EarLevel.E9
        override val itemIds get() = listOf("ear:mix:key", "ear:mix:prog", "ear:mix:mel")
        override fun sounds(): List<Sound> {
            val out = mutableListOf<Sound>()
            degrees.forEachIndexed { i, d ->
                val (chord, bass) = chordNotes(key, d)
                out += chord.map { Sound(i * 1200L, it - 12, 1150, 50) }
                out += Sound(i * 1200L, bass, 1150, 70)
            }
            val step = 4 * 1200L / melody.size
            melody.forEachIndexed { i, n -> out += Sound(i * step, n, step - 60, 90) }
            return out
        }
    }
}

/** Common pop progressions (learning-system.md 8.1) that use only the degrees taught here. */
object Progressions {
    val NAMED: List<Pair<String, List<Degree>>> = listOf(
        "Axis (I–V–vi–IV)" to listOf(Degree.I, Degree.V, Degree.vi, Degree.IV),
        "Axis เริ่มที่ vi (vi–IV–I–V)" to listOf(Degree.vi, Degree.IV, Degree.I, Degree.V),
        "วงจร 50s (I–vi–IV–V)" to listOf(Degree.I, Degree.vi, Degree.IV, Degree.V),
        "โกมุโร (vi–IV–V–I)" to listOf(Degree.vi, Degree.IV, Degree.V, Degree.I),
        "Canon ครึ่งแรก (I–V–vi–iii)" to listOf(Degree.I, Degree.V, Degree.vi, Degree.iii),
    )
}

/**
 * Builds questions. [weights] (item id -> weight, default 1) tilt the choice toward items that need work (11.4).
 * Deterministic per seed.
 */
class EarGenerator(private val seed: Long, private val weights: Map<String, Double> = emptyMap()) {
    private val rnd = Random(seed)

    private fun <T> pickWeighted(options: List<T>, id: (T) -> String): T {
        val w = options.map { weights[id(it)] ?: 1.0 }
        var r = rnd.nextDouble() * w.sum()
        for ((i, o) in options.withIndex()) { r -= w[i]; if (r <= 0) return o }
        return options.last()
    }

    private fun key(): EarKey = EarKey.entries[rnd.nextInt(EarKey.entries.size)]

    fun next(level: EarLevel): EarQuestion = when (level) {
        EarLevel.E1 -> if (rnd.nextInt(3) < 2) {
            val a = rnd.nextInt(52, 77)
            val diff = if (rnd.nextInt(12) == 0) 0 else rnd.nextInt(1, 13) * if (rnd.nextBoolean()) 1 else -1
            EarQuestion.Higher(a, (a + diff).coerceIn(40, 88))
        } else {
            val k = key()
            var d = rnd.nextInt(0, 5)
            val notes = mutableListOf(scaleNote(k.tonic, d))
            repeat(rnd.nextInt(2, 4)) {
                d = (d + listOf(-2, -1, 0, 1, 2)[rnd.nextInt(5)]).coerceIn(-2, 7)
                notes += scaleNote(k.tonic, d)
            }
            EarQuestion.Direction(notes)
        }
        EarLevel.E2 -> {
            val s = pickWeighted((1..7).toList()) { "ear:int:$it" }
            EarQuestion.Interval(level, rnd.nextInt(48, 68), s, IntervalPlay.UP)
        }
        EarLevel.E4 -> {
            val s = pickWeighted((1..12).toList()) { "ear:int:$it" }
            val play = IntervalPlay.entries[rnd.nextInt(3)]
            val root = if (play == IntervalPlay.DOWN) rnd.nextInt(60, 76) else rnd.nextInt(48, 64)
            EarQuestion.Interval(level, root, s, play)
        }
        EarLevel.E3 -> EarQuestion.Triad(rnd.nextInt(48, 62), pickWeighted(TriadQuality.entries) { "ear:triad:${it.name.lowercase()}" })
        EarLevel.E5 -> {
            val skips = (weights["ear:mel:step"] ?: 1.0) < 1.0 && rnd.nextBoolean() // steps are going well: add skips
            val k = key()
            val length = rnd.nextInt(3, 5)
            var d = listOf(0, 2, 4)[rnd.nextInt(3)]
            val notes = mutableListOf(scaleNote(k.tonic, d))
            while (notes.size < length) {
                val moves = if (skips) listOf(-2, -1, 1, 2) else listOf(-1, 1)
                val nd = d + moves[rnd.nextInt(moves.size)]
                if (nd in -1..7) { d = nd; notes += scaleNote(k.tonic, d) }
            }
            EarQuestion.Melody(level, k, notes, skips)
        }
        EarLevel.E6 -> {
            val bars = if ((weights["ear:rhy:1"] ?: 1.0) < 1.0 && rnd.nextBoolean()) 2 else 1
            val cells = listOf("q", "h", "ee", "qr", "q.e")
            val bar = {
                val out = mutableListOf<String>()
                var left = 4
                while (left > 0) {
                    val fits = cells.filter { RhythmCells.beats(it) <= left }
                    val c = fits[rnd.nextInt(fits.size)]
                    out += c; left -= RhythmCells.beats(c)
                }
                out.flatMap { RhythmCells.CELLS.getValue(it) }.joinToString(" ") { d -> if (d < 0) "r/${RhythmCells.token(-d)}" else "C5/${RhythmCells.token(d)}" }
            }
            val rh = List(bars) { bar() }
            EarQuestion.Rhythm(PieceFile("ear-rhythm-$seed", "จังหวะ", "สร้างโดยแอพ", "Generated by the app", "L1", 0, listOf(4, 4), 72,
                rh, List(bars) { "r/w" }), bars)
        }
        EarLevel.E7 -> {
            val k = key()
            val extended = (weights["ear:deg:IV"] ?: 1.0) < 1.0 && (weights["ear:deg:V"] ?: 1.0) < 1.0
            if (rnd.nextInt(10) < 6) {
                val named = Progressions.NAMED.filter { (_, ds) -> extended || Degree.iii !in ds }
                val (name, degrees) = named[rnd.nextInt(named.size)]
                EarQuestion.Progression(level, k, degrees, name)
            } else {
                val pool = if (extended) Degree.entries else listOf(Degree.I, Degree.IV, Degree.V, Degree.vi)
                val degrees = mutableListOf(Degree.I)
                while (degrees.size < 4) {
                    val d = pickWeighted(pool.filter { it != degrees.last() }) { "ear:deg:${it.name}" }
                    degrees += d
                }
                EarQuestion.Progression(level, k, degrees, null)
            }
        }
        EarLevel.E8 -> if (rnd.nextBoolean()) {
            EarQuestion.Seventh(rnd.nextInt(48, 60), pickWeighted(SeventhQuality.entries) { "ear:7th:${it.name.lowercase()}" })
        } else {
            val k = key()
            val (_, degrees) = Progressions.NAMED[rnd.nextInt(Progressions.NAMED.size)]
            // Inversions for a smooth bass: some chords get their 3rd (2 steps) or 5th (4 steps) in the bass.
            EarQuestion.BassLine(k, degrees.mapIndexed { i, d -> d to if (i == 0) 0 else listOf(0, 0, 2, 4)[rnd.nextInt(4)] })
        }
        EarLevel.E9 -> {
            val k = key()
            val (_, degrees) = Progressions.NAMED[rnd.nextInt(Progressions.NAMED.size)]
            // Melody: one or two chord tones per chord, in the octave above Middle C.
            val melody = degrees.flatMap { d ->
                val tones = listOf(d.step, d.step + 2, d.step + 4).map { scaleNote(k.tonic, it) }.map { m -> var x = m; while (x < 60) x += 12; while (x > 79) x -= 12; x }
                List(rnd.nextInt(1, 3)) { tones[rnd.nextInt(3)] }
            }
            EarQuestion.Mixed(k, degrees, melody)
        }
    }

    /** A mixed set (11.1: several levels each week): [count] questions over [levels], weighted toward weak levels. */
    fun mixed(levels: List<EarLevel>, count: Int, levelWeights: Map<EarLevel, Double> = emptyMap()): List<EarQuestion> {
        require(levels.isNotEmpty())
        return List(count) {
            val w = levels.map { levelWeights[it] ?: 1.0 }
            var r = rnd.nextDouble() * w.sum()
            var chosen = levels.last()
            for ((i, l) in levels.withIndex()) { r -= w[i]; if (r <= 0) { chosen = l; break } }
            next(chosen)
        }
    }
}

/** Answer checks and names. */
object EarAnswers {
    /** Interval names in Thai-friendly form, by semitones. */
    val INTERVAL_NAMES = mapOf(
        1 to "2 ไมเนอร์ (m2)", 2 to "2 เมเจอร์ (M2)", 3 to "3 ไมเนอร์ (m3)", 4 to "3 เมเจอร์ (M3)", 5 to "4 เพอร์เฟกต์ (P4)",
        6 to "ไตรโทน (TT)", 7 to "5 เพอร์เฟกต์ (P5)", 8 to "6 ไมเนอร์ (m6)", 9 to "6 เมเจอร์ (M6)", 10 to "7 ไมเนอร์ (m7)",
        11 to "7 เมเจอร์ (M7)", 12 to "อ็อกเทฟ (P8)",
    )

    /** Melody played back: every note in order, exact pitch; the first written note is given and not counted. */
    fun melodyMatches(expected: List<Int>, played: List<Int>): Boolean = played == expected.drop(1)

    /** The key note may be played in any octave. */
    fun tonicMatches(key: EarKey, midi: Int): Boolean = Math.floorMod(midi - key.tonic, 12) == 0

    /** Bass notes played back: compared by pitch class (any octave). */
    fun bassMatches(q: EarQuestion.BassLine, played: List<Int>): Boolean {
        val expected = q.chords.map { (d, b) -> Math.floorMod(scaleNote(q.key.tonic, d.step + b), 12) }
        return played.map { Math.floorMod(it, 12) } == expected
    }
}

/** Per-type accuracy (11.4) and the weights that steer the generator toward weak items. */
object EarStats {
    data class ItemStat(val itemId: String, val correct: Int, val total: Int, val medianMs: Long?) {
        val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
    }

    /** Weight per item: 1 when unknown, up to 3 for weak items, 0.5 for well-known ones (>= 90 % over 5+ answers). */
    fun weights(stats: List<ItemStat>): Map<String, Double> = stats.associate { s ->
        s.itemId to when {
            s.total < 3 -> 1.0
            s.accuracy >= 0.9 && s.total >= 5 -> 0.5
            else -> 1.0 + 2.0 * (1.0 - s.accuracy)
        }
    }

    /** Level weight = mean item weight of the level's answered items (1 if none yet). */
    fun levelWeights(stats: List<ItemStat>, levelOf: (String) -> EarLevel?): Map<EarLevel, Double> {
        val w = weights(stats)
        return w.entries.groupBy({ levelOf(it.key) }, { it.value }).filterKeys { it != null }.mapKeys { it.key!! }.mapValues { it.value.average() }
    }

    /** The levels an item id belongs to (intervals belong to E2 and E4). */
    fun levelsOf(itemId: String): List<EarLevel> = when {
        itemId == "ear:cmp" || itemId == "ear:dir" -> listOf(EarLevel.E1)
        itemId.startsWith("ear:int:") -> if ((itemId.removePrefix("ear:int:").toIntOrNull() ?: 0) <= 7) listOf(EarLevel.E2, EarLevel.E4) else listOf(EarLevel.E4)
        itemId.startsWith("ear:triad:") -> listOf(EarLevel.E3)
        itemId.startsWith("ear:mel:") -> listOf(EarLevel.E5)
        itemId.startsWith("ear:rhy:") -> listOf(EarLevel.E6)
        itemId.startsWith("ear:deg:") -> listOf(EarLevel.E7)
        itemId.startsWith("ear:7th:") || itemId == "ear:bass" -> listOf(EarLevel.E8)
        itemId.startsWith("ear:mix:") -> listOf(EarLevel.E9)
        else -> emptyList()
    }

    /** Levels open at a stage (the highest stage with a lesson passed or reached). */
    fun unlocked(stage: Int): List<EarLevel> = EarLevel.entries.filter { it.unlockStage <= stage }

    /** Fixed benchmark set (12.1 item 5): the same 20 questions from E1-E4 every time. */
    fun benchmarkSet(): List<EarQuestion> = EarGenerator(20261012L).mixed(listOf(EarLevel.E1, EarLevel.E2, EarLevel.E3, EarLevel.E4), 20)
}

/** Name for a MIDI note with the key's spelling where it matters (flat keys use flats). */
fun earNoteName(midi: Int, key: EarKey? = null): SpelledPitch {
    val sharp = SpelledPitch.fromMidiSharp(midi)
    if (key != EarKey.F || sharp.accidental == 0) return sharp
    val up = Letter.entries[(sharp.letter.ordinal + 1) % 7]
    val octave = if (sharp.letter == Letter.B) sharp.octave + 1 else sharp.octave
    return SpelledPitch(up, -1, octave)
}
