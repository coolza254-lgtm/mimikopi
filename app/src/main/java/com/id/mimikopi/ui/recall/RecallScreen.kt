package com.id.mimikopi.ui.recall

import com.id.mimikopi.core.i18n.tr
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
    ScreenScaffold(if (run != null) tr("ลองเล่นเอง · ประเมินเอง", "Try it yourself · self-assessed", "自分で弾く · 自己評価") else tr("เพลงส่วนตัว · ฝึกจำ", "Private songs · recall", "個人曲 · 暗記練習"), ::back) {
        if (message != null) Text(message!!, color = Tokens.Palette.Ink, modifier = Modifier.testTag("recall-message"))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            run != null && song != null && phrase != null -> {
                RecallTrial(song.title, phrase, run!!, busy, { vm.hint() }, { openSource(phrase) },
                    song.sourceUrl.isNotBlank(), vm::resume, { vm.start(song, phrase, run!!.hand) }, vm::finish, vm::cancelRun,
                    canCold = PrivateRecall.current(phrase, run!!.hand).none { it.day == LocalDate.now().toString() })
                if (noSound) {
                    Text(tr("เสียงแอปยังไม่พร้อม · เปียโน MIDI ที่มีลำโพงยังใช้เสียงของตัวเองได้", "The app's sound isn't ready · a MIDI piano with speakers can still use its own sound", "アプリの音が未準備 · スピーカー付きMIDIピアノは本体の音を使えます"), color = Tokens.Palette.InkMuted)
                    TextButton(onClick = onSound) { Text(tr("ตั้งค่าเสียงเปียโนบนมือถือ", "Set up the piano sound on the phone", "スマホのピアノ音を設定")) }
                }
                ReadingKeyboard(connected, 36..96, names, false, pressed, touch, enabled = !run!!.paused && !busy,
                    twoHands = run!!.hand == RecallHand.BOTH)
                TextButton(onClick = onMidi) { Text(tr("การเชื่อมต่อ MIDI", "MIDI connection", "MIDI接続")) }
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
    if (deleting && song != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("ลบ${if (phraseId == null) tr("เพลง", "Songs", "曲") else tr("ช่วง", "section", "区間")}ส่วนตัวนี้?") },
        text = { Text(tr("ผลฝึกของรายการนี้จะถูกนำออกด้วย ระบบจะเก็บสำเนาข้อมูลในเครื่องก่อนลบ", "This item's practice results will be removed too. A local backup is kept before deleting", "この項目の練習結果も削除されます。削除前に端末内にバックアップを保存します")) },
        confirmButton = { TextButton(onClick = { vm.delete(song.id, phraseId); deleting = false }, enabled = !busy) { Text(tr("ลบ", "Delete", "削除")) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(tr("ยกเลิก", "Cancel", "キャンセル")) } })
}

@Composable
internal fun RecallLibrary(songs: List<PrivateSong>, busy: Boolean, onOpen: (String) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    Text(tr("จากเล่นตามวิดีโอ ไปสู่เล่นเองได้", "From playing along with a video to playing on your own", "動画に合わせて弾くことから、自分で弾けるまで"), style = MaterialTheme.typography.headlineSmall, color = Tokens.Palette.Ink)
    Text(tr("แบ่งวลีสั้น · ปิดต้นทางแล้วลองเอง · ทวนก่อนเปิดดูในวันถัดไป", "Short phrases · turn off the source and try · recall before looking on later days", "短いフレーズに分ける · 元を止めて自分で · 翌日以降は見る前に思い出す"), color = Tokens.Palette.InkMuted)
    Text(tr("ข้อมูลเก็บในเครื่อง ไม่มีการส่งเพลงขึ้น GitHub หรือซิงก์เพลงส่วนตัวผ่าน LAN อัตโนมัติ", "Data stays on the device. Songs are never sent to GitHub, and private songs don't sync over LAN automatically", "データは端末内に保存。曲はGitHubに送らず、個人曲がLANで自動同期されることもありません"), color = Tokens.Palette.InkMuted)
    Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("recall-add-song")) { Text(tr("เพิ่มเพลงส่วนตัว", "Add a private song", "個人曲を追加")) }
    TextButton(onClick = onImport, enabled = !busy) { Text(tr("นำเข้าไฟล์เพลงส่วนตัวที่สำรองไว้", "Import a backed-up private song file", "バックアップした個人曲ファイルをインポート")) }
    if (songs.isEmpty()) Text(tr("เริ่มจากชื่อเพลง ลิงก์วิดีโอ และช่วงสั้น ๆ ยังไม่ต้องมีโน้ตทั้งเพลง", "Start with the song name, a video link and a short section; you don't need the full sheet", "曲名、動画リンク、短い区間から。全曲の楽譜はまだ不要です"), color = Tokens.Palette.Ink)
    songs.forEach { s ->
        PageCard(title = s.title, modifier = Modifier.testTag("recall-song-${s.id}"), onClick = { onOpen(s.id) }) {
            Text(tr("${s.phrases.size} ช่วง · ประเมินการเล่นด้วยตนเอง", "${s.phrases.size} sections · self-assessed playing", "${s.phrases.size}区間 · 演奏は自己評価"), color = Tokens.Palette.InkMuted)
            val due = PrivateRecall.next(s, RecallHand.RIGHT, LocalDate.now())
            Text(due?.let { tr("เริ่มทวน/ลองเอง: ${it.title}", "Start recall / try: ${it.title}", "復習・自分で：${it.title}") } ?: tr("เปิดดูช่วงและผลแยกตามมือ", "See sections and results per hand", "区間と手ごとの結果を見る"), color = Tokens.Palette.Ink)
        }
    }
}

@Composable
internal fun RecallSongContent(song: PrivateSong, hand: RecallHand, busy: Boolean, onHand: (RecallHand) -> Unit,
    onStart: (PrivatePhrase) -> Unit, onSource: (PrivatePhrase) -> Unit, onAdd: () -> Unit,
    onEdit: (PrivatePhrase) -> Unit, onEditSong: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
    onDeletePhrase: (PrivatePhrase) -> Unit) {
    Text(song.title, style = MaterialTheme.typography.headlineSmall, color = Tokens.Palette.Ink)
    Text(tr("ผลต่อไปนี้เป็นการประเมินของคุณ · ยังไม่มีโน้ตอ้างอิงให้แอปตรวจความถูกต้อง", "These results are your own assessment · there is no reference score for the app to check", "以下の結果はあなた自身の評価です · アプリが確認できる参照楽譜はまだありません"), color = Tokens.Palette.InkMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { RecallHand.entries.forEach { h ->
        FilterChip(selected = hand == h, onClick = { onHand(h) }, label = { Text(h.title) }, modifier = Modifier.testTag("recall-hand-${h.name}"))
    } }
    val next = PrivateRecall.next(song, hand, LocalDate.now())
    if (next != null) PageCard(title = tr("ลองเองก่อนเปิดดู · ${hand.title}", "Try before looking · ${hand.title}", "見る前に自分で · ${hand.title}")) {
        Text("${next.title} · ${VideoReference.clock(next.startSeconds)}–${VideoReference.clock(next.endSeconds)}", color = Tokens.Palette.Ink)
        Text(tr("หยุดวิดีโอและเสียงนำ แล้วลองเรียกคืนจากความจำก่อน", "Stop the video and guide sound, and try to recall it first", "動画とガイド音を止めて、まず記憶から思い出す"), color = Tokens.Palette.InkMuted)
        Button(onClick = { onStart(next) }, enabled = !busy, modifier = Modifier.testTag("recall-next")) { Text(tr("เริ่มลองเอง", "Start trying", "自分で弾く")) }
    } else if (song.phrases.isNotEmpty()) Text(tr("ยังไม่มีช่วงถึงกำหนดทวนสำหรับมือนี้ เลือกทบทวนเองหรือเพิ่มวลีใหม่ได้", "No sections due for this hand. Review on your own or add a new phrase", "この手で復習時期の区間はありません。自由に復習するか新しいフレーズを追加"), color = Tokens.Palette.Ink)
    Button(onClick = onAdd, enabled = !busy, modifier = Modifier.testTag("recall-add-phrase")) { Text(tr("เพิ่มช่วงสั้นจากวิดีโอ", "Add a short section from the video", "動画から短い区間を追加")) }
    song.phrases.forEach { p -> PageCard(title = p.title, modifier = Modifier.testTag("recall-phrase-${p.id}")) {
        Text("${VideoReference.clock(p.startSeconds)}–${VideoReference.clock(p.endSeconds)} · ${hand.title}", color = Tokens.Palette.InkMuted)
        val current = PrivateRecall.current(p, hand)
        val days = PrivateRecall.rememberedDays(p, hand)
        val independent = PrivateRecall.independentDays(p, hand)
        Text(when { current.isEmpty() -> tr("ยังไม่มีผลของมือและฉบับนี้", "No results for this hand and version yet", "この手とバージョンの結果はまだありません"); days.size >= 2 -> tr("ลองก่อนเปิดดูได้ ${days.size} วัน · ประเมินเอง", "Recalled before looking on ${days.size} days · self-assessed", "見る前に思い出せた日：${days.size}日 · 自己評価"); days.size == 1 -> tr("ลองก่อนเปิดดูได้ 1 วัน · ยังควรทวนอีกวัน", "Recalled before looking on 1 day · review another day", "見る前に思い出せた日：1日 · もう1日復習を"); independent.isNotEmpty() -> tr("เล่นเองได้ในรอบฝึก · ยังไม่ยืนยันการทวนก่อนดูข้ามวัน", "Played alone in practice · not yet recalled before looking on another day", "練習では一人で弾けた · 別の日に見る前の復習はまだ"); else -> tr("กำลังฝึกด้วยตัวช่วย/ซ่อมจุดติด", "Practising with help / fixing sticking points", "補助付きで練習中・つまずきを修正中") }, color = Tokens.Palette.Ink)
        PrivateRecall.due(p, hand)?.let { Text(tr("เสนอทวน $it · เลือกฝึกเองได้ทุกเวลา", "Suggested review $it · practise any time you like", "復習のおすすめ $it · いつでも自由に練習できます"), color = Tokens.Palette.InkMuted) }
        current.lastOrNull()?.let { a -> Text(tr("ล่าสุด ${a.day}: ${a.outcome.title}", "Last ${a.day}: ${a.outcome.title}", "最新 ${a.day}：${a.outcome.title}") + if (a.note.isBlank()) "" else " · ${a.note}", color = Tokens.Palette.Ink) }
        Button(onClick = { onStart(p) }, enabled = !busy, modifier = Modifier.testTag("recall-start-${p.id}")) { Text(tr("ลองเล่นเองช่วงนี้", "Play this section yourself", "この区間を自分で弾く")) }
        if (song.sourceUrl.isNotBlank()) TextButton(onClick = { onSource(p) }) { Text(tr("เปิดต้นทางที่เวลาเริ่ม · หยุดเองที่เวลาจบ", "Opens the source at the start time · stop yourself at the end time", "開始時刻から元動画を開く · 終了時刻で自分で止める")) }
        Row { TextButton(onClick = { onEdit(p) }, enabled = !busy) { Text(tr("แก้ช่วง/คำใบ้", "Edit section / cue", "区間・ヒントを編集")) }; TextButton(onClick = { onDeletePhrase(p) }, enabled = !busy) { Text(tr("ลบช่วง", "Delete section", "区間を削除")) } }
        if (p.attempts.isNotEmpty()) {
            Text(tr("ผลล่าสุดแยกมือและฉบับ", "Latest results per hand and version", "手とバージョンごとの最新結果"), style = MaterialTheme.typography.labelLarge, color = Tokens.Palette.Ink)
            p.attempts.takeLast(5).asReversed().forEach { a -> Text("${a.day} · ${a.hand.title} · ${a.outcome.title}" + if (a.revision != p.revision) tr(" (ฉบับก่อนแก้)", " (earlier version)", "（編集前のバージョン）") else "", color = Tokens.Palette.InkMuted) }
        }
    } }
    Text(tr("เริ่มช่วงถัดไปโดยตรงบ้าง แล้วฝึกคร่อมรอยต่อ อย่าวนจากต้นเพลงอย่างเดียว · เกณฑ์ทวนเริ่มที่ 1, 3 และ 7 วัน ปรับตามผล ไม่ใช่การรับรองว่าจะจำได้ถาวร", "Sometimes start straight at the next section and practise across the joins, don't only loop from the beginning · reviews start at 1, 3 and 7 days and adapt to results, no promise you'll remember for good", "ときどき次の区間から始めてつなぎ目を練習し、曲頭からだけ繰り返さないこと · 復習は1・3・7日から始まり結果で調整、永久に覚えている保証ではありません"), color = Tokens.Palette.InkMuted)
    TextButton(onClick = onEditSong, enabled = !busy) { Text(tr("แก้ชื่อ/ลิงก์เพลง", "Edit song name / link", "曲名・リンクを編集")) }
    TextButton(onClick = onExport, enabled = !busy) { Text(tr("ส่งออกเพลงและผลฝึกไปไฟล์ในเครื่อง/USB", "Export the song and its practice to a file on the device / USB", "曲と練習結果を端末内／USBのファイルへエクスポート")) }
    TextButton(onClick = onDelete, enabled = !busy) { Text(tr("ลบเพลงส่วนตัวนี้", "Delete this private song", "この個人曲を削除")) }
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
    Text(tr("เล่นโดยไม่มีวิดีโอ โน้ต หรือเสียงนำ แล้วประเมินผลตามจริง แอปไม่ได้ตรวจระดับเสียงหรือจังหวะของเพลงนี้", "Play without the video, sheet or guide sound, then assess honestly. The app doesn't check this song's pitches or timing", "動画・楽譜・ガイド音なしで弾き、正直に評価してください。アプリはこの曲の音程やリズムを確認しません"), color = Tokens.Palette.Ink)
    if (run.paused) {
        Text(tr("รอบนี้ถูกพักเมื่อออกจากหน้า · กลับมาลองต่อจะนับว่าใช้ตัวช่วย หรือเริ่มรอบใหม่", "This round was paused when you left · continuing counts as using help, or start a new round", "ページを離れたためこの回は一時停止中 · 続けると補助ありとして数えます。または新しく始める"), color = Tokens.Palette.Ink)
        Button(onClick = onResume) { Text(tr("กลับมาลองต่อ · ใช้ตัวช่วย", "Continue · with help", "続ける · 補助あり")) }
        TextButton(onClick = onRestart) { Text(tr("เริ่มรอบใหม่", "Start a new round", "新しく始める")) }
    }
    if (phrase.cue.isNotBlank()) {
        if (run.helped) Text(tr("คำใบ้ของคุณ: ${phrase.cue}", "Your cue: ${phrase.cue}", "あなたのヒント：${phrase.cue}"), color = Tokens.Palette.Ink, modifier = Modifier.testTag("recall-cue"))
        else TextButton(onClick = onHint, enabled = !run.paused, modifier = Modifier.testTag("recall-hint")) { Text(tr("ขอดูคำใบ้ของฉัน", "Show my cue", "ヒントを見る")) }
    }
    if (hasSource) TextButton(onClick = onSource) { Text(tr("เปิดวิดีโอต้นทาง · นับว่าใช้ตัวช่วย", "Open the source video · counts as help", "元動画を開く · 補助ありとして数えます")) }
    Text(tr("รับการกดคีย์ ${run.presses} ครั้ง · ไม่ใช่จำนวนโน้ตที่ถูก", "${run.presses} key presses received · not the number of right notes", "鍵盤入力${run.presses}回 · 正しい音の数ではありません"), color = Tokens.Palette.InkMuted)
    Row { Checkbox(checked = external, onCheckedChange = { external = it }); Text(tr("ฉันเล่นบนเปียโนที่ไม่ได้ต่อแอป", "I played on a piano not connected to the app", "アプリにつないでいないピアノで弾いた"), color = Tokens.Palette.Ink) }
    if (!run.helped) Row { Checkbox(checked = unaided, onCheckedChange = { unaided = it }, modifier = Modifier.testTag("recall-unaided")); Text(tr("ระหว่างรอบนี้ฉันไม่ได้ดูคำตอบหรือเปิดเสียงนำ", "During this round I didn't look at the answer or play the guide sound", "この回の間、答えを見たりガイド音を流したりしなかった"), color = Tokens.Palette.Ink) }
    else Text(tr("รอบนี้ใช้ตัวช่วย · ไม่ใช้เป็นหลักฐานว่าเล่นจากความจำเอง", "This round used help · not evidence of playing from memory", "この回は補助あり · 記憶だけで弾けた証拠にはなりません"), color = Tokens.Palette.InkMuted)
    if (!run.helped && canCold) Row {
        Checkbox(checked = cold, onCheckedChange = { cold = it }, modifier = Modifier.testTag("recall-cold"))
        Text(tr("รอบแรกของมือนี้วันนี้: ฉันลองก่อนเปิดดู/ฟังต้นทาง", "First round for this hand today: I tried before looking at / hearing the source", "今日この手の最初の回：元を見る・聞く前に試した"), color = Tokens.Palette.Ink)
    }
    OutlinedTextField(note, { note = it.take(300) }, label = { Text(tr("ติดตรงไหน / ครั้งหน้าควรซ่อมอะไร", "Where did you get stuck / what to fix next time", "どこでつまずいたか・次に直すこと")) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    val canSave = !busy && !run.paused && (run.presses > 0 || external)
    Button(onClick = { onFinish(RecallOutcome.INDEPENDENT, unaided, external, note, cold && canCold) }, enabled = canSave && unaided && !run.helped,
        modifier = Modifier.fillMaxWidth().testTag("recall-independent")) { Text(tr("เล่นเองได้ · บันทึกการประเมิน", "Played it myself · save the assessment", "自分で弾けた · 評価を保存")) }
    OutlinedButton(onClick = { onFinish(RecallOutcome.HELPED, false, external, note, false) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text(tr("เล่นได้โดยใช้ตัวช่วย", "Played it with help", "補助ありで弾けた")) }
    OutlinedButton(onClick = { onFinish(RecallOutcome.STUCK, false, external, note, false) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text(tr("ยังติด · เก็บจุดที่ต้องซ่อม", "Still stuck · keep the spot to fix", "まだつまずく · 直す箇所を保存")) }
    TextButton(onClick = onCancel, enabled = !busy) { Text(tr("ยกเลิกรอบนี้โดยไม่บันทึกผล", "Cancel this round without saving", "保存せずにこの回をやめる")) }
}

@Composable
private fun SongForm(song: PrivateSong?, busy: Boolean, message: String?, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable(song?.id) { mutableStateOf(song?.title ?: "") }
    var url by rememberSaveable(song?.id) { mutableStateOf(song?.sourceUrl ?: "") }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (song == null) tr("เพิ่มเพลงส่วนตัว", "Add a private song", "個人曲を追加") else tr("แก้เพลงส่วนตัว", "Edit private song", "個人曲を編集")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it.take(120) }, label = { Text(tr("ชื่อเพลงที่ตั้งเอง", "Your own name for the song", "自分でつけた曲名")) }, modifier = Modifier.testTag("recall-song-title"))
            OutlinedTextField(url, { url = it.take(300) }, label = { Text(tr("ลิงก์วิดีโอ YouTube · เว้นว่างได้", "YouTube video link · optional", "YouTube動画リンク · 空欄可")) })
            Text(tr("เก็บเพียงลิงก์ ไม่ดาวน์โหลดวิดีโอ เปลี่ยนต้นทางแล้วผลเก่ายังคงอยู่ในฉบับก่อน", "Only the link is kept, the video isn't downloaded. Changing the source keeps old results in the earlier version", "リンクのみ保存し動画はダウンロードしません。元を変えても以前の結果は前のバージョンに残ります"), color = Tokens.Palette.InkMuted)
            message?.let { Text(it, color = Tokens.Palette.Ink) }
        } }, confirmButton = { TextButton(onClick = { onSave(title, url) }, enabled = !busy && title.isNotBlank() && VideoReference.canonical(url) != null) { Text(tr("บันทึก", "Save", "保存")) } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(tr("ยกเลิก", "Cancel", "キャンセル")) } })
}

@Composable
private fun PhraseForm(p: PrivatePhrase?, busy: Boolean, message: String?, onClose: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by rememberSaveable(p?.id) { mutableStateOf(p?.title ?: tr("วลี A", "Phrase A", "フレーズA")) }
    var start by rememberSaveable(p?.id) { mutableStateOf(p?.let { VideoReference.clock(it.startSeconds) } ?: "0:00") }
    var end by rememberSaveable(p?.id) { mutableStateOf(p?.let { VideoReference.clock(it.endSeconds) } ?: "0:08") }
    var cue by rememberSaveable(p?.id) { mutableStateOf(p?.cue ?: "") }
    val from = VideoReference.seconds(start); val to = VideoReference.seconds(end)
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(tr("ช่วงสั้นและคำใบ้ของฉัน", "A short section and my cue", "短い区間と自分のヒント")) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("เริ่มเพียง 1–2 ห้องหรือไม่กี่วินาที ยังไม่ต้องมีโน้ตทั้งเพลง", "Start with just 1–2 bars or a few seconds; no full sheet needed yet", "まずは1〜2小節か数秒だけ。全曲の楽譜はまだ不要です"), color = Tokens.Palette.InkMuted)
            OutlinedTextField(title, { title = it.take(120) }, label = { Text(tr("ชื่อช่วง", "Section name", "区間名")) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it.take(9) }, label = { Text(tr("เริ่ม เช่น 0:42", "Start, e.g. 0:42", "開始 例 0:42")) }, modifier = Modifier.weight(1f))
                OutlinedTextField(end, { end = it.take(9) }, label = { Text(tr("จบ เช่น 0:48", "End, e.g. 0:48", "終了 例 0:48")) }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(cue, { cue = it.take(500) }, label = { Text(tr("คำใบ้ที่คุณเขียนเอง · จะซ่อนตอนลองเล่น", "A cue you write yourself · hidden while you try", "自分で書くヒント · 弾くときは隠れます")) }, maxLines = 5)
            Text(tr("แก้เวลา/คำใบ้แล้วผลเดิมจะแยกเป็นฉบับก่อน เพื่อไม่ให้นับแทนช่วงใหม่", "Changing the times or cue moves the old results to an earlier version so they don't count for the new section", "時刻やヒントを変えると以前の結果は前のバージョンに分かれ、新しい区間の分として数えません"), color = Tokens.Palette.InkMuted)
            message?.let { Text(it, color = Tokens.Palette.Ink) }
        } }, confirmButton = { TextButton(onClick = { onSave(title, start, end, cue) }, enabled = !busy && title.isNotBlank() && from != null && to != null && to > from) { Text(tr("บันทึกช่วง", "Save section", "区間を保存")) } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(tr("ยกเลิก", "Cancel", "キャンセル")) } })
}
