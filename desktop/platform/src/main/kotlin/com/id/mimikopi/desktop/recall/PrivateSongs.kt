package com.id.mimikopi.desktop.recall

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.recall.PrivatePhrase
import com.id.mimikopi.core.recall.PrivateRecall
import com.id.mimikopi.core.recall.PrivateSong
import com.id.mimikopi.core.recall.VideoReference
import com.id.mimikopi.desktop.data.Database
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/**
 * Private songs: the same settings row as the phone ("private.recall.songs.v1"), validated by core's PrivateRecall on
 * every read and write. It stays on this computer: never in the content folder, the installer or GitHub, and never in
 * the progress merge (only a whole-data backup the person makes, or a song file they export, carries it).
 */
class PrivateSongs(private val db: Database) {
    companion object { const val KEY = "private.recall.songs.v1" }

    private val lock = Any()
    private val serializer = ListSerializer(PrivateSong.serializer())
    private val _songs = MutableStateFlow<List<PrivateSong>>(emptyList())
    val songs: StateFlow<List<PrivateSong>> = _songs.asStateFlow()

    private fun read(): List<PrivateSong> = db.get(KEY)?.let { raw ->
        PrivateRecall.json.decodeFromString(serializer, raw).also { songs ->
            require(songs.size <= 30 && songs.map { it.id }.distinct().size == songs.size)
            songs.forEach(PrivateRecall::validate)
        }
    } ?: emptyList()

    fun reload() = synchronized(lock) { _songs.value = read() }

    fun mutate(change: (List<PrivateSong>) -> List<PrivateSong>) = synchronized(lock) {
        val next = change(read())
        require(next.size <= 30) { tr("เก็บได้ไม่เกิน 30 เพลงส่วนตัว", "Up to 30 private songs", "最大30曲までの個人曲") }
        next.forEach(PrivateRecall::validate)
        db.put(KEY, PrivateRecall.json.encodeToString(serializer, next))
        _songs.value = next
    }

    fun add(title: String, url: String) = mutate { it + PrivateSong(UUID.randomUUID().toString(), title.trim(), VideoReference.canonical(url) ?: error(tr("ลิงก์ YouTube ไม่ถูกต้อง", "Not a valid YouTube link", "YouTubeのリンクが正しくありません"))) }

    fun changePhrase(songId: String, phraseId: String, change: (PrivatePhrase) -> PrivatePhrase) = mutate { all ->
        all.map { s -> if (s.id != songId) s else s.copy(phrases = s.phrases.map { p -> if (p.id == phraseId) change(p) else p }) }
    }

    fun importCopy(text: String) = mutate { all -> all + PrivateRecall.import(text).copy(id = UUID.randomUUID().toString()) }

    fun delete(id: String) = mutate { it.filterNot { s -> s.id == id } }

    fun editSong(id: String, title: String, url: String) = mutate { songs ->
        val source = VideoReference.canonical(url) ?: error(tr("ลิงก์ YouTube ไม่ถูกต้อง", "Not a valid YouTube link", "YouTubeのリンクが正しくありません"))
        songs.map { s -> if (s.id != id) s else s.copy(title = title.trim(), sourceUrl = source,
            phrases = if (source == s.sourceUrl) s.phrases else s.phrases.map { it.copy(revision = it.revision + 1) }) }
    }

    fun addPhrase(songId: String, p: PrivatePhrase) = mutate { all -> all.map { s -> if (s.id != songId) s else s.copy(phrases = s.phrases + p) } }

    fun removePhrase(songId: String, phraseId: String) = mutate { all ->
        all.map { s -> if (s.id != songId) s else s.copy(phrases = s.phrases.filterNot { it.id == phraseId }) }
    }
}
