package com.id.mimikopi.core.playalong

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.score.Hand
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Evidence belongs to an exact arrangement, section, hand and mode, never to time spent opening a screen. */
@Serializable
data class StudyEvidence(
    val attempts: Int = 0,
    val streak: Int = 0,
    val days: List<String> = emptyList(),
    val lastDay: String? = null,
    val lastAccuracy: Double? = null,
    val lastTiming: Double? = null,
    val lastBpm: Int? = null,
    val bestCleanBpm: Int? = null,
    val troubleMeasures: List<Int> = emptyList(),
)

object HeroicPlan {
    const val EASY = "heroic-polonaise-easy"
    const val THEME = "heroic-polonaise-theme"
    data class Target(val id: String, val piece: String, val from: Int, val to: Int,
        val hands: Set<Hand>, val mode: PlayMode, val bpm: Int) {
        val handLabel get() = when (hands.size) { 2 -> tr("สองมือ", "Both hands", "両手"); else -> if (Hand.RIGHT in hands) tr("มือขวา", "Right hand", "右手") else tr("มือซ้าย", "Left hand", "左手") }
        val label get() = tr("ห้อง ${from + 1}–${to + 1} · $handLabel", "Bars ${from + 1}–${to + 1} · $handLabel", "${from + 1}–${to + 1}小節 · $handLabel")
        val goal get() = if (mode == PlayMode.WAIT) tr("โหมดรอ · อ่านให้ถูกก่อน ไม่วัดจังหวะ", "Wait mode · read correctly first, timing not measured", "待ちモード · まず正しく読む、リズムは測りません") else tr("ตามจังหวะ $bpm bpm · ปิดชื่อโน้ตเมื่อวัดผล", "In time $bpm bpm · note names off when measuring", "リズム $bpm bpm · 測定時は音名を消す")
        fun completed(e: StudyEvidence) = e.days.distinct().size >= if (mode == PlayMode.WAIT) 1 else 2
    }
    val targets: List<Target> = buildList {
        for ((piece, prefix, slow, target) in listOf(Variant(EASY, "c", 48, 66), Variant(THEME, "ab", 48, 72))) {
            for (from in listOf(0, 2, 4, 6)) {
                add(Target("$prefix-$from-right", piece, from, from + 1, setOf(Hand.RIGHT), PlayMode.WAIT, slow))
                add(Target("$prefix-$from-left", piece, from, from + 1, setOf(Hand.LEFT), PlayMode.WAIT, slow))
                add(Target("$prefix-$from-together", piece, from, from + 1, Hand.entries.toSet(), PlayMode.TEMPO, slow))
            }
            // Practise crossing phrase boundaries before joining all eight bars.
            add(Target("$prefix-join", piece, 1, 6, Hand.entries.toSet(), PlayMode.TEMPO, slow))
            add(Target("$prefix-whole-slow", piece, 0, 7, Hand.entries.toSet(), PlayMode.TEMPO, slow))
            add(Target("$prefix-whole", piece, 0, 7, Hand.entries.toSet(), PlayMode.TEMPO, target))
        }
    }
    private data class Variant(val piece: String, val prefix: String, val slow: Int, val target: Int)
    fun target(id: String?) = targets.firstOrNull { it.id == id }
    fun evidence(t: Target, progress: Map<String, PieceProgress>) = progress[t.piece]?.studyTargets?.get(t.id) ?: StudyEvidence()
    fun next(progress: Map<String, PieceProgress>, day: LocalDate = LocalDate.now()): Target? {
        val pending = targets.filter { !it.completed(evidence(it, progress)) }
        // Once today's evidence is earned, move on; return to it tomorrow for the retention check.
        return pending.firstOrNull { day.toString() !in evidence(it, progress).days } ?: pending.firstOrNull()
    }

    /** A changed section/hand/mode never earns credit for the launch target. Interrupted runs cannot pass. */
    fun record(progress: PieceProgress, result: PlayResult, helper: Boolean, interrupted: Boolean,
        day: LocalDate = LocalDate.now()): PieceProgress {
        if (!result.hasNoteInput) return progress
        val setup = result.setup
        val matched = targets.filter { it.piece == progress.pieceId && it.from == setup.fromMeasure && it.to == setup.toMeasure &&
            it.hands == setup.hands && it.mode == setup.mode && !setup.anyKey }
        val summary = result.summary
        val changes = matched.associate { t ->
            val old = progress.studyTargets[t.id] ?: StudyEvidence()
            val complete = !interrupted && result.outcomes.isNotEmpty() && result.outcomes.values.none {
                it.result == NoteResult.PENDING || (t.mode == PlayMode.WAIT && it.result == NoteResult.MISSED)
            }
            val clean = complete && !helper && summary != null && summary.noteAccuracy >= .95 && result.wrongPresses.isEmpty() &&
                (t.mode == PlayMode.WAIT || (!helper && setup.bpm >= t.bpm && setup.toleranceMs <= 120 &&
                    summary.fractionWithinTiming >= .90 && result.stumbles <= 1))
            val streak = if (clean) (if (old.lastDay == day.toString()) old.streak else 0) + 1 else 0
            val earnsDay = clean && (t.mode == PlayMode.WAIT || streak >= 3)
            t.id to old.copy(attempts = old.attempts + 1, streak = if (earnsDay) 0 else streak,
                days = if (earnsDay) (old.days + day.toString()).distinct().sorted() else old.days,
                lastDay = day.toString(), lastAccuracy = summary?.noteAccuracy,
                lastTiming = if (result.timingJudged) summary?.fractionWithinTiming else null,
                lastBpm = if (result.timingJudged) setup.bpm.toInt() else null,
                bestCleanBpm = if (clean && result.timingJudged) maxOf(old.bestCleanBpm ?: 0, setup.bpm.toInt()) else old.bestCleanBpm,
                troubleMeasures = result.problemMeasures)
        }
        return progress.copy(studyTargets = progress.studyTargets + changes)
    }
}
