package com.id.mimikopi.desktop.learning

import com.id.mimikopi.core.playalong.PieceProgress
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.desktop.data.Store
import com.id.mimikopi.desktop.port.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Bundled pieces (content/pieces) and practice material (content/exercises), plus each piece's practice record in
 * the same "piece.progress" row as the Android app. The same calls as the Android PieceRepository.
 */
class PieceRepository(private val store: Store) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), PieceProgress.serializer())

    private fun scores(folder: String): List<Score> = contentFiles(folder).mapNotNull { resourceText(it) }.map { Score.from(PieceFile.parse(it)) }

    /** Pieces sorted by level, then title. */
    val pieces: List<Score> by lazy { scores("pieces").sortedWith(compareBy({ it.level }, { it.title })) }
    val exercises: List<Score> by lazy { scores("exercises") }

    fun piece(id: String): Score? = pieces.firstOrNull { it.id == id }
    fun pieceOrExercise(id: String): Score? = piece(id) ?: exercises.firstOrNull { it.id == id }

    private val _progress = MutableStateFlow(loadProgress())
    val progress: StateFlow<Map<String, PieceProgress>> = _progress.asStateFlow()

    private fun loadProgress(): Map<String, PieceProgress> =
        store.get(SettingKeys.PIECE_PROGRESS)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyMap()

    /** Read when the app starts; kept so the ported code reads like the Android code. */
    fun load() = Unit

    /** Re-reads after a backup import. */
    fun reload() { _progress.value = loadProgress() }

    fun lessonPiecePassed(id: String, hand: String): Boolean = PieceRules.isPassed(progressOf(id), hand)

    fun progressOf(id: String): PieceProgress = _progress.value[id] ?: PieceProgress(id)

    fun save(p: PieceProgress) {
        _progress.value = _progress.value + (p.pieceId to p)
        store.put(SettingKeys.PIECE_PROGRESS, json.encodeToString(serializer, _progress.value))
    }
}
