package com.id.mimikopi.songs

import android.content.Context
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.songs.Song
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.pieces.PieceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The user's song library (learning-system.md 9.4-9.6): one settings row per song. On first use the bundled
 * target-song shells (content/songs, unverified facts only) are copied in; after that the library is the user's.
 */
class SongRepository(private val context: Context, private val db: AppDatabase) {
    private val mutex = Mutex()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private var loaded = false

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        loaded = true
        val dao = db.settingsDao()
        if (dao.get(SettingKeys.SONGS_SEEDED) == null) {
            val shells = runCatching {
                context.assets.open("songs/targets.json").use { Song.parseList(it.readBytes().toString(Charsets.UTF_8)) }
            }.getOrDefault(emptyList())
            shells.forEach { dao.put(SettingEntity(SettingKeys.SONG_PREFIX + it.id, it.toJson())) }
            dao.put(SettingEntity(SettingKeys.SONGS_SEEDED, "1"))
        }
        _songs.value = dao.withPrefix(SettingKeys.SONG_PREFIX).mapNotNull { runCatching { Song.parse(it.value) }.getOrNull() }.sortedBy { it.order }
    }

    fun song(id: String): Song? = _songs.value.firstOrNull { it.id == id }

    suspend fun save(song: Song) {
        load()
        mutex.withLock {
            db.settingsDao().put(SettingEntity(SettingKeys.SONG_PREFIX + song.id, song.toJson()))
            _songs.value = (_songs.value.filter { it.id != song.id } + song).sortedBy { it.order }
        }
    }

    suspend fun delete(id: String) {
        load()
        mutex.withLock {
            db.settingsDao().delete(SettingKeys.SONG_PREFIX + id)
            _songs.value = _songs.value.filter { it.id != id }
        }
    }

    fun newId(): String = "song-" + System.currentTimeMillis().toString(36)

    fun nextOrder(): Int = (_songs.value.maxOfOrNull { it.order } ?: -1) + 1

    /** Capstone B: all 7 pipeline days done and the linked score passed with both hands. */
    fun isComplete(song: Song, pieces: PieceRepository): Boolean =
        song.pipeline.all { it.done } && song.scoreId != null && PieceRules.isPassed(pieces.progressOf(song.scoreId!!), "BOTH")

    fun completedCount(pieces: PieceRepository): Int = _songs.value.count { isComplete(it, pieces) }
}
