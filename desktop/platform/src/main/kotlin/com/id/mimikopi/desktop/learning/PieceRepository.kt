package com.id.mimikopi.desktop.learning

import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.playalong.PieceProgress
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.desktop.data.Database
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The bundled pieces (only Heroic Polonaise's two practice editions) and exercises, plus each piece's practice record
 * (the same settings row as Android, so it travels in backups).
 */
class PieceRepository(private val db: Database, private val privateScore: (String) -> Score? = { null }) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), PieceProgress.serializer())
    private val lock = Any()

    val pieces: List<Score> by lazy {
        Content.list("pieces").map { Score.from(PieceFile.parse(Content.read(it)!!)) }.sortedWith(compareBy({ it.level }, { it.title }))
    }

    val exercises: List<Score> by lazy { Content.list("exercises").map { Score.from(PieceFile.parse(Content.read(it)!!)) } }

    fun piece(id: String): Score? = pieces.firstOrNull { it.id == id }

    fun pieceOrExercise(id: String): Score? = piece(id) ?: exercises.firstOrNull { it.id == id }
        ?: Exams.practicePiece(id)?.let { runCatching { Score.from(it) }.getOrNull() }
        ?: privateScore(id)

    /** The source and arrangement notes of a bundled piece (shown offline under "ที่มา"). */
    fun pieceFile(id: String): PieceFile? = Content.read("pieces/$id.json")?.let { runCatching { PieceFile.parse(it) }.getOrNull() }

    private val _progress = MutableStateFlow<Map<String, PieceProgress>>(emptyMap())
    val progress: StateFlow<Map<String, PieceProgress>> = _progress.asStateFlow()
    private var loaded = false

    fun load(): Unit = synchronized(lock) {
        if (loaded) return
        loaded = true
        db.get(SettingKeys.PIECE_PROGRESS)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }?.let { _progress.value = it }
    }

    fun reload() {
        synchronized(lock) { loaded = false }
        load()
    }

    fun lessonPiecePassed(id: String, hand: String): Boolean = PieceRules.isPassed(progressOf(id), hand)

    fun progressOf(id: String): PieceProgress = _progress.value[id] ?: PieceProgress(id)

    fun save(p: PieceProgress) {
        load()
        synchronized(lock) {
            _progress.value = _progress.value + (p.pieceId to p)
            db.put(SettingKeys.PIECE_PROGRESS, json.encodeToString(serializer, _progress.value))
        }
    }
}
