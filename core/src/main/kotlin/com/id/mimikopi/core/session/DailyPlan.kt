package com.id.mimikopi.core.session

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

/** Parts of a day's practice, in the order they are done. */
enum class BlockKind(val log: String, val title: String, val why: String) {
    WARMUP("warmup", "วอร์มนิ้ว", "นิ้วพร้อม หัวพร้อม ก่อนเรื่องยาก"),
    CHECK("check", "ทดสอบวัดระดับ", "วัดว่าเก่งขึ้นจริงไหม ทำตอนยังสดอยู่"),
    REVIEW("review", "ทวนโน้ตและคีย์", "ทวนตามกำหนด ก่อนจะลืม"),
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
    /** Warm-up drill ("lessonId/drillIndex"), null when the ladder has none yet. */
    val warmup: String?,
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
 * Builds a day's practice from the time the user has (10-60 minutes): a short warm-up, the due test while fresh,
 * the review, the main ladder step (the largest part), new reading, chords, ear, and a song to end with. The weakest
 * skill's part gets more time. Short days keep only the warm-up, the test, the step and the weakest part, so even
 * ten minutes is a real session.
 */
object DailyPlan {
    val CHOICES = listOf(10, 20, 30, 45, 60)

    private fun blockFor(skill: SkillId?): BlockKind? = when (skill) {
        SkillId.KEYS, SkillId.NOTES -> BlockKind.REVIEW
        SkillId.RHYTHM -> BlockKind.LADDER
        SkillId.SIGHT -> BlockKind.SIGHT
        SkillId.TECH -> BlockKind.WARMUP
        SkillId.EAR -> BlockKind.EAR
        SkillId.CHORDS -> BlockKind.CHORDS
        null -> null
    }

    fun build(input: PlanInput): List<PlanBlock> {
        val total = input.minutes.coerceIn(10, 60)
        val weak = blockFor(input.weakest)
        val candidates = buildList {
            if (input.warmup != null) add(BlockKind.WARMUP to input.warmup)
            if (input.check != null) add(BlockKind.CHECK to input.check)
            if (input.reviewDue > 0) add(BlockKind.REVIEW to null)
            if (input.ladder) add(BlockKind.LADDER to null)
            if (input.sightLevel != null) add(BlockKind.SIGHT to input.sightLevel.toString())
            if (input.chords) add(BlockKind.CHORDS to null)
            if (input.earLevel != null) add(BlockKind.EAR to input.earLevel)
            add(BlockKind.SONG to null)
        }
        // Short days: the essentials and the weakest part only.
        val kept = if (total <= 10) candidates.filter { it.first in setOf(BlockKind.WARMUP, BlockKind.CHECK, BlockKind.LADDER) || it.first == weak }
            .ifEmpty { candidates.take(2) }
        else if (total <= 20) candidates.filter { it.first != BlockKind.SONG || candidates.size <= 3 } else candidates
        val fixed = mapOf(BlockKind.WARMUP to (if (total >= 30) 3 else 2), BlockKind.CHECK to 3)
        val weight = mapOf(
            BlockKind.REVIEW to 2.0, BlockKind.LADDER to 7.0, BlockKind.SIGHT to 3.0, BlockKind.CHORDS to 2.5,
            BlockKind.EAR to 3.0, BlockKind.SONG to if (total >= 45) 5.0 else 3.0,
        )
        val flexible = kept.filter { it.first !in fixed }
        val budget = (total - kept.sumOf { fixed[it.first] ?: 0 }).coerceAtLeast(flexible.size * 2)
        val w = flexible.associate { (k, _) -> k to (weight[k] ?: 2.0) * (if (k == weak) 1.6 else 1.0) }
        val sum = w.values.sum().takeIf { it > 0 } ?: 1.0
        val exact = w.mapValues { (_, x) -> budget * x / sum }
        val minutes = exact.mapValues { (_, x) -> maxOf(2, x.toInt()) }.toMutableMap()
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

    fun parse(text: String?): Stored? = text?.let { runCatching { json.decodeFromString(Stored.serializer(), it) }.getOrNull() }
    fun encode(s: Stored): String = json.encodeToString(Stored.serializer(), s)

    /** A block counts as done when its kind has at least 60% of its minutes logged today (or the test was taken). */
    fun isDone(block: PlanBlock, today: DayLog, checkTakenToday: Boolean, reviewDue: Int): Boolean = when (block.kind) {
        BlockKind.CHECK -> checkTakenToday
        BlockKind.REVIEW -> reviewDue == 0 || (today.seconds[block.kind.log] ?: 0) >= block.minutes * 36
        else -> (today.seconds[block.kind.log] ?: 0) >= block.minutes * 36
    }
}
