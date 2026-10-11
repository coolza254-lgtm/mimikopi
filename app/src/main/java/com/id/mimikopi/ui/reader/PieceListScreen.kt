package com.id.mimikopi.ui.reader

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
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

class PieceListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MimikopiApp).pieces
    val pieces = repo.pieces
    val progress = repo.progress

    init {
        viewModelScope.launch { repo.load() }
    }
}

@Composable
fun PieceListScreen(onBack: () -> Unit, onOpenPiece: (String) -> Unit, vm: PieceListViewModel = viewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.pieces_title), onBack = onBack) {
        Text(stringResource(R.string.pieces_intro), style = MaterialTheme.typography.bodyMedium)
        for (s in vm.pieces) {
            val p = progress[s.id] ?: PieceProgress(s.id)
            PageCard(title = s.title, onClick = { onOpenPiece(s.id) }) {
                Text(s.composer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.pieces_meta, s.level, s.beatsPerMeasure, s.beatUnit, s.tempo, s.measures),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tokens.Palette.InkMuted,
                )
                Text(status(p), style = MaterialTheme.typography.bodySmall, color = if (PieceRules.isPassed(p)) Tokens.Palette.Good else Tokens.Palette.InkMuted)
            }
        }
        Text(stringResource(R.string.pieces_source_note), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
    }
}

@Composable
private fun status(p: PieceProgress): String {
    if (PieceRules.isPassed(p)) return stringResource(R.string.pieces_status_passed)
    if (p.runs == 0) return stringResource(R.string.pieces_status_new)
    val parts = mutableListOf(stringResource(R.string.pieces_status_runs, p.runs))
    p.bestCleanBpm?.let { parts += stringResource(R.string.pieces_status_best, it) }
    if (p.passDays.isNotEmpty()) parts += stringResource(R.string.pieces_status_days, p.passDays.toSet().size)
    return parts.joinToString(" · ")
}
