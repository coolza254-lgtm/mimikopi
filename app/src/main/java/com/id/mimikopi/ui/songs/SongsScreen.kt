package com.id.mimikopi.ui.songs

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.Hairline
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

/**
 * Practice songs as a numbered list (public-domain melodies and the app's own short pieces), easiest first.
 * Tapping a song opens a three-part choice of hands.
 */
@Composable
fun SongsScreen(onPlay: (pieceId: String, hand: String) -> Unit, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit, onOpenLibrary: () -> Unit, vm: SongsViewModel = viewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<String?>(null) }
    val hands = listOf("RIGHT" to R.string.reader_hand_right, "LEFT" to R.string.reader_hand_left, "BOTH" to R.string.reader_hand_both)
        .map { (h, id) -> h to stringResource(id) }
    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            val passedCount = vm.pieces.count { s -> progress[s.id]?.let { PieceRules.isPassed(it) } == true }
            TabHeader(stringResource(R.string.tab_songs), stringResource(R.string.songs_count, vm.pieces.size), onOpenMidi, onOpenSettings)
            if (passedCount > 0) Text(stringResource(R.string.songs_passed_count, passedCount, vm.pieces.size), fontFamily = Tokens.Fonts.mono, fontSize = 12.sp,
                color = Tokens.Palette.InkMuted, modifier = Modifier.padding(start = 24.dp, top = 6.dp))
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 22.dp, bottom = 24.dp)) {
                PageCard(title = "คลาสสิก · ง่ายไปยาก", onClick = onOpenLibrary) {
                    Text("11 ขั้นพร้อมแนวทางฝึก · เริ่มจากห้าโน้ต แล้วค่อยเพิ่ม Chopin และ Mozart", style = MaterialTheme.typography.bodyMedium)
                    Text("ดูชุดฝึกและที่มาของโน้ต →", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(12.dp))
                vm.pieces.forEachIndexed { i, s ->
                    val p = progress[s.id]
                    val isOpen = open == s.id
                    val passed = hands.filter { (h, _) -> p != null && PieceRules.isPassed(p, h) }
                    Column(Modifier.fillMaxWidth().clickable { open = if (isOpen) null else s.id }.padding(vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("%02d".format(i + 1), fontFamily = Tokens.Fonts.mono, fontSize = 13.sp,
                                color = if (isOpen) Tokens.Palette.Ink else Tokens.Palette.Dim, modifier = Modifier.width(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontFamily = Tokens.Fonts.display, fontWeight = if (isOpen) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = if (isOpen) 22.sp else 18.sp, color = Tokens.Palette.Ink)
                                Text(
                                    if (passed.isNotEmpty()) stringResource(R.string.songs_passed, passed.joinToString { it.second })
                                    else s.composer.substringBefore(" เรียบเรียง"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (passed.isNotEmpty()) Tokens.Palette.Ink else Tokens.Palette.InkMuted, maxLines = 1,
                                )
                            }
                            Text("Lv.${s.level.removePrefix("L")}", fontFamily = Tokens.Fonts.mono, fontSize = 12.sp,
                                color = if (isOpen) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                        }
                        if (isOpen) {
                            if ("not yet checked" in s.source) Text(stringResource(R.string.songs_unverified), style = MaterialTheme.typography.bodySmall,
                                color = Tokens.Palette.InkMuted, modifier = Modifier.padding(start = 38.dp, top = 8.dp))
                            HandChoice(hands, Modifier.padding(start = 38.dp, top = 14.dp)) { onPlay(s.id, it) }
                        }
                    }
                    Hairline()
                }
            }
        }
    }
}

/** Three joined buttons on one white outline; each starts the song with that hand. */
@Composable
private fun HandChoice(hands: List<Pair<String, String>>, modifier: Modifier, onPick: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(modifier.fillMaxWidth().height(52.dp).clip(shape).border(1.dp, Tokens.Palette.Ink, shape)) {
        hands.forEachIndexed { i, (h, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Tokens.Palette.Ink))
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (i == 0) Modifier.background(Tokens.Palette.Ink) else Modifier)
                    .clickable(role = Role.Button) { onPick(h) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (i == 0) Tokens.Palette.OnInk else Tokens.Palette.Ink)
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
