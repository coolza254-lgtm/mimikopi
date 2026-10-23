package com.id.mimikopi.core.exercise

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.theory.ChordQualities
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.StageGenerators
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Free chord practice (the chord tab), outside the lesson ladder: the user picks what to practise and the existing
 * chord drills run it. Three kinds: chords asked by name ("Am7"), by roman numeral in a key ("vi ในคีย์ G"), or
 * changes through a progression against the clock. Nothing is unlocked or scored beyond the run itself.
 */
@Serializable
data class ChordPractice(
    val mode: Mode = Mode.NAME,
    /** Keys as fifths (-6..6): roots come from these scales (by name), or the numerals are read in them. */
    val keys: List<Int> = listOf(0),
    /** By name: ids of [GROUPS]. */
    val groups: List<String> = listOf("triads"),
    /** By numeral: ids of [NUMERAL_SETS]. */
    val numeralSets: List<String> = listOf("diatonic"),
    /** Changes: a progression name of [StageGenerators.PROGRESSIONS]. */
    val progression: String = "Axis",
    val items: Int = 10,
    val seconds: Int = 60,
) {
    enum class Mode { NAME, NUMERAL, CHANGE }

    /** A named set of chord qualities or numerals to choose from. */
    data class Group(val id: String, val title: String, val members: List<String>)

    val qualities: List<String> get() = GROUPS.filter { it.id in groups }.flatMap { it.members }.ifEmpty { listOf("", "m") }
    val numerals: List<String> get() = NUMERAL_SETS.filter { it.id in numeralSets }.flatMap { it.members }.ifEmpty { NUMERAL_SETS.first().members }
    private val safeKeys: List<Int> get() = keys.filter { it in -6..6 }.distinct().ifEmpty { listOf(0) }

    /** The drill the existing runner plays. */
    fun drill(): Drill = when (mode) {
        Mode.NAME -> Drill(ExerciseType.E_CHORD_BUILD, tr("ฝึกคอร์ด: ${summary()}", "Chord practice: ${summary()}", "コード練習：${summary()}"), items = items, keys = safeKeys, qualities = qualities)
        Mode.NUMERAL -> Drill(ExerciseType.E_CHORD_BUILD, tr("ฝึกคอร์ด: ${summary()}", "Chord practice: ${summary()}", "コード練習：${summary()}"), items = items, keys = safeKeys, degrees = numerals)
        Mode.CHANGE -> Drill(ExerciseType.E_CHORD_CHANGE, tr("ฝึกคอร์ด: ${summary()}", "Chord practice: ${summary()}", "コード練習：${summary()}"), keys = safeKeys.take(1), progression = progression, seconds = seconds)
    }

    /** One line saying what is practised ("เมเจอร์/ไมเนอร์ · คีย์ C, G"). */
    fun summary(): String {
        val keyText = tr("คีย์ ", "Key ", "キー ") + safeKeys.joinToString(", ") { Keys.name(it) }
        return when (mode) {
            Mode.NAME -> GROUPS.filter { it.id in groups }.joinToString(" + ") { it.title }.ifEmpty { GROUPS.first().title } + " · " + keyText
            Mode.NUMERAL -> NUMERAL_SETS.filter { it.id in numeralSets }.joinToString(" + ") { it.title }.ifEmpty { NUMERAL_SETS.first().title } + " · " + keyText
            Mode.CHANGE -> {
                val degrees = ChordDrills.progressionDegrees(progression).joinToString(" ")
                tr("$progression ($degrees) · คีย์ ${Keys.name(safeKeys.first())} · $seconds วินาที", "$progression ($degrees) · key ${Keys.name(safeKeys.first())} · $seconds s", "$progression（$degrees） · キー ${Keys.name(safeKeys.first())} · ${seconds}秒")
            }
        }
    }

    companion object {
        val GROUPS = listOf(
            Group("triads", tr("เมเจอร์/ไมเนอร์", "Major/minor", "メジャー/マイナー"), listOf("", "m")),
            Group("dimaug", tr("ดิม/ออก", "Dim/aug", "ディミニッシュ/オーギュメント"), listOf("dim", "aug")),
            Group("sus", tr("ซัส 2/ซัส 4", "Sus2/sus4", "sus2/sus4"), listOf("sus2", "sus4")),
            Group("sevenths", tr("คอร์ด 7 (7, maj7, m7)", "7th chords (7, maj7, m7)", "セブンスコード（7、maj7、m7）"), listOf("7", "maj7", "m7")),
            Group("moresevenths", "m7♭5, dim7", listOf("m7b5", "dim7")),
            Group("sixths", "6, m6", listOf("6", "m6")),
            Group("add9", "add9, madd9", listOf("add9", "madd9")),
        )

        val NUMERAL_SETS = listOf(
            Group("diatonic", tr("คอร์ดในคีย์ (I–vii°)", "Chords in the key (I–vii°)", "キーの中のコード（I–vii°）"), listOf("I", "ii", "iii", "IV", "V", "vi", "vii")),
            Group("primary", tr("คอร์ดหลัก (I IV V)", "Primary chords (I IV V)", "主要三和音（I IV V）"), listOf("I", "IV", "V")),
            Group("sevenths", tr("คอร์ด 7 ในคีย์", "7th chords in the key", "キーの中のセブンスコード"), listOf("Imaj7", "ii7", "iii7", "IVmaj7", "V7", "vi7", "vii7")),
            Group("secondary", tr("โดมิแนนท์รอง (V7/…)", "Secondary dominants (V7/…)", "副属和音（V7/…）"), listOf("V7/ii", "V7/iii", "V7/IV", "V7/V", "V7/vi")),
            Group("borrowed", tr("คอร์ดยืม (♭III ♭VI ♭VII iv)", "Borrowed chords (♭III ♭VI ♭VII iv)", "借用和音（♭III ♭VI ♭VII iv）"), listOf("bIII", "bVI", "bVII", "iv")),
        )

        /** Keys offered, in the order they are usually learnt, then the rest. */
        val KEYS = StageGenerators.KEY_ORDER + (-6..6).filter { it !in StageGenerators.KEY_ORDER }

        val ITEMS = listOf(10, 20, 30)
        val SECONDS = listOf(30, 60, 90, 120)

        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String?): ChordPractice = text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: ChordPractice()
        fun encode(p: ChordPractice): String = json.encodeToString(serializer(), p)

        init {
            check(GROUPS.all { g -> g.members.all { it in ChordQualities.INTERVALS } })
        }
    }
}

/** One finished chord practice run, for the short list under the settings (newest first). */
@Serializable
data class ChordRun(val day: String, val summary: String, val result: String) {
    companion object {
        const val KEEP = 20
        private val json = Json { ignoreUnknownKeys = true }
        private val list = ListSerializer(serializer())
        fun parse(text: String?): List<ChordRun> = text?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() } ?: emptyList()
        fun add(runs: List<ChordRun>, run: ChordRun): List<ChordRun> = (listOf(run) + runs).take(KEEP)
        fun encode(runs: List<ChordRun>): String = json.encodeToString(list, runs)
    }
}
