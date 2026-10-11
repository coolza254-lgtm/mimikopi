package com.id.mimikopi.ui.practice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
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
import com.id.mimikopi.core.practice.TimingRules
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.input.OutputRoute
import com.id.mimikopi.learning.AttemptEvent
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
            if (review) {
                val due = repo.dueItems()
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
            if (review) {
                val due = repo.dueItems()
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
        if (e !is InputEvent.NoteOn) return
        val q = _ui.value as? DrillUi.Question ?: return
        if (busy || q.feedback != null) return
        val p = current ?: return
        val responseMs = ((e.timeNanos - shownAt) / 1_000_000).coerceAtLeast(0)
        val ok = p.accepts(e.note)
        val expected = (p as? Prompt.Note)?.item?.pitch?.midi ?: (p as? Prompt.Key)?.item?.target?.midi
        events += AttemptEvent(expectedPitch = expected, actualPitch = e.note, actualOnsetMs = responseMs, velocity = e.velocity)
        if (currentFirstTry) {
            answers += Answer(ok, responseMs, firstTry = true)
            viewModelScope.launch { repo.review(p.srsId, ok, responseMs) }
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
        val l = lesson
        val acc = Scoring.accuracy(answers)
        val slow = answers.count { it.correct && it.responseMs > 3000 }
        val isPassDrill = l != null && l.pass.drill == drillIndex
        val met: Boolean? = if (!isPassDrill || l == null) null else when (d.type) {
            ExerciseType.E_KEYFIND -> Scoring.keyFindPassed(answers, l.pass, d.items)
            ExerciseType.E_NOTEID -> Scoring.noteIdPassed(answers, l.pass, d.items)
            ExerciseType.E_PULSE -> null
        }
        viewModelScope.launch {
            repo.saveAttempt(
                exerciseId = if (review) "REVIEW" else "${l?.id}:${d.type}:$drillIndex",
                paramsJson = """{"seed":$seed,"drill":$drillIndex,"lesson":"${l?.id ?: "review"}"}""",
                startedAt = startedAt, tempoBpm = null, noteAccuracy = acc, timingWithinTol = null,
                clean = met, helperUses = helperUses, events = events.toList(), sessionId = services.session.currentId(),
            )
            val passedNow = if (l != null) repo.recordDrill(l, met == true, helperUses > 0) else false
            val coach = mutableListOf<String>()
            misses.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.let { (id, _) ->
                coach += "${describe(id)} พลาดบ่อย รอบหน้าระบบทบทวนจะถามรายการนี้บ่อยขึ้น"
            }
            if (slow > 0) coach += "ถูก แต่มี $slow ข้อที่ใช้เวลาเกิน 3 วินาที ลองมองหากลุ่มคีย์ดำ (หรือจุดอ้างอิง) ก่อน แล้วค่อยหา"
            if (helperUses > 0 && met == true) coach += "ผ่านเกณฑ์ แต่ใช้ปุ่มแสดงชื่อโน้ต จึงยังไม่นับเป็นการผ่านเต็ม ลองอีกครั้งโดยไม่ใช้ตัวช่วย"
            if (met == true && !passedNow && helperUses == 0) coach += "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อให้ผ่านบทนี้ (ช่วยให้จำได้นาน)"
            if (passedNow) coach += "ผ่านบทนี้แล้ว บทถัดไปปลดล็อก"
            if (coach.isEmpty()) l?.tips?.firstOrNull()?.let { coach += it }
            val pct = (acc * 100).toInt()
            _ui.value = DrillUi.Result("ถูกตั้งแต่ครั้งแรก $pct% (${answers.count { it.correct }}/${answers.size})", met, passedNow, coach)
        }
    }

    private fun describe(srsId: String): String = when {
        srsId.startsWith("key:") -> "คีย์ ${Letter.valueOf(srsId.removePrefix("key:")).let { "${it.name} (${it.thaiSolfege})" }}"
        else -> NoteIdItem.parse(srsId).let { "โน้ต ${it.pitch.name(NoteNameStyle.BOTH, withOctave = true)}" }
    }

    // ---------------- Steady pulse (lesson 1.3) ----------------

    private val taps = mutableListOf<InputEvent.NoteOn>()

    private suspend fun runPulse(l: Lesson, d: Drill) {
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
        val tolerance = TimingRules(repo.config.timing).toleranceMs(l.stage, bpm.toDouble())
        val stats = Scoring.pulse(offsets, tolerance)
        val isPassDrill = l.pass.drill == drillIndex
        val met = if (isPassDrill) Scoring.pulsePassed(stats, l.pass, d.beats) else null

        repo.saveAttempt(
            exerciseId = "${l.id}:${d.type}:$drillIndex",
            paramsJson = """{"seed":$seed,"bpm":$bpm,"beats":${d.beats},"latencyMs":${latency ?: 0.0}}""",
            startedAt = startedAt, tempoBpm = bpm.toDouble(), noteAccuracy = null, timingWithinTol = stats.within,
            clean = met, helperUses = 0,
            events = offsets.map { AttemptEvent(expectedPitch = null, actualPitch = null, offsetMs = it.toLong()) },
            sessionId = services.session.currentId(),
        )
        val passedNow = repo.recordDrill(l, met == true, helperUsed = false)
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

    fun again() {
        val d = drill ?: return
        _ui.value = DrillUi.Ready(d.title, d.type, d.items, if (d.type == ExerciseType.E_PULSE) d.bpmMin else null)
    }

    fun noteLabel(p: SpelledPitch): String = p.name(nameStyle, withOctave = true)

    override fun onCleared() = onStop()
}
