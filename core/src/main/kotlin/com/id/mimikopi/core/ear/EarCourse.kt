package com.id.mimikopi.core.ear

import com.id.mimikopi.core.theory.ChordQualities
import com.id.mimikopi.core.theory.StageGenerators
import kotlin.random.Random

/**
 * The ear course (v0.25): from hearing do-mi-sol in a key to transcribing like a real song. Seven kinds of question,
 * each tracked per item (a degree, a chord quality, a progression ...), taught in ten stages that each add a few items
 * and keep the earlier ones in the mix. Aimed at the user's goal: working out idol songs by ear (melody by scale
 * degree, bass, the common J-pop progressions, last-chorus key changes, syncopated rhythm).
 */
enum class EarKind(val title: String, val about: String) {
    DEGREE("โน้ตในคีย์", "ฟังคอร์ดนำให้รู้คีย์ แล้วบอกว่าโน้ตที่ได้ยินคือตัวไหน"),
    MELODY("แกะทำนอง", "ฟังทำนองสั้นๆ แล้วเล่นกลับบนคีย์บอร์ด"),
    QUALITY("ชนิดคอร์ด", "ฟังคอร์ดหนึ่งตัวแล้วบอกว่าเป็นคอร์ดแบบไหน"),
    PROGRESSION("ทางเดินคอร์ด", "ฟังคอร์ดต่อกันแล้วบอกว่าเป็นโปรเกรสชันไหน"),
    BASS("เบส", "ฟังคอร์ดแล้วบอกว่าโน้ตต่ำสุดคือตัวไหนของคีย์"),
    KEY_CHANGE("เปลี่ยนคีย์", "ฟังทำนองสองรอบ แล้วบอกว่ารอบสองขึ้นคีย์ไหม ขึ้นเท่าไร"),
    RHYTHM("จังหวะ", "ฟังจังหวะหนึ่งห้อง แล้วเคาะกลับให้ตรง"),
}

/** Which keys a stage or test uses. */
enum class KeyPool(val tonics: List<Int>) {
    C_ONLY(listOf(0)),
    EASY(listOf(0, 7, 5, 2, 10)),
    ANY(listOf(0, 7, 5, 2, 10, 9, 3, 4, 8, 11, 1, 6)),
}

/** One step of what the app plays: notes together for [ms]; an empty step is a rest; [click] = metronome click. */
data class EarStep(val notes: List<Int>, val ms: Int, val velocity: Int = 80, val click: Boolean = false)

/** An answer button: what it means ([id]) and what it says. */
data class EarOption(val id: String, val label: String, val sub: String? = null)

/** What counts as right. */
sealed interface EarAnswer {
    /** One of the options. */
    data class Choice(val id: String) : EarAnswer
    /** Notes played back in order, judged by place in the key (any octave): semitones from do. */
    data class Notes(val places: List<Int>) : EarAnswer
    /** Taps in the answer bar: onsets in beats from its downbeat, at [bpm]. */
    data class Taps(val beats: List<Double>, val bpm: Int) : EarAnswer
}

/**
 * One question. [cadence] plays the key first; [sound] is the question itself (MIDI notes); [after] is what the app
 * plays once answered (the note walking home to do, the progression with its numerals ...).
 */
data class EarQuestion(
    val kind: EarKind,
    val item: String,
    val tonic: Int,
    val base: Int,
    val cadence: Boolean,
    val sound: List<EarStep>,
    val answer: EarAnswer,
    val options: List<EarOption> = emptyList(),
    val after: List<EarStep> = emptyList(),
    /** Shown once answered ("IVmaj7 – V7 – iii7 – vi"). */
    val reveal: String,
)

object EarCourse {
    // ---------------- items ----------------

    data class Prog(val id: String, val name: String, val numerals: List<String>)

    /** The progressions taught, the idol staples first (learning-system.md 8.1). */
    val PROGRESSIONS = listOf(
        Prog("axis", "Axis (I–V–vi–IV)", listOf("I", "V", "vi", "IV")),
        Prog("komuro", "โกมุโร 小室進行 (vi–IV–V–I)", listOf("vi", "IV", "V", "I")),
        Prog("royal", "ทางหลวง 王道進行 (IVM7–V7–iii7–vi)", listOf("IVmaj7", "V7", "iii7", "vi")),
        Prog("fifties", "วงจร 50s (I–vi–IV–V)", listOf("I", "vi", "IV", "V")),
        Prog("canon", "Canon (I–V–vi–iii–IV–I–IV–V)", listOf("I", "V", "vi", "iii", "IV", "I", "IV", "V")),
    )

    /** Diatonic triad (semitones above its root) on each major-scale place, for bass questions. */
    private val TRIAD_ON = mapOf(0 to "", 2 to "m", 4 to "m", 5 to "", 7 to "", 9 to "m", 11 to "dim")

    fun kindOf(item: String): EarKind = when (item.substringBefore(':')) {
        "deg" -> EarKind.DEGREE
        "mel" -> EarKind.MELODY
        "q" -> EarKind.QUALITY
        "prog" -> EarKind.PROGRESSION
        "bass" -> EarKind.BASS
        "kc" -> EarKind.KEY_CHANGE
        else -> EarKind.RHYTHM
    }

    private fun arg(item: String) = item.substringAfter(':')

    /** "ミ (3)", "ไมเนอร์ 7", "ทางหลวง 王道進行 ...". */
    fun label(item: String): String = when (kindOf(item)) {
        EarKind.DEGREE -> arg(item).toInt().let { "${FunctionalEar.solfege(it)} (${FunctionalEar.degree(it)})" }
        EarKind.BASS -> arg(item).toInt().let { "เบส ${FunctionalEar.solfege(it)}" + if (it == 4 || it == 11) " (อินเวอร์ชัน)" else "" }
        EarKind.QUALITY -> ChordQualities.NAMES[arg(item)] ?: arg(item)
        EarKind.PROGRESSION -> PROGRESSIONS.firstOrNull { it.id == arg(item) }?.name ?: arg(item)
        EarKind.KEY_CHANGE -> when (arg(item)) { "0" -> "คีย์เดิม"; "1" -> "ขึ้นครึ่งเสียง"; else -> "ขึ้นหนึ่งเสียง" }
        EarKind.MELODY -> "ทำนอง ${arg(item)} โน้ต"
        EarKind.RHYTHM -> "จังหวะ ระดับ ${arg(item)}"
    }

    // ---------------- stages ----------------

    data class Stage(val n: Int, val title: String, val adds: List<String>, val keys: KeyPool, val wide: Boolean, val about: String)

    val STAGES = listOf(
        Stage(1, "ド ミ ソ", listOf("deg:0", "deg:4", "deg:7"), KeyPool.C_ONLY, false, "สามโน้ตหลักของคีย์ ฟังว่าแต่ละตัวรู้สึกต่างกันอย่างไร"),
        Stage(2, "ห้าโน้ตแรก", listOf("deg:2", "deg:5", "rh:1"), KeyPool.C_ONLY, false, "เพิ่ม レ ファ และเคาะจังหวะตัวดำตัวขาว"),
        Stage(3, "ครบเจ็ดตัว", listOf("deg:9", "deg:11", "q:", "q:m"), KeyPool.C_ONLY, false, "ครบทั้งสเกล และแยกคอร์ดเมเจอร์กับไมเนอร์"),
        Stage(4, "ทำนองสั้น", listOf("mel:2", "mel:3", "rh:2"), KeyPool.C_ONLY, false, "ฟังทำนอง 2–3 โน้ตแล้วเล่นกลับ และจังหวะตัวเขบ็ด"),
        Stage(5, "หลายคีย์ และเบส", listOf("bass:0", "bass:5", "bass:7", "q:dim", "q:aug"), KeyPool.EASY, true, "คีย์อื่นนอกจาก C ช่วงเสียงกว้างขึ้น และโน้ตเบสของคอร์ด I IV V"),
        Stage(6, "ทางเดินคอร์ดแรก", listOf("prog:axis", "prog:komuro", "mel:4"), KeyPool.EASY, true, "โปรเกรสชันที่เพลงป็อปใช้มากที่สุด และทำนอง 4 โน้ต"),
        Stage(7, "เซเวนท์และทางหลวง", listOf("q:7", "q:maj7", "q:m7", "prog:royal", "prog:fifties", "bass:9", "bass:2", "rh:3"), KeyPool.EASY, true, "คอร์ดเซเวนท์ 王道進行 ที่เพลงไอดอลใช้บ่อย และจังหวะขัด"),
        Stage(8, "โน้ตนอกคีย์แบบป็อป", listOf("deg:10", "deg:3", "deg:8", "mel:5"), KeyPool.ANY, true, "♭7 ♭3 ♭6 ที่เพลงป็อปยืมมาใช้ ทุกคีย์"),
        Stage(9, "เปลี่ยนคีย์", listOf("kc:0", "kc:1", "kc:2", "q:sus4", "q:add9", "mel:6"), KeyPool.ANY, true, "ท่อนสุดท้ายขึ้นคีย์ แบบเพลงไอดอล และคอร์ด sus4 add9"),
        Stage(10, "แกะแบบเพลงจริง", listOf("prog:canon", "bass:4", "bass:11", "q:m7b5", "deg:6", "deg:1", "mel:8", "rh:4"), KeyPool.ANY, true, "ทำนองยาว เบสอินเวอร์ชัน Canon และจังหวะ 16"),
    )

    val ALL_ITEMS: List<String> = STAGES.flatMap { it.adds }

    fun stageOf(item: String): Stage = STAGES.first { item in it.adds }

    // ---------------- tests (the EAR skill levels) ----------------

    data class Test(val level: Int, val items: List<String>, val count: Int, val keys: KeyPool, val wide: Boolean)

    val TESTS = listOf(
        Test(1, listOf("deg:0", "deg:2", "deg:4", "deg:5", "deg:7"), 15, KeyPool.C_ONLY, false),
        Test(2, listOf("deg:0", "deg:2", "deg:4", "deg:5", "deg:7", "deg:9", "deg:11"), 20, KeyPool.C_ONLY, false),
        Test(3, listOf("mel:3", "q:", "q:m"), 12, KeyPool.C_ONLY, false),
        Test(4, listOf("deg:0", "deg:2", "deg:4", "deg:5", "deg:7", "deg:9", "deg:11", "bass:0", "bass:5", "bass:7"), 20, KeyPool.EASY, true),
        Test(5, listOf("mel:4", "prog:axis", "prog:komuro", "prog:fifties"), 12, KeyPool.EASY, true),
        Test(6, listOf("q:7", "q:maj7", "q:m7", "q:", "q:m", "prog:royal", "prog:axis", "prog:komuro"), 12, KeyPool.EASY, true),
        Test(7, listOf("mel:5", "mel:6", "kc:0", "kc:1", "kc:2", "deg:10", "deg:3"), 12, KeyPool.ANY, true),
        Test(8, listOf("mel:8", "prog:canon", "prog:royal", "bass:4", "bass:11", "q:m7b5", "q:sus4"), 12, KeyPool.ANY, true),
    )

    fun test(level: Int): Test? = TESTS.firstOrNull { it.level == level }

    // ---------------- questions ----------------

    /** MIDI of the tonic in the middle register (F#3–F4), as the old trainer. */
    fun baseOf(tonic: Int) = FunctionalEar.base(tonic)

    /** Fifths of the major key on pitch class [tonic] (for spelling and numerals): C 0, G 1, F -1 ... */
    fun fifthsOf(tonic: Int): Int = ((Math.floorMod(tonic, 12) * 7) % 12).let { if (it > 6) it - 12 else it }

    /** The key-setting cadence I–IV–V–I as MIDI steps. */
    fun cadenceSteps(tonic: Int): List<EarStep> {
        val base = baseOf(tonic)
        return FunctionalEar.cadence(EarScale.MAJOR).map { c -> EarStep(c.map { base + it }, 650, 68) } + EarStep(emptyList(), 350)
    }

    /**
     * A question for [item]. [unlocked] limits the answer buttons and melody notes to what has been taught; [strength]
     * (0..1, how well the item is known) decides help such as a cadence before every question.
     */
    fun question(item: String, unlocked: Collection<String>, keys: KeyPool, wide: Boolean, strength: Double, random: Random): EarQuestion {
        val tonic = keys.tonics[random.nextInt(keys.tonics.size)]
        val base = baseOf(tonic)
        val degreePlaces = unlocked.filter { kindOf(it) == EarKind.DEGREE }.map { arg(it).toInt() }.ifEmpty { listOf(0, 4, 7) }.sorted()
        val n = arg(item)
        return when (kindOf(item)) {
            EarKind.DEGREE -> {
                val place = n.toInt()
                val note = when {
                    !wide -> if (place == 0 && random.nextBoolean()) 12 else place
                    random.nextInt(3) == 0 -> place - 12
                    else -> place
                }
                EarQuestion(
                    EarKind.DEGREE, item, tonic, base, cadence = true,
                    sound = listOf(EarStep(listOf(base + note), 1100, 90)),
                    answer = EarAnswer.Choice(item),
                    options = degreePlaces.map { EarOption("deg:$it", FunctionalEar.solfege(it), FunctionalEar.degree(it)) },
                    after = FunctionalEar.resolution(note, EarScale.MAJOR).map { EarStep(listOf(base + it), 300, 84) },
                    reveal = "${FunctionalEar.solfege(place)} · ${FunctionalEar.letter(tonic + place, tonic)}",
                )
            }
            EarKind.BASS -> {
                val place = n.toInt()
                // Inversions: I with its 3rd in the bass (4), V with its 7th degree (11); otherwise the triad on that degree.
                val (rootPlace, quality) = when (place) {
                    4 -> 0 to ""
                    11 -> 7 to ""
                    else -> place to (TRIAD_ON[place] ?: "")
                }
                val upper = ChordQualities.INTERVALS.getValue(quality).map { base + rootPlace + it }.map { if (it > base + 9) it - 12 else it }.sorted()
                val bass = base - 12 + place - (if (place >= 7) 12 else 0)
                val options = unlocked.filter { kindOf(it) == EarKind.BASS }.map { arg(it).toInt() }.sorted()
                    .map { EarOption("bass:$it", FunctionalEar.solfege(it), FunctionalEar.degree(it)) }
                EarQuestion(
                    EarKind.BASS, item, tonic, base, cadence = true,
                    sound = listOf(EarStep(listOf(bass) + upper, 1600, 84)),
                    answer = EarAnswer.Choice(item), options = options,
                    after = listOf(EarStep(listOf(bass), 900, 96), EarStep(listOf(bass) + upper, 1200, 84)),
                    reveal = "เบส ${FunctionalEar.solfege(place)} · ${FunctionalEar.letter(tonic + place, tonic)}",
                )
            }
            EarKind.QUALITY -> {
                val root = 55 + random.nextInt(9)
                val intervals = ChordQualities.INTERVALS.getValue(n)
                val chord = intervals.map { root + it }
                val options = unlocked.filter { kindOf(it) == EarKind.QUALITY }.map { arg(it) }
                    .map { EarOption("q:$it", ChordQualities.NAMES[it] ?: it, "C" + it.replace("b", "♭").ifEmpty { "" }) }
                EarQuestion(
                    EarKind.QUALITY, item, 0, root, cadence = false,
                    sound = listOf(EarStep(chord, 1300, 84), EarStep(emptyList(), 200)) + chord.map { EarStep(listOf(it), 320, 80) },
                    answer = EarAnswer.Choice(item), options = options,
                    after = listOf(EarStep(chord, 1300, 84)),
                    reveal = ChordQualities.NAMES[n] ?: n,
                )
            }
            EarKind.PROGRESSION -> {
                val p = PROGRESSIONS.first { it.id == n }
                val fifths = fifthsOf(tonic)
                val ms = if (p.numerals.size > 4) 750 else 1100
                val steps = p.numerals.map { numeral ->
                    val c = StageGenerators.degreeChord(numeral, fifths)
                    val bass = 36 + Math.floorMod(c.root.pc - 36, 12)
                    EarStep(listOf(bass) + c.voicing(55).filter { it > bass }, ms, 80)
                }
                val options = unlocked.filter { kindOf(it) == EarKind.PROGRESSION }.map { id -> PROGRESSIONS.first { it.id == arg(id) } }
                    .map { EarOption("prog:${it.id}", it.name.substringBefore(" ("), it.name.substringAfter("(", "").removeSuffix(")")) }
                EarQuestion(
                    EarKind.PROGRESSION, item, tonic, base, cadence = false,
                    sound = steps, answer = EarAnswer.Choice(item), options = options, after = steps,
                    reveal = p.numerals.joinToString(" – ") + " (คีย์ ${FunctionalEar.keyName(tonic, EarScale.MAJOR)})",
                )
            }
            EarKind.KEY_CHANGE -> {
                val shift = n.toInt()
                val phrase = melody(4, listOf(0, 2, 4, 5, 7), wide = false, random = random)
                val first = phrase.map { EarStep(listOf(base + it), 420, 86) }
                val second = phrase.map { EarStep(listOf(base + it + shift), 420, 86) }
                EarQuestion(
                    EarKind.KEY_CHANGE, item, tonic, base, cadence = true,
                    sound = first + EarStep(emptyList(), 600) + second,
                    answer = EarAnswer.Choice(item),
                    options = listOf("0", "1", "2").map { EarOption("kc:$it", label("kc:$it")) },
                    after = first + EarStep(emptyList(), 600) + second,
                    reveal = label(item),
                )
            }
            EarKind.MELODY -> {
                val length = n.toInt()
                // Melody notes: the degrees taught (diatonic ones only until the out-of-key stage).
                val pool = degreePlaces.filter { it in FunctionalEar.MAJOR || "deg:$it" in unlocked }.ifEmpty { listOf(0, 2, 4, 5, 7) }
                val phrase = melody(length, pool, wide = wide && strength > 0.5, random = random)
                val ms = if (length >= 6) 380 else 480
                EarQuestion(
                    EarKind.MELODY, item, tonic, base, cadence = true,
                    sound = phrase.map { EarStep(listOf(base + it), ms, 88) },
                    answer = EarAnswer.Notes(phrase),
                    after = phrase.map { EarStep(listOf(base + it), ms, 88) },
                    reveal = phrase.joinToString(" ") { FunctionalEar.solfege(it) },
                )
            }
            EarKind.RHYTHM -> {
                val level = n.toInt()
                val bpm = when (level) { 1 -> 72; 2 -> 76; 3 -> 84; else -> 92 }
                val onsets = rhythm(level, random)
                EarQuestion(
                    EarKind.RHYTHM, item, 0, 72, cadence = false,
                    sound = rhythmSteps(onsets, bpm),
                    answer = EarAnswer.Taps(onsets, bpm),
                    after = rhythmSteps(onsets, bpm),
                    reveal = onsets.joinToString(" ") { b -> "%.2f".format(b + 1).trimEnd('0').trimEnd('.') },
                )
            }
        }
    }

    /**
     * A singable phrase of [length] places from [pool]: starts on do, mi or sol, moves mostly by step through the pool
     * (sorted), with leaps of up to four pool steps now and then; in the wide range it may dip below do.
     */
    fun melody(length: Int, pool: List<Int>, wide: Boolean, random: Random): List<Int> {
        val ladder = (pool.sorted() + if (wide) pool.filter { it in 5..11 }.map { it - 12 } else emptyList()).distinct().sorted()
        val starts = ladder.filter { Math.floorMod(it, 12) in setOf(0, 4, 7) }.ifEmpty { ladder }
        var i = ladder.indexOf(starts[random.nextInt(starts.size)])
        val out = mutableListOf(ladder[i])
        while (out.size < length) {
            val move = if (random.nextInt(10) < 7) (if (random.nextBoolean()) 1 else -1) else (if (random.nextBoolean()) 1 else -1) * (2 + random.nextInt(3))
            i = (i + move).coerceIn(0, ladder.size - 1)
            if (ladder[i] == out.last() && ladder.size > 1) i = if (i + 1 < ladder.size) i + 1 else i - 1
            out += ladder[i]
        }
        return out
    }

    /**
     * One bar of 4/4 as onsets in beats: level 1 quarters and halves, 2 adds eighths, 3 adds dotted and syncopated
     * figures (e-q-e), 4 adds sixteenths. Always starts on the downbeat.
     */
    fun rhythm(level: Int, random: Random): List<Double> {
        val cells = mutableListOf(listOf(0.0), listOf(0.0)) // the quarter note, twice as likely
        if (level >= 2) cells.add(listOf(0.0, 0.5))
        if (level >= 3) { cells.add(listOf(0.0, 0.75)); cells.add(listOf(0.5)) } // dotted eighth + sixteenth; off-beat eighth
        if (level >= 4) { cells.add(listOf(0.0, 0.25, 0.5, 0.75)); cells.add(listOf(0.0, 0.5, 0.75)); cells.add(listOf(0.0, 0.25, 0.5)) }
        val out = mutableListOf<Double>()
        var beat = 0
        while (beat < 4) {
            // A half note (two beats, one onset) now and then on beats 1 or 3.
            if (level <= 2 && beat % 2 == 0 && random.nextInt(4) == 0) { out += beat.toDouble(); beat += 2; continue }
            val cell = cells[random.nextInt(cells.size)]
            // A beat that starts with a rest ("e-rest then e") never opens the bar.
            val use = if (beat == 0 && cell.first() > 0.0) listOf(0.0) else cell
            out += use.map { beat + it }
            beat++
        }
        return out
    }

    /** Count-in (four clicks), the rhythm on one note, then four more clicks before the user taps it back. */
    fun rhythmSteps(onsets: List<Double>, bpm: Int): List<EarStep> {
        val beatMs = 60_000.0 / bpm
        val out = mutableListOf<EarStep>()
        repeat(4) { out += EarStep(emptyList(), beatMs.toInt(), 100, click = true) }
        val times = onsets + 4.0
        for (i in onsets.indices) out += EarStep(listOf(72), ((times[i + 1] - times[i]) * beatMs).toInt(), 96)
        return out
    }

    // ---------------- judging ----------------

    /** A played-back melody: right when every note is the right place in the key (any octave), in order. */
    fun judgeNotes(expected: List<Int>, playedMidi: List<Int>, base: Int): List<Boolean> =
        expected.mapIndexed { i, place -> playedMidi.getOrNull(i)?.let { Math.floorMod(it - base, 12) == Math.floorMod(place, 12) } ?: false }

    /**
     * Tapped rhythm: [tapsMs] are times from the answer bar's downbeat (latency already removed). Right when there are
     * as many taps as notes and each lands within [toleranceMs] of its note. Returns the offsets (null = missing).
     */
    fun judgeTaps(expected: EarAnswer.Taps, tapsMs: List<Long>, toleranceMs: Long = tolerance(expected.bpm)): Pair<Boolean, List<Long?>> {
        val beatMs = 60_000.0 / expected.bpm
        val targets = expected.beats.map { (it * beatMs).toLong() }
        val offsets = targets.mapIndexed { i, t -> tapsMs.getOrNull(i)?.let { it - t } }
        val right = tapsMs.size == targets.size && offsets.all { it != null && kotlin.math.abs(it) <= toleranceMs }
        return right to offsets
    }

    fun tolerance(bpm: Int): Long = maxOf(90L, (60_000.0 / bpm * 0.15).toLong())
}
