package com.id.mimikopi.ui.reader

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.Lesson
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.playalong.PieceProgress
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
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
fun PieceListScreen(onBack: () -> Unit, onOpenPiece: (String, String?) -> Unit, vm: PieceListViewModel = viewModel()) {
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
