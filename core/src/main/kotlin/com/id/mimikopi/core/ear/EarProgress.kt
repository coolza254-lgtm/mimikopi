package com.id.mimikopi.core.ear

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * How well one item is known: first-try answers counted, and [strength] (0..1) that moves 30% of the way toward each
 * new answer, so recent answers weigh most and one lucky answer cannot master an item.
 */
@Serializable
data class EarItemStat(
    val asked: Int = 0,
    val right: Int = 0,
    val strength: Double = 0.5,
    /** Last answer (epoch ms). */
    val lastAt: Long = 0,
    /** Answer time, smoothed the same way (ms). */
    val ms: Int = 0,
) {
    val mastered: Boolean get() = asked >= EarProgress.MASTER_ASKED && strength >= EarProgress.MASTER_STRENGTH
}

/** One round or test, for the history and the charts. */
@Serializable
data class EarSession(val at: Long, val day: String, val asked: Int, val right: Int, val test: Boolean = false, val focus: String? = null)

/** Everything the ear course stores (settings row "ear.course"). */
@Serializable
data class EarState(val items: Map<String, EarItemStat> = emptyMap(), val sessions: List<EarSession> = emptyList())

/** One answered question. */
data class EarResult(val item: String, val right: Boolean, val ms: Long)

/** An item and how it stands, for the "how is my ear" view. */
data class EarItemView(val item: String, val label: String, val kind: EarKind, val stat: EarItemStat)

object EarProgress {
    const val KEY = "ear.course"
    const val MASTER_ASKED = 6
    const val MASTER_STRENGTH = 0.8
    const val ROUND = 12
    private const val RATE = 0.3
    private const val KEEP_SESSIONS = 500

    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String?): EarState = text?.let { runCatching { json.decodeFromString(EarState.serializer(), it) }.getOrNull() } ?: EarState()
    fun encode(state: EarState): String = json.encodeToString(EarState.serializer(), state)

    fun stat(state: EarState, item: String): EarItemStat = state.items[item] ?: EarItemStat()

    /** The stage being learned: the first whose items are not all mastered (the last one once everything is). */
    fun currentStage(state: EarState): EarCourse.Stage =
        EarCourse.STAGES.firstOrNull { st -> st.adds.any { !stat(state, it).mastered } } ?: EarCourse.STAGES.last()

    /** Items taught so far: every stage up to the current one. */
    fun unlocked(state: EarState): List<String> {
        val n = currentStage(state).n
        return EarCourse.STAGES.filter { it.n <= n }.flatMap { it.adds }
    }

    /** Stage progress: items mastered of the stage's items. */
    fun stageProgress(state: EarState, stage: EarCourse.Stage): Pair<Int, Int> = stage.adds.count { stat(state, it).mastered } to stage.adds.size

    /**
     * The items of a round of [count] questions: items still being learned come up most, weak ones next, mastered ones
     * now and then (more when not heard for days), the newest stage a little more. [focus] keeps to one kind. Grouped
     * into short runs of one kind (up to three), so each question type gets a moment before the next.
     */
    fun plan(state: EarState, now: Long, random: Random, count: Int = ROUND, focus: EarKind? = null): List<String> {
        val stage = currentStage(state)
        val pool = unlocked(state).filter { focus == null || EarCourse.kindOf(it) == focus }
        if (pool.isEmpty()) return emptyList()
        fun weight(item: String): Double {
            val s = stat(state, item)
            val days = if (s.lastAt == 0L) 99.0 else (now - s.lastAt) / 86_400_000.0
            val base = when {
                s.asked < MASTER_ASKED -> 3.0
                s.strength < MASTER_STRENGTH -> 1.0 + 3.0 * (MASTER_STRENGTH - s.strength)
                else -> 0.25 + minOf(0.75, days / 14.0)
            }
            return base * (if (item in stage.adds) 1.5 else 1.0)
        }
        val weights = pool.associateWith(::weight)
        val picked = mutableListOf<String>()
        // No item more often than its fair share plus one, so a round always mixes.
        val cap = maxOf(2, (count + pool.size - 1) / pool.size + 1)
        while (picked.size < count) {
            val choices = weights.filter { (item, _) -> picked.count { it == item } < cap }
            val total = choices.values.sum()
            var r = random.nextDouble() * total
            val item = choices.entries.firstOrNull { r -= it.value; r <= 0 }?.key ?: choices.keys.last()
            picked += item
        }
        // Short runs of one kind, kinds interleaved.
        val byKind = picked.groupBy { EarCourse.kindOf(it) }.mapValues { (_, v) -> v.shuffled(random).chunked(3) }.toMutableMap()
        val out = mutableListOf<String>()
        val kinds = byKind.keys.shuffled(random).toMutableList()
        while (kinds.isNotEmpty()) {
            val it = kinds.iterator()
            while (it.hasNext()) {
                val k = it.next()
                val chunks = byKind.getValue(k)
                out += chunks.first()
                if (chunks.size == 1) { it.remove(); byKind.remove(k) } else byKind[k] = chunks.drop(1)
            }
        }
        return out
    }

    /** The questions of a round (practice): each item at the current stage's keys and range. */
    fun round(state: EarState, now: Long, random: Random, focus: EarKind? = null): List<EarQuestion> {
        val stage = currentStage(state)
        val unlocked = unlocked(state)
        return plan(state, now, random, focus = focus).map { item ->
            EarCourse.question(item, unlocked, stage.keys, stage.wide, stat(state, item).strength, random)
        }
    }

    /** The questions of an EAR level test: every item of the test in turn, then at random, fixed keys and range. */
    fun testRound(test: EarCourse.Test, random: Random): List<EarQuestion> {
        val items = (test.items + List(test.count) { test.items[random.nextInt(test.items.size)] }).take(test.count).shuffled(random)
        return items.map { EarCourse.question(it, test.items, test.keys, test.wide, 1.0, random) }
    }

    /** Applies a finished round's answers: item stats and one session record. */
    fun record(state: EarState, results: List<EarResult>, now: Long, zone: ZoneId = ZoneId.systemDefault(), test: Boolean = false, focus: EarKind? = null): EarState {
        if (results.isEmpty()) return state
        val items = state.items.toMutableMap()
        for (r in results) {
            val s = items[r.item] ?: EarItemStat()
            val x = if (r.right) 1.0 else 0.0
            items[r.item] = s.copy(
                asked = s.asked + 1,
                right = s.right + if (r.right) 1 else 0,
                strength = s.strength + RATE * (x - s.strength),
                lastAt = now,
                ms = if (s.ms == 0) r.ms.toInt() else (s.ms + RATE * (r.ms - s.ms)).toInt(),
            )
        }
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
        val session = EarSession(now, day, results.size, results.count { it.right }, test, focus?.name)
        return EarState(items, (state.sessions + session).takeLast(KEEP_SESSIONS))
    }

    /** Items taught so far with their stats, by kind, weakest first within a kind. */
    fun overview(state: EarState): Map<EarKind, List<EarItemView>> =
        unlocked(state).map { EarItemView(it, EarCourse.label(it), EarCourse.kindOf(it), stat(state, it)) }
            .groupBy { it.kind }.mapValues { (_, v) -> v.sortedBy { it.stat.strength } }

    /** The weakest items heard at least three times (what to work on). */
    fun weakest(state: EarState, n: Int = 3): List<EarItemView> =
        unlocked(state).map { EarItemView(it, EarCourse.label(it), EarCourse.kindOf(it), stat(state, it)) }
            .filter { it.stat.asked >= 3 && it.stat.strength < MASTER_STRENGTH }.sortedBy { it.stat.strength }.take(n)

    /** First-try accuracy per day over the last [days] days (practice and tests), oldest first; days without ear work left out. */
    fun daily(state: EarState, today: LocalDate, days: Int = 60): List<Pair<LocalDate, Double>> =
        state.sessions.groupBy { it.day }.mapNotNull { (d, ss) ->
            val date = runCatching { LocalDate.parse(d) }.getOrNull() ?: return@mapNotNull null
            if (date.isBefore(today.minusDays(days.toLong() - 1)) || date.isAfter(today)) return@mapNotNull null
            date to ss.sumOf { it.right }.toDouble() / ss.sumOf { it.asked }.coerceAtLeast(1)
        }.sortedBy { it.first }

    /** Merges two copies (sync): per item the copy with more answers (equal: by content), every session from both. */
    fun merge(a: EarState, b: EarState): EarState {
        val items = (a.items.keys + b.items.keys).sorted().associateWith { k ->
            val x = a.items[k]
            val y = b.items[k]
            when {
                x == null -> y!!
                y == null -> x
                x.asked != y.asked -> if (x.asked > y.asked) x else y
                else -> maxOf(x, y, compareBy({ it.lastAt }, { it.right }, { it.strength }, { it.ms }))
            }
        }
        val sessions = (a.sessions + b.sessions).distinct().sortedBy { it.at }.takeLast(KEEP_SESSIONS)
        return EarState(items, sessions)
    }
}
