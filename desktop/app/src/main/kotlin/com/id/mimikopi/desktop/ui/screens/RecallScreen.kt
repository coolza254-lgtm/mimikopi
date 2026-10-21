package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.recall.PrivatePhrase
import com.id.mimikopi.core.recall.PrivateRecall
import com.id.mimikopi.core.recall.PrivateSong
import com.id.mimikopi.core.recall.RecallHand
import com.id.mimikopi.core.recall.RecallOutcome
import com.id.mimikopi.core.recall.VideoReference
import com.id.mimikopi.desktop.recall.RecallRun
import com.id.mimikopi.desktop.recall.RecallSession
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PageCard
import com.id.mimikopi.desktop.ui.components.PianoKeys
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.theme.Tokens
import java.net.URI
import java.time.LocalDate

/** A text field that tells the window it has the keyboard, so typing never plays notes. */
@Composable
fun TextInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val keys = LocalApp.current.keys
    OutlinedTextField(value, onChange, label = { Text(label) }, maxLines = maxLines, singleLine = maxLines == 1,
        modifier = modifier.onFocusChanged { keys.textInput = it.isFocused })
}

/** Opens a link in the person's browser (only when they click). */
fun openLink(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }
}

/**
 * Private songs: split a song from a video into short phrases, pause and play them from memory, note where it got
 * stuck and review across days. Self-assessed (no reference notes judge these songs); stored only on this computer.
 */
@Composable
fun RecallScreen() {
    val app = LocalApp.current
    val s = app.services
    val scope = rememberCoroutineScope()
    val vm = remember { RecallSession(s, scope) }
    val songs by vm.songs.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val run by vm.run.collectAsState()
    val pressed by s.hub.pressed.collectAsState()
    val ports by s.midi.ports.collectAsState()
    val lowC by s.keysLowC.collectAsState()
    var songId by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<String?>(null) }
    var phraseId by remember { mutableStateOf<String?>(null) }
    var hand by remember { mutableStateOf(RecallHand.RIGHT) }
    var deleting by remember { mutableStateOf(false) }
    val song = songs.firstOrNull { it.id == songId }
    val phrase = song?.phrases?.firstOrNull { it.id == run?.phraseId }
    DisposableEffect(vm) {
        vm.enter()
        app.keys.onEscape = {
            when {
                run != null -> { vm.cancelRun(); true }
                songId != null -> { songId = null; true }
                else -> false
            }
        }
        onDispose {
            app.keys.onEscape = null
            app.keys.textInput = false
            vm.leave()
        }
    }
    fun openSource(p: PrivatePhrase) {
        val url = song?.sourceUrl?.let { VideoReference.at(it, p.startSeconds) } ?: return
        vm.hint()
        openLink(url)
    }
    Page {
        LargeTitle(if (run != null) "ลองเล่นเอง · ประเมินเอง" else "เพลงส่วนตัว · ฝึกจำ", onBack = {
            when { run != null -> vm.cancelRun(); songId != null -> songId = null; else -> app.nav.back() }
        })
        message?.let { Text(it, color = Tokens.Palette.Ink) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val r = run
        when {
            r != null && song != null && phrase != null -> {
                RecallTrial(song.title, phrase, r, busy, vm::hint, { openSource(phrase) }, song.sourceUrl.isNotBlank(), vm::resume,
                    { vm.start(song, phrase, r.hand) }, vm::finish, vm::cancelRun,
                    canCold = PrivateRecall.current(phrase, r.hand).none { it.day == LocalDate.now().toString() })
                if (ports.none { it.open }) PianoKeys(36..96, pressed, { n, down -> s.hub.dispatch(keyEvent(n, down)) }, computerKeysLowC = lowC, height = 120.dp)
                else Text("ต่อ MIDI อยู่ · เล่นบนเปียโน", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }
            song != null -> RecallSongContent(song, hand, busy, { hand = it }, { p -> vm.start(song, p, hand) }, ::openSource,
                { phraseId = null; vm.clearMessage(); form = "phrase" }, { p -> phraseId = p.id; vm.clearMessage(); form = "phrase" },
                { vm.clearMessage(); form = "song" },
                { chooseFile("ส่งออกเพลงส่วนตัว", save = true, suggested = "mimikopi-private-song.json")?.let { vm.exportSong(song.id, it) } },
                { phraseId = null; deleting = true }, { p -> phraseId = p.id; deleting = true })
            else -> RecallLibrary(songs, busy, { songId = it }, { vm.clearMessage(); form = "song" },
                { chooseFile("นำเข้าเพลงส่วนตัว", save = false)?.let { vm.importSong(it) } })
        }
    }
    if (form == "song") SongForm(song, busy, message, { form = null; app.keys.textInput = false }) { title, url ->
        vm.song(song?.id, title, url) { form = null; app.keys.textInput = false }
    }
    if (form == "phrase" && song != null) PhraseForm(song.phrases.firstOrNull { it.id == phraseId }, busy, message, { form = null; app.keys.textInput = false }) { title, start, end, cue ->
        vm.phrase(song.id, phraseId, title, start, end, cue) { form = null; app.keys.textInput = false }
    }
    if (deleting && song != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("ลบ${if (phraseId == null) "เพลง" else "ช่วง"}ส่วนตัวนี้?") },
        text = { Text("ผลฝึกของรายการนี้จะถูกนำออกด้วย โปรแกรมเก็บสำเนาข้อมูลในเครื่องก่อนลบ") },
        confirmButton = { TextButton(onClick = { vm.delete(song.id, phraseId); deleting = false; if (phraseId == null) songId = null }, enabled = !busy) { Text("ลบ") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("ยกเลิก") } })
}

@Composable
private fun RecallLibrary(songs: List<PrivateSong>, busy: Boolean, onOpen: (String) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    Text("จากเล่นตามวิดีโอ ไปสู่เล่นเองได้", fontSize = 22.sp, color = Tokens.Palette.Ink)
    Text("แบ่งวลีสั้น · ปิดต้นทางแล้วลองเอง · ทวนก่อนเปิดดูในวันถัดไป", color = Tokens.Palette.InkMuted)
    Text("ข้อมูลเก็บในเครื่องนี้ ไม่ส่งขึ้น GitHub ไม่รวมในตัวติดตั้ง และไม่ถูกรวมอัตโนมัติเมื่อนำเข้าข้อมูลจากมือถือ", color = Tokens.Palette.InkMuted)
    PrimaryButton("เพิ่มเพลงส่วนตัว", onAdd, enabled = !busy)
    SecondaryButton("นำเข้าไฟล์เพลงส่วนตัวที่ส่งออกไว้ (จากมือถือหรือเครื่องนี้)", onImport, enabled = !busy)
    if (songs.isEmpty()) Text("เริ่มจากชื่อเพลง ลิงก์วิดีโอ และช่วงสั้น ๆ ยังไม่ต้องมีโน้ตทั้งเพลง", color = Tokens.Palette.Ink)
    songs.forEach { s ->
        PageCard(title = s.title, modifier = Modifier.clickable { onOpen(s.id) }) {
            Text("${s.phrases.size} ช่วง · ประเมินการเล่นด้วยตนเอง", color = Tokens.Palette.InkMuted)
            val due = PrivateRecall.next(s, RecallHand.RIGHT, LocalDate.now())
            Text(due?.let { "เริ่มทวน/ลองเอง: ${it.title}" } ?: "เปิดดูช่วงและผลแยกตามมือ", color = Tokens.Palette.Ink)
        }
    }
}

@Composable
private fun RecallSongContent(song: PrivateSong, hand: RecallHand, busy: Boolean, onHand: (RecallHand) -> Unit,
    onStart: (PrivatePhrase) -> Unit, onSource: (PrivatePhrase) -> Unit, onAdd: () -> Unit,
    onEdit: (PrivatePhrase) -> Unit, onEditSong: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
    onDeletePhrase: (PrivatePhrase) -> Unit) {
    Text(song.title, fontSize = 24.sp, color = Tokens.Palette.Ink)
    Text("ผลต่อไปนี้เป็นการประเมินของคุณ · ยังไม่มีโน้ตอ้างอิงให้โปรแกรมตรวจความถูกต้อง", color = Tokens.Palette.InkMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { RecallHand.entries.forEach { h -> FilterChip(hand == h, { onHand(h) }, label = { Text(h.title) }) } }
    val next = PrivateRecall.next(song, hand, LocalDate.now())
    if (next != null) PageCard(title = "ลองเองก่อนเปิดดู · ${hand.title}") {
        Text("${next.title} · ${VideoReference.clock(next.startSeconds)}–${VideoReference.clock(next.endSeconds)}", color = Tokens.Palette.Ink)
        Text("หยุดวิดีโอและเสียงนำ แล้วลองเรียกคืนจากความจำก่อน", color = Tokens.Palette.InkMuted)
        PrimaryButton("เริ่มลองเอง", { onStart(next) }, enabled = !busy)
    } else if (song.phrases.isNotEmpty()) Text("ยังไม่มีช่วงถึงกำหนดทวนสำหรับมือนี้ เลือกทบทวนเองหรือเพิ่มวลีใหม่ได้", color = Tokens.Palette.Ink)
    SecondaryButton("เพิ่มช่วงสั้นจากวิดีโอ", onAdd, enabled = !busy)
    song.phrases.forEach { p ->
        PageCard(title = p.title) {
            Text("${VideoReference.clock(p.startSeconds)}–${VideoReference.clock(p.endSeconds)} · ${hand.title}", color = Tokens.Palette.InkMuted)
            val current = PrivateRecall.current(p, hand)
            val days = PrivateRecall.rememberedDays(p, hand)
            val independent = PrivateRecall.independentDays(p, hand)
            Text(when {
                current.isEmpty() -> "ยังไม่มีผลของมือและฉบับนี้"
                days.size >= 2 -> "ลองก่อนเปิดดูได้ ${days.size} วัน · ประเมินเอง"
                days.size == 1 -> "ลองก่อนเปิดดูได้ 1 วัน · ยังควรทวนอีกวัน"
                independent.isNotEmpty() -> "เล่นเองได้ในรอบฝึก · ยังไม่ยืนยันการทวนก่อนดูข้ามวัน"
                else -> "กำลังฝึกด้วยตัวช่วย/ซ่อมจุดติด"
            }, color = Tokens.Palette.Ink)
            PrivateRecall.due(p, hand)?.let { Text("เสนอทวน $it · เลือกฝึกเองได้ทุกเวลา", color = Tokens.Palette.InkMuted) }
            current.lastOrNull()?.let { a -> Text("ล่าสุด ${a.day}: ${a.outcome.title}" + if (a.note.isBlank()) "" else " · ${a.note}", color = Tokens.Palette.Ink) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("ลองเล่นเองช่วงนี้", { onStart(p) }, enabled = !busy)
                if (song.sourceUrl.isNotBlank()) TextButton(onClick = { onSource(p) }) { Text("เปิดต้นทางที่เวลาเริ่ม (นับเป็นตัวช่วย)") }
                TextButton(onClick = { onEdit(p) }, enabled = !busy) { Text("แก้ช่วง/คำใบ้") }
                TextButton(onClick = { onDeletePhrase(p) }, enabled = !busy) { Text("ลบช่วง") }
            }
            if (p.attempts.isNotEmpty()) {
                Text("ผลล่าสุดแยกมือและฉบับ", color = Tokens.Palette.Ink)
                p.attempts.takeLast(5).asReversed().forEach { a ->
                    Text("${a.day} · ${a.hand.title} · ${a.outcome.title}" + if (a.revision != p.revision) " (ฉบับก่อนแก้)" else "", color = Tokens.Palette.InkMuted)
                }
            }
        }
    }
    Text("เริ่มช่วงถัดไปโดยตรงบ้าง แล้วฝึกคร่อมรอยต่อ · ระยะทวนเริ่มที่ 1, 3 และ 7 วัน ปรับตามผล ไม่ใช่การรับรองว่าจะจำได้ถาวร", color = Tokens.Palette.InkMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onEditSong, enabled = !busy) { Text("แก้ชื่อ/ลิงก์เพลง") }
        TextButton(onClick = onExport, enabled = !busy) { Text("ส่งออกเพลงและผลฝึกเป็นไฟล์") }
        TextButton(onClick = onDelete, enabled = !busy) { Text("ลบเพลงส่วนตัวนี้") }
    }
}

@Composable
private fun RecallTrial(title: String, phrase: PrivatePhrase, run: RecallRun, busy: Boolean, onHint: () -> Unit,
    onSource: () -> Unit, hasSource: Boolean, onResume: () -> Unit, onRestart: () -> Unit,
    onFinish: (RecallOutcome, Boolean, Boolean, String, Boolean) -> Unit, onCancel: () -> Unit, canCold: Boolean) {
    var unaided by remember(run.id, run.helped) { mutableStateOf(false) }
    var cold by remember(run.id) { mutableStateOf(false) }
    var external by remember(run.id) { mutableStateOf(false) }
    var note by remember(run.id) { mutableStateOf("") }
    Text("$title · ${phrase.title}", fontSize = 22.sp, color = Tokens.Palette.Ink)
    Text("${run.hand.title} · ${VideoReference.clock(phrase.startSeconds)}–${VideoReference.clock(phrase.endSeconds)}", color = Tokens.Palette.InkMuted)
    Text("เล่นโดยไม่มีวิดีโอ โน้ต หรือเสียงนำ แล้วประเมินผลตามจริง โปรแกรมไม่ได้ตรวจระดับเสียงหรือจังหวะของเพลงนี้", color = Tokens.Palette.Ink)
    if (run.paused) {
        Text("รอบนี้ถูกพักเมื่อออกจากหน้า · กลับมาลองต่อจะนับว่าใช้ตัวช่วย หรือเริ่มรอบใหม่", color = Tokens.Palette.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SecondaryButton("กลับมาลองต่อ · ใช้ตัวช่วย", onResume); SecondaryButton("เริ่มรอบใหม่", onRestart) }
    }
    if (phrase.cue.isNotBlank()) {
        if (run.helped) Text("คำใบ้ของคุณ: ${phrase.cue}", color = Tokens.Palette.Ink)
        else TextButton(onClick = onHint, enabled = !run.paused) { Text("ขอดูคำใบ้ของฉัน (นับเป็นตัวช่วย)") }
    }
    if (hasSource) TextButton(onClick = onSource) { Text("เปิดวิดีโอต้นทาง · นับว่าใช้ตัวช่วย") }
    Text("รับการกดคีย์ ${run.presses} ครั้ง · ไม่ใช่จำนวนโน้ตที่ถูก", color = Tokens.Palette.InkMuted)
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(external, { external = it }); Text("ฉันเล่นบนเปียโนที่ไม่ได้ต่อโปรแกรม", color = Tokens.Palette.Ink) }
    if (!run.helped) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(unaided, { unaided = it }); Text("ระหว่างรอบนี้ฉันไม่ได้ดูคำตอบหรือเปิดเสียงนำ", color = Tokens.Palette.Ink) }
    else Text("รอบนี้ใช้ตัวช่วย · ไม่ใช้เป็นหลักฐานว่าเล่นจากความจำเอง", color = Tokens.Palette.InkMuted)
    if (!run.helped && canCold) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cold, { cold = it }); Text("รอบแรกของมือนี้วันนี้: ฉันลองก่อนเปิดดู/ฟังต้นทาง", color = Tokens.Palette.Ink) }
    TextInput(note, { note = it.take(300) }, "ติดตรงไหน / ครั้งหน้าควรซ่อมอะไร", Modifier.fillMaxWidth(), maxLines = 3)
    val canSave = !busy && !run.paused && (run.presses > 0 || external)
    PrimaryButton("เล่นเองได้ · บันทึกการประเมิน", { onFinish(RecallOutcome.INDEPENDENT, unaided, external, note, cold && canCold) }, enabled = canSave && unaided && !run.helped)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("เล่นได้โดยใช้ตัวช่วย", { onFinish(RecallOutcome.HELPED, false, external, note, false) }, enabled = canSave)
        SecondaryButton("ยังติด · เก็บจุดที่ต้องซ่อม", { onFinish(RecallOutcome.STUCK, false, external, note, false) }, enabled = canSave)
        SecondaryButton("ยกเลิกรอบนี้โดยไม่บันทึกผล (Esc)", onCancel, enabled = !busy)
    }
}

@Composable
private fun SongForm(song: PrivateSong?, busy: Boolean, message: String?, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(song?.id) { mutableStateOf(song?.title ?: "") }
    var url by remember(song?.id) { mutableStateOf(song?.sourceUrl ?: "") }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (song == null) "เพิ่มเพลงส่วนตัว" else "แก้เพลงส่วนตัว") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(title, { title = it.take(120) }, "ชื่อเพลงที่ตั้งเอง")
            TextInput(url, { url = it.take(300) }, "ลิงก์วิดีโอ YouTube · เว้นว่างได้")
            Text("เก็บเพียงลิงก์ ไม่ดาวน์โหลดหรือแยกเสียงวิดีโอ เปลี่ยนต้นทางแล้วผลเก่ายังคงอยู่ในฉบับก่อน", color = Tokens.Palette.InkMuted)
            message?.let { Text(it, color = Tokens.Palette.Ink) }
        } },
        confirmButton = { TextButton(onClick = { onSave(title, url) }, enabled = !busy && title.isNotBlank() && VideoReference.canonical(url) != null) { Text("บันทึก") } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("ยกเลิก") } })
}

@Composable
private fun PhraseForm(p: PrivatePhrase?, busy: Boolean, message: String?, onClose: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember(p?.id) { mutableStateOf(p?.title ?: "วลี A") }
    var start by remember(p?.id) { mutableStateOf(p?.let { VideoReference.clock(it.startSeconds) } ?: "0:00") }
    var end by remember(p?.id) { mutableStateOf(p?.let { VideoReference.clock(it.endSeconds) } ?: "0:08") }
    var cue by remember(p?.id) { mutableStateOf(p?.cue ?: "") }
    val from = VideoReference.seconds(start)
    val to = VideoReference.seconds(end)
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text("ช่วงสั้นและคำใบ้ของฉัน") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("เริ่มเพียง 1–2 ห้องหรือไม่กี่วินาที ยังไม่ต้องมีโน้ตทั้งเพลง", color = Tokens.Palette.InkMuted)
            TextInput(title, { title = it.take(120) }, "ชื่อช่วง")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(start, { start = it.take(9) }, "เริ่ม เช่น 0:42", Modifier.weight(1f))
                TextInput(end, { end = it.take(9) }, "จบ เช่น 0:48", Modifier.weight(1f))
            }
            TextInput(cue, { cue = it.take(500) }, "คำใบ้ที่คุณเขียนเอง · จะซ่อนตอนลองเล่น", maxLines = 5)
            Text("แก้เวลา/คำใบ้แล้วผลเดิมจะแยกเป็นฉบับก่อน เพื่อไม่ให้นับแทนช่วงใหม่", color = Tokens.Palette.InkMuted)
            message?.let { Text(it, color = Tokens.Palette.Ink) }
        } },
        confirmButton = { TextButton(onClick = { onSave(title, start, end, cue) }, enabled = !busy && title.isNotBlank() && from != null && to != null && to > from) { Text("บันทึกช่วง") } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("ยกเลิก") } })
}
