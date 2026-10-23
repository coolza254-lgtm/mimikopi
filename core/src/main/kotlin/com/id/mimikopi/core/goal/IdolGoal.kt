package com.id.mimikopi.core.goal

import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.progress.SkillStatus

/**
 * The user's goal: playing Hello! Project and other idol songs (learning-system.md 9). The app has no notes or lyrics
 * of these songs (copyright); the goal page shows what each first target song asks for, measured against the skill
 * levels from tests, and practice material in the same style that the app makes itself. Every song fact is from web
 * sources noted in learning-system.md 9.6 and is NOT verified: the page says so.
 */
data class TargetSong(
    val id: String,
    val title: String,
    val artist: String,
    val facts: List<String>,
    /** Skill levels the song asks for (my estimate from tempo, range, keys and chords; not an official grade). */
    val needs: Map<SkillId, Int>,
    val why: String,
)

/** A skill short of what a song asks for. */
data class Gap(val skill: SkillId, val have: Int, val need: Int)

/** A practice in the style of the songs, made by the app. */
data class IdolDrill(val title: String, val about: String, val practice: ChordPractice)

object IdolGoal {
    /** The user's first targets (confirmed 2026-10-11), in the suggested order (learning-system.md 9.6). */
    val SONGS = listOf(
        TargetSong(
            "kokoro-placard", "心のプラカード", "AKB48 (2014)",
            listOf("ราว 98 bpm", "ทำนองช่วงแคบ ราว C4–C5", "ยังไม่ทราบคีย์และคอร์ด ต้องแกะจากหู"),
            mapOf(SkillId.NOTES to 4, SkillId.RHYTHM to 4, SkillId.SIGHT to 3, SkillId.TECH to 3, SkillId.EAR to 5, SkillId.CHORDS to 4, SkillId.SOLO to 3),
            "ทำนองแคบและช้าที่สุดในสามเพลง เหมาะเป็นเพลงแรกที่ลองแกะเอง",
        ),
        TargetSong(
            "i-wish", "I WISH", "モーニング娘。 (2000)",
            listOf("ราว 90–102 bpm (แหล่งข้อมูลไม่ตรงกัน)", "ทำนองแคบ ราว A♯3–F♯4", "คีย์ A♭ แล้วไป E♭ คอร์ด add9 maj9 sus4 สแลช m7♭5 aug 13 (จากเว็บคอร์ดเว็บเดียว)"),
            mapOf(SkillId.NOTES to 6, SkillId.RHYTHM to 4, SkillId.SIGHT to 5, SkillId.TECH to 6, SkillId.EAR to 7, SkillId.CHORDS to 6, SkillId.SOLO to 5),
            "ทำนองไม่ยาก แต่ฮาร์โมนีหลากหลาย มีเปลี่ยนคีย์ และอยู่ในคีย์แฟลตเยอะ",
        ),
        TargetSong(
            "love-revolution-21", "恋愛レボリューション21", "モーニング娘。 (2000)",
            listOf("ราว 133 bpm", "ทำนองกว้าง ราว F♯3–D♯5", "น่าจะคีย์ C (อนุมาน) มี maj9 m7 E7 D7 A7♭9 aug A♭M7"),
            mapOf(SkillId.NOTES to 6, SkillId.RHYTHM to 6, SkillId.SIGHT to 6, SkillId.TECH to 7, SkillId.EAR to 8, SkillId.CHORDS to 7, SkillId.SOLO to 7),
            "เร็วที่สุดและทำนองกว้างที่สุด คอร์ดยืมและโดมิแนนท์ข้างเคียงเยอะ ควรเป็นเพลงที่สาม",
        ),
    )

    /** What [song] still needs: skills below its level, biggest gap first. */
    fun gaps(song: TargetSong, statuses: List<SkillStatus>): List<Gap> =
        song.needs.mapNotNull { (skill, need) ->
            val have = statuses.firstOrNull { it.skill == skill }?.level ?: 0
            if (have < need) Gap(skill, have, need) else null
        }.sortedByDescending { it.need - it.have }

    /** Share of the song's needed levels already reached (each skill capped at what the song needs). */
    fun readiness(song: TargetSong, statuses: List<SkillStatus>): Double {
        val total = song.needs.values.sum()
        val have = song.needs.entries.sumOf { (skill, need) -> minOf(need, statuses.firstOrNull { it.skill == skill }?.level ?: 0) }
        return if (total == 0) 0.0 else have.toDouble() / total
    }

    /**
     * Chord-change practice in the style of the songs: the progressions J-pop uses most (王道進行, 小室進行, Canon)
     * in the flat keys the first target songs use (A♭, E♭) and in C, timed one minute each.
     */
    val DRILLS = listOf(
        IdolDrill("王道進行 ในคีย์ C", "IVmaj7–V7–iii7–vi โปรเกรสชันที่ป็อปญี่ปุ่นใช้มากที่สุด", ChordPractice(ChordPractice.Mode.CHANGE, listOf(0), progression = "ทางหลวง (王道進行)", seconds = 60)),
        IdolDrill("小室進行 ในคีย์ C", "vi–IV–V–I", ChordPractice(ChordPractice.Mode.CHANGE, listOf(0), progression = "โกมุโร", seconds = 60)),
        IdolDrill("王道進行 ในคีย์ A♭", "คีย์ที่ I WISH เริ่ม (ข้อมูลยังไม่ยืนยัน)", ChordPractice(ChordPractice.Mode.CHANGE, listOf(-4), progression = "ทางหลวง (王道進行)", seconds = 60)),
        IdolDrill("王道進行 ในคีย์ E♭", "คีย์ที่ I WISH ย้ายไป (ข้อมูลยังไม่ยืนยัน)", ChordPractice(ChordPractice.Mode.CHANGE, listOf(-3), progression = "ทางหลวง (王道進行)", seconds = 60)),
        IdolDrill("Canon ในคีย์ D", "I–V–vi–iii–IV–I–IV–V ทางเดินคอร์ดที่เพลงไอดอลหลายเพลงยืมไปใช้", ChordPractice(ChordPractice.Mode.CHANGE, listOf(2), progression = "Canon", seconds = 60)),
    )
}
