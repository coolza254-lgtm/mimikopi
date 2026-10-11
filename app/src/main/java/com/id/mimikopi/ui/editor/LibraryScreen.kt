package com.id.mimikopi.ui.editor

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.interchange.ImportException
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.pieces.ScoreFiles
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val repo = services.pieces
    private val files = ScoreFiles(app)
    val scores = repo.userScores
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        viewModelScope.launch { repo.load() }
    }

    /** Creates an empty score and returns its id. */
    suspend fun create(meta: ScoreMeta): String {
        val id = repo.newUserId()
        repo.saveUserScore(Timeline.empty(meta.copy(id = id), 4).toPieceFile())
        return id
    }

    fun newMeta() = ScoreMeta("", "เพลงใหม่")

    fun importFile(uri: Uri, then: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val id = repo.newUserId()
                val imported = withContext(Dispatchers.IO) { files.import(uri, id) }
                repo.saveUserScore(imported.piece)
                _message.value = "นำเข้า \"${imported.piece.title}\" แล้ว (${imported.piece.rh.size} ห้อง)" +
                    if (imported.skipped > 0) " · ข้ามบางส่วนที่ยังไม่รองรับ ${imported.skipped} จุด (เช่น เสียงที่สองในบรรทัดเดียวกัน)" else ""
                then(id)
            } catch (e: ImportException) {
                _message.value = "นำเข้าไม่ได้: ${e.message}"
            } catch (e: Exception) {
                _message.value = "นำเข้าไม่ได้: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun export(id: String, kind: String, uri: Uri) {
        val f = scores.value[id] ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val s = Score.from(f)
                    when (kind) {
                        "xml" -> files.exportMusicXml(s, uri)
                        "mid" -> files.exportMidi(s, uri)
                        else -> files.exportPdf(s, uri)
                    }
                }
            }.onSuccess { _message.value = "ส่งออกแล้ว" }.onFailure { _message.value = "ส่งออกไม่ได้: ${it.message}" }
        }
    }

    fun duplicate(id: String) {
        val f = scores.value[id] ?: return
        viewModelScope.launch { repo.saveUserScore(f.copy(id = repo.newUserId(), title = f.title + " (สำเนา)")) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteUserScore(id) }
    }

    fun dismiss() { _message.value = null }
}

/** "My scores": write new scores, import MusicXML/MIDI, export MusicXML/MIDI/PDF. All stay on the phone. */
@Composable
fun LibraryScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onPlay: (String) -> Unit, vm: LibraryViewModel = viewModel()) {
    val scores by vm.scores.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PieceFile?>(null) }
    var exporting by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFile(it) { id -> onEdit(id) } } }
    val xmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.recordare.musicxml+xml")) { uri ->
        val e = exporting; if (uri != null && e != null) vm.export(e.first, "xml", uri)
    }
    val midLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/midi")) { uri ->
        val e = exporting; if (uri != null && e != null) vm.export(e.first, "mid", uri)
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val e = exporting; if (uri != null && e != null) vm.export(e.first, "pdf", uri)
    }

    ScreenScaffold(title = stringResource(R.string.library_title), onBack = onBack) {
        Text(stringResource(R.string.library_intro), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
            Button(onClick = { creating = true }, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.library_new)) }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.library_import)) }
        }
        message?.let { m ->
            Row { Text(m, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.reader_done)) } }
        }
        if (scores.isEmpty()) Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted)
        scores.values.sortedBy { it.title.lowercase() }.forEach { f ->
            PageCard(title = f.title) {
                Text(stringResource(R.string.library_meta, f.rh.size, f.time[0], f.time[1], f.tempo) + if (f.composer.isNotBlank()) " · ${f.composer}" else "",
                    style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                    TextButton(onClick = { onEdit(f.id) }) { Text(stringResource(R.string.library_edit)) }
                    TextButton(onClick = { onPlay(f.id) }) { Text(stringResource(R.string.editor_play_along)) }
                    TextButton(onClick = { exporting = f.id to "xml"; xmlLauncher.launch(ScoreFiles.safeName(f.title) + ".musicxml") }) { Text("MusicXML") }
                    TextButton(onClick = { exporting = f.id to "mid"; midLauncher.launch(ScoreFiles.safeName(f.title) + ".mid") }) { Text("MIDI") }
                    TextButton(onClick = { exporting = f.id to "pdf"; pdfLauncher.launch(ScoreFiles.safeName(f.title) + ".pdf") }) { Text("PDF") }
                    TextButton(onClick = { vm.duplicate(f.id) }) { Text(stringResource(R.string.library_duplicate)) }
                    TextButton(onClick = { deleting = f }) { Text(stringResource(R.string.library_delete)) }
                }
            }
        }
        Text(stringResource(R.string.library_copyright), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
    }

    if (creating) MetaDialog(vm.newMeta(), onDismiss = { creating = false }, onSave = { m ->
        creating = false
        scope.launch { onEdit(vm.create(m)) }
    })
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.library_delete_title, f.title)) },
            text = { Text(stringResource(R.string.library_delete_body)) },
            confirmButton = { TextButton(onClick = { vm.delete(f.id); deleting = null }) { Text(stringResource(R.string.library_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.reader_done)) } },
        )
    }
}
