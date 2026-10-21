package com.id.mimikopi.core.practice

import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.music.SpelledPitch

/** Note-reading practice pools (shared by the phone and the computer). Small, explicit pools of exact pitches. */
enum class ReadingClef(val title: String) { TREBLE("กุญแจซอล"), BASS("กุญแจฟา"), BOTH("สลับซอล / ฟา") }
enum class ReadingRange(val title: String) { FIVE("ห้าโน้ต"), OCTAVE("หนึ่งอ็อกเทฟ"), STAFF("ช่วงกว้าง") }

data class ReadingPractice(
    val clef: ReadingClef = ReadingClef.TREBLE,
    val range: ReadingRange = ReadingRange.FIVE,
    val accidentals: Boolean = false,
    val items: Int = 20,
) {
    fun drill(): Drill {
        val pool = buildList {
            for (c in listOf(ReadingClef.TREBLE, ReadingClef.BASS).filter { clef == ReadingClef.BOTH || clef == it }) {
                val span = when (c) {
                    ReadingClef.TREBLE -> when (range) { ReadingRange.FIVE -> 60..67; ReadingRange.OCTAVE -> 60..72; ReadingRange.STAFF -> 60..81 }
                    else -> when (range) { ReadingRange.FIVE -> 53..60; ReadingRange.OCTAVE -> 48..60; ReadingRange.STAFF -> 40..60 }
                }
                val staff = if (c == ReadingClef.TREBLE) "treble" else "bass"
                for (m in span) {
                    val p = SpelledPitch.fromMidiSharp(m)
                    if (p.accidental == 0 || accidentals) add("$p:$staff")
                    if (accidentals && p.accidental == 1) {
                        val next = SpelledPitch.fromMidiSharp(m + 1)
                        add("${next.copy(accidental = -1)}:$staff")
                    }
                }
            }
        }
        return Drill(ExerciseType.E_NOTEID, "อ่านโน้ตทีละตัว", items = items.coerceIn(10, 30), pool = pool)
    }
}
