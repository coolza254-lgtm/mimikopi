package com.id.mimikopi.desktop.port.reader

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/reader/PieceListScreen.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.id.mimikopi.desktop.port.stringResource
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.desktop.port.collectAsStateWithLifecycle
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.core.playalong.PieceProgress
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.desktop.port.components.PageCard
import com.id.mimikopi.desktop.port.components.ScreenScaffold
import com.id.mimikopi.desktop.ui.Tokens
import kotlinx.coroutines.launch

class PieceListViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as MimikopiApp).pieces
    /** Opened from a lesson's repertoire drill: only its pieces, played with its hand. */
    val lesson: Lesson? = args.get<String>("lesson")?.let { (app as MimikopiApp).learning.lesson(it) }
    val drill: Drill? = args.get<String>("drill")?.toIntOrNull()?.let { lesson?.drills?.getOrNull(it) }
    val hand: String? = drill?.hand
    val pieces = drill?.pieces?.mapNotNull { repo.pieceOrExercise(it) } ?: repo.pieces
    val progress = repo.progress

    init {
        viewModelScope.launch { repo.load() }
    }
}

@Composable
fun PieceListScreen(onBack: () -> Unit, onOpenPiece: (String, String?) -> Unit, vm: PieceListViewModel) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    ScreenScaffold(title = vm.lesson?.let { "${it.id} ${it.title}" } ?: stringResource(R.string.pieces_title), onBack = onBack) {
        val lesson = vm.lesson
        if (lesson != null) {
            Text(stringResource(R.string.lesson_pass, lesson.passText), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.pieces_intro), style = MaterialTheme.typography.bodyMedium)
        }
        for (s in vm.pieces) {
            val p = progress[s.id] ?: PieceProgress(s.id)
            PageCard(title = s.title, onClick = { onOpenPiece(s.id, vm.hand) }) {
                Text(s.composer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.pieces_meta, s.level, s.beatsPerMeasure, s.beatUnit, s.tempo, s.measures),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tokens.Palette.InkMuted,
                )
                val passed = PieceRules.isPassed(p, vm.hand ?: "BOTH")
                Text(status(p, vm.hand), style = MaterialTheme.typography.bodySmall, color = if (passed) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
        Text(stringResource(R.string.pieces_source_note), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
    }
}

@Composable
private fun status(p: PieceProgress, hand: String?): String {
    if (PieceRules.isPassed(p, hand ?: "BOTH")) return stringResource(R.string.pieces_status_passed)
    if (p.runs == 0) return stringResource(R.string.pieces_status_new)
    val parts = mutableListOf(stringResource(R.string.pieces_status_runs, p.runs))
    p.bestCleanBpm?.let { parts += stringResource(R.string.pieces_status_best, it) }
    val days = (p.passDays + (hand?.let { p.handPassDays[it] }.orEmpty())).toSet().size
    if (days > 0) parts += stringResource(R.string.pieces_status_days, days)
    return parts.joinToString(" · ")
}
