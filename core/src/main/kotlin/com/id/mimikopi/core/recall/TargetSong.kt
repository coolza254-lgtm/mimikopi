package com.id.mimikopi.core.recall

import java.time.LocalDate

/**
 * The goal song (learning-system.md 2.11): one private song the user chose, shown first on Today and Songs (Design J
 * "เพลงเป้าหมาย" card). Its progress is the user's own recall evidence: a phrase counts as learned once it was played
 * without help before looking, on two different days, with either hand set ([LEARNED_DAYS]).
 */
object TargetSong {
    const val SETTING_KEY = "private.target"
    const val LEARNED_DAYS = 2

    enum class State { LEARNED, PRACTISING, NEW }

    /** The phrase to work on now and with which hand, and why. */
    data class Next(val phrase: PrivatePhrase, val hand: RecallHand, val due: Boolean)

    /** The chosen song, or the first private song when none was chosen (or the chosen one was deleted). */
    fun pick(songs: List<PrivateSong>, chosenId: String?): PrivateSong? = songs.firstOrNull { it.id == chosenId } ?: songs.firstOrNull()

    fun state(p: PrivatePhrase): State = when {
        RecallHand.entries.any { PrivateRecall.rememberedDays(p, it).size >= LEARNED_DAYS } -> State.LEARNED
        RecallHand.entries.any { PrivateRecall.current(p, it).isNotEmpty() } -> State.PRACTISING
        else -> State.NEW
    }

    /** Learned phrases of all, 0..1 (the ring on the card). */
    fun fraction(song: PrivateSong): Float =
        if (song.phrases.isEmpty()) 0f else song.phrases.count { state(it) == State.LEARNED }.toFloat() / song.phrases.size

    /**
     * What to practise: a phrase due for review today (any hand, the earliest first), else the first phrase not yet
     * learned with the hand last used on it (right hand to start).
     */
    fun next(song: PrivateSong, day: LocalDate): Next? {
        val due = song.phrases.flatMap { p -> RecallHand.entries.mapNotNull { h -> PrivateRecall.due(p, h)?.takeIf { !it.isAfter(day) }?.let { Triple(p, h, it) } } }
            .minByOrNull { it.third }
        if (due != null) return Next(due.first, due.second, due = true)
        val open = song.phrases.firstOrNull { state(it) != State.LEARNED } ?: return null
        val hand = open.attempts.lastOrNull { it.revision == open.revision }?.hand ?: RecallHand.RIGHT
        return Next(open, hand, due = false)
    }
}
