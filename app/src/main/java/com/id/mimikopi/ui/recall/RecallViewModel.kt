package com.id.mimikopi.ui.recall

import com.id.mimikopi.core.i18n.tr
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.recall.*
import com.id.mimikopi.data.db.SettingKeys
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

internal data class RecallRun(val songId: String, val phraseId: String, val revision: Int,
    val hand: RecallHand, val presses: Int = 0, val helped: Boolean = false, val paused: Boolean = false, val id: String = UUID.randomUUID().toString())

class RecallViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val repo = services.privateSongs
    val songs = repo.songs
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    internal val run = MutableStateFlow<RecallRun?>(null)
    val noSound = MutableStateFlow(false)
    val names = MutableStateFlow(NoteNameStyle.BOTH)
    val pressed = services.inputHub.pressed
    private var foreground = false
    private var holding = false
    private val audioLock = Mutex()
    init {
        refresh()
        viewModelScope.launch {
            services.inputHub.events.collect { e ->
                if (foreground && e is InputEvent.NoteOn && e.velocity > 0) run.update { r ->
                    r?.let { if (it.paused) it else it.copy(presses = it.presses + 1) }
                }
            }
        }
    }
    fun refresh() = viewModelScope.launch {
        runCatching { repo.reload() }.onFailure { _message.value = tr("อ่านเพลงส่วนตัวไม่ได้ ข้อมูลเดิมยังไม่ถูกเขียนทับ", "Couldn't read the private song; the existing data hasn't been overwritten", "個人曲を読み込めません。既存のデータは上書きされていません") }
        services.database.settingsDao().get(SettingKeys.NOTE_NAME_STYLE)?.let { runCatching { names.value = NoteNameStyle.valueOf(it) } }
    }
    fun enter() {
        foreground = true
        refresh()
        services.appScope.launch {
            audioLock.withLock {
                if (foreground && !holding) {
                    holding = withContext(Dispatchers.IO) { services.piano.acquire() }
                    noSound.value = !holding
                    if (!foreground && holding) { services.piano.release(); holding = false }
                }
            }
        }
    }
    fun leave() {
        foreground = false
        run.update { it?.copy(paused = true) }
        services.inputHub.releaseAll()
        services.appScope.launch { audioLock.withLock { if (holding) { services.piano.release(); holding = false } } }
    }
    private fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        services.appScope.launch {
            try { block() } catch (e: Exception) { _message.value = e.message?.take(180) ?: tr("บันทึกไม่ได้ โปรดลองอีกครั้ง", "Couldn't save, please try again", "保存できませんでした。もう一度お試しください") }
            finally { _busy.value = false }
        }
    }
    fun clearMessage() { _message.value = null }
    fun song(id: String?, title: String, url: String, done: () -> Unit) = action {
        if (id == null) repo.add(title, url) else repo.editSong(id, title, url)
        done()
    }
    fun phrase(songId: String, id: String?, title: String, from: String, to: String, cue: String, done: () -> Unit) = action {
        val start = VideoReference.seconds(from) ?: error(tr("เวลาเริ่มไม่ถูกต้อง เช่น 0:42", "Start time isn't valid, e.g. 0:42", "開始時刻が正しくありません 例 0:42"))
        val end = VideoReference.seconds(to) ?: error(tr("เวลาจบไม่ถูกต้อง เช่น 0:48", "End time isn't valid, e.g. 0:48", "終了時刻が正しくありません 例 0:48"))
        if (id == null) repo.addPhrase(songId, PrivatePhrase(UUID.randomUUID().toString(), title.trim(), start, end, cue.trim()))
        else repo.changePhrase(songId, id) { p -> PrivateRecall.editPhrase(p, title.trim(), start, end, cue.trim()) }
        done()
    }
    fun delete(songId: String, phraseId: String?) = action {
        services.backupService.autoBackup("before-private-delete")
        if (phraseId == null) repo.delete(songId) else repo.removePhrase(songId, phraseId)
    }
    fun start(song: PrivateSong, phrase: PrivatePhrase, hand: RecallHand) {
        if (_busy.value) return
        services.inputHub.releaseAll()
        run.value = RecallRun(song.id, phrase.id, phrase.revision, hand)
        _message.value = null
    }
    fun hint() { run.update { it?.copy(helped = true) } }
    fun resume() { run.update { it?.copy(paused = false, helped = true) } }
    fun cancelRun() { run.value = null; services.inputHub.releaseAll() }
    fun finish(outcome: RecallOutcome, unaided: Boolean, external: Boolean, note: String, beforeViewing: Boolean) {
        val r = run.value ?: return
        if (r.paused) { _message.value = tr("รอบนี้พักอยู่ ให้เริ่มใหม่หรือกลับมาลองต่อก่อน", "This round is paused; start again or continue first", "この回は一時停止中です。新しく始めるか続けてください"); return }
        if (r.presses == 0 && !external) { _message.value = tr("ยังไม่ได้รับการกดคีย์ หรือระบุว่าเล่นบนเปียโนที่ไม่ได้ต่อแอป", "No key presses yet; or say you played on a piano not connected to the app", "まだ鍵盤入力がありません。またはアプリにつないでいないピアノで弾いたと選んでください"); return }
        if (outcome == RecallOutcome.INDEPENDENT && (r.helped || !unaided)) {
            _message.value = tr("รอบที่ใช้คำใบ้ให้นับเป็นใช้ตัวช่วย หรือเริ่มรอบใหม่โดยไม่เปิดคำตอบ", "A round with the cue shown counts as using help; or start a new round without showing the answer", "ヒントを見た回は補助ありとして数えます。または答えを見ずに新しく始めてください")
            return
        }
        action {
            val attempt = RecallAttempt(UUID.randomUUID().toString(), LocalDate.now().toString(), r.hand, outcome,
                r.revision, note.trim().take(300), r.presses, external, r.helped, unaided, beforeViewing && outcome == RecallOutcome.INDEPENDENT)
            repo.changePhrase(r.songId, r.phraseId) { p -> PrivateRecall.record(p, attempt) }
            run.value = null
            services.inputHub.releaseAll()
            _message.value = when (outcome) {
                RecallOutcome.INDEPENDENT -> tr("บันทึกว่าเล่นเองได้ในรอบนี้ · เป็นการประเมินของคุณ เริ่มทวนก่อนดูต้นทางในวันถัดไป", "Saved as played on your own this round · your own assessment; recall before watching the source starts tomorrow", "この回は自分で弾けたと保存 · あなたの評価です。明日から元を見る前に思い出す復習が始まります")
                RecallOutcome.HELPED -> tr("บันทึกการฝึกพร้อมตัวช่วยแล้ว · ลองปิดคำตอบแล้วเล่นช่วงเดิมอีกครั้ง", "Practice with help saved · try the same section again with the answer hidden", "補助ありの練習を保存 · 答えを隠して同じ区間をもう一度")
                RecallOutcome.STUCK -> tr("บันทึกจุดที่ติดแล้ว · ลองลดช่วงหรือแยกมือ ก่อนลองเล่นเองอีกครั้ง", "Sticking point saved · try a shorter section or hands separately before trying alone again", "つまずいた箇所を保存 · 区間を短くするか片手ずつ練習してから再挑戦")
            }
        }
    }
    fun exportSong(id: String, uri: Uri) = action {
        val song = repo.songs.value.first { it.id == id }
        withContext(Dispatchers.IO) {
            val data = PrivateRecall.export(song).toByteArray(Charsets.UTF_8)
            require(data.size <= PrivateRecall.MAX_BYTES) { tr("ข้อมูลเพลงใหญ่เกินขนาดที่รองรับ", "The song data is larger than supported", "曲データが対応サイズを超えています") }
            services.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) } ?: error(tr("เปิดไฟล์ปลายทางไม่ได้", "Couldn't open the target file", "保存先のファイルを開けません"))
        }
        _message.value = tr("ส่งออกเพลงและประวัติไปไฟล์ที่เลือกแล้ว", "Exported the song and history to the chosen file", "曲と履歴を選んだファイルへエクスポートしました")
    }
    fun importSong(uri: Uri) = action {
        val text = withContext(Dispatchers.IO) {
            services.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                while (out.size() <= PrivateRecall.MAX_BYTES) {
                    val read = stream.read(buffer, 0, minOf(buffer.size, PrivateRecall.MAX_BYTES + 1 - out.size()))
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                val bytes = out.toByteArray()
                require(bytes.size <= PrivateRecall.MAX_BYTES) { tr("ไฟล์ใหญ่เกิน 4 MB", "File is larger than 4 MB", "ファイルが4 MBを超えています") }
                bytes.toString(Charsets.UTF_8)
            } ?: error(tr("เปิดไฟล์ไม่ได้", "Couldn't open the file", "ファイルを開けません"))
        }
        repo.importCopy(text)
        _message.value = tr("นำเข้าเป็นสำเนาเพลงส่วนตัวแล้ว ไม่เขียนทับเพลงเดิม", "Imported as a copy of the private song, the existing one isn't overwritten", "個人曲のコピーとしてインポートしました。既存の曲は上書きしません")
    }
    override fun onCleared() { leave(); super.onCleared() }
}
