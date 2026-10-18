package com.id.mimikopi.ui.ear

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.id.mimikopi.R
import com.id.mimikopi.core.ear.EarAnswer
import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarOption
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.EarResult
import com.id.mimikopi.core.ear.EarState
import com.id.mimikopi.core.ear.EarStep
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.core.exam.ExamPart
import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.exam.Grade
import com.id.mimikopi.core.exam.PartMark
import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.progress.CheckMetrics
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.input.OutputRoute
import com.id.mimikopi.piano.NoteScheduler
import com.id.mimikopi.ui.components.KeyHint
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.components.SecondaryButton
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val question: EarQuestion? get() = questions.getOrNull(index)
}

/**
 * One round of the ear course (or an EAR level test): plays each question, takes the answer from a button, the MIDI
 * piano or the on-screen keys, says right or wrong with the answer played, and at the end records every first-try
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
    private var flow: Job? = null
    private var answerStartedNs = 0L
    private var startedAt = 0L
    private val playedMidi = mutableListOf<Int>()
    /** Rhythm: the answer bar's downbeat (System.nanoTime) and the taps in it. */
    private var barStartNs = 0L
    private val tapTimes = mutableListOf<Long>()
    private var latencyNs = 0L

    init {
        viewModelScope.launch {
            state = EarProgress.parse(dao.get(EarProgress.KEY))
            var title = check?.let { "ทดสอบระดับหู ${it.level}" } ?: focus?.let { "ฝึก${it.title}" } ?: "ฝึกหู"
            if (examId != null) {
                services.progress.load()
                val sitting = services.progress.exams.value.firstOrNull { it.id == examId }
                examGrade = sitting?.let { Exams.grade(it.grade) }
                title = "สอบ ${examGrade?.name ?: ""} · ${ExamPart.AURAL.title}"
                val stop = when {
                    sitting == null || examGrade == null -> "ไม่พบการสอบนี้"
                    sitting.mark(ExamPart.AURAL) != null -> "สอบส่วนนี้ไปแล้ว ได้ ${sitting.mark(ExamPart.AURAL)!!.marks} คะแนน"
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
                    _ui.value = EarUi(phase = EarPhase.DONE, test = true, title = title, message = "ลองระดับนี้ครบ 2 ครั้งของวันนี้แล้ว ฝึกต่อแล้วลองใหม่วันอื่น")
                    return@launch
                }
            }
            latencyNs = runCatching {
                val cfg = services.inputConfig.load()
                ((cfg.latencyMs[InputConfig.latencyKey(InputSource.USB_MIDI, OutputRoute.current(getApplication()))] ?: 0.0) * 1_000_000).toLong()
            }.getOrDefault(0L)
            _ui.value = EarUi(phase = EarPhase.LISTEN, questions = makeRound(), test = testLike, title = title, places = places())
            startedAt = System.currentTimeMillis()
            delay(400)
            ask()
        }
        viewModelScope.launch {
            hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) onKey(e.note, e.timeNanos, e.source) }
        }
    }

    private fun places(): List<Int> {
        val items = examGrade?.aural?.let { it.items + it.context } ?: check?.level?.let { EarCourse.test(it)?.items } ?: EarProgress.unlocked(state)
        return (FunctionalEar.MAJOR + items.filter { EarCourse.kindOf(it) == EarKind.DEGREE }.map { it.substringAfter(':').toInt() }).distinct().sorted()
    }

    private fun makeRound(): List<EarQuestion> {
        examGrade?.let { return Exams.auralRound(it, random) }
        val level = check?.level
        if (level != null) {
            val test = EarCourse.test(level) ?: return emptyList()
            return EarProgress.testRound(test, random)
        }
        return EarProgress.round(state, System.currentTimeMillis(), random, focus)
    }

    fun onStart() {
        if (holding) return
        viewModelScope.launch { holding = withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    fun onStop() {
        flow?.cancel()
        scheduler.clear()
        if (!holding) return
        holding = false
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
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
        val i = _ui.value.index
        val q = _ui.value.question ?: return
        playedMidi.clear()
        tapTimes.clear()
        _ui.update { it.copy(phase = EarPhase.LISTEN, played = emptyList(), chosen = null, right = null, beat = 0, taps = 0) }
        play(q, cadence = wantsCadence(i), question = true)
    }

    /** Schedules steps from [t0] on the scheduler; returns the time after the last one. */
    private fun schedule(steps: List<EarStep>, t0: Long): Long {
        var t = t0
        for (s in steps) {
            val len = s.ms * 1_000_000L
            if (s.click) {
                scheduler.noteOn(t, 96, s.velocity)
                scheduler.noteOff(t + 60_000_000L, 96)
            } else {
                s.notes.forEach { scheduler.noteOn(t, it, s.velocity) }
                s.notes.forEach { scheduler.noteOff(t + len - 40_000_000L, it) }
            }
            t += len
        }
        return t
    }

    /** Plays a question (with the cadence when asked); answers are open once it has sounded (rhythm: after a count-in). */
    private fun play(q: EarQuestion, cadence: Boolean, question: Boolean) {
        flow?.cancel()
        scheduler.clear()
        flow = viewModelScope.launch {
            var t = System.nanoTime() + 150_000_000L
            if (cadence) t = schedule(EarCourse.cadenceSteps(q.tonic), t)
            t = schedule(q.sound, t)
            val answer = q.answer
            if (question && answer is EarAnswer.Taps) {
                val beatNs = (60_000.0 / answer.bpm * 1_000_000).toLong()
                for (k in 0 until 4) { scheduler.noteOn(t + k * beatNs, 96, 100); scheduler.noteOff(t + k * beatNs + 60_000_000L, 96) }
                barStartNs = t + 4 * beatNs
                delay(((barStartNs - System.nanoTime()) / 1_000_000 - 80).coerceAtLeast(0))
                tapTimes.clear()
                _ui.update { it.copy(phase = EarPhase.ANSWER, beat = 1) }
                answerStartedNs = System.nanoTime()
                for (b in 1..4) {
                    _ui.update { if (it.phase == EarPhase.ANSWER) it.copy(beat = b) else it }
                    delay(((barStartNs + b * beatNs - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                }
                delay(beatNs / 2_000_000)
                if (_ui.value.phase == EarPhase.ANSWER) finishTaps(answer)
                return@launch
            }
            delay(((t - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
            if (question) {
                answerStartedNs = System.nanoTime()
                _ui.update { if (it.phase == EarPhase.LISTEN) it.copy(phase = EarPhase.ANSWER) else it }
            }
        }
    }

    /** Hear the question again (practice: any time; a test: also allowed, as an examiner plays it twice). */
    fun replay() {
        val u = _ui.value
        val q = u.question ?: return
        if (u.phase == EarPhase.FEEDBACK) { play(q.copy(sound = q.after), cadence = false, question = false); return }
        if (u.phase != EarPhase.ANSWER || q.answer is EarAnswer.Taps) return
        play(q, cadence = false, question = false)
    }

    fun replayCadence() {
        val u = _ui.value
        val q = u.question ?: return
        if (u.phase != EarPhase.ANSWER || !q.cadence) return
        flow?.cancel()
        scheduler.clear()
        schedule(EarCourse.cadenceSteps(q.tonic), System.nanoTime() + 100_000_000L)
    }

    /** A button. */
    fun choose(id: String) {
        val u = _ui.value
        val q = u.question ?: return
        if (u.phase != EarPhase.ANSWER) return
        when (val a = q.answer) {
            is EarAnswer.Choice -> settle(id == a.id, chosen = id)
            is EarAnswer.Notes -> addNote(q.base + id.substringAfter(':').toInt(), q, a)
            is EarAnswer.Taps -> tap(System.nanoTime(), InputSource.TOUCH)
        }
    }

    /** Melody: undo the last note played back. */
    fun undo() {
        val u = _ui.value
        if (u.phase != EarPhase.ANSWER || playedMidi.isEmpty()) return
        playedMidi.removeAt(playedMidi.size - 1)
        val base = u.question?.base ?: return
        _ui.update { it.copy(played = playedMidi.map { m -> Math.floorMod(m - base, 12) }) }
    }

    private fun onKey(midi: Int, timeNs: Long, source: InputSource) {
        val u = _ui.value
        val q = u.question ?: return
        if (u.phase != EarPhase.ANSWER) return
        when (val a = q.answer) {
            is EarAnswer.Choice -> {
                // A key answers degree and bass questions by its place in the key (any octave).
                val place = Math.floorMod(midi - q.base, 12)
                val id = when (q.kind) {
                    EarKind.DEGREE -> "deg:$place"
                    EarKind.BASS -> "bass:$place"
                    else -> return
                }
                if (q.options.any { it.id == id }) settle(id == a.id, chosen = id)
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
        val t = timeNs - (if (source == InputSource.TOUCH) 0L else latencyNs)
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
        // Played after answering: the note walking home, the chord again, the progression with its numerals shown.
        if (q.kind != EarKind.RHYTHM || !right) play(q.copy(sound = q.after), cadence = false, question = false)
    }

    fun next() {
        val u = _ui.value
        if (u.phase != EarPhase.FEEDBACK) return
        if (u.index + 1 < u.questions.size) {
            _ui.update { it.copy(index = it.index + 1) }
            ask()
        } else finish()
    }

    private fun finish() {
        val u = _ui.value
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
                    verdict = listOf("${ExamPart.AURAL.title}: $marks จาก ${ExamPart.AURAL.max} คะแนน · ${Exams.partBand(ExamPart.AURAL, marks).title}",
                        "ตอบถูกครั้งแรก $right จาก ${u.results.size} ข้อ"),
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
                    verdict = listOf(if (verdict.passed) "ผ่านระดับ ${level.level}: ${level.canDo}" else "ยังไม่ผ่านระดับ ${level.level}") + verdict.lines +
                        (if (verdict.passed) "ระดับหูตอนนี้ ${status.level} จาก ${status.maxLevel}"
                        else if (status.lockedToday) "ลองครบ 2 ครั้งของวันนี้แล้ว ฝึกต่อแล้วลองใหม่วันอื่น" else "ยังลองได้อีกครั้งวันนี้"),
                )
            }
            _ui.update { it.copy(phase = EarPhase.DONE, summary = summary) }
        }
    }

    /** Another round (practice), or the test once more when today's tries allow. */
    fun again() {
        viewModelScope.launch {
            if (check != null) {
                services.progress.load()
                if (SkillBook.status(check.skill, services.progress.checks.value, LocalDate.now()).lockedToday) return@launch
            }
            if (examId != null) return@launch
            _ui.value = EarUi(phase = EarPhase.LISTEN, questions = makeRound(), test = testLike, title = _ui.value.title, places = places())
            startedAt = System.currentTimeMillis()
            delay(300)
            ask()
        }
    }

    override fun onCleared() = onStop()
}

@Composable
fun EarSessionScreen(onBack: () -> Unit, vm: EarSessionViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "กลับ", tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
                }
                Text(ui.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                if (ui.questions.isNotEmpty() && ui.phase != EarPhase.DONE) Text("${ui.index + 1}/${ui.questions.size}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
            }
            if (ui.questions.isNotEmpty() && ui.phase != EarPhase.DONE) {
                val f = (ui.index + (if (ui.phase == EarPhase.FEEDBACK) 1 else 0)).toFloat() / ui.questions.size
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Line)) {
                    Box(Modifier.fillMaxWidth(f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Ink))
                }
            }
            when {
                ui.phase == EarPhase.DONE -> Result(ui, onAgain = vm::again, onDone = onBack)
                ui.question != null -> Question(ui, vm)
                ui.phase != EarPhase.LOADING -> Text("ยังไม่มีข้อให้ฝึกในหมวดนี้", fontSize = 17.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 40.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Question(ui: EarUi, vm: EarSessionViewModel) {
    val q = ui.question ?: return
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(q.kind.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.InkMuted)
        Text(prompt(q), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 2.dp))
        Text(
            when (ui.phase) {
                EarPhase.LISTEN -> if (q.answer is EarAnswer.Taps) "ฟังจังหวะ แล้วรอนับ 4 ก่อนเคาะ" else "ฟัง…"
                EarPhase.ANSWER -> hint(q, ui)
                else -> ""
            },
            fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp).heightIn(min = 20.dp),
        )
        Spacer(Modifier.height(20.dp))

        if (ui.phase == EarPhase.FEEDBACK) {
            val right = ui.right == true
            Text(if (right) "ถูก" else "ยังไม่ใช่", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            Text(if (right) q.reveal else "คำตอบ: ${q.reveal}", fontSize = 17.sp, lineHeight = 24.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 4.dp))
            if (q.answer is EarAnswer.Notes && !right) Text("ที่เล่น: " + ui.played.joinToString(" ") { FunctionalEar.solfege(it) }, fontSize = 15.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(24.dp))
            PrimaryButton(if (ui.index + 1 < ui.questions.size) "ต่อไป" else "ดูผล", vm::next)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("ฟังอีกครั้ง", vm::replay)
            return@Column
        }

        when (val a = q.answer) {
            is EarAnswer.Choice -> Options(q, ui, vm::choose)
            is EarAnswer.Notes -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.places.indices.forEach { i ->
                        val note = ui.played.getOrNull(i)
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (note != null) Tokens.Palette.Ink else Tokens.Palette.Group),
                            contentAlignment = Alignment.Center,
                        ) { if (note != null) Text(FunctionalEar.solfege(note), fontSize = 13.sp, color = Tokens.Palette.OnInk) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Degree buttons for those who prefer tapping names; the keyboard (MIDI or on screen) works too.
                Options(q.copy(options = ui.places.map { EarOption("deg:$it", FunctionalEar.solfege(it), FunctionalEar.degree(it)) }), ui, vm::choose, columns = 4)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("ลบตัวล่าสุด", vm::undo)
                KeyHint(marked = emptySet(), nameStyle = NoteNameStyle.SOLFEGE, range = (q.base - 5)..(q.base + 19))
            }
            is EarAnswer.Taps -> {
                Box(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp))
                        .background(if (ui.phase == EarPhase.ANSWER) Tokens.Palette.Ink else Tokens.Palette.Group)
                        .clickable(enabled = ui.phase == EarPhase.ANSWER) { vm.choose("tap") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (ui.phase == EarPhase.ANSWER) "แตะ (หรือกดคีย์ไหนก็ได้)\nจังหวะ ${ui.beat}" else "รอ…",
                        fontSize = 20.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                        color = if (ui.phase == EarPhase.ANSWER) Tokens.Palette.OnInk else Tokens.Palette.InkMuted,
                    )
                }
            }
        }
        if (q.answer !is EarAnswer.Taps) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("ฟังอีกครั้ง", vm::replay)
                if (q.cadence) SecondaryButton("คอร์ดนำ", vm::replayCadence)
            }
        }
    }
}

@Composable
private fun Options(q: EarQuestion, ui: EarUi, onChoose: (String) -> Unit, columns: Int = 2) {
    val cols = if (q.options.size > 6 && columns == 2) 3 else columns
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        q.options.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { o ->
                    Column(
                        Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp)).background(Tokens.Palette.Group)
                            .clickable(enabled = ui.phase == EarPhase.ANSWER) { onChoose(o.id) }.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(o.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, textAlign = TextAlign.Center)
                        o.sub?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = Tokens.Palette.InkMuted, textAlign = TextAlign.Center) }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Result(ui: EarUi, onAgain: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        ui.message?.let {
            Text(it, fontSize = 17.sp, lineHeight = 24.sp, color = Tokens.Palette.Ink)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("เสร็จ", onDone)
            return@Column
        }
        val s = ui.summary ?: return@Column
        s.verdict.firstOrNull()?.let { Text(it, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(bottom = 6.dp)) }
        s.verdict.drop(1).forEach { Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = Tokens.Palette.InkMuted) }
        if (s.verdict.isNotEmpty()) Spacer(Modifier.height(20.dp))
        val pct = if (s.asked == 0) 0 else s.right * 100 / s.asked
        Text("$pct%", fontSize = 72.sp, lineHeight = 76.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        Text("ถูกตั้งแต่ครั้งแรก ${s.right} จาก ${s.asked} ข้อ", fontSize = 16.sp, color = Tokens.Palette.InkMuted)
        Spacer(Modifier.height(20.dp))
        s.byKind.forEach { (k, rn) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(k.title, fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                Text("${rn.first}/${rn.second}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
            }
        }
        if (s.better.isNotEmpty()) Text("แม่นขึ้น: " + s.better.joinToString(" · "), fontSize = 15.sp, lineHeight = 21.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp))
        if (s.missed.isNotEmpty()) Text("ยังพลาด: " + s.missed.joinToString(" · ") + " (รอบหน้าจะมาบ่อยขึ้น)", fontSize = 15.sp, lineHeight = 21.sp,
            color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(28.dp))
        PrimaryButton(if (ui.test) "เสร็จ" else "อีกรอบ", if (ui.test) onDone else onAgain)
        if (!ui.test) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton("เสร็จ", onDone)
        }
    }
}

private fun prompt(q: EarQuestion): String = when (q.kind) {
    EarKind.DEGREE -> "โน้ตนี้คือตัวไหน?"
    EarKind.BASS -> "โน้ตเบส (ต่ำสุด) คือตัวไหน?"
    EarKind.QUALITY -> "คอร์ดนี้แบบไหน?"
    EarKind.PROGRESSION -> "ทางเดินคอร์ดนี้คืออะไร?"
    EarKind.KEY_CHANGE -> "รอบสองเปลี่ยนคีย์ไหม?"
    EarKind.MELODY -> "เล่นทำนองที่ได้ยินกลับ"
    EarKind.RHYTHM -> "เคาะจังหวะที่ได้ยินกลับ"
}

private fun hint(q: EarQuestion, ui: EarUi): String = when (val a = q.answer) {
    is EarAnswer.Notes -> "เล่นบนคีย์บอร์ด (อ็อกเทฟไหนก็ได้) หรือแตะชื่อโน้ต · ${ui.played.size}/${a.places.size}"
    is EarAnswer.Taps -> "เคาะแล้ว ${ui.taps} ครั้ง"
    is EarAnswer.Choice -> if (q.kind == EarKind.DEGREE || q.kind == EarKind.BASS) "แตะคำตอบ หรือกดโน้ตนั้นบนคีย์บอร์ด" else "แตะคำตอบ"
}
