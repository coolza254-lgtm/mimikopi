package com.id.mimikopi.ui.recall

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.core.recall.*
import com.id.mimikopi.ui.components.*
import com.id.mimikopi.ui.reading.ReadingKeyboard
import com.id.mimikopi.ui.theme.Tokens
import java.time.LocalDate

@Composable
fun RecallScreen(onBack: () -> Unit, onMidi: () -> Unit, onSound: () -> Unit, initialSong: String? = null, initialHand: String? = null, vm: RecallViewModel = viewModel()) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val run by vm.run.collectAsStateWithLifecycle()
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    val noSound by vm.noSound.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    var songId by rememberSaveable { mutableStateOf(initialSong) }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var phraseId by rememberSaveable { mutableStateOf<String?>(null) }
    var handName by rememberSaveable { mutableStateOf(initialHand?.takeIf { h -> RecallHand.entries.any { it.name == h } } ?: RecallHand.RIGHT.name) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    val hand = RecallHand.valueOf(handName)
    val song = songs.firstOrNull { it.id == songId }
    val phrase = song?.phrases?.firstOrNull { it.id == run?.phraseId }
    val context = LocalContext.current
    val connected = midiConnected()
    val touch = rememberTouchPlayer()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportId; if (uri != null && id != null) vm.exportSong(id, uri)
        exportId = null
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSong(uri) }
    fun back() { when { run != null -> vm.cancelRun(); songId != null -> songId = null; else -> onBack() } }
    BackHandler { back() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.enter() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.leave() }
    DisposableEffect(Unit) { onDispose { vm.leave() } }
    fun openSource(p: PrivatePhrase) {
        val url = song?.sourceUrl?.let { VideoReference.at(it, p.startSeconds) } ?: return
        vm.hint()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    ScreenScaffold(if (run != null) "ลองเล่นเอง · ประเมินเอง" else "เพลงส่วนตัว · ฝึกจำ", ::back) {
        if (message != null) Text(message!!, color = Tokens.Palette.Ink, modifier = Modifier.testTag("recall-message"))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            run != null && song != null && phrase != null -> {
                RecallTrial(song.title, phrase, run!!, busy, { vm.hint() }, { openSource(phrase) },
                    song.sourceUrl.isNotBlank(), vm::resume, { vm.start(song, phrase, run!!.hand) }, vm::finish, vm::cancelRun,
                    canCold = PrivateRecall.current(phrase, run!!.hand).none { it.day == LocalDate.now().toString() })
                if (noSound) {
                    Text("เสียงแอปยังไม่พร้อม · เปียโน MIDI ที่มีลำโพงยังใช้เสียงของตัวเองได้", color = Tokens.Palette.InkMuted)
                    TextButton(onClick = onSound) { Text("ตั้งค่าเสียงเปียโนบนมือถือ") }
                }
                ReadingKeyboard(connected, 36..96, names, false, pressed, touch, enabled = !run!!.paused && !busy,
                    twoHands = run!!.hand == RecallHand.BOTH)
                TextButton(onClick = onMidi) { Text("การเชื่อมต่อ MIDI") }
            }
            song != null -> RecallSongContent(song, hand, busy, { handName = it.name },
                { p -> vm.start(song, p, hand) }, ::openSource,
                { phraseId = null; vm.clearMessage(); form = "phrase" },
                { p -> phraseId = p.id; vm.clearMessage(); form = "phrase" },
                { vm.clearMessage(); form = "song" },
                { exportId = song.id; export.launch("mimikopi-private-song.json") },
                { phraseId = null; deleting = true }, { p -> phraseId = p.id; deleting = true })
            else -> RecallLibrary(songs, busy, { songId = it }, { vm.clearMessage(); form = "song" },
                { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        }
    }
    if (form == "song") SongForm(song, busy, message, { form = null }) { title, url ->
        vm.song(song?.id, title, url) { form = null }
    }
    if (form == "phrase" && song != null) PhraseForm(song.phrases.firstOrNull { it.id == phraseId }, busy, message,
        { form = null }) { title, start, end, cue -> vm.phrase(song.id, phraseId, title, start, end, cue) { form = null } }
    if (deleting && song != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("ลบ${if (phraseId == null) "เพลง" else "ช่วง"}ส่วนตัวนี้?") },
        text = { Text("ผลฝึกของรายการนี้จะถูกนำออกด้วย ระบบจะเก็บสำเนาข้อมูลในเครื่องก่อนลบ") },
        confirmButton = { TextButton(onClick = { vm.delete(song.id, phraseId); deleting = false }, enabled = !busy) { Text("ลบ") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("ยกเลิก") } })
}

@Composable
internal fun RecallLibrary(songs: List<PrivateSong>, busy: Boolean, onOpen: (String) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    Text("จากเล่นตามวิดีโอ ไปสู่เล่นเองได้", style = MaterialTheme.typography.headlineSmall, color = Tokens.Palette.Ink)
    Text("แบ่งวลีสั้น · ปิดต้นทางแล้วลองเอง · ทวนก่อนเปิดดูในวันถัดไป", color = Tokens.Palette.InkMuted)
    Text("ข้อมูลเก็บในเครื่อง ไม่มีการส่งเพลงขึ้น GitHub หรือซิงก์เพลงส่วนตัวผ่าน LAN อัตโนมัติ", color = Tokens.Palette.InkMuted)
    Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("recall-add-song")) { Text("เพิ่มเพลงส่วนตัว") }
    TextButton(onClick = onImport, enabled = !busy) { Text("นำเข้าไฟล์เพลงส่วนตัวที่สำรองไว้") }
    if (songs.isEmpty()) Text("เริ่มจากชื่อเพลง ลิงก์วิดีโอ และช่วงสั้น ๆ ยังไม่ต้องมีโน้ตทั้งเพลง", color = Tokens.Palette.Ink)
    songs.forEach { s ->
        PageCard(title = s.title, modifier = Modifier.testTag("recall-song-${s.id}"), onClick = { onOpen(s.id) }) {
            Text("${s.phrases.size} ช่วง · ประเมินการเล่นด้วยตนเอง", color = Tokens.Palette.InkMuted)
            val due = PrivateRecall.next(s, RecallHand.RIGHT, LocalDate.now())
            Text(due?.let { "เริ่มทวน/ลองเอง: ${it.title}" } ?: "เปิดดูช่วงและผลแยกตามมือ", color = Tokens.Palette.Ink)
        }
    }
}

@Composable
internal fun RecallSongContent(song: PrivateSong, hand: RecallHand, busy: Boolean, onHand: (RecallHand) -> Unit,
    onStart: (PrivatePhrase) -> Unit, onSource: (PrivatePhrase) -> Unit, onAdd: () -> Unit,
    onEdit: (PrivatePhrase) -> Unit, onEditSong: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
    onDeletePhrase: (PrivatePhrase) -> Unit) {
    Text(song.title, style = MaterialTheme.typography.headlineSmall, color = Tokens.Palette.Ink)
    Text("ผลต่อไปนี้เป็นการประเมินของคุณ · ยังไม่มีโน้ตอ้างอิงให้แอปตรวจความถูกต้อง", color = Tokens.Palette.InkMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { RecallHand.entries.forEach { h ->
        FilterChip(selected = hand == h, onClick = { onHand(h) }, label = { Text(h.title) }, modifier = Modifier.testTag("recall-hand-${h.name}"))
    } }
    val next = PrivateRecall.next(song, hand, LocalDate.now())
    if (next != null) PageCard(title = "ลองเองก่อนเปิดดู · ${hand.title}") {
        Text("${next.title} · ${VideoReference.clock(next.startSeconds)}–${VideoReference.clock(next.endSeconds)}", color = Tokens.Palette.Ink)
        Text("หยุดวิดีโอและเสียงนำ แล้วลองเรียกคืนจากความจำก่อน", color = Tokens.Palette.InkMuted)
        Button(onClick = { onStart(next) }, enabled = !busy, modifier = Modifier.testTag("recall-next")) { Text("เริ่มลองเอง") }
    } else if (song.phrases.isNotEmpty()) Text("ยังไม่มีช่วงถึงกำหนดทวนสำหรับมือนี้ เลือกทบทวนเองหรือเพิ่มวลีใหม่ได้", color = Tokens.Palette.Ink)
    Button(onClick = onAdd, enabled = !busy, modifier = Modifier.testTag("recall-add-phrase")) { Text("เพิ่มช่วงสั้นจากวิดีโอ") }
    song.phrases.forEach { p -> PageCard(title = p.title, modifier = Modifier.testTag("recall-phrase-${p.id}")) {
        Text("${VideoReference.clock(p.startSeconds)}–${VideoReference.clock(p.endSeconds)} · ${hand.title}", color = Tokens.Palette.InkMuted)
        val current = PrivateRecall.current(p, hand)
        val days = PrivateRecall.rememberedDays(p, hand)
        val independent = PrivateRecall.independentDays(p, hand)
        Text(when { current.isEmpty() -> "ยังไม่มีผลของมือและฉบับนี้"; days.size >= 2 -> "ลองก่อนเปิดดูได้ ${days.size} วัน · ประเมินเอง"; days.size == 1 -> "ลองก่อนเปิดดูได้ 1 วัน · ยังควรทวนอีกวัน"; independent.isNotEmpty() -> "เล่นเองได้ในรอบฝึก · ยังไม่ยืนยันการทวนก่อนดูข้ามวัน"; else -> "กำลังฝึกด้วยตัวช่วย/ซ่อมจุดติด" }, color = Tokens.Palette.Ink)
        PrivateRecall.due(p, hand)?.let { Text("เสนอทวน $it · เลือกฝึกเองได้ทุกเวลา", color = Tokens.Palette.InkMuted) }
        current.lastOrNull()?.let { a -> Text("ล่าสุด ${a.day}: ${a.outcome.title}" + if (a.note.isBlank()) "" else " · ${a.note}", color = Tokens.Palette.Ink) }
        Button(onClick = { onStart(p) }, enabled = !busy, modifier = Modifier.testTag("recall-start-${p.id}")) { Text("ลองเล่นเองช่วงนี้") }
        if (song.sourceUrl.isNotBlank()) TextButton(onClick = { onSource(p) }) { Text("เปิดต้นทางที่เวลาเริ่ม · หยุดเองที่เวลาจบ") }
        Row { TextButton(onClick = { onEdit(p) }, enabled = !busy) { Text("แก้ช่วง/คำใบ้") }; TextButton(onClick = { onDeletePhrase(p) }, enabled = !busy) { Text("ลบช่วง") } }
        if (p.attempts.isNotEmpty()) {
            Text("ผลล่าสุดแยกมือและฉบับ", style = MaterialTheme.typography.labelLarge, color = Tokens.Palette.Ink)
            p.attempts.takeLast(5).asReversed().forEach { a -> Text("${a.day} · ${a.hand.title} · ${a.outcome.title}" + if (a.revision != p.revision) " (ฉบับก่อนแก้)" else "", color = Tokens.Palette.InkMuted) }
        }
    } }
    Text("เริ่มช่วงถัดไปโดยตรงบ้าง แล้วฝึกคร่อมรอยต่อ อย่าวนจากต้นเพลงอย่างเดียว · เกณฑ์ทวนเริ่มที่ 1, 3 และ 7 วัน ปรับตามผล ไม่ใช่การรับรองว่าจะจำได้ถาวร", color = Tokens.Palette.InkMuted)
    TextButton(onClick = onEditSong, enabled = !busy) { Text("แก้ชื่อ/ลิงก์เพลง") }
    TextButton(onClick = onExport, enabled = !busy) { Text("ส่งออกเพลงและผลฝึกไปไฟล์ในเครื่อง/USB") }
    TextButton(onClick = onDelete, enabled = !busy) { Text("ลบเพลงส่วนตัวนี้") }
}

@Composable
internal fun RecallTrial(title: String, phrase: PrivatePhrase, run: RecallRun, busy: Boolean, onHint: () -> Unit,
    onSource: () -> Unit, hasSource: Boolean, onResume: () -> Unit, onRestart: () -> Unit,
    onFinish: (RecallOutcome, Boolean, Boolean, String, Boolean) -> Unit, onCancel: () -> Unit, canCold: Boolean = true) {
    var unaided by rememberSaveable(run.id, run.helped) { mutableStateOf(false) }
    var cold by rememberSaveable(run.id) { mutableStateOf(false) }
    var external by rememberSaveable(run.id) { mutableStateOf(false) }
    var note by rememberSaveable(run.id) { mutableStateOf("") }
    Text("$title · ${phrase.title}", style = MaterialTheme.typography.titleLarge, color = Tokens.Palette.Ink)
    Text("${run.hand.title} · ${VideoReference.clock(phrase.startSeconds)}–${VideoReference.clock(phrase.endSeconds)}", color = Tokens.Palette.InkMuted)
    Text("เล่นโดยไม่มีวิดีโอ โน้ต หรือเสียงนำ แล้วประเมินผลตามจริง แอปไม่ได้ตรวจระดับเสียงหรือจังหวะของเพลงนี้", color = Tokens.Palette.Ink)
    if (run.paused) {
        Text("รอบนี้ถูกพักเมื่อออกจากหน้า · กลับมาลองต่อจะนับว่าใช้ตัวช่วย หรือเริ่มรอบใหม่", color = Tokens.Palette.Ink)
        Button(onClick = onResume) { Text("กลับมาลองต่อ · ใช้ตัวช่วย") }
        TextButton(onClick = onRestart) { Text("เริ่มรอบใหม่") }
    }
    if (phrase.cue.isNotBlank()) {
        if (run.helped) Text("คำใบ้ของคุณ: ${phrase.cue}", color = Tokens.Palette.Ink, modifier = Modifier.testTag("recall-cue"))
        else TextButton(onClick = onHint, enabled = !run.paused, modifier = Modifier.testTag("recall-hint")) { Text("ขอดูคำใบ้ของฉัน") }
    }
    if (hasSource) TextButton(onClick = onSource) { Text("เปิดวิดีโอต้นทาง · นับว่าใช้ตัวช่วย") }
    Text("รับการกดคีย์ ${run.presses} ครั้ง · ไม่ใช่จำนวนโน้ตที่ถูก", color = Tokens.Palette.InkMuted)
    Row { Checkbox(checked = external, onCheckedChange = { external = it }); Text("ฉันเล่นบนเปียโนที่ไม่ได้ต่อแอป", color = Tokens.Palette.Ink) }
    if (!run.helped) Row { Checkbox(checked = unaided, onCheckedChange = { unaided = it }, modifier = Modifier.testTag("recall-unaided")); Text("ระหว่างรอบนี้ฉันไม่ได้ดูคำตอบหรือเปิดเสียงนำ", color = Tokens.Palette.Ink) }
    else Text("รอบนี้ใช้ตัวช่วย · ไม่ใช้เป็นหลักฐานว่าเล่นจากความจำเอง", color = Tokens.Palette.InkMuted)
    if (!run.helped && canCold) Row {
        Checkbox(checked = cold, onCheckedChange = { cold = it }, modifier = Modifier.testTag("recall-cold"))
        Text("รอบแรกของมือนี้วันนี้: ฉันลองก่อนเปิดดู/ฟังต้นทาง", color = Tokens.Palette.Ink)
    }
    OutlinedTextField(note, { note = it.take(300) }, label = { Text("ติดตรงไหน / ครั้งหน้าควรซ่อมอะไร") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    val canSave = !busy && !run.paused && (run.presses > 0 || external)
    Button(onClick = { onFinish(RecallOutcome.INDEPENDENT, unaided, external, note, cold && canCold) }, enabled = canSave && unaided && !run.helped,
        modifier = Modifier.fillMaxWidth().testTag("recall-independent")) { Text("เล่นเองได้ · บันทึกการประเมิน") }
    OutlinedButton(onClick = { onFinish(RecallOutcome.HELPED, false, external, note, false) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("เล่นได้โดยใช้ตัวช่วย") }
    OutlinedButton(onClick = { onFinish(RecallOutcome.STUCK, false, external, note, false) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("ยังติด · เก็บจุดที่ต้องซ่อม") }
    TextButton(onClick = onCancel, enabled = !busy) { Text("ยกเลิกรอบนี้โดยไม่บันทึกผล") }
}

@Composable
private fun SongForm(song: PrivateSong?, busy: Boolean, message: String?, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable(song?.id) { mutableStateOf(song?.title ?: "") }
    var url by rememberSaveable(song?.id) { mutableStateOf(song?.sourceUrl ?: "") }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (song == null) "เพิ่มเพลงส่วนตัว" else "แก้เพลงส่วนตัว") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it.take(120) }, label = { Text("ชื่อเพลงที่ตั้งเอง") }, modifier = Modifier.testTag("recall-song-title"))
            OutlinedTextField(url, { url = it.take(300) }, label = { Text("ลิงก์วิดีโอ YouTube · เว้นว่างได้") })
            Text("เก็บเพียงลิงก์ ไม่ดาวน์โหลดวิดีโอ เปลี่ยนต้นทางแล้วผลเก่ายังคงอยู่ในฉบับก่อน", color = Tokens.Palette.InkMuted)
            message?.let { Text(it, color = Tokens.Palette.Ink) }
        } }, confirmButton = { TextButton(onClick = { onSave(title, url) }, enabled = !busy && title.isNotBlank() && VideoReference.canonical(url) != null) { Text("บันทึก") } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("ยกเลิก") } })
}

@Composable
private fun PhraseForm(p: PrivatePhrase?, busy: Boolean, message: String?, onClose: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by rememberSaveable(p?.id) { mutableStateOf(p?.title ?: "วลี A") }
    var start by rememberSaveable(p?.id) { mutableStateOf(p?.let { VideoReference.clock(it.startSeconds) } ?: "0:00") }
    var end by rememberSaveable(p?.id) { mutableStateOf(p?.let { VideoReference.clock(it.endSeconds) } ?: "0:08") }
    var cue by rememberSaveable(p?.id) { mutableStateOf(p?.cue ?: "") }
    val from = VideoReference.seconds(start); val to = VideoReference.seconds(end)
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text("ช่วงสั้นและคำใบ้ของฉัน") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("เริ่มเพียง 1–2 ห้องหรือไม่กี่วินาที ยังไม่ต้องมีโน้ตทั้งเพลง", color = Tokens.Palette.InkMuted)
            OutlinedTextField(title, { title = it.take(120) }, label = { Text("ชื่อช่วง") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it.take(9) }, label = { Text("เริ่ม เช่น 0:42") }, modifier = Modifier.weight(1f))
                OutlinedTextField(end, { end = it.take(9) }, label = { Text("จบ เช่น 0:48") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(cue, { cue = it.take(500) }, label = { Text("คำใบ้ที่คุณเขียนเอง · จะซ่อนตอนลองเล่น") }, maxLines = 5)
            Text("แก้เวลา/คำใบ้แล้วผลเดิมจะแยกเป็นฉบับก่อน เพื่อไม่ให้นับแทนช่วงใหม่", color = Tokens.Palette.InkMuted)
            message?.let { Text(it, color = Tokens.Palette.Ink) }
        } }, confirmButton = { TextButton(onClick = { onSave(title, start, end, cue) }, enabled = !busy && title.isNotBlank() && from != null && to != null && to > from) { Text("บันทึกช่วง") } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("ยกเลิก") } })
}
