package com.id.mimikopi.core.session

import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.progress.SkillId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** One day's practice time by kind, in seconds of actual practice (runs played, not time the app was open). */
@Serializable
data class DayLog(val date: String, val seconds: Map<String, Int> = emptyMap()) {
    val total: Int get() = seconds.values.sum()
}

/** What was practised and for how long, kept per day (settings row "practice.log"). */
object PracticeLog {
    const val KEEP_DAYS = 400
    /** One run counts at most this long (a screen left open is not practice). */
    const val MAX_RUN_SECONDS = 20 * 60

    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(DayLog.serializer())
    fun parse(text: String?): List<DayLog> = text?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() } ?: emptyList()
    fun encode(days: List<DayLog>): String = json.encodeToString(list, days.sortedBy { it.date }.takeLast(KEEP_DAYS))

    fun add(days: List<DayLog>, date: LocalDate, kind: String, seconds: Long): List<DayLog> {
        val s = seconds.coerceIn(0, MAX_RUN_SECONDS.toLong()).toInt()
        if (s == 0) return days
        val d = date.toString()
        val today = days.firstOrNull { it.date == d } ?: DayLog(d)
        val next = today.copy(seconds = today.seconds + (kind to (today.seconds[kind] ?: 0) + s))
        return days.filter { it.date != d } + next
    }

    fun on(days: List<DayLog>, date: LocalDate): DayLog = days.firstOrNull { it.date == date.toString() } ?: DayLog(date.toString())

    /** Minutes for each of the last [count] days, oldest first (a calendar strip). */
    fun lastDays(days: List<DayLog>, today: LocalDate, count: Int): List<Pair<LocalDate, Int>> =
        (count - 1 downTo 0).map { back -> today.minusDays(back.toLong()).let { d -> d to on(days, d).total / 60 } }

    /** Days with any practice in the 7 days up to [today], and minutes in them. */
    fun week(days: List<DayLog>, today: LocalDate): Pair<Int, Int> {
        val last = lastDays(days, today, 7)
        return last.count { it.second > 0 } to last.sumOf { it.second }
    }
}

/**
 * Parts of a day's practice, in the order they are done: from easy to hard, so the day starts with notes already
 * known. [WARMUP] is no longer planned (a finger drill first was too hard before reading is fluent); it stays so
 * older stored plans and logs still read.
 */
enum class BlockKind(val log: String, val title: String, val why: String) {
    WARMUP("warmup", "วอร์มนิ้ว", "นิ้วพร้อม หัวพร้อม ก่อนเรื่องยาก"),
    REVIEW("review", "ทวนโน้ตที่รู้แล้ว", "เริ่มจากง่าย ทีละตัว ให้ตาและมือเข้ากัน"),
    CHECK("check", "ทดสอบวัดระดับ", "วัดว่าเก่งขึ้นจริงไหม ทำตอนยังสดอยู่"),
    LADDER("ladder", "ขั้นหลัก", "เรื่องใหม่ของวันนี้ ฝึกจนผ่านเกณฑ์"),
    SIGHT("sight", "อ่านโน้ตใหม่", "โน้ตที่ไม่เคยเห็น เล่นครั้งเดียวแล้วไปต่อ"),
    CHORDS("chords", "คอร์ด", "กดคอร์ดให้คล่องมือ"),
    EAR("ear", "ฝึกหู", "ฟังแล้วรู้ว่าเป็นโน้ตตัวไหน"),
    SONG("song", "เล่นเพลง", "เพลงที่ชอบ ไม่มีคะแนน"),
}

/** One block of today's plan; [target] says what it opens (a step, a level, a test key), null for the default. */
@Serializable
data class PlanBlock(val kind: BlockKind, val minutes: Int, val target: String? = null)

/** What today's practice can contain. */
data class PlanInput(
    val minutes: Int,
    val reviewDue: Int,
    /** The ladder has a step or confirmation for today. */
    val ladder: Boolean,
    /** New-reading level, null before reading starts. */
    val sightLevel: Int?,
    /** Ear level id to practise. */
    val earLevel: String?,
    /** Chord practice is useful yet (always, after the first chord test or step). */
    val chords: Boolean,
    /** A level test due today ("SKILL:LEVEL"). */
    val check: String?,
    /** The weakest measured skill, which gets more time. */
    val weakest: SkillId?,
)

/**
 * Builds a day's practice from the time the user has (10-60 minutes), easy to hard: the review of notes already
 * known, the due test, the main ladder step (the largest part), new reading, chords, ear, and a song to end with.
 * The weakest skill's part gets more time. Short days keep only the test, the step and the weakest part, so even
 * ten minutes is a real session. There is no separate warm-up: the review is the gentle start.
 */
object DailyPlan {
    val CHOICES = listOf(10, 20, 30, 45, 60)

    private fun blockFor(skill: SkillId?): BlockKind? = when (skill) {
        SkillId.KEYS, SkillId.NOTES -> BlockKind.REVIEW
        SkillId.RHYTHM -> BlockKind.LADDER
        SkillId.SIGHT -> BlockKind.SIGHT
        // Finger and scale drills live in the main practice, so technique's extra time goes there.
        SkillId.TECH -> BlockKind.LADDER
        SkillId.EAR -> BlockKind.EAR
        SkillId.CHORDS -> BlockKind.CHORDS
        null -> null
    }

    fun build(input: PlanInput): List<PlanBlock> {
        val total = input.minutes.coerceIn(10, 60)
        val weak = blockFor(input.weakest)
        val candidates = buildList {
            if (input.check != null) add(BlockKind.CHECK to input.check)
            if (input.reviewDue > 0) add(BlockKind.REVIEW to null)
            if (input.ladder) add(BlockKind.LADDER to null)
            if (input.sightLevel != null) add(BlockKind.SIGHT to input.sightLevel.toString())
            if (input.chords) add(BlockKind.CHORDS to null)
            if (input.earLevel != null) add(BlockKind.EAR to input.earLevel)
            add(BlockKind.SONG to null)
        }
        // Short days: the essentials and the weakest part only.
        val kept = (if (total <= 10) candidates.filter { it.first in setOf(BlockKind.CHECK, BlockKind.LADDER) || it.first == weak }
            .ifEmpty { candidates.take(2) }
        else if (total <= 20) candidates.filter { it.first != BlockKind.SONG || candidates.size <= 3 } else candidates).toMutableList()
        val fixed = mapOf(BlockKind.CHECK to 3)
        // When only fixed activities are available (e.g. a beginner's short day), finish with a song
        // instead of silently leaving most of the chosen time unallocated.
        if (kept.all { it.first in fixed }) kept += BlockKind.SONG to null
        val weight = mapOf(
            BlockKind.REVIEW to 2.0, BlockKind.LADDER to 7.0, BlockKind.SIGHT to 3.0, BlockKind.CHORDS to 2.5,
            BlockKind.EAR to 3.0, BlockKind.SONG to if (total >= 45) 5.0 else 3.0,
        )
        val flexible = kept.filter { it.first !in fixed }
        val budget = total - kept.sumOf { fixed[it.first] ?: 0 }
        val w = flexible.associate { (k, _) -> k to (weight[k] ?: 2.0) * (if (k == weak) 1.6 else 1.0) }
        // Apply the two-minute minimum before rounding, redistributing its cost among the
        // remaining activities. Clamping rounded shares alone can exceed the total budget.
        val exact = mutableMapOf<BlockKind, Double>()
        val remaining = w.toMutableMap()
        var available = budget.toDouble()
        while (remaining.isNotEmpty()) {
            val sum = remaining.values.sum()
            val belowMinimum = remaining.filterValues { available * it / sum < 2.0 }.keys
            if (belowMinimum.isEmpty()) {
                remaining.forEach { (k, weight) -> exact[k] = available * weight / sum }
                break
            }
            belowMinimum.forEach { k -> exact[k] = 2.0; remaining.remove(k); available -= 2.0 }
        }
        val minutes = exact.mapValues { (_, x) -> x.toInt() }.toMutableMap()
        var left = budget - minutes.values.sum()
        for (k in exact.keys.sortedByDescending { exact.getValue(it) - exact.getValue(it).toInt() }) {
            if (left <= 0) break
            minutes[k] = minutes.getValue(k) + 1
            left--
        }
        return kept.map { (k, target) -> PlanBlock(k, fixed[k] ?: minutes.getValue(k), target) }
            .sortedBy { it.kind.ordinal }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Today's plan as stored (so it stays the same all day): date, minutes chosen, blocks. */
    @Serializable
    data class Stored(val date: String, val minutes: Int, val blocks: List<PlanBlock>)

    /** A stored plan that no longer fits the rules (wrong totals, or an old warm-up block) is null: it is built again. */
    fun parse(text: String?): Stored? = text?.let { runCatching { json.decodeFromString(Stored.serializer(), it) }.getOrNull() }
        ?.takeIf { s -> s.minutes in CHOICES && s.blocks.isNotEmpty() && s.blocks.all { it.minutes >= 2 && it.kind != BlockKind.WARMUP } &&
            s.blocks.map { it.kind }.distinct().size == s.blocks.size && s.blocks.sumOf { it.minutes } == s.minutes }
    fun encode(s: Stored): String = json.encodeToString(Stored.serializer(), s)

    /** A block counts as done when its kind has at least 60% of its minutes logged today (or the test was taken). */
    fun isDone(block: PlanBlock, today: DayLog, checkTakenToday: Boolean, reviewDue: Int): Boolean = when (block.kind) {
        BlockKind.CHECK -> checkTakenToday
        BlockKind.REVIEW -> reviewDue == 0 || (today.seconds[block.kind.log] ?: 0) >= block.minutes * 36
        else -> (today.seconds[block.kind.log] ?: 0) >= block.minutes * 36
    }
}

/** What each part of the plan opens, from the ladder records and the skill levels. */
object PlanContent {
    private fun readLevel(l: com.id.mimikopi.core.lesson.Lesson): Int? =
        l.drills.filter { it.type == com.id.mimikopi.core.lesson.ExerciseType.E_READ_PIECE }.maxOfOrNull { it.level.removePrefix("L").toInt() }

    /**
     * What today's plan can contain, from the ladder, the review due, the skill tests taken and the date: the one
     * place both apps get it from, so a plan is the same on the phone and the computer.
     */
    fun input(
        minutes: Int,
        lessons: List<com.id.mimikopi.core.lesson.Lesson>,
        records: Map<String, com.id.mimikopi.core.lesson.LessonRecord>,
        checks: List<com.id.mimikopi.core.progress.CheckRecord>,
        reviewDue: Int,
        today: java.time.LocalDate,
    ): PlanInput {
        fun level(s: SkillId) = com.id.mimikopi.core.progress.SkillBook.status(s, checks, today).level
        val rules = com.id.mimikopi.core.lesson.ProgressRules
        return PlanInput(
            minutes = minutes,
            reviewDue = reviewDue,
            ladder = rules.nextToLearn(lessons, records) != null || rules.awaitingConfirmation(lessons, records, today).isNotEmpty(),
            sightLevel = sightLevel(lessons, records, level(SkillId.SIGHT)),
            earLevel = EAR_ROUND,
            chords = level(SkillId.KEYS) >= 2 || level(SkillId.CHORDS) >= 1,
            check = com.id.mimikopi.core.progress.SkillBook.dueCheck(checks, today)?.let { st -> st.nextTest?.let { com.id.mimikopi.core.progress.SkillBook.key(st.skill, it) } },
            weakest = com.id.mimikopi.core.progress.SkillBook.weakest(checks, today),
        )
    }

    /** New reading one level below the highest reading level reached on the ladder (L1 at least), or the sight level held. */
    fun sightLevel(lessons: List<com.id.mimikopi.core.lesson.Lesson>, records: Map<String, com.id.mimikopi.core.lesson.LessonRecord>, sightHeld: Int): Int? {
        if (sightHeld > 0) return sightHeld.coerceAtMost(5)
        val current = com.id.mimikopi.core.lesson.ProgressRules.current(lessons, records)
        val reached = lessons.filter { com.id.mimikopi.core.lesson.ProgressRules.isPassed(records[it.id], it) || it == current }
            .mapNotNull { readLevel(it) }.maxOrNull() ?: return null
        val passedAtReached = lessons.any { readLevel(it) == reached && com.id.mimikopi.core.lesson.ProgressRules.isPassed(records[it.id], it) }
        return (if (passedAtReached) reached else maxOf(1, reached - 1)).coerceAtMost(5)
    }

    /** Chord practice for the level held: the material of the next level's test, as practice. */
    fun chords(levelHeld: Int): ChordPractice = when (levelHeld) {
        0 -> ChordPractice(ChordPractice.Mode.NAME, listOf(0), listOf("triads"), items = 10)
        1 -> ChordPractice(ChordPractice.Mode.NAME, listOf(0, 1, -1), listOf("triads"), items = 12)
        2 -> ChordPractice(ChordPractice.Mode.NUMERAL, listOf(0, 1, -1), numeralSets = listOf("diatonic"), items = 12)
        3 -> ChordPractice(ChordPractice.Mode.CHANGE, listOf(0), progression = "Axis", seconds = 60)
        4 -> ChordPractice(ChordPractice.Mode.NAME, listOf(0, 1, -1, 2, -2), listOf("sevenths"), items = 12)
        5 -> ChordPractice(ChordPractice.Mode.CHANGE, listOf(1), progression = "Canon", seconds = 60)
        else -> ChordPractice(ChordPractice.Mode.CHANGE, listOf(-1), progression = "ii–V–I", seconds = 60)
    }

    /** The ear part is always the ear course's adaptive round (it picks the stage and the weak items itself). */
    const val EAR_ROUND = "course"
}
