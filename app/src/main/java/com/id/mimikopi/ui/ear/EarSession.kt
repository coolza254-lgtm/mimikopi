package com.id.mimikopi.ui.ear

import com.id.mimikopi.core.i18n.tr
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.ear.EarAnswer
import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.EarResult
import com.id.mimikopi.core.ear.EarState
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.core.exam.ExamPart
import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.exam.Grade
import com.id.mimikopi.core.exam.PartMark
import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.progress.CheckMetrics
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.input.OutputRoute
import com.id.mimikopi.piano.NoteScheduler
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.components.SecondaryButton
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

enum class EarPhase { LOADING, LISTEN, ANSWER, FEEDBACK, DONE }

/** The end of a round: first-try results and what changed. */
data class EarSummary(
    val right: Int,
    val asked: Int,
    val byKind: List<Pair<EarKind, Pair<Int, Int>>>,
    val better: List<String>,
    val missed: List<String>,
    /** A level test's verdict lines (empty in practice). */
    val verdict: List<String> = emptyList(),
    val passed: Boolean? = null,
)

data class EarUi(
    val phase: EarPhase = EarPhase.LOADING,
    val questions: List<EarQuestion> = emptyList(),
    val index: Int = 0,
    /** Notes played back so far (melody), as places in the key. */
    val played: List<Int> = emptyList(),
    val chosen: String? = null,
    val right: Boolean? = null,
    /** Rhythm: beat of the answer bar now (1-4), or 0 while listening. */
    val beat: Int = 0,
    val taps: Int = 0,
    val results: List<EarResult> = emptyList(),
    val summary: EarSummary? = null,
    val test: Boolean = false,
    val title: String = "",
    /** Why nothing can start (a test locked for today). */
    val message: String? = null,
    /** Note-name buttons for playing a melody back: the scale and the out-of-key notes taught. */
    val places: List<Int> = FunctionalEar.MAJOR,
    val audio: EarAudioStage = EarAudioStage.IDLE,
    val paused: Boolean = false,
    val countIn: Int? = null,
) {
    val question: EarQuestion? get() = questions.getOrNull(index)
}

/**
 * One round of the ear course (or an EAR level test): plays each question, takes the answer from a button, the MIDI
 * piano or touch answer cards, says right or wrong with the answer played, and at the end records every first-try
 * answer per item ([EarProgress]). Tests use fixed items and keys, a single answer and no hints.
 */
class EarSessionViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val piano = services.piano
    private val hub = services.inputHub
    private val dao = services.database.settingsDao()
    private val scheduler = NoteScheduler(piano)
    private val random = Random(System.nanoTime())

    private val check = SkillBook.parseKey(args.get<String>("check"))?.takeIf { it.run is CheckRun.EarRun }
    private val focus: EarKind? = args.get<String>("focus")?.let { f -> EarKind.entries.firstOrNull { it.name == f } }
    /** The aural part of a grade exam (the sitting's id), taken once. */
    private val examId: String? = args.get<String>("exam")
    private var examGrade: Grade? = null
    /** Level tests and exams: fixed items, a single answer, no hints. */
    private val testLike: Boolean get() = check != null || examId != null

    private val _ui = MutableStateFlow(EarUi(test = check != null || examId != null))
    val ui: StateFlow<EarUi> = _ui.asStateFlow()

    private var state = EarState()
    private var holding = false
    private var active = false
    private var lifecycleGeneration = 0L
    private var silentInput: AutoCloseable? = null
    private val audio = EarAudioPlayer(viewModelScope, scheduler, onStage = { stage -> _ui.update { it.copy(audio = stage) } })
    private var answerOpenedNs = 0L
    private var feedbackReadyNs = 0L
    private var answerStartedNs = 0L
    private var startedAt = 0L
    private val playedMidi = mutableListOf<Int>()
    /** Rhythm: the answer bar's downbeat (System.nanoTime) and the taps in it. */
    private var barStartNs = 0L
    private val tapTimes = mutableListOf<Long>()
    private var latenciesNs: Map<InputSource, Long> = emptyMap()

    init {
        viewModelScope.launch {
            state = EarProgress.parse(dao.get(EarProgress.KEY))
            var title = check?.let { tr("ทดสอบระดับหู ${it.level}", "Ear test, level ${it.level}", "耳のテスト レベル${it.level}") } ?: focus?.let { tr("ฝึก${it.title}", "Practise ${it.title}", "${it.title}を練習") } ?: tr("ฝึกหู", "Ear training", "耳トレ")
            if (examId != null) {
                services.progress.load()
                val sitting = services.progress.exams.value.firstOrNull { it.id == examId }
                examGrade = sitting?.let { Exams.grade(it.grade) }
                title = "สอบ ${examGrade?.name ?: ""} · ${ExamPart.AURAL.title}"
                val stop = when {
                    sitting == null || examGrade == null -> tr("ไม่พบการสอบนี้", "Exam not found", "試験が見つかりません")
                    sitting.mark(ExamPart.AURAL) != null -> tr("สอบส่วนนี้ไปแล้ว ได้ ${sitting.mark(ExamPart.AURAL)!!.marks} คะแนน", "You already took this part and got ${sitting.mark(ExamPart.AURAL)!!.marks} marks", "この部は受験済みで${sitting.mark(ExamPart.AURAL)!!.marks}点でした")
                    sitting.abandoned || sitting.day != LocalDate.now().toString() -> tr("การสอบนี้ยกเลิกแล้วหรือหมดวันแล้ว เริ่มสอบใหม่ได้ที่หน้าเกรด", "This exam was cancelled or the day ended. Start a new one on the Grade page", "この試験は中止されたか日付が変わりました。グレードページで新しく受けてください")
                    else -> null
                }
                if (stop != null) {
                    _ui.value = EarUi(phase = EarPhase.DONE, test = true, title = title, message = stop)
                    return@launch
                }
            }
            if (check != null) {
                services.progress.load()
                if (SkillBook.status(check.skill, services.progress.checks.value, LocalDate.now()).lockedToday) {
                    _ui.value = EarUi(phase = EarPhase.DONE, test = true, title = title, message = tr("ลองระดับนี้ครบ 2 ครั้งของวันนี้แล้ว ฝึกต่อแล้วลองใหม่วันอื่น", "You've used both tries for this level today; keep practising and try another day", "このレベルは今日2回挑戦しました。練習して別の日に"))
                    return@launch
                }
            }
            latenciesNs = runCatching {
                val cfg = services.inputConfig.load()
                val route = OutputRoute.current(getApplication())
                InputSource.entries.associateWith { input -> ((cfg.latencyMs[InputConfig.latencyKey(input, route)] ?: 0.0) * 1_000_000).toLong() }
            }.getOrDefault(emptyMap())
            val noTaps = !services.hasCable()
            val questions = if (noTaps && focus == EarKind.RHYTHM) emptyList() else makeRound(noTaps)
            // A test or exam asks its rhythm taps too: without a cable it waits for the PC (or a cable).
            if (noTaps && (questions.isEmpty() || questions.any { it.kind == EarKind.RHYTHM })) {
                _ui.value = EarUi(phase = EarPhase.DONE, test = testLike, title = title, message = com.id.mimikopi.core.input.TimingInput.NEED_CABLE)
                return@launch
            }
            _ui.value = EarUi(phase = EarPhase.LISTEN, questions = questions, test = testLike, title = title, places = places(), message = _ui.value.message, paused = _ui.value.paused)
            startedAt = System.currentTimeMillis()
            startIfReady()
        }
        viewModelScope.launch {
            hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) onKey(e.note, e.timeNanos, e.source) }
        }
    }

    private fun places(): List<Int> {
        val items = examGrade?.aural?.let { it.items + it.context } ?: check?.level?.let { EarCourse.test(it)?.items } ?: EarProgress.unlocked(state)
        return (FunctionalEar.MAJOR + items.filter { EarCourse.kindOf(it) == EarKind.DEGREE }.map { it.substringAfter(':').toInt() }).distinct().sorted()
    }

    /** [noTaps]: no piano on a cable, so practice leaves out rhythm taps (learning-system.md 2.11). */
    private fun makeRound(noTaps: Boolean = !services.hasCable()): List<EarQuestion> {
        examGrade?.let { return Exams.auralRound(it, random) }
        val level = check?.level
        if (level != null) {
            val test = EarCourse.test(level) ?: return emptyList()
            return EarProgress.testRound(test, random)
        }
        return EarProgress.round(state, System.currentTimeMillis(), random, focus, without = if (noTaps) setOf(EarKind.RHYTHM) else emptySet())
    }

    fun onStart() {
        if (active) return
        active = true
        val ticket = ++lifecycleGeneration
        silentInput = hub.silenceInput()
        viewModelScope.launch {
            var acquired = false
            try {
                // Capture ownership inside IO even if this coroutine is cancelled while returning to Main.
                withContext(Dispatchers.IO) { acquired = piano.acquire(needPack = false) }
                if (!active || ticket != lifecycleGeneration) return@launch
                holding = acquired
                acquired = false
                if (holding) {
                    if (_ui.value.phase != EarPhase.DONE) _ui.update { it.copy(message = null) }
                    piano.sustain(false)
                    startIfReady()
                } else _ui.update { it.copy(paused = true, message = tr("เปิดเสียงเปียโนไม่ได้ ลองออกแล้วเข้าหน้านี้อีกครั้ง", "Couldn't start the piano sound; leave this page and come back", "ピアノ音を開始できません。このページを出てもう一度入ってください")) }
            } finally {
                if (acquired) withContext(NonCancellable + Dispatchers.IO) { piano.release() }
            }
        }
    }

    private fun startIfReady() {
        if (!active || !holding || _ui.value.questions.isEmpty() || _ui.value.phase in setOf(EarPhase.DONE, EarPhase.LOADING)) return
        if (_ui.value.phase == EarPhase.FEEDBACK) {
            feedbackReadyNs = System.nanoTime()
            _ui.update { it.copy(paused = false, audio = EarAudioStage.IDLE) }
        } else if (!audio.busy) ask()
    }

    fun onStop() {
        active = false
        lifecycleGeneration++
        audio.stop()
        _ui.update { it.copy(paused = true, countIn = null) }
        silentInput?.close()
        silentInput = null
        if (!holding) return
        holding = false
        services.appScope.launch(Dispatchers.IO) { piano.release() }
    }

    fun retryAudio() {
        if (active && !holding) { onStop(); onStart() }
    }

    /** Whether this question starts with the key-setting cadence: every time in tests; in practice when the key changed or the item is not yet known. */
    private fun wantsCadence(i: Int): Boolean {
        val qs = _ui.value.questions
        val q = qs.getOrNull(i) ?: return false
        if (!q.cadence) return false
        if (testLike || i == 0) return true
        val prev = qs[i - 1]
        return !prev.cadence || prev.tonic != q.tonic || EarProgress.stat(state, q.item).strength < 0.7
    }

    private fun ask() {
        if (!active || !holding) return
        val i = _ui.value.index
        val q = _ui.value.question ?: return
        playedMidi.clear()
        tapTimes.clear()
        answerStartedNs = 0
        answerOpenedNs = 0
        _ui.update { it.copy(phase = EarPhase.LISTEN, played = emptyList(), chosen = null, right = null,
            beat = 0, taps = 0, countIn = null, paused = false) }
        playQuestion(q, wantsCadence(i))
    }

    private fun playQuestion(q: EarQuestion, cadence: Boolean) {
        if (audio.busy || !active || !holding) return
        _ui.update { it.copy(phase = EarPhase.LISTEN) }
        audio.play(EarAudioPlan.question(q, cadence, rhythm = q.answer is EarAnswer.Taps),
            initial = EarAudioStage.WAIT_RELEASE,
            prepare = { awaitReleaseQuiet(EarAudioStage.PREPARE) }, finish = {
                if (q.answer is EarAnswer.Taps) runAnswerBar(q.answer as EarAnswer.Taps)
                else {
                    answerOpenedNs = System.nanoTime()
                    if (answerStartedNs == 0L) answerStartedNs = answerOpenedNs
                    _ui.update { it.copy(phase = EarPhase.ANSWER) }
                }
            })
    }

    private suspend fun runAnswerBar(answer: EarAnswer.Taps) {
        val beatNs = (60_000.0 / answer.bpm * 1_000_000).toLong()
        val countStart = System.nanoTime() + 100_000_000
        barStartNs = countStart + 4 * beatNs
        for (k in 0 until 4) {
            scheduler.noteOn(countStart + k * beatNs, 96, 100)
            scheduler.noteOff(countStart + k * beatNs + 60_000_000, 96)
        }
        _ui.update { it.copy(audio = EarAudioStage.COUNT_IN, countIn = 4) }
        for (k in 0 until 4) {
            delayUntil(countStart + k * beatNs)
            _ui.update { it.copy(countIn = 4 - k) }
        }
        delayUntil(barStartNs - 80_000_000)
        tapTimes.clear()
        answerOpenedNs = System.nanoTime()
        if (answerStartedNs == 0L) answerStartedNs = answerOpenedNs
        _ui.update { it.copy(phase = EarPhase.ANSWER, audio = EarAudioStage.IDLE, countIn = null, beat = 1) }
        for (b in 1..4) {
            _ui.update { it.copy(beat = b) }
            delayUntil(barStartNs + b * beatNs)
        }
        delay(beatNs / 2_000_000)
        if (_ui.value.phase == EarPhase.ANSWER) finishTaps(answer)
    }

    private suspend fun delayUntil(at: Long) {
        val left = at - System.nanoTime()
        if (left > 0) delay((left + 999_999) / 1_000_000)
    }

    private suspend fun awaitReleaseQuiet(freeStage: EarAudioStage) {
        val released = combine(hub.pressed, hub.sustainPressed) { keys, pedal -> keys.isEmpty() && !pedal }
        awaitEarQuiet(released, EarAudioPlan.RELEASE_QUIET_MS) { held ->
            _ui.update { it.copy(audio = if (held) EarAudioStage.WAIT_RELEASE else freeStage) }
        }
    }

    /** Replays close the answer gate until the final sound and its tail have finished. */
    fun replay() {
        val u = _ui.value
        val q = u.question ?: return
        if (audio.busy || u.paused || !active || !holding) return
        if (u.phase == EarPhase.FEEDBACK) { playFeedback(q); return }
        if (u.phase != EarPhase.ANSWER || q.answer is EarAnswer.Taps) return
        playQuestion(q, cadence = false)
    }

    fun replayCadence() {
        val u = _ui.value
        val q = u.question ?: return
        if (u.phase != EarPhase.ANSWER || u.audio != EarAudioStage.IDLE || audio.busy || u.paused || !q.cadence) return
        playQuestion(q, cadence = true)
    }

    private fun playFeedback(q: EarQuestion, audible: Boolean = true) {
        audio.play(EarAudioPlan.feedback(q, audible), initial = EarAudioStage.FEEDBACK_GAP, prepare = {
            val start = System.nanoTime()
            awaitReleaseQuiet(EarAudioStage.FEEDBACK_GAP)
            delayUntil(start + EarAudioPlan.FEEDBACK_GAP_MS * 1_000_000)
        }, finish = { feedbackReadyNs = System.nanoTime() })
    }

    /** A button. */
    fun choose(id: String) {
        val u = _ui.value
        val q = u.question ?: return
        if (u.phase != EarPhase.ANSWER || u.audio != EarAudioStage.IDLE || u.paused || !active || !holding) return
        when (val a = q.answer) {
            is EarAnswer.Choice -> settle(id == a.id, chosen = id)
            is EarAnswer.Notes -> addNote(q.base + id.substringAfter(':').toInt(), q, a)
            is EarAnswer.Taps -> tap(System.nanoTime(), InputSource.TOUCH)
        }
    }

    /** Melody: undo the last note played back. */
    fun undo() {
        val u = _ui.value
        if (u.phase != EarPhase.ANSWER || u.audio != EarAudioStage.IDLE || u.paused || playedMidi.isEmpty()) return
        playedMidi.removeAt(playedMidi.size - 1)
        val base = u.question?.base ?: return
        _ui.update { it.copy(played = playedMidi.map { m -> Math.floorMod(m - base, 12) }) }
    }

    private fun onKey(midi: Int, timeNs: Long, source: InputSource) {
        val u = _ui.value
        val q = u.question ?: return
        if (u.phase == EarPhase.FEEDBACK && u.audio == EarAudioStage.IDLE && !u.paused && active && holding &&
            timeNs >= feedbackReadyNs && Math.floorMod(midi, 12) == 0 && source != InputSource.TOUCH) {
            next()
            return
        }
        if (timeNs < answerOpenedNs) return
        if (u.phase != EarPhase.ANSWER || u.audio != EarAudioStage.IDLE || u.paused || !active || !holding) return
        when (val a = q.answer) {
            is EarAnswer.Choice -> {
                val id = q.midiChoice(midi) ?: return
                settle(id == a.id, chosen = id)
            }
            is EarAnswer.Notes -> addNote(midi, q, a)
            is EarAnswer.Taps -> tap(timeNs, source)
        }
    }

    private fun addNote(midi: Int, q: EarQuestion, a: EarAnswer.Notes) {
        playedMidi += midi
        _ui.update { it.copy(played = playedMidi.map { m -> Math.floorMod(m - q.base, 12) }) }
        if (playedMidi.size >= a.places.size) settle(EarCourse.judgeNotes(a.places, playedMidi, q.base).all { it })
    }

    private fun tap(timeNs: Long, source: InputSource) {
        val t = timeNs - (if (source == InputSource.TOUCH) 0L else latenciesNs[source] ?: 0L)
        tapTimes += t
        _ui.update { it.copy(taps = tapTimes.size) }
    }

    private fun finishTaps(a: EarAnswer.Taps) {
        val ms = tapTimes.map { (it - barStartNs) / 1_000_000 }.filter { it > -EarCourse.tolerance(a.bpm) }
        settle(EarCourse.judgeTaps(a, ms).first)
    }

    private fun settle(right: Boolean, chosen: String? = null) {
        val u = _ui.value
        val q = u.question ?: return
        val ms = (System.nanoTime() - answerStartedNs) / 1_000_000
        _ui.update { it.copy(phase = EarPhase.FEEDBACK, right = right, chosen = chosen, results = it.results + EarResult(q.item, right, ms)) }
        // Stop the rhythm answer timer before starting a separate, serial feedback job.
        audio.stop()
        playFeedback(q, audible = q.kind != EarKind.RHYTHM || !right)
    }

    fun next() {
        val u = _ui.value
        if (u.phase != EarPhase.FEEDBACK || u.audio != EarAudioStage.IDLE || audio.busy || u.paused || !active) return
        if (u.index + 1 < u.questions.size) {
            _ui.update { it.copy(index = it.index + 1) }
            ask()
        } else finish()
    }

    private fun finish() {
        val u = _ui.value
        _ui.update { it.copy(phase = EarPhase.LOADING) }
        val now = System.currentTimeMillis()
        val before = state
        state = EarProgress.record(state, u.results, now, test = testLike, focus = focus)
        val after = state
        val seconds = (now - startedAt) / 1000
        viewModelScope.launch {
            dao.put(SettingEntity(EarProgress.KEY, EarProgress.encode(after)))
            val items = u.results.map { it.item }.distinct()
            val better = items.filter { EarProgress.stat(after, it).strength - EarProgress.stat(before, it).strength >= 0.1 }.map(EarCourse::label)
            val missed = u.results.filter { !it.right }.map { it.item }.distinct().map(EarCourse::label)
            val byKind = u.results.groupBy { EarCourse.kindOf(it.item) }.map { (k, rs) -> k to (rs.count { it.right } to rs.size) }
            var summary = EarSummary(u.results.count { it.right }, u.results.size, byKind, better, missed)
            val level = check
            val exam = examId
            if (exam != null) {
                val right = u.results.count { it.right }
                val marks = Exams.auralMarks(right, u.results.size)
                services.progress.recordExam(exam, ExamPart.AURAL, PartMark(marks, now, right = right, asked = u.results.size))
                services.progress.log("check", seconds)
                summary = summary.copy(
                    passed = marks >= ExamPart.AURAL.pass,
                    verdict = listOf(tr("${ExamPart.AURAL.title}: $marks จาก ${ExamPart.AURAL.max} คะแนน · ${Exams.partBand(ExamPart.AURAL, marks).title}", "${ExamPart.AURAL.title}: $marks of ${ExamPart.AURAL.max} marks · ${Exams.partBand(ExamPart.AURAL, marks).title}", "${ExamPart.AURAL.title}：${ExamPart.AURAL.max}点中${marks}点 · ${Exams.partBand(ExamPart.AURAL, marks).title}"),
                        tr("ตอบถูกครั้งแรก $right จาก ${u.results.size} ข้อ", "Right first time $right of ${u.results.size}", "1回目で正解 ${u.results.size}問中${right}問")),
                )
            } else if (level == null) {
                services.progress.log("ear", seconds)
            } else {
                val m = CheckMetrics(accuracy = if (u.results.isEmpty()) 0.0 else u.results.count { it.right }.toDouble() / u.results.size)
                val verdict = level.judge(m)
                val p = services.progress
                p.addCheck(CheckRecord(level.skill, level.level, LocalDate.now().toString(), verdict.passed, m, at = now))
                p.log("check", seconds)
                val status = SkillBook.status(level.skill, p.checks.value, LocalDate.now())
                summary = summary.copy(
                    passed = verdict.passed,
                    verdict = listOf(if (verdict.passed) tr("ผ่านระดับ ${level.level}: ${level.canDo}", "Passed: ${level.canDo}", "合格：${level.canDo}") else tr("ยังไม่ผ่านระดับ ${level.level}", "Not passed yet", "まだ不合格")) + verdict.lines +
                        (if (verdict.passed) tr("ระดับหูตอนนี้ ${status.level} จาก ${status.maxLevel}", "Ear level now ${status.level} of ${status.maxLevel}", "現在の耳のレベル ${status.maxLevel}中${status.level}")
                        else if (status.lockedToday) tr("ลองครบ 2 ครั้งของวันนี้แล้ว ฝึกต่อแล้วลองใหม่วันอื่น", "Both tries used today; keep practising and try another day", "今日の2回を使いました。練習して別の日に") else tr("ยังลองได้อีกครั้งวันนี้", "One more try today", "今日あと1回挑戦できます")),
                )
            }
            _ui.update { it.copy(phase = EarPhase.DONE, summary = summary) }
        }
    }

    /** Another round (practice), or the test once more when today's tries allow. */
    fun again() {
        if (_ui.value.phase != EarPhase.DONE || audio.busy) return
        viewModelScope.launch {
            if (check != null) {
                services.progress.load()
                if (SkillBook.status(check.skill, services.progress.checks.value, LocalDate.now()).lockedToday) return@launch
            }
            if (examId != null) return@launch
            _ui.value = EarUi(phase = EarPhase.LISTEN, questions = makeRound(), test = testLike, title = _ui.value.title, places = places())
            startedAt = System.currentTimeMillis()
            startIfReady()
        }
    }

    override fun onCleared() {
        onStop()
        scheduler.shutdown()
    }
}

@Composable
fun EarSessionScreen(onBack: () -> Unit, vm: EarSessionViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val midi = com.id.mimikopi.ui.components.midiConnected()
    EarSessionContent(ui, midi, onBack, vm::choose, vm::replay, vm::replayCadence, vm::undo, vm::next, vm::retryAudio) {
        Result(ui, onAgain = vm::again, onDone = onBack)
    }
}

@Composable
private fun Result(ui: EarUi, onAgain: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        ui.message?.let {
            Text(it, fontSize = 17.sp, lineHeight = 24.sp, color = Tokens.Palette.Ink)
            Spacer(Modifier.height(24.dp))
            PrimaryButton(tr("เสร็จ", "Done", "完了"), onDone)
            return@Column
        }
        val s = ui.summary ?: return@Column
        s.verdict.firstOrNull()?.let { Text(it, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(bottom = 6.dp)) }
        s.verdict.drop(1).forEach { Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = Tokens.Palette.InkMuted) }
        if (s.verdict.isNotEmpty()) Spacer(Modifier.height(20.dp))
        val pct = if (s.asked == 0) 0 else s.right * 100 / s.asked
        Text("$pct%", fontSize = 72.sp, lineHeight = 76.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        Text(tr("ถูกตั้งแต่ครั้งแรก ${s.right} จาก ${s.asked} ข้อ", "Right first time ${s.right} of ${s.asked}", "1回目で正解 ${s.asked}問中${s.right}問"), fontSize = 16.sp, color = Tokens.Palette.InkMuted)
        Spacer(Modifier.height(20.dp))
        s.byKind.forEach { (k, rn) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(k.title, fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                Text("${rn.first}/${rn.second}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
            }
        }
        if (s.better.isNotEmpty()) Text(tr("แม่นขึ้น: ", "Better at: ", "上達：") + s.better.joinToString(" · "), fontSize = 15.sp, lineHeight = 21.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp))
        if (s.missed.isNotEmpty()) Text(tr("ยังพลาด: ", "Still missing: ", "まだ間違える：") + s.missed.joinToString(" · ") + tr(" (รอบหน้าจะมาบ่อยขึ้น)", " (comes up more next round)", "（次の回でより多く出ます）"), fontSize = 15.sp, lineHeight = 21.sp,
            color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(28.dp))
        PrimaryButton(if (ui.test) tr("เสร็จ", "Done", "完了") else tr("อีกรอบ", "Again", "もう一回"), if (ui.test) onDone else onAgain)
        if (!ui.test) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(tr("เสร็จ", "Done", "完了"), onDone)
        }
    }
}
