package com.id.mimikopi.desktop.recall

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.recall.PrivatePhrase
import com.id.mimikopi.core.recall.PrivateRecall
import com.id.mimikopi.core.recall.PrivateSong
import com.id.mimikopi.core.recall.RecallAttempt
import com.id.mimikopi.core.recall.RecallHand
import com.id.mimikopi.core.recall.RecallOutcome
import com.id.mimikopi.core.recall.VideoReference
import com.id.mimikopi.desktop.Services
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class RecallRun(
    val songId: String, val phraseId: String, val revision: Int, val hand: RecallHand,
    val presses: Int = 0, val helped: Boolean = false, val paused: Boolean = false, val id: String = UUID.randomUUID().toString(),
)

/**
 * Practising a private song phrase from memory (the Android RecallViewModel): pick a phrase, play it without the video
 * or notes, then say honestly how it went. Everything is the person's own assessment; key presses are only counted
 * as "something was played", never judged as right notes.
 */
class RecallSession(private val services: Services, private val scope: CoroutineScope) {
    private val repo = services.privateSongs
    val songs = repo.songs
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val run = MutableStateFlow<RecallRun?>(null)
    private var foreground = false
    private var holding = false

    init {
        refresh()
        scope.launch {
            services.hub.events.collect { e ->
                if (foreground && e is InputEvent.NoteOn && e.velocity > 0) run.update { r -> r?.let { if (it.paused) it else it.copy(presses = it.presses + 1) } }
            }
        }
    }

    fun refresh() {
        runCatching { repo.reload() }.onFailure { _message.value = tr("อ่านเพลงส่วนตัวไม่ได้ ข้อมูลเดิมยังไม่ถูกเขียนทับ", "Couldn't read the private song; the existing data hasn't been overwritten", "個人曲を読み込めません。既存のデータは上書きされていません") }
    }

    fun enter() {
        foreground = true
        refresh()
        if (!holding) holding = services.piano.acquire()
    }

    fun leave() {
        foreground = false
        run.update { it?.copy(paused = true) }
        services.hub.releaseAll()
        if (holding) { services.piano.release(); holding = false }
    }

    private fun action(block: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
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
        services.transfer.autoBackup("before-private-delete")
        if (phraseId == null) repo.delete(songId) else repo.removePhrase(songId, phraseId)
    }

    fun start(song: PrivateSong, phrase: PrivatePhrase, hand: RecallHand) {
        if (_busy.value) return
        services.hub.releaseAll()
        run.value = RecallRun(song.id, phrase.id, phrase.revision, hand)
        _message.value = null
    }

    fun hint() { run.update { it?.copy(helped = true) } }
    fun resume() { run.update { it?.copy(paused = false, helped = true) } }
    fun cancelRun() { run.value = null; services.hub.releaseAll() }

    fun finish(outcome: RecallOutcome, unaided: Boolean, external: Boolean, note: String, beforeViewing: Boolean) {
        val r = run.value ?: return
        if (r.paused) { _message.value = tr("รอบนี้พักอยู่ ให้เริ่มใหม่หรือกลับมาลองต่อก่อน", "This round is paused; start again or continue first", "この回は一時停止中です。新しく始めるか続けてください"); return }
        if (r.presses == 0 && !external) { _message.value = tr("ยังไม่ได้รับการกดคีย์ หรือระบุว่าเล่นบนเปียโนที่ไม่ได้ต่อโปรแกรม", "No key presses yet; or say you played on a piano not connected to the app", "まだ鍵盤入力がありません。またはアプリにつないでいないピアノで弾いたと選んでください"); return }
        if (outcome == RecallOutcome.INDEPENDENT && (r.helped || !unaided)) {
            _message.value = tr("รอบที่ใช้คำใบ้ให้นับเป็นใช้ตัวช่วย หรือเริ่มรอบใหม่โดยไม่เปิดคำตอบ", "A round with the cue shown counts as using help; or start a new round without showing the answer", "ヒントを見た回は補助ありとして数えます。または答えを見ずに新しく始めてください")
            return
        }
        action {
            val attempt = RecallAttempt(UUID.randomUUID().toString(), LocalDate.now().toString(), r.hand, outcome,
                r.revision, note.trim().take(300), r.presses, external, r.helped, unaided, beforeViewing && outcome == RecallOutcome.INDEPENDENT)
            repo.changePhrase(r.songId, r.phraseId) { p -> PrivateRecall.record(p, attempt) }
            run.value = null
            services.hub.releaseAll()
            _message.value = when (outcome) {
                RecallOutcome.INDEPENDENT -> tr("บันทึกว่าเล่นเองได้ในรอบนี้ · เป็นการประเมินของคุณ เริ่มทวนก่อนดูต้นทางในวันถัดไป", "Saved as played on your own this round · your own assessment; recall before watching the source starts tomorrow", "この回は自分で弾けたと保存 · あなたの評価です。明日から元を見る前に思い出す復習が始まります")
                RecallOutcome.HELPED -> tr("บันทึกการฝึกพร้อมตัวช่วยแล้ว · ลองปิดคำตอบแล้วเล่นช่วงเดิมอีกครั้ง", "Practice with help saved · try the same section again with the answer hidden", "補助ありの練習を保存 · 答えを隠して同じ区間をもう一度")
                RecallOutcome.STUCK -> tr("บันทึกจุดที่ติดแล้ว · ลองลดช่วงหรือแยกมือ ก่อนลองเล่นเองอีกครั้ง", "Sticking point saved · try a shorter section or hands separately before trying alone again", "つまずいた箇所を保存 · 区間を短くするか片手ずつ練習してから再挑戦")
            }
        }
    }

    fun exportSong(id: String, file: File) = action {
        val song = repo.songs.value.first { it.id == id }
        val data = PrivateRecall.export(song).toByteArray(Charsets.UTF_8)
        require(data.size <= PrivateRecall.MAX_BYTES) { tr("ข้อมูลเพลงใหญ่เกินขนาดที่รองรับ", "The song data is larger than supported", "曲データが対応サイズを超えています") }
        file.writeBytes(data)
        _message.value = tr("ส่งออกเพลงและประวัติไปไฟล์ที่เลือกแล้ว", "Exported the song and history to the chosen file", "曲と履歴を選んだファイルへエクスポートしました")
    }

    fun importSong(file: File) = action {
        require(file.length() <= PrivateRecall.MAX_BYTES) { tr("ไฟล์ใหญ่เกิน 4 MB", "File is larger than 4 MB", "ファイルが4 MBを超えています") }
        repo.importCopy(file.readText(Charsets.UTF_8))
        _message.value = tr("นำเข้าเป็นสำเนาเพลงส่วนตัวแล้ว ไม่เขียนทับเพลงเดิม", "Imported as a copy of the private song, the existing one isn't overwritten", "個人曲のコピーとしてインポートしました。既存の曲は上書きしません")
    }
}
