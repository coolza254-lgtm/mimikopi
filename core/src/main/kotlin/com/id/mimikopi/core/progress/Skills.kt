package com.id.mimikopi.core.progress

import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.i18n.tr
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The skills the app measures (learning-system.md 2.1, regrouped for the skill page). A level is earned only by a
 * short test with fixed criteria, never by time spent or steps passed, and it is lost again when the test at that
 * level fails twice in a row (the user asked for an honest picture: "ไม่อวย").
 */
enum class SkillId(private val names: Array<String>, private val abouts: Array<String>) {
    KEYS(arrayOf("หาคีย์บนคีย์บอร์ด", "Finding keys", "鍵盤の位置"), arrayOf("รู้ว่าคีย์แต่ละตัวอยู่ตรงไหนโดยไม่ต้องนับ", "Know where each key is without counting", "数えずにどの鍵盤か分かる")),
    NOTES(arrayOf("อ่านโน้ต", "Note reading", "読譜"), arrayOf("ดูโน้ตบนบรรทัดแล้วรู้ทันทีว่าเป็นตัวไหน", "See a note on the staff and know it at once", "五線の音符を見てすぐ分かる")),
    RHYTHM(arrayOf("จังหวะ", "Rhythm", "リズム"), arrayOf("กดตรงจังหวะตามที่เขียนไว้ ตั้งแต่ตัวดำจนถึงซิงโคเพชัน", "Play in time as written, from quarter notes to syncopation", "四分音符からシンコペーションまで、書かれた通りのリズムで")),
    SIGHT(arrayOf("อ่านโน้ตใหม่", "Sight-reading", "初見"), arrayOf("เล่นโน้ตที่ไม่เคยเห็นได้ครั้งแรก ตามจังหวะ", "Play music you have never seen, first time, in time", "初めて見る楽譜をその場でリズム通りに")),
    TECH(arrayOf("นิ้วและสเกล", "Fingers and scales", "指とスケール"), arrayOf("นิ้วเดินสม่ำเสมอ สเกลมือเดียวและสองมือ", "Even fingers, scales hands separately and together", "均等な指づかい、片手と両手のスケール")),
    EAR(arrayOf("หู", "Ear", "耳"), arrayOf("ฟังแล้วบอกโน้ต ทำนอง คอร์ด และทางเดินคอร์ดได้ เพื่อแกะเพลงเอง", "Hear notes, melodies, chords and progressions, to play songs by ear", "音・メロディ・コード・進行を聞き取り、耳コピできる")),
    CHORDS(arrayOf("คอร์ด", "Chords", "コード"), arrayOf("เห็นชื่อคอร์ดแล้วกดได้ และเปลี่ยนคอร์ดได้ทัน", "See a chord name and play it, change chords in time", "コード名を見て押さえ、遅れずに切り替える")),
    SOLO(arrayOf("เล่นเดี่ยวสองมือ", "Two-hand solo", "両手ソロ"), arrayOf("ทำนองมือขวากับรูปแบบประกอบมือซ้าย เล่นคนเดียวแบบ piano solo", "Right-hand melody over a left-hand pattern, played alone as a piano solo", "右手メロディと左手伴奏をひとりで弾くピアノソロ"));

    val title: String get() = tr(names[0], names[1], names[2])
    val about: String get() = tr(abouts[0], abouts[1], abouts[2])
}

/** How a test is run: a drill (key finding, note reading, chords), a score in the reader, or an ear level. */
sealed interface CheckRun {
    data class DrillRun(val drill: Drill) : CheckRun
    data class ScoreRun(val drill: Drill) : CheckRun
    data class EarRun(val levelId: String) : CheckRun
}

/** What a test must reach. Every criterion given must be met. */
data class Criteria(
    /** First-try accuracy (drills, ear) or notes right (scores), 0..1. */
    val minAccuracy: Double? = null,
    /** Median answer time in seconds. */
    val maxMedianSec: Double? = null,
    /** Notes within the timing tolerance, 0..1 (scores). */
    val minTiming: Double? = null,
    /** Chord changes in the time given. */
    val minCount: Int? = null,
)

/** What a test measured. */
@Serializable
data class CheckMetrics(val accuracy: Double? = null, val medianSec: Double? = null, val timing: Double? = null, val count: Int? = null)

data class SkillLevel(val skill: SkillId, val level: Int, val canDo: String, val run: CheckRun, val criteria: Criteria) {
    /** The test judges timing (a score in tempo, or ear rhythm taps), so it needs a piano on a cable. */
    val judgesTiming: Boolean get() = when (val r = run) {
        is CheckRun.ScoreRun -> r.drill.judgesTiming
        is CheckRun.DrillRun -> r.drill.judgesTiming
        is CheckRun.EarRun -> com.id.mimikopi.core.ear.EarCourse.test(level)?.items.orEmpty().any { com.id.mimikopi.core.ear.EarCourse.kindOf(it) == com.id.mimikopi.core.ear.EarKind.RHYTHM }
    }

    /** The criteria in words ("ถูก ≥ 90% · เฉลี่ยข้อละ ≤ 2.5 วินาที"). */
    fun criteriaText(): String = listOfNotNull(
        criteria.minAccuracy?.let { tr("ถูก", "Correct", "正解") + " ≥ ${pct(it)}" },
        criteria.minTiming?.let { tr("ตรงจังหวะ", "In time", "タイミング") + " ≥ ${pct(it)}" },
        criteria.maxMedianSec?.let { tr("ตอบเร็วกว่า ${sec(it)} ต่อข้อ (ค่ากลาง)", "Answer within ${sec(it)} each (median)", "1問 ${sec(it)} 以内（中央値）") },
        criteria.minCount?.let { tr("เปลี่ยนคอร์ดถูก ≥ $it ครั้ง", "Chord changes ≥ $it", "コードチェンジ ≥ $it 回") },
    ).joinToString(" · ")

    /** Judges a run: passed, and one line per criterion with the number reached. */
    fun judge(m: CheckMetrics): Verdict {
        val lines = mutableListOf<String>()
        var ok = true
        fun line(name: String, got: String, need: String, met: Boolean) {
            ok = ok && met
            lines += "$name $got (" + tr("ต้อง", "need", "目標") + " $need) · " + if (met) tr("ผ่าน", "passed", "合格") else tr("ยังไม่ผ่าน", "not yet", "未達")
        }
        criteria.minAccuracy?.let { need -> val got = m.accuracy ?: 0.0; line(tr("ถูก", "Correct", "正解"), pct(got), "≥ ${pct(need)}", got + 1e-9 >= need) }
        criteria.minTiming?.let { need -> val got = m.timing ?: 0.0; line(tr("ตรงจังหวะ", "In time", "タイミング"), pct(got), "≥ ${pct(need)}", got + 1e-9 >= need) }
        criteria.maxMedianSec?.let { need -> val got = m.medianSec; line(tr("ความเร็ว", "Speed", "速さ"), got?.let { sec(it) } ?: "-", "≤ ${sec(need)}", got != null && got <= need + 1e-9) }
        criteria.minCount?.let { need -> val got = m.count ?: 0; line(tr("เปลี่ยนคอร์ด", "Chord changes", "コードチェンジ"), tr("$got ครั้ง", "$got", "$got 回"), "≥ $need", got >= need) }
        return Verdict(ok, lines)
    }

    companion object {
        fun pct(x: Double) = "${Math.round(x * 100)}%"
        fun sec(x: Double) = tr("%.1f วินาที", "%.1f s", "%.1f 秒").format(x)
    }
}

data class Verdict(val passed: Boolean, val lines: List<String>)

/** One test taken. */
@Serializable
data class CheckRecord(
    val skill: SkillId,
    val level: Int,
    val date: String,
    val passed: Boolean,
    val metrics: CheckMetrics = CheckMetrics(),
    /** When it was taken (epoch ms; older records have none): orders tests of one day across devices. */
    val at: Long? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val list = ListSerializer(serializer())
        fun parse(text: String?): List<CheckRecord> = text?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() } ?: emptyList()
        fun encode(records: List<CheckRecord>): String = json.encodeToString(list, records.takeLast(600))
    }
}

/** Where a skill stands, from its test records only. */
data class SkillStatus(
    val skill: SkillId,
    /** 0 = no level yet (never passed level 1). */
    val level: Int,
    val maxLevel: Int,
    val lastTested: LocalDate?,
    /** The level to test next (null when the top is held and not due for a re-test). */
    val nextTest: Int?,
    /** The next test is due today (never tested, or a week since the last test). */
    val due: Boolean,
    /** No more attempts at [nextTest] today (two failed already). */
    val lockedToday: Boolean,
    /** The level was lost: the current level's test failed twice in a row after it was earned. */
    val dropped: Boolean,
    /** Not open yet: the skills it builds on are not held ([SkillBook.opensWith]); no test is suggested. */
    val locked: Boolean = false,
) {
    val fraction: Double get() = level.toDouble() / maxLevel
}

/**
 * The levels of every skill, each a short test of 1-3 minutes. New material every time (seeded by the run), the same
 * difficulty. Criteria are my choices, set so a level means what its sentence says; they are not from an outside exam.
 */
object SkillBook {
    /** A week between tests of a skill (one try to move up), and a re-test of a held level after four weeks. */
    const val RETEST_DAYS = 7L
    const val HOLD_DAYS = 28L
    /** Failed tries of one level per day: more would let luck decide the level. */
    const val TRIES_PER_DAY = 2

    private val WHITE = listOf("C", "D", "E", "F", "G", "A", "B")

    private fun keyfind(exact: Boolean) = CheckRun.DrillRun(
        Drill(ExerciseType.E_KEYFIND, tr("ทดสอบ: ", "Test: ", "テスト: ") + if (exact) tr("หาคีย์พร้อมอ็อกเทฟ", "find keys with octave", "オクターブ付きで鍵盤を探す") else tr("หาคีย์", "find keys", "鍵盤を探す"), items = 12, letters = WHITE, exactOctave = exact),
    )

    /** Natural notes from [low] to [high] on a clef ("C4:treble" ...). */
    private fun naturals(low: String, high: String, clef: String): List<String> {
        fun index(n: String) = n.drop(1).toInt() * 7 + WHITE.indexOf(n.take(1))
        return (index(low)..index(high)).map { i -> "${WHITE[i % 7]}${i / 7}:$clef" }
    }

    private val ACCIDENTALS = listOf("F#4:treble", "Bb4:treble", "C#5:treble", "Eb5:treble", "G#4:treble", "Ab4:treble", "F#3:bass", "Bb2:bass", "C#3:bass", "Eb3:bass")

    private fun noteid(title: String, pool: List<String>) =
        CheckRun.DrillRun(Drill(ExerciseType.E_NOTEID, tr("ทดสอบ: ", "Test: ", "テスト: ") + title, items = 20, pool = pool.distinct()))

    private fun rhythm(title: String, rhythms: List<String>, bpm: Int, left: List<String> = emptyList()) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_RHYTHM_TAP, tr("ทดสอบ: ", "Test: ", "テスト: ") + title, items = 1, rhythms = rhythms, rhythmsLeft = left, hand = if (left.isEmpty()) "RIGHT" else "BOTH",
            bars = 4, mode = "TEMPO", bpm = bpm),
    )

    private fun sight(level: Int, bpm: Int, hand: String = "RIGHT", keys: List<Int> = emptyList()) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_READ_PIECE, tr("ทดสอบ: ", "Test: ", "テスト: ") + tr("อ่านโน้ตใหม่", "sight-reading", "初見"), items = 1, level = "L$level", hand = hand, keys = keys, mode = "TEMPO", bpm = bpm),
    )

    private fun fingers(hand: String) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_FINGER_PATTERN, tr("ทดสอบ: ", "Test: ", "テスト: ") + tr("รูปแบบห้านิ้ว", "five-finger patterns", "5指パターン"), items = 8, hand = hand, mode = "TEMPO", bpm = 60),
    )

    private fun scale(piece: String, hand: String, bpm: Int, title: String) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_PIECE, tr("ทดสอบ: ", "Test: ", "テスト: ") + title, items = 1, pieces = listOf(piece), hand = hand, mode = "TEMPO", bpm = bpm),
    )

    /** An ear test of the ear course (EarCourse.TESTS). */
    private fun ear(level: Int) = CheckRun.EarRun("ear-test-$level")

    private fun chords(p: ChordPractice) = CheckRun.DrillRun(p.drill().copy(title = tr("ทดสอบ: ", "Test: ", "テスト: ") + p.summary()))

    private val ALL_KEYS = ChordPractice.KEYS

    /**
     * Melody and accompaniment together (learning-system.md 2.12): an app-made melody over a progression, the left
     * hand in one pattern of the 8.2 ladder, both hands played, in tempo. Keys move to the flat side the target
     * songs use (A♭, E♭, B♭, E) and tempos towards the songs' 100+.
     */
    private fun solo(pattern: Int, progression: String, keys: List<Int>, bpm: Int, bars: Int = 4) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_COMP_PATTERN, tr("ทดสอบ: ", "Test: ", "テスト: ") + tr("สองมือ ", "two hands, ", "両手 ") + com.id.mimikopi.core.theory.Accompaniment.LEVEL_NAMES.getValue(pattern),
            items = 1, hand = "BOTH", mode = "TEMPO", bpm = bpm, bars = bars, keys = keys, progression = progression, pattern = pattern),
    )

    private val ROYAL = "ทางหลวง (王道進行)"

    private val SCORE = Criteria(minAccuracy = 0.95, minTiming = 0.85)
    private val READING = Criteria(minAccuracy = 0.9, minTiming = 0.8)

    // Built on each use, so the texts follow the language in use.
    private val LEVELS: Map<SkillId, List<SkillLevel>> get() = mapOf(
        SkillId.KEYS to listOf(
            SkillLevel(SkillId.KEYS, 1, tr("หาคีย์ขาวทั้ง 7 ตัวได้ (อ็อกเทฟไหนก็ได้)", "Find all 7 white keys (any octave)", "白鍵7つを見つける（どのオクターブでも）"), keyfind(false), Criteria(0.9, 4.0)),
            SkillLevel(SkillId.KEYS, 2, tr("หาคีย์ขาวได้เร็วโดยไม่ต้องนับ", "Find white keys fast without counting", "数えずに白鍵をすばやく見つける"), keyfind(false), Criteria(0.9, 2.5)),
            SkillLevel(SkillId.KEYS, 3, tr("หาคีย์ตามชื่อพร้อมอ็อกเทฟ เช่น G4 ได้", "Find a key by name with octave, like G4", "G4のようにオクターブ付きで鍵盤を見つける"), keyfind(true), Criteria(0.9, 3.5)),
            SkillLevel(SkillId.KEYS, 4, tr("หาคีย์พร้อมอ็อกเทฟได้แม่นและเร็ว", "Find keys with octave accurately and fast", "オクターブ付きで正確にすばやく"), keyfind(true), Criteria(0.95, 2.5)),
            SkillLevel(SkillId.KEYS, 5, tr("หาคีย์พร้อมอ็อกเทฟได้แทบทันที", "Find keys with octave almost instantly", "オクターブ付きでほぼ即座に"), keyfind(true), Criteria(0.95, 1.5)),
        ),
        SkillId.NOTES to listOf(
            SkillLevel(SkillId.NOTES, 1, tr("อ่านโน้ต 5 ตัวแรกบนกุญแจซอล (C4–G4)", "Read the first 5 notes on the treble clef (C4–G4)", "ト音記号の最初の5音を読む（C4–G4）"), noteid("C4–G4", naturals("C4", "G4", "treble")), Criteria(0.9, 4.0)),
            SkillLevel(SkillId.NOTES, 2, tr("อ่านกุญแจซอลช่วง C4–C5", "Read the treble clef from C4 to C5", "ト音記号 C4–C5 を読む"), noteid("C4–C5", naturals("C4", "C5", "treble")), Criteria(0.9, 3.0)),
            SkillLevel(SkillId.NOTES, 3, tr("อ่านได้ทั้งสองกุญแจในช่วงกลาง (กุญแจซอล C4–G5, กุญแจฟา C3–C4)", "Read both clefs in the middle range (treble C4–G5, bass C3–C4)", "両方の音部記号の中音域（ト音 C4–G5、ヘ音 C3–C4）"),
                noteid(tr("สองกุญแจช่วงกลาง", "both clefs, middle", "両音部記号・中音域"), naturals("C4", "G5", "treble") + naturals("C3", "C4", "bass")), Criteria(0.9, 3.0)),
            SkillLevel(SkillId.NOTES, 4, tr("อ่านได้เต็มบรรทัดทั้งสองกุญแจ (C2–C6)", "Read the whole staff in both clefs (C2–C6)", "両方の音部記号で五線全体（C2–C6）"),
                noteid(tr("เต็มบรรทัด", "whole staff", "五線全体"), naturals("C4", "C6", "treble") + naturals("C2", "C4", "bass")), Criteria(0.9, 2.5)),
            SkillLevel(SkillId.NOTES, 5, tr("อ่านเส้นน้อยเหนือและใต้บรรทัดได้คล่อง", "Read ledger lines above and below easily", "上下の加線をすらすら読む"),
                noteid(tr("รวมเส้นน้อย", "with ledger lines", "加線あり"), naturals("A3", "C6", "treble") + naturals("C2", "E4", "bass")), Criteria(0.9, 2.0)),
            SkillLevel(SkillId.NOTES, 6, tr("อ่านโน้ตที่มีชาร์ปและแฟลตได้", "Read notes with sharps and flats", "シャープ・フラット付きの音を読む"),
                noteid(tr("รวมชาร์ปแฟลต", "with sharps and flats", "シャープ・フラットあり"), naturals("C4", "C6", "treble") + naturals("C2", "C4", "bass") + ACCIDENTALS), Criteria(0.9, 2.0)),
            SkillLevel(SkillId.NOTES, 7, tr("อ่านโน้ตได้แทบทันทีทั้งบรรทัดคู่", "Read the grand staff almost instantly", "大譜表をほぼ即座に読む"),
                noteid(tr("ทั้งบรรทัดคู่", "grand staff", "大譜表"), naturals("A3", "C6", "treble") + naturals("C2", "E4", "bass") + ACCIDENTALS), Criteria(0.95, 1.3)),
        ),
        SkillId.RHYTHM to listOf(
            SkillLevel(SkillId.RHYTHM, 1, tr("ตัวดำ ตัวขาว ตัวกลม และตัวหยุด ตรงจังหวะ", "Quarter, half and whole notes and rests, in time", "四分・二分・全音符と休符をリズム通りに"), rhythm(tr("ตัวดำ ตัวขาว ตัวกลม", "quarter, half, whole notes", "四分・二分・全音符"), listOf("q", "h", "w", "qr", "hr"), 70), SCORE),
            SkillLevel(SkillId.RHYTHM, 2, tr("เขบ็ตหนึ่งชั้นและโน้ตประจุด", "Eighth notes and dotted notes", "八分音符と付点音符"), rhythm(tr("เขบ็ตและโน้ตประจุด", "eighths and dotted notes", "八分と付点"), listOf("q", "ee", "h", "q.e", "h.", "qr"), 66), SCORE),
            SkillLevel(SkillId.RHYTHM, 3, tr("จังหวะย่อย 8 แบบ (เขบ็ตสองชั้น)", "The 8 beat divisions (sixteenths)", "8種類の拍の分割（十六分音符）"), rhythm(tr("จังหวะย่อย 8 แบบ", "8 beat divisions", "8種類の分割"), listOf("q", "ee", "ssss", "ess", "sse", "e.s", "ses", "re"), 56), SCORE),
            SkillLevel(SkillId.RHYTHM, 4, tr("ซิงโคเพชัน (เน้นนอกจังหวะ)", "Syncopation (off-beat accents)", "シンコペーション（裏拍のアクセント）"), rhythm(tr("ซิงโคเพชัน", "syncopation", "シンコペーション"), listOf("q", "ee", "eqe", "re", "h"), 66), SCORE),
            SkillLevel(SkillId.RHYTHM, 5, tr("สองมือคนละจังหวะพร้อมกัน", "Different rhythms in each hand at once", "左右で違うリズムを同時に"), rhythm(tr("สองมือคนละจังหวะ", "different rhythm per hand", "左右別リズム"), listOf("q", "ee", "h", "qr"), 66, left = listOf("h", "q", "w")), SCORE),
            SkillLevel(SkillId.RHYTHM, 6, tr("จังหวะย่อยที่ความเร็ว 72", "Beat divisions at 72", "テンポ72で拍の分割"), rhythm(tr("จังหวะย่อยเร็วขึ้น", "faster divisions", "速い分割"), listOf("q", "ee", "ssss", "ess", "sse", "e.s", "ses", "re", "eqe"), 72), SCORE),
        ),
        SkillId.SIGHT to listOf(
            SkillLevel(SkillId.SIGHT, 1, tr("เล่นทำนองง่ายมือขวาที่ไม่เคยเห็นได้ในรอบแรก", "Play an easy new right-hand melody first time", "初めての右手メロディを一回で弾く"), sight(1, 60), READING),
            SkillLevel(SkillId.SIGHT, 2, tr("เช่นเดียวกันด้วยมือซ้าย", "The same with the left hand", "左手で同じく"), sight(2, 60), READING),
            SkillLevel(SkillId.SIGHT, 3, tr("ทำนองที่มีเขบ็ต จังหวะ 3/4 และตำแหน่ง G", "Melodies with eighths, 3/4 time and G position", "八分音符・3拍子・Gポジションのメロディ"), sight(3, 66), READING),
            SkillLevel(SkillId.SIGHT, 4, tr("ทำนองที่กระโดดไกลและย้ายมือ", "Melodies with leaps and hand moves", "跳躍と手の移動があるメロディ"), sight(4, 60), READING),
            SkillLevel(SkillId.SIGHT, 5, tr("อ่านสองมือพร้อมกันแบบง่าย", "Read both hands together, simply", "簡単な両手の初見"), sight(5, 56), READING),
            SkillLevel(SkillId.SIGHT, 6, tr("สองมือในคีย์ที่มีชาร์ป/แฟลต", "Both hands in keys with sharps or flats", "シャープ・フラットの調で両手"), sight(6, 66, keys = listOf(0, 1, -1, 2, -2)), READING),
            SkillLevel(SkillId.SIGHT, 7, tr("สองมือที่มีคอร์ดซ้อน", "Both hands with chords", "和音のある両手"), sight(7, 60), READING),
        ),
        SkillId.TECH to listOf(
            SkillLevel(SkillId.TECH, 1, tr("รูปแบบห้านิ้วมือขวาสม่ำเสมอที่ 60", "Even five-finger patterns, right hand, at 60", "右手の5指パターンを均等に（60）"), fingers("RIGHT"), SCORE),
            SkillLevel(SkillId.TECH, 2, tr("รูปแบบห้านิ้วมือซ้ายสม่ำเสมอที่ 60", "Even five-finger patterns, left hand, at 60", "左手の5指パターンを均等に（60）"), fingers("LEFT"), SCORE),
            SkillLevel(SkillId.TECH, 3, tr("สเกล C มือขวา (ลอดนิ้วโป้ง) ที่ 72", "C scale, right hand (thumb under), at 72", "Cスケール右手（親指くぐり）72"), scale("ex-c-major-scale-rh", "RIGHT", 72, tr("สเกล C มือขวา", "C scale, right hand", "Cスケール右手")), SCORE),
            SkillLevel(SkillId.TECH, 4, tr("สเกล C มือซ้ายที่ 72", "C scale, left hand, at 72", "Cスケール左手 72"), scale("ex-c-major-scale-lh", "LEFT", 72, tr("สเกล C มือซ้าย", "C scale, left hand", "Cスケール左手")), SCORE),
            SkillLevel(SkillId.TECH, 5, tr("สเกล G สองมือพร้อมกันที่ 66", "G scale, hands together, at 66", "Gスケール両手 66"), scale("ex-scale-g-both", "BOTH", 66, tr("สเกล G สองมือ", "G scale, hands together", "Gスケール両手")), SCORE),
            SkillLevel(SkillId.TECH, 6, tr("สเกล D สองมือพร้อมกันที่ 80", "D scale, hands together, at 80", "Dスケール両手 80"), scale("ex-scale-d-both", "BOTH", 80, tr("สเกล D สองมือ", "D scale, hands together", "Dスケール両手")), SCORE),
            SkillLevel(SkillId.TECH, 7, tr("สเกล B♭ สองมือพร้อมกันที่ 80", "B♭ scale, hands together, at 80", "B♭スケール両手 80"), scale("ex-scale-bb-both", "BOTH", 80, tr("สเกล B♭ สองมือ", "B♭ scale, hands together", "B♭スケール両手")), SCORE),
        ),
        SkillId.EAR to listOf(
            SkillLevel(SkillId.EAR, 1, tr("บอกโน้ต ド レ ミ ファ ソ ในคีย์ C ได้", "Name ド レ ミ ファ ソ (do re mi fa so) in C", "Cの ド レ ミ ファ ソ を聞き分ける"), ear(1), Criteria(minAccuracy = 0.9)),
            SkillLevel(SkillId.EAR, 2, tr("บอกโน้ตครบทั้ง 7 ตัวของคีย์ C ได้", "Name all 7 notes of C", "Cの7音すべてを聞き分ける"), ear(2), Criteria(minAccuracy = 0.85)),
            SkillLevel(SkillId.EAR, 3, tr("แกะทำนอง 3 โน้ต และแยกคอร์ดเมเจอร์กับไมเนอร์", "Copy 3-note melodies, tell major from minor", "3音のメロディを耳コピ、長短を聞き分ける"), ear(3), Criteria(minAccuracy = 0.8)),
            SkillLevel(SkillId.EAR, 4, tr("บอกโน้ตและเบส I IV V ได้ในหลายคีย์", "Notes and I IV V bass in several keys", "複数の調で音とI IV Vのベース"), ear(4), Criteria(minAccuracy = 0.85)),
            SkillLevel(SkillId.EAR, 5, tr("แกะทำนอง 4 โน้ต และฟังออกว่าเป็น Axis โกมุโร หรือ 50s", "Copy 4-note melodies, hear Axis, Komuro or 50s", "4音のメロディ、Axis・小室・50s進行を聞き分ける"), ear(5), Criteria(minAccuracy = 0.8)),
            SkillLevel(SkillId.EAR, 6, tr("ฟังคอร์ดเซเวนท์ และ 王道進行 ออก", "Hear seventh chords and 王道進行 (Ōdō shinkō)", "セブンスコードと王道進行を聞き分ける"), ear(6), Criteria(minAccuracy = 0.8)),
            SkillLevel(SkillId.EAR, 7, tr("แกะทำนอง 5–6 โน้ตได้ทุกคีย์ และจับการขึ้นคีย์ได้", "Copy 5–6-note melodies in any key, catch key changes", "全調で5–6音のメロディ、転調を聞き取る"), ear(7), Criteria(minAccuracy = 0.75)),
            SkillLevel(SkillId.EAR, 8, tr("แกะทำนองยาวแบบเพลงจริง เบสอินเวอร์ชัน และ Canon", "Copy song-length melodies, inverted basses and Canon", "実際の曲の長さのメロディ、転回ベース、カノン進行"), ear(8), Criteria(minAccuracy = 0.7)),
        ),
        SkillId.CHORDS to listOf(
            SkillLevel(SkillId.CHORDS, 1, tr("กดคอร์ดเมเจอร์/ไมเนอร์ในคีย์ C G F ได้", "Play major/minor chords in C, G, F", "C・G・Fの長三和音・短三和音"), chords(ChordPractice(ChordPractice.Mode.NAME, listOf(0, 1, -1), listOf("triads"), items = 10)), Criteria(0.9, 6.0)),
            SkillLevel(SkillId.CHORDS, 2, tr("คอร์ดเมเจอร์/ไมเนอร์ทุกคีย์ได้เร็ว", "Major/minor chords in every key, fast", "全調の長短三和音をすばやく"), chords(ChordPractice(ChordPractice.Mode.NAME, ALL_KEYS, listOf("triads"), items = 12)), Criteria(0.9, 4.0)),
            SkillLevel(SkillId.CHORDS, 3, tr("รู้คอร์ดในคีย์ (I–vii°) ของ C G F", "Know the chords of C, G, F (I–vii°)", "C・G・Fのダイアトニックコード（I–vii°）"), chords(ChordPractice(ChordPractice.Mode.NUMERAL, listOf(0, 1, -1), numeralSets = listOf("diatonic"), items = 12)), Criteria(0.9, 5.0)),
            SkillLevel(SkillId.CHORDS, 4, tr("เปลี่ยนคอร์ด I–V–vi–IV ในคีย์ C ได้คล่อง", "Change I–V–vi–IV in C smoothly", "CでI–V–vi–IVをスムーズに"), chords(ChordPractice(ChordPractice.Mode.CHANGE, listOf(0), progression = "Axis", seconds = 60)), Criteria(minCount = 16)),
            SkillLevel(SkillId.CHORDS, 5, tr("คอร์ด 7 (7, maj7, m7) ในหลายคีย์", "Seventh chords (7, maj7, m7) in several keys", "セブンスコード（7・maj7・m7）を複数の調で"), chords(ChordPractice(ChordPractice.Mode.NAME, listOf(0, 1, -1, 2, -2), listOf("sevenths"), items = 12)), Criteria(0.9, 5.0)),
            SkillLevel(SkillId.CHORDS, 6, tr("เปลี่ยนคอร์ด Canon ในคีย์ G ได้คล่อง", "Change the Canon chords in G smoothly", "Gでカノン進行をスムーズに"), chords(ChordPractice(ChordPractice.Mode.CHANGE, listOf(1), progression = "Canon", seconds = 60)), Criteria(minCount = 20)),
            SkillLevel(SkillId.CHORDS, 7, tr("เปลี่ยนคอร์ด ii–V–I (คอร์ด 7) ในคีย์ F", "Change ii–V–I (sevenths) in F", "Fでii–V–I（セブンス）"), chords(ChordPractice(ChordPractice.Mode.CHANGE, listOf(-1), progression = "ii–V–I", seconds = 60)), Criteria(minCount = 15)),
        ),
        SkillId.SOLO to listOf(
            SkillLevel(SkillId.SOLO, 1, tr("เล่นทำนองง่ายพร้อมโน้ตรากยาวมือซ้าย คีย์ C", "Easy melody over long left-hand roots, in C", "左手の長いルート音とメロディ（C）"), solo(1, "Axis", listOf(0), 60), READING),
            SkillLevel(SkillId.SOLO, 2, tr("ทำนองกับอาร์เพจจิโอ 1-5-8 คีย์ C G F", "Melody with a 1-5-8 arpeggio in C, G, F", "1-5-8アルペジオとメロディ（C・G・F）"), solo(2, "Axis", listOf(0, 1, -1), 60), READING),
            SkillLevel(SkillId.SOLO, 3, tr("ทำนองกับคอร์ดบล็อก คีย์ C G F", "Melody with block chords in C, G, F", "ブロックコードとメロディ（C・G・F）"), solo(3, "โกมุโร", listOf(0, 1, -1), 66), READING),
            SkillLevel(SkillId.SOLO, 4, tr("ทำนองกับคอร์ดทุกจังหวะ คีย์ฝั่งแฟลต F B♭ E♭", "Melody with chords on every beat, flat keys F B♭ E♭", "毎拍のコードとメロディ（F・B♭・E♭）"), solo(4, "วงจร 50s", listOf(-1, -2, -3), 72), READING),
            SkillLevel(SkillId.SOLO, 5, tr("เบสอ็อกเทฟกับคอร์ดจังหวะยกใน 王道進行 คีย์ A♭ E♭", "Octave bass and off-beat chords in 王道進行 (Ōdō shinkō), A♭ E♭", "王道進行でオクターブベースと裏拍コード（A♭・E♭）"), solo(5, ROYAL, listOf(-4, -3), 80), READING),
            SkillLevel(SkillId.SOLO, 6, tr("อาร์เพจจิโอ 8 ตัวไหลต่อเนื่อง คีย์ A♭ E♭ B♭ E", "Flowing 8-note arpeggios in A♭ E♭ B♭ E", "流れる8音アルペジオ（A♭・E♭・B♭・E）"), solo(6, ROYAL, listOf(-4, -3, -2, 4), 88), READING),
            SkillLevel(SkillId.SOLO, 7, tr("ซิงโคเพชัน 16 บีตที่ความเร็วเพลงจริง 100", "16-beat syncopation at song tempo 100", "実際の曲のテンポ100で16ビートのシンコペーション"), solo(7, "Canon", listOf(-4, -3, 4, 0), 100, bars = 8), READING),
        ),
    )

    /**
     * What a skill builds on before its tests are offered: two-hand solo playing needs simple two-hand reading
     * (new reading level 5) and the first chord level. A skill already tested stays open.
     */
    val opensWith: Map<SkillId, Map<SkillId, Int>> = mapOf(SkillId.SOLO to mapOf(SkillId.SIGHT to 5, SkillId.CHORDS to 1))

    /** When a closed skill opens, in words (no level numbers: the user asked for none), or null for one open from the start. */
    fun opensText(skill: SkillId): String? = when (skill) {
        SkillId.SOLO -> tr("เปิดเมื่ออ่านสองมือได้และกดคอร์ดพื้นฐานได้", "Opens once you can read both hands and play basic chords", "両手の初見と基本のコードができたら開きます")
        else -> null
    }

    fun levels(skill: SkillId): List<SkillLevel> = LEVELS.getValue(skill)
    fun level(skill: SkillId, n: Int): SkillLevel? = levels(skill).getOrNull(n - 1)
    fun maxLevel(skill: SkillId): Int = levels(skill).size

    /** "KEYS:3" for navigation, and back. */
    fun key(skill: SkillId, level: Int) = "${skill.name}:$level"
    fun parseKey(text: String?): SkillLevel? {
        val parts = text?.split(':') ?: return null
        val skill = runCatching { SkillId.valueOf(parts[0]) }.getOrNull() ?: return null
        return parts.getOrNull(1)?.toIntOrNull()?.let { level(skill, it) }
    }

    /**
     * A skill's level from its records: the highest level passed, unless its test has since failed twice in a row
     * (then one lower, checked again). The next test is one level up, or the held top after [HOLD_DAYS].
     */
    fun status(skill: SkillId, records: List<CheckRecord>, today: LocalDate): SkillStatus {
        val mine = records.filter { it.skill == skill }
        val locked = mine.isEmpty() && opensWith[skill].orEmpty().any { (s, n) -> status(s, records, today).level < n }
        val max = maxLevel(skill)
        var level = mine.filter { it.passed }.maxOfOrNull { it.level } ?: 0
        var dropped = false
        while (level > 0) {
            val atLevel = mine.filter { it.level == level }
            val lastPass = atLevel.indexOfLast { it.passed }
            val after = atLevel.drop(lastPass + 1)
            if (after.size >= 2 && after.takeLast(2).none { it.passed }) { level--; dropped = true } else break
        }
        val last = mine.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull()
        val sinceLast = last?.let { ChronoUnit.DAYS.between(it, today) }
        val lastAtLevel = mine.filter { it.level == level }.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull()
        val next = when {
            level < max -> level + 1
            lastAtLevel == null || ChronoUnit.DAYS.between(lastAtLevel, today) >= HOLD_DAYS -> max
            else -> null
        }
        val failsToday = next?.let { n -> mine.count { it.level == n && it.date == today.toString() && !it.passed } } ?: 0
        val due = next != null && (sinceLast == null || sinceLast >= RETEST_DAYS || (level == max && next == max))
        return SkillStatus(skill, level, max, last, next, !locked && due && failsToday < TRIES_PER_DAY, failsToday >= TRIES_PER_DAY, dropped, locked)
    }

    /** The test to suggest today: a never-tested skill first (placement), then the one tested longest ago. */
    fun dueCheck(records: List<CheckRecord>, today: LocalDate): SkillStatus? =
        SkillId.entries.map { status(it, records, today) }.filter { it.due && it.nextTest != null }
            .minWithOrNull(compareBy<SkillStatus> { it.lastTested != null }.thenBy { it.lastTested ?: LocalDate.MIN })

    /** The weakest measured skill (lowest share of its levels), to give it more practice time. */
    fun weakest(records: List<CheckRecord>, today: LocalDate): SkillId? =
        SkillId.entries.map { status(it, records, today) }.filter { it.lastTested != null }.minByOrNull { it.fraction }?.skill

    /**
     * An honest one-line summary over the measured skills (inside this app's levels, not an exam grade).
     */
    fun overall(records: List<CheckRecord>, today: LocalDate): String? {
        val measured = SkillId.entries.map { status(it, records, today) }.filter { it.lastTested != null }
        if (measured.isEmpty()) return null
        val avg = measured.map { it.fraction }.average()
        return when {
            avg < 0.15 -> tr("เพิ่งเริ่มต้น", "Just starting", "始めたばかり")
            avg < 0.35 -> tr("มือใหม่", "Beginner", "初心者")
            avg < 0.55 -> tr("มือใหม่ที่เริ่มเล่นเพลงง่ายได้", "Beginner playing easy songs", "簡単な曲が弾ける初心者")
            avg < 0.8 -> tr("ระดับกลางช่วงต้น", "Early intermediate", "初中級")
            else -> tr("ระดับกลาง", "Intermediate", "中級")
        }
    }
}
