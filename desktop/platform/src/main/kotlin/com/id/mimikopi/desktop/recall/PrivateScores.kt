package com.id.mimikopi.desktop.recall

import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.desktop.data.Database
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Bought piano-solo sheets entered by playing them (learning-system.md 2.12, step 3). Like private songs they stay on
 * this computer: one settings row, never in the content folder, the installer, GitHub or the progress merge. Their
 * practice records use the ordinary piece progress under the score's id ("private-…").
 */
class PrivateScores(private val db: Database) {
    companion object {
        const val KEY = "private.scores.v1"
        const val PREFIX = "private-"
        const val MAX = 50
    }

    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(PieceFile.serializer())
    private val _scores = MutableStateFlow<List<PieceFile>>(emptyList())
    val scores: StateFlow<List<PieceFile>> = _scores.asStateFlow()

    private fun read(): List<PieceFile> = db.get(KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    fun reload() = synchronized(lock) { _scores.value = read() }

    private fun write(next: List<PieceFile>) = synchronized(lock) {
        require(next.size <= MAX)
        next.forEach { Score.from(it) } // every saved score must still read as a score
        db.put(KEY, json.encodeToString(serializer, next))
        _scores.value = next
    }

    fun file(id: String): PieceFile? = _scores.value.firstOrNull { it.id == id }

    fun score(id: String): Score? = file(id)?.let { runCatching { Score.from(it) }.getOrNull() }

    fun timeline(id: String): Timeline? = file(id)?.let { runCatching { Timeline.fromPiece(it) }.getOrNull() }

    /** A new empty score; returns its id. */
    fun create(title: String, key: Int, time: List<Int>, tempo: Int, measures: Int): String {
        val id = PREFIX + UUID.randomUUID().toString()
        val t = Timeline.empty(ScoreMeta(id, title.trim().ifEmpty { "Untitled" }, source = "Private score (entered from the piano)", key = key, time = time, tempo = tempo), measures)
        write(read() + t.toPieceFile())
        return id
    }

    fun save(t: Timeline) {
        val f = t.toPieceFile()
        write(read().map { if (it.id == f.id) f else it }.let { if (it.none { s -> s.id == f.id }) it + f else it })
    }

    fun delete(id: String) = write(read().filterNot { it.id == id })
}
