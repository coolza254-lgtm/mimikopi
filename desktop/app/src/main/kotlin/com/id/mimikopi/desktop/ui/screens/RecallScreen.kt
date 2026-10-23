package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
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
fun RecallScreen(initialSong: String? = null, initialHand: String? = null) {
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
    var songId by remember { mutableStateOf(initialSong) }
    var form by remember { mutableStateOf<String?>(null) }
    var phraseId by remember { mutableStateOf<String?>(null) }
    var hand by remember { mutableStateOf(initialHand?.let { h -> RecallHand.entries.firstOrNull { it.name == h } } ?: RecallHand.RIGHT) }
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
        LargeTitle(if (run != null) tr("ลองเล่นเอง · ประเมินเอง", "Try it yourself · self-assessed", "自分で弾く · 自己評価") else tr("เพลงส่วนตัว · ฝึกจำ", "Private songs · recall", "個人曲 · 暗記練習"), onBack = {
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
                else Text(tr("ต่อ MIDI อยู่ · เล่นบนเปียโน", "MIDI connected · play on the piano", "MIDI接続中 · ピアノで弾く"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }
            song != null -> RecallSongContent(song, hand, busy, { hand = it }, { p -> vm.start(song, p, hand) }, ::openSource,
                { phraseId = null; vm.clearMessage(); form = "phrase" }, { p -> phraseId = p.id; vm.clearMessage(); form = "phrase" },
                { vm.clearMessage(); form = "song" },
                { chooseFile(tr("ส่งออกเพลงส่วนตัว", "Export private song", "個人曲をエクスポート"), save = true, suggested = "mimikopi-private-song.json")?.let { vm.exportSong(song.id, it) } },
                { phraseId = null; deleting = true }, { p -> phraseId = p.id; deleting = true })
            else -> RecallLibrary(songs, busy, { songId = it }, { vm.clearMessage(); form = "song" },
                { chooseFile(tr("นำเข้าเพลงส่วนตัว", "Import private song", "個人曲をインポート"), save = false)?.let { vm.importSong(it) } })
        }
    }
    if (form == "song") SongForm(song, busy, message, { form = null; app.keys.textInput = false }) { title, url ->
        vm.song(song?.id, title, url) { form = null; app.keys.textInput = false }
    }
    if (form == "phrase" && song != null) PhraseForm(song.phrases.firstOrNull { it.id == phraseId }, busy, message, { form = null; app.keys.textInput = false }) { title, start, end, cue ->
        vm.phrase(song.id, phraseId, title, start, end, cue) { form = null; app.keys.textInput = false }
    }
    if (deleting && song != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("ลบ${if (phraseId == null) tr("เพลง", "Songs", "曲") else tr("ช่วง", "section", "区間")}ส่วนตัวนี้?") },
        text = { Text(tr("ผลฝึกของรายการนี้จะถูกนำออกด้วย โปรแกรมเก็บสำเนาข้อมูลในเครื่องก่อนลบ", "This item's practice results will be removed too. The app keeps a local copy before deleting", "この項目の練習結果も削除されます。削除前に端末内にコピーを保存します")) },
        confirmButton = { TextButton(onClick = { vm.delete(song.id, phraseId); deleting = false; if (phraseId == null) songId = null }, enabled = !busy) { Text(tr("ลบ", "Delete", "削除")) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(tr("ยกเลิก", "Cancel", "キャンセル")) } })
}

@Composable
private fun RecallLibrary(songs: List<PrivateSong>, busy: Boolean, onOpen: (String) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    Text(tr("จากเล่นตามวิดีโอ ไปสู่เล่นเองได้", "From playing along with a video to playing on your own", "動画に合わせて弾くことから、自分で弾けるまで"), fontSize = 22.sp, color = Tokens.Palette.Ink)
    Text(tr("แบ่งวลีสั้น · ปิดต้นทางแล้วลองเอง · ทวนก่อนเปิดดูในวันถัดไป", "Short phrases · turn off the source and try · recall before looking on later days", "短いフレーズに分ける · 元を止めて自分で · 翌日以降は見る前に思い出す"), color = Tokens.Palette.InkMuted)
    Text(tr("ข้อมูลเก็บในเครื่องนี้ ไม่ส่งขึ้น GitHub ไม่รวมในตัวติดตั้ง และไม่ถูกรวมอัตโนมัติเมื่อนำเข้าข้อมูลจากมือถือ", "Data stays on this computer: never sent to GitHub, not in the installer, and not merged automatically when importing phone data", "データはこのPCに保存：GitHubに送らず、インストーラーにも含まれず、スマホのデータのインポート時にも自動で統合されません"), color = Tokens.Palette.InkMuted)
    PrimaryButton(tr("เพิ่มเพลงส่วนตัว", "Add a private song", "個人曲を追加"), onAdd, enabled = !busy)
    SecondaryButton(tr("นำเข้าไฟล์เพลงส่วนตัวที่ส่งออกไว้ (จากมือถือหรือเครื่องนี้)", "Import an exported private song file (from the phone or this computer)", "エクスポートした個人曲ファイルをインポート（スマホかこのPCから）"), onImport, enabled = !busy)
    if (songs.isEmpty()) Text(tr("เริ่มจากชื่อเพลง ลิงก์วิดีโอ และช่วงสั้น ๆ ยังไม่ต้องมีโน้ตทั้งเพลง", "Start with the song name, a video link and a short section; you don't need the full sheet", "曲名、動画リンク、短い区間から。全曲の楽譜はまだ不要です"), color = Tokens.Palette.Ink)
    songs.forEach { s ->
        PageCard(title = s.title, modifier = Modifier.clickable { onOpen(s.id) }) {
            Text(tr("${s.phrases.size} ช่วง · ประเมินการเล่นด้วยตนเอง", "${s.phrases.size} sections · self-assessed playing", "${s.phrases.size}区間 · 演奏は自己評価"), color = Tokens.Palette.InkMuted)
            val due = PrivateRecall.next(s, RecallHand.RIGHT, LocalDate.now())
            Text(due?.let { tr("เริ่มทวน/ลองเอง: ${it.title}", "Start recall / try: ${it.title}", "復習・自分で：${it.title}") } ?: tr("เปิดดูช่วงและผลแยกตามมือ", "See sections and results per hand", "区間と手ごとの結果を見る"), color = Tokens.Palette.Ink)
        }
    }
}

@Composable
private fun RecallSongContent(song: PrivateSong, hand: RecallHand, busy: Boolean, onHand: (RecallHand) -> Unit,
    onStart: (PrivatePhrase) -> Unit, onSource: (PrivatePhrase) -> Unit, onAdd: () -> Unit,
    onEdit: (PrivatePhrase) -> Unit, onEditSong: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
    onDeletePhrase: (PrivatePhrase) -> Unit) {
    Text(song.title, fontSize = 24.sp, color = Tokens.Palette.Ink)
    Text(tr("ผลต่อไปนี้เป็นการประเมินของคุณ · ยังไม่มีโน้ตอ้างอิงให้โปรแกรมตรวจความถูกต้อง", "These results are your own assessment · there is no reference score for the app to check", "以下の結果はあなた自身の評価です · アプリが確認できる参照楽譜はまだありません"), color = Tokens.Palette.InkMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { RecallHand.entries.forEach { h -> FilterChip(hand == h, { onHand(h) }, label = { Text(h.title) }) } }
    val next = PrivateRecall.next(song, hand, LocalDate.now())
    if (next != null) PageCard(title = tr("ลองเองก่อนเปิดดู · ${hand.title}", "Try before looking · ${hand.title}", "見る前に自分で · ${hand.title}")) {
        Text("${next.title} · ${VideoReference.clock(next.startSeconds)}–${VideoReference.clock(next.endSeconds)}", color = Tokens.Palette.Ink)
        Text(tr("หยุดวิดีโอและเสียงนำ แล้วลองเรียกคืนจากความจำก่อน", "Stop the video and guide sound, and try to recall it first", "動画とガイド音を止めて、まず記憶から思い出す"), color = Tokens.Palette.InkMuted)
        PrimaryButton(tr("เริ่มลองเอง", "Start trying", "自分で弾く"), { onStart(next) }, enabled = !busy)
    } else if (song.phrases.isNotEmpty()) Text(tr("ยังไม่มีช่วงถึงกำหนดทวนสำหรับมือนี้ เลือกทบทวนเองหรือเพิ่มวลีใหม่ได้", "No sections due for this hand. Review on your own or add a new phrase", "この手で復習時期の区間はありません。自由に復習するか新しいフレーズを追加"), color = Tokens.Palette.Ink)
    SecondaryButton(tr("เพิ่มช่วงสั้นจากวิดีโอ", "Add a short section from the video", "動画から短い区間を追加"), onAdd, enabled = !busy)
    song.phrases.forEach { p ->
        PageCard(title = p.title) {
            Text("${VideoReference.clock(p.startSeconds)}–${VideoReference.clock(p.endSeconds)} · ${hand.title}", color = Tokens.Palette.InkMuted)
            val current = PrivateRecall.current(p, hand)
            val days = PrivateRecall.rememberedDays(p, hand)
            val independent = PrivateRecall.independentDays(p, hand)
            Text(when {
                current.isEmpty() -> tr("ยังไม่มีผลของมือและฉบับนี้", "No results for this hand and version yet", "この手とバージョンの結果はまだありません")
                days.size >= 2 -> tr("ลองก่อนเปิดดูได้ ${days.size} วัน · ประเมินเอง", "Recalled before looking on ${days.size} days · self-assessed", "見る前に思い出せた日：${days.size}日 · 自己評価")
                days.size == 1 -> tr("ลองก่อนเปิดดูได้ 1 วัน · ยังควรทวนอีกวัน", "Recalled before looking on 1 day · review another day", "見る前に思い出せた日：1日 · もう1日復習を")
                independent.isNotEmpty() -> tr("เล่นเองได้ในรอบฝึก · ยังไม่ยืนยันการทวนก่อนดูข้ามวัน", "Played alone in practice · not yet recalled before looking on another day", "練習では一人で弾けた · 別の日に見る前の復習はまだ")
                else -> tr("กำลังฝึกด้วยตัวช่วย/ซ่อมจุดติด", "Practising with help / fixing sticking points", "補助付きで練習中・つまずきを修正中")
            }, color = Tokens.Palette.Ink)
            PrivateRecall.due(p, hand)?.let { Text(tr("เสนอทวน $it · เลือกฝึกเองได้ทุกเวลา", "Suggested review $it · practise any time you like", "復習のおすすめ $it · いつでも自由に練習できます"), color = Tokens.Palette.InkMuted) }
            current.lastOrNull()?.let { a -> Text(tr("ล่าสุด ${a.day}: ${a.outcome.title}", "Last ${a.day}: ${a.outcome.title}", "最新 ${a.day}：${a.outcome.title}") + if (a.note.isBlank()) "" else " · ${a.note}", color = Tokens.Palette.Ink) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(tr("ลองเล่นเองช่วงนี้", "Play this section yourself", "この区間を自分で弾く"), { onStart(p) }, enabled = !busy)
                if (song.sourceUrl.isNotBlank()) TextButton(onClick = { onSource(p) }) { Text(tr("เปิดต้นทางที่เวลาเริ่ม (นับเป็นตัวช่วย)", "Open the source at the start time (counts as help)", "開始時刻から元動画を開く（補助ありとして数えます）")) }
                TextButton(onClick = { onEdit(p) }, enabled = !busy) { Text(tr("แก้ช่วง/คำใบ้", "Edit section / cue", "区間・ヒントを編集")) }
                TextButton(onClick = { onDeletePhrase(p) }, enabled = !busy) { Text(tr("ลบช่วง", "Delete section", "区間を削除")) }
            }
            if (p.attempts.isNotEmpty()) {
                Text(tr("ผลล่าสุดแยกมือและฉบับ", "Latest results per hand and version", "手とバージョンごとの最新結果"), color = Tokens.Palette.Ink)
                p.attempts.takeLast(5).asReversed().forEach { a ->
                    Text("${a.day} · ${a.hand.title} · ${a.outcome.title}" + if (a.revision != p.revision) tr(" (ฉบับก่อนแก้)", " (earlier version)", "（編集前のバージョン）") else "", color = Tokens.Palette.InkMuted)
                }
            }
        }
    }
    Text(tr("เริ่มช่วงถัดไปโดยตรงบ้าง แล้วฝึกคร่อมรอยต่อ · ระยะทวนเริ่มที่ 1, 3 และ 7 วัน ปรับตามผล ไม่ใช่การรับรองว่าจะจำได้ถาวร", "Sometimes start straight at the next section and practise across the joins · reviews start at 1, 3 and 7 days and adapt to results, no promise you'll remember for good", "ときどき次の区間から始めてつなぎ目を練習 · 復習は1・3・7日から始まり結果で調整、永久に覚えている保証ではありません"), color = Tokens.Palette.InkMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onEditSong, enabled = !busy) { Text(tr("แก้ชื่อ/ลิงก์เพลง", "Edit song name / link", "曲名・リンクを編集")) }
        TextButton(onClick = onExport, enabled = !busy) { Text(tr("ส่งออกเพลงและผลฝึกเป็นไฟล์", "Export the song and its practice to a file", "曲と練習結果をファイルにエクスポート")) }
        TextButton(onClick = onDelete, enabled = !busy) { Text(tr("ลบเพลงส่วนตัวนี้", "Delete this private song", "この個人曲を削除")) }
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
    Text(tr("เล่นโดยไม่มีวิดีโอ โน้ต หรือเสียงนำ แล้วประเมินผลตามจริง โปรแกรมไม่ได้ตรวจระดับเสียงหรือจังหวะของเพลงนี้", "Play without the video, sheet or guide sound, then assess honestly. The app doesn't check this song's pitches or timing", "動画・楽譜・ガイド音なしで弾き、正直に評価してください。アプリはこの曲の音程やリズムを確認しません"), color = Tokens.Palette.Ink)
    if (run.paused) {
        Text(tr("รอบนี้ถูกพักเมื่อออกจากหน้า · กลับมาลองต่อจะนับว่าใช้ตัวช่วย หรือเริ่มรอบใหม่", "This round was paused when you left · continuing counts as using help, or start a new round", "ページを離れたためこの回は一時停止中 · 続けると補助ありとして数えます。または新しく始める"), color = Tokens.Palette.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SecondaryButton(tr("กลับมาลองต่อ · ใช้ตัวช่วย", "Continue · with help", "続ける · 補助あり"), onResume); SecondaryButton(tr("เริ่มรอบใหม่", "Start a new round", "新しく始める"), onRestart) }
    }
    if (phrase.cue.isNotBlank()) {
        if (run.helped) Text(tr("คำใบ้ของคุณ: ${phrase.cue}", "Your cue: ${phrase.cue}", "あなたのヒント：${phrase.cue}"), color = Tokens.Palette.Ink)
        else TextButton(onClick = onHint, enabled = !run.paused) { Text(tr("ขอดูคำใบ้ของฉัน (นับเป็นตัวช่วย)", "Show my cue (counts as help)", "ヒントを見る（補助ありとして数えます）")) }
    }
    if (hasSource) TextButton(onClick = onSource) { Text(tr("เปิดวิดีโอต้นทาง · นับว่าใช้ตัวช่วย", "Open the source video · counts as help", "元動画を開く · 補助ありとして数えます")) }
    Text(tr("รับการกดคีย์ ${run.presses} ครั้ง · ไม่ใช่จำนวนโน้ตที่ถูก", "${run.presses} key presses received · not the number of right notes", "鍵盤入力${run.presses}回 · 正しい音の数ではありません"), color = Tokens.Palette.InkMuted)
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(external, { external = it }); Text(tr("ฉันเล่นบนเปียโนที่ไม่ได้ต่อโปรแกรม", "I played on a piano not connected to the app", "アプリにつないでいないピアノで弾いた"), color = Tokens.Palette.Ink) }
    if (!run.helped) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(unaided, { unaided = it }); Text(tr("ระหว่างรอบนี้ฉันไม่ได้ดูคำตอบหรือเปิดเสียงนำ", "During this round I didn't look at the answer or play the guide sound", "この回の間、答えを見たりガイド音を流したりしなかった"), color = Tokens.Palette.Ink) }
    else Text(tr("รอบนี้ใช้ตัวช่วย · ไม่ใช้เป็นหลักฐานว่าเล่นจากความจำเอง", "This round used help · not evidence of playing from memory", "この回は補助あり · 記憶だけで弾けた証拠にはなりません"), color = Tokens.Palette.InkMuted)
    if (!run.helped && canCold) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cold, { cold = it }); Text(tr("รอบแรกของมือนี้วันนี้: ฉันลองก่อนเปิดดู/ฟังต้นทาง", "First round for this hand today: I tried before looking at / hearing the source", "今日この手の最初の回：元を見る・聞く前に試した"), color = Tokens.Palette.Ink) }
    TextInput(note, { note = it.take(300) }, tr("ติดตรงไหน / ครั้งหน้าควรซ่อมอะไร", "Where did you get stuck / what to fix next time", "どこでつまずいたか・次に直すこと"), Modifier.fillMaxWidth(), maxLines = 3)
    val canSave = !busy && !run.paused && (run.presses > 0 || external)
    PrimaryButton(tr("เล่นเองได้ · บันทึกการประเมิน", "Played it myself · save the assessment", "自分で弾けた · 評価を保存"), { onFinish(RecallOutcome.INDEPENDENT, unaided, external, note, cold && canCold) }, enabled = canSave && unaided && !run.helped)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(tr("เล่นได้โดยใช้ตัวช่วย", "Played it with help", "補助ありで弾けた"), { onFinish(RecallOutcome.HELPED, false, external, note, false) }, enabled = canSave)
        SecondaryButton(tr("ยังติด · เก็บจุดที่ต้องซ่อม", "Still stuck · keep the spot to fix", "まだつまずく · 直す箇所を保存"), { onFinish(RecallOutcome.STUCK, false, external, note, false) }, enabled = canSave)
        SecondaryButton(tr("ยกเลิกรอบนี้โดยไม่บันทึกผล (Esc)", "Cancel this round without saving (Esc)", "保存せずにこの回をやめる（Esc）"), onCancel, enabled = !busy)
    }
}

@Composable
private fun SongForm(song: PrivateSong?, busy: Boolean, message: String?, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(song?.id) { mutableStateOf(song?.title ?: "") }
    var url by remember(song?.id) { mutableStateOf(song?.sourceUrl ?: "") }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (song == null) tr("เพิ่มเพลงส่วนตัว", "Add a private song", "個人曲を追加") else tr("แก้เพลงส่วนตัว", "Edit private song", "個人曲を編集")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(title, { title = it.take(120) }, tr("ชื่อเพลงที่ตั้งเอง", "Your own name for the song", "自分でつけた曲名"))
            TextInput(url, { url = it.take(300) }, tr("ลิงก์วิดีโอ YouTube · เว้นว่างได้", "YouTube video link · optional", "YouTube動画リンク · 空欄可"))
            Text(tr("เก็บเพียงลิงก์ ไม่ดาวน์โหลดหรือแยกเสียงวิดีโอ เปลี่ยนต้นทางแล้วผลเก่ายังคงอยู่ในฉบับก่อน", "Only the link is kept; the video isn't downloaded or split. Changing the source keeps old results in the earlier version", "リンクのみ保存し、動画のダウンロードや音声分離はしません。元を変えても以前の結果は前のバージョンに残ります"), color = Tokens.Palette.InkMuted)
            message?.let { Text(it, color = Tokens.Palette.Ink) }
        } },
        confirmButton = { TextButton(onClick = { onSave(title, url) }, enabled = !busy && title.isNotBlank() && VideoReference.canonical(url) != null) { Text(tr("บันทึก", "Save", "保存")) } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(tr("ยกเลิก", "Cancel", "キャンセル")) } })
}

@Composable
private fun PhraseForm(p: PrivatePhrase?, busy: Boolean, message: String?, onClose: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember(p?.id) { mutableStateOf(p?.title ?: tr("วลี A", "Phrase A", "フレーズA")) }
    var start by remember(p?.id) { mutableStateOf(p?.let { VideoReference.clock(it.startSeconds) } ?: "0:00") }
    var end by remember(p?.id) { mutableStateOf(p?.let { VideoReference.clock(it.endSeconds) } ?: "0:08") }
    var cue by remember(p?.id) { mutableStateOf(p?.cue ?: "") }
    val from = VideoReference.seconds(start)
    val to = VideoReference.seconds(end)
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(tr("ช่วงสั้นและคำใบ้ของฉัน", "A short section and my cue", "短い区間と自分のヒント")) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("เริ่มเพียง 1–2 ห้องหรือไม่กี่วินาที ยังไม่ต้องมีโน้ตทั้งเพลง", "Start with just 1–2 bars or a few seconds; no full sheet needed yet", "まずは1〜2小節か数秒だけ。全曲の楽譜はまだ不要です"), color = Tokens.Palette.InkMuted)
            TextInput(title, { title = it.take(120) }, tr("ชื่อช่วง", "Section name", "区間名"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(start, { start = it.take(9) }, tr("เริ่ม เช่น 0:42", "Start, e.g. 0:42", "開始 例 0:42"), Modifier.weight(1f))
                TextInput(end, { end = it.take(9) }, tr("จบ เช่น 0:48", "End, e.g. 0:48", "終了 例 0:48"), Modifier.weight(1f))
            }
            TextInput(cue, { cue = it.take(500) }, tr("คำใบ้ที่คุณเขียนเอง · จะซ่อนตอนลองเล่น", "A cue you write yourself · hidden while you try", "自分で書くヒント · 弾くときは隠れます"), maxLines = 5)
            Text(tr("แก้เวลา/คำใบ้แล้วผลเดิมจะแยกเป็นฉบับก่อน เพื่อไม่ให้นับแทนช่วงใหม่", "Changing the times or cue moves the old results to an earlier version so they don't count for the new section", "時刻やヒントを変えると以前の結果は前のバージョンに分かれ、新しい区間の分として数えません"), color = Tokens.Palette.InkMuted)
            message?.let { Text(it, color = Tokens.Palette.Ink) }
        } },
        confirmButton = { TextButton(onClick = { onSave(title, start, end, cue) }, enabled = !busy && title.isNotBlank() && from != null && to != null && to > from) { Text(tr("บันทึกช่วง", "Save section", "区間を保存")) } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(tr("ยกเลิก", "Cancel", "キャンセル")) } })
}
