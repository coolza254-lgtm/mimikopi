package com.id.mimikopi.core.exam

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.KeyPool
import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.theory.Accompaniment
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.Roman
import com.id.mimikopi.core.theory.StageGenerators
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * Grade exams modelled on the ABRSM practical exam (learning-system.md 2.7): three pieces, scales and arpeggios,
 * sight-reading and aural tests, 150 marks, pass 100, merit 120, distinction 130, grades Initial to 5. The user asked
 * for "ABRSM + a pop part": the third piece is a pop piece in the style of idol songs. Everything is made by the app
 * or taken from the public-domain library: no real exam pieces, no ABRSM material. The marks are the app's own, from
 * what the judge measures (notes, timing, stops); they are not an official grade.
 */

/** The parts of an exam with their marks (ABRSM's split: 30 per piece, 21 scales, 21 sight-reading, 18 aural). */
enum class ExamPart(val title: String, val max: Int, val pass: Int, val merit: Int, val distinction: Int) {
    PIECE_1(tr("เพลงที่ 1", "Piece 1", "1曲目"), 30, 20, 25, 27),
    PIECE_2(tr("เพลงที่ 2", "Piece 2", "2曲目"), 30, 20, 25, 27),
    POP(tr("เพลงป็อป", "Pop piece", "ポップ曲"), 30, 20, 25, 27),
    SCALES(tr("สเกลและอาร์เพจจิโอ", "Scales and arpeggios", "スケールとアルペジオ"), 21, 14, 17, 19),
    SIGHT(tr("อ่านโน้ตใหม่", "Sight-reading", "初見"), 21, 14, 17, 19),
    AURAL(tr("ทดสอบการฟัง", "Aural test", "聴音テスト"), 18, 12, 15, 17),
}

enum class Band(val title: String) {
    DISTINCTION(tr("ดีเยี่ยม (Distinction)", "Distinction", "優")),
    MERIT(tr("ดี (Merit)", "Merit", "良")),
    PASS(tr("ผ่าน (Pass)", "Pass", "合格")),
    BELOW(tr("ยังไม่ผ่าน", "Not passed yet", "まだ不合格")),
}

/** The pop piece of a grade: a progression in a key with a left-hand pattern of the accompaniment ladder. */
data class PopSpec(val key: Int, val progression: String, val pattern: Int, val bars: Int, val bpm: Int, val melody: Melody) {
    enum class Melody { SIMPLE, FLOWING, SYNCOPATED }
}

/** Aural tests: ear-course items asked [count] times; [context] sets the answer choices and the notes melodies use. */
data class AuralSpec(val items: List<String>, val count: Int, val keys: KeyPool, val wide: Boolean, val context: List<String>)

data class Grade(
    val n: Int,
    val name: String,
    val about: String,
    /** Library pieces: two are chosen for the exam. */
    val pieces: List<String>,
    val pop: PopSpec,
    val scales: List<ScaleItem>,
    /** How many scales the exam asks. */
    val ask: Int,
    val scaleBpm: Int,
    /** Scales in quarter notes (Initial) instead of eighths. */
    val quarters: Boolean,
    /** Sight-reading level (L1-L8 of the reading ladder) and tempo. */
    val sightLevel: Int,
    val sightBpm: Int,
    val aural: AuralSpec,
    /** Roughly how long the whole exam takes. */
    val minutes: Int,
)

/** One part taken. */
@Serializable
data class PartMark(
    val marks: Int,
    val at: Long,
    val accuracy: Double? = null,
    val timing: Double? = null,
    val stumbles: Int? = null,
    /** Aural: first-try right answers of [asked]. */
    val right: Int? = null,
    val asked: Int? = null,
)

/** One exam: a grade started on a day, with the two library pieces chosen and the parts taken so far. */
@Serializable
data class ExamSitting(
    val id: String,
    val grade: Int,
    val day: String,
    val startedAt: Long,
    val pieces: List<String>,
    /** Seeds the scales asked and the sight-reading piece. */
    val seed: Long,
    /** Part name -> its marks. Each part is taken once, as in an exam. */
    val parts: Map<String, PartMark> = emptyMap(),
    /** Given up (it then never counts). */
    val abandoned: Boolean = false,
) {
    fun mark(part: ExamPart): PartMark? = parts[part.name]
    val complete: Boolean get() = ExamPart.entries.all { it.name in parts }
    val total: Int get() = parts.values.sumOf { it.marks }
}

object Exams {
    private fun major(t: String, o: Int, ht: Boolean) = ScaleItem(ScaleKind.MAJOR, t, o, ht)
    private fun minor(t: String, o: Int, ht: Boolean) = ScaleItem(ScaleKind.MINOR, t, o, ht)
    private fun arp(t: String, o: Int, ht: Boolean) = ScaleItem(ScaleKind.ARP_MAJOR, t, o, ht)
    private fun arpm(t: String, o: Int, ht: Boolean) = ScaleItem(ScaleKind.ARP_MINOR, t, o, ht)
    private val CONTRARY_C = ScaleItem(ScaleKind.CONTRARY, "C", 1, true)
    private fun chromatic(t: String) = ScaleItem(ScaleKind.CHROMATIC, t, 1, false)

    private val DIATONIC = listOf("deg:0", "deg:2", "deg:4", "deg:5", "deg:7", "deg:9", "deg:11")

    /**
     * Grades Initial-5. Pieces come from the library levels (Initial L1-L2, then one level per grade up to L7); the
     * pop piece moves from root notes in C to flowing arpeggios in E♭; scales from one octave hands separately to two
     * octaves hands together; sight-reading follows the reading ladder; aural follows the ear course.
     */
    val GRADES: List<Grade> = listOf(
        Grade(
            0, "Initial", tr("ทำนองมือขวาง่ายๆ มือซ้ายเบา สเกลอ็อกเทฟเดียวทีละมือ", "Easy right-hand melodies, a light left hand, one-octave scales hands separately", "やさしい右手のメロディ、軽い左手、1オクターブのスケールを片手ずつ"),
            listOf("hot-cross-buns", "lightly-row", "old-macdonald"),
            PopSpec(0, "Axis", 1, 8, 72, PopSpec.Melody.SIMPLE),
            listOf(major("C", 1, false), major("G", 1, false), major("F", 1, false), minor("A", 1, false), CONTRARY_C),
            ask = 3, scaleBpm = 60, quarters = true, sightLevel = 1, sightBpm = 60,
            aural = AuralSpec(listOf("rh:1", "deg:0", "deg:2", "deg:4", "deg:5", "deg:7"), 10, KeyPool.C_ONLY, false,
                listOf("deg:0", "deg:2", "deg:4", "deg:5", "deg:7")),
            minutes = 12,
        ),
        Grade(
            1, tr("เกรด 1", "Grade 1", "グレード1"), tr("สองมือแบบง่าย จังหวะ 3/4 และ 6/8 สเกลเขบ็ตหนึ่งชั้น อาร์เพจจิโอแรก", "Easy hands together, 3/4 and 6/8 time, quaver scales, first arpeggios", "やさしい両手、3/4と6/8拍子、8分音符のスケール、はじめてのアルペジオ"),
            listOf("au-clair-de-la-lune", "mary-had-a-little-lamb", "row-row-row", "when-the-saints", "yankee-doodle"),
            PopSpec(1, "โกมุโร", 1, 8, 80, PopSpec.Melody.SIMPLE),
            listOf(major("C", 1, false), major("G", 1, false), major("D", 1, false), major("F", 1, false), minor("A", 1, false), minor("D", 1, false),
                CONTRARY_C, arp("C", 1, false), arp("G", 1, false), arpm("A", 1, false)),
            ask = 4, scaleBpm = 56, quarters = false, sightLevel = 3, sightBpm = 66,
            aural = AuralSpec(listOf("rh:1", "rh:2", "mel:2", "mel:3", "q:", "q:m", "deg:9", "deg:11"), 12, KeyPool.C_ONLY, false, DIATONIC),
            minutes = 12,
        ),
        Grade(
            2, tr("เกรด 2", "Grade 2", "グレード2"), tr("เพลงยาวขึ้น มือซ้ายเดินมากขึ้น สเกลสองมือพร้อมกัน และโครมาติก", "Longer pieces, a busier left hand, scales hands together, and chromatic", "長めの曲、よく動く左手、両手のスケール、半音階"),
            listOf("frere-jacques", "jingle-bells", "london-bridge", "ode-to-joy", "sakura-sakura", "we-wish-you-a-merry-christmas"),
            PopSpec(-1, "วงจร 50s", 2, 8, 84, PopSpec.Melody.SIMPLE),
            listOf(major("G", 1, true), major("D", 1, true), major("F", 1, true), major("Bb", 1, false), minor("E", 1, false), minor("D", 1, false),
                minor("G", 1, false), CONTRARY_C, arp("G", 1, false), arp("D", 1, false), arp("F", 1, false), arpm("E", 1, false), arpm("D", 1, false), chromatic("C")),
            ask = 5, scaleBpm = 63, quarters = false, sightLevel = 4, sightBpm = 60,
            aural = AuralSpec(listOf("rh:2", "mel:3", "q:", "q:m", "bass:0", "bass:5", "bass:7"), 12, KeyPool.EASY, true, DIATONIC),
            minutes = 13,
        ),
        Grade(
            3, tr("เกรด 3", "Grade 3", "グレード3"), tr("อ่านสองมือ สเกลสองอ็อกเทฟ คีย์แฟลต และทางเดินคอร์ดป็อปด้วยหู", "Two-hand reading, two-octave scales, flat keys and pop progressions by ear", "両手の読譜、2オクターブのスケール、フラット系のキー、耳でポップのコード進行"),
            listOf("eine-kleine-nachtmusik", "silent-night", "twinkle-twinkle"),
            PopSpec(2, "ทางหลวง (王道進行)", 4, 12, 88, PopSpec.Melody.FLOWING),
            listOf(major("A", 2, false), major("E", 2, false), major("Bb", 2, false), major("Eb", 2, false), major("D", 2, true),
                minor("C", 2, false), minor("G", 2, false), minor("A", 2, true), arp("A", 2, false), arp("E", 2, false),
                arpm("C", 2, false), arpm("G", 2, false), chromatic("D")),
            ask = 5, scaleBpm = 72, quarters = false, sightLevel = 5, sightBpm = 56,
            aural = AuralSpec(listOf("mel:4", "rh:3", "q:dim", "q:aug", "prog:axis", "prog:komuro", "prog:fifties"), 12, KeyPool.EASY, true,
                DIATONIC + listOf("q:", "q:m", "q:dim", "q:aug")),
            minutes = 15,
        ),
        Grade(
            4, tr("เกรด 4", "Grade 4", "グレード4"), tr("เพลงคลาสสิกจริงจังขึ้น ป็อปคีย์ A♭ แบบ I WISH สเกลสองอ็อกเทฟสองมือ", "More serious classical pieces, pop in A♭ like I WISH, two-octave scales hands together", "本格的なクラシック曲、I WISHのようなA♭のポップ、2オクターブの両手スケール"),
            listOf("greensleeves", "minuet-in-g"),
            PopSpec(-4, "ทางหลวงเต็มวง", 5, 16, 92, PopSpec.Melody.SYNCOPATED),
            listOf(major("A", 2, true), major("E", 2, true), major("Bb", 2, true), major("Eb", 2, true), major("Ab", 2, true),
                minor("A", 2, true), minor("E", 2, true), minor("D", 2, true), minor("G", 2, true), minor("C", 2, true),
                arp("C", 2, true), arp("G", 2, true), arp("D", 2, true), arp("F", 2, true), arpm("A", 2, true), arpm("E", 2, true), arpm("D", 2, true),
                chromatic("C")),
            ask = 6, scaleBpm = 80, quarters = false, sightLevel = 6, sightBpm = 66,
            aural = AuralSpec(listOf("mel:5", "rh:3", "q:7", "q:maj7", "q:m7", "prog:royal", "bass:9", "bass:2"), 12, KeyPool.EASY, true,
                DIATONIC + listOf("q:", "q:m", "q:7", "q:maj7", "q:m7", "prog:royal", "prog:axis", "prog:komuro", "bass:0", "bass:5", "bass:7", "bass:9", "bass:2")),
            minutes = 18,
        ),
        Grade(
            5, tr("เกรด 5", "Grade 5", "グレード5"), tr("Für Elise และ Prelude in C ป็อปคีย์ E♭ แบบเพลงไอดอล ทุกสเกลที่เรียนมา และแกะการเปลี่ยนคีย์", "Für Elise and Prelude in C, idol-style pop in E♭, every scale so far, and hearing key changes", "エリーゼのためにとプレリュードハ長調、E♭のアイドル風ポップ、これまでの全スケール、転調の聞き取り"),
            listOf("fur-elise", "prelude-in-c"),
            PopSpec(-3, "Canon", 6, 16, 96, PopSpec.Melody.SYNCOPATED),
            listOf("C", "G", "D", "A", "E", "F", "Bb", "Eb", "Ab").map { major(it, 2, true) } +
                listOf("A", "E", "D", "G", "C").map { minor(it, 2, true) } +
                listOf("C", "G", "D", "A", "E", "F").map { arp(it, 2, true) } +
                listOf("A", "E", "D", "G", "C").map { arpm(it, 2, true) } + listOf(CONTRARY_C, chromatic("C"), chromatic("D")),
            ask = 7, scaleBpm = 92, quarters = false, sightLevel = 7, sightBpm = 60,
            aural = AuralSpec(listOf("mel:6", "rh:4", "kc:0", "kc:1", "kc:2", "prog:canon", "q:sus4", "bass:4", "bass:11"), 12, KeyPool.ANY, true,
                DIATONIC + listOf("deg:10", "deg:3", "q:", "q:m", "q:7", "q:sus4", "q:add9", "prog:canon", "prog:royal", "prog:axis", "prog:komuro",
                    "bass:0", "bass:5", "bass:7", "bass:4", "bass:11")),
            minutes = 20,
        ),
    )

    fun grade(n: Int): Grade? = GRADES.firstOrNull { it.n == n }

    /** The library level a grade's own material matches (sets the timing tolerance as for library pieces). */
    fun levelOf(grade: Grade): String = "L${if (grade.n == 0) 2 else grade.n + 2}"

    // ---------------- material ----------------

    /** The scales an exam asks: [Grade.ask] of the grade's list, at least one scale and one arpeggio, in list order. */
    fun askedScales(grade: Grade, seed: Long): List<ScaleItem> {
        val rnd = Random(seed)
        val shuffled = grade.scales.shuffled(rnd)
        val arp = shuffled.firstOrNull { it.kind == ScaleKind.ARP_MAJOR || it.kind == ScaleKind.ARP_MINOR }
        val scale = shuffled.firstOrNull { it.kind != ScaleKind.ARP_MAJOR && it.kind != ScaleKind.ARP_MINOR }
        val picked = (listOfNotNull(arp, scale) + shuffled).distinct().take(grade.ask)
        return grade.scales.filter { it in picked }
    }

    /** The scales part: the asked items in one score, a bar of rest between them. */
    fun scalesPiece(grade: Grade, seed: Long): PieceFile =
        ExamScales.piece("exam-scales-${grade.n}-$seed", tr("สเกลและอาร์เพจจิโอ · ${grade.name}", "Scales and arpeggios · ${grade.name}", "スケールとアルペジオ · ${grade.name}"), askedScales(grade, seed), grade.scaleBpm, grade.quarters, levelOf(grade))

    /** One scale to practise on its own (opened from the requirements list). */
    fun scalePractice(grade: Grade, item: ScaleItem): PieceFile =
        ExamScales.piece("exam-scale-${grade.n}-${item.id}", item.title(), listOf(item), grade.scaleBpm, grade.quarters, levelOf(grade))

    /** The sight-reading piece of a sitting: new music at the grade's reading level. */
    fun sightPiece(grade: Grade, seed: Long): PieceFile {
        val level = grade.sightLevel
        val drill = Drill(
            ExerciseType.E_READ_PIECE, "exam", level = "L$level", bpm = grade.sightBpm, mode = "TEMPO",
            hand = sightHand(grade), keys = if (level == 6) listOf(0, 1, -1, 2, -2) else emptyList(),
        )
        return ScoreGenerators.read(drill, seed).copy(id = "exam-sight-${grade.n}-$seed", title = tr("อ่านโน้ตใหม่ · ${grade.name}", "Sight-reading · ${grade.name}", "初見 · ${grade.name}"))
    }

    /** Hands the sight-reading piece is written for: "RIGHT", "LEFT" or "BOTH". */
    fun sightHand(grade: Grade): String = when (grade.sightLevel) {
        2 -> "LEFT"
        1, 3, 4 -> "RIGHT"
        else -> "BOTH"
    }

    private const val POP_SEED = 52_000L

    /**
     * The pop piece of a grade, the same every time so it can be prepared: a melody of chord tones over an idol-style
     * progression, the left hand in the grade's accompaniment pattern, chord symbols above. The left hand stays below
     * the right hand: never the same key, never above it.
     */
    fun pop(grade: Grade): PieceFile {
        val spec = grade.pop
        val rnd = Random(POP_SEED + grade.n)
        val prog = StageGenerators.PROGRESSIONS.first { it.name == spec.progression }
        val fifths = spec.key
        val chords = StageGenerators.progression(prog, fifths, spec.bars - 1) + Roman.chordOf(0, fifths)
        val placed = chords.mapIndexed { i, c -> i * 16 to c }
        val melody = StageGenerators.chordToneMelody(chords, fifths, rnd, simple = spec.melody == PopSpec.Melody.SIMPLE,
            syncopation = spec.melody == PopSpec.Melody.SYNCOPATED)
        // From comping on every beat up, the accompaniment reaches G4-B♭4: the melody goes up an octave to stay above it.
        val rh = StageGenerators.endOnTonic(melody, fifths).let { r ->
            if (spec.pattern >= 3) r.map { n -> n.copy(pitches = n.pitches.map { Keys.spellMidi(it.midi + 12, fifths) }) } else r
        }
        val lh = StageGenerators.clearLeft(
            Accompaniment.leftHand(placed, spec.pattern, spec.bars * 16).map { n -> n.copy(pitches = n.pitches.map { Keys.spellMidi(it.midi, fifths) }) },
            rh, below = true,
        )
        val meta = ScoreMeta("exam-pop-${grade.n}", "ป็อป ${grade.name}: ${prog.name}", "สร้างโดยแอพ", "Generated by the app (pop study, original)",
            levelOf(grade), fifths, listOf(4, 4), spec.bpm)
        return Timeline(meta, spec.bars, rh, lh, placed.map { it.first to it.second.symbol }).toPieceFile()
    }

    /** Exam material the reader can open as a piece to practise ("exam-pop-3", "exam-scale-3-<item>"). */
    fun practicePiece(id: String): PieceFile? {
        Regex("""^exam-pop-(\d)$""").matchEntire(id)?.let { m -> return grade(m.groupValues[1].toInt())?.let(::pop) }
        Regex("""^exam-scale-(\d)-(.+)$""").matchEntire(id)?.let { m ->
            val g = grade(m.groupValues[1].toInt()) ?: return null
            val item = ScaleItem.parse(m.groupValues[2])?.takeIf { it in g.scales } ?: return null
            return scalePractice(g, item)
        }
        return null
    }

    /** The aural questions of a sitting. */
    fun auralRound(grade: Grade, random: Random): List<EarQuestion> {
        val a = grade.aural
        val items = (a.items + List(a.count) { a.items[random.nextInt(a.items.size)] }).take(a.count).shuffled(random)
        val context = (a.items + a.context).distinct()
        return items.map { EarCourse.question(it, context, a.keys, a.wide, 1.0, random) }
    }

    // ---------------- marking ----------------

    /**
     * One number for a played part: notes right weigh 60%, notes on time 40%, minus 3 points per stop (at most 15).
     * Then pieces get 20 at 0.80 and 30 at 1.00; scales 14 at 0.80 and 21 at 1.00; sight-reading, judged more gently
     * as in an exam, 14 at 0.70 and 21 at 1.00. A part played to the end earns at least a third, however it went;
     * one stopped early earns only what its notes give; nothing played (no key pressed) earns nothing.
     */
    fun quality(accuracy: Double, timing: Double, stumbles: Int): Double =
        0.6 * accuracy + 0.4 * timing - minOf(0.15, 0.03 * stumbles)

    fun playedMarks(part: ExamPart, accuracy: Double, timing: Double, stumbles: Int, played: Boolean = accuracy > 0.0, complete: Boolean = true): Int {
        if (!played) return 0
        val q = quality(accuracy, timing, stumbles)
        val raw = when (part) {
            ExamPart.PIECE_1, ExamPart.PIECE_2, ExamPart.POP -> 20 + (q - 0.80) * 50
            ExamPart.SCALES -> 14 + (q - 0.80) * 35
            ExamPart.SIGHT -> 14 + (q - 0.70) * (7 / 0.30)
            ExamPart.AURAL -> error("aural is marked from answers")
        }
        return raw.roundToInt().coerceIn(if (complete) part.max / 3 else 0, part.max)
    }

    /** Aural: marks in proportion to first-try answers (12 of 18 = two thirds right). */
    fun auralMarks(right: Int, asked: Int): Int = if (asked <= 0) 0 else (18.0 * right / asked).roundToInt().coerceIn(0, 18)

    fun partBand(part: ExamPart, marks: Int): Band = when {
        marks >= part.distinction -> Band.DISTINCTION
        marks >= part.merit -> Band.MERIT
        marks >= part.pass -> Band.PASS
        else -> Band.BELOW
    }

    const val MAX = 150
    const val PASS = 100
    const val MERIT = 120
    const val DISTINCTION = 130

    /** As in ABRSM, only the total decides; a part below its pass mark does not fail the exam by itself. */
    fun band(total: Int): Band = when {
        total >= DISTINCTION -> Band.DISTINCTION
        total >= MERIT -> Band.MERIT
        total >= PASS -> Band.PASS
        else -> Band.BELOW
    }

    /** A short examiner-style remark on a played part, from what pulled its marks down most. */
    fun remark(mark: PartMark): String {
        val acc = mark.accuracy
        val timing = mark.timing
        if (mark.right != null && mark.asked != null) {
            val wrong = mark.asked - mark.right
            return if (wrong == 0) tr("ตอบถูกทุกข้อในครั้งแรก", "All right first time", "すべて1回目で正解") else tr("ตอบผิดครั้งแรก $wrong ข้อจาก ${mark.asked}", "$wrong of ${mark.asked} wrong first time", "${mark.asked}問中${wrong}問を1回目で間違え")
        }
        if (acc == null || timing == null) return ""
        val parts = mutableListOf(tr("โน้ตถูก ${(acc * 100).roundToInt()}%", "Notes right ${(acc * 100).roundToInt()}%", "音の正確さ ${(acc * 100).roundToInt()}%"), tr("ตรงจังหวะ ${(timing * 100).roundToInt()}%", "On time ${(timing * 100).roundToInt()}%", "リズム ${(timing * 100).roundToInt()}%"))
        if ((mark.stumbles ?: 0) > 0) parts += tr("หยุด ${mark.stumbles} ครั้ง", "Stopped ${mark.stumbles} times", "${mark.stumbles}回止まった")
        val note = when {
            (mark.stumbles ?: 0) >= 2 -> tr("เล่นต่อเนื่องให้ได้ก่อน ผิดแล้วไปต่อ", "Keep going first: after a mistake, carry on", "まず止まらずに：間違えても弾き続ける")
            acc < 0.9 -> tr("โน้ตยังหลุดบ่อย ฝึกช้าลงจนแม่น", "Notes still slip often; practise slower until accurate", "まだ音をよく外します。正確になるまでゆっくり練習")
            timing < 0.8 -> tr("จังหวะยังไม่นิ่ง ฝึกกับเมโทรนอม", "Timing isn't steady yet; practise with the metronome", "リズムがまだ安定していません。メトロノームで練習")
            acc >= 0.97 && timing >= 0.9 -> tr("แม่นและนิ่ง", "Accurate and steady", "正確で安定")
            else -> tr("ดีแล้ว เก็บรายละเอียดอีกนิด", "Good; polish the details a little more", "よくできました。細部をもう少し")
        }
        return parts.joinToString(" · ") + "\n" + note
    }
}

/** The exams taken, kept in one settings row and merged by sync. */
object ExamLog {
    const val KEY = "exam.log"
    const val KEEP = 200

    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(ExamSitting.serializer())
    fun parse(text: String?): List<ExamSitting> = text?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() } ?: emptyList()
    fun encode(sittings: List<ExamSitting>): String = json.encodeToString(list, sittings.sortedBy { it.startedAt }.takeLast(KEEP))

    fun start(grade: Int, pieces: List<String>, now: Long, day: String, seed: Long): ExamSitting {
        require(pieces.size == 2 && pieces[0] != pieces[1]) { "two different pieces" }
        return ExamSitting("$now-$grade", grade, day, now, pieces, seed)
    }

    /** Still being taken on [today]: started that day, not finished, not given up. */
    fun open(s: ExamSitting, today: String): Boolean = s.day == today && !s.complete && !s.abandoned

    /** Adds a new exam; every other exam still open that day is given up (one exam at a time, even after a sync). */
    fun begin(log: List<ExamSitting>, sitting: ExamSitting): List<ExamSitting> =
        log.map { if (open(it, sitting.day)) it.copy(abandoned = true) else it } + sitting

    /** Records a part once: a part already taken keeps its first marks. */
    fun record(log: List<ExamSitting>, id: String, part: ExamPart, mark: PartMark): List<ExamSitting> =
        log.map { s -> if (s.id == id && part.name !in s.parts && !s.abandoned) s.copy(parts = s.parts + (part.name to mark)) else s }

    /** The exam in progress: started today, not finished, not given up. An exam must be finished on the day it starts. */
    fun active(log: List<ExamSitting>, today: String): ExamSitting? = log.filter { open(it, today) }.maxByOrNull { it.startedAt }

    fun abandon(log: List<ExamSitting>, id: String): List<ExamSitting> = log.map { if (it.id == id) it.copy(abandoned = true) else it }

    /** Finished exams, newest first. */
    fun finished(log: List<ExamSitting>): List<ExamSitting> = log.filter { it.complete && !it.abandoned }.sortedByDescending { it.startedAt }

    /** The best finished result of a grade. */
    fun best(log: List<ExamSitting>, grade: Int): ExamSitting? = finished(log).filter { it.grade == grade }.maxByOrNull { it.total }

    /**
     * Sync: every sitting from both devices; a part taken on both keeps the earlier take. Giving up on either device
     * counts, unless the parts together finish the exam: only an unfinished exam can be given up, so a finished one
     * wins over a copy given up before the other device's last parts arrived.
     */
    fun merge(a: List<ExamSitting>, b: List<ExamSitting>): List<ExamSitting> {
        val byId = LinkedHashMap<String, ExamSitting>()
        for (s in a + b) {
            val old = byId[s.id]
            byId[s.id] = if (old == null) s else {
                val parts = (old.parts.keys + s.parts.keys).associateWith { k ->
                    listOfNotNull(old.parts[k], s.parts[k]).minWith(compareBy<PartMark> { it.at }.thenBy { it.marks })
                }
                old.copy(parts = parts, abandoned = (old.abandoned || s.abandoned) && !ExamPart.entries.all { it.name in parts })
            }
        }
        return byId.values.sortedBy { it.startedAt }.takeLast(KEEP)
    }
}
