package com.id.mimikopi.recall

import com.id.mimikopi.core.recall.*
import com.id.mimikopi.data.db.SettingsDao
import com.id.mimikopi.data.db.SettingEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/**
 * Private songs in their settings row. They travel with the GitHub sync (core PrivateSongSync); a deleted song or
 * phrase leaves its id in [PrivateSongSync.DELETED] so a sync does not bring it back.
 */
class PrivateSongRepository(private val dao: SettingsDao) {
    companion object { const val KEY = "private.recall.songs.v1" }
    private val mutex = Mutex()
    private val serializer = ListSerializer(PrivateSong.serializer())
    private val _songs = MutableStateFlow<List<PrivateSong>>(emptyList())
    val songs = _songs.asStateFlow()
    private suspend fun read(): List<PrivateSong> = dao.get(KEY)?.let { raw ->
        PrivateRecall.json.decodeFromString(serializer, raw).also { songs ->
            require(songs.size <= 30 && songs.map { it.id }.distinct().size == songs.size)
            songs.forEach(PrivateRecall::validate)
        }
    } ?: emptyList()
    suspend fun reload() = mutex.withLock { _songs.value = read() }
    suspend fun mutate(change: (List<PrivateSong>) -> List<PrivateSong>) = mutex.withLock {
        val next = change(read())
        require(next.size <= 30) { "เก็บได้ไม่เกิน 30 เพลงส่วนตัว" }
        next.forEach(PrivateRecall::validate)
        dao.put(SettingEntity(KEY, PrivateRecall.json.encodeToString(serializer, next)))
        _songs.value = next
    }
    suspend fun add(title: String, url: String) = mutate { it + PrivateSong(UUID.randomUUID().toString(), title.trim(), VideoReference.canonical(url) ?: error("ลิงก์ YouTube ไม่ถูกต้อง")) }
    suspend fun update(song: PrivateSong) = mutate { all -> require(all.any { it.id == song.id }); all.map { if (it.id == song.id) song else it } }
    suspend fun changePhrase(songId: String, phraseId: String, change: (PrivatePhrase) -> PrivatePhrase) = mutate { all ->
        all.map { s -> if (s.id != songId) s else s.copy(phrases = s.phrases.map { p -> if (p.id == phraseId) change(p) else p }) }
    }
    suspend fun importCopy(text: String) = mutate { all -> all + PrivateRecall.import(text).copy(id = UUID.randomUUID().toString()) }
    suspend fun delete(id: String) { forget(PrivateSongSync.songKey(id)); mutate { it.filterNot { s -> s.id == id } } }
    suspend fun editSong(id: String, title: String, url: String) = mutate { songs ->
        val source = VideoReference.canonical(url) ?: error("ลิงก์ YouTube ไม่ถูกต้อง")
        songs.map { s -> if (s.id != id) s else s.copy(title = title.trim(), sourceUrl = source, edited = System.currentTimeMillis(),
            phrases = if (source == s.sourceUrl) s.phrases else s.phrases.map { it.copy(revision = it.revision + 1) }) }
    }
    suspend fun addPhrase(songId: String, p: PrivatePhrase) = mutate { all -> all.map { s ->
        if (s.id != songId) s else s.copy(phrases = s.phrases + p)
    } }
    suspend fun removePhrase(songId: String, phraseId: String) {
        forget(PrivateSongSync.phraseKey(songId, phraseId))
        mutate { all -> all.map { s -> if (s.id != songId) s else s.copy(phrases = s.phrases.filterNot { it.id == phraseId }) } }
    }

    /** Marks a song or phrase as deleted for the sync. */
    private suspend fun forget(key: String) {
        val gone = PrivateSongSync.decodeDeleted(dao.get(PrivateSongSync.DELETED))
        if (key !in gone) dao.put(SettingEntity(PrivateSongSync.DELETED, PrivateSongSync.encodeDeleted(gone + key)))
    }

}
