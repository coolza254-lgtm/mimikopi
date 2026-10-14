package com.id.mimikopi.ui.ear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.ear.Compare
import com.id.mimikopi.core.ear.Degree
import com.id.mimikopi.core.ear.Dir
import com.id.mimikopi.core.ear.EarAnswers
import com.id.mimikopi.core.ear.EarGenerator
import com.id.mimikopi.core.ear.EarKey
import com.id.mimikopi.core.ear.EarLevel
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.ear.EarStats
import com.id.mimikopi.core.ear.IntervalPlay
import com.id.mimikopi.core.ear.SeventhQuality
import com.id.mimikopi.core.ear.TriadQuality
import com.id.mimikopi.core.ear.earNoteName
import com.id.mimikopi.core.ear.scaleNote
import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.playalong.PlayAlongJudge
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.playalong.PlaySetup
import com.id.mimikopi.core.practice.isClean
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.input.OutputRoute
import com.id.mimikopi.piano.NoteScheduler
import com.id.mimikopi.piano.countInDownbeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Choice(val label: String, val value: String)

sealed interface EarUi {
    data object Loading : EarUi
    data class Ready(val title: String, val count: Int, val levels: List<EarLevel>, val packMissing: Boolean) : EarUi
    data class Asking(
        val q: EarQuestion,
        val index: Int,
        val total: Int,
        /** What to answer now. */
        val prompt: String,
        val choices: List<Choice>,
        /** True when keys (on screen or MIDI) answer this step. */
        val keysAnswer: Boolean,
        /** Keys to point out (the given first note, or the answer after a mistake). */
        val marked: Set<Int>,
        /** Notes entered so far in a played answer. */
        val entered: List<Int>,
        /** null = waiting for the answer; true / false = answered. */
        val correct: Boolean?,
        val correction: String?,
        /** Rhythm echo: count-in number, or "listen" / "your turn" state. */
        val rhythmState: String? = null,
    ) : EarUi
    data class Done(val headline: String, val lines: List<String>) : EarUi
}

/**
 * Runs a set of ear-training questions (learning-system.md 11): a mixed set over the unlocked levels (weighted toward
 * weak items), one level, or the fixed benchmark set. Answers come from buttons or from keys (on-screen or MIDI);
 * the keyboard always stays playable so the sound can be found on the piano (11.2).
 * Nav args: "level" = E1..E9 or "mix"; "bench" = "true" for the benchmark.
 */
class EarDrillViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val learning = services.learning
    private val hub = services.inputHub
    private val piano = services.piano
    private val scheduler = NoteScheduler(piano)

    /** A lesson's E_EAR drill ("lessonId" + "drill"): its level and item count, and the result counts for the lesson. */
    private val lesson: com.id.mimikopi.core.lesson.Lesson? = args.get<String>("lessonId")?.let { learning.lesson(it) }
    private val drillIndex: Int = args.get<String>("drill")?.toIntOrNull() ?: 0
    private val lessonDrill = lesson?.drills?.getOrNull(drillIndex)?.takeIf { it.type == com.id.mimikopi.core.lesson.ExerciseType.E_EAR }
    private val levelArg: String = lessonDrill?.level ?: args.get<String>("level") ?: "mix"
    private val count: Int get() = if (bench) 20 else lessonDrill?.items ?: 12
    private val bench: Boolean = args.get<String>("bench") == "true"

    private val _ui = MutableStateFlow<EarUi>(EarUi.Loading)
    val ui: StateFlow<EarUi> = _ui.asStateFlow()
    val pressed = hub.pressed
    var nameStyle: NoteNameStyle = NoteNameStyle.BOTH
        private set

    private var questions: List<EarQuestion> = emptyList()
    private var index = 0
    private var step = 0
    private var firstTry = true
    private var stepsAllRight = true
    private val itemResults = mutableListOf<Pair<String, Boolean>>()
    private var holding = false
    private var job: Job? = null
    private var startedAt = 0L

    init {
        viewModelScope.launch {
            learning.load()
            services.database.settingsDao().get(com.id.mimikopi.data.db.SettingKeys.NOTE_NAME_STYLE)?.let { runCatching { nameStyle = NoteNameStyle.valueOf(it) } }
            val missing = withContext(Dispatchers.IO) { services.samplePacks.installed() == null }
            val levels = levels()
            _ui.value = EarUi.Ready(title(), count, levels, missing)
        }
        viewModelScope.launch { hub.events.collect(::onInput) }
    }

    private fun title(): String = when {
        bench -> "วัดผล: หู (ชุดมาตรฐาน)"
        lessonDrill != null -> "${lesson!!.id} · ${lessonDrill.title}"
        levelArg == "mix" -> "ฝึกหูแบบผสม"
        else -> "ฝึกหู ${levelArg}"
    }

    /** Stage reached = the current lesson's stage (or the highest stage when every lesson is passed). */
    private fun stage(): Int {
        val records = learning.records.value
        return ProgressRules.current(learning.lessons, records)?.stage ?: learning.lessons.maxOfOrNull { it.stage } ?: 1
    }

    private fun levels(): List<EarLevel> = when {
        bench -> listOf(EarLevel.E1, EarLevel.E2, EarLevel.E3, EarLevel.E4)
        levelArg == "mix" -> EarStats.unlocked(stage())
        else -> listOf(EarLevel.valueOf(levelArg))
    }

    fun onStart() {
        if (holding) return
        holding = true
        viewModelScope.launch { withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    fun onStop() {
        job?.cancel()
        scheduler.clear()
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        if (!holding) return
        holding = false
        hub.releaseAll()
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    fun start() {
        viewModelScope.launch {
            val stats = learning.earStats()
            val weights = EarStats.weights(stats)
            val seed = System.currentTimeMillis()
            questions = when {
                bench -> EarStats.benchmarkSet()
                // An ear-ladder rung: every question from its one narrow step.
                lessonDrill?.ear != null -> {
                    val spec = lessonDrill!!.ear!!
                    val g = EarGenerator(seed, weights)
                    List(count) { g.next(EarLevel.valueOf(levelArg), spec) }
                }
                else -> EarGenerator(seed, weights).mixed(levels(), count,
                    EarStats.levelWeights(stats) { id -> EarStats.levelsOf(id).firstOrNull { it in levels() } })
            }
            index = 0
            itemResults.clear()
            startedAt = System.currentTimeMillis()
            show()
        }
    }

    // ---------------- question flow ----------------

    private fun current() = questions[index]

    private fun show() {
        step = 0
        mixProgOk = true
        firstTry = true
        stepsAllRight = true
        _ui.value = asking(current())
        if (current() !is EarQuestion.Rhythm) play()
    }

    private fun asking(q: EarQuestion, entered: List<Int> = emptyList(), correct: Boolean? = null, correction: String? = null, marked: Set<Int>? = null): EarUi.Asking {
        val (prompt, choices, keys) = stepSpec(q)
        val given = when (q) {
            is EarQuestion.Interval -> setOf(q.root)
            is EarQuestion.Melody -> setOf(q.notes.first())
            is EarQuestion.Mixed -> if (step >= 4) setOf(q.melody.first()) else emptySet()
            else -> emptySet()
        }
        return EarUi.Asking(q, index + 1, questions.size, prompt, choices, keys, marked ?: given, entered, correct, correction)
    }

    /** Prompt, buttons and whether keys answer, for the current question and step. */
    private fun stepSpec(q: EarQuestion): Triple<String, List<Choice>, Boolean> = when (q) {
        is EarQuestion.Higher -> Triple("เสียงที่สองสูงขึ้น ต่ำลง หรือเท่ากัน?",
            listOf(Choice("สูงขึ้น", "HIGHER"), Choice("ต่ำลง", "LOWER"), Choice("เท่ากัน", "SAME"))
                .filter { it.value != "SAME" || lessonDrill?.ear?.same != false }, false)
        is EarQuestion.Direction -> Triple("โน้ตตัวที่ ${step + 1} → ${step + 2} ไปทางไหน?",
            listOf(Choice("ขึ้น", "UP"), Choice("ลง", "DOWN"), Choice("ซ้ำ", "SAME")), false)
        is EarQuestion.Interval -> Triple(
            when (q.play) {
                IntervalPlay.TOGETHER -> "สองเสียงพร้อมกัน ห่างกันเท่าไร? (หรือกดเสียงบนบนคีย์บอร์ด)"
                IntervalPlay.DOWN -> "เสียงที่สองต่ำลงเท่าไร? (หรือกดเสียงที่สองบนคีย์บอร์ด)"
                IntervalPlay.UP -> "เสียงที่สองสูงขึ้นเท่าไร? (หรือกดเสียงที่สองบนคีย์บอร์ด)"
            },
            (lessonDrill?.ear?.intervals?.takeIf { it.isNotEmpty() }?.sorted() ?: if (q.level == EarLevel.E2) (1..7).toList() else (1..12).toList()).map { Choice(EarAnswers.INTERVAL_NAMES.getValue(it), "$it") }, true)
        is EarQuestion.Triad -> Triple("คอร์ดนี้เมเจอร์ (สว่าง) หรือไมเนอร์ (หม่น)?",
            listOf(Choice("เมเจอร์", "MAJOR"), Choice("ไมเนอร์", "MINOR")), false)
        is EarQuestion.Melody -> Triple("โน้ตแรกคือคีย์ที่มีจุด เล่นทำนองที่เหลืออีก ${q.notes.size - 1} ตัวบนคีย์บอร์ด", emptyList(), true)
        is EarQuestion.Rhythm -> Triple("ฟังจังหวะ ${q.bars} บาร์ แล้วเคาะตามในบาร์ถัดไป (กดคีย์ใดก็ได้) พร้อมเสียงคลิก", emptyList(), false)
        is EarQuestion.Progression -> Triple(
            "คอร์ดแรกคือ ${degreeLabel(q.key, q.degrees[0])} · คอร์ดที่ ${step + 2} คืออะไร?",
            progressionChoices(q.degrees).map { Choice(degreeLabel(q.key, it), it.name) }, false)
        is EarQuestion.Seventh -> Triple("คอร์ดเซเวนท์ชนิดไหน?",
            listOf(Choice("เมเจอร์ 7 (maj7)", "MAJ7"), Choice("ไมเนอร์ 7 (m7)", "MIN7"), Choice("โดมิแนนท์ 7 (7)", "DOM7")), false)
        is EarQuestion.BassLine -> Triple("เบสของคอร์ดที่ ${step + 1} คือขั้นที่เท่าไรของคีย์ ${q.key.label}? (หรือกดเสียงเบสบนคีย์บอร์ด)",
            (1..7).map { Choice("$it", "$it") }, true)
        is EarQuestion.Mixed -> when (step) {
            0 -> Triple("ท่อนนี้อยู่คีย์อะไร? กดโน้ตหลักของคีย์ (โด) ที่อ็อกเทฟไหนก็ได้", emptyList(), true)
            in 1..3 -> Triple("คอร์ดแรกคือ ${degreeLabel(q.key, q.degrees[0])} · คอร์ดที่ ${step + 1} คืออะไร?",
                progressionChoices(q.degrees).map { Choice(degreeLabel(q.key, it), it.name) }, false)
            else -> Triple("เล่นทำนอง: โน้ตแรกคือคีย์ที่มีจุด เล่นต่ออีก ${q.melody.size - 1} ตัว", emptyList(), true)
        }
    }

    /** I IV V vi first; ii and iii join the buttons once questions use them (11.1 E7). */
    private fun progressionChoices(degrees: List<Degree>): List<Degree> {
        val basic = listOf(Degree.I, Degree.IV, Degree.V, Degree.vi)
        return if (degrees.all { it in basic }) basic else Degree.entries
    }

    private fun degreeLabel(key: EarKey, d: Degree): String {
        val root = earNoteName(scaleNote(key.tonic, d.step), key)
        val name = root.letter.name + com.id.mimikopi.core.music.SpelledPitch.accidentalSymbol(root.accidental) + if (d.minor) "m" else ""
        return "${d.name} ($name)"
    }

    /** Plays the question (or, after answering, the same sounds again as the answer). */
    fun play() {
        val q = (current())
        if (q is EarQuestion.Rhythm) return
        scheduler.clear()
        val t0 = System.nanoTime() + 150_000_000L
        for (s in q.sounds()) {
            scheduler.noteOn(t0 + s.atMs * 1_000_000, s.midi, s.velocity)
            scheduler.noteOff(t0 + (s.atMs + s.durMs) * 1_000_000, s.midi)
        }
    }

    fun answer(value: String) {
        val ui = _ui.value as? EarUi.Asking ?: return
        if (ui.correct != null) return
        val q = ui.q
        when (q) {
            is EarQuestion.Higher -> finishQuestion(Compare.valueOf(value) == q.answer, "คำตอบ: ${compareLabel(q.answer)}")
            is EarQuestion.Direction -> stepAnswer(Dir.valueOf(value) == q.answers[step], q.answers.size, "ตัวที่ ${step + 1} → ${step + 2}: ${dirLabel(q.answers[step])}")
            is EarQuestion.Interval -> finishQuestion(value.toInt() == q.semitones, "คำตอบ: ${EarAnswers.INTERVAL_NAMES.getValue(q.semitones)}", setOf(q.root, q.target))
            is EarQuestion.Triad -> finishQuestion(TriadQuality.valueOf(value) == q.quality, "คำตอบ: ${if (q.quality == TriadQuality.MAJOR) "เมเจอร์" else "ไมเนอร์"}", q.notes.toSet())
            is EarQuestion.Progression -> {
                val d = q.degrees[step + 1]
                itemResults += "ear:deg:${d.name}" to (Degree.valueOf(value) == d)
                stepAnswer(Degree.valueOf(value) == d, 3, "คอร์ดที่ ${step + 2}: ${degreeLabel(q.key, d)}", recordItems = false)
            }
            is EarQuestion.Seventh -> finishQuestion(SeventhQuality.valueOf(value) == q.quality, "คำตอบ: ${seventhLabel(q.quality)}", q.notes.toSet())
            is EarQuestion.BassLine -> stepAnswer(value.toInt() == q.bassDegrees[step], 4, "เบสคอร์ดที่ ${step + 1}: ขั้นที่ ${q.bassDegrees[step]}")
            is EarQuestion.Mixed -> if (step in 1..3) {
                val d = q.degrees[step]
                mixedStep(Degree.valueOf(value) == d, "คอร์ดที่ ${step + 1}: ${degreeLabel(q.key, d)}")
            }
            else -> Unit
        }
    }

    private fun onInput(e: InputEvent) {
        if (e !is InputEvent.NoteOn) return
        val ui = _ui.value as? EarUi.Asking ?: return
        if (!ui.keysAnswer || ui.correct != null) return
        val q = ui.q
        when (q) {
            is EarQuestion.Interval -> if (e.note != q.root) finishQuestion(e.note == q.target, "คำตอบ: ${EarAnswers.INTERVAL_NAMES.getValue(q.semitones)} (คีย์ที่มีจุด)", setOf(q.root, q.target))
            is EarQuestion.Melody -> {
                val entered = ui.entered + e.note
                if (entered.size < q.notes.size - 1) _ui.value = ui.copy(entered = entered)
                else finishQuestion(EarAnswers.melodyMatches(q.notes, entered), "ทำนองที่ถูกอยู่บนคีย์ที่มีจุด", q.notes.toSet(), entered)
            }
            is EarQuestion.BassLine -> {
                val expected = Math.floorMod(scaleNote(q.key.tonic, q.chords[step].first.step + q.chords[step].second), 12)
                stepAnswer(Math.floorMod(e.note, 12) == expected, 4, "เบสคอร์ดที่ ${step + 1}: ขั้นที่ ${q.bassDegrees[step]}")
            }
            is EarQuestion.Mixed -> when {
                step == 0 -> mixedStep(EarAnswers.tonicMatches(q.key, e.note), "คีย์ ${q.key.label}")
                step >= 4 -> {
                    val entered = ui.entered + e.note
                    if (entered.size < q.melody.size - 1) _ui.value = ui.copy(entered = entered)
                    else {
                        val ok = EarAnswers.melodyMatches(q.melody, entered)
                        itemResults += "ear:mix:mel" to ok
                        record("ear:mix:mel", ok)
                        _ui.value = ui.copy(entered = entered, correct = ok && stepsAllRight, marked = q.melody.toSet(),
                            correction = if (ok) null else "ทำนองที่ถูกอยู่บนคีย์ที่มีจุด")
                    }
                }
            }
            else -> Unit
        }
    }

    /** One part of a multi-part answer; the question is right only if every part was. */
    private fun stepAnswer(ok: Boolean, steps: Int, correction: String, recordItems: Boolean = true) {
        val ui = _ui.value as? EarUi.Asking ?: return
        if (!ok) stepsAllRight = false
        if (step + 1 < steps) {
            step++
            _ui.value = asking(ui.q).copy(correction = if (ok) null else "ไม่ใช่ · $correction")
            return
        }
        if (recordItems) finishQuestion(stepsAllRight, if (ok) null else correction)
        else {
            ui.q.itemIds.forEach { id -> itemResults.filter { it.first == id }.lastOrNull()?.let { record(id, it.second) } }
            _ui.value = ui.copy(correct = stepsAllRight, correction = if (ok) progressionReveal(ui.q) else "$correction${progressionReveal(ui.q)?.let { " · $it" } ?: ""}")
        }
    }

    private fun progressionReveal(q: EarQuestion): String? = (q as? EarQuestion.Progression)?.name?.let { "โปรเกรสชันนี้คือ $it" }

    private var mixProgOk = true

    private fun mixedStep(ok: Boolean, correction: String) {
        val ui = _ui.value as? EarUi.Asking ?: return
        val q = ui.q as EarQuestion.Mixed
        if (!ok) stepsAllRight = false
        if (step == 0) { itemResults += "ear:mix:key" to ok; record("ear:mix:key", ok) }
        if (step in 1..3 && !ok) mixProgOk = false
        if (step == 3) { itemResults += "ear:mix:prog" to mixProgOk; record("ear:mix:prog", mixProgOk) }
        step++
        _ui.value = asking(q).copy(correction = if (ok) null else "ไม่ใช่ · $correction")
    }

    private fun finishQuestion(ok: Boolean, correction: String?, answerKeys: Set<Int>? = null, entered: List<Int> = emptyList()) {
        val ui = _ui.value as? EarUi.Asking ?: return
        ui.q.itemIds.forEach { id -> itemResults += id to ok; record(id, ok) }
        _ui.value = ui.copy(correct = ok, correction = if (ok) null else correction, marked = answerKeys ?: ui.marked, entered = entered)
    }

    private fun record(itemId: String, ok: Boolean) {
        if (bench) return // the benchmark does not change spaced repetition
        viewModelScope.launch { learning.review(itemId, ok, 0) }
    }

    /** Melody answers can be tried again (not counted) after a mistake. */
    fun retryEntry() {
        val ui = _ui.value as? EarUi.Asking ?: return
        _ui.value = ui.copy(entered = emptyList())
    }

    fun next() {
        index++
        if (index < questions.size) show() else finish()
    }

    // ---------------- rhythm echo (E6) ----------------

    fun startRhythm() {
        val ui = _ui.value as? EarUi.Asking ?: return
        val q = ui.q as? EarQuestion.Rhythm ?: return
        if (ui.rhythmState != null || ui.correct != null) return
        job?.cancel()
        job = viewModelScope.launch {
            val score = Score.from(q.piece)
            val bpm = 72.0
            val beatNanos = (60_000_000_000.0 / bpm).toLong()
            val barNanos = beatNanos * 4
            _ui.value = ui.copy(rhythmState = "นับเข้า")
            val t0 = countInDownbeat(piano, bpm, 4, 4)
            // The app plays the bars; the player answers in the same number of bars right after.
            for (e in score.events) if (!e.isRest) {
                val on = t0 + (e.onset * beatNanos).toLong()
                scheduler.noteOn(on, 84, 90)
                scheduler.noteOff(on + (e.duration * beatNanos * 0.8).toLong(), 84)
            }
            val cfg = services.inputConfig.load()
            val route = OutputRoute.current(getApplication())
            val latency = com.id.mimikopi.core.input.InputSource.entries.associateWith { cfg.latencyMs[InputConfig.latencyKey(it, route)] ?: 0.0 }
            val judge = PlayAlongJudge(score, PlaySetup(PlayMode.TEMPO, setOf(Hand.RIGHT), 0, q.bars - 1, bpm,
                learning.toleranceMs(2, bpm, benchmark = bench), 0, anyKey = true))
            val userStart = t0 + q.bars * barNanos
            judge.start(userStart / 1_000_000)
            val collector = launch {
                hub.events.collect { ev ->
                    if (ev is InputEvent.NoteOn && System.nanoTime() > userStart - beatNanos / 2) {
                        judge.onNoteOn(ev.note, ((ev.timeNanos - (latency[ev.source] ?: 0.0) * 1e6) / 1e6).toLong())
                    }
                }
            }
            while (!judge.isFinished(System.nanoTime() / 1_000_000)) {
                val now = System.nanoTime()
                val state = when {
                    now < t0 -> "นับเข้า"
                    now < userStart -> "ฟัง…"
                    else -> "ตาคุณ: เคาะตาม"
                }
                (_ui.value as? EarUi.Asking)?.let { if (it.rhythmState != state) _ui.value = it.copy(rhythmState = state) }
                judge.advance(now / 1_000_000)
                delay(20)
            }
            collector.cancel()
            piano.setMetronome(false, bpm, 1, intArrayOf(0), 0f)
            val r = judge.result()
            val ok = r.summary?.isClean(learning.config.cleanRun) == true
            val hit = r.outcomes.values.count { it.result == NoteResult.CORRECT }
            (_ui.value as? EarUi.Asking)?.let { cur ->
                _ui.value = cur.copy(rhythmState = null)
                finishQuestion(ok, "ตรงจังหวะ $hit จาก ${r.outcomes.size} โน้ต · เร็ว ${r.count(NoteResult.EARLY)} · ช้า ${r.count(NoteResult.LATE)} · ไม่ได้เคาะ ${r.count(NoteResult.MISSED)}")
            }
        }
    }

    // ---------------- summary ----------------

    private fun finish() {
        val total = questions.size
        val right = questions.indices.count { i -> questionRight(i) }
        val acc = if (total == 0) 0.0 else right.toDouble() / total
        val byType = itemResults.groupBy { it.first }.map { (id, rs) -> "${itemLabel(id)}: ${rs.count { it.second }}/${rs.size}" }
        viewModelScope.launch {
            learning.saveAttempt(
                exerciseId = if (bench) "EAR:BENCH" else if (lessonDrill != null) "${lesson!!.id}:E_EAR:$drillIndex" else "EAR:${levelArg.uppercase()}", paramsJson = """{"questions":$total}""",
                startedAt = startedAt, tempoBpm = null, noteAccuracy = acc, timingWithinTol = null, clean = null,
                helperUses = 0, events = emptyList(), sessionId = services.session.currentId(),
            )
            if (bench) learning.updateBenchmark { it.copy(earAccuracy = acc) }
            val lines = mutableListOf<String>()
            lines += byType
            val weak = itemResults.groupBy { it.first }.filter { (_, rs) -> rs.count { !it.second } >= 2 }.keys.map { itemLabel(it) }
            if (weak.isNotEmpty()) lines += "ที่พลาดบ่อยรอบนี้: ${weak.joinToString()} รอบหน้าแอพจะถามบ่อยขึ้น"
            if (bench) lines += "บันทึกผลวัดแล้ว"
            val l = lesson
            if (l != null && lessonDrill != null && drillIndex in l.pass.requiredDrills) {
                val need = l.pass.minAccuracy ?: 0.8
                val met = acc >= need
                val passedNow = learning.recordDrill(l, drillIndex, met, helperUsed = false)
                lines.add(0, when {
                    passedNow -> "ผ่านบท ${l.id} แล้ว"
                    met && l.pass.requiredDrills.size > 1 -> "ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว (≥ ${(need * 100).toInt()}%) ต้องผ่านส่วนอื่นของบทในวันเดียวกันด้วย"
                    met -> "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่น"
                    else -> "ยังไม่ถึงเกณฑ์ ${(need * 100).toInt()}%"
                })
            } else if (l != null && lessonDrill != null) {
                learning.recordDrill(l, drillIndex, met = false, helperUsed = false)
            }
            lines += "ลองเล่นเสียงที่ยังสับสนบนเปียโนช้าๆ เทียบกัน การเชื่อมหูกับมือช่วยให้จำเสียงได้"
            _ui.value = EarUi.Done("ถูก $right จาก $total ข้อ (${(acc * 100).toInt()}%)", lines)
        }
    }

    private val questionOutcomes = mutableMapOf<Int, Boolean>()

    private fun questionRight(i: Int): Boolean = questionOutcomes[i] ?: false

    init {
        // Remember each question's overall outcome when it is answered.
        viewModelScope.launch {
            _ui.collect { u -> if (u is EarUi.Asking && u.correct != null) questionOutcomes[u.index - 1] = u.correct }
        }
    }

    private fun compareLabel(c: Compare) = when (c) { Compare.HIGHER -> "สูงขึ้น"; Compare.LOWER -> "ต่ำลง"; Compare.SAME -> "เท่ากัน" }
    private fun dirLabel(d: Dir) = when (d) { Dir.UP -> "ขึ้น"; Dir.DOWN -> "ลง"; Dir.SAME -> "ซ้ำ" }
    private fun seventhLabel(q: SeventhQuality) = when (q) { SeventhQuality.MAJ7 -> "เมเจอร์ 7"; SeventhQuality.MIN7 -> "ไมเนอร์ 7"; SeventhQuality.DOM7 -> "โดมิแนนท์ 7" }

    companion object {
        /** Thai label of an ear item id (statistics). */
        fun itemLabel(id: String): String = when {
            id == "ear:cmp" -> "สูง-ต่ำ"
            id == "ear:dir" -> "ทิศทางทำนอง"
            id.startsWith("ear:int:") -> "ระยะ ${EarAnswers.INTERVAL_NAMES[id.removePrefix("ear:int:").toIntOrNull()] ?: id}"
            id == "ear:triad:major" -> "ไทรแอดเมเจอร์"
            id == "ear:triad:minor" -> "ไทรแอดไมเนอร์"
            id == "ear:mel:step" -> "ทำนองเดินทีละขั้น"
            id == "ear:mel:skip" -> "ทำนองมีกระโดด"
            id.startsWith("ear:rhy:") -> "จังหวะ ${id.removePrefix("ear:rhy:")} บาร์"
            id.startsWith("ear:deg:") -> "คอร์ดขั้น ${id.removePrefix("ear:deg:")}"
            id.startsWith("ear:7th:") -> when (id.removePrefix("ear:7th:")) { "maj7" -> "เมเจอร์ 7"; "min7" -> "ไมเนอร์ 7"; else -> "โดมิแนนท์ 7" }
            id == "ear:bass" -> "เบสไลน์"
            id == "ear:mix:key" -> "หาคีย์"
            id == "ear:mix:prog" -> "โปรเกรสชันในท่อน"
            id == "ear:mix:mel" -> "ทำนองในท่อน"
            else -> id
        }
    }

    override fun onCleared() {
        onStop()
        scheduler.shutdown()
    }
}
