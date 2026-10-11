package com.id.mimikopi.core.songs

import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.theory.Chord
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.Roman
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One section of a song as the user writes it: name, chords ("| C | G | Am | F |" or "C G Am F"), optional key. */
@Serializable
data class SongSection(
    val name: String,
    val chords: String = "",
    /** Key of this section when it differs from the song (modulation), as fifths. */
    val key: Int? = null,
    val bars: Int? = null,
)

/** One day of the 7-day song-learning pipeline (learning-system.md 9.5). */
@Serializable
data class PipelineDay(val day: Int, val done: Boolean = false, val date: String? = null, val note: String = "")

/**
 * A song in the user's library (learning-system.md 9.4-9.6). Only facts the user enters (or facts marked unverified
 * in the bundled shells) - the app never fetches songs. Melody and arrangement live in a linked user score.
 */
@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val year: String = "",
    /** Sources, doubts and remarks (Thai). */
    val notes: String = "",
    val key: Int? = null,
    val minor: Boolean = false,
    val keyVerified: Boolean = false,
    val bpm: Int? = null,
    val bpmVerified: Boolean = false,
    /** Melody range, e.g. "C4" and "D5". */
    val rangeLow: String? = null,
    val rangeHigh: String? = null,
    val rangeVerified: Boolean = false,
    /** Chord types reported for the song but not yet written into sections (e.g. from a chord site), unverified. */
    val reportedChords: List<String> = emptyList(),
    val sections: List<SongSection> = emptyList(),
    /** The user score holding the melody / arrangement, if any. */
    val scoreId: String? = null,
    val pipeline: List<PipelineDay> = (1..7).map { PipelineDay(it) },
    /** Lower = earlier in the user's own order. */
    val order: Int = 0,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    /** Every chord written in the sections (parsed; unreadable symbols skipped). */
    fun chords(): List<Chord> = sections.flatMap { parseChords(it.chords) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun parse(text: String): Song = json.decodeFromString(serializer(), text)
        fun parseList(text: String): List<Song> = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer()), text)

        /** "| C | G/B | Am7 | F |" -> chords; bar lines, "%" and "-" are ignored. */
        fun parseChords(text: String): List<Chord> =
            text.split(Regex("""[\s|]+""")).filter { it.isNotBlank() && it != "%" && it != "-" }.mapNotNull { Chord.parse(it) }

        /** The 7 days of 9.5 (Thai). */
        val PIPELINE = listOf(
            "ฟังทั้งเพลง ทำแผนผังฟอร์ม (intro/verse/chorus) และหาคีย์",
            "ถอดคอร์ดท่อนคอรัสเป็นเลขโรมัน (วนลูปและลดความเร็วได้)",
            "ถอดทำนองท่อนคอรัสทีละ 2 บาร์ ตรวจด้วยการเล่น",
            "เล่นคอร์ดประกอบรูปแบบง่ายตามโปรเกรสชัน",
            "รวมทำนอง + ประกอบ ช้า 50%",
            "เพิ่มความเร็วทีละขั้น และแยกฝึกท่อนที่ยาก",
            "เล่นท่อนคอรัสสะอาด 3 รอบ แล้วขยายไปท่อนอื่น",
        )
    }
}

/** A reason a song is not reachable yet, pointing at the lesson that teaches it. */
data class Requirement(val lessonId: String, val reason: String)

/** Difficulty (0-100) with its parts, and what is still missing (learning-system.md 9.4). */
data class SongAnalysis(
    val score: Int,
    val parts: Map<String, Int>,
    val requirements: List<Requirement>,
    val missing: List<Requirement>,
    /** Facts the difficulty is based on that are not verified yet. */
    val unverified: List<String>,
    /** Roman numerals of the written chords in the song key. */
    val romans: List<String>,
)

/** Song Ladder: difficulty from what the user entered, compared with the lessons passed. */
object SongLadder {
    /**
     * Parts (each 0-100): key signature, tempo, rhythm density and syncopation (from the linked score), melody range,
     * chord vocabulary (distinct chords, non-diatonic chords, extended chords), hand moves and left-hand complexity
     * (from the linked score). Missing data counts as 0 and is listed so the score is read with care.
     */
    fun analyze(song: Song, score: Score?, passed: Set<String>): SongAnalysis {
        val parts = linkedMapOf<String, Int>()
        val reqs = mutableListOf<Requirement>()
        val unverified = mutableListOf<String>()
        val key = song.key
        if (key != null) {
            val acc = kotlin.math.abs(key)
            parts["คีย์"] = (acc * 100 / 6).coerceAtMost(100)
            if (!song.keyVerified) unverified += "คีย์"
            if (acc <= 2) reqs += Requirement("4.3", "คีย์ ${Keys.name(key)} (${acc} เครื่องหมาย)")
            else reqs += Requirement("5.2", "คีย์ ${Keys.name(key)} มี $acc เครื่องหมาย")
        }
        song.sections.mapNotNull { it.key }.filter { it != key }.distinct().takeIf { it.isNotEmpty() }?.let {
            reqs += Requirement("6.3", "มีการเปลี่ยนคีย์ในเพลง")
            parts["เปลี่ยนคีย์"] = 60
        }
        song.bpm?.let { bpm ->
            parts["ความเร็ว"] = ((bpm - 60) * 100 / 100).coerceIn(0, 100)
            if (!song.bpmVerified) unverified += "ความเร็ว"
            if (bpm > 100) reqs += Requirement("5.8", "ความเร็ว $bpm bpm (อารมณ์จังหวะ 8/16 บีต)")
            if (bpm > 130) reqs += Requirement("6.4", "ความเร็ว $bpm bpm ต้องมีรูปแบบประกอบที่คล่อง")
        }
        val low = song.rangeLow?.let { runCatching { SpelledPitch.parse(it) }.getOrNull() }
        val high = song.rangeHigh?.let { runCatching { SpelledPitch.parse(it) }.getOrNull() }
        if (low != null && high != null) {
            val span = high.midi - low.midi
            parts["ช่วงเสียงทำนอง"] = (span * 100 / 24).coerceIn(0, 100)
            if (!song.rangeVerified) unverified += "ช่วงเสียง"
            if (low.midi < 60 || high.midi > 74) reqs += Requirement("4.1", "ทำนองใช้เส้นน้อย (${low}–${high})")
        }
        val chords = song.chords()
        val keyFifths = key ?: 0
        val romans = chords.map { Roman.analyze(it, keyFifths).numeral }
        val allChordTexts = chords.map { it.quality } + song.reportedChords.mapNotNull { Chord.parse("C$it")?.quality ?: Chord.parse(it)?.quality }
        if (song.reportedChords.isNotEmpty()) unverified += "ชนิดคอร์ดที่รายงาน"
        if (chords.isNotEmpty() || song.reportedChords.isNotEmpty()) {
            val distinct = chords.map { it.symbol }.distinct().size
            val nonDiatonic = chords.count { !Roman.analyze(it, keyFifths).diatonic }
            val extended = allChordTexts.count { it in EXTENDED }
            parts["คอร์ด"] = (distinct * 6 + nonDiatonic * 8 + extended * 6).coerceAtMost(100)
            reqs += Requirement("5.4", "อ่านคอร์ดและเลขโรมันในคีย์")
            if (allChordTexts.any { it in SEVENTHS }) reqs += Requirement("5.6", "มีคอร์ดเซเวนท์")
            if (chords.any { it.bass != null } || allChordTexts.any { it in SLASH_SUS_ADD }) reqs += Requirement("5.3", "มีสแลชคอร์ด / sus / add9")
            if (nonDiatonic > 0 || allChordTexts.any { it in EXTENDED }) reqs += Requirement("6.3", "มีคอร์ดนอกคีย์หรือคอร์ดขยาย")
        }
        if (score != null) {
            val notes = score.events.filter { !it.isRest }
            val beats = score.totalBeats.coerceAtLeast(1.0)
            val density = notes.size / beats
            val syncopated = notes.count { (it.onset * 4).toInt() % 4 != 0 && it.duration >= 0.5 }
            parts["ความหนาแน่นจังหวะ"] = (density * 40 + syncopated * 100.0 / notes.size.coerceAtLeast(1)).toInt().coerceIn(0, 100)
            if (notes.any { it.duration <= 0.25 }) reqs += Requirement("2.6", "มีเขบ็ตสองชั้น")
            if (syncopated > notes.size / 6) reqs += Requirement("6.4", "มีซิงโคเพชันมาก")
            val rh = notes.filter { it.hand == com.id.mimikopi.core.score.Hand.RIGHT }.flatMap { it.pitches }.map { it.midi }
            val moves = rh.windowed(2).count { (a, b) -> kotlin.math.abs(a - b) > 7 }
            parts["ย้ายมือ"] = (moves * 100 / rh.size.coerceAtLeast(1) * 4).coerceIn(0, 100)
            val lh = notes.filter { it.hand == com.id.mimikopi.core.score.Hand.LEFT }
            parts["มือซ้าย"] = ((lh.size / beats) * 30 + lh.count { it.pitches.size >= 3 } * 50.0 / lh.size.coerceAtLeast(1)).toInt().coerceIn(0, 100)
            if (lh.any { it.pitches.size >= 3 }) reqs += Requirement("4.6", "มือซ้ายเล่นคอร์ด")
        }
        val weights = mapOf("คีย์" to 1.0, "เปลี่ยนคีย์" to 0.5, "ความเร็ว" to 1.2, "ช่วงเสียงทำนอง" to 0.8, "คอร์ด" to 1.2,
            "ความหนาแน่นจังหวะ" to 1.0, "ย้ายมือ" to 0.6, "มือซ้าย" to 0.8)
        val used = parts.keys.sumOf { weights[it] ?: 1.0 }
        val total = if (used == 0.0) 0 else (parts.entries.sumOf { (k, v) -> v * (weights[k] ?: 1.0) } / used).toInt()
        val distinctReqs = reqs.distinctBy { it.lessonId to it.reason }
        return SongAnalysis(total, parts, distinctReqs, distinctReqs.filter { it.lessonId !in passed }, unverified.distinct(), romans)
    }

    private val SEVENTHS = setOf("7", "maj7", "m7", "m7b5", "dim7", "7sus4", "mM7", "aug7", "9", "maj9", "m9", "7b9", "11", "13")
    private val SLASH_SUS_ADD = setOf("sus2", "sus4", "7sus4", "add9", "madd9")
    private val EXTENDED = setOf("maj9", "m9", "9", "7b9", "11", "13", "aug", "aug7", "m7b5", "dim7", "mM7")

    /** Songs in ladder order: fewest missing lessons first, then lower difficulty, then the user's order. */
    fun ladder(songs: List<Pair<Song, SongAnalysis>>): List<Pair<Song, SongAnalysis>> =
        songs.sortedWith(compareBy({ it.second.missing.size }, { it.second.score }, { it.first.order }))
}
