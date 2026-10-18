package com.id.mimikopi.core.progress

import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
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
enum class SkillId(val title: String, val about: String) {
    KEYS("หาคีย์บนคีย์บอร์ด", "รู้ว่าคีย์แต่ละตัวอยู่ตรงไหนโดยไม่ต้องนับ"),
    NOTES("อ่านโน้ต", "ดูโน้ตบนบรรทัดแล้วรู้ทันทีว่าเป็นตัวไหน"),
    RHYTHM("จังหวะ", "กดตรงจังหวะตามที่เขียนไว้ ตั้งแต่ตัวดำจนถึงซิงโคเพชัน"),
    SIGHT("อ่านโน้ตใหม่", "เล่นโน้ตที่ไม่เคยเห็นได้ครั้งแรก ตามจังหวะ"),
    TECH("นิ้วและสเกล", "นิ้วเดินสม่ำเสมอ สเกลมือเดียวและสองมือ"),
    EAR("หู", "ฟังแล้วบอกได้ว่าโน้ตเป็นตัวไหนในคีย์"),
    CHORDS("คอร์ด", "เห็นชื่อคอร์ดแล้วกดได้ และเปลี่ยนคอร์ดได้ทัน"),
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
    /** The criteria in words ("ถูก ≥ 90% · เฉลี่ยข้อละ ≤ 2.5 วินาที"). */
    fun criteriaText(): String = listOfNotNull(
        criteria.minAccuracy?.let { "ถูก ≥ ${pct(it)}" },
        criteria.minTiming?.let { "ตรงจังหวะ ≥ ${pct(it)}" },
        criteria.maxMedianSec?.let { "ตอบเร็วกว่า ${sec(it)} ต่อข้อ (ค่ากลาง)" },
        criteria.minCount?.let { "เปลี่ยนคอร์ดถูก ≥ $it ครั้ง" },
    ).joinToString(" · ")

    /** Judges a run: passed, and one line per criterion with the number reached. */
    fun judge(m: CheckMetrics): Verdict {
        val lines = mutableListOf<String>()
        var ok = true
        fun line(name: String, got: String, need: String, met: Boolean) {
            ok = ok && met
            lines += "$name $got (ต้อง $need) · " + if (met) "ผ่าน" else "ยังไม่ผ่าน"
        }
        criteria.minAccuracy?.let { need -> val got = m.accuracy ?: 0.0; line("ถูก", pct(got), "≥ ${pct(need)}", got + 1e-9 >= need) }
        criteria.minTiming?.let { need -> val got = m.timing ?: 0.0; line("ตรงจังหวะ", pct(got), "≥ ${pct(need)}", got + 1e-9 >= need) }
        criteria.maxMedianSec?.let { need -> val got = m.medianSec; line("ความเร็ว", got?.let { sec(it) } ?: "-", "≤ ${sec(need)}", got != null && got <= need + 1e-9) }
        criteria.minCount?.let { need -> val got = m.count ?: 0; line("เปลี่ยนคอร์ด", "$got ครั้ง", "≥ $need", got >= need) }
        return Verdict(ok, lines)
    }

    companion object {
        fun pct(x: Double) = "${Math.round(x * 100)}%"
        fun sec(x: Double) = "%.1f วินาที".format(x)
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
        Drill(ExerciseType.E_KEYFIND, if (exact) "ทดสอบ: หาคีย์พร้อมอ็อกเทฟ" else "ทดสอบ: หาคีย์", items = 12, letters = WHITE, exactOctave = exact),
    )

    /** Natural notes from [low] to [high] on a clef ("C4:treble" ...). */
    private fun naturals(low: String, high: String, clef: String): List<String> {
        fun index(n: String) = n.drop(1).toInt() * 7 + WHITE.indexOf(n.take(1))
        return (index(low)..index(high)).map { i -> "${WHITE[i % 7]}${i / 7}:$clef" }
    }

    private val ACCIDENTALS = listOf("F#4:treble", "Bb4:treble", "C#5:treble", "Eb5:treble", "G#4:treble", "Ab4:treble", "F#3:bass", "Bb2:bass", "C#3:bass", "Eb3:bass")

    private fun noteid(title: String, pool: List<String>) =
        CheckRun.DrillRun(Drill(ExerciseType.E_NOTEID, "ทดสอบ: $title", items = 20, pool = pool.distinct()))

    private fun rhythm(title: String, rhythms: List<String>, bpm: Int, left: List<String> = emptyList()) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_RHYTHM_TAP, "ทดสอบ: $title", items = 1, rhythms = rhythms, rhythmsLeft = left, hand = if (left.isEmpty()) "RIGHT" else "BOTH",
            bars = 4, mode = "TEMPO", bpm = bpm),
    )

    private fun sight(level: Int, bpm: Int, hand: String = "RIGHT", keys: List<Int> = emptyList()) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_READ_PIECE, "ทดสอบ: อ่านโน้ตใหม่ L$level", items = 1, level = "L$level", hand = hand, keys = keys, mode = "TEMPO", bpm = bpm),
    )

    private fun fingers(hand: String) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_FINGER_PATTERN, "ทดสอบ: รูปแบบห้านิ้ว", items = 8, hand = hand, mode = "TEMPO", bpm = 60),
    )

    private fun scale(piece: String, hand: String, bpm: Int, title: String) = CheckRun.ScoreRun(
        Drill(ExerciseType.E_PIECE, "ทดสอบ: $title", items = 1, pieces = listOf(piece), hand = hand, mode = "TEMPO", bpm = bpm),
    )

    private fun chords(p: ChordPractice) = CheckRun.DrillRun(p.drill().copy(title = "ทดสอบ: " + p.summary()))

    private val ALL_KEYS = ChordPractice.KEYS

    private val SCORE = Criteria(minAccuracy = 0.95, minTiming = 0.85)
    private val READING = Criteria(minAccuracy = 0.9, minTiming = 0.8)

    private val LEVELS: Map<SkillId, List<SkillLevel>> = mapOf(
        SkillId.KEYS to listOf(
            SkillLevel(SkillId.KEYS, 1, "หาคีย์ขาวทั้ง 7 ตัวได้ (อ็อกเทฟไหนก็ได้)", keyfind(false), Criteria(0.9, 4.0)),
            SkillLevel(SkillId.KEYS, 2, "หาคีย์ขาวได้เร็วโดยไม่ต้องนับ", keyfind(false), Criteria(0.9, 2.5)),
            SkillLevel(SkillId.KEYS, 3, "หาคีย์ตามชื่อพร้อมอ็อกเทฟ เช่น G4 ได้", keyfind(true), Criteria(0.9, 3.5)),
            SkillLevel(SkillId.KEYS, 4, "หาคีย์พร้อมอ็อกเทฟได้แม่นและเร็ว", keyfind(true), Criteria(0.95, 2.5)),
            SkillLevel(SkillId.KEYS, 5, "หาคีย์พร้อมอ็อกเทฟได้แทบทันที", keyfind(true), Criteria(0.95, 1.5)),
        ),
        SkillId.NOTES to listOf(
            SkillLevel(SkillId.NOTES, 1, "อ่านโน้ต 5 ตัวแรกบนกุญแจซอล (C4–G4)", noteid("C4–G4", naturals("C4", "G4", "treble")), Criteria(0.9, 4.0)),
            SkillLevel(SkillId.NOTES, 2, "อ่านกุญแจซอลช่วง C4–C5", noteid("C4–C5", naturals("C4", "C5", "treble")), Criteria(0.9, 3.0)),
            SkillLevel(SkillId.NOTES, 3, "อ่านได้ทั้งสองกุญแจในช่วงกลาง (กุญแจซอล C4–G5, กุญแจฟา C3–C4)",
                noteid("สองกุญแจช่วงกลาง", naturals("C4", "G5", "treble") + naturals("C3", "C4", "bass")), Criteria(0.9, 3.0)),
            SkillLevel(SkillId.NOTES, 4, "อ่านได้เต็มบรรทัดทั้งสองกุญแจ (C2–C6)",
                noteid("เต็มบรรทัด", naturals("C4", "C6", "treble") + naturals("C2", "C4", "bass")), Criteria(0.9, 2.5)),
            SkillLevel(SkillId.NOTES, 5, "อ่านเส้นน้อยเหนือและใต้บรรทัดได้คล่อง",
                noteid("รวมเส้นน้อย", naturals("A3", "C6", "treble") + naturals("C2", "E4", "bass")), Criteria(0.9, 2.0)),
            SkillLevel(SkillId.NOTES, 6, "อ่านโน้ตที่มีชาร์ปและแฟลตได้",
                noteid("รวมชาร์ปแฟลต", naturals("C4", "C6", "treble") + naturals("C2", "C4", "bass") + ACCIDENTALS), Criteria(0.9, 2.0)),
            SkillLevel(SkillId.NOTES, 7, "อ่านโน้ตได้แทบทันทีทั้งบรรทัดคู่",
                noteid("ทั้งบรรทัดคู่", naturals("A3", "C6", "treble") + naturals("C2", "E4", "bass") + ACCIDENTALS), Criteria(0.95, 1.3)),
        ),
        SkillId.RHYTHM to listOf(
            SkillLevel(SkillId.RHYTHM, 1, "ตัวดำ ตัวขาว ตัวกลม และตัวหยุด ตรงจังหวะ", rhythm("ตัวดำ ตัวขาว ตัวกลม", listOf("q", "h", "w", "qr", "hr"), 70), SCORE),
            SkillLevel(SkillId.RHYTHM, 2, "เขบ็ตหนึ่งชั้นและโน้ตประจุด", rhythm("เขบ็ตและโน้ตประจุด", listOf("q", "ee", "h", "q.e", "h.", "qr"), 66), SCORE),
            SkillLevel(SkillId.RHYTHM, 3, "จังหวะย่อย 8 แบบ (เขบ็ตสองชั้น)", rhythm("จังหวะย่อย 8 แบบ", listOf("q", "ee", "ssss", "ess", "sse", "e.s", "ses", "re"), 56), SCORE),
            SkillLevel(SkillId.RHYTHM, 4, "ซิงโคเพชัน (เน้นนอกจังหวะ)", rhythm("ซิงโคเพชัน", listOf("q", "ee", "eqe", "re", "h"), 66), SCORE),
            SkillLevel(SkillId.RHYTHM, 5, "สองมือคนละจังหวะพร้อมกัน", rhythm("สองมือคนละจังหวะ", listOf("q", "ee", "h", "qr"), 66, left = listOf("h", "q", "w")), SCORE),
            SkillLevel(SkillId.RHYTHM, 6, "จังหวะย่อยที่ความเร็ว 72", rhythm("จังหวะย่อยเร็วขึ้น", listOf("q", "ee", "ssss", "ess", "sse", "e.s", "ses", "re", "eqe"), 72), SCORE),
        ),
        SkillId.SIGHT to listOf(
            SkillLevel(SkillId.SIGHT, 1, "เล่นทำนองง่ายมือขวาที่ไม่เคยเห็นได้ในรอบแรก", sight(1, 60), READING),
            SkillLevel(SkillId.SIGHT, 2, "เช่นเดียวกันด้วยมือซ้าย", sight(2, 60), READING),
            SkillLevel(SkillId.SIGHT, 3, "ทำนองที่มีเขบ็ต จังหวะ 3/4 และตำแหน่ง G", sight(3, 66), READING),
            SkillLevel(SkillId.SIGHT, 4, "ทำนองที่กระโดดไกลและย้ายมือ", sight(4, 60), READING),
            SkillLevel(SkillId.SIGHT, 5, "อ่านสองมือพร้อมกันแบบง่าย", sight(5, 56), READING),
            SkillLevel(SkillId.SIGHT, 6, "สองมือในคีย์ที่มีชาร์ป/แฟลต", sight(6, 66, keys = listOf(0, 1, -1, 2, -2)), READING),
            SkillLevel(SkillId.SIGHT, 7, "สองมือที่มีคอร์ดซ้อน", sight(7, 60), READING),
        ),
        SkillId.TECH to listOf(
            SkillLevel(SkillId.TECH, 1, "รูปแบบห้านิ้วมือขวาสม่ำเสมอที่ 60", fingers("RIGHT"), SCORE),
            SkillLevel(SkillId.TECH, 2, "รูปแบบห้านิ้วมือซ้ายสม่ำเสมอที่ 60", fingers("LEFT"), SCORE),
            SkillLevel(SkillId.TECH, 3, "สเกล C มือขวา (ลอดนิ้วโป้ง) ที่ 72", scale("ex-c-major-scale-rh", "RIGHT", 72, "สเกล C มือขวา"), SCORE),
            SkillLevel(SkillId.TECH, 4, "สเกล C มือซ้ายที่ 72", scale("ex-c-major-scale-lh", "LEFT", 72, "สเกล C มือซ้าย"), SCORE),
            SkillLevel(SkillId.TECH, 5, "สเกล G สองมือพร้อมกันที่ 66", scale("ex-scale-g-both", "BOTH", 66, "สเกล G สองมือ"), SCORE),
            SkillLevel(SkillId.TECH, 6, "สเกล D สองมือพร้อมกันที่ 80", scale("ex-scale-d-both", "BOTH", 80, "สเกล D สองมือ"), SCORE),
            SkillLevel(SkillId.TECH, 7, "สเกล B♭ สองมือพร้อมกันที่ 80", scale("ex-scale-bb-both", "BOTH", 80, "สเกล B♭ สองมือ"), SCORE),
        ),
        SkillId.EAR to listOf(
            SkillLevel(SkillId.EAR, 1, "แยก ド レ ミ ファ ในคีย์ C ได้", CheckRun.EarRun("intro3"), Criteria(minAccuracy = 0.9)),
            SkillLevel(SkillId.EAR, 2, "บอกโน้ตทั้ง 7 ตัวของ C เมเจอร์ได้ (หนึ่งอ็อกเทฟ)", CheckRun.EarRun("c-major-1"), Criteria(minAccuracy = 0.85)),
            SkillLevel(SkillId.EAR, 3, "เช่นเดียวกันในสองอ็อกเทฟ", CheckRun.EarRun("c-major-2"), Criteria(minAccuracy = 0.85)),
            SkillLevel(SkillId.EAR, 4, "บอกโน้ตได้ในทุกคีย์เมเจอร์", CheckRun.EarRun("any-major-1"), Criteria(minAccuracy = 0.85)),
            SkillLevel(SkillId.EAR, 5, "ทุกคีย์เมเจอร์ สองอ็อกเทฟ", CheckRun.EarRun("any-major-2"), Criteria(minAccuracy = 0.85)),
            SkillLevel(SkillId.EAR, 6, "คีย์ไมเนอร์ (A ไมเนอร์)", CheckRun.EarRun("a-minor-1"), Criteria(minAccuracy = 0.85)),
            SkillLevel(SkillId.EAR, 7, "ทุกคีย์ไมเนอร์", CheckRun.EarRun("any-minor-1"), Criteria(minAccuracy = 0.85)),
            SkillLevel(SkillId.EAR, 8, "รวมโน้ตครึ่งเสียง (โครมาติก)", CheckRun.EarRun("c-chromatic-1"), Criteria(minAccuracy = 0.8)),
        ),
        SkillId.CHORDS to listOf(
            SkillLevel(SkillId.CHORDS, 1, "กดคอร์ดเมเจอร์/ไมเนอร์ในคีย์ C G F ได้", chords(ChordPractice(ChordPractice.Mode.NAME, listOf(0, 1, -1), listOf("triads"), items = 10)), Criteria(0.9, 6.0)),
            SkillLevel(SkillId.CHORDS, 2, "คอร์ดเมเจอร์/ไมเนอร์ทุกคีย์ได้เร็ว", chords(ChordPractice(ChordPractice.Mode.NAME, ALL_KEYS, listOf("triads"), items = 12)), Criteria(0.9, 4.0)),
            SkillLevel(SkillId.CHORDS, 3, "รู้คอร์ดในคีย์ (I–vii°) ของ C G F", chords(ChordPractice(ChordPractice.Mode.NUMERAL, listOf(0, 1, -1), numeralSets = listOf("diatonic"), items = 12)), Criteria(0.9, 5.0)),
            SkillLevel(SkillId.CHORDS, 4, "เปลี่ยนคอร์ด I–V–vi–IV ในคีย์ C ได้คล่อง", chords(ChordPractice(ChordPractice.Mode.CHANGE, listOf(0), progression = "Axis", seconds = 60)), Criteria(minCount = 16)),
            SkillLevel(SkillId.CHORDS, 5, "คอร์ด 7 (7, maj7, m7) ในหลายคีย์", chords(ChordPractice(ChordPractice.Mode.NAME, listOf(0, 1, -1, 2, -2), listOf("sevenths"), items = 12)), Criteria(0.9, 5.0)),
            SkillLevel(SkillId.CHORDS, 6, "เปลี่ยนคอร์ด Canon ในคีย์ G ได้คล่อง", chords(ChordPractice(ChordPractice.Mode.CHANGE, listOf(1), progression = "Canon", seconds = 60)), Criteria(minCount = 20)),
            SkillLevel(SkillId.CHORDS, 7, "เปลี่ยนคอร์ด ii–V–I (คอร์ด 7) ในคีย์ F", chords(ChordPractice(ChordPractice.Mode.CHANGE, listOf(-1), progression = "ii–V–I", seconds = 60)), Criteria(minCount = 15)),
        ),
    )

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
        return SkillStatus(skill, level, max, last, next, due && failsToday < TRIES_PER_DAY, failsToday >= TRIES_PER_DAY, dropped)
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
            avg < 0.15 -> "เพิ่งเริ่มต้น"
            avg < 0.35 -> "มือใหม่"
            avg < 0.55 -> "มือใหม่ที่เริ่มเล่นเพลงง่ายได้"
            avg < 0.8 -> "ระดับกลางช่วงต้น"
            else -> "ระดับกลาง"
        }
    }
}
