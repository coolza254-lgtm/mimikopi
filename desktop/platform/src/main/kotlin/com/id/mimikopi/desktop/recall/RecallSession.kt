package com.id.mimikopi.desktop.recall

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
        runCatching { repo.reload() }.onFailure { _message.value = "อ่านเพลงส่วนตัวไม่ได้ ข้อมูลเดิมยังไม่ถูกเขียนทับ" }
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
        if (r.paused) { _message.value = "รอบนี้พักอยู่ ให้เริ่มใหม่หรือกลับมาลองต่อก่อน"; return }
        if (r.presses == 0 && !external) { _message.value = "ยังไม่ได้รับการกดคีย์ หรือระบุว่าเล่นบนเปียโนที่ไม่ได้ต่อโปรแกรม"; return }
        if (outcome == RecallOutcome.INDEPENDENT && (r.helped || !unaided)) {
            _message.value = "รอบที่ใช้คำใบ้ให้นับเป็นใช้ตัวช่วย หรือเริ่มรอบใหม่โดยไม่เปิดคำตอบ"
            return
        }
        action {
            val attempt = RecallAttempt(UUID.randomUUID().toString(), LocalDate.now().toString(), r.hand, outcome,
                r.revision, note.trim().take(300), r.presses, external, r.helped, unaided, beforeViewing && outcome == RecallOutcome.INDEPENDENT)
            repo.changePhrase(r.songId, r.phraseId) { p -> PrivateRecall.record(p, attempt) }
            run.value = null
            services.hub.releaseAll()
            _message.value = when (outcome) {
                RecallOutcome.INDEPENDENT -> "บันทึกว่าเล่นเองได้ในรอบนี้ · เป็นการประเมินของคุณ เริ่มทวนก่อนดูต้นทางในวันถัดไป"
                RecallOutcome.HELPED -> "บันทึกการฝึกพร้อมตัวช่วยแล้ว · ลองปิดคำตอบแล้วเล่นช่วงเดิมอีกครั้ง"
                RecallOutcome.STUCK -> "บันทึกจุดที่ติดแล้ว · ลองลดช่วงหรือแยกมือ ก่อนลองเล่นเองอีกครั้ง"
            }
        }
    }

    fun exportSong(id: String, file: File) = action {
        val song = repo.songs.value.first { it.id == id }
        val data = PrivateRecall.export(song).toByteArray(Charsets.UTF_8)
        require(data.size <= PrivateRecall.MAX_BYTES) { "ข้อมูลเพลงใหญ่เกินขนาดที่รองรับ" }
        file.writeBytes(data)
        _message.value = "ส่งออกเพลงและประวัติไปไฟล์ที่เลือกแล้ว"
    }

    fun importSong(file: File) = action {
        require(file.length() <= PrivateRecall.MAX_BYTES) { "ไฟล์ใหญ่เกิน 4 MB" }
        repo.importCopy(file.readText(Charsets.UTF_8))
        _message.value = "นำเข้าเป็นสำเนาเพลงส่วนตัวแล้ว ไม่เขียนทับเพลงเดิม"
    }
}
