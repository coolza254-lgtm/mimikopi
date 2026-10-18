package com.id.mimikopi.desktop.port.reader

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/reader/ReaderViewModel.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.progress.Benchmarks
import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.exam.ExamPart
import com.id.mimikopi.core.exam.ExamSitting
import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.exam.Grade
import com.id.mimikopi.core.exam.PartMark
import com.id.mimikopi.core.playalong.Advice
import com.id.mimikopi.core.playalong.Coach
import com.id.mimikopi.core.playalong.Feedback
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.playalong.PlayAlongJudge
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.playalong.PlayResult
import com.id.mimikopi.core.playalong.PlaySetup
import com.id.mimikopi.core.practice.TimingRules
import com.id.mimikopi.core.practice.isClean
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import com.id.mimikopi.desktop.port.SettingEntity
import com.id.mimikopi.desktop.port.SettingKeys
import com.id.mimikopi.desktop.port.OutputRoute
import com.id.mimikopi.desktop.port.AttemptEvent
import com.id.mimikopi.desktop.port.NoteScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

enum class Phase { IDLE, COUNT_IN, PLAYING, LISTENING, RESULT }

enum class HandChoice(val hands: Set<Hand>) {
    RIGHT(setOf(Hand.RIGHT)), LEFT(setOf(Hand.LEFT)), BOTH(setOf(Hand.RIGHT, Hand.LEFT))
}

/** Reader preferences remembered between pieces. */
@Serializable
data class ReaderOptions(
    val mode: PlayMode = PlayMode.WAIT,
    val hands: HandChoice = HandChoice.RIGHT,
    /** The app plays the hand(s) you are not playing; off = that hand stays silent. */
    val playOtherHand: Boolean = true,
    /** Click during tempo-mode runs (the count-in always clicks). */
    val metronome: Boolean = true,
    /** Tempo ramp (learning-system.md 6.3) in tempo mode; off = set the tempo by hand. */
    val useRamp: Boolean = true,
    val showKeyboard: Boolean = true,
    /** Transcribe helper (learning-system.md 11.3): notes hidden until the run ends. */
    val hideNotes: Boolean = false,
    /** "Listen" repeats the section until stopped. */
    val loopListen: Boolean = false,
    /** Page view (the default): the score wrapped into lines like a printed sheet, instead of one long strip. */
    val pageView: Boolean = true,
    /** Page view at the size of printed music (several lines on screen); off = large lines for a phone on the stand. */
    val paperSize: Boolean = true,
    /** Falling notes over a keyboard instead of the score (songs only); a run with it does not count toward passing. */
    val falling: Boolean = false,
)

sealed interface ResultAction {
    data class Loop(val from: Int, val to: Int) : ResultAction
    data class Hands(val choice: HandChoice) : ResultAction
    data class Tempo(val bpm: Int) : ResultAction
}

data class CoachLine(val text: String, val action: ResultAction? = null)

data class ResultUi(
    val headline: String,
    val details: String,
    val clean: Boolean?,
    val coach: List<CoachLine>,
    val progressLine: String?,
)

data class ReaderUi(
    val phase: Phase = Phase.IDLE,
    val options: ReaderOptions = ReaderOptions(),
    val bpm: Int = 60,
    val targetBpm: Int = 60,
    val section: IntRange = 0..0,
    /** First bar tapped while choosing a section, waiting for the second tap. */
    val pendingSectionStart: Int? = null,
    val countIn: Int? = null,
    val results: Map<NoteKey, NoteResult> = emptyMap(),
    val wrongMarks: List<WrongMark> = emptyList(),
    val hotMeasures: List<Int> = emptyList(),
    val result: ResultUi? = null,
    val showNames: Boolean = false,
    val packMissing: Boolean = false,
    val passDays: Int = 0,
    val message: String? = null,
    /** Exam sight-reading: seconds left to look at the music before the run starts by itself. */
    val previewLeft: Int? = null,
)

/**
 * Sheet reader and play-along (PROMPT.md section 3): wait and tempo modes, per-note judging, section
 * loops, separate hands with the app playing the other hand, tempo ramp, demo playback, problem spots.
 * All judging happens on the main thread with timestamps from the shared input stream; the count-in
 * clicks come from the native metronome and fix the timeline (the downbeat time t0).
 */
class ReaderViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val pieces = services.pieces
    private val learning = services.learning
    private val hub = services.inputHub
    private val piano = services.piano
    private val scheduler = NoteScheduler(piano)
    private val json = Json { ignoreUnknownKeys = true }

    /** What the reader shows: a library piece, a lesson's score drill, a new-reading piece, or a benchmark level. */
    sealed interface Source {
        data class Piece(val id: String, val hand: HandChoice?) : Source
        data class LessonDrill(val lesson: Lesson, val index: Int) : Source { val drill: Drill get() = lesson.drills[index] }
        data class NewReading(val level: Int) : Source
        data class Benchmark(val level: Int) : Source
        /** A level test of the skill page (rhythm, sight-reading, technique). */
        data class Check(val level: com.id.mimikopi.core.progress.SkillLevel) : Source {
            val drill: Drill get() = (level.run as com.id.mimikopi.core.progress.CheckRun.ScoreRun).drill
        }
        /** One played part of a grade exam (pieces, scales, sight-reading), taken once. */
        data class Exam(val sitting: String, val part: ExamPart) : Source
    }

    /** The practice log kind of this sitting's runs, when the Today plan says (e.g. "warmup"). */
    private val via: String? = args.get<String>("via")

    data class LoadedPiece(val score: Score, val layout: ScoreLayout, val keyRange: IntRange)

    val source: Source = when {
        args.get<String>("pieceId") != null -> Source.Piece(args.get<String>("pieceId")!!, args.get<String>("hand")?.let { h -> runCatching { HandChoice.valueOf(h) }.getOrNull() })
        args.get<String>("lessonId") != null -> learning.lesson(args.get<String>("lessonId")!!)
            ?.let { l -> args.get<String>("drill")?.toIntOrNull()?.takeIf { it in l.drills.indices }?.let { Source.LessonDrill(l, it) } }
            ?: Source.Piece("", null)
        args.get<String>("check") != null -> com.id.mimikopi.core.progress.SkillBook.parseKey(args.get<String>("check"))
            ?.takeIf { it.run is com.id.mimikopi.core.progress.CheckRun.ScoreRun }?.let { Source.Check(it) } ?: Source.Piece("", null)
        args.get<String>("exam") != null -> ExamPart.entries.firstOrNull { it.name == args.get<String>("part") }
            ?.let { Source.Exam(args.get<String>("exam")!!, it) } ?: Source.Piece("", null)
        args.get<String>("newread") != null -> Source.NewReading(args.get<String>("newread")!!.toIntOrNull()?.coerceIn(1, 5) ?: 1)
        else -> Source.Benchmark(args.get<String>("bench")?.toIntOrNull()?.coerceIn(1, Benchmarks.MAX_LEVEL) ?: 1)
    }

    /** Library pieces keep options and progress; drills, new reading and benchmarks use fixed settings. */
    val fixed: Boolean get() = source !is Source.Piece

    private val _piece = MutableStateFlow<LoadedPiece?>(null)
    val piece: StateFlow<LoadedPiece?> = _piece.asStateFlow()
    val score: Score? get() = _piece.value?.score

    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()
    val pressed = hub.pressed

    var nameStyle: NoteNameStyle = NoteNameStyle.BOTH
        private set

    private var seed = System.currentTimeMillis()
    private var benchLevel = (source as? Source.Benchmark)?.level ?: 1
    /** Drill sitting: clean runs so far, clean runs in a row, runs on the current generated piece. */
    private var cleanCount = 0
    private var cleanStreak = 0
    private var runsOnPiece = 0
    private var metThisSitting = false
    private var newPieceNext = false

    private fun keyRangeOf(score: Score): IntRange = score.events.flatMap { e -> e.pitches.map { it.midi } }.let { notes ->
        val lo = minOf(notes.minOrNull() ?: 48, 48)
        val hi = maxOf(notes.maxOrNull() ?: 72, 72)
        (lo - lo % 12)..(if (hi % 12 == 0) hi else hi - hi % 12 + 12)
    }

    /** E_TRANSPOSE: the score to play (the shown score moved to [transposeKey]); null for everything else. */
    private var playScore: Score? = null
    var transposeKey: Int? = null
        private set

    /** Builds the score for the current source (a new one for generated material). */
    private fun generate(): Score? = runCatching {
        playScore = null
        transposeKey = null
        when (val src = source) {
            is Source.Piece -> pieces.pieceOrExercise(src.id)
            is Source.Check -> scoreFor(src.drill)
            is Source.Exam -> examScore(src)
            is Source.LessonDrill -> scoreFor(src.drill)
            is Source.NewReading -> Score.from(ScoreGenerators.sightReading(newReadingDrill(src.level), seed))
            is Source.Benchmark -> Score.from(ScoreGenerators.sightReading(Benchmarks.sightReadingDrill(benchLevel), Benchmarks.sightReadingSeed(benchLevel)))
        }
    }.getOrNull()

    /** A drill's score: generated for exercises, the piece or exercise file for E_PIECE. */
    private fun scoreFor(d: Drill): Score? = when (d.type) {
        ExerciseType.E_FINGER_PATTERN -> Score.from(ScoreGenerators.fingerPatterns(d, seed))
        ExerciseType.E_RHYTHM_TAP -> Score.from(ScoreGenerators.rhythm(d, seed))
        ExerciseType.E_READ_PIECE -> Score.from(ScoreGenerators.read(d, seed))
        ExerciseType.E_COMP_PATTERN -> Score.from(ScoreGenerators.comp(d, seed))
        ExerciseType.E_TRANSPOSE -> {
            val from = d.keys.firstOrNull() ?: 0
            val targets = d.targetKeys.ifEmpty { listOf(1) }
            val to = targets[kotlin.random.Random(seed).nextInt(targets.size)]
            val (original, moved) = com.id.mimikopi.core.theory.StageGenerators.transposePair(from, to, seed)
            playScore = Score.from(moved)
            transposeKey = to
            Score.from(original)
        }
        ExerciseType.E_PIECE -> pieces.pieceOrExercise(d.pieces.first())
        else -> null
    }

    /** The exam this reader plays a part of, with its grade (null until the exams are read). */
    private fun exam(): Pair<ExamSitting, Grade>? {
        val src = source as? Source.Exam ?: return null
        val sitting = services.progress.exams.value.firstOrNull { it.id == src.sitting } ?: return null
        return Exams.grade(sitting.grade)?.let { sitting to it }
    }

    private fun examScore(src: Source.Exam): Score? {
        val (sitting, grade) = exam() ?: return null
        return when (src.part) {
            ExamPart.PIECE_1 -> pieces.pieceOrExercise(sitting.pieces[0])
            ExamPart.PIECE_2 -> pieces.pieceOrExercise(sitting.pieces[1])
            ExamPart.POP -> Score.from(Exams.pop(grade))
            ExamPart.SCALES -> Score.from(Exams.scalesPiece(grade, sitting.seed))
            ExamPart.SIGHT -> Score.from(Exams.sightPiece(grade, sitting.seed))
            ExamPart.AURAL -> null
        }
    }

    /** This exam part was taken already (a part counts once). */
    private fun examTaken(): Boolean {
        val src = source as? Source.Exam ?: return false
        return exam()?.first?.mark(src.part) != null
    }

    private fun newReadingDrill(level: Int) = Drill(ExerciseType.E_READ_PIECE, "new", level = "L$level", bpm = 60, mode = "TEMPO",
        hand = if (level == 2) "LEFT" else "RIGHT")

    private fun loadPiece() {
        val s = generate() ?: run { _piece.value = null; return }
        val range = playScore?.let { p -> keyRangeOf(p).let { r -> minOf(r.first, keyRangeOf(s).first)..maxOf(r.last, keyRangeOf(s).last) } } ?: keyRangeOf(s)
        _piece.value = LoadedPiece(s, ScoreLayout.of(s), range)
        runsOnPiece = 0
        newPieceNext = false
        val bpm = when (val src = source) {
            is Source.LessonDrill -> src.drill.bpm
            is Source.Check -> src.drill.bpm
            is Source.NewReading, is Source.Benchmark -> 60
            is Source.Piece, is Source.Exam -> s.tempo
        }
        _ui.update { it.copy(section = 0 until s.measures, bpm = bpm, targetBpm = bpm, results = emptyMap(), wrongMarks = emptyList(), pendingSectionStart = null) }
    }

    /** Options a fixed source dictates (mode, hands, no ramp); the keyboard choice stays the user's. */
    private fun fixedOptions(saved: ReaderOptions): ReaderOptions = when (val src = source) {
        is Source.LessonDrill, is Source.Check -> {
            val d = if (src is Source.Check) src.drill else (src as Source.LessonDrill).drill
            ReaderOptions(
                mode = if (d.mode == "WAIT") PlayMode.WAIT else PlayMode.TEMPO,
                hands = runCatching { HandChoice.valueOf(d.hand) }.getOrDefault(HandChoice.RIGHT),
                playOtherHand = d.type == ExerciseType.E_PIECE || d.type == ExerciseType.E_COMP_PATTERN || d.backing,
                metronome = true, useRamp = false, showKeyboard = saved.showKeyboard,
            )
        }
        is Source.NewReading -> ReaderOptions(PlayMode.TEMPO, if (src.level == 2) HandChoice.LEFT else if (src.level == 5) HandChoice.BOTH else HandChoice.RIGHT,
            playOtherHand = false, metronome = true, useRamp = false, showKeyboard = saved.showKeyboard)
        is Source.Benchmark -> ReaderOptions(PlayMode.TEMPO, if (benchLevel == 2) HandChoice.LEFT else if (benchLevel == 5) HandChoice.BOTH else HandChoice.RIGHT,
            playOtherHand = false, metronome = true, useRamp = false, showKeyboard = saved.showKeyboard)
        is Source.Piece -> saved.let { o -> src.hand?.let { o.copy(hands = it) } ?: o }
        // Exams: both hands, at the written tempo, nothing played for the player (the metronome stays: the judge times
        // every note against a steady beat).
        is Source.Exam -> ReaderOptions(PlayMode.TEMPO,
            if (src.part == ExamPart.SIGHT) exam()?.second?.let { runCatching { HandChoice.valueOf(Exams.sightHand(it)) }.getOrNull() } ?: HandChoice.RIGHT else HandChoice.BOTH,
            playOtherHand = false, metronome = true, useRamp = false, showKeyboard = saved.showKeyboard)
    }

    /** Title line for the top bar. */
    fun title(): String = when (val src = source) {
        is Source.Piece -> score?.title ?: ""
        is Source.LessonDrill -> "${src.lesson.id} · ${src.drill.title}" +
            (transposeKey?.let { " · เล่นในคีย์ ${com.id.mimikopi.core.theory.Keys.name(it)}" } ?: "")
        is Source.NewReading -> "อ่านโน้ตใหม่ (ระดับ L${src.level})"
        is Source.Benchmark -> "วัดผล: อ่านโน้ตระดับ L$benchLevel"
        is Source.Check -> "ทดสอบวัดระดับ · ${src.level.skill.title} ระดับ ${src.level.level}"
        is Source.Exam -> "สอบ ${exam()?.second?.name ?: ""} · ${src.part.title}"
    }

    private var holding = false
    private var runJob: Job? = null
    /** Exam sight-reading: the look-first countdown. */
    private var previewJob: Job? = null
    private var judge: PlayAlongJudge? = null
    private var latencies: Map<InputSource, Double> = emptyMap()
    private var helperUsedThisRun = false
    private var runStartedAt = 0L
    private var hitPitch = mutableMapOf<NoteKey, Int>()

    // Cursor clock, read by the score view every frame.
    @Volatile private var clockT0 = 0L
    @Volatile private var clockBeatNanos = 1L
    @Volatile private var clockStartBeat = 0.0
    @Volatile private var clockEndBeat = 0.0
    @Volatile private var clockRunning = false
    @Volatile private var waitBeat = 0.0

    init {
        loadPiece()
        viewModelScope.launch {
            pieces.load()
            services.progress.load()
            if (_piece.value == null) loadPiece() // user scores and exams are read from storage first
            learning.load()
            services.database.settingsDao().get(SettingKeys.NOTE_NAME_STYLE)?.let { runCatching { nameStyle = NoteNameStyle.valueOf(it) } }
            val options = services.database.settingsDao().get(SettingKeys.READER_OPTIONS)
                ?.let { runCatching { json.decodeFromString(ReaderOptions.serializer(), it) }.getOrNull() } ?: ReaderOptions()
            val missing = withContext(Dispatchers.IO) { services.samplePacks.installed() == null }
            _ui.update { it.copy(options = fixedOptions(options), packMissing = missing) }
            refreshProgress()
            applyRampTempo()
            startPreview()
        }
        viewModelScope.launch { hub.events.collect(::onInput) }
    }

    /** Exam sight-reading: half a minute to look at the music, then the run starts, as an examiner would ask. */
    private fun startPreview() {
        val src = source as? Source.Exam ?: return
        if (src.part != ExamPart.SIGHT || examTaken() || score == null) return
        previewJob = viewModelScope.launch {
            for (left in PREVIEW_SECONDS downTo 1) {
                _ui.update { it.copy(previewLeft = left) }
                delay(1000)
            }
            _ui.update { it.copy(previewLeft = null) }
            previewJob = null
            if (_ui.value.phase == Phase.IDLE) start()
        }
    }

    fun onStart() {
        if (holding) return
        holding = true
        viewModelScope.launch { withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    fun onStop() {
        stop()
        if (!holding) return
        holding = false
        hub.releaseAll()
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    // ---------------- options ----------------

    fun setMode(mode: PlayMode) = updateOptions { it.copy(mode = mode) }
    fun setHands(choice: HandChoice) = updateOptions { it.copy(hands = choice) }
    fun setPlayOtherHand(on: Boolean) { if (source !is Source.Exam) updateOptions { it.copy(playOtherHand = on) } }
    fun setMetronome(on: Boolean) = updateOptions { it.copy(metronome = on) }
    fun setUseRamp(on: Boolean) = updateOptions { it.copy(useRamp = on) }
    fun setShowKeyboard(on: Boolean) = updateOptions { it.copy(showKeyboard = on) }
    fun setHideNotes(on: Boolean) = updateOptions { it.copy(hideNotes = on) }
    fun setLoopListen(on: Boolean) = updateOptions { it.copy(loopListen = on) }
    fun setPageView(on: Boolean) = updateOptions { it.copy(pageView = on) }
    fun setPaperSize(on: Boolean) = updateOptions { it.copy(paperSize = on) }
    fun setFalling(on: Boolean) = updateOptions { it.copy(falling = on) }

    private fun updateOptions(change: (ReaderOptions) -> ReaderOptions) {
        if (!editable()) return
        val before = _ui.value.options
        var next = change(before)
        if (fixed) next = before.copy(showKeyboard = next.showKeyboard, playOtherHand = next.playOtherHand, pageView = next.pageView, paperSize = next.paperSize)
        _ui.update { it.copy(options = next) }
        viewModelScope.launch {
            val dao = services.database.settingsDao()
            val stored = if (fixed) (dao.get(SettingKeys.READER_OPTIONS)?.let { runCatching { json.decodeFromString(ReaderOptions.serializer(), it) }.getOrNull() }
                ?: ReaderOptions()).copy(showKeyboard = next.showKeyboard, pageView = next.pageView, paperSize = next.paperSize) else next
            dao.put(SettingEntity(SettingKeys.READER_OPTIONS, json.encodeToString(ReaderOptions.serializer(), stored)))
        }
        applyRampTempo()
    }

    fun changeBpm(delta: Int) {
        if (!editable() || fixed) return
        val s = score ?: return
        _ui.update { it.copy(bpm = (it.bpm + delta).coerceIn(30, maxOf(s.tempo * 2, 160))) }
    }

    /** Bars are chosen by tapping: first tap = start (and end), second tap = end. */
    fun onMeasureTap(m: Int) {
        if (!editable() || fixed) return
        val start = _ui.value.pendingSectionStart
        if (start == null) {
            _ui.update { it.copy(section = m..m, pendingSectionStart = m, result = null, phase = Phase.IDLE, results = emptyMap()) }
        } else {
            _ui.update { it.copy(section = minOf(start, m)..maxOf(start, m), pendingSectionStart = null) }
        }
        applyRampTempo()
    }

    fun wholePiece() {
        if (!editable()) return
        val s = score ?: return
        _ui.update { it.copy(section = 0 until s.measures, pendingSectionStart = null) }
        applyRampTempo()
    }

    fun practiseMeasures(from: Int, to: Int) {
        if (!editable()) return
        _ui.update { it.copy(section = from..to, pendingSectionStart = null, phase = Phase.IDLE, result = null, results = emptyMap()) }
        applyRampTempo()
    }

    fun applyAction(a: ResultAction) {
        when (a) {
            is ResultAction.Loop -> practiseMeasures(a.from, a.to)
            is ResultAction.Hands -> { setHands(a.choice); _ui.update { it.copy(phase = Phase.IDLE, result = null, results = emptyMap()) } }
            is ResultAction.Tempo -> {
                updateOptions { it.copy(useRamp = false, mode = PlayMode.TEMPO) }
                _ui.update { it.copy(bpm = a.bpm, phase = Phase.IDLE, result = null, results = emptyMap()) }
            }
        }
    }

    /** Note names are a counted helper: a run that used them does not count toward passing the piece. */
    fun toggleNames() {
        val on = !_ui.value.showNames
        _ui.update { it.copy(showNames = on) }
        if (on) {
            if (_ui.value.phase == Phase.PLAYING || _ui.value.phase == Phase.COUNT_IN) helperUsedThisRun = true
            viewModelScope.launch {
                val dao = services.database.settingsDao()
                val n = (dao.get(SettingKeys.HELPER_NOTE_NAMES_COUNT)?.toIntOrNull() ?: 0) + 1
                dao.put(SettingEntity(SettingKeys.HELPER_NOTE_NAMES_COUNT, n.toString()))
            }
        }
    }

    private fun editable() = _ui.value.phase == Phase.IDLE || _ui.value.phase == Phase.RESULT

    private fun rampKey(): String {
        val u = _ui.value
        return PieceRules.rampKey(u.options.hands.hands, u.section.first, u.section.last)
    }

    /** In tempo mode with the ramp on, the tempo comes from the ramp of the current hands + section. */
    private fun applyRampTempo() {
        if (fixed) return
        val s = score ?: return
        val u = _ui.value
        if (u.options.mode != PlayMode.TEMPO || !u.options.useRamp) return
        val ramp = PieceRules.ramp(pieces.progressOf(s.id), rampKey(), s.tempo, learning.config.tempoRamp)
        _ui.update { it.copy(bpm = ramp.currentBpm) }
    }

    private fun refreshProgress() {
        if (fixed) return
        val s = score ?: return
        val p = pieces.progressOf(s.id)
        _ui.update { it.copy(hotMeasures = PieceRules.hotMeasures(p), passDays = p.passDays.toSet().size) }
    }

    // ---------------- cursor ----------------

    fun cursorAt(frameNanos: Long): Double {
        val u = _ui.value
        return when {
            clockRunning -> (clockStartBeat + (frameNanos - clockT0).toDouble() / clockBeatNanos).coerceIn(clockStartBeat, clockEndBeat)
            u.phase == Phase.PLAYING && u.options.mode == PlayMode.WAIT -> waitBeat
            else -> score?.measureStart(u.section.first) ?: 0.0
        }
    }

    // ---------------- runs ----------------

    fun start() {
        if (!editable()) return
        previewJob?.cancel()
        previewJob = null
        if (_ui.value.previewLeft != null) _ui.update { it.copy(previewLeft = null) }
        if (examTaken()) {
            _ui.update { it.copy(message = "สอบส่วนนี้ไปแล้ว ผลนับครั้งแรกเท่านั้น") }
            return
        }
        if (newPieceNext) {
            seed = System.currentTimeMillis()
            loadPiece()
        }
        val s = playScore ?: score ?: return
        val u = _ui.value
        val setup = PlaySetup(
            mode = u.options.mode,
            hands = u.options.hands.hands,
            fromMeasure = u.section.first,
            toMeasure = u.section.last,
            bpm = u.bpm.toDouble(),
            toleranceMs = learning.toleranceMs((source as? Source.LessonDrill)?.lesson?.stage ?: stageOf(s), u.bpm.toDouble(), benchmark = source is Source.Benchmark || source is Source.Check || source is Source.Exam),
            latencyMs = 0, // compensated per event and source in onInput
            anyKey = (source as? Source.LessonDrill)?.drill?.type == ExerciseType.E_RHYTHM_TAP,
        )
        val j = PlayAlongJudge(s, setup)
        if (j.expected.isEmpty()) {
            _ui.update { it.copy(message = "ท่อนนี้ไม่มีโน้ตของมือที่เลือก ลองเลือกมืออื่นหรือท่อนอื่น") }
            return
        }
        judge = j
        hitPitch = mutableMapOf()
        pedalEvents.clear()
        chordHits.clear()
        chordBeats = j.expected.groupBy { it.beat }.filterValues { it.size >= 3 }.keys
        // Falling notes are played without reading the score: like note names, the run does not count toward passing.
        helperUsedThisRun = u.showNames || (u.options.falling && !fixed)
        runStartedAt = System.currentTimeMillis()
        _ui.update { it.copy(results = emptyMap(), wrongMarks = emptyList(), result = null, pendingSectionStart = null, message = null) }
        runJob?.cancel()
        runJob = viewModelScope.launch {
            val cfg = services.inputConfig.load()
            val route = OutputRoute.current(getApplication())
            latencies = InputSource.entries.associateWith { cfg.latencyMs[InputConfig.latencyKey(it, route)] ?: 0.0 }
            if (setup.mode == PlayMode.TEMPO) runTempo(j) else runWait(j)
        }
    }

    /** Stops a run or the demo. A stopped run with some notes played still shows its result. */
    fun stop() {
        val phase = _ui.value.phase
        runJob?.cancel()
        runJob = null
        scheduler.clear()
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        clockRunning = false
        val j = judge
        judge = null
        if (j != null && (phase == Phase.PLAYING) && j.outcomes().values.any { it.result != NoteResult.PENDING }) {
            finish(j)
        } else if (phase != Phase.RESULT) {
            _ui.update { it.copy(phase = Phase.IDLE, countIn = null) }
        }
    }

    private suspend fun runTempo(j: PlayAlongJudge) {
        val s = j.score
        val beatNanos = (j.beatMs * 1e6).toLong()
        val countIn = if (s.beatsPerMeasure >= 3) s.beatsPerMeasure else s.beatsPerMeasure * 2
        val pattern = IntArray(s.beatsPerMeasure) { if (it == 0) 0 else 2 }
        _ui.update { it.copy(phase = Phase.COUNT_IN, countIn = countIn) }
        piano.drainMetronomeTicks()
        piano.setMetronome(true, j.setup.bpm, 1, pattern, 0.9f)
        val stamps = mutableListOf<FrameTimestamp>()
        val ticks = mutableListOf<Long>()
        val began = System.nanoTime()
        var t0: Long? = null
        // The downbeat follows the count-in clicks exactly; their frames give its presentation time.
        while (t0 == null) {
            delay(5)
            piano.timestamp()?.let { stamps += FrameTimestamp(it[0], it[1]) }
            ticks += piano.drainMetronomeTicks().toList()
            val rate = piano.outputSampleRate()
            if (ticks.size >= 2 && stamps.isNotEmpty() && rate > 0) {
                val times = JitterAnalysis.presentationTimesNanos(ticks.take(2), stamps, rate)
                t0 = (times.mapIndexed { i, t -> t - i * beatNanos }.average() + countIn * beatNanos).toLong()
            } else if (System.nanoTime() - began > 3 * beatNanos + 600_000_000L) {
                // No audio clock (output closed): count in visually from now.
                t0 = System.nanoTime() + countIn * beatNanos
            }
        }
        val downbeat = t0
        j.start(downbeat / 1_000_000)
        startClock(downbeat, beatNanos, j.startBeat, j.endBeat)
        if (_ui.value.options.playOtherHand) scheduleAccompaniment(j, j.startBeat, j.endBeat, downbeat)
        var metronomeOff = false
        while (true) {
            val now = System.nanoTime()
            piano.drainMetronomeTicks()
            if (now < downbeat) {
                val left = ((downbeat - now + beatNanos - 1) / beatNanos).toInt().coerceIn(1, countIn)
                if (_ui.value.countIn != left) _ui.update { it.copy(countIn = left) }
            } else if (_ui.value.phase == Phase.COUNT_IN) {
                _ui.update { it.copy(phase = Phase.PLAYING, countIn = null) }
            }
            if (!metronomeOff && !_ui.value.options.metronome && now > downbeat - beatNanos / 2) {
                piano.setMetronome(false, j.setup.bpm, 1, pattern, 0f)
                metronomeOff = true
            }
            val missed = j.advance(now / 1_000_000)
            if (missed.isNotEmpty()) _ui.update { u -> u.copy(results = u.results + missed.associateWith { NoteResult.MISSED }) }
            if (j.isFinished(now / 1_000_000)) break
            delay(16)
        }
        piano.setMetronome(false, j.setup.bpm, 1, pattern, 0f)
        clockRunning = false
        judge = null
        finish(j)
    }

    private suspend fun runWait(j: PlayAlongJudge) {
        j.start(System.nanoTime() / 1_000_000)
        waitBeat = j.cursorBeat(0)
        _ui.update { it.copy(phase = Phase.PLAYING, countIn = null) }
        val first = j.currentGroup()?.first()?.beat ?: j.endBeat
        if (_ui.value.options.playOtherHand) scheduleAccompaniment(j, j.startBeat, first, System.nanoTime())
        // Input drives the run (onInput); this only waits for the end.
        while (!j.isFinished(0)) delay(50)
        delay(600)
        judge = null
        finish(j)
    }

    /** Plays the other hand's notes with onset in [from, to), placing beat [from] at [anchorNanos]. */
    private fun scheduleAccompaniment(j: PlayAlongJudge, from: Double, to: Double, anchorNanos: Long) {
        val held = j.score.sounding().associate { (it.eventId to it.midi) to it.duration }
        val durations = j.score.events.associate { it.id to it.duration }
        val beatNanos = j.beatMs * 1e6
        // Tempo mode: the click timeline is in presentation time, so the app's notes are sent early by the
        // output latency to sound with it (Oboe's estimate, capped; wait mode plays right away).
        val lead = if (j.setup.mode == PlayMode.TEMPO) ((piano.outputLatencyMs() ?: 0.0).coerceIn(0.0, 250.0) * 1e6).toLong() else 0L
        for (n in j.accompaniment) {
            if (n.beat < from - 1e-9 || n.beat >= to - 1e-9) continue
            val on = anchorNanos + ((n.beat - from) * beatNanos).toLong() - lead
            val len = ((held[n.key.eventId to n.midi] ?: durations[n.key.eventId] ?: 1.0) * beatNanos * 0.92).toLong().coerceAtMost(8_000_000_000L)
            scheduler.noteOn(on, n.midi, 62)
            scheduler.noteOff(on + len, n.midi)
        }
    }

    private fun startClock(t0: Long, beatNanos: Long, startBeat: Double, endBeat: Double) {
        clockT0 = t0
        clockBeatNanos = beatNanos
        clockStartBeat = startBeat
        clockEndBeat = endBeat
        clockRunning = true
    }

    /** Demo: the app plays the section (both hands) at the current tempo with the cursor. */
    fun listen() {
        val s = playScore ?: score ?: return
        if (!editable()) return
        val u = _ui.value
        val from = s.measureStart(u.section.first)
        val to = s.measureStart(u.section.last + 1)
        val beatNanos = (TimingRules.beatMs(u.bpm.toDouble()) * 1e6).toLong()
        val t0 = System.nanoTime() + 400_000_000L
        scheduleSection(s, from, to, t0, beatNanos)
        startClock(t0, beatNanos, from, to)
        _ui.update { it.copy(phase = Phase.LISTENING, results = emptyMap(), result = null, pendingSectionStart = null) }
        runJob?.cancel()
        runJob = viewModelScope.launch {
            var start = t0
            while (true) {
                delay(((start + ((to - from) * beatNanos).toLong() - System.nanoTime()) / 1_000_000).coerceAtLeast(0) + 400)
                if (!_ui.value.options.loopListen) break
                // Loop: schedule the section again right after a short gap.
                start = System.nanoTime() + 600_000_000L
                scheduleSection(s, from, to, start, beatNanos)
                startClock(start, beatNanos, from, to)
            }
            delay(500)
            clockRunning = false
            _ui.update { it.copy(phase = Phase.IDLE) }
        }
    }

    private fun scheduleSection(s: Score, from: Double, to: Double, t0: Long, beatNanos: Long) {
        // Tied notes are held, not struck again.
        for (n in s.sounding()) {
            if (n.onset < from - 1e-9 || n.onset >= to - 1e-9) continue
            val on = t0 + ((n.onset - from) * beatNanos).toLong()
            scheduler.noteOn(on, n.midi, if (n.hand == Hand.RIGHT) 74 else 60)
            scheduler.noteOff(on + (minOf(n.duration, to - n.onset) * beatNanos * 0.92).toLong(), n.midi)
        }
    }

    private fun onInput(e: InputEvent) {
        val j = judge ?: return
        val phase = _ui.value.phase
        if (phase != Phase.PLAYING && phase != Phase.COUNT_IN) return
        when (e) {
            is InputEvent.NoteOn -> {
                val latency = latencies[e.source] ?: 0.0
                val beforeGroup = j.currentGroup()
                val atMs = ((e.timeNanos - latency * 1e6) / 1e6).toLong()
                when (val f = j.onNoteOn(e.note, atMs)) {
                    is Feedback.Hit -> {
                        hitPitch[f.key] = e.note
                        j.expected.firstOrNull { it.key == f.key }?.beat?.takeIf { it in chordBeats }?.let { b -> chordHits.putIfAbsent(b, atMs) }
                        val done = if (j.setup.mode == PlayMode.WAIT && beforeGroup != null && j.currentGroup() !== beforeGroup) beforeGroup else null
                        _ui.update { u -> u.copy(results = u.results + (done?.associate { it.key to j.outcome(it.key).result } ?: mapOf(f.key to f.result))) }
                        if (done != null) onGroupDone(j, done.first().beat)
                    }
                    is Feedback.Wrong -> {
                        val mark = WrongMark(e.note, if (j.setup.mode == PlayMode.WAIT) waitBeat else f.press.beat, System.nanoTime())
                        _ui.update { u -> u.copy(wrongMarks = (u.wrongMarks + mark).takeLast(6)) }
                    }
                    Feedback.Ignored -> Unit
                }
            }
            is InputEvent.NoteOff -> j.onNoteOff(e.note)
            is InputEvent.Sustain -> pedalEvents += ((e.timeNanos - (latencies[e.source] ?: 0.0) * 1e6) / 1e6).toLong() to e.down
            else -> Unit
        }
    }

    // Sustain pedal (learning-system.md 4.5): pedal events and when each chord of the run was first struck.
    private val pedalEvents = mutableListOf<Pair<Long, Boolean>>()
    private val chordHits = mutableMapOf<Double, Long>()
    private var chordBeats: Set<Double> = emptySet()

    /** Coach line on pedal changes for drills that ask for it; informative only, never part of the pass. */
    private fun pedalLine(): CoachLine? {
        val d = (source as? Source.LessonDrill)?.drill ?: return null
        if (!d.pedal) return null
        val r = com.id.mimikopi.core.practice.PedalCheck.analyze(chordHits.toSortedMap().values.toList(), pedalEvents.toList())
        return CoachLine(when {
            !r.sawPedal -> "ไม่ได้รับสัญญาณแป้นเหยียบ จึงตรวจการเปลี่ยนแป้นไม่ได้ (แป้นของเปียโนบางรุ่นไม่ส่งผ่าน MIDI หรือยังไม่ได้เหยียบ)"
            r.changes == 0 -> "ยังไม่มีการเปลี่ยนคอร์ดให้ตรวจการเปลี่ยนแป้น"
            r.good == r.changes -> "เปลี่ยนแป้นตามคอร์ดได้ครบ ${r.changes} ครั้ง"
            else -> "เปลี่ยนแป้นทันคอร์ด ${r.good}/${r.changes} ครั้ง ยกแป้นขึ้นทันทีหลังกดคอร์ดใหม่ แล้วเหยียบลงอีกครั้ง"
        })
    }

    private fun onGroupDone(j: PlayAlongJudge, doneBeat: Double) {
        val next = j.currentGroup()?.first()?.beat ?: j.endBeat
        waitBeat = next
        if (_ui.value.options.playOtherHand) scheduleAccompaniment(j, doneBeat, next, System.nanoTime())
    }

    private fun finish(j: PlayAlongJudge) {
        if (fixed) { finishFixed(j); return }
        val s = score ?: return
        val result = j.result()
        val seconds = (System.currentTimeMillis() - runStartedAt) / 1000
        services.appScope.launch {
            services.progress.log(via ?: "song", seconds)
            services.progress.addMistakes(com.id.mimikopi.core.progress.Mistakes.fromPlay(j, result, "piece", System.currentTimeMillis(), java.time.LocalDate.now().toString()))
        }
        val u = _ui.value
        val helper = helperUsedThisRun
        viewModelScope.launch {
            val before = pieces.progressOf(s.id)
            val slower = before.ramps[rampKey()]?.history?.lastOrNull() ?: (u.bpm * 0.9).roundToInt().coerceAtLeast(30)
            val update = PieceRules.afterRun(before, s, result, helper, u.options.useRamp && u.options.mode == PlayMode.TEMPO,
                learning.config.tempoRamp, learning.config.cleanRun)
            pieces.save(update.progress)
            saveAttempt(s, j, result, helper)
            val coach = coachLines(s, result, slower, update.newBpm, helper)
            enrolTrouble(j, result)
            val progressLine = when {
                update.piecePassed && update.passedToday -> "ผ่านเพลงนี้แล้ว (เล่นสะอาด 3 รอบติดที่ความเร็วเป้าหมาย ใน 2 วันที่ต่างกัน)"
                update.passedToday -> "วันนี้เล่นสะอาดครบ 3 รอบติดที่ความเร็วเป้าหมายแล้ว ต้องทำอีกครั้งในวันอื่นเพื่อผ่านเพลงนี้"
                else -> null
            }
            _ui.update {
                it.copy(
                    phase = Phase.RESULT,
                    result = resultUi(result, coach, progressLine),
                    bpm = update.newBpm ?: it.bpm,
                    countIn = null,
                )
            }
            if (update.passedToday) {
                learning.checkPieceLessons(pieces::lessonPiecePassed).forEach { l ->
                    _ui.update { u2 -> u2.copy(result = u2.result?.let { r -> r.copy(progressLine = listOfNotNull(r.progressLine, "ผ่านบท ${l.id} ${l.title} แล้ว").joinToString("\n")) }) }
                }
            }
            refreshProgress()
        }
    }

    /** Drills, new reading and benchmarks: count clean runs for the lesson criterion, rotate generated pieces. */
    private fun finishFixed(j: PlayAlongJudge) {
        val s = score ?: return
        val result = j.result()
        val helper = helperUsedThisRun
        // A wait-mode step (no timing) counts a run as clean when its first-try accuracy reaches the step's minimum.
        val waitMin = (source as? Source.LessonDrill)?.lesson?.pass?.minAccuracy?.takeIf { !result.timingJudged }
        val clean = if (waitMin != null) (result.summary?.noteAccuracy ?: 0.0) >= waitMin
        else result.timingJudged && result.summary?.isClean(learning.config.cleanRun) == true
        runsOnPiece++
        if (clean) { cleanCount++; cleanStreak++ } else cleanStreak = 0
        val generated = when (val src = source) {
            is Source.LessonDrill -> src.drill.type != ExerciseType.E_PIECE
            is Source.Check -> src.drill.type != ExerciseType.E_PIECE
            is Source.Exam -> false
            else -> true
        }
        // A clean run, or three runs of the same piece (learning-system.md 1.4), brings a new one.
        newPieceNext = generated && (clean || runsOnPiece >= 3) && source !is Source.Benchmark
        val seconds = (System.currentTimeMillis() - runStartedAt) / 1000
        if (source !is Source.Check && source !is Source.Exam) services.appScope.launch {
            services.progress.log(via ?: when (source) { is Source.NewReading -> "sight"; is Source.Benchmark -> "check"; else -> "ladder" }, seconds)
        }
        services.appScope.launch {
            services.progress.addMistakes(com.id.mimikopi.core.progress.Mistakes.fromPlay(j, result, "score", System.currentTimeMillis(), java.time.LocalDate.now().toString()))
        }
        viewModelScope.launch {
            val lines = mutableListOf<CoachLine>()
            var progressLine: String? = null
            when (val src = source) {
                is Source.Exam -> {
                    val sum = result.summary
                    val acc = sum?.noteAccuracy ?: 0.0
                    val timing = if (result.timingJudged) sum?.fractionWithinTiming ?: 0.0 else 0.0
                    val before = exam()?.first?.mark(src.part)
                    when {
                        before != null -> progressLine = "สอบส่วนนี้ไปแล้ว ได้ ${before.marks} คะแนน (นับครั้งแรกเท่านั้น)"
                        helper -> lines += CoachLine("เปิดชื่อโน้ตระหว่างสอบ ส่วนนี้จึงยังไม่นับ ปิดชื่อโน้ตแล้วเล่นใหม่ได้")
                        else -> {
                            val marks = Exams.playedMarks(src.part, acc, timing, result.stumbles)
                            val mark = PartMark(marks, System.currentTimeMillis(), acc, timing, result.stumbles)
                            services.progress.recordExam(src.sitting, src.part, mark)
                            services.progress.log("check", seconds)
                            benchDone = true
                            progressLine = "${src.part.title}: $marks จาก ${src.part.max} คะแนน · ${Exams.partBand(src.part, marks).title}"
                            Exams.remark(mark).lines().drop(1).forEach { lines += CoachLine(it) }
                        }
                    }
                    if (before != null) benchDone = true
                    _ui.update { it.copy(phase = Phase.RESULT, result = resultUi(result, lines.distinct(), progressLine), countIn = null) }
                    return@launch
                }
                is Source.Check -> {
                    val level = src.level
                    val m = com.id.mimikopi.core.progress.CheckMetrics(
                        accuracy = result.summary?.noteAccuracy ?: 0.0,
                        timing = if (result.timingJudged) result.summary?.fractionWithinTiming ?: 0.0 else 0.0,
                    )
                    val verdict = level.judge(m)
                    val passed = verdict.passed && !helper
                    val p = services.progress
                    p.addCheck(com.id.mimikopi.core.progress.CheckRecord(level.skill, level.level, java.time.LocalDate.now().toString(), passed, m, at = System.currentTimeMillis()))
                    p.log("check", seconds)
                    val status = com.id.mimikopi.core.progress.SkillBook.status(level.skill, p.checks.value, java.time.LocalDate.now())
                    verdict.lines.forEach { lines += CoachLine(it) }
                    if (helper) lines += CoachLine("เปิดตัวช่วยระหว่างทดสอบ ผลนี้จึงไม่นับ")
                    progressLine = if (passed) "ผ่านระดับ ${level.level}: ${level.canDo} · ${level.skill.title}ตอนนี้ระดับ ${status.level} จาก ${status.maxLevel}"
                    else if (status.lockedToday) "ยังไม่ผ่านระดับ ${level.level} · ลองครบ 2 ครั้งของวันนี้แล้ว ฝึกต่อแล้วลองใหม่วันอื่น"
                    else "ยังไม่ผ่านระดับ ${level.level} · ยังลองได้อีกครั้งวันนี้ (โน้ตใหม่)"
                    benchDone = passed || status.lockedToday
                    _ui.update { it.copy(phase = Phase.RESULT, result = resultUi(result, lines.distinct(), progressLine), countIn = null) }
                    return@launch
                }
                is Source.LessonDrill -> {
                    val l = src.lesson
                    val need = l.pass.cleanRuns
                    val required = src.index in l.pass.requiredDrills && (result.timingJudged || waitMin != null)
                    val count = if (l.pass.inRow) cleanStreak else cleanCount
                    val met = required && need != null && count >= need && !metThisSitting && !helper
                    learning.saveAttempt(
                        exerciseId = "${l.id}:${src.drill.type}:${src.index}",
                        paramsJson = """{"seed":$seed,"drill":${src.index},"lesson":"${l.id}","bpm":${result.setup.bpm}}""",
                        startedAt = runStartedAt, tempoBpm = if (result.timingJudged) result.setup.bpm else null,
                        noteAccuracy = result.summary?.noteAccuracy, timingWithinTol = if (result.timingJudged) result.summary?.fractionWithinTiming else null,
                        clean = if (result.timingJudged) clean else null, helperUses = if (helper) 1 else 0,
                        events = emptyList(), sessionId = services.session.currentId(),
                        hand = result.setup.hands.joinToString("+") { it.name }, stumbles = result.stumbles,
                    )
                    val passedNow = learning.recordDrill(l, src.index, met, helper)
                    if (met) metThisSitting = true
                    progressLine = when {
                        passedNow && met -> "ผ่านบท ${l.id} แล้ว บทถัดไปปลดล็อก"
                        met -> if (l.pass.requiredDrills.size > 1) "ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านส่วนอื่นของบทในวันเดียวกันด้วย" else "ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อผ่านบทนี้"
                        required && need != null && !metThisSitting -> if (l.pass.inRow) "เล่นสะอาดติดกัน $cleanStreak/$need ครั้ง" else "เล่นสะอาดแล้ว $cleanCount/$need ครั้ง"
                        !result.timingJudged && waitMin == null -> "โหมดรอใช้ฝึกอ่าน แบบทดสอบใช้โหมดตามจังหวะ"
                        else -> null
                    }
                    if (helper && required) lines += CoachLine("รอบนี้เปิดชื่อโน้ตไว้ จึงไม่นับเป็นการผ่าน")
                }
                is Source.NewReading -> learning.saveAttempt(
                    exerciseId = "NEW_READING:L${src.level}", paramsJson = """{"seed":$seed,"level":${src.level}}""",
                    startedAt = runStartedAt, tempoBpm = result.setup.bpm, noteAccuracy = result.summary?.noteAccuracy,
                    timingWithinTol = result.summary?.fractionWithinTiming, clean = clean, helperUses = if (helper) 1 else 0,
                    events = emptyList(), sessionId = services.session.currentId(), stumbles = result.stumbles,
                )
                is Source.Benchmark -> {
                    val level = benchLevel
                    if (clean && !helper && level < Benchmarks.MAX_LEVEL) {
                        learning.updateBenchmark { it.copy(sightReadingLevel = maxOf(it.sightReadingLevel ?: 0, level)) }
                        benchLevel = level + 1
                        newPieceNext = true
                        progressLine = "ระดับ L$level เล่นสะอาด ไปต่อระดับ L${level + 1}"
                    } else {
                        val reached = if (clean && !helper) level else level - 1
                        learning.updateBenchmark { it.copy(sightReadingLevel = maxOf(it.sightReadingLevel ?: 0, reached)) }
                        progressLine = "บันทึกผลแล้ว: อ่านโน้ตสะอาดได้ถึงระดับ L$reached"
                        benchDone = true
                    }
                }
                is Source.Piece -> Unit
            }
            enrolTrouble(j, result)
            pedalLine()?.let { lines += it }
            lines += coachLines(s, result, (result.setup.bpm * 0.9).roundToInt(), null, false, drill = true)
            _ui.update { it.copy(phase = Phase.RESULT, result = resultUi(result, lines.distinct(), progressLine), countIn = null) }
        }
    }

    /** Notes that went wrong repeatedly join the note-reading review (stands in for "practise this note"). */
    private suspend fun enrolTrouble(j: PlayAlongJudge, result: PlayResult) {
        val ids = result.troubleNotes.map { p ->
            val clef = if (j.expected.firstOrNull { it.pitch == p }?.hand == Hand.LEFT) "bass" else "treble"
            "note:$p:$clef"
        }
        if (ids.isEmpty()) return
        learning.enrol(ids)
        ids.forEach { learning.review(it, correct = false, responseMs = 0) }
    }

    /** Benchmark: the sight-reading part is finished (no more levels to try). */
    var benchDone = false
        private set

    /** Label for the button that starts the next run. */
    fun nextLabelIsNew(): Boolean = newPieceNext

    private fun resultUi(r: PlayResult, coach: List<CoachLine>, progressLine: String?): ResultUi {
        val sum = r.summary
        val headline: String
        val details: String
        val clean: Boolean?
        if (sum == null) {
            headline = "ไม่มีโน้ตให้ตัดสิน"; details = ""; clean = null
        } else if (r.timingJudged) {
            clean = sum.isClean(learning.config.cleanRun)
            headline = (if (clean) "เล่นสะอาด · " else "") + "โน้ตถูก ${(sum.noteAccuracy * 100).roundToInt()}% · ตรงจังหวะ ${(sum.fractionWithinTiming * 100).roundToInt()}%"
            details = "ถูก ${r.count(NoteResult.CORRECT)} · เร็วไป ${r.count(NoteResult.EARLY)} · ช้าไป ${r.count(NoteResult.LATE)} · " +
                "ไม่ได้เล่น ${r.count(NoteResult.MISSED)} · กดผิด ${r.wrongPresses.size} · สะดุด ${r.stumbles} ครั้ง · ${r.setup.bpm.roundToInt()} bpm"
        } else {
            clean = null
            headline = "ถูกตั้งแต่ครั้งแรก ${sum.correctNotes}/${sum.expectedNotes} (${(sum.noteAccuracy * 100).roundToInt()}%)"
            details = "แก้หลังกดผิด ${r.count(NoteResult.CORRECTED)} · กดผิด ${r.wrongPresses.size} · หยุดนานเกิน 1 จังหวะ ${r.stumbles} ครั้ง" +
                " (โหมดรอไม่ตัดสินจังหวะ)"
        }
        return ResultUi(headline, details, clean, coach, progressLine)
    }

    private fun coachLines(s: Score, r: PlayResult, slowerBpm: Int, newBpm: Int?, helper: Boolean, drill: Boolean = false): List<CoachLine> {
        val out = mutableListOf<CoachLine>()
        val unknownLatency = latencies.values.all { it == 0.0 }
        for (a in Coach.advise(r, slowerBpm)) {
            if (drill) {
                // Drills keep their own settings: advice only, no buttons that change the section, hands or tempo.
                when (a) {
                    is Advice.HandAlone -> out += CoachLine("ผิดเฉพาะมือ${if (a.hand == Hand.LEFT) "ซ้าย" else "ขวา"} ลองดูโน้ตของมือนั้นช้าๆ ก่อนเล่นรอบต่อไป")
                    is Advice.SlowDown -> out += CoachLine("พลาดหลายโน้ต ลองแบบฝึกในโหมด \"รอ\" ของบทนี้ก่อน แล้วค่อยกลับมา")
                    is Advice.Chain, Advice.Clean -> Unit
                    else -> Unit
                }
                if (a !is Advice.TroubleNote && a !is Advice.ConsistentlyOff) continue
            }
            out += when (a) {
                Advice.Clean -> CoachLine(if (r.setup.toMeasure - r.setup.fromMeasure < s.measures - 1) "ท่อนนี้เล่นสะอาดแล้ว ลองท่อนถัดไป หรือเล่นคร่อมรอยต่อกับท่อนข้างๆ" else "เล่นสะอาดทั้งเพลง")
                is Advice.TroubleNote -> CoachLine("โน้ต ${a.pitch.name(NoteNameStyle.BOTH, withOctave = true)} พลาดบ่อย เพิ่มเข้ารายการทบทวนแล้ว (ฝึกได้ที่หน้า \"วันนี้\")")
                is Advice.ConsistentlyOff -> {
                    val dir = if (a.early) "เร็วกว่า" else "ช้ากว่า"
                    CoachLine(
                        if (unknownLatency) "ส่วนใหญ่กด${dir}จังหวะเท่าๆ กัน (ราว ${kotlin.math.abs(a.meanOffsetMs)} ms) อาจเป็นความหน่วงของเครื่อง ลองวัดความหน่วงที่หน้า \"ต่อเปียโน (MIDI)\" ก่อน"
                        else "ส่วนใหญ่กด${dir}จังหวะ ลองนับออกเสียงหรือเคาะเท้าไปด้วย",
                    )
                }
                is Advice.Chain -> CoachLine("พลาดตรงรอยต่อบาร์ ${a.fromMeasure + 1}–${a.toMeasure + 1} ลองวนเล่นคร่อมสองบาร์นี้", ResultAction.Loop(a.fromMeasure, a.toMeasure))
                is Advice.HandAlone -> {
                    val name = if (a.hand == Hand.LEFT) "ซ้าย" else "ขวา"
                    CoachLine("ผิดเฉพาะมือ$name ลองฝึกมือ${name}คนเดียวก่อน", ResultAction.Hands(if (a.hand == Hand.LEFT) HandChoice.LEFT else HandChoice.RIGHT))
                }
                is Advice.SlowDown -> CoachLine("ยังเร็วไปสำหรับตอนนี้ ลดเหลือ ${a.bpm} bpm ก่อน", ResultAction.Tempo(a.bpm))
            }
        }
        if (!drill) r.problemMeasures.firstOrNull()?.let { m ->
            if (out.none { it.action is ResultAction.Loop } && r.setup.fromMeasure != r.setup.toMeasure) {
                out += CoachLine("บาร์ ${m + 1} พลาดมากที่สุด ฝึกบาร์นี้ช้าๆ แยกก่อน แล้วค่อยกลับมาเล่นทั้งท่อน", ResultAction.Loop(m, m))
            }
        }
        if (newBpm != null) {
            out += CoachLine(if (newBpm > r.setup.bpm) "เล่นสะอาด 3 รอบติด เพิ่มความเร็วเป็น $newBpm bpm" else "พลาด 2 รอบติด ลดความเร็วกลับเป็น $newBpm bpm")
        }
        if (helper) out += CoachLine(
            if (_ui.value.options.falling && !fixed) "รอบนี้เล่นแบบโน้ตตก (ไม่ได้อ่านโน้ตดนตรี) จึงไม่นับเป็นการผ่านเพลง สลับกลับเป็นโน้ตดนตรีเมื่อพร้อม"
            else "รอบนี้เปิดชื่อโน้ตไว้ จึงไม่นับเป็นการผ่านเพลง",
        )
        if (!r.timingJudged && (r.summary?.noteAccuracy ?: 0.0) >= 0.95) {
            out += CoachLine("อ่านโน้ตได้แม่นแล้ว ลองโหมด \"ตามจังหวะ\" ดู")
        }
        return out
    }

    private suspend fun saveAttempt(s: Score, j: PlayAlongJudge, r: PlayResult, helper: Boolean) {
        val sum = r.summary ?: return
        val events = j.expected.map { n ->
            val o = r.outcomes[n.key]
            AttemptEvent(
                expectedPitch = n.midi,
                actualPitch = hitPitch[n.key],
                expectedOnsetMs = ((n.beat - j.startBeat) * j.beatMs).toLong(),
                offsetMs = o?.offsetMs,
            )
        } + r.wrongPresses.map { AttemptEvent(expectedPitch = null, actualPitch = it.midi, expectedOnsetMs = ((it.beat - j.startBeat) * j.beatMs).toLong()) }
        learning.saveAttempt(
            exerciseId = "piece:${s.id}:${r.setup.mode}",
            paramsJson = """{"from":${r.setup.fromMeasure},"to":${r.setup.toMeasure},"bpm":${r.setup.bpm},"toleranceMs":${r.setup.toleranceMs}}""",
            startedAt = runStartedAt,
            tempoBpm = if (r.timingJudged) r.setup.bpm else null,
            noteAccuracy = sum.noteAccuracy,
            timingWithinTol = if (r.timingJudged) sum.fractionWithinTiming else null,
            clean = if (r.timingJudged) sum.isClean(learning.config.cleanRun) else null,
            helperUses = if (helper) 1 else 0,
            events = events,
            sessionId = services.session.currentId(),
            hand = r.setup.hands.joinToString("+") { it.name },
            stumbles = r.stumbles,
        )
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun backToSetup() {
        if (_ui.value.phase == Phase.RESULT) _ui.update { it.copy(phase = Phase.IDLE, result = null, results = emptyMap()) }
    }

    override fun onCleared() {
        onStop()
        scheduler.shutdown()
    }

    companion object {
        /** Exam sight-reading: time to look at the music first (ABRSM gives half a minute). */
        const val PREVIEW_SECONDS = 30

        /** Timing tolerance follows the piece level: L1-L3 stage 1, L4-L5 stage 2, L6+ stage 3 (learning-system.md 4 and 6.2). */
        fun stageOf(s: Score): Int = when (s.level.removePrefix("L").toIntOrNull() ?: 1) {
            in 1..3 -> 1
            in 4..5 -> 2
            6 -> 3
            else -> 4
        }
    }
}
