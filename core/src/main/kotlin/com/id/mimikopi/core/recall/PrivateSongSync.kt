package com.id.mimikopi.core.recall

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Private songs travel with the progress sync (the user chose convenience over keeping them on one device,
 * 2026-10-22). Only what the user typed and their own practice goes: titles, video links, phrase times, cues, chords
 * and recall results; never notes or lyrics.
 *
 * The merge never loses practice and gives the same result from either side: songs and phrases are joined by id,
 * attempts and recall days are unions, and the title, times, cue and chords come from the copy edited last ([PrivatePhrase.edited],
 * then the higher revision, then the text). Deleting a song or phrase leaves an id in [DELETED] so a sync does not bring it back.
 */
object PrivateSongSync {
    const val SONGS = "private.recall.songs.v1"
    const val DELETED = "private.recall.deleted"
    const val MAX_SONGS = 30

    private val songsSer = ListSerializer(PrivateSong.serializer())
    private val deletedSer = SetSerializer(String.serializer())

    fun songKey(id: String) = "song:$id"
    fun phraseKey(songId: String, phraseId: String) = "phrase:$songId/$phraseId"

    fun decodeSongs(raw: String?): List<PrivateSong> =
        raw?.let { runCatching { PrivateRecall.json.decodeFromString(songsSer, it) }.getOrNull() }.orEmpty()

    fun encodeSongs(songs: List<PrivateSong>): String = PrivateRecall.json.encodeToString(songsSer, songs)

    fun decodeDeleted(raw: String?): Set<String> = raw?.let { runCatching { PrivateRecall.json.decodeFromString(deletedSer, it) }.getOrNull() }.orEmpty()

    fun encodeDeleted(ids: Set<String>): String = PrivateRecall.json.encodeToString(deletedSer, ids.toSortedSet())

    /**
     * Both devices' songs joined. Songs keep this device's order, then new ones from the other in theirs; phrases are
     * ordered by start time. More than [MAX_SONGS] keeps the first ones.
     */
    fun merge(local: List<PrivateSong>, remote: List<PrivateSong>, deleted: Set<String>): List<PrivateSong> {
        val byId = remote.associateBy { it.id }
        val ids = (local.map { it.id } + remote.map { it.id }).distinct().filter { songKey(it) !in deleted }
        return ids.map { id ->
            val a = local.firstOrNull { it.id == id }
            val b = byId[id]
            if (a == null || b == null) prune(a ?: b!!, deleted) else song(a, b, deleted)
        }.take(MAX_SONGS)
    }

    private fun prune(s: PrivateSong, deleted: Set<String>) =
        s.copy(phrases = s.phrases.filter { phraseKey(s.id, it.id) !in deleted }.sortedWith(compareBy({ it.startSeconds }, { it.id })))

    private fun song(a: PrivateSong, b: PrivateSong, deleted: Set<String>): PrivateSong {
        val head = listOf(a, b).maxWith(compareBy({ it.edited }, { it.title + "\u0000" + it.sourceUrl }))
        val phraseIds = (a.phrases.map { it.id } + b.phrases.map { it.id }).distinct().filter { phraseKey(a.id, it) !in deleted }
        val phrases = phraseIds.map { pid ->
            val x = a.phrases.firstOrNull { it.id == pid }
            val y = b.phrases.firstOrNull { it.id == pid }
            if (x == null || y == null) (x ?: y)!! else phrase(x, y)
        }.sortedWith(compareBy({ it.startSeconds }, { it.id }))
        return head.copy(edited = maxOf(a.edited, b.edited), phrases = phrases)
    }

    /** One phrase from two copies (public for tests). */
    fun phrase(x: PrivatePhrase, y: PrivatePhrase): PrivatePhrase {
        val head = listOf(x, y).maxWith(compareBy({ it.edited }, { it.revision }, { PrivateRecall.json.encodeToString(PrivatePhrase.serializer(), it.copy(attempts = emptyList())) }))
        val revision = maxOf(x.revision, y.revision)
        val attempts = (x.attempts + y.attempts).distinctBy { it.id }.filter { it.revision <= revision }
            .sortedWith(compareBy({ it.day }, { it.id })).takeLast(PrivateRecall.KEEP_ATTEMPTS)
        fun days(m: Map<String, List<String>>, n: Map<String, List<String>>) =
            (m.keys + n.keys).sorted().associateWith { k -> (m[k].orEmpty() + n[k].orEmpty()).distinct().sorted() }
        val hard = (x.lastDifficulty.keys + y.lastDifficulty.keys).sorted()
            .associateWith { k -> listOfNotNull(x.lastDifficulty[k], y.lastDifficulty[k]).max() }
        return head.copy(
            revision = revision, attempts = attempts, edited = maxOf(x.edited, y.edited),
            recallDays = days(x.recallDays, y.recallDays), coldRecallDays = days(x.coldRecallDays, y.coldRecallDays), lastDifficulty = hard,
        )
    }
}
