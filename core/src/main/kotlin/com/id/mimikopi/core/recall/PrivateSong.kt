package com.id.mimikopi.core.recall

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.time.LocalDate

@Serializable enum class RecallHand(val title: String) { RIGHT("มือขวา"), LEFT("มือซ้าย"), BOTH("สองมือ") }
@Serializable enum class RecallOutcome(val title: String) { INDEPENDENT("เล่นเองได้"), HELPED("ใช้ตัวช่วย"), STUCK("ยังติด") }
@Serializable data class RecallAttempt(
    val id: String, val day: String, val hand: RecallHand, val outcome: RecallOutcome,
    val revision: Int, val note: String = "", val presses: Int = 0, val externalPiano: Boolean = false, val hintUsed: Boolean = false, val unaidedConfirmed: Boolean = false, val beforeViewing: Boolean = false,
)
@Serializable data class PrivatePhrase(
    val id: String, val title: String, val startSeconds: Int, val endSeconds: Int,
    val cue: String = "", val revision: Int = 1, val attempts: List<RecallAttempt> = emptyList(),
    val recallDays: Map<String, List<String>> = emptyMap(), val lastDifficulty: Map<String, String> = emptyMap(),
    val coldRecallDays: Map<String, List<String>> = emptyMap(),
    /** The phrase's chords in order, as written on a chord sheet ("C", "Am7", "F/G"); none until the user adds them. */
    val chords: List<String> = emptyList(),
)
@Serializable data class PrivateSong(
    val id: String, val title: String, val sourceUrl: String = "", val phrases: List<PrivatePhrase> = emptyList(),
)
@Serializable data class PrivateSongFile(val format: String = "mimikopi-private-recall", val version: Int = 1, val song: PrivateSong)

/** Public video links only. No downloading, embedding, scraping, or executing arbitrary user schemes. */
object VideoReference {
    fun canonical(raw: String): String? {
        if (raw.isBlank()) return ""
        val u = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (u.scheme != "https" || u.userInfo != null || u.port != -1) return null
        val id = when (u.host?.lowercase()) {
            "youtu.be" -> u.path.removePrefix("/").substringBefore('/')
            "youtube.com", "www.youtube.com", "m.youtube.com" -> when {
                u.path == "/watch" -> u.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                u.path.startsWith("/shorts/") -> u.path.removePrefix("/shorts/").substringBefore('/')
                else -> null
            }
            else -> null
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube.com/watch?v=$it" }
    }
    fun at(raw: String, second: Int): String? = canonical(raw)?.takeIf { it.isNotBlank() }?.let { "$it&t=${second.coerceAtLeast(0)}s" }
    fun seconds(raw: String): Int? {
        val parts = raw.trim().split(':')
        if (parts.size !in 1..3 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.drop(1).any { it !in 0..59 }) return null
        val total = nums.fold(0L) { a, n -> a * 60 + n }
        return total.takeIf { it in 0..86_400 }?.toInt()
    }
    fun clock(seconds: Int): String = if (seconds >= 3600) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

object PrivateRecall {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val KEEP_ATTEMPTS = 64
    const val MAX_CHORDS = 64
    val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    fun validate(song: PrivateSong) {
        require(song.id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "รหัสเพลงไม่ถูกต้อง" }
        require(song.title.isNotBlank() && song.title.length <= 120) { "ตั้งชื่อเพลงไม่เกิน 120 ตัวอักษร" }
        require(song.sourceUrl.length <= 300 && VideoReference.canonical(song.sourceUrl) != null) { "ใช้ลิงก์วิดีโอ YouTube แบบ https หรือเว้นว่าง" }
        require(song.phrases.size <= 100 && song.phrases.map { it.id }.distinct().size == song.phrases.size) { "ช่วงเพลงมากเกินไปหรือรหัสซ้ำ" }
        for (p in song.phrases) {
            require(p.id.matches(Regex("[A-Za-z0-9-]{1,80}")) && p.title.isNotBlank() && p.title.length <= 120) { "ชื่อหรือรหัสช่วงไม่ถูกต้อง" }
            require(p.startSeconds in 0..86_399 && p.endSeconds in (p.startSeconds + 1)..86_400) { "เวลาจบต้องมากกว่าเวลาเริ่ม" }
            require(p.cue.length <= 500 && p.revision > 0 && p.attempts.size <= KEEP_ATTEMPTS) { "ข้อมูลช่วงยาวเกินกำหนด" }
            require(p.chords.size <= MAX_CHORDS && p.chords.all { it.isNotBlank() && it.length <= 16 }) { "คอร์ดต่อช่วงไม่เกิน $MAX_CHORDS ตัว ตัวละไม่เกิน 16 ตัวอักษร" }
            require(p.attempts.map { it.id }.distinct().size == p.attempts.size) { "รหัสผลฝึกซ้ำ" }
            for ((key, days) in p.recallDays.entries + p.coldRecallDays.entries) {
                require(key.matches(Regex("[1-9][0-9]*\\|(RIGHT|LEFT|BOTH)")) && days.size <= 20_000 && days.distinct().size == days.size)
                require(days.all { runCatching { LocalDate.parse(it).year in 1970..2200 }.getOrDefault(false) })
            }
            require(p.lastDifficulty.all { (key, day) -> key.matches(Regex("[1-9][0-9]*\\|(RIGHT|LEFT|BOTH)")) && runCatching { LocalDate.parse(day).year in 1970..2200 }.getOrDefault(false) })
            for (a in p.attempts) {
                require(a.id.matches(Regex("[A-Za-z0-9-]{1,80}")) && a.revision in 1..p.revision && a.note.length <= 300 && a.presses >= 0) { "ผลฝึกไม่ถูกต้อง" }
                require(runCatching { LocalDate.parse(a.day).year in 1970..2200 }.getOrDefault(false)) { "วันที่ผลฝึกไม่ถูกต้อง" }
                require(!a.beforeViewing || a.outcome == RecallOutcome.INDEPENDENT) { "การทวนก่อนดูต้องเป็นรอบเล่นเอง" }
                require(a.outcome != RecallOutcome.INDEPENDENT || (!a.hintUsed && a.unaidedConfirmed)) { "ผลเล่นเองต้องยืนยันว่าไม่ใช้คำตอบ" }
                require(a.presses > 0 || a.externalPiano) { "ผลฝึกต้องมีการเล่นหรือระบุว่าใช้เปียโนนอกแอป" }
            }
        }
    }
    fun export(song: PrivateSong): String { validate(song); return json.encodeToString(PrivateSongFile.serializer(), PrivateSongFile(song = song)) }
    fun import(text: String): PrivateSong {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "ไฟล์ใหญ่เกิน 4 MB" }
        val file = json.decodeFromString(PrivateSongFile.serializer(), text)
        require(file.format == "mimikopi-private-recall" && file.version == 1) { "ไฟล์ไม่ใช่เพลงส่วนตัวรุ่นที่รองรับ" }
        validate(file.song)
        return file.song
    }
    fun record(p: PrivatePhrase, attempt: RecallAttempt): PrivatePhrase {
        require(attempt.revision == p.revision && (attempt.presses > 0 || attempt.externalPiano))
        require(p.attempts.none { it.id == attempt.id })
        require(attempt.outcome != RecallOutcome.INDEPENDENT || (!attempt.hintUsed && attempt.unaidedConfirmed))
        require(!attempt.beforeViewing || (attempt.outcome == RecallOutcome.INDEPENDENT && p.attempts.none {
            it.revision == p.revision && it.hand == attempt.hand && it.day == attempt.day
        }))
        val key = "${p.revision}|${attempt.hand.name}"
        return p.copy(attempts = (p.attempts + attempt).takeLast(KEEP_ATTEMPTS),
            recallDays = if (attempt.outcome == RecallOutcome.INDEPENDENT) p.recallDays + (key to (p.recallDays[key].orEmpty() + attempt.day).distinct().sorted()) else p.recallDays,
            coldRecallDays = if (attempt.beforeViewing) p.coldRecallDays + (key to (p.coldRecallDays[key].orEmpty() + attempt.day).distinct().sorted()) else p.coldRecallDays,
            lastDifficulty = if (attempt.outcome != RecallOutcome.INDEPENDENT) p.lastDifficulty + (key to attempt.day) else p.lastDifficulty)
    }
    fun current(p: PrivatePhrase, hand: RecallHand) = p.attempts.filter { it.revision == p.revision && it.hand == hand }
    fun independentDays(p: PrivatePhrase, hand: RecallHand) = (p.recallDays["${p.revision}|${hand.name}"].orEmpty() +
        current(p, hand).filter { it.outcome == RecallOutcome.INDEPENDENT }.map { it.day }).distinct().sorted()
    fun rememberedDays(p: PrivatePhrase, hand: RecallHand) = (p.coldRecallDays["${p.revision}|${hand.name}"].orEmpty() +
        current(p, hand).filter { it.outcome == RecallOutcome.INDEPENDENT && it.beforeViewing }.map { it.day }).distinct().sorted()
    /** A same-day repeat cannot increase the interval; latest evidence of needing help brings review closer. */
    fun due(p: PrivatePhrase, hand: RecallHand): LocalDate? {
        val attempts = current(p, hand)
        val last = attempts.lastOrNull() ?: return null
        val difficulty = p.lastDifficulty["${p.revision}|${hand.name}"] ?: attempts.lastOrNull { it.outcome != RecallOutcome.INDEPENDENT }?.day
        val run = if (last.outcome != RecallOutcome.INDEPENDENT || !last.beforeViewing) 0 else rememberedDays(p, hand).count { difficulty == null || it >= difficulty }
        val interval = when { run <= 1 -> 1L; run == 2 -> 3L; else -> 7L }
        return LocalDate.parse(last.day).plusDays(interval)
    }
    fun next(song: PrivateSong, hand: RecallHand, day: LocalDate): PrivatePhrase? =
        song.phrases.filter { due(it, hand)?.let { due -> !due.isAfter(day) } == true }
            .minByOrNull { due(it, hand)!! } ?: song.phrases.firstOrNull { current(it, hand).isEmpty() }
    fun editPhrase(p: PrivatePhrase, title: String, start: Int, end: Int, cue: String): PrivatePhrase = p.copy(
        title = title, startSeconds = start, endSeconds = end, cue = cue,
        revision = if (start != p.startSeconds || end != p.endSeconds || cue != p.cue) p.revision + 1 else p.revision,
    )

    /** Sets a phrase's chords (the recall revision stays: chords are a separate practice). */
    fun editChords(p: PrivatePhrase, chords: List<String>): PrivatePhrase = p.copy(chords = chords.map(String::trim).filter(String::isNotEmpty))
}
