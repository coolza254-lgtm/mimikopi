package com.id.mimikopi.ui.songs

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.Pill
import com.id.mimikopi.ui.components.TabHeader
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.launch

class SongsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MimikopiApp).pieces
    val pieces: List<Score> get() = repo.pieces
    val progress = repo.progress
    init { viewModelScope.launch { repo.load() } }
}

/** Practice songs: public-domain melodies (and the app's own short pieces), easiest first. Tap a hand to play. */
@Composable
fun SongsScreen(onPlay: (pieceId: String, hand: String) -> Unit, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit, vm: SongsViewModel = viewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            TabHeader(stringResource(R.string.tab_songs), onOpenMidi, onOpenSettings)
            Text(stringResource(R.string.songs_intro), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vm.pieces.forEach { s ->
                    val p = progress[s.id]
                    val hands = listOf("RIGHT" to R.string.reader_hand_right, "LEFT" to R.string.reader_hand_left, "BOTH" to R.string.reader_hand_both)
                        .map { (h, id) -> h to stringResource(id) }
                    PageCard(onClick = { open = if (open == s.id) null else s.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.titleMedium)
                                Text(s.composer, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, maxLines = 1)
                            }
                            Pill(stringResource(R.string.songs_level, s.level.removePrefix("L")), Tokens.Palette.Track)
                        }
                        val passed = hands.filter { (h, _) -> p != null && PieceRules.isPassed(p, h) }
                        if (passed.isNotEmpty()) Text(
                            stringResource(R.string.songs_passed, passed.joinToString { it.second }),
                            style = MaterialTheme.typography.labelMedium, color = Tokens.Palette.GoodInk, modifier = Modifier.padding(top = 6.dp),
                        )
                        if (open == s.id) {
                            if ("not yet checked" in s.source) Text(stringResource(R.string.songs_unverified), style = MaterialTheme.typography.bodySmall,
                                color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                hands.forEach { (h, label) ->
                                    OutlinedButton(onClick = { onPlay(s.id, h) }, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
