package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.playalong.HeroicPlan
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.score.Hand
import kotlinx.serialization.Serializable

enum class Phase { IDLE, COUNT_IN, PLAYING, LISTENING, RESULT }

enum class HandChoice(val hands: Set<Hand>, val title: String) {
    RIGHT(setOf(Hand.RIGHT), tr("มือขวา", "Right hand", "右手")), LEFT(setOf(Hand.LEFT), tr("มือซ้าย", "Left hand", "左手")), BOTH(setOf(Hand.RIGHT, Hand.LEFT), tr("สองมือ", "Both hands", "両手"))
}

/** Reader preferences remembered between pieces: the Android ReaderOptions row, field for field. */
@Serializable
data class ReaderOptions(
    val mode: PlayMode = PlayMode.WAIT,
    val hands: HandChoice = HandChoice.RIGHT,
    val playOtherHand: Boolean = true,
    val metronome: Boolean = true,
    val useRamp: Boolean = true,
    /** On-screen keys while playing. The desktop hides them whenever a MIDI piano is connected. */
    val showKeyboard: Boolean = true,
    val hideNotes: Boolean = false,
    val loopListen: Boolean = false,
    val pageView: Boolean = true,
    /** Legacy backup field only; playback always uses the large score. */
    val paperSize: Boolean = false,
    val falling: Boolean = false,
    val fallingKeyLabels: Boolean = true,
)

/** Free reading may change pace; fixed lessons, benchmarks and exams keep their assessment settings. */
internal fun ReaderOptions.constrainReadingChange(requested: ReaderOptions, fixed: Boolean, freeReading: Boolean): ReaderOptions {
    if (!fixed) return requested
    return copy(
        mode = if (freeReading) requested.mode else mode,
        showKeyboard = requested.showKeyboard,
        playOtherHand = requested.playOtherHand,
        pageView = requested.pageView,
        paperSize = requested.paperSize,
        fallingKeyLabels = requested.fallingKeyLabels,
    )
}

internal fun ReaderOptions.newReadingOptions(level: Int) = ReaderOptions(
    mode = PlayMode.WAIT,
    hands = if (level == 2) HandChoice.LEFT else if (level == 5) HandChoice.BOTH else HandChoice.RIGHT,
    playOtherHand = false, metronome = true, useRamp = false, showKeyboard = showKeyboard,
    pageView = pageView, paperSize = false,
)

sealed interface ResultAction {
    data class Loop(val from: Int, val to: Int, val bpm: Int) : ResultAction
    data class Hands(val choice: HandChoice, val bpm: Int) : ResultAction
    data class Tempo(val bpm: Int) : ResultAction
}

data class CoachLine(val text: String, val action: ResultAction? = null)

data class ResultUi(
    val headline: String,
    val details: String,
    val clean: Boolean?,
    val coach: List<CoachLine>,
    val progressLine: String?,
) {
    /** A device timing check comes before advice to practise around a possible device delay. */
    val nextStep: CoachLine? get() = coach.firstOrNull { it.action != null } ?: coach.firstOrNull()
}

/** A key pressed that the score did not ask for, drawn on the score for a moment. */
data class WrongMark(val midi: Int, val beat: Double, val atNanos: Long)

/** Visual events use the judge's verdict, never merely the set of held keys. */
enum class FallingVerdict(val caption: String) {
    HIT(tr("ถูก", "Right", "正解")), CORRECTED(tr("แก้ถูก", "Corrected", "修正")), EARLY(tr("เร็ว", "Early", "早い")), LATE(tr("ช้า", "Late", "遅い")), WRONG(tr("ผิดคีย์", "Wrong key", "違う鍵盤")), MISS(tr("พลาด", "Missed", "ミス"))
}

data class FallingFeedback(val midi: Int, val verdict: FallingVerdict, val left: Boolean, val atNanos: Long) {
    fun age(now: Long): Float = ((now - atNanos) / 850_000_000f).coerceAtLeast(0f)

    companion object {
        fun judged(midi: Int, result: NoteResult, left: Boolean, atNanos: Long): FallingFeedback? {
            val verdict = when (result) {
                NoteResult.CORRECT -> FallingVerdict.HIT
                NoteResult.CORRECTED -> FallingVerdict.CORRECTED
                NoteResult.EARLY -> FallingVerdict.EARLY
                NoteResult.LATE -> FallingVerdict.LATE
                NoteResult.MISSED -> FallingVerdict.MISS
                NoteResult.PENDING -> return null
            }
            return FallingFeedback(midi, verdict, left, atNanos)
        }
    }
}

internal fun List<FallingFeedback>.withFeedback(events: List<FallingFeedback>, now: Long): List<FallingFeedback> =
    (filter { it.age(now) < 1f } + events).takeLast(48)

data class ReaderUi(
    val phase: Phase = Phase.IDLE,
    val options: ReaderOptions = ReaderOptions(),
    val bpm: Int = 60,
    val targetBpm: Int = 60,
    val section: IntRange = 0..0,
    val pendingSectionStart: Int? = null,
    val countIn: Int? = null,
    val results: Map<NoteKey, NoteResult> = emptyMap(),
    val wrongMarks: List<WrongMark> = emptyList(),
    val fallingFeedback: List<FallingFeedback> = emptyList(),
    val hotMeasures: List<Int> = emptyList(),
    val result: ResultUi? = null,
    val showNames: Boolean = false,
    val passDays: Int = 0,
    val message: String? = null,
    val previewLeft: Int? = null,
)

/** A short corrective run. Its temporary tempo must not be overwritten by the piece's ramp. */
internal fun ResultAction.prepare(ui: ReaderUi, measures: Int): ReaderUi {
    require(measures > 0)
    val reset = ui.copy(
        phase = Phase.IDLE, result = null, results = emptyMap(), wrongMarks = emptyList(), fallingFeedback = emptyList(),
        pendingSectionStart = null, message = null, options = ui.options.copy(useRamp = false),
    )
    return when (this) {
        is ResultAction.Loop -> {
            val first = from.coerceIn(0, measures - 1)
            reset.copy(section = first..to.coerceIn(first, measures - 1), bpm = bpm.coerceAtLeast(30))
        }
        is ResultAction.Hands -> reset.copy(options = reset.options.copy(hands = choice), bpm = bpm.coerceAtLeast(30))
        is ResultAction.Tempo -> reset.copy(options = reset.options.copy(mode = PlayMode.TEMPO), bpm = bpm.coerceAtLeast(30))
    }
}

/** Apply a Heroic target after loading preferences, so saved options cannot silently alter its criteria. */
internal fun ReaderUi.withStudyTarget(t: HeroicPlan.Target): ReaderUi = copy(
    section = t.from..t.to, bpm = t.bpm, targetBpm = t.bpm, showNames = false,
    options = options.copy(mode = t.mode, hands = HandChoice.entries.first { it.hands == t.hands },
        useRamp = false, falling = false, hideNotes = false, playOtherHand = false, loopListen = false, metronome = true),
    message = tr("แผน Heroic · ${t.label} · ${t.goal}", "Heroic plan · ${t.label} · ${t.goal}", "Heroicプラン · ${t.label} · ${t.goal}"),
)
