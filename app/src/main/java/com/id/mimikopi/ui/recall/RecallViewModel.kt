package com.id.mimikopi.ui.recall

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
        runCatching { repo.reload() }.onFailure { _message.value = "อ่านเพลงส่วนตัวไม่ได้ ข้อมูลเดิมยังไม่ถูกเขียนทับ" }
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
            try { block() } catch (e: Exception) { _message.value = e.message?.take(180) ?: "บันทึกไม่ได้ โปรดลองอีกครั้ง" }
            finally { _busy.value = false }
        }
    }
    fun clearMessage() { _message.value = null }
    fun song(id: String?, title: String, url: String, done: () -> Unit) = action {
        if (id == null) repo.add(title, url) else repo.editSong(id, title, url)
        done()
    }
    fun phrase(songId: String, id: String?, title: String, from: String, to: String, cue: String, done: () -> Unit) = action {
        val start = VideoReference.seconds(from) ?: error("เวลาเริ่มไม่ถูกต้อง เช่น 0:42")
        val end = VideoReference.seconds(to) ?: error("เวลาจบไม่ถูกต้อง เช่น 0:48")
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
        if (r.paused) { _message.value = "รอบนี้พักอยู่ ให้เริ่มใหม่หรือกลับมาลองต่อก่อน"; return }
        if (r.presses == 0 && !external) { _message.value = "ยังไม่ได้รับการกดคีย์ หรือระบุว่าเล่นบนเปียโนที่ไม่ได้ต่อแอป"; return }
        if (outcome == RecallOutcome.INDEPENDENT && (r.helped || !unaided)) {
            _message.value = "รอบที่ใช้คำใบ้ให้นับเป็นใช้ตัวช่วย หรือเริ่มรอบใหม่โดยไม่เปิดคำตอบ"
            return
        }
        action {
            val attempt = RecallAttempt(UUID.randomUUID().toString(), LocalDate.now().toString(), r.hand, outcome,
                r.revision, note.trim().take(300), r.presses, external, r.helped, unaided, beforeViewing && outcome == RecallOutcome.INDEPENDENT)
            repo.changePhrase(r.songId, r.phraseId) { p -> PrivateRecall.record(p, attempt) }
            run.value = null
            services.inputHub.releaseAll()
            _message.value = when (outcome) {
                RecallOutcome.INDEPENDENT -> "บันทึกว่าเล่นเองได้ในรอบนี้ · เป็นการประเมินของคุณ เริ่มทวนก่อนดูต้นทางในวันถัดไป"
                RecallOutcome.HELPED -> "บันทึกการฝึกพร้อมตัวช่วยแล้ว · ลองปิดคำตอบแล้วเล่นช่วงเดิมอีกครั้ง"
                RecallOutcome.STUCK -> "บันทึกจุดที่ติดแล้ว · ลองลดช่วงหรือแยกมือ ก่อนลองเล่นเองอีกครั้ง"
            }
        }
    }
    fun exportSong(id: String, uri: Uri) = action {
        val song = repo.songs.value.first { it.id == id }
        withContext(Dispatchers.IO) {
            val data = PrivateRecall.export(song).toByteArray(Charsets.UTF_8)
            require(data.size <= PrivateRecall.MAX_BYTES) { "ข้อมูลเพลงใหญ่เกินขนาดที่รองรับ" }
            services.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) } ?: error("เปิดไฟล์ปลายทางไม่ได้")
        }
        _message.value = "ส่งออกเพลงและประวัติไปไฟล์ที่เลือกแล้ว"
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
                require(bytes.size <= PrivateRecall.MAX_BYTES) { "ไฟล์ใหญ่เกิน 4 MB" }
                bytes.toString(Charsets.UTF_8)
            } ?: error("เปิดไฟล์ไม่ได้")
        }
        repo.importCopy(text)
        _message.value = "นำเข้าเป็นสำเนาเพลงส่วนตัวแล้ว ไม่เขียนทับเพลงเดิม"
    }
    override fun onCleared() { leave(); super.onCleared() }
}
