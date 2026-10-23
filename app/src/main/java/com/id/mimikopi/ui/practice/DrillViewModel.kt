package com.id.mimikopi.ui.practice

import com.id.mimikopi.core.i18n.tr
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.exercise.Answer
import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.exercise.ChordRun
import com.id.mimikopi.core.exercise.Generators
import com.id.mimikopi.core.exercise.KeyFindItem
import com.id.mimikopi.core.exercise.NoteIdItem
import com.id.mimikopi.core.exercise.Scoring
import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.input.OutputRoute
import com.id.mimikopi.learning.AttemptEvent
import com.id.mimikopi.ui.reading.ReadingPractice
import com.id.mimikopi.ui.reading.ReadingPromptQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** A question in a reading/key drill. */
sealed interface Prompt {
    val srsId: String
    fun accepts(midi: Int): Boolean
    /** Keys that count as right (for showing the answer). */
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
    /** Interval reading: answer the motion of each neighbouring pair, then play the set. */
    data class Interval(
        val notes: List<SpelledPitch>,
        val clef: com.id.mimikopi.core.notation.Clef,
        /** Pair being asked (notes[pair] -> notes[pair + 1]); null while playing the set. */
        val pair: Int?,
        /** Notes played correctly so far in the play phase. */
        val played: Int,
        val index: Int,
        val total: Int,
        val feedback: Feedback?,
        /** The right answer, shown after a wrong one. */
        val correction: String?,
        val answerMode: String,
        val numbers: List<Int>,
        val withRepeat: Boolean,
    ) : DrillUi
    /** Chord drills: build the asked chord, or keep changing through a progression against the clock. */
    data class ChordAsk(
        val label: String,
        val index: Int,
        val total: Int,
        val feedback: Feedback?,
        /** Keys of one right voicing, shown after a wrong answer. */
        val answer: Set<Int>,
        /** E_CHORD_CHANGE: seconds left, correct changes so far, and the chord after this one. */
        val secondsLeft: Int? = null,
        val count: Int? = null,
        val next: String? = null,
    ) : DrillUi
    data class Result(
        val summary: String,
        val passCriterionMet: Boolean?,
        val lessonPassed: Boolean,
        val coach: List<String>,
    ) : DrillUi
}

enum class Feedback { CORRECT, WRONG }

/**
 * Runs one drill (from a lesson, or the SRS review) against the shared input stream: on-screen keys
 * and a MIDI piano answer the same way. Saves the attempt, SRS reviews and lesson progress.
 * Nav args: "lessonId" + "drill" (index), "review" = true for the due-items review, or "practice" = "chords" for the
 * chord tab's free practice (its settings are read from [SettingKeys.CHORDS_SETTINGS]; no lesson progress).
 */
class DrillViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val repo = services.learning
    private val hub = services.inputHub
    private val piano = services.piano
    private val inputConfig = services.inputConfig

    private val lessonId: String? = args.get<String>("lessonId")
    private val drillIndex: Int = args.get<String>("drill")?.toIntOrNull() ?: 0
    private val review: Boolean = args.get<String>("review") == "true"
    /** Benchmark part (learning-system.md 12.1): "noteid" or "pulse"; fixed sets, results go to the benchmark record. */
    private val bench: String? = args.get<String>("benchmark")
    /** Free chord practice from the chord tab ("chords"), or the Today plan's chord part ("chords-today"). */
    private val practice: Boolean = args.get<String>("practice")?.startsWith("chords") == true
    val notePractice: Boolean = args.get<String>("practice") == "notes"
    private val _readingSettings = MutableStateFlow(ReadingPractice())
    val readingSettings: StateFlow<ReadingPractice> = _readingSettings.asStateFlow()
    val readingPitches: List<SpelledPitch> get() = drill?.pool?.map { NoteIdItem.parse("note:$it").pitch }?.distinct().orEmpty()
    val readingOnly: Boolean get() = drill?.type == ExerciseType.E_NOTEID
    private val practiceRow: String = if (args.get<String>("practice") == "chords-today") com.id.mimikopi.ui.today.ProgressStore.CHORDS_TODAY else SettingKeys.CHORDS_SETTINGS
    private var chordPractice = ChordPractice()
    /** A level test ("NOTES:3"): fixed drill, judged by the level's criteria, recorded on the skill page. */
    private val check: com.id.mimikopi.core.progress.SkillLevel? = com.id.mimikopi.core.progress.SkillBook.parseKey(args.get<String>("check"))
        ?.takeIf { it.run is com.id.mimikopi.core.progress.CheckRun.DrillRun }
    /** The practice log kind this run counts as (the Today plan says, e.g. "warmup"); otherwise from the source. */
    private val via: String? = args.get<String>("via")

    private val _ui = MutableStateFlow<DrillUi>(DrillUi.Loading)
    val ui: StateFlow<DrillUi> = _ui.asStateFlow()

    private val _showNames = MutableStateFlow(false)
    val showNames: StateFlow<Boolean> = _showNames.asStateFlow()
    val pressed = hub.pressed
    var nameStyle: NoteNameStyle = NoteNameStyle.BOTH
        private set

    private var lesson: Lesson? = null
    private var drill: Drill? = null
    private var holding = false
    private var active = false
    private var engineGeneration = 0L
    private var inputSinceNs = 0L
    private var pausedAtNs = 0L
    private var readingTransition: Job? = null
    private var job: Job? = null
    private var helperUses = 0

    /** Keyboard range for the drill: wide enough for every answer, small enough to tap. */
    var keyRange: IntRange = 48..71
        private set

    init {
        viewModelScope.launch {
            repo.load()
            settingsName()
            if (bench != null) {
                drill = if (bench == "pulse") Drill(ExerciseType.E_PULSE, tr("วัดผล: จังหวะคงที่", "Measure: steady beat", "測定：一定のリズム"), items = 1,
                    bpmMin = com.id.mimikopi.core.progress.Benchmarks.PULSE_BPM, bpmMax = com.id.mimikopi.core.progress.Benchmarks.PULSE_BPM,
                    beats = com.id.mimikopi.core.progress.Benchmarks.PULSE_BEATS)
                else Drill(ExerciseType.E_NOTEID, tr("วัดผล: ความเร็วอ่านโน้ต", "Measure: reading speed", "測定：読譜の速さ"), items = com.id.mimikopi.core.progress.Benchmarks.NOTE_ITEMS, pool = listOf("C4:treble"))
                keyRange = if (bench == "pulse") 57..64 else 48..84
                val d = drill!!
                _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
            } else if (check != null) {
                val d = (check.run as com.id.mimikopi.core.progress.CheckRun.DrillRun).drill
                drill = d
                keyRange = when (d.type) {
                    ExerciseType.E_KEYFIND -> if (d.exactOctave) 48..83 else 48..71
                    ExerciseType.E_NOTEID -> 36..84
                    else -> 48..83
                }
                services.progress.load()
                _ui.value = if (com.id.mimikopi.core.progress.SkillBook.status(check.skill, services.progress.checks.value, java.time.LocalDate.now()).lockedToday)
                    DrillUi.Result(tr("ลองระดับนี้ครบ 2 ครั้งของวันนี้แล้ว", "You've tried this level twice today", "このレベルは今日2回挑戦しました"), null, false, listOf(tr("ฝึกเรื่องนี้ต่อในวันนี้ แล้วลองใหม่วันอื่น", "Keep practising this today and try again another day", "今日はこれを練習して、別の日にもう一度")))
                else DrillUi.Ready(d.title, d.type, d.items, null)
            } else if (notePractice) {
                setReadingPractice(_readingSettings.value)
            } else if (practice) {
                chordPractice = ChordPractice.parse(services.database.settingsDao().get(practiceRow))
                val d = chordPractice.drill()
                drill = d
                // Room for a left-hand bass under right-hand chords.
                keyRange = 48..83
                _ui.value = DrillUi.Ready(d.title, d.type, d.items, null)
            } else if (review) {
                val due = repo.readingDueItems()
                drill = Drill(ExerciseType.E_NOTEID, tr("ทบทวน", "Review", "復習"), items = due.size.coerceAtLeast(1))
                _ui.value = if (due.isEmpty()) DrillUi.Result(tr("ไม่มีรายการที่ต้องทวนตอนนี้ 👍", "Nothing to review right now 👍", "今は復習するものがありません 👍"), null, false, listOf(tr("รายการจะกลับมาตามกำหนดของระบบทบทวน", "Items come back on the review schedule", "項目は復習スケジュールに合わせて戻ってきます"))) else DrillUi.Ready(tr("ทบทวน", "Review", "復習"), ExerciseType.E_NOTEID, due.size, null)
                keyRange = 45..76
            } else {
                val l = lessonId?.let { repo.lesson(it) }
                val d = l?.drills?.getOrNull(drillIndex)
                lesson = l
                drill = d
                if (l == null || d == null) {
                    _ui.value = DrillUi.Result(tr("ไม่พบแบบฝึกนี้", "Drill not found", "練習が見つかりません"), null, false, emptyList())
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
        viewModelScope.launch { hub.events.collect(::onInput) }
    }

    private suspend fun settingsName() {
        services.database.settingsDao().get(SettingKeys.NOTE_NAME_STYLE)?.let { runCatching { nameStyle = NoteNameStyle.valueOf(it) } }
    }

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
        val ticket = ++engineGeneration
        viewModelScope.launch {
            var acquired = false
            try {
                withContext(Dispatchers.IO) { acquired = piano.acquire(needPack = false) }
                if (!active || ticket != engineGeneration) return@launch
                holding = acquired
                acquired = false
            } finally {
                if (acquired) withContext(NonCancellable + Dispatchers.IO) { piano.release() }
            }
        }
    }

    fun onStop() {
        if (active) pausedAtNs = System.nanoTime()
        active = false
        engineGeneration++
        readingTransition?.cancel()
        readingTransition = null
        if (!holding) return
        holding = false
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        hub.releaseAll()
        services.appScope.launch(Dispatchers.IO) { piano.release() }
    }

    private fun scheduleReadingNext(ms: Long) {
        readingTransition?.cancel()
        readingTransition = viewModelScope.launch {
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
            viewModelScope.launch {
                val dao = services.database.settingsDao()
                val n = (dao.get(SettingKeys.HELPER_NOTE_NAMES_COUNT)?.toIntOrNull() ?: 0) + 1
                dao.put(SettingEntity(SettingKeys.HELPER_NOTE_NAMES_COUNT, n.toString()))
            }
        }
    }

    fun setReadingPractice(settings: ReadingPractice) {
        if (!notePractice || _ui.value !is DrillUi.Ready && _ui.value != DrillUi.Loading) return
        _readingSettings.value = settings
        drill = settings.drill()
        val notes = readingPitches.map { it.midi }
        keyRange = notes.min()..notes.max()
        _ui.value = DrillUi.Ready(drill!!.title, ExerciseType.E_NOTEID, drill!!.items, null)
    }

    /** Name answers are a reading exercise, not a keyboard performance test. */
    fun answerReadingName(midi: Int) {
        if (notePractice) onInput(InputEvent.NoteOn(midi, 80, System.nanoTime(), InputSource.TOUCH))
    }

    fun continueReading() {
        val q = _ui.value as? DrillUi.Question ?: return
        if (!active || q.prompt !is Prompt.Note || q.feedback != Feedback.WRONG || !q.canContinue) return
        busy = false
        next()
    }

    // ---------------- Reading / key drills ----------------

    private val queue = ReadingPromptQueue()
    private val answers = mutableListOf<Answer>()
    private val events = mutableListOf<AttemptEvent>()
    private val readingSources = mutableSetOf<InputSource>()
    private val misses = mutableMapOf<String, Int>()
    /** Reading confusions of this run as "expected>played" (for the mistakes summary). */
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
        // The pulse is judged by timing: only from a piano on a cable (the screen says so and keeps Start off).
        if ((bench == "pulse" || d.judgesTiming) && !services.hasCable()) return
        _ui.value = DrillUi.Loading
        readingTransition?.cancel()
        startedAt = System.currentTimeMillis()
        seed = startedAt
        answers.clear(); events.clear(); readingSources.clear(); misses.clear(); confusions.clear(); queue.clear(); busy = false
        helperUses = if (_showNames.value) 1 else 0
        job?.cancel()
        job = viewModelScope.launch {
            if (bench == "noteid") {
                queue.addAll(com.id.mimikopi.core.progress.Benchmarks.noteIdItems().map { Prompt.Note(it) })
                total = queue.size
                next()
                return@launch
            }
            if (bench == "pulse") { runPulse(null, d); return@launch }
            if (review) {
                val due = repo.readingDueItems()
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
            if (l != null) repo.enrol(l.srsItems) else if (!practice && !notePractice && check == null) return@launch
            when (d.type) {
                ExerciseType.E_KEYFIND -> queue.addAll(Generators.keyFind(d, seed).map { Prompt.Key(it) })
                ExerciseType.E_NOTEID -> queue.addAll(Generators.noteId(d, seed, if (notePractice) emptyList() else repo.dueItems("note:")).map { Prompt.Note(it) })
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
            if (bench == null) viewModelScope.launch { repo.review(p.srsId, ok, responseMs) }
        }
        if (!ok) {
            misses[p.srsId] = (misses[p.srsId] ?: 0) + 1
            if (currentFirstTry) when (p) {
                is Prompt.Note -> confusions += "${p.item.pitch}>${com.id.mimikopi.core.music.SpelledPitch.fromMidiSharp(e.note)}"
                is Prompt.Key -> confusions += "${p.item.target ?: p.item.letter.name}>" + com.id.mimikopi.core.music.SpelledPitch.fromMidiSharp(e.note).let { pp -> if (p.item.target != null) pp.toString() else pp.letter.name }
                else -> Unit
            }
            // learning-system.md section 10: show the right place, ask again at the end of the round (once).
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
            finishCheck(com.id.mimikopi.core.progress.CheckMetrics(accuracy = acc, medianSec = times.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] / 1000.0 }))
            return
        }
        logRun()
        if (bench == null && (d.type == ExerciseType.E_NOTEID || d.type == ExerciseType.E_KEYFIND)) {
            val run = com.id.mimikopi.core.progress.RunMistakes(
                System.currentTimeMillis(), java.time.LocalDate.now().toString(), "drill",
                notes = answers.size, wrong = answers.count { !it.correct }, confusions = confusions.toList(),
            )
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
        viewModelScope.launch {
            repo.saveAttempt(
                exerciseId = if (review) "REVIEW" else if (notePractice) "READING_PRACTICE" else if (practice) "CHORDS:${d.type}" else "${l?.id}:${d.type}:$drillIndex",
                paramsJson = if (notePractice) _readingSettings.value.let { settings ->
                    """{"seed":$seed,"clef":"${settings.clef}","range":"${settings.range}","accidentals":${settings.accidentals},"items":${settings.items},"inputSources":[${readingSources.joinToString { "\"${it.name}\"" }}]}"""
                } else """{"seed":$seed,"drill":$drillIndex,"lesson":"${l?.id ?: "review"}","completed":true,"inputSources":"${readingSources.map { it.name }.sorted().joinToString("+")}","mode":"ANSWER"}""",
                startedAt = startedAt, tempoBpm = null, noteAccuracy = acc, timingWithinTol = null,
                clean = met, helperUses = helperUses, events = events.toList(), sessionId = services.session.currentId(), hand = d.hand,
            )
            val passedNow = if (l != null) repo.recordDrill(l, drillIndex, met == true, helperUses > 0) else false
            val coach = mutableListOf<String>()
            misses.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.let { (id, _) ->
                coach += if (practice) tr("${describe(id)} พลาดบ่อย ลองกดช้าๆ ทีละตัวแล้วฟังเสียงคอร์ด", "${describe(id)} is often missed; press it slowly one note at a time and listen to the chord", "${describe(id)}をよく間違えます。1音ずつゆっくり押してコードの響きを聞いてみて") else tr("${describe(id)} พลาดบ่อย รอบหน้าระบบทบทวนจะถามรายการนี้บ่อยขึ้น", "${describe(id)} is often missed; the review will ask it more often next time", "${describe(id)}をよく間違えます。次回の復習でより多く出題します")
            }
            if (slow > 0) coach += tr("ถูก แต่มี $slow ข้อที่ใช้เวลาเกิน 3 วินาที ลองมองหากลุ่มคีย์ดำ (หรือจุดอ้างอิง) ก่อน แล้วค่อยหา", "Right, but $slow took longer than 3 seconds; look for the black-key groups (or landmarks) first, then find it", "正解ですが${slow}問は3秒以上かかりました。先に黒鍵のまとまり（または目印）を探してから")
            if (helperUses > 0 && met == true) coach += tr("ผ่านเกณฑ์ แต่ใช้ปุ่มแสดงชื่อโน้ต จึงยังไม่นับเป็นการผ่านเต็ม ลองอีกครั้งโดยไม่ใช้ตัวช่วย", "Criteria met, but the note-name button was used, so it doesn't fully count yet. Try again without help", "基準は満たしましたが音名ボタンを使ったためまだ完全には数えません。補助なしでもう一度")
            if (met == true && !passedNow && helperUses == 0) coach += if (l != null && l.pass.requiredDrills.size > 1) tr("ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านส่วนอื่นของบทในวันเดียวกัน และทำซ้ำในวันอื่น", "This part met the criteria today; the lesson's other parts must pass the same day, and again on another day", "この部分は今日基準を満たしました。同じ日にレッスンの他の部分も合格し、別の日にもくり返してください") else tr("ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อให้ผ่านบทนี้ (ช่วยให้จำได้นาน)", "Met the criteria today; pass again on another day to pass this lesson (helps it stick)", "今日基準を満たしました。別の日にもう一度でこのレッスンに合格（長く覚えるため）")
            if (passedNow) coach += tr("ผ่านบทนี้แล้ว บทถัดไปปลดล็อก", "Lesson passed, the next one is open", "レッスン合格、次が開きました")
            val pct = (acc * 100).toInt()
            if (notePractice) {
                val median = answers.map { it.responseMs }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] / 1000.0 }
                coach.add(0, "เวลาตอบมัธยฐาน ${"%.1f".format(median)} วินาที · อ่านเสียงและอ็อกเทฟตรงกัน")
                misses.maxByOrNull { it.value }?.let { coach.add(1, tr("ฝึกต่อ: ${describe(it.key)} · พลาด ${it.value} ครั้ง", "Keep practising: ${describe(it.key)} · missed ${it.value} times", "練習を続けて：${describe(it.key)} · ${it.value}回ミス")) }
                coach += if (readingSources == setOf(InputSource.TOUCH)) tr("รอบนี้ฝึกอ่านชื่อโน้ตด้วยปุ่มบนมือถือ", "This round practised naming notes with phone buttons", "この回はスマホのボタンで音名を読む練習でした") else tr("รอบนี้ฝึกอ่านโน้ตแล้วหาคีย์ด้วย MIDI", "This round practised reading notes and finding keys with MIDI", "この回は音符を読んでMIDIで鍵盤を探す練習でした")
                coach += tr("รอบฝึกอ่านโน้ตนี้ไม่เปลี่ยนระดับทักษะ ทดสอบระดับได้ที่หน้า \"ทักษะ\"", "Note-reading practice doesn't change skill levels; test your level on the \"Skills\" page", "読譜練習ではスキルレベルは変わりません。レベルは「スキル」ページでテスト")
            }
            val summary = tr("ถูกตั้งแต่ครั้งแรก $pct% (${answers.count { it.correct }}/${answers.size})", "Right first time $pct% (${answers.count { it.correct }}/${answers.size})", "1回目で正解 $pct%（${answers.count { it.correct }}/${answers.size}）")
            saveChordRun(summary)
            _ui.value = DrillUi.Result(summary, met, passedNow, coach)
        }
    }

    private fun describe(srsId: String): String = when {
        srsId.startsWith("chord:") -> "คอร์ด ${srsId.removePrefix("chord:")}"
        srsId.startsWith("key:") -> "คีย์ ${Letter.valueOf(srsId.removePrefix("key:")).let { "${it.name} (${it.solfege})" }}"
        else -> NoteIdItem.parse(srsId).let { tr("โน้ต ${it.pitch.name(NoteNameStyle.BOTH, withOctave = true)}", "Note ${it.pitch.name(NoteNameStyle.BOTH, withOctave = true)}", "音 ${it.pitch.name(NoteNameStyle.BOTH, withOctave = true)}") }
    }

    // ---------------- Steady pulse (lesson 1.3) ----------------

    private val taps = mutableListOf<InputEvent.NoteOn>()

    private fun finishNoteBenchmark() {
        val correct = answers.count { it.correct && it.firstTry }
        val ms = answers.sumOf { it.responseMs }
        val perMinute = com.id.mimikopi.core.progress.Benchmarks.notesPerMinute(correct, ms)
        val acc = Scoring.accuracy(answers)
        viewModelScope.launch {
            repo.updateBenchmark { it.copy(noteIdPerMinute = perMinute, noteIdAccuracy = acc) }
            _ui.value = DrillUi.Result(
                "ถูก $correct/${answers.size} (${(acc * 100).toInt()}%) · อ่านได้ ${"%.1f".format(perMinute)} โน้ตต่อนาที (นับเฉพาะเวลาที่ใช้ตอบ)",
                null, false, listOf(tr("บันทึกผลวัดแล้ว ดูกราฟเทียบได้ที่หน้า \"ความก้าวหน้า\"", "Measurement saved; compare on the graph on the \"Progress\" page", "測定結果を保存しました。「上達」ページのグラフで比べられます")),
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
        val collector = viewModelScope.launch {
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
        val cfg = inputConfig.load()
        val latency = cfg.latencyMs[InputConfig.latencyKey(source, OutputRoute.current(getApplication()))]
        val offsets = Scoring.tapOffsetsMs(beats, tapList.map { it.timeNanos }, latency ?: 0.0, beatMs * 0.45)
        val tolerance = repo.toleranceMs(l?.stage ?: 1, bpm.toDouble(), benchmark = l == null)
        val stats = Scoring.pulse(offsets, tolerance)
        if (l == null) {
            // Benchmark: keep the SD only if enough beats were tapped to mean something.
            val enough = stats.taps >= d.beats * 3 / 4
            if (enough) repo.updateBenchmark { it.copy(pulseSdMs = stats.sdMs) }
            _ui.value = DrillUi.Result(
                "$bpm BPM · กด ${stats.taps}/${d.beats} · ความคลาดเคลื่อน (SD) ${"%.0f".format(stats.sdMs)} ms",
                null, false,
                listOf(if (enough) tr("บันทึกผลวัดแล้ว", "Measurement saved", "測定結果を保存しました") else tr("กดไม่ครบพอจะวัดผล ลองใหม่อีกครั้ง", "Not enough presses to measure, try again", "測定に必要な入力が足りません。もう一度")) +
                    if (latency == null) listOf(tr("ยังไม่ได้วัดความหน่วงของเครื่อง ค่าเฉลี่ยอาจเลื่อน แต่ค่า SD ไม่ขึ้นกับความหน่วงคงที่", "Device latency not measured yet; the average may shift, but the SD isn't affected by a steady latency", "端末の遅延はまだ測っていません。平均はずれることがありますが、SDは一定の遅延の影響を受けません")) else emptyList(),
            )
            return
        }
        val isPassDrill = drillIndex in l.pass.requiredDrills
        val met = if (isPassDrill) Scoring.pulsePassed(stats, l.pass, d.beats) else null
        logRun()

        repo.saveAttempt(
            exerciseId = "${l.id}:${d.type}:$drillIndex",
            paramsJson = """{"seed":$seed,"bpm":$bpm,"beats":${d.beats},"latencyMs":${latency ?: 0.0},"completed":true,"mode":"PULSE"}""",
            startedAt = startedAt, tempoBpm = bpm.toDouble(), noteAccuracy = null, timingWithinTol = stats.within,
            clean = met, helperUses = 0,
            events = offsets.map { AttemptEvent(expectedPitch = null, actualPitch = null, offsetMs = it.toLong()) },
            sessionId = services.session.currentId(),
        )
        val passedNow = repo.recordDrill(l, drillIndex, met == true, helperUsed = false)
        val coach = mutableListOf<String>()
        val missed = d.beats - stats.taps
        if (missed > d.beats / 4) coach += tr("กดไม่ครบ ($missed จังหวะหายไป) ลองนับออกเสียงตลอด แล้วกดทุกครั้งที่นับ", "Not enough presses ($missed beats missing). Count aloud all the way and press on every count", "入力不足（${missed}拍抜け）。ずっと声に出して数え、数えるたびに押しましょう")
        if (abs(stats.meanMs) > 40) {
            val dir = if (stats.meanMs > 0) tr("ช้ากว่า", "late", "遅め") else tr("เร็วกว่า", "early", "早め")
            coach += if (latency == null) "ส่วนใหญ่กด${dir}คลิกเท่าๆ กัน (${"%.0f".format(abs(stats.meanMs))} ms) อาจเป็นความหน่วงของเครื่อง ลองวัดความหน่วงที่หน้า \"ต่อเปียโน (MIDI)\" ก่อน"
            else tr("ส่วนใหญ่กด${dir}คลิก ลองนับออกเสียงหรือเคาะเท้าไปด้วย", "Mostly ${dir} the click; try counting aloud or tapping your foot", "ほとんどクリックより${dir}押しています。声に出して数えるか足でリズムを取ってみて")
        }
        if (met == true && !passedNow) coach += tr("ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อให้ผ่านบทนี้", "Met the criteria today; pass again on another day to pass this lesson", "今日基準を満たしました。別の日にもう一度でこのレッスンに合格")
        if (passedNow) coach += tr("ผ่านบทนี้แล้ว", "Lesson passed", "レッスン合格")
        if (coach.isEmpty()) coach += l.tips.first()
        _ui.value = DrillUi.Result(
            "$bpm BPM · กด ${stats.taps}/${d.beats} · ความคลาดเคลื่อน (SD) ${"%.0f".format(stats.sdMs)} ms · เฉลี่ย ${"%+.0f".format(stats.meanMs)} ms" +
                (if (latency == null) tr(" (ยังไม่ได้หักความหน่วง)", " (latency not subtracted yet)", "（遅延はまだ差し引いていません）") else " (หักความหน่วง ${"%.0f".format(latency)} ms แล้ว)"),
            met, passedNow, coach,
        )
    }

    // ---------------- Interval reading (1.6, 2.1, 3.1) ----------------

    private var sets: List<List<SpelledPitch>> = emptyList()
    private var setIndex = 0
    private var pairTried = false

    private fun startIntervals(d: Drill) {
        sets = com.id.mimikopi.core.exercise.ScoreGenerators.intervalSets(d, seed)
        setIndex = 0
        total = sets.size
        showPair(d, 0)
    }

    private fun showPair(d: Drill, pair: Int?) {
        val notes = sets[setIndex]
        pairTried = false
        shownAt = System.nanoTime()
        _ui.value = DrillUi.Interval(notes, com.id.mimikopi.core.exercise.ScoreGenerators.clefOf(d), pair, 0, setIndex + 1, sets.size, null, null,
            d.answer, com.id.mimikopi.core.exercise.IntervalQuiz.numbersFor(d.intervals), 0 in d.intervals)
    }

    /** The user's answer for the current pair. */
    fun answerInterval(answer: com.id.mimikopi.core.music.Motion) {
        val d = drill ?: return
        val q = _ui.value as? DrillUi.Interval ?: return
        val pair = q.pair ?: return
        if (busy || q.feedback != null) return
        val expected = com.id.mimikopi.core.music.Motion.between(q.notes[pair], q.notes[pair + 1])
        val ok = com.id.mimikopi.core.exercise.IntervalQuiz.isCorrect(expected, answer, d.answer)
        val responseMs = (System.nanoTime() - shownAt) / 1_000_000
        if (!pairTried) answers += Answer(ok, responseMs, firstTry = true)
        pairTried = true
        events += AttemptEvent(expectedPitch = q.notes[pair + 1].midi, actualPitch = null, actualOnsetMs = responseMs)
        busy = true
        _ui.value = q.copy(feedback = if (ok) Feedback.CORRECT else Feedback.WRONG, correction = if (ok) null else describeMotion(expected, d.answer))
        viewModelScope.launch {
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
        viewModelScope.launch {
            delay(500)
            busy = false
            setIndex++
            if (setIndex < sets.size) showPair(d, 0) else finishReading()
        }
    }

    fun describeMotion(m: com.id.mimikopi.core.music.Motion, mode: String): String {
        val dir = when (m.direction) {
            com.id.mimikopi.core.music.Direction.UP -> tr("ขึ้น", "Up", "上")
            com.id.mimikopi.core.music.Direction.DOWN -> tr("ลง", "Down", "下")
            com.id.mimikopi.core.music.Direction.SAME -> ""
        }
        if (m.kind == com.id.mimikopi.core.music.MotionKind.REPEAT) return tr("ซ้ำ", "Same", "同音")
        return if (mode == "INTERVAL") tr("ระยะ ${m.intervalNumber} $dir", "Interval ${m.intervalNumber} $dir", "${m.intervalNumber}度 $dir")
        else (if (m.kind == com.id.mimikopi.core.music.MotionKind.STEP) tr("ขั้น", "step", "順次") else tr("ข้าม", "leap", "跳躍")) + dir
    }

    // ---------------- Chords (stages 5-6) ----------------

    private val chordQueue = ArrayDeque<com.id.mimikopi.core.exercise.ChordItem>()
    private val chordRequeued = mutableSetOf<com.id.mimikopi.core.exercise.ChordItem>()
    private var chordItem: com.id.mimikopi.core.exercise.ChordItem? = null
    private var chordJudge: Job? = null
    /** Notes struck since the current chord was shown (keys still held from the last chord don't count). */
    private val fresh = mutableSetOf<Int>()
    private var changeItems: List<com.id.mimikopi.core.exercise.ChordItem> = emptyList()
    private var changeIndex = 0
    private var changeCount = 0
    private var struckSinceAdvance = false

    private fun startChordBuild(d: Drill) {
        chordQueue.clear(); chordRequeued.clear()
        chordQueue += com.id.mimikopi.core.exercise.ChordDrills.build(d, seed)
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

    /** One right voicing near Middle C (slash bass below it) to show after a mistake. */
    private fun voicingKeys(c: com.id.mimikopi.core.theory.Chord): Set<Int> {
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
            // Changes: judge what is held now, shortly after it settles (notes of a chord rarely land together).
            chordJudge?.cancel()
            chordJudge = viewModelScope.launch { delay(90); judgeChange() }
            return
        }
        if (e !is InputEvent.NoteOn || busy || q.feedback != null) return
        chordJudge?.cancel()
        chordJudge = viewModelScope.launch { delay(220); judgeBuild() }
    }

    private fun judgeBuild() {
        val q = _ui.value as? DrillUi.ChordAsk ?: return
        val item = chordItem ?: return
        val held = hub.pressed.value intersect fresh
        if (held.isEmpty()) return
        val ok = com.id.mimikopi.core.theory.Chord.matches(item.chord, held)
        // Fewer keys than the chord needs: still being played, wait for more.
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
        viewModelScope.launch {
            delay(if (ok) 450 else 1800)
            busy = false
            nextChord()
        }
    }

    private suspend fun runChordChange(l: Lesson?, d: Drill) {
        changeItems = com.id.mimikopi.core.exercise.ChordDrills.changes(d)
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
        if (held.isEmpty() || !com.id.mimikopi.core.theory.Chord.matches(changeItems[changeIndex].chord, held)) return
        changeCount++
        changeIndex = (changeIndex + 1) % changeItems.size
        struckSinceAdvance = false
        showChange(q.secondsLeft ?: 0)
    }

    private suspend fun finishChordChange(l: Lesson?, d: Drill, seconds: Int) {
        val summary = tr("เปลี่ยนคอร์ดถูก $changeCount ครั้งใน $seconds วินาที", "$changeCount right chord changes in $seconds s", "${seconds}秒間で正しいコードチェンジ${changeCount}回")
        if (check != null) { finishCheck(com.id.mimikopi.core.progress.CheckMetrics(count = changeCount)); return }
        logRun()
        if (l == null) {
            // Free practice: the count is the result, kept in the chord tab's list.
            saveChordRun(summary)
            _ui.value = DrillUi.Result(summary, null, false,
                if (changeCount == 0) listOf(tr("ยังไม่มีครั้งที่ถูก ลองเริ่มจากเวลานานขึ้น หรือฝึกแต่ละคอร์ดในแบบชื่อคอร์ดก่อน", "None right yet; start with more time, or practise each chord with chord names first", "まだ正解がありません。時間を長くするか、先にコード名で各コードを練習しましょう")) else emptyList())
            return
        }
        val isPassDrill = drillIndex in l.pass.requiredDrills
        val met = if (isPassDrill) Scoring.chordChangePassed(changeCount, l.pass) else null
        repo.saveAttempt(
            exerciseId = "${l.id}:${d.type}:$drillIndex",
            paramsJson = """{"seed":$seed,"drill":$drillIndex,"lesson":"${l.id}","count":$changeCount,"seconds":$seconds,"completed":true,"mode":"CHORD_CHANGE"}""",
            startedAt = startedAt, tempoBpm = null, noteAccuracy = null, timingWithinTol = null,
            clean = met, helperUses = helperUses, events = events.toList(), sessionId = services.session.currentId(), hand = d.hand,
        )
        val passedNow = repo.recordDrill(l, drillIndex, met == true, helperUses > 0)
        val coach = mutableListOf<String>()
        val need = l.pass.minCount
        if (need != null && changeCount < need) coach += tr("ต้องการอย่างน้อย $need ครั้ง ลองค้างโน้ตที่เหมือนกันระหว่างคอร์ดไว้ แล้วเลื่อนเฉพาะตัวที่ต่าง", "At least $need needed; keep shared notes held between chords and move only the others", "最低${need}回必要です。コード間の共通音は押さえたまま、違う音だけ動かしましょう")
        if (helperUses > 0 && met == true) coach += tr("ผ่านเกณฑ์ แต่ใช้ปุ่มแสดงชื่อโน้ต จึงยังไม่นับเป็นการผ่านเต็ม", "Criteria met, but the note-name button was used, so it doesn't fully count yet", "基準は満たしましたが音名ボタンを使ったためまだ完全には数えません")
        if (met == true && !passedNow && helperUses == 0) coach += if (l.pass.requiredDrills.size > 1) tr("ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านส่วนอื่นของบทในวันเดียวกัน และทำซ้ำในวันอื่น", "This part met the criteria today; the lesson's other parts must pass the same day, and again on another day", "この部分は今日基準を満たしました。同じ日にレッスンの他の部分も合格し、別の日にもくり返してください") else tr("ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่น", "Met the criteria today; pass again on another day", "今日基準を満たしました。別の日にもう一度合格を")
        if (passedNow) coach += tr("ผ่านบทนี้แล้ว บทถัดไปปลดล็อก", "Lesson passed, the next one is open", "レッスン合格、次が開きました")
        if (coach.isEmpty()) l.tips.firstOrNull()?.let { coach += it }
        _ui.value = DrillUi.Result(summary, met, passedNow, coach)
    }

    /** Counts the run's time in the practice log (Today plan and minutes per day). */
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

    /**
     * A level test ended: judged by the level's criteria only (note names shown = not counted), recorded, and told
     * plainly with the numbers reached and needed.
     */
    private fun finishCheck(m: com.id.mimikopi.core.progress.CheckMetrics) {
        val level = check ?: return
        val verdict = level.judge(m)
        val passed = verdict.passed && helperUses == 0
        val seconds = (System.currentTimeMillis() - startedAt) / 1000
        viewModelScope.launch {
            val p = services.progress
            p.addCheck(com.id.mimikopi.core.progress.CheckRecord(level.skill, level.level, java.time.LocalDate.now().toString(), passed, m, at = System.currentTimeMillis()))
            p.log("check", seconds)
            val status = com.id.mimikopi.core.progress.SkillBook.status(level.skill, p.checks.value, java.time.LocalDate.now())
            val lines = verdict.lines.toMutableList()
            if (helperUses > 0) lines += tr("เปิดชื่อโน้ตระหว่างทดสอบ ผลนี้จึงไม่นับ", "Note names were on during the test, so this result doesn't count", "テスト中に音名を表示したため、この結果は数えません")
            lines += if (passed) tr("ระดับ${level.skill.title}ตอนนี้: ${status.level} จาก ${status.maxLevel}", "${level.skill.title} now: ${status.level} of ${status.maxLevel}", "${level.skill.title}の現在：${status.maxLevel}中${status.level}")
            else if (status.lockedToday) tr("ลองระดับนี้ครบ 2 ครั้งของวันนี้แล้ว ฝึกต่อแล้วลองใหม่วันอื่น", "You've used both tries for this level today; keep practising and try another day", "このレベルは今日2回挑戦しました。練習して別の日に")
            else tr("ยังลองได้อีกครั้งวันนี้ หรือฝึกก่อนแล้วค่อยลอง", "One more try today, or practise first and then try", "今日あと1回挑戦できます。先に練習してからでも")
            _ui.value = DrillUi.Result(
                if (passed) tr("ผ่านระดับ ${level.level}: ${level.canDo}", "Passed: ${level.canDo}", "合格：${level.canDo}") else tr("ยังไม่ผ่านระดับ ${level.level}", "Not passed yet", "まだ不合格"),
                passed, false, lines,
            )
        }
    }

    /** Free chord practice: the run goes to the chord tab's list of recent results. */
    private fun saveChordRun(result: String) {
        if (!practice) return
        viewModelScope.launch {
            val dao = services.database.settingsDao()
            val runs = ChordRun.add(ChordRun.parse(dao.get(SettingKeys.CHORDS_RUNS)), ChordRun(java.time.LocalDate.now().toString(), chordPractice.summary(), result))
            dao.put(SettingEntity(SettingKeys.CHORDS_RUNS, ChordRun.encode(runs)))
        }
    }

    fun again() {
        val d = drill ?: return
        if (check != null) {
            // A test is tried at most twice a day when it fails (luck should not decide a level).
            viewModelScope.launch {
                if (com.id.mimikopi.core.progress.SkillBook.status(check.skill, services.progress.checks.value, java.time.LocalDate.now()).lockedToday)
                    _ui.value = DrillUi.Result(tr("ลองระดับนี้ครบ 2 ครั้งของวันนี้แล้ว", "You've tried this level twice today", "このレベルは今日2回挑戦しました"), null, false, listOf(tr("ฝึกเรื่องนี้ต่อในวันนี้ แล้วลองใหม่วันอื่น", "Keep practising this today and try again another day", "今日はこれを練習して、別の日にもう一度")))
                else _ui.value = DrillUi.Ready(d.title, d.type, d.items, null)
            }
            return
        }
        _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
    }

    fun noteLabel(p: SpelledPitch): String = p.name(nameStyle, withOctave = true)

    override fun onCleared() = onStop()
}
