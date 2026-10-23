package com.id.mimikopi.core.goal

import com.id.mimikopi.core.i18n.tr
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
            listOf(tr("ราว 98 bpm", "About 98 bpm", "約98 bpm"), tr("ทำนองช่วงแคบ ราว C4–C5", "Narrow melody, about C4–C5", "音域の狭いメロディ、約C4–C5"), tr("ยังไม่ทราบคีย์และคอร์ด ต้องแกะจากหู", "Key and chords unknown, work them out by ear", "キーとコードは不明、耳コピで")),
            mapOf(SkillId.NOTES to 4, SkillId.RHYTHM to 4, SkillId.SIGHT to 3, SkillId.TECH to 3, SkillId.EAR to 5, SkillId.CHORDS to 4, SkillId.SOLO to 3),
            tr("ทำนองแคบและช้าที่สุดในสามเพลง เหมาะเป็นเพลงแรกที่ลองแกะเอง", "The narrowest and slowest melody of the three, a good first song to work out yourself", "3曲の中で最も音域が狭く遅いメロディ。はじめての耳コピに最適"),
        ),
        TargetSong(
            "i-wish", "I WISH", "モーニング娘。 (2000)",
            listOf(tr("ราว 90–102 bpm (แหล่งข้อมูลไม่ตรงกัน)", "About 90–102 bpm (sources disagree)", "約90–102 bpm（情報源により異なる）"), tr("ทำนองแคบ ราว A♯3–F♯4", "Narrow melody, about A♯3–F♯4", "音域の狭いメロディ、約A♯3–F♯4"), tr("คีย์ A♭ แล้วไป E♭ คอร์ด add9 maj9 sus4 สแลช m7♭5 aug 13 (จากเว็บคอร์ดเว็บเดียว)", "Key A♭ then E♭; chords add9 maj9 sus4 slash m7♭5 aug 13 (from a single chord site)", "キーA♭からE♭へ。コードはadd9 maj9 sus4 分数 m7♭5 aug 13（コードサイト1つから）")),
            mapOf(SkillId.NOTES to 6, SkillId.RHYTHM to 4, SkillId.SIGHT to 5, SkillId.TECH to 6, SkillId.EAR to 7, SkillId.CHORDS to 6, SkillId.SOLO to 5),
            tr("ทำนองไม่ยาก แต่ฮาร์โมนีหลากหลาย มีเปลี่ยนคีย์ และอยู่ในคีย์แฟลตเยอะ", "The melody isn't hard, but the harmony is varied, with a key change and many flat keys", "メロディは難しくないが和音が多彩、転調ありでフラット系のキーが多い"),
        ),
        TargetSong(
            "love-revolution-21", "恋愛レボリューション21", "モーニング娘。 (2000)",
            listOf(tr("ราว 133 bpm", "About 133 bpm", "約133 bpm"), tr("ทำนองกว้าง ราว F♯3–D♯5", "Wide melody, about F♯3–D♯5", "音域の広いメロディ、約F♯3–D♯5"), tr("น่าจะคีย์ C (อนุมาน) มี maj9 m7 E7 D7 A7♭9 aug A♭M7", "Probably in C (inferred), with maj9 m7 E7 D7 A7♭9 aug A♭M7", "おそらくC（推定）、maj9 m7 E7 D7 A7♭9 aug A♭M7あり")),
            mapOf(SkillId.NOTES to 6, SkillId.RHYTHM to 6, SkillId.SIGHT to 6, SkillId.TECH to 7, SkillId.EAR to 8, SkillId.CHORDS to 7, SkillId.SOLO to 7),
            tr("เร็วที่สุดและทำนองกว้างที่สุด คอร์ดยืมและโดมิแนนท์ข้างเคียงเยอะ ควรเป็นเพลงที่สาม", "The fastest with the widest melody, many borrowed and secondary dominant chords; best as the third song", "最も速く音域も最も広い。借用和音や副属和音が多く、3曲目に向いています"),
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
        IdolDrill(tr("王道進行 ในคีย์ C", "王道進行 (Ōdō shinkō) in C", "Cの王道進行"), tr("IVmaj7–V7–iii7–vi โปรเกรสชันที่ป็อปญี่ปุ่นใช้มากที่สุด", "IVmaj7–V7–iii7–vi, the progression Japanese pop uses most", "IVmaj7–V7–iii7–vi、J-POPで最も使われるコード進行"), ChordPractice(ChordPractice.Mode.CHANGE, listOf(0), progression = "ทางหลวง (王道進行)", seconds = 60)),
        IdolDrill(tr("小室進行 ในคีย์ C", "小室進行 (Komuro shinkō) in C", "Cの小室進行"), "vi–IV–V–I", ChordPractice(ChordPractice.Mode.CHANGE, listOf(0), progression = "โกมุโร", seconds = 60)),
        IdolDrill(tr("王道進行 ในคีย์ A♭", "王道進行 (Ōdō shinkō) in A♭", "A♭の王道進行"), tr("คีย์ที่ I WISH เริ่ม (ข้อมูลยังไม่ยืนยัน)", "The key I WISH starts in (unconfirmed)", "I WISHの最初のキー（未確認）"), ChordPractice(ChordPractice.Mode.CHANGE, listOf(-4), progression = "ทางหลวง (王道進行)", seconds = 60)),
        IdolDrill(tr("王道進行 ในคีย์ E♭", "王道進行 (Ōdō shinkō) in E♭", "E♭の王道進行"), tr("คีย์ที่ I WISH ย้ายไป (ข้อมูลยังไม่ยืนยัน)", "The key I WISH moves to (unconfirmed)", "I WISHの転調先のキー（未確認）"), ChordPractice(ChordPractice.Mode.CHANGE, listOf(-3), progression = "ทางหลวง (王道進行)", seconds = 60)),
        IdolDrill(tr("Canon ในคีย์ D", "Canon in D", "Dのカノン進行"), tr("I–V–vi–iii–IV–I–IV–V ทางเดินคอร์ดที่เพลงไอดอลหลายเพลงยืมไปใช้", "I–V–vi–iii–IV–I–IV–V, borrowed by many idol songs", "I–V–vi–iii–IV–I–IV–V、多くのアイドル曲が借りている進行"), ChordPractice(ChordPractice.Mode.CHANGE, listOf(2), progression = "Canon", seconds = 60)),
    )
}
