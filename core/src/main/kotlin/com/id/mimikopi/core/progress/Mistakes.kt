package com.id.mimikopi.core.progress

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.ear.EarState
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What went wrong in one finished run (a piece, a reading drill, a test), kept so patterns across days can be told
 * apart from one bad run. Only counts and note names; nothing personal.
 */
@Serializable
data class RunMistakes(
    val at: Long,
    val day: String,
    /** "piece", "score" (score drills and tests) or "drill" (note and key finding). */
    val source: String,
    val notes: Int = 0,
    val wrong: Int = 0,
    val early: Int = 0,
    val late: Int = 0,
    /** Mean timing of the notes hit, negative = early (tempo runs only). */
    val meanOffsetMs: Int? = null,
    val leftNotes: Int = 0,
    val leftErrors: Int = 0,
    val rightNotes: Int = 0,
    val rightErrors: Int = 0,
    /** Notes that went wrong at least twice in the run ("F4"). */
    val trouble: List<String> = emptyList(),
    val stumbles: Int = 0,
    /** Reading confusions as "expected>played" ("E4>F4"). */
    val confusions: List<String> = emptyList(),
)

/** A pattern worth fixing, with what to practise. */
data class Mistake(val id: String, val title: String, val detail: String, val fix: Fix, val fixText: String)

/** Where a fix is practised. */
sealed interface Fix {
    data object Metronome : Fix
    data object Review : Fix
    data object Songs : Fix
    data class Ear(val kind: EarKind) : Fix
}

object Mistakes {
    const val KEY = "mistakes.log"
    const val KEEP = 300
    /** How far back the summary looks. */
    const val DAYS = 21L

    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(RunMistakes.serializer())
    fun parse(text: String?): List<RunMistakes> = text?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() } ?: emptyList()
    fun encode(runs: List<RunMistakes>): String = json.encodeToString(list, runs.sortedBy { it.at }.takeLast(KEEP))

    /** A finished score run as mistakes (the hands played only). */
    fun fromPlay(judge: com.id.mimikopi.core.playalong.PlayAlongJudge, result: com.id.mimikopi.core.playalong.PlayResult, source: String, at: Long, day: String): RunMistakes {
        val hands = judge.expected.groupingBy { it.hand }.eachCount()
        val left = hands[com.id.mimikopi.core.score.Hand.LEFT] ?: 0
        val right = hands[com.id.mimikopi.core.score.Hand.RIGHT] ?: 0
        return RunMistakes(
            at, day, source,
            notes = left + right,
            wrong = result.errorsByHand.values.sum(),
            early = result.count(com.id.mimikopi.core.playalong.NoteResult.EARLY),
            late = result.count(com.id.mimikopi.core.playalong.NoteResult.LATE),
            meanOffsetMs = if (result.timingJudged) result.meanOffsetMs?.roundToInt() else null,
            leftNotes = left, leftErrors = result.errorsByHand[com.id.mimikopi.core.score.Hand.LEFT] ?: 0,
            rightNotes = right, rightErrors = result.errorsByHand[com.id.mimikopi.core.score.Hand.RIGHT] ?: 0,
            trouble = result.troubleNotes.map { it.toString() },
            stumbles = result.stumbles,
        )
    }

    /** Sync: every run from both devices (runs carry their time, so the same run is counted once). */
    fun merge(a: List<RunMistakes>, b: List<RunMistakes>): List<RunMistakes> = (a + b).distinct().sortedBy { it.at }.takeLast(KEEP)

    /**
     * The patterns of the last [DAYS] days, most important first. Each needs enough runs and notes to be more than
     * one bad day: rushing or dragging (mean timing past 35 ms over three tempo runs or more), one hand clearly weaker
     * (twice the other's error rate, at least 30 notes each), notes that keep going wrong (three runs or more), notes
     * read as another (three times or more), stopping mid-piece, and ear items still under 60%.
     */
    fun summarize(runs: List<RunMistakes>, ear: EarState?, today: LocalDate): List<Mistake> {
        val since = today.minusDays(DAYS - 1).toString()
        val recent = runs.filter { it.day >= since && it.day <= today.toString() }
        val out = mutableListOf<Mistake>()

        val tempo = recent.filter { it.meanOffsetMs != null && it.notes > 0 }
        if (tempo.size >= 3) {
            val mean = tempo.sumOf { it.meanOffsetMs!!.toDouble() * it.notes } / tempo.sumOf { it.notes }
            if (abs(mean) >= 35) {
                val rush = mean < 0
                out += Mistake(
                    if (rush) "rush" else "drag",
                    if (rush) tr("มักกดเร็วกว่าจังหวะ", "Often ahead of the beat", "よく拍より早く押す") else tr("มักกดช้ากว่าจังหวะ", "Often behind the beat", "よく拍より遅く押す"),
                    tr("เฉลี่ย ${abs(mean).roundToInt()} ms จาก ${tempo.size} รอบที่เล่นตามจังหวะ", "Average ${abs(mean).roundToInt()} ms over ${tempo.size} in-time rounds", "リズムモード${tempo.size}回の平均 ${abs(mean).roundToInt()} ms"),
                    Fix.Metronome,
                    if (rush) tr("ฝึกกับเมโทรนอมช้าลง 10 bpm ฟังคลิกก่อนแล้วค่อยกดให้ตรงกับคลิก", "Practise with the metronome 10 bpm slower; hear the click first, then press with it", "メトロノームを10 bpm遅くして練習。先にクリックを聞いてから合わせて押す") else tr("ฝึกกับเมโทรนอม นับในใจล่วงหน้าหนึ่งจังหวะ แล้วกดพร้อมคลิก", "Practise with the metronome, counting one beat ahead in your head, then press with the click", "メトロノームで練習。頭の中で1拍先を数え、クリックと一緒に押す"),
                )
            }
        }

        val ln = recent.sumOf { it.leftNotes }
        val rn = recent.sumOf { it.rightNotes }
        if (ln >= 30 && rn >= 30) {
            val lr = recent.sumOf { it.leftErrors }.toDouble() / ln
            val rr = recent.sumOf { it.rightErrors }.toDouble() / rn
            val (weak, wr, sr) = if (lr >= rr) Triple(tr("ซ้าย", "left", "左"), lr, rr) else Triple(tr("ขวา", "right", "右"), rr, lr)
            if (wr >= 0.08 && wr >= 2 * sr) {
                out += Mistake(
                    "hand", tr("มือ${weak}ผิดบ่อยกว่าอีกมือ", "The ${weak} hand makes more mistakes than the other", "${weak}手のミスがもう一方より多い"),
                    tr("มือ${weak}ผิด ${(wr * 100).roundToInt()}% ของโน้ต อีกมือ ${(sr * 100).roundToInt()}%", "${weak} hand wrong on ${(wr * 100).roundToInt()}% of notes, the other ${(sr * 100).roundToInt()}%", "${weak}手のミス ${(wr * 100).roundToInt()}%、もう一方 ${(sr * 100).roundToInt()}%"),
                    Fix.Songs, tr("ฝึกมือ${weak}แยกช้าๆ จนไม่ผิดสามรอบติด แล้วค่อยรวมสองมือ", "Practise the ${weak} hand alone slowly until three clean rounds in a row, then hands together", "${weak}手だけをゆっくり3回連続ミスなしまで練習してから両手に"),
                )
            }
        }

        val trouble = recent.flatMap { r -> r.trouble.distinct() }.groupingBy { it }.eachCount().filterValues { it >= 3 }
            .entries.sortedByDescending { it.value }.take(3)
        if (trouble.isNotEmpty()) {
            out += Mistake(
                "notes", tr("โน้ตที่ผิดซ้ำๆ: ", "Notes missed again and again: ", "何度も間違える音：") + trouble.joinToString(" ") { it.key },
                trouble.joinToString(" · ") { tr("${it.key} ผิดใน ${it.value} รอบ", "${it.key} wrong in ${it.value} rounds", "${it.key} ${it.value}回の練習でミス") },
                Fix.Review, tr("ทวนโน้ตเหล่านี้ในการทวนประจำวัน แอพใส่ไว้ให้แล้ว", "Review these notes in the daily review; the app has added them", "毎日の復習でこれらの音を復習しましょう。アプリが追加済みです"),
            )
        }

        val confusions = recent.flatMap { it.confusions }.groupingBy { it }.eachCount().filterValues { it >= 3 }
            .entries.sortedByDescending { it.value }.take(2)
        if (confusions.isNotEmpty()) {
            out += Mistake(
                "confuse", tr("อ่านโน้ตสลับกัน: ", "Notes read as each other: ", "取り違える音：") + confusions.joinToString(" · ") { c -> c.key.replace(">", tr(" เป็น ", " as ", " を ")) },
                confusions.joinToString(" · ") { tr("${it.value} ครั้ง", "${it.value} times", "${it.value}回") },
                Fix.Review, tr("ดูตำแหน่งบนบรรทัดของสองตัวนี้ให้ชัด (บนเส้นหรือในช่อง) แล้วทวนอ่านโน้ต", "Look closely at where these two sit on the staff (on a line or in a space), then review reading", "この2つが五線のどこにあるか（線上か間か）をよく見てから読譜を復習"),
            )
        }

        val long = recent.filter { it.notes >= 12 && it.source != "drill" }
        if (long.size >= 3) {
            val avg = long.sumOf { it.stumbles }.toDouble() / long.size
            if (avg >= 1.0) {
                out += Mistake(
                    "stumble", tr("หยุดกลางเพลงบ่อย", "Often stops in the middle", "よく途中で止まる"),
                    "เฉลี่ย ${"%.1f".format(avg)} ครั้งต่อรอบ จาก ${long.size} รอบ",
                    Fix.Songs, tr("เล่นช้าลงจนเล่นต่อเนื่องได้ ผิดแล้วเล่นต่อ อย่าหยุดแก้ แล้วค่อยเร่งทีละนิด", "Play slower until you can keep going; after a mistake carry on, don't stop to fix, then speed up bit by bit", "止まらずに弾ける速さまで下げる。間違えても直さずに続け、少しずつテンポを上げる"),
                )
            }
        }

        if (ear != null) {
            EarProgress.weakest(ear, 2).filter { it.stat.asked >= 5 && it.stat.strength < 0.6 }.forEach { w ->
                out += Mistake(
                    "ear:${w.item}", tr("หูยังพลาด: ${w.label}", "Ear still misses: ${w.label}", "耳がまだ間違える：${w.label}"),
                    tr("${w.kind.title} · ถูก ${w.stat.right} จาก ${w.stat.asked} ครั้ง", "${w.kind.title} · right ${w.stat.right} of ${w.stat.asked}", "${w.kind.title} · ${w.stat.asked}回中${w.stat.right}回正解"),
                    Fix.Ear(w.kind), tr("รอบฝึกหูจะถามข้อนี้บ่อยขึ้นเอง ฝึก${w.kind.title}เพิ่มอีกรอบได้", "Ear rounds will ask this more often; you can practise ${w.kind.title} for another round", "耳トレの回でこれをより多く出題します。${w.kind.title}をもう1回練習できます"),
                )
            }
        }
        return out
    }
}
