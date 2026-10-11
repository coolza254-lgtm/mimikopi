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

    fun piece(id: String): Score? = pieces.firstOrNull { it.id == id } ?: userScore(id)

    // ---------------- user scores (written in the editor or imported) ----------------

    private val _user = MutableStateFlow<Map<String, PieceFile>>(emptyMap())
    /** The user's own scores by id. */
    val userScores: StateFlow<Map<String, PieceFile>> = _user.asStateFlow()
    private val userCache = mutableMapOf<String, Pair<PieceFile, Score?>>()

    fun userScore(id: String): Score? {
        val f = _user.value[id] ?: return null
        val cached = userCache[id]
        if (cached != null && cached.first === f) return cached.second
        val s = runCatching { Score.from(f) }.getOrNull()
        userCache[id] = f to s
        return s
    }

    suspend fun loadUserScores() {
        val rows = db.settingsDao().withPrefix(SettingKeys.USER_SCORE_PREFIX)
        _user.value = rows.mapNotNull { r -> runCatching { PieceFile.parse(r.value) }.getOrNull() }.associateBy { it.id }
    }

    suspend fun saveUserScore(f: PieceFile) {
        db.settingsDao().put(SettingEntity(SettingKeys.USER_SCORE_PREFIX + f.id, f.toJson()))
        _user.value = _user.value + (f.id to f)
    }

    suspend fun deleteUserScore(id: String) {
        db.settingsDao().delete(SettingKeys.USER_SCORE_PREFIX + id)
        _user.value = _user.value - id
        userCache.remove(id)
    }

    fun newUserId(): String = "user-" + System.currentTimeMillis().toString(36)

    /** Practice material that is not a song (scales, accompaniment patterns): content/exercises. */
    val exercises: List<Score> by lazy {
        context.assets.list("exercises").orEmpty().filter { it.endsWith(".json") }
            .map { name -> context.assets.open("exercises/$name").use { Score.from(PieceFile.parse(it.readBytes().toString(Charsets.UTF_8))) } }
    }

    fun pieceOrExercise(id: String): Score? = piece(id) ?: exercises.firstOrNull { it.id == id }

    private val _progress = MutableStateFlow<Map<String, PieceProgress>>(emptyMap())
    val progress: StateFlow<Map<String, PieceProgress>> = _progress.asStateFlow()
    private var loaded = false

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        loaded = true
        loadUserScores()
        db.settingsDao().get(SettingKeys.PIECE_PROGRESS)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?.let { _progress.value = it }
    }

    fun progressOf(id: String): PieceProgress = _progress.value[id] ?: PieceProgress(id)

    suspend fun save(p: PieceProgress) {
        load()
        mutex.withLock {
            _progress.value = _progress.value + (p.pieceId to p)
            db.settingsDao().put(SettingEntity(SettingKeys.PIECE_PROGRESS, json.encodeToString(serializer, _progress.value)))
        }
    }
}
