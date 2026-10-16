package com.id.mimikopi.desktop.port.practice

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/practice/DrillViewModel.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.core.exercise.Answer
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
import com.id.mimikopi.desktop.port.SettingEntity
import com.id.mimikopi.desktop.port.SettingKeys
import com.id.mimikopi.desktop.port.OutputRoute
import com.id.mimikopi.desktop.port.AttemptEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * Nav args: "lessonId" + "drill" (index), or "review" = true for the due-items review.
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
                drill = if (bench == "pulse") Drill(ExerciseType.E_PULSE, "วัดผล: จังหวะคงที่", items = 1,
                    bpmMin = com.id.mimikopi.core.progress.Benchmarks.PULSE_BPM, bpmMax = com.id.mimikopi.core.progress.Benchmarks.PULSE_BPM,
                    beats = com.id.mimikopi.core.progress.Benchmarks.PULSE_BEATS)
                else Drill(ExerciseType.E_NOTEID, "วัดผล: ความเร็วอ่านโน้ต", items = com.id.mimikopi.core.progress.Benchmarks.NOTE_ITEMS, pool = listOf("C4:treble"))
                keyRange = if (bench == "pulse") 57..64 else 48..84
                val d = drill!!
                _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
            } else if (review) {
                val due = repo.readingDueItems()
                drill = Drill(ExerciseType.E_NOTEID, "ทบทวน", items = due.size.coerceAtLeast(1))
                _ui.value = if (due.isEmpty()) DrillUi.Result("ไม่มีรายการที่ต้องทวนตอนนี้ 👍", null, false, listOf("รายการจะกลับมาตามกำหนดของระบบทบทวน")) else DrillUi.Ready("ทบทวน", ExerciseType.E_NOTEID, due.size, null)
                keyRange = 45..76
            } else {
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
        viewModelScope.launch { hub.events.collect(::onInput) }
    }

    private suspend fun settingsName() {
        services.database.settingsDao().get(SettingKeys.NOTE_NAME_STYLE)?.let { runCatching { nameStyle = NoteNameStyle.valueOf(it) } }
    }

    fun onStart() {
        if (holding) return
        viewModelScope.launch { holding = withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    fun onStop() {
        if (!holding) return
        holding = false
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        hub.releaseAll()
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
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

    // ---------------- Reading / key drills ----------------

    private val queue = ArrayDeque<Prompt>()
    private val requeued = mutableSetOf<Prompt>()
    private val answers = mutableListOf<Answer>()
    private val events = mutableListOf<AttemptEvent>()
    private val misses = mutableMapOf<String, Int>()
    private var current: Prompt? = null
    private var currentFirstTry = true
    private var shownAt = 0L
    private var startedAt = 0L
    private var total = 0
    private var seed = 0L
    private var busy = false

    fun start() {
        val d = drill ?: return
        startedAt = System.currentTimeMillis()
        seed = startedAt
        answers.clear(); events.clear(); misses.clear(); queue.clear(); requeued.clear()
        helperUses = if (_showNames.value) 1 else 0
        job?.cancel()
        job = viewModelScope.launch {
            if (bench == "noteid") {
                queue += com.id.mimikopi.core.progress.Benchmarks.noteIdItems().map { Prompt.Note(it) }
                total = queue.size
                next()
                return@launch
            }
            if (bench == "pulse") { runPulse(null, d); return@launch }
            if (review) {
                val due = repo.readingDueItems()
                queue += due.map { id ->
                    if (id.startsWith("key:")) Prompt.Key(KeyFindItem(Letter.valueOf(id.removePrefix("key:")), null))
                    else Prompt.Note(NoteIdItem.parse(id))
                }
                total = queue.size
                if (total == 0) return@launch
                next()
                return@launch
            }
            val l = lesson ?: return@launch
            repo.enrol(l.srsItems)
            when (d.type) {
                ExerciseType.E_KEYFIND -> queue += Generators.keyFind(d, seed).map { Prompt.Key(it) }
                ExerciseType.E_NOTEID -> queue += Generators.noteId(d, seed, repo.dueItems("note:")).map { Prompt.Note(it) }
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
        val p = queue.removeFirstOrNull()
        if (p == null) {
            finishReading()
            return
        }
        current = p
        currentFirstTry = p !in requeued
        shownAt = System.nanoTime()
        _ui.value = DrillUi.Question(p, (answers.size + 1).coerceAtMost(total), total, null)
    }

    private fun onInput(e: InputEvent) {
        if (_ui.value is DrillUi.ChordAsk) { onChordInput(e); return }
        if (e !is InputEvent.NoteOn) return
        (_ui.value as? DrillUi.Interval)?.let { onIntervalPlay(it, e.note); return }
        val q = _ui.value as? DrillUi.Question ?: return
        if (busy || q.feedback != null) return
        val p = current ?: return
        val responseMs = ((e.timeNanos - shownAt) / 1_000_000).coerceAtLeast(0)
        val ok = p.accepts(e.note)
        val expected = (p as? Prompt.Note)?.item?.pitch?.midi ?: (p as? Prompt.Key)?.item?.target?.midi
        events += AttemptEvent(expectedPitch = expected, actualPitch = e.note, actualOnsetMs = responseMs, velocity = e.velocity)
        if (currentFirstTry) {
            answers += Answer(ok, responseMs, firstTry = true)
            if (bench == null) viewModelScope.launch { repo.review(p.srsId, ok, responseMs) }
        }
        if (!ok) {
            misses[p.srsId] = (misses[p.srsId] ?: 0) + 1
            // learning-system.md section 10: show the right place, ask again at the end of the round (once).
            if (p !in requeued) {
                requeued += p
                queue.addLast(p)
            }
        }
        busy = true
        _ui.value = q.copy(feedback = if (ok) Feedback.CORRECT else Feedback.WRONG)
        viewModelScope.launch {
            delay(if (ok) 450 else 1400)
            busy = false
            next()
        }
    }

    private fun finishReading() {
        val d = drill ?: return
        if (bench == "noteid") { finishNoteBenchmark(); return }
        val l = lesson
        val acc = Scoring.accuracy(answers)
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
                exerciseId = if (review) "REVIEW" else "${l?.id}:${d.type}:$drillIndex",
                paramsJson = """{"seed":$seed,"drill":$drillIndex,"lesson":"${l?.id ?: "review"}"}""",
                startedAt = startedAt, tempoBpm = null, noteAccuracy = acc, timingWithinTol = null,
                clean = met, helperUses = helperUses, events = events.toList(), sessionId = services.session.currentId(),
            )
            val passedNow = if (l != null) repo.recordDrill(l, drillIndex, met == true, helperUses > 0) else false
            val coach = mutableListOf<String>()
            misses.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.let { (id, _) ->
                coach += "${describe(id)} พลาดบ่อย รอบหน้าระบบทบทวนจะถามรายการนี้บ่อยขึ้น"
            }
            if (slow > 0) coach += "ถูก แต่มี $slow ข้อที่ใช้เวลาเกิน 3 วินาที ลองมองหากลุ่มคีย์ดำ (หรือจุดอ้างอิง) ก่อน แล้วค่อยหา"
            if (helperUses > 0 && met == true) coach += "ผ่านเกณฑ์ แต่ใช้ปุ่มแสดงชื่อโน้ต จึงยังไม่นับเป็นการผ่านเต็ม ลองอีกครั้งโดยไม่ใช้ตัวช่วย"
            if (met == true && !passedNow && helperUses == 0) coach += if (l != null && l.pass.requiredDrills.size > 1) "ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านส่วนอื่นของบทในวันเดียวกัน และทำซ้ำในวันอื่น" else "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อให้ผ่านบทนี้ (ช่วยให้จำได้นาน)"
            if (passedNow) coach += "ผ่านบทนี้แล้ว บทถัดไปปลดล็อก"
            val pct = (acc * 100).toInt()
            _ui.value = DrillUi.Result("ถูกตั้งแต่ครั้งแรก $pct% (${answers.count { it.correct }}/${answers.size})", met, passedNow, coach)
        }
    }

    private fun describe(srsId: String): String = when {
        srsId.startsWith("chord:") -> "คอร์ด ${srsId.removePrefix("chord:")}"
        srsId.startsWith("key:") -> "คีย์ ${Letter.valueOf(srsId.removePrefix("key:")).let { "${it.name} (${it.thaiSolfege})" }}"
        else -> NoteIdItem.parse(srsId).let { "โน้ต ${it.pitch.name(NoteNameStyle.BOTH, withOctave = true)}" }
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
                listOf(if (enough) "บันทึกผลวัดแล้ว" else "กดไม่ครบพอจะวัดผล ลองใหม่อีกครั้ง") +
                    if (latency == null) listOf("ยังไม่ได้วัดความหน่วงของเครื่อง ค่าเฉลี่ยอาจเลื่อน แต่ค่า SD ไม่ขึ้นกับความหน่วงคงที่") else emptyList(),
            )
            return
        }
        val isPassDrill = drillIndex in l.pass.requiredDrills
        val met = if (isPassDrill) Scoring.pulsePassed(stats, l.pass, d.beats) else null

        repo.saveAttempt(
            exerciseId = "${l.id}:${d.type}:$drillIndex",
            paramsJson = """{"seed":$seed,"bpm":$bpm,"beats":${d.beats},"latencyMs":${latency ?: 0.0}}""",
            startedAt = startedAt, tempoBpm = bpm.toDouble(), noteAccuracy = null, timingWithinTol = stats.within,
            clean = met, helperUses = 0,
            events = offsets.map { AttemptEvent(expectedPitch = null, actualPitch = null, offsetMs = it.toLong()) },
            sessionId = services.session.currentId(),
        )
        val passedNow = repo.recordDrill(l, drillIndex, met == true, helperUsed = false)
        val coach = mutableListOf<String>()
        val missed = d.beats - stats.taps
        if (missed > d.beats / 4) coach += "กดไม่ครบ ($missed จังหวะหายไป) ลองนับออกเสียงตลอด แล้วกดทุกครั้งที่นับ"
        if (abs(stats.meanMs) > 40) {
            val dir = if (stats.meanMs > 0) "ช้ากว่า" else "เร็วกว่า"
            coach += if (latency == null) "ส่วนใหญ่กด${dir}คลิกเท่าๆ กัน (${"%.0f".format(abs(stats.meanMs))} ms) อาจเป็นความหน่วงของเครื่อง ลองวัดความหน่วงที่หน้า \"ต่อเปียโน (MIDI)\" ก่อน"
            else "ส่วนใหญ่กด${dir}คลิก ลองนับออกเสียงหรือเคาะเท้าไปด้วย"
        }
        if (met == true && !passedNow) coach += "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อให้ผ่านบทนี้"
        if (passedNow) coach += "ผ่านบทนี้แล้ว"
        if (coach.isEmpty()) coach += l.tips.first()
        _ui.value = DrillUi.Result(
            "$bpm BPM · กด ${stats.taps}/${d.beats} · ความคลาดเคลื่อน (SD) ${"%.0f".format(stats.sdMs)} ms · เฉลี่ย ${"%+.0f".format(stats.meanMs)} ms" +
                (if (latency == null) " (ยังไม่ได้หักความหน่วง)" else " (หักความหน่วง ${"%.0f".format(latency)} ms แล้ว)"),
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
            com.id.mimikopi.core.music.Direction.UP -> "ขึ้น"
            com.id.mimikopi.core.music.Direction.DOWN -> "ลง"
            com.id.mimikopi.core.music.Direction.SAME -> ""
        }
        if (m.kind == com.id.mimikopi.core.music.MotionKind.REPEAT) return "ซ้ำ"
        return if (mode == "INTERVAL") "ระยะ ${m.intervalNumber} $dir"
        else (if (m.kind == com.id.mimikopi.core.music.MotionKind.STEP) "ขั้น" else "ข้าม") + dir
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

    private suspend fun runChordChange(l: Lesson, d: Drill) {
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

    private suspend fun finishChordChange(l: Lesson, d: Drill, seconds: Int) {
        val isPassDrill = drillIndex in l.pass.requiredDrills
        val met = if (isPassDrill) Scoring.chordChangePassed(changeCount, l.pass) else null
        repo.saveAttempt(
            exerciseId = "${l.id}:${d.type}:$drillIndex",
            paramsJson = """{"seed":$seed,"drill":$drillIndex,"lesson":"${l.id}","count":$changeCount,"seconds":$seconds}""",
            startedAt = startedAt, tempoBpm = null, noteAccuracy = null, timingWithinTol = null,
            clean = met, helperUses = helperUses, events = events.toList(), sessionId = services.session.currentId(),
        )
        val passedNow = repo.recordDrill(l, drillIndex, met == true, helperUses > 0)
        val coach = mutableListOf<String>()
        val need = l.pass.minCount
        if (need != null && changeCount < need) coach += "ต้องการอย่างน้อย $need ครั้ง ลองค้างโน้ตที่เหมือนกันระหว่างคอร์ดไว้ แล้วเลื่อนเฉพาะตัวที่ต่าง"
        if (helperUses > 0 && met == true) coach += "ผ่านเกณฑ์ แต่ใช้ปุ่มแสดงชื่อโน้ต จึงยังไม่นับเป็นการผ่านเต็ม"
        if (met == true && !passedNow && helperUses == 0) coach += if (l.pass.requiredDrills.size > 1) "ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านส่วนอื่นของบทในวันเดียวกัน และทำซ้ำในวันอื่น" else "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่น"
        if (passedNow) coach += "ผ่านบทนี้แล้ว บทถัดไปปลดล็อก"
        if (coach.isEmpty()) l.tips.firstOrNull()?.let { coach += it }
        _ui.value = DrillUi.Result("เปลี่ยนคอร์ดถูก $changeCount ครั้งใน $seconds วินาที", met, passedNow, coach)
    }

    fun again() {
        val d = drill ?: return
        _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
    }

    fun noteLabel(p: SpelledPitch): String = p.name(nameStyle, withOctave = true)

    override fun onCleared() = onStop()
}
