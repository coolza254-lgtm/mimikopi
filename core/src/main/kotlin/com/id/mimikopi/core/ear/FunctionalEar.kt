package com.id.mimikopi.core.ear

import com.id.mimikopi.core.i18n.tr
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Major or natural minor; the cadence of the minor uses the raised leading tone (V). */
@Serializable
enum class EarScale { MAJOR, MINOR }

/** When the key-setting chords are played: before every question (beginners) or only before the first. */
@Serializable
enum class CadenceMode { ALWAYS, FIRST }

/**
 * A level of functional ear training (M19): the app sets the key with a short cadence, plays one note, and the user
 * names its place in the key (do, re, mi ...), by playing it on the keyboard or with the buttons.
 *
 * [notes] are the notes that can be asked, in semitones from the tonic of the middle octave (0 = do, 12 = the do
 * above, -1 = the ti below). [tonic] is a pitch class (0 = C), or null for a different key every run. The fields
 * after [cadence] only rebuild a custom level in its editor.
 */
@Serializable
data class ToneLevel(
    val id: String,
    val title: String,
    val tonic: Int? = 0,
    val scale: EarScale = EarScale.MAJOR,
    val notes: List<Int>,
    val questions: Int = 20,
    val cadence: CadenceMode = CadenceMode.ALWAYS,
    val octaves: Int = 1,
    val chromatic: Boolean = false,
    val degrees: List<Int> = emptyList(),
) {
    /** The answer buttons: the distinct places (semitones 0..11 from do) of the notes asked, in order. */
    val answers: List<Int> get() = notes.map { Math.floorMod(it, 12) }.distinct().sorted()
}

/** One finished run of a level. */
@Serializable
data class ToneRun(
    val day: String,
    val asked: Int,
    val firstTry: Int,
    /** When it ended (epoch ms; older runs have none): orders and tells apart runs of one day across devices. */
    val at: Long? = null,
) {
    val accuracy: Double get() = if (asked == 0) 0.0 else firstTry.toDouble() / asked
}

/** Recent runs of one level (the last [FunctionalEar.KEEP_RUNS]). */
@Serializable
data class ToneStats(val runs: List<ToneRun> = emptyList()) {
    val last: ToneRun? get() = runs.lastOrNull()
    fun plus(run: ToneRun): ToneStats = ToneStats((runs + run).takeLast(FunctionalEar.KEEP_RUNS))
}

object FunctionalEar {
    const val KEEP_RUNS = 20
    val MAJOR = listOf(0, 2, 4, 5, 7, 9, 11)
    val MINOR = listOf(0, 2, 3, 5, 7, 8, 10)

    fun scaleTones(scale: EarScale) = if (scale == EarScale.MAJOR) MAJOR else MINOR

    /** Movable-do names (katakana) of the 12 places from do; flats for the notes between. */
    private val SOLFEGE = listOf("ド", "レ♭", "レ", "ミ♭", "ミ", "ファ", "ファ♯", "ソ", "ラ♭", "ラ", "シ♭", "シ")
    private val DEGREE = listOf("1", "♭2", "2", "♭3", "3", "4", "♯4", "5", "♭6", "6", "♭7", "7")
    private val LETTERS_SHARP = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val LETTERS_FLAT = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
    /** Keys usually written with flats. */
    private val FLAT_KEYS = setOf(5, 10, 3, 8, 1)

    /** "レ" for place 2. */
    fun solfege(place: Int): String = SOLFEGE[Math.floorMod(place, 12)]

    /** "2", "♭3" ... */
    fun degree(place: Int): String = DEGREE[Math.floorMod(place, 12)]

    /** Letter of a pitch class, spelled for the key of [tonic]. */
    fun letter(pitchClass: Int, tonic: Int): String =
        (if (Math.floorMod(tonic, 12) in FLAT_KEYS) LETTERS_FLAT else LETTERS_SHARP)[Math.floorMod(pitchClass, 12)]

    /** "C เมเจอร์", "A ไมเนอร์". */
    fun keyName(tonic: Int, scale: EarScale): String = letter(tonic, tonic) + if (scale == EarScale.MAJOR) tr(" เมเจอร์", " major", " メジャー") else tr(" ไมเนอร์", " minor", " マイナー")

    /** MIDI note of the tonic of the middle octave: kept between F#3 and F4, so every key sits in the same register. */
    fun base(tonic: Int): Int = Math.floorMod(tonic, 12).let { pc -> if (pc <= 5) 60 + pc else 48 + pc }

    /** Notes from [lo] to [hi] (semitones from do) whose place is in [places]. */
    fun range(lo: Int, hi: Int, places: Collection<Int>): List<Int> = (lo..hi).filter { Math.floorMod(it, 12) in places }

    /** The span of a custom level: 1 octave = do to do, 2 = an octave below too, 3 = one more above. */
    fun span(octaves: Int): IntRange = when (octaves) {
        1 -> 0..12
        2 -> -12..12
        else -> -12..24
    }

    /** A level built from editor choices. */
    fun custom(id: String, tonic: Int?, scale: EarScale, octaves: Int, chromatic: Boolean, degrees: List<Int>, questions: Int, cadence: CadenceMode): ToneLevel {
        val allowed = if (chromatic) degrees else degrees.filter { it in scaleTones(scale) }
        val span = span(octaves)
        val keyLabel = tonic?.let { keyName(it, scale) } ?: (tr("ทุกคีย์ ", "All keys ", "全キー ") + if (scale == EarScale.MAJOR) tr("เมเจอร์", "major", "メジャー") else tr("ไมเนอร์", "minor", "マイナー"))
        return ToneLevel(
            id, keyLabel, tonic, scale, range(span.first, span.last, allowed), questions.coerceIn(5, 100), cadence,
            octaves = octaves, chromatic = chromatic, degrees = allowed.sorted(),
        )
    }

    private fun intro(n: Int, title: String, notes: List<Int>) = ToneLevel("intro$n", title, 0, EarScale.MAJOR, notes, questions = 10)

    /** Getting started: two notes, then three, four; the top of the octave; the whole octave; another octave; two. */
    val intro: List<ToneLevel> = listOf(
        intro(1, "ド レ", listOf(0, 2)),
        intro(2, "ド レ ミ", listOf(0, 2, 4)),
        intro(3, "ド レ ミ ファ", listOf(0, 2, 4, 5)),
        intro(4, "シ ド", listOf(11, 12)),
        intro(5, "ラ シ ド", listOf(9, 11, 12)),
        intro(6, "ソ ラ シ ド", listOf(7, 9, 11, 12)),
        intro(7, tr("ทั้งอ็อกเทฟ", "Whole octave", "1オクターブ全体"), range(0, 12, MAJOR)),
        intro(8, tr("อีกอ็อกเทฟ (ต่ำลง)", "Another octave (lower)", "もう1オクターブ（低い方）"), range(-12, 0, MAJOR)),
        intro(9, tr("สองอ็อกเทฟรวมกัน", "Two octaves together", "2オクターブ合わせて"), range(-12, 12, MAJOR)),
    )

    /** The main levels: C major in one, two and three octaves; any major key; minor; with the notes in between. */
    val basic: List<ToneLevel> = listOf(
        ToneLevel("c-major-1", tr("C เมเจอร์", "C major", "ハ長調"), 0, EarScale.MAJOR, range(0, 12, MAJOR), octaves = 1),
        ToneLevel("c-major-2", tr("C เมเจอร์", "C major", "ハ長調"), 0, EarScale.MAJOR, range(-12, 12, MAJOR), octaves = 2),
        ToneLevel("c-major-3", tr("C เมเจอร์", "C major", "ハ長調"), 0, EarScale.MAJOR, range(-12, 24, MAJOR), octaves = 3),
        ToneLevel("any-major-1", tr("ทุกคีย์ เมเจอร์", "All keys, major", "全キー メジャー"), null, EarScale.MAJOR, range(0, 12, MAJOR), octaves = 1),
        ToneLevel("any-major-2", tr("ทุกคีย์ เมเจอร์", "All keys, major", "全キー メジャー"), null, EarScale.MAJOR, range(-12, 12, MAJOR), octaves = 2),
        ToneLevel("a-minor-1", tr("A ไมเนอร์", "A minor", "イ短調"), 9, EarScale.MINOR, range(0, 12, MINOR), octaves = 1),
        ToneLevel("any-minor-1", tr("ทุกคีย์ ไมเนอร์", "All keys, minor", "全キー マイナー"), null, EarScale.MINOR, range(0, 12, MINOR), octaves = 1),
        ToneLevel("c-chromatic-1", tr("C เมเจอร์ + โน้ตครึ่งเสียง", "C major + semitones", "ハ長調＋半音"), 0, EarScale.MAJOR, (0..12).toList(), octaves = 1, chromatic = true, degrees = (0..11).toList()),
    )

    /**
     * The key-setting cadence, I-IV-V-I (i-iv-V-i in minor): chords as semitones from do, each a bass note an octave
     * below and a close triad around the middle do.
     */
    fun cadence(scale: EarScale): List<List<Int>> {
        val third = if (scale == EarScale.MAJOR) 4 else 3
        val sixth = if (scale == EarScale.MAJOR) 9 else 8
        return listOf(
            listOf(-12, 0, third, 7),
            listOf(-7, 0, 5, sixth),
            listOf(-5, -1, 2, 7),
            listOf(-12, 0, third, 7),
        )
    }

    /**
     * How a note resolves to do, step by step along the scale: re, mi, fa (and the notes between, up to the tritone)
     * go down, sol, la, ti go up; do stays. This is what makes each place recognisable.
     */
    fun resolution(note: Int, scale: EarScale): List<Int> {
        val place = Math.floorMod(note, 12)
        if (place == 0) return listOf(note)
        val octaveDo = note - place
        val tones = scaleTones(scale)
        val path = mutableListOf(note)
        if (place <= 6) {
            tones.filter { it in 0 until place }.sortedDescending().forEach { path += octaveDo + it }
        } else {
            tones.filter { it > place }.sorted().forEach { path += octaveDo + it }
            path += octaveDo + 12
        }
        return path
    }

    /** The next note to ask: random from the level, not the same note twice in a row when there is a choice. */
    fun pick(level: ToneLevel, random: Random, previous: Int?): Int {
        val choices = level.notes.filter { it != previous }.ifEmpty { level.notes }
        return choices[random.nextInt(choices.size)]
    }

    /** A played key (MIDI) answers the question when it is the same place in the key, in any octave. */
    fun isRightKey(midi: Int, base: Int, note: Int): Boolean = Math.floorMod(midi - base, 12) == Math.floorMod(note, 12)

    // ---------------- storage (settings rows, so backups carry them) ----------------

    private val json = Json { ignoreUnknownKeys = true }
    private val levelsSerializer = ListSerializer(ToneLevel.serializer())
    private val statsSerializer = MapSerializer(String.serializer(), ToneStats.serializer())

    fun parseLevels(text: String?): List<ToneLevel> = text?.let { runCatching { json.decodeFromString(levelsSerializer, it) }.getOrNull() } ?: emptyList()
    fun levelsJson(levels: List<ToneLevel>): String = json.encodeToString(levelsSerializer, levels)
    fun parseStats(text: String?): Map<String, ToneStats> = text?.let { runCatching { json.decodeFromString(statsSerializer, it) }.getOrNull() } ?: emptyMap()
    fun statsJson(stats: Map<String, ToneStats>): String = json.encodeToString(statsSerializer, stats)
}
