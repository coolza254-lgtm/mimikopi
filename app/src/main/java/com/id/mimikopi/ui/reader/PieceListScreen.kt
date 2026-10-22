package com.id.mimikopi.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.pieces.ClassicalRepertoire
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
    val drillIndex: Int? = args.get<String>("drill")?.toIntOrNull()
    val drill: Drill? = drillIndex?.let { lesson?.drills?.getOrNull(it) }
    val hand: String? = drill?.hand
    val pieces = drill?.pieces?.mapNotNull { repo.pieceOrExercise(it) } ?: repo.pieces
    val progress = repo.progress

    init {
        viewModelScope.launch { repo.load() }
    }
}

@Composable
fun PieceListScreen(onBack: () -> Unit, onOpenPiece: (String, String?, String?, Int?) -> Unit, vm: PieceListViewModel = viewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    PieceLibrary(vm.pieces, progress, vm.lesson?.let { "${it.id} ${it.title}" }, vm.lesson?.passText, vm.hand, onBack, { id, hand -> onOpenPiece(id, hand, vm.lesson?.id, vm.drillIndex) })
}

/** Also used by render tests without starting the audio, database or MIDI services. */
@Composable
internal fun PieceLibrary(
    pieces: List<Score>,
    progress: Map<String, PieceProgress>,
    lessonTitle: String? = null,
    lessonPass: String? = null,
    hand: String? = null,
    onBack: () -> Unit,
    onOpenPiece: (String, String?) -> Unit,
) {
    var classical by rememberSaveable { mutableStateOf(true) }
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    val steps = ClassicalRepertoire.steps.associateBy { it.id }
    val suggested = classical && lessonTitle == null
    val visible = if (suggested) ClassicalRepertoire.steps.mapNotNull { step -> pieces.find { it.id == step.id } } else pieces
    ScreenScaffold(title = lessonTitle ?: stringResource(R.string.pieces_title), onBack = onBack) {
        if (lessonPass != null) {
            Text(stringResource(R.string.lesson_pass, lessonPass), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = classical, onClick = { classical = true }, label = { Text("คลาสสิก · ไล่ขั้น") }, modifier = Modifier.testTag("classical-filter"))
                FilterChip(selected = !classical, onClick = { classical = false }, label = { Text("ทุกเพลง") }, modifier = Modifier.testTag("all-pieces-filter"))
            }
            Text(if (suggested) "เริ่มทีละมือในโหมดรอให้กดถูก แล้วค่อยรวมมือและเพิ่มความเร็ว แต่ละเพลงเป็นฉบับฝึกหรือท่อนย่อ" else stringResource(R.string.pieces_intro), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink)
            if (suggested) Text("ฝึกด้วยคีย์บนมือถือหรือ MIDI ได้ · เลือกข้ามขั้นได้ทุกเพลง", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
        }
        for ((index, s) in visible.withIndex()) {
            val step = if (suggested) steps[s.id] else null
            val p = progress[s.id] ?: PieceProgress(s.id)
            val startHand = hand ?: step?.startHand
            PageCard(title = if (suggested) "${index + 1}. ${s.title}" else s.title,
                modifier = Modifier.testTag("piece-${s.id}"), onClick = { onOpenPiece(s.id, startHand) }) {
                Text(s.composer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                step?.let {
                    Text(it.focus, style = MaterialTheme.typography.bodyMedium)
                    Text(if (it.startHand == "LEFT") "เปิดด้วยมือซ้าย · เปลี่ยนเป็นสองมือได้ก่อนเริ่ม" else "เปิดด้วยมือขวา · เปลี่ยนเป็นสองมือได้ก่อนเริ่ม", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.pieces_meta, s.level, s.beatsPerMeasure, s.beatUnit, s.tempo, s.measures),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tokens.Palette.InkMuted,
                )
                val passed = PieceRules.isPassed(p, hand ?: "BOTH")
                Text(status(p, hand), style = MaterialTheme.typography.bodySmall, color = if (passed) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                TextButton(onClick = { sourceId = s.id }, modifier = Modifier.testTag("source-${s.id}")) { Text("ที่มาและการเรียบเรียง") }
            }
        }
        Text(stringResource(R.string.pieces_source_note), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
    }
    pieces.find { it.id == sourceId }?.let { score ->
        AlertDialog(
            onDismissRequest = { sourceId = null },
            title = { Text("ที่มาและการเรียบเรียง") },
            text = {
                SelectionContainer {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).testTag("piece-source")) {
                        Text(score.title, style = MaterialTheme.typography.titleSmall)
                        Text(score.source)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sourceId = null }) { Text("ปิด") } },
        )
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
