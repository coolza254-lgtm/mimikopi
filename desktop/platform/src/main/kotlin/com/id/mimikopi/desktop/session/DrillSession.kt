package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.exercise.Answer
import com.id.mimikopi.core.exercise.ChordDrills
import com.id.mimikopi.core.exercise.ChordItem
import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.exercise.ChordRun
import com.id.mimikopi.core.exercise.Generators
import com.id.mimikopi.core.exercise.IntervalQuiz
import com.id.mimikopi.core.exercise.KeyFindItem
import com.id.mimikopi.core.exercise.NoteIdItem
import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.exercise.Scoring
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.music.Direction
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.Motion
import com.id.mimikopi.core.music.MotionKind
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.core.practice.ReadingPractice
import com.id.mimikopi.core.progress.Benchmarks
import com.id.mimikopi.core.progress.CheckMetrics
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.RunMistakes
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillLevel
import com.id.mimikopi.core.theory.Chord
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.learning.AttemptEvent
import com.id.mimikopi.desktop.learning.ProgressStore
import com.id.mimikopi.desktop.learning.SettingKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

/** A question in a reading/key drill. */
sealed interface Prompt {
    val srsId: String
    fun accepts(midi: Int): Boolean
    fun answerKeys(range: IntRange): Set<Int>

    data class Key(val item: KeyFindItem) : Prompt {
        override val srsId get() = item.srsId
        override fun accepts(midi: Int) = item.accepts(midi)
        override fun answerKeys(range: IntRange) = range.filter { item.accepts(it) }.toSet()
    }

    data class Note(val item: NoteIdItem) : Prompt {
        override val srsId get() = item.srsId
        override fun accepts(midi: Int) = item.accepts(midi)
        override fun answerKeys(range: IntRange) = setOf(item.pitch.midi)
    }
}

enum class Feedback { CORRECT, WRONG }

sealed interface DrillUi {
    data object Loading : DrillUi
    data class Ready(val title: String, val type: ExerciseType, val items: Int, val bpm: Int?) : DrillUi
    data class Question(
        val prompt: Prompt,
        val index: Int,
        val total: Int,
        val feedback: Feedback?,
        val answeredMidi: Int? = null,
        val firstCorrect: Int = 0,
        val firstAnswered: Int = 0,
        val retry: Boolean = false,
        val canContinue: Boolean = false,
    ) : DrillUi
    data class Pulse(val bpm: Int, val countIn: Int?, val beat: Int, val beats: Int, val taps: Int) : DrillUi
    data class Interval(
        val notes: List<SpelledPitch>,
        val clef: Clef,
        val pair: Int?,
        val played: Int,
        val index: Int,
        val total: Int,
        val feedback: Feedback?,
        val correction: String?,
        val answerMode: String,
        val numbers: List<Int>,
        val withRepeat: Boolean,
    ) : DrillUi
    data class ChordAsk(
        val label: String,
        val index: Int,
        val total: Int,
        val feedback: Feedback?,
        val answer: Set<Int>,
        val secondsLeft: Int? = null,
        val count: Int? = null,
        val next: String? = null,
    ) : DrillUi
    data class Result(val summary: String, val passCriterionMet: Boolean?, val lessonPassed: Boolean, val coach: List<String>) : DrillUi
}

/** What a drill page runs. */
sealed interface DrillSource {
    data class LessonDrill(val lessonId: String, val index: Int) : DrillSource
    /** Due reading items (spaced repetition). */
    data object Review : DrillSource
    /** Free note reading with chosen pools (does not change skill levels). */
    data object NotePractice : DrillSource
    /** The chord tab's practice ([today] = the Today plan's chord part). */
    data class Chords(val today: Boolean) : DrillSource
    /** A level test ("NOTES:3"). */
    data class Check(val key: String) : DrillSource
    /** Benchmark part: "noteid" or "pulse". */
    data class Benchmark(val part: String) : DrillSource
}

/**
 * Runs one drill against the shared input stream: on-screen keys, the computer keyboard and a MIDI piano answer the
 * same way. A port of the Android DrillViewModel: same judging (core Scoring), same saved attempt parameters, same
 * lesson records, so history and progress mean the same on both devices.
 */
class DrillSession(
    private val services: Services,
    val source: DrillSource,
    private val scope: CoroutineScope,
    /** The practice log kind (the Today plan says, e.g. "warmup"). */
    private val via: String? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val repo = services.learning
    private val hub = services.hub
    private val piano = services.piano

    private val lessonId: String? = (source as? DrillSource.LessonDrill)?.lessonId
    private val drillIndex: Int = (source as? DrillSource.LessonDrill)?.index ?: 0
    private val review = source == DrillSource.Review
    private val bench: String? = (source as? DrillSource.Benchmark)?.part
    private val practice = source is DrillSource.Chords
    val notePractice: Boolean = source == DrillSource.NotePractice
    private val practiceRow = if ((source as? DrillSource.Chords)?.today == true) ProgressStore.CHORDS_TODAY else SettingKeys.CHORDS_SETTINGS
    private var chordPractice = ChordPractice()
    private val check: SkillLevel? = (source as? DrillSource.Check)?.key?.let { SkillBook.parseKey(it) }?.takeIf { it.run is CheckRun.DrillRun }

    private val _readingSettings = MutableStateFlow(ReadingPractice())
    val readingSettings: StateFlow<ReadingPractice> = _readingSettings.asStateFlow()
    val readingPitches: List<SpelledPitch> get() = drill?.pool?.map { NoteIdItem.parse("note:$it").pitch }?.distinct().orEmpty()

    private val _ui = MutableStateFlow<DrillUi>(DrillUi.Loading)
    val ui: StateFlow<DrillUi> = _ui.asStateFlow()
    private val _showNames = MutableStateFlow(false)
    val showNames: StateFlow<Boolean> = _showNames.asStateFlow()
    val pressed = hub.pressed
    val nameStyle: NoteNameStyle get() = services.nameStyle.value

    var lesson: Lesson? = null
        private set
    var drill: Drill? = null
        private set
    private var holding = false
    private var active = false
    private var inputSinceNs = 0L
    private var pausedAtNs = 0L
    private var readingTransition: Job? = null
    private var job: Job? = null
    private var helperUses = 0

    var keyRange: IntRange = 48..71
        private set

    val title: String get() = when (val s = source) {
        is DrillSource.LessonDrill -> "${s.lessonId} · ${drill?.title ?: ""}"
        DrillSource.Review -> "ทบทวน"
        DrillSource.NotePractice -> "ฝึกอ่านโน้ต"
        is DrillSource.Chords -> drill?.title ?: "คอร์ด"
        is DrillSource.Check -> "ทดสอบวัดระดับ · ${check?.skill?.title ?: ""} ${check?.level ?: ""}"
        is DrillSource.Benchmark -> drill?.title ?: "วัดผล"
    }

    private suspend fun <T> io(block: () -> T): T = withContext(io) { block() }

    init {
        scope.launch {
            when {
                bench != null -> {
                    drill = if (bench == "pulse") Drill(ExerciseType.E_PULSE, "วัดผล: จังหวะคงที่", items = 1,
                        bpmMin = Benchmarks.PULSE_BPM, bpmMax = Benchmarks.PULSE_BPM, beats = Benchmarks.PULSE_BEATS)
                    else Drill(ExerciseType.E_NOTEID, "วัดผล: ความเร็วอ่านโน้ต", items = Benchmarks.NOTE_ITEMS, pool = listOf("C4:treble"))
                    keyRange = if (bench == "pulse") 57..64 else 48..84
                    val d = drill!!
                    _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
                }
                check != null -> {
                    val d = (check.run as CheckRun.DrillRun).drill
                    drill = d
                    keyRange = when (d.type) {
                        ExerciseType.E_KEYFIND -> if (d.exactOctave) 48..83 else 48..71
                        ExerciseType.E_NOTEID -> 36..84
                        else -> 48..83
                    }
                    _ui.value = if (SkillBook.status(check.skill, services.progress.checks.value, LocalDate.now()).lockedToday)
                        DrillUi.Result("ลองระดับนี้ครบ 2 ครั้งของวันนี้แล้ว", null, false, listOf("ฝึกเรื่องนี้ต่อในวันนี้ แล้วลองใหม่วันอื่น"))
                    else DrillUi.Ready(d.title, d.type, d.items, null)
                }
                notePractice -> setReadingPractice(_readingSettings.value)
                practice -> {
                    chordPractice = ChordPractice.parse(io { services.db.get(practiceRow) })
                    val d = chordPractice.drill()
                    drill = d
                    keyRange = 48..83
                    _ui.value = DrillUi.Ready(d.title, d.type, d.items, null)
                }
                review -> {
                    val due = io { repo.readingDueItems() }
                    drill = Drill(ExerciseType.E_NOTEID, "ทบทวน", items = due.size.coerceAtLeast(1))
                    _ui.value = if (due.isEmpty()) DrillUi.Result("ไม่มีรายการที่ต้องทวนตอนนี้", null, false, listOf("รายการจะกลับมาตามกำหนดของระบบทบทวน"))
                    else DrillUi.Ready("ทบทวน", ExerciseType.E_NOTEID, due.size, null)
                    keyRange = 45..76
                }
                else -> {
                    val l = lessonId?.let { repo.lesson(it) }
                    val d = l?.drills?.getOrNull(drillIndex)
                    lesson = l
                    drill = d
                    if (l == null || d == null) {
                        _ui.value = DrillUi.Result("ไม่พบแบบฝึกนี้", null, false, emptyList())
                    } else {
                        keyRange = when (d.type) {
                            ExerciseType.E_KEYFIND -> if (d.exactOctave) 48..83 else 48..71
                            ExerciseType.E_NOTEID -> 48..72
                            ExerciseType.E_PULSE -> 57..64
                            ExerciseType.E_INTERVAL_READ -> if (d.clef == "bass") 36..64 else 55..86
                            else -> 48..72
                        }
                        _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
                    }
                }
            }
        }
        scope.launch { hub.events.collect(::onInput) }
    }

    /** The page is shown: input counts and the piano opens. */
    fun onStart() {
        if (active) return
        active = true
        inputSinceNs = System.nanoTime()
        if (pausedAtNs > 0 && _ui.value is DrillUi.Question) {
            shownAt += inputSinceNs - maxOf(pausedAtNs, shownAt)
            startedAt += (inputSinceNs - pausedAtNs) / 1_000_000
        }
        pausedAtNs = 0
        val q = _ui.value as? DrillUi.Question
        if (q?.feedback != null && !q.canContinue) scheduleReadingNext(if (q.feedback == Feedback.CORRECT) 650 else 1400)
        if (!holding) holding = piano.acquire()
    }

    /** The page is hidden or closed. */
    fun onStop() {
        if (active) pausedAtNs = System.nanoTime()
        active = false
        readingTransition?.cancel()
        readingTransition = null
        if (!holding) return
        holding = false
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        hub.releaseAll()
        piano.release()
    }

    /** Stops a running drill now (Esc / the stop button): nothing is saved for an unfinished round. */
    fun stop() {
        job?.cancel()
        readingTransition?.cancel()
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        val d = drill ?: return
        if (_ui.value !is DrillUi.Result && _ui.value !is DrillUi.Ready) {
            _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
        }
    }

    private fun scheduleReadingNext(ms: Long) {
        readingTransition?.cancel()
        readingTransition = scope.launch {
            delay(ms)
            if (active) { busy = false; next() }
        }
    }

    /** Note names are a counted helper (learning-system.md 2.4): a drill passed with it is not a full pass. */
    fun toggleNames() {
        val on = !_showNames.value
        _showNames.value = on
        if (on) {
            helperUses++
            services.appScope.launch {
                val n = (services.db.get(SettingKeys.HELPER_NOTE_NAMES_COUNT)?.toIntOrNull() ?: 0) + 1
                services.db.put(SettingKeys.HELPER_NOTE_NAMES_COUNT, n.toString())
            }
        }
    }

    fun setReadingPractice(settings: ReadingPractice) {
        if (!notePractice || _ui.value !is DrillUi.Ready && _ui.value != DrillUi.Loading) return
        _readingSettings.value = settings
        val d = settings.drill()
        drill = d
        val notes = readingPitches.map { it.midi }
        keyRange = notes.min()..notes.max()
        _ui.value = DrillUi.Ready(d.title, ExerciseType.E_NOTEID, d.items, null)
    }

    fun continueReading() {
        val q = _ui.value as? DrillUi.Question ?: return
        if (!active || q.prompt !is Prompt.Note || q.feedback != Feedback.WRONG || !q.canContinue) return
        busy = false
        next()
    }

    // ---------------- reading / key drills ----------------

    private class PromptQueue {
        data class Entry(val prompt: Prompt, val retry: Boolean = false)
        private val entries = ArrayDeque<Entry>()
        private val repeated = mutableSetOf<Prompt>()
        val size: Int get() = entries.size
        fun clear() { entries.clear(); repeated.clear() }
        fun addAll(prompts: List<Prompt>) { entries.addAll(prompts.map { Entry(it) }) }
        fun next(): Entry? = entries.removeFirstOrNull()
        fun repeatOnce(prompt: Prompt) { if (repeated.add(prompt)) entries.addLast(Entry(prompt, retry = true)) }
    }

    private val queue = PromptQueue()
    private val answers = mutableListOf<Answer>()
    private val events = mutableListOf<AttemptEvent>()
    private val readingSources = mutableSetOf<InputSource>()
    private val misses = mutableMapOf<String, Int>()
    private val confusions = mutableListOf<String>()
    private var current: Prompt? = null
    private var currentFirstTry = true
    private var shownAt = 0L
    private var startedAt = 0L
    private var total = 0
    private var seed = 0L
    private var busy = false

    fun start() {
        if (!active || _ui.value !is DrillUi.Ready) return
        val d = drill ?: return
        // The pulse is judged by timing: only from a piano on a cable (the page says so and keeps Start off).
        if ((bench == "pulse" || d.judgesTiming) && !services.hasCable()) return
        _ui.value = DrillUi.Loading
        readingTransition?.cancel()
        startedAt = System.currentTimeMillis()
        seed = startedAt
        answers.clear(); events.clear(); readingSources.clear(); misses.clear(); confusions.clear(); queue.clear(); busy = false
        helperUses = if (_showNames.value) 1 else 0
        job?.cancel()
        job = scope.launch {
            if (bench == "noteid") {
                queue.addAll(Benchmarks.noteIdItems().map { Prompt.Note(it) })
                total = queue.size
                next()
                return@launch
            }
            if (bench == "pulse") { runPulse(null, d); return@launch }
            if (review) {
                val due = io { repo.readingDueItems() }
                queue.addAll(due.map { id ->
                    if (id.startsWith("key:")) Prompt.Key(KeyFindItem(Letter.valueOf(id.removePrefix("key:")), null))
                    else Prompt.Note(NoteIdItem.parse(id))
                })
                total = queue.size
                if (total == 0) return@launch
                next()
                return@launch
            }
            val l = lesson
            if (l != null) io { repo.enrol(l.srsItems) } else if (!practice && !notePractice && check == null) return@launch
            when (d.type) {
                ExerciseType.E_KEYFIND -> queue.addAll(Generators.keyFind(d, seed).map { Prompt.Key(it) })
                ExerciseType.E_NOTEID -> queue.addAll(Generators.noteId(d, seed, if (notePractice) emptyList() else io { repo.dueItems("note:") }).map { Prompt.Note(it) })
                ExerciseType.E_PULSE -> { runPulse(l, d); return@launch }
                ExerciseType.E_INTERVAL_READ -> { startIntervals(d); return@launch }
                ExerciseType.E_CHORD_BUILD -> { startChordBuild(d); return@launch }
                ExerciseType.E_CHORD_CHANGE -> { runChordChange(l, d); return@launch }
                else -> return@launch
            }
            total = queue.size
            next()
        }
    }

    private fun next() {
        val entry = queue.next()
        if (entry == null) {
            finishReading()
            return
        }
        val p = entry.prompt
        current = p
        currentFirstTry = !entry.retry
        shownAt = System.nanoTime()
        _ui.value = DrillUi.Question(p, (answers.size + 1).coerceAtMost(total), total, null, firstCorrect = answers.count { it.correct }, firstAnswered = answers.size, retry = entry.retry)
    }

    private fun onInput(e: InputEvent) {
        if (!active || e is InputEvent.NoteOn && e.timeNanos < inputSinceNs) return
        if (_ui.value is DrillUi.ChordAsk) { onChordInput(e); return }
        if (e !is InputEvent.NoteOn || e.velocity <= 0) return
        (_ui.value as? DrillUi.Interval)?.let { onIntervalPlay(it, e.note); return }
        val q = _ui.value as? DrillUi.Question ?: return
        if (q.feedback == Feedback.WRONG && q.canContinue && e.timeNanos > shownAt + 300_000_000 && hub.pressed.value.size <= 1 && q.prompt.accepts(e.note)) {
            continueReading()
            return
        }
        if (busy || q.feedback != null || e.timeNanos < shownAt) return
        val p = current ?: return
        val responseMs = ((e.timeNanos - shownAt) / 1_000_000).coerceAtLeast(0)
        val ok = p.accepts(e.note)
        val expected = (p as? Prompt.Note)?.item?.pitch?.midi ?: (p as? Prompt.Key)?.item?.target?.midi
        events += AttemptEvent(expectedPitch = expected, actualPitch = e.note, actualOnsetMs = responseMs, velocity = e.velocity)
        if (currentFirstTry) {
            readingSources += e.source
            answers += Answer(ok, responseMs, firstTry = true)
            if (bench == null) services.appScope.launch { repo.review(p.srsId, ok, responseMs) }
        }
        if (!ok) {
            misses[p.srsId] = (misses[p.srsId] ?: 0) + 1
            if (currentFirstTry) when (p) {
                is Prompt.Note -> confusions += "${p.item.pitch}>${SpelledPitch.fromMidiSharp(e.note)}"
                is Prompt.Key -> confusions += "${p.item.target ?: p.item.letter.name}>" + SpelledPitch.fromMidiSharp(e.note).let { pp -> if (p.item.target != null) pp.toString() else pp.letter.name }
            }
            queue.repeatOnce(p)
        }
        busy = true
        val pauseForCorrection = p is Prompt.Note && bench == null && check == null && !ok
        _ui.value = q.copy(feedback = if (ok) Feedback.CORRECT else Feedback.WRONG, answeredMidi = e.note,
            firstCorrect = answers.count { it.correct }, firstAnswered = answers.size, canContinue = pauseForCorrection)
        if (pauseForCorrection) return
        scheduleReadingNext(if (ok) 650 else 1400)
    }

    private fun finishReading() {
        val d = drill ?: return
        if (bench == "noteid") { finishNoteBenchmark(); return }
        val l = lesson
        val acc = Scoring.accuracy(answers)
        if (check != null) {
            val times = answers.filter { it.firstTry }.map { it.responseMs }.sorted()
            finishCheck(CheckMetrics(accuracy = acc, medianSec = times.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] / 1000.0 }))
            return
        }
        logRun()
        if (bench == null && (d.type == ExerciseType.E_NOTEID || d.type == ExerciseType.E_KEYFIND)) {
            val run = RunMistakes(System.currentTimeMillis(), LocalDate.now().toString(), "drill",
                notes = answers.size, wrong = answers.count { !it.correct }, confusions = confusions.toList())
            services.appScope.launch { services.progress.addMistakes(run) }
        }
        val slow = if (d.type == ExerciseType.E_INTERVAL_READ || d.type == ExerciseType.E_CHORD_BUILD) 0 else answers.count { it.correct && it.responseMs > 3000 }
        val isPassDrill = l != null && drillIndex in l.pass.requiredDrills
        val met: Boolean? = if (!isPassDrill || l == null) null else when (d.type) {
            ExerciseType.E_KEYFIND -> Scoring.keyFindPassed(answers, l.pass, d.items)
            ExerciseType.E_NOTEID -> Scoring.noteIdPassed(answers, l.pass, d.items)
            ExerciseType.E_INTERVAL_READ -> Scoring.noteIdPassed(answers, l.pass, d.items * (d.notesPerSet - 1))
            ExerciseType.E_CHORD_BUILD -> Scoring.chordBuildPassed(answers, l.pass, d.items)
            else -> null
        }
        val sessionId = services.session.currentId()
        scope.launch {
            val passedNow = io {
                repo.saveAttempt(
                    exerciseId = if (review) "REVIEW" else if (notePractice) "READING_PRACTICE" else if (practice) "CHORDS:${d.type}" else "${l?.id}:${d.type}:$drillIndex",
                    paramsJson = if (notePractice) _readingSettings.value.let { settings ->
                        """{"seed":$seed,"clef":"${settings.clef}","range":"${settings.range}","accidentals":${settings.accidentals},"items":${settings.items},"inputSources":[${readingSources.joinToString { "\"${it.name}\"" }}]}"""
                    } else """{"seed":$seed,"drill":$drillIndex,"lesson":"${l?.id ?: "review"}","completed":true,"inputSources":"${readingSources.map { it.name }.sorted().joinToString("+")}","mode":"ANSWER"}""",
                    startedAt = startedAt, tempoBpm = null, noteAccuracy = acc, timingWithinTol = null,
                    clean = met, helperUses = helperUses, events = events.toList(), sessionId = sessionId, hand = d.hand,
                )
                if (l != null) repo.recordDrill(l, drillIndex, met == true, helperUses > 0) else false
            }
            val coach = mutableListOf<String>()
            misses.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.let { (id, _) ->
                coach += if (practice) "${describe(id)} พลาดบ่อย ลองกดช้าๆ ทีละตัวแล้วฟังเสียงคอร์ด" else "${describe(id)} พลาดบ่อย รอบหน้าระบบทบทวนจะถามรายการนี้บ่อยขึ้น"
            }
            if (slow > 0) coach += "ถูก แต่มี $slow ข้อที่ใช้เวลาเกิน 3 วินาที ลองมองหากลุ่มคีย์ดำ (หรือจุดอ้างอิง) ก่อน แล้วค่อยหา"
            if (helperUses > 0 && met == true) coach += "ผ่านเกณฑ์ แต่ใช้ปุ่มแสดงชื่อโน้ต จึงยังไม่นับเป็นการผ่านเต็ม ลองอีกครั้งโดยไม่ใช้ตัวช่วย"
            if (met == true && !passedNow && helperUses == 0) coach += if (l != null && l.pass.requiredDrills.size > 1) "ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านส่วนอื่นของบทในวันเดียวกัน และทำซ้ำในวันอื่น" else "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อให้ผ่านบทนี้ (ช่วยให้จำได้นาน)"
            if (met == false && l != null) coach += "ยังไม่ถึงเกณฑ์: ${l.passText}"
            if (passedNow) coach += "ผ่านบทนี้แล้ว บทถัดไปปลดล็อก"
            val pct = (acc * 100).toInt()
            if (notePractice) {
                val median = answers.map { it.responseMs }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] / 1000.0 }
                coach.add(0, "เวลาตอบมัธยฐาน ${"%.1f".format(median)} วินาที · อ่านเสียงและอ็อกเทฟตรงกัน")
                misses.maxByOrNull { it.value }?.let { coach.add(1, "ฝึกต่อ: ${describe(it.key)} · พลาด ${it.value} ครั้ง") }
                coach += "รอบฝึกอ่านโน้ตนี้ไม่เปลี่ยนระดับทักษะ ทดสอบระดับได้ที่หน้า \"ทักษะ\""
            }
            val summary = "ถูกตั้งแต่ครั้งแรก $pct% (${answers.count { it.correct }}/${answers.size})"
            saveChordRun(summary)
            _ui.value = DrillUi.Result(summary, met, passedNow, coach)
        }
    }

    fun describe(srsId: String): String = when {
        srsId.startsWith("chord:") -> "คอร์ด ${srsId.removePrefix("chord:")}"
        srsId.startsWith("key:") -> "คีย์ ${Letter.valueOf(srsId.removePrefix("key:")).let { "${it.name} (${it.solfege})" }}"
        else -> NoteIdItem.parse(srsId).let { "โน้ต ${it.pitch.name(NoteNameStyle.BOTH, withOctave = true)}" }
    }

    // ---------------- benchmarks and the steady pulse ----------------

    private val taps = mutableListOf<InputEvent.NoteOn>()

    private fun finishNoteBenchmark() {
        val correct = answers.count { it.correct && it.firstTry }
        val ms = answers.sumOf { it.responseMs }
        val perMinute = Benchmarks.notesPerMinute(correct, ms)
        val acc = Scoring.accuracy(answers)
        scope.launch {
            io { repo.updateBenchmark { it.copy(noteIdPerMinute = perMinute, noteIdAccuracy = acc) } }
            _ui.value = DrillUi.Result(
                "ถูก $correct/${answers.size} (${(acc * 100).toInt()}%) · อ่านได้ ${"%.1f".format(perMinute)} โน้ตต่อนาที (นับเฉพาะเวลาที่ใช้ตอบ)",
                null, false, listOf("บันทึกผลวัดแล้ว ดูกราฟเทียบได้ที่หน้า \"ความก้าวหน้า\""),
            )
        }
    }

    private suspend fun runPulse(l: Lesson?, d: Drill) {
        val bpm = Generators.pulseBpm(d, seed)
        val countIn = 4
        synchronized(taps) { taps.clear() }
        piano.drainMetronomeTicks()
        piano.setMetronome(true, bpm.toDouble(), 1, intArrayOf(0, 2, 2, 2), 0.9f)
        val stamps = mutableListOf<FrameTimestamp>()
        val ticks = mutableListOf<Long>()
        val collector = scope.launch {
            hub.events.collect { e -> if (e is InputEvent.NoteOn && ticks.size >= countIn) synchronized(taps) { taps += e } }
        }
        val beatMs = 60_000.0 / bpm
        val deadline = System.nanoTime() + ((countIn + d.beats + 4) * beatMs * 1e6).toLong()
        while (ticks.size < countIn + d.beats && System.nanoTime() < deadline) {
            delay(30)
            piano.timestamp()?.let { stamps += FrameTimestamp(it[0], it[1]) }
            ticks += piano.drainMetronomeTicks().toList()
            val n = ticks.size
            _ui.value = DrillUi.Pulse(bpm, if (n < countIn) countIn - n else null, (n - countIn).coerceAtLeast(0), d.beats, synchronized(taps) { taps.size })
        }
        delay((beatMs / 2).toLong())
        piano.setMetronome(false, bpm.toDouble(), 1, intArrayOf(0), 0f)
        collector.cancel()

        val rate = piano.outputSampleRate()
        val tapList = synchronized(taps) { taps.toList() }
        val beats = if (rate > 0 && stamps.size > 5) JitterAnalysis.presentationTimesNanos(ticks.drop(countIn), stamps, rate) else emptyList()
        val source = tapList.groupingBy { it.source }.eachCount().maxByOrNull { it.value }?.key ?: InputSource.TOUCH
        val latency = services.latencyMs(source)
        val offsets = Scoring.tapOffsetsMs(beats, tapList.map { it.timeNanos }, latency ?: 0.0, beatMs * 0.45)
        val tolerance = repo.toleranceMs(l?.stage ?: 1, bpm.toDouble(), benchmark = l == null)
        val stats = Scoring.pulse(offsets, tolerance)
        if (l == null) {
            val enough = stats.taps >= d.beats * 3 / 4
            if (enough) io { repo.updateBenchmark { it.copy(pulseSdMs = stats.sdMs) } }
            _ui.value = DrillUi.Result(
                "$bpm BPM · กด ${stats.taps}/${d.beats} · ความคลาดเคลื่อน (SD) ${"%.0f".format(stats.sdMs)} ms",
                null, false,
                listOf(if (enough) "บันทึกผลวัดแล้ว" else "กดไม่ครบพอจะวัดผล ลองใหม่อีกครั้ง") +
                    if (latency == null) listOf("ยังไม่ได้วัดความหน่วงของเครื่อง ค่าเฉลี่ยอาจเลื่อน แต่ค่า SD ไม่ขึ้นกับความหน่วงคงที่") else emptyList(),
            )
            return
        }
        val isPassDrill = drillIndex in l.pass.requiredDrills
        val met = if (isPassDrill) Scoring.pulsePassed(stats, l.pass, d.beats) else null
        logRun()
        val sessionId = services.session.currentId()
        val passedNow = io {
            repo.saveAttempt(
                exerciseId = "${l.id}:${d.type}:$drillIndex",
                paramsJson = """{"seed":$seed,"bpm":$bpm,"beats":${d.beats},"latencyMs":${latency ?: 0.0},"completed":true,"mode":"PULSE","inputSources":"${tapList.map { it.source.name }.distinct().sorted().joinToString("+")}"}""",
                startedAt = startedAt, tempoBpm = bpm.toDouble(), noteAccuracy = null, timingWithinTol = stats.within,
                clean = met, helperUses = 0,
                events = offsets.map { AttemptEvent(expectedPitch = null, actualPitch = null, offsetMs = it.toLong()) },
                sessionId = sessionId,
            )
            repo.recordDrill(l, drillIndex, met == true, helperUsed = false)
        }
        val coach = mutableListOf<String>()
        val missed = d.beats - stats.taps
        if (missed > d.beats / 4) coach += "กดไม่ครบ ($missed จังหวะหายไป) ลองนับออกเสียงตลอด แล้วกดทุกครั้งที่นับ"
        if (abs(stats.meanMs) > 40) {
            val dir = if (stats.meanMs > 0) "ช้ากว่า" else "เร็วกว่า"
            coach += if (latency == null) "ส่วนใหญ่กด${dir}คลิกเท่าๆ กัน (${"%.0f".format(abs(stats.meanMs))} ms) อาจเป็นความหน่วงของเครื่อง ลองวัดความหน่วงที่หน้า \"เสียงและ MIDI\" ก่อน"
            else "ส่วนใหญ่กด${dir}คลิก ลองนับออกเสียงหรือเคาะเท้าไปด้วย"
        }
        if (met == true && !passedNow) coach += "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อให้ผ่านบทนี้"
        if (met == false) coach += "ยังไม่ถึงเกณฑ์: ${l.passText}"
        if (passedNow) coach += "ผ่านบทนี้แล้ว"
        if (coach.isEmpty()) coach += l.tips.first()
        _ui.value = DrillUi.Result(
            "$bpm BPM · กด ${stats.taps}/${d.beats} · ความคลาดเคลื่อน (SD) ${"%.0f".format(stats.sdMs)} ms · เฉลี่ย ${"%+.0f".format(stats.meanMs)} ms" +
                (if (latency == null) " (ยังไม่ได้หักความหน่วง)" else " (หักความหน่วง ${"%.0f".format(latency)} ms แล้ว)"),
            met, passedNow, coach,
        )
    }

    // ---------------- interval reading ----------------

    private var sets: List<List<SpelledPitch>> = emptyList()
    private var setIndex = 0
    private var pairTried = false

    private fun startIntervals(d: Drill) {
        sets = ScoreGenerators.intervalSets(d, seed)
        setIndex = 0
        total = sets.size
        showPair(d, 0)
    }

    private fun showPair(d: Drill, pair: Int?) {
        val notes = sets[setIndex]
        pairTried = false
        shownAt = System.nanoTime()
        _ui.value = DrillUi.Interval(notes, ScoreGenerators.clefOf(d), pair, 0, setIndex + 1, sets.size, null, null,
            d.answer, IntervalQuiz.numbersFor(d.intervals), 0 in d.intervals)
    }

    fun answerInterval(answer: Motion) {
        val d = drill ?: return
        val q = _ui.value as? DrillUi.Interval ?: return
        val pair = q.pair ?: return
        if (busy || q.feedback != null) return
        val expected = Motion.between(q.notes[pair], q.notes[pair + 1])
        val ok = IntervalQuiz.isCorrect(expected, answer, d.answer)
        val responseMs = (System.nanoTime() - shownAt) / 1_000_000
        if (!pairTried) answers += Answer(ok, responseMs, firstTry = true)
        pairTried = true
        events += AttemptEvent(expectedPitch = q.notes[pair + 1].midi, actualPitch = null, actualOnsetMs = responseMs)
        busy = true
        _ui.value = q.copy(feedback = if (ok) Feedback.CORRECT else Feedback.WRONG, correction = if (ok) null else describeMotion(expected, d.answer))
        scope.launch {
            delay(if (ok) 400 else 1600)
            busy = false
            val next = pair + 1
            if (next < q.notes.size - 1) showPair(d, next)
            else _ui.value = q.copy(pair = null, played = 0, feedback = null, correction = null)
        }
    }

    private fun onIntervalPlay(q: DrillUi.Interval, midi: Int) {
        val d = drill ?: return
        if (q.pair != null || busy) return
        if (midi != q.notes[q.played].midi) {
            _ui.value = q.copy(feedback = Feedback.WRONG)
            return
        }
        val played = q.played + 1
        if (played < q.notes.size) {
            _ui.value = q.copy(played = played, feedback = null)
            return
        }
        _ui.value = q.copy(played = played, feedback = Feedback.CORRECT)
        busy = true
        scope.launch {
            delay(500)
            busy = false
            setIndex++
            if (setIndex < sets.size) showPair(d, 0) else finishReading()
        }
    }

    fun describeMotion(m: Motion, mode: String): String {
        val dir = when (m.direction) {
            Direction.UP -> "ขึ้น"
            Direction.DOWN -> "ลง"
            Direction.SAME -> ""
        }
        if (m.kind == MotionKind.REPEAT) return "ซ้ำ"
        return if (mode == "INTERVAL") "ระยะ ${m.intervalNumber} $dir" else (if (m.kind == MotionKind.STEP) "ขั้น" else "ข้าม") + dir
    }

    // ---------------- chords ----------------

    private val chordQueue = ArrayDeque<ChordItem>()
    private val chordRequeued = mutableSetOf<ChordItem>()
    private var chordItem: ChordItem? = null
    private var chordJudge: Job? = null
    private val fresh = mutableSetOf<Int>()
    private var changeItems: List<ChordItem> = emptyList()
    private var changeIndex = 0
    private var changeCount = 0
    private var struckSinceAdvance = false

    private fun startChordBuild(d: Drill) {
        chordQueue.clear(); chordRequeued.clear()
        chordQueue += ChordDrills.build(d, seed)
        total = chordQueue.size
        nextChord()
    }

    private fun nextChord() {
        val item = chordQueue.removeFirstOrNull()
        if (item == null) { finishReading(); return }
        chordItem = item
        currentFirstTry = item !in chordRequeued
        fresh.clear()
        shownAt = System.nanoTime()
        _ui.value = DrillUi.ChordAsk(item.label, (answers.size + 1).coerceAtMost(total), total, null, emptySet())
    }

    private fun voicingKeys(c: Chord): Set<Int> {
        val upper = c.inversions(60).first()
        val bass = c.bass?.let { 48 + Math.floorMod(it.pc, 12) }
        return (upper + listOfNotNull(bass)).toSet()
    }

    private fun onChordInput(e: InputEvent) {
        val q = _ui.value as? DrillUi.ChordAsk ?: return
        when (e) {
            is InputEvent.NoteOn -> { fresh += e.note; struckSinceAdvance = true }
            is InputEvent.NoteOff -> Unit
            else -> return
        }
        if (q.secondsLeft != null) {
            chordJudge?.cancel()
            chordJudge = scope.launch { delay(90); judgeChange() }
            return
        }
        if (e !is InputEvent.NoteOn || busy || q.feedback != null) return
        chordJudge?.cancel()
        chordJudge = scope.launch { delay(220); judgeBuild() }
    }

    private fun judgeBuild() {
        val q = _ui.value as? DrillUi.ChordAsk ?: return
        val item = chordItem ?: return
        val held = hub.pressed.value intersect fresh
        if (held.isEmpty()) return
        val ok = Chord.matches(item.chord, held)
        if (!ok && held.size < item.chord.essential.size) return
        val responseMs = (System.nanoTime() - shownAt) / 1_000_000
        val id = "chord:${item.label}"
        events += AttemptEvent(expectedPitch = item.chord.root.pc, actualPitch = held.min(), actualOnsetMs = responseMs)
        if (currentFirstTry) answers += Answer(ok, responseMs, firstTry = true)
        if (!ok) {
            misses[id] = (misses[id] ?: 0) + 1
            if (item !in chordRequeued) { chordRequeued += item; chordQueue.addLast(item) }
        }
        busy = true
        _ui.value = q.copy(feedback = if (ok) Feedback.CORRECT else Feedback.WRONG, answer = if (ok) emptySet() else voicingKeys(item.chord))
        scope.launch {
            delay(if (ok) 450 else 1800)
            busy = false
            nextChord()
        }
    }

    private suspend fun runChordChange(l: Lesson?, d: Drill) {
        changeItems = ChordDrills.changes(d)
        if (changeItems.isEmpty()) return
        changeIndex = 0; changeCount = 0; struckSinceAdvance = false
        val seconds = d.seconds.coerceAtLeast(10)
        val end = System.nanoTime() + seconds * 1_000_000_000L
        showChange(seconds)
        while (System.nanoTime() < end) {
            delay(200)
            val left = ((end - System.nanoTime()) / 1_000_000_000L).toInt() + 1
            (_ui.value as? DrillUi.ChordAsk)?.let { if (it.secondsLeft != left) _ui.value = it.copy(secondsLeft = left.coerceAtLeast(0)) }
        }
        chordJudge?.cancel()
        finishChordChange(l, d, seconds)
    }

    private fun showChange(secondsLeft: Int) {
        val item = changeItems[changeIndex]
        val next = changeItems[(changeIndex + 1) % changeItems.size]
        _ui.value = DrillUi.ChordAsk(item.label, changeCount + 1, 0, null, emptySet(), secondsLeft, changeCount, next.label)
    }

    private fun judgeChange() {
        val q = _ui.value as? DrillUi.ChordAsk ?: return
        if (!struckSinceAdvance) return
        val held = hub.pressed.value
        if (held.isEmpty() || !Chord.matches(changeItems[changeIndex].chord, held)) return
        changeCount++
        changeIndex = (changeIndex + 1) % changeItems.size
        struckSinceAdvance = false
        showChange(q.secondsLeft ?: 0)
    }

    private suspend fun finishChordChange(l: Lesson?, d: Drill, seconds: Int) {
        val summary = "เปลี่ยนคอร์ดถูก $changeCount ครั้งใน $seconds วินาที"
        if (check != null) { finishCheck(CheckMetrics(count = changeCount)); return }
        logRun()
        if (l == null) {
            saveChordRun(summary)
            _ui.value = DrillUi.Result(summary, null, false,
                if (changeCount == 0) listOf("ยังไม่มีครั้งที่ถูก ลองเริ่มจากเวลานานขึ้น หรือฝึกแต่ละคอร์ดในแบบชื่อคอร์ดก่อน") else emptyList())
            return
        }
        val isPassDrill = drillIndex in l.pass.requiredDrills
        val met = if (isPassDrill) Scoring.chordChangePassed(changeCount, l.pass) else null
        val sessionId = services.session.currentId()
        val passedNow = io {
            repo.saveAttempt(
                exerciseId = "${l.id}:${d.type}:$drillIndex",
                paramsJson = """{"seed":$seed,"drill":$drillIndex,"lesson":"${l.id}","count":$changeCount,"seconds":$seconds,"completed":true,"mode":"CHORD_CHANGE"}""",
                startedAt = startedAt, tempoBpm = null, noteAccuracy = null, timingWithinTol = null,
                clean = met, helperUses = helperUses, events = events.toList(), sessionId = sessionId, hand = d.hand,
            )
            repo.recordDrill(l, drillIndex, met == true, helperUses > 0)
        }
        val coach = mutableListOf<String>()
        val need = l.pass.minCount
        if (need != null && changeCount < need) coach += "ต้องการอย่างน้อย $need ครั้ง ลองค้างโน้ตที่เหมือนกันระหว่างคอร์ดไว้ แล้วเลื่อนเฉพาะตัวที่ต่าง"
        if (helperUses > 0 && met == true) coach += "ผ่านเกณฑ์ แต่ใช้ปุ่มแสดงชื่อโน้ต จึงยังไม่นับเป็นการผ่านเต็ม"
        if (met == true && !passedNow && helperUses == 0) coach += if (l.pass.requiredDrills.size > 1) "ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านส่วนอื่นของบทในวันเดียวกัน และทำซ้ำในวันอื่น" else "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่น"
        if (passedNow) coach += "ผ่านบทนี้แล้ว บทถัดไปปลดล็อก"
        if (coach.isEmpty()) l.tips.firstOrNull()?.let { coach += it }
        _ui.value = DrillUi.Result(summary, met, passedNow, coach)
    }

    private fun logRun() {
        val kind = via ?: when {
            review -> "review"
            notePractice -> "review"
            practice -> "chords"
            bench != null -> "check"
            else -> "ladder"
        }
        val seconds = (System.currentTimeMillis() - startedAt) / 1000
        services.appScope.launch { services.progress.log(kind, seconds) }
    }

    /** A level test ended: judged by the level's criteria only, recorded, told plainly with the numbers. */
    private fun finishCheck(m: CheckMetrics) {
        val level = check ?: return
        val verdict = level.judge(m)
        val passed = verdict.passed && helperUses == 0
        val seconds = (System.currentTimeMillis() - startedAt) / 1000
        scope.launch {
            val p = services.progress
            io {
                p.addCheck(CheckRecord(level.skill, level.level, LocalDate.now().toString(), passed, m, at = System.currentTimeMillis()))
                p.log("check", seconds)
            }
            val status = SkillBook.status(level.skill, p.checks.value, LocalDate.now())
            val lines = verdict.lines.toMutableList()
            if (helperUses > 0) lines += "เปิดชื่อโน้ตระหว่างทดสอบ ผลนี้จึงไม่นับ"
            lines += if (passed) "ระดับ${level.skill.title}ตอนนี้: ${status.level} จาก ${status.maxLevel}"
            else if (status.lockedToday) "ลองระดับนี้ครบ 2 ครั้งของวันนี้แล้ว ฝึกต่อแล้วลองใหม่วันอื่น"
            else "ยังลองได้อีกครั้งวันนี้ หรือฝึกก่อนแล้วค่อยลอง"
            _ui.value = DrillUi.Result(if (passed) "ผ่านระดับ ${level.level}: ${level.canDo}" else "ยังไม่ผ่านระดับ ${level.level}", passed, false, lines)
        }
    }

    private fun saveChordRun(result: String) {
        if (!practice) return
        services.appScope.launch {
            val runs = ChordRun.add(ChordRun.parse(services.db.get(SettingKeys.CHORDS_RUNS)), ChordRun(LocalDate.now().toString(), chordPractice.summary(), result))
            services.db.put(SettingKeys.CHORDS_RUNS, ChordRun.encode(runs))
        }
    }

    fun again() {
        val d = drill ?: return
        if (check != null) {
            _ui.value = if (SkillBook.status(check.skill, services.progress.checks.value, LocalDate.now()).lockedToday)
                DrillUi.Result("ลองระดับนี้ครบ 2 ครั้งของวันนี้แล้ว", null, false, listOf("ฝึกเรื่องนี้ต่อในวันนี้ แล้วลองใหม่วันอื่น"))
            else DrillUi.Ready(d.title, d.type, d.items, null)
            return
        }
        _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
    }

    fun noteLabel(p: SpelledPitch): String = p.name(nameStyle, withOctave = true)
}
