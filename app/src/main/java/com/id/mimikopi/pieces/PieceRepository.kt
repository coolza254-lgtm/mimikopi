package com.id.mimikopi.pieces

import android.content.Context
import com.id.mimikopi.core.playalong.PieceProgress
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Bundled pieces (content/pieces: public-domain melodies in the app's own simple arrangements, and
 * original pieces) plus each piece's practice record.
 */
class PieceRepository(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), PieceProgress.serializer())
    private val mutex = Mutex()

    /** Pieces sorted by level, then title. */
    val pieces: List<Score> by lazy {
        context.assets.list("pieces").orEmpty().filter { it.endsWith(".json") }
            .map { name -> context.assets.open("pieces/$name").use { Score.from(PieceFile.parse(it.readBytes().toString(Charsets.UTF_8))) } }
            .sortedWith(compareBy({ it.level }, { it.title }))
    }

    fun piece(id: String): Score? = pieces.firstOrNull { it.id == id }

    /** Practice material that is not a song (scales, accompaniment patterns): content/exercises. */
    val exercises: List<Score> by lazy {
        context.assets.list("exercises").orEmpty().filter { it.endsWith(".json") }
            .map { name -> context.assets.open("exercises/$name").use { Score.from(PieceFile.parse(it.readBytes().toString(Charsets.UTF_8))) } }
    }

    fun pieceOrExercise(id: String): Score? = piece(id) ?: exercises.firstOrNull { it.id == id }
        ?: com.id.mimikopi.core.exam.Exams.practicePiece(id)?.let { runCatching { Score.from(it) }.getOrNull() }

    private val _progress = MutableStateFlow<Map<String, PieceProgress>>(emptyMap())
    val progress: StateFlow<Map<String, PieceProgress>> = _progress.asStateFlow()
    private var loaded = false

    /** Reads the stored rows again (after a backup restore). */
    suspend fun reload() {
        mutex.withLock { loaded = false }
        load()
    }

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        loaded = true
        db.settingsDao().get(SettingKeys.PIECE_PROGRESS)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?.let { _progress.value = it }
    }

    /** Whether a lesson's piece counts as passed with [hand]. */
    fun lessonPiecePassed(id: String, hand: String): Boolean =
        com.id.mimikopi.core.playalong.PieceRules.isPassed(progressOf(id), hand)

    fun progressOf(id: String): PieceProgress = _progress.value[id] ?: PieceProgress(id)

    suspend fun save(p: PieceProgress) {
        load()
        mutex.withLock {
            _progress.value = _progress.value + (p.pieceId to p)
            db.settingsDao().put(SettingEntity(SettingKeys.PIECE_PROGRESS, json.encodeToString(serializer, _progress.value)))
        }
    }
}
