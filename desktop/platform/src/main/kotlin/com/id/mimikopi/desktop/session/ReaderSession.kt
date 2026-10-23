package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.exam.ExamPart
import com.id.mimikopi.core.exam.ExamSitting
import com.id.mimikopi.core.exam.Exams
import com.id.mimikopi.core.exam.Grade
import com.id.mimikopi.core.exam.PartMark
import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.session.PlanContent
import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.playalong.Advice
import com.id.mimikopi.core.playalong.Coach
import com.id.mimikopi.core.playalong.Feedback as JudgeFeedback
import com.id.mimikopi.core.playalong.HeroicPlan
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.playalong.PieceRules
import com.id.mimikopi.core.playalong.PlayAlongJudge
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.playalong.PlayResult
import com.id.mimikopi.core.playalong.PlaySetup
import com.id.mimikopi.core.playalong.StudyEvidence
import com.id.mimikopi.core.practice.PedalCheck
import com.id.mimikopi.core.practice.TimingRules
import com.id.mimikopi.core.practice.isClean
import com.id.mimikopi.core.progress.Benchmarks
import com.id.mimikopi.core.progress.CheckMetrics
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.Mistakes
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillLevel
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.MeterPulse
import com.id.mimikopi.core.score.NoteKey
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.StageGenerators
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.audio.NoteScheduler
import com.id.mimikopi.desktop.learning.AttemptEvent
import com.id.mimikopi.desktop.learning.SettingKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.roundToInt

/** What the score player shows and how it was opened (the Android reader's navigation arguments). */
data class ReaderArgs(
    val pieceId: String? = null,
    val hand: HandChoice? = null,
    val lessonId: String? = null,
    val drill: Int? = null,
    val check: String? = null,
    val exam: String? = null,
    val part: ExamPart? = null,
    val newReading: Int? = null,
    /** Two-hand solo practice on the material of this solo level (PlanContent.solo). */
    val solo: Int? = null,
    val benchmark: Int? = null,
    /** A Heroic plan target id (with [pieceId]). */
    val target: String? = null,
    val via: String? = null,
    /** The lesson and drill a piece was opened from (its runs then count for that lesson's history). */
    val originLesson: String? = null,
    val originDrill: Int? = null,
)

/**
 * Score reader and play-along: wait and tempo modes, per-note judging, section loops, separate hands with the
 * program playing the other hand, tempo ramp, demo playback, problem spots, Heroic plan targets, level tests and
 * exam parts. A port of the Android ReaderViewModel with the same core judge and rules and the same saved rows.
 *
 * Timing: the count-in clicks come from the native metronome and fix the downbeat on the System.nanoTime clock (from
 * the output's frame timestamps), every input event carries its arrival time on that clock, and each input source's
 * measured latency for the current output path is subtracted once.
 */
class ReaderSession(
    private val services: Services,
    args: ReaderArgs,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val pieces = services.pieces
    private val learning = services.learning
    private val hub = services.hub
    private val piano = services.piano
    private val scheduler = NoteScheduler(piano)
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Source {
        data class Piece(val id: String, val hand: HandChoice?) : Source
        data class LessonDrill(val lesson: Lesson, val index: Int) : Source { val drill: Drill get() = lesson.drills[index] }
        data class NewReading(val level: Int) : Source
        /** Practice from the Today plan or a skill page on app-made material (two-hand solo); nothing is passed. */
        data class Practice(val drill: Drill, val log: String) : Source
        data class Benchmark(val level: Int) : Source
        data class Check(val level: SkillLevel) : Source { val drill: Drill get() = (level.run as CheckRun.ScoreRun).drill }
        data class Exam(val sitting: String, val part: ExamPart) : Source
    }

    private val studyTarget = HeroicPlan.target(args.target)?.takeIf { it.piece == args.pieceId }
    private val via: String? = args.via

    data class LoadedPiece(val score: Score, val layout: ScoreLayout, val keyRange: IntRange)

    val source: Source = when {
        args.pieceId != null -> Source.Piece(args.pieceId, args.hand)
        args.lessonId != null -> learning.lesson(args.lessonId)
            ?.let { l -> args.drill?.takeIf { it in l.drills.indices }?.let { Source.LessonDrill(l, it) } } ?: Source.Piece("", null)
        args.check != null -> SkillBook.parseKey(args.check)?.takeIf { it.run is CheckRun.ScoreRun }?.let { Source.Check(it) } ?: Source.Piece("", null)
        args.exam != null && args.part != null -> Source.Exam(args.exam, args.part)
        args.solo != null -> PlanContent.solo(args.solo)?.let { Source.Practice(it, "solo") } ?: Source.Piece("", null)
        args.newReading != null -> Source.NewReading(args.newReading.coerceIn(1, 5))
        else -> Source.Benchmark((args.benchmark ?: 1).coerceIn(1, Benchmarks.MAX_LEVEL))
    }

    /** Library pieces keep options and progress; drills, new reading and benchmarks use fixed settings. */
    val fixed: Boolean get() = source !is Source.Piece

    private val _piece = MutableStateFlow<LoadedPiece?>(null)
    val piece: StateFlow<LoadedPiece?> = _piece.asStateFlow()
    val score: Score? get() = _piece.value?.score

    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()
    val pressed = hub.pressed
    val nameStyle: NoteNameStyle get() = services.nameStyle.value

    private var seed = System.currentTimeMillis()
    private var benchLevel = (source as? Source.Benchmark)?.level ?: 1
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

    private var playScore: Score? = null
    var transposeKey: Int? = null
        private set

    private fun generate(): Score? = runCatching {
        playScore = null
        transposeKey = null
        when (val src = source) {
            is Source.Piece -> pieces.pieceOrExercise(src.id)
            is Source.Check -> scoreFor(src.drill)
            is Source.Exam -> examScore(src)
            is Source.LessonDrill -> scoreFor(src.drill)
            is Source.Practice -> scoreFor(src.drill)
            is Source.NewReading -> Score.from(ScoreGenerators.sightReading(newReadingDrill(src.level), seed))
            is Source.Benchmark -> Score.from(ScoreGenerators.sightReading(Benchmarks.sightReadingDrill(benchLevel), Benchmarks.sightReadingSeed(benchLevel)))
        }
    }.getOrNull()

    private fun scoreFor(d: Drill): Score? = when (d.type) {
        ExerciseType.E_FINGER_PATTERN -> Score.from(ScoreGenerators.fingerPatterns(d, seed))
        ExerciseType.E_RHYTHM_TAP -> Score.from(ScoreGenerators.rhythm(d, seed))
        ExerciseType.E_READ_PIECE -> Score.from(ScoreGenerators.read(d, seed))
        ExerciseType.E_COMP_PATTERN -> Score.from(ScoreGenerators.comp(d, seed))
        ExerciseType.E_TRANSPOSE -> {
            val from = d.keys.firstOrNull() ?: 0
            val targets = d.targetKeys.ifEmpty { listOf(1) }
            val to = targets[kotlin.random.Random(seed).nextInt(targets.size)]
            val (original, moved) = StageGenerators.transposePair(from, to, seed)
            playScore = Score.from(moved)
            transposeKey = to
            Score.from(original)
        }
        ExerciseType.E_PIECE -> pieces.pieceOrExercise(d.pieces.first())
        else -> null
    }

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
            ExamPart.SIGHT -> Score.from(Exams.sightPiece(grade, seed))
            ExamPart.AURAL -> null
        }
    }

    private fun examTaken(): Boolean {
        val src = source as? Source.Exam ?: return false
        return exam()?.first?.mark(src.part) != null
    }

    private fun examClosed(): Boolean = exam()?.first?.let { it.abandoned || it.day != LocalDate.now().toString() } == true

    private var sightNeedsLook = false

    private fun newSightLook(): Boolean {
        val src = source as? Source.Exam ?: return false
        if (src.part != ExamPart.SIGHT || !sightNeedsLook || examTaken()) return false
        sightNeedsLook = false
        seed = System.currentTimeMillis()
        loadPiece()
        _ui.update { it.copy(phase = Phase.IDLE, result = null, results = emptyMap(), fallingFeedback = emptyList()) }
        startPreview()
        return true
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
            is Source.Practice -> src.drill.bpm
            is Source.NewReading -> _ui.value.bpm
            is Source.Benchmark -> 60
            is Source.Piece, is Source.Exam -> s.tempo
        }
        _ui.update { it.copy(section = 0 until s.measures, bpm = bpm, targetBpm = bpm, results = emptyMap(), wrongMarks = emptyList(), fallingFeedback = emptyList(), pendingSectionStart = null) }
    }

    private fun fixedOptions(saved: ReaderOptions): ReaderOptions = when (val src = source) {
        is Source.LessonDrill, is Source.Check, is Source.Practice -> {
            val d = when (src) { is Source.Check -> src.drill; is Source.Practice -> src.drill; else -> (src as Source.LessonDrill).drill }
            ReaderOptions(
                mode = if (d.mode == "WAIT") PlayMode.WAIT else PlayMode.TEMPO,
                hands = runCatching { HandChoice.valueOf(d.hand) }.getOrDefault(HandChoice.RIGHT),
                playOtherHand = d.type == ExerciseType.E_PIECE || d.type == ExerciseType.E_COMP_PATTERN || d.backing,
                metronome = true, useRamp = false, showKeyboard = saved.showKeyboard, fallingKeyLabels = saved.fallingKeyLabels,
            )
        }
        is Source.NewReading -> saved.newReadingOptions(src.level)
        is Source.Benchmark -> ReaderOptions(PlayMode.TEMPO, if (benchLevel == 2) HandChoice.LEFT else if (benchLevel == 5) HandChoice.BOTH else HandChoice.RIGHT,
            playOtherHand = false, metronome = true, useRamp = false, showKeyboard = saved.showKeyboard)
        is Source.Piece -> saved.let { o -> src.hand?.let { o.copy(hands = it) } ?: o }
        is Source.Exam -> ReaderOptions(PlayMode.TEMPO,
            if (src.part == ExamPart.SIGHT) exam()?.second?.let { runCatching { HandChoice.valueOf(Exams.sightHand(it)) }.getOrNull() } ?: HandChoice.RIGHT else HandChoice.BOTH,
            playOtherHand = false, metronome = true, useRamp = false, showKeyboard = saved.showKeyboard)
    }

    fun title(): String = when (val src = source) {
        is Source.Piece -> score?.title ?: ""
        is Source.LessonDrill -> "${src.lesson.id} · ${src.drill.title}" + (transposeKey?.let { tr(" · เล่นในคีย์ ${Keys.name(it)}", " · played in ${Keys.name(it)}", " · ${Keys.name(it)}で演奏") } ?: "")
        is Source.NewReading -> tr("อ่านโน้ตใหม่", "Sight-reading", "初見")
        is Source.Practice -> src.drill.title
        is Source.Benchmark -> tr("วัดผล: อ่านโน้ตใหม่", "Measure: sight-reading", "測定：初見")
        is Source.Check -> tr("ทดสอบ · ${src.level.skill.title}", "Test · ${src.level.skill.title}", "テスト · ${src.level.skill.title}")
        is Source.Exam -> "สอบ ${exam()?.second?.name ?: ""} · ${src.part.title}"
    }

    private var holding = false
    private var pianoHeld = false
    private var runJob: Job? = null
    private var previewJob: Job? = null
    private var stoppedEarly = false
    private var studyInterrupted = false
    private var judge: PlayAlongJudge? = null
    private var latencies: Map<InputSource, Double> = emptyMap()
    private var helperUsedThisRun = false
    private val originLesson = args.originLesson?.let(learning::lesson)
    private val originDrill = args.originDrill
    private var runStartedAt = 0L
    private val runInputSources = mutableSetOf<InputSource>()
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
        val options = services.db.get(SettingKeys.READER_OPTIONS)
            ?.let { runCatching { json.decodeFromString(ReaderOptions.serializer(), it) }.getOrNull() } ?: ReaderOptions()
        _ui.update { it.copy(options = fixedOptions(options)) }
        refreshProgress()
        studyTarget?.let { t -> _ui.update { it.withStudyTarget(t) } }
        applyRampTempo()
        scope.launch { hub.events.collect(::onInput) }
    }

    private fun startPreview() {
        val src = source as? Source.Exam ?: return
        if (src.part != ExamPart.SIGHT || previewJob != null || examTaken() || score == null || _ui.value.phase != Phase.IDLE) return
        if (examClosed()) {
            _ui.update { it.copy(message = CLOSED_EXAM) }
            return
        }
        previewJob = scope.launch {
            for (left in PREVIEW_SECONDS downTo 1) {
                _ui.update { it.copy(previewLeft = left) }
                delay(1000)
            }
            _ui.update { it.copy(previewLeft = null) }
            previewJob = null
            if (holding && _ui.value.phase == Phase.IDLE) start()
        }
    }

    fun onStart() {
        if (!newSightLook()) startPreview()
        if (holding) return
        holding = true
        pianoHeld = piano.acquire()
    }

    fun onStop() {
        previewJob?.cancel()
        previewJob = null
        if (_ui.value.previewLeft != null) _ui.update { it.copy(previewLeft = null) }
        val src = source
        if (src is Source.Exam) {
            judge = null
            if (src.part == ExamPart.SIGHT && !examTaken()) sightNeedsLook = true
        }
        stop()
        if (!holding) return
        holding = false
        hub.releaseAll()
        if (pianoHeld) piano.release()
        pianoHeld = false
    }

    /** The page is closed for good. */
    fun dispose() {
        onStop()
        scheduler.shutdown()
    }

    // ---------------- options ----------------

    fun setMode(mode: PlayMode) = updateOptions { it.copy(mode = mode) }
    fun setHands(choice: HandChoice) = updateOptions { it.copy(hands = choice) }
    fun setPlayOtherHand(on: Boolean) { if (source !is Source.Exam) updateOptions { it.copy(playOtherHand = on) } }
    fun setMetronome(on: Boolean) = updateOptions { it.copy(metronome = on) }
    fun setUseRamp(on: Boolean) = updateOptions { it.copy(useRamp = on) }
    fun setShowKeyboard(on: Boolean) = updateOptions(duringRun = true) { it.copy(showKeyboard = on) }
    fun setHideNotes(on: Boolean) = updateOptions { it.copy(hideNotes = on) }
    fun setLoopListen(on: Boolean) = updateOptions { it.copy(loopListen = on) }
    fun setFalling(on: Boolean) = updateOptions { it.copy(falling = on) }
    fun setFallingKeyLabels(on: Boolean) = updateOptions(duringRun = true) { it.copy(fallingKeyLabels = on) }

    private fun updateOptions(duringRun: Boolean = false, change: (ReaderOptions) -> ReaderOptions) {
        if (!duringRun && !editable()) return
        val before = _ui.value.options
        val next = before.constrainReadingChange(change(before), fixed, source is Source.NewReading)
        _ui.update { it.copy(options = next) }
        services.appScope.launch {
            val db = services.db
            val stored = if (fixed) (db.get(SettingKeys.READER_OPTIONS)?.let { runCatching { json.decodeFromString(ReaderOptions.serializer(), it) }.getOrNull() }
                ?: ReaderOptions()).copy(showKeyboard = next.showKeyboard, pageView = next.pageView, paperSize = next.paperSize, fallingKeyLabels = next.fallingKeyLabels) else next
            db.put(SettingKeys.READER_OPTIONS, json.encodeToString(ReaderOptions.serializer(), stored))
        }
        if (!duringRun) applyRampTempo()
    }

    fun changeBpm(delta: Int) {
        if (!editable() || fixed && source !is Source.NewReading) return
        val s = score ?: return
        if (!fixed && _ui.value.options.useRamp) updateOptions { it.copy(useRamp = false) }
        _ui.update { it.copy(bpm = (it.bpm + delta).coerceIn(30, maxOf(s.tempo * 2, 160))) }
    }

    /** Bars are chosen by clicking: first click = start (and end), second click = end. */
    fun onMeasureTap(m: Int) {
        if (!editable() || fixed) return
        val start = _ui.value.pendingSectionStart
        if (start == null) {
            _ui.update { it.copy(section = m..m, pendingSectionStart = m, result = null, phase = Phase.IDLE, results = emptyMap(), fallingFeedback = emptyList()) }
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
        _ui.update { it.copy(section = from..to, pendingSectionStart = null, phase = Phase.IDLE, result = null, results = emptyMap(), fallingFeedback = emptyList()) }
        applyRampTempo()
    }

    fun applyAction(a: ResultAction) {
        if (fixed || _ui.value.phase != Phase.RESULT) return
        val s = score ?: return
        _ui.update { a.prepare(it, s.measures) }
        start()
    }

    /** Note names are a counted helper: a run that used them does not count toward passing. */
    fun toggleNames() {
        val on = !_ui.value.showNames
        _ui.update { it.copy(showNames = on) }
        if (on) {
            if (_ui.value.phase == Phase.PLAYING || _ui.value.phase == Phase.COUNT_IN) helperUsedThisRun = true
            services.appScope.launch {
                val n = (services.db.get(SettingKeys.HELPER_NOTE_NAMES_COUNT)?.toIntOrNull() ?: 0) + 1
                services.db.put(SettingKeys.HELPER_NOTE_NAMES_COUNT, n.toString())
            }
        }
    }

    fun editable() = _ui.value.phase == Phase.IDLE || _ui.value.phase == Phase.RESULT

    private fun rampKey(): String {
        val u = _ui.value
        return PieceRules.rampKey(u.options.hands.hands, u.section.first, u.section.last)
    }

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

    /** Tests: the downbeat of the running tempo run on System.nanoTime, and its quarter-note length. */
    internal val clockForTest: Pair<Long, Long>? get() = if (clockRunning) clockT0 to clockBeatNanos else null

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
            _ui.update { it.copy(message = tr("สอบส่วนนี้ไปแล้ว ผลนับครั้งแรกเท่านั้น", "You already took this part; only the first try counts", "この部は受験済みです。1回目だけが数えられます")) }
            return
        }
        if (examClosed()) {
            _ui.update { u -> u.copy(message = CLOSED_EXAM) }
            return
        }
        if (_ui.value.options.mode == PlayMode.TEMPO && !services.hasCable()) {
            _ui.update { it.copy(message = com.id.mimikopi.core.input.TimingInput.NEED_CABLE) }
            return
        }
        if (newSightLook()) return
        stoppedEarly = false
        studyInterrupted = false
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
            toleranceMs = learning.toleranceMs((source as? Source.LessonDrill)?.lesson?.stage ?: stageOf(s), u.bpm.toDouble(),
                benchmark = source is Source.Benchmark || source is Source.Check || source is Source.Exam),
            latencyMs = 0,
            anyKey = (source as? Source.LessonDrill)?.drill?.type == ExerciseType.E_RHYTHM_TAP,
        )
        val j = PlayAlongJudge(s, setup)
        if (j.expected.isEmpty()) {
            _ui.update { it.copy(message = tr("ท่อนนี้ไม่มีโน้ตของมือที่เลือก ลองเลือกมืออื่นหรือท่อนอื่น", "This section has no notes for the chosen hand; try another hand or section", "この区間には選んだ手の音符がありません。別の手か区間を選んでください")) }
            return
        }
        judge = j
        hitPitch = mutableMapOf()
        pedalEvents.clear()
        chordHits.clear()
        chordBeats = j.expected.groupBy { it.beat }.filterValues { it.size >= 3 }.keys
        helperUsedThisRun = u.showNames || (u.options.falling && !fixed)
        runStartedAt = System.currentTimeMillis()
        runInputSources.clear()
        _ui.update { it.copy(results = emptyMap(), wrongMarks = emptyList(), fallingFeedback = emptyList(), result = null, pendingSectionStart = null, message = null) }
        runJob?.cancel()
        latencies = InputSource.entries.associateWith { services.latencyMs(it) ?: 0.0 }
        runJob = scope.launch { if (setup.mode == PlayMode.TEMPO) runTempo(j) else runWait(j) }
    }

    /** Stops a run or the demo (Space / Esc / the stop button). A stopped run with notes played still shows its result. */
    fun stop() {
        val phase = _ui.value.phase
        runJob?.cancel()
        runJob = null
        scheduler.clear()
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        clockRunning = false
        val j = judge
        judge = null
        if (j != null && phase == Phase.PLAYING && j.outcomes().values.any { it.result != NoteResult.PENDING }) {
            studyInterrupted = true
            stoppedEarly = j.outcomes().values.any { it.result == NoteResult.PENDING }
            finish(j)
        } else if (phase != Phase.RESULT) {
            if ((source as? Source.Exam)?.part == ExamPart.SIGHT && (phase == Phase.COUNT_IN || phase == Phase.PLAYING)) sightNeedsLook = true
            _ui.update { it.copy(phase = Phase.IDLE, countIn = null, fallingFeedback = emptyList()) }
        }
    }

    private suspend fun runTempo(j: PlayAlongJudge) {
        val s = j.score
        val pulse = MeterPulse.of(s)
        val quarterNanos = (j.beatMs * 1e6).toLong()
        val beatNanos = (pulse.clickMillis(j.setup.bpm) * 1e6).toLong()
        val countIn = pulse.countInClicks
        val pattern = pulse.levels
        val clickBpm = pulse.nativeBpm(j.setup.bpm)
        _ui.update { it.copy(phase = Phase.COUNT_IN, countIn = countIn) }
        piano.drainMetronomeTicks()
        piano.setMetronome(true, clickBpm, pulse.ticksPerBeat, pattern, 0.9f)
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
        startClock(downbeat, quarterNanos, j.startBeat, j.endBeat)
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
                piano.setMetronome(false, clickBpm, pulse.ticksPerBeat, pattern, 0f)
                metronomeOff = true
            }
            val missed = j.advance(now / 1_000_000)
            if (missed.isNotEmpty()) {
                val at = System.nanoTime()
                val events = j.expected.filter { it.key in missed }.mapNotNull { FallingFeedback.judged(it.midi, NoteResult.MISSED, it.hand == Hand.LEFT, at) }
                _ui.update { u -> u.copy(results = u.results + missed.associateWith { NoteResult.MISSED }, fallingFeedback = u.fallingFeedback.withFeedback(events, at)) }
            }
            if (j.isFinished(now / 1_000_000)) break
            delay(16)
        }
        piano.setMetronome(false, clickBpm, pulse.ticksPerBeat, pattern, 0f)
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
        while (!j.isFinished(0)) delay(50)
        delay(600)
        judge = null
        finish(j)
    }

    private fun scheduleAccompaniment(j: PlayAlongJudge, from: Double, to: Double, anchorNanos: Long) {
        val held = j.score.sounding().associate { (it.eventId to it.midi) to it.duration }
        val durations = j.score.events.associate { it.id to it.duration }
        val beatNanos = j.beatMs * 1e6
        // Tempo mode: the click timeline is presentation time, so notes go out early by the output buffering.
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

    /** Demo: the program plays the section (both hands) at the current tempo with the cursor. */
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
        _ui.update { it.copy(phase = Phase.LISTENING, results = emptyMap(), fallingFeedback = emptyList(), result = null, pendingSectionStart = null) }
        runJob?.cancel()
        runJob = scope.launch {
            var start = t0
            while (true) {
                delay(((start + ((to - from) * beatNanos).toLong() - System.nanoTime()) / 1_000_000).coerceAtLeast(0) + 400)
                if (!_ui.value.options.loopListen) break
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
                if (phase == Phase.PLAYING) runInputSources += e.source
                val latency = latencies[e.source] ?: 0.0
                val beforeGroup = j.currentGroup()
                val atMs = ((e.timeNanos - latency * 1e6) / 1e6).toLong()
                when (val f = j.onNoteOn(e.note, atMs)) {
                    is JudgeFeedback.Hit -> {
                        hitPitch[f.key] = e.note
                        j.expected.firstOrNull { it.key == f.key }?.beat?.takeIf { it in chordBeats }?.let { b -> chordHits.putIfAbsent(b, atMs) }
                        val done = if (j.setup.mode == PlayMode.WAIT && beforeGroup != null && j.currentGroup() !== beforeGroup) beforeGroup else null
                        val at = System.nanoTime()
                        val event = FallingFeedback.judged(e.note, f.result, j.expected.firstOrNull { it.key == f.key }?.hand == Hand.LEFT, at)
                        _ui.update { u -> u.copy(results = u.results + (done?.associate { it.key to j.outcome(it.key).result } ?: mapOf(f.key to f.result)),
                            fallingFeedback = u.fallingFeedback.withFeedback(listOfNotNull(event), at)) }
                        if (done != null) onGroupDone(j, done.first().beat)
                    }
                    is JudgeFeedback.Wrong -> {
                        val mark = WrongMark(e.note, if (j.setup.mode == PlayMode.WAIT) waitBeat else f.press.beat, System.nanoTime())
                        _ui.update { u -> u.copy(wrongMarks = (u.wrongMarks + mark).takeLast(6),
                            fallingFeedback = u.fallingFeedback.withFeedback(listOf(FallingFeedback(e.note, FallingVerdict.WRONG, false, mark.atNanos)), mark.atNanos)) }
                    }
                    JudgeFeedback.Ignored -> Unit
                }
            }
            is InputEvent.NoteOff -> j.onNoteOff(e.note)
            is InputEvent.Sustain -> pedalEvents += ((e.timeNanos - (latencies[e.source] ?: 0.0) * 1e6) / 1e6).toLong() to e.down
            else -> Unit
        }
    }

    private val pedalEvents = mutableListOf<Pair<Long, Boolean>>()
    private val chordHits = mutableMapOf<Double, Long>()
    private var chordBeats: Set<Double> = emptySet()

    private fun pedalLine(): CoachLine? {
        val d = (source as? Source.LessonDrill)?.drill ?: return null
        if (!d.pedal) return null
        val r = PedalCheck.analyze(chordHits.toSortedMap().values.toList(), pedalEvents.toList())
        return CoachLine(when {
            !r.sawPedal -> tr("ไม่ได้รับสัญญาณแป้นเหยียบ จึงตรวจการเปลี่ยนแป้นไม่ได้ (แป้นของเปียโนบางรุ่นไม่ส่งผ่าน MIDI หรือยังไม่ได้เหยียบ)", "No pedal signal received, so pedal changes can't be checked (some pianos don't send the pedal over MIDI, or it wasn't pressed)", "ペダル信号がないためペダルの踏み替えを確認できません（MIDIでペダルを送らない機種か、踏んでいません）")
            r.changes == 0 -> tr("ยังไม่มีการเปลี่ยนคอร์ดให้ตรวจการเปลี่ยนแป้น", "No chord changes yet to check the pedal against", "ペダルを確認できるコードチェンジがまだありません")
            r.good == r.changes -> tr("เปลี่ยนแป้นตามคอร์ดได้ครบ ${r.changes} ครั้ง", "Pedal changed with every chord, ${r.changes} times", "コードごとにペダルを踏み替えられました（${r.changes}回）")
            else -> tr("เปลี่ยนแป้นทันคอร์ด ${r.good}/${r.changes} ครั้ง ยกแป้นขึ้นทันทีหลังกดคอร์ดใหม่ แล้วเหยียบลงอีกครั้ง", "Pedal in time ${r.good}/${r.changes}. Lift right after the new chord, then press again", "ペダルが間に合ったのは${r.good}/${r.changes}回。新しいコードを押した直後に上げ、また踏み込む")
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
        val seconds = if (result.hasNoteInput) (System.currentTimeMillis() - runStartedAt) / 1000 else 0L
        services.appScope.launch {
            services.progress.log(via ?: "song", seconds)
            services.progress.addMistakes(Mistakes.fromPlay(j, result, "piece", System.currentTimeMillis(), LocalDate.now().toString()))
        }
        val u = _ui.value
        val helper = helperUsedThisRun
        val interrupted = stoppedEarly || studyInterrupted
        val sources = runInputSources.toSet()
        val early = stoppedEarly
        val sessionId = services.session.currentId()
        scope.launch {
            val (update, studied, passedLessons) = withContext(io) {
                pieces.load()
                val before = pieces.progressOf(s.id)
                val update = PieceRules.afterRun(before, s, result, helper, u.options.useRamp && u.options.mode == PlayMode.TEMPO,
                    learning.config.tempoRamp, learning.config.cleanRun)
                val studied = HeroicPlan.record(update.progress, result, helper, interrupted)
                pieces.save(studied)
                saveAttempt(s, j, result, helper, sources, early, sessionId)
                enrolTrouble(j, result)
                val passedLessons = if (update.passedToday) learning.checkPieceLessons(pieces::lessonPiecePassed) else emptyList()
                Triple(update, studied, passedLessons)
            }
            val slower = pieces.progressOf(s.id).ramps[rampKey()]?.history?.lastOrNull() ?: (u.bpm * 0.9).roundToInt().coerceAtLeast(30)
            val coach = coachLines(s, result, slower, update.newBpm, helper)
            val studyLine = studyTarget?.let { t -> (studied.studyTargets[t.id] ?: StudyEvidence()).let { e ->
                when {
                    result.setup.fromMeasure != t.from || result.setup.toMeasure != t.to || result.setup.hands != t.hands || result.setup.mode != t.mode -> tr("บันทึกการฝึกแล้ว · ช่วง/มือ/โหมดที่เปลี่ยนไม่นับแทนเป้าหมายเดิม", "Practice saved · a changed section/hand/mode doesn't count for the original goal", "練習を保存しました · 区間・手・モードを変えた分は元の目標には数えません")
                    t.completed(e) -> tr("เป้าหมายท่อนนี้ทำได้ตามเกณฑ์แล้ว · กลับหน้าเพลงเพื่อฝึกขั้นถัดไป", "This section's goal is met · back to the piece for the next step", "この区間の目標を達成 · 曲ページに戻って次の段階へ")
                    t.mode == PlayMode.WAIT -> tr("บันทึกการอ่านช่วงนี้แล้ว · ยังไม่วัดจังหวะ", "Reading of this section saved · timing not measured yet", "この区間の読譜を保存 · リズムはまだ測っていません")
                    else -> tr("แผนท่อนนี้: สะอาดต่อเนื่อง ${e.streak}/3 รอบ · ทำได้ ${e.days.size}/2 วัน", "This section: clean in a row ${e.streak}/3 · done on ${e.days.size}/2 days", "この区間：連続クリーン${e.streak}/3回 · ${e.days.size}/2日達成")
                }
            } }
            val progressLine = studyLine ?: when {
                update.piecePassed && update.passedToday -> tr("ผ่านเพลงนี้แล้ว (เล่นสะอาด 3 รอบติดที่ความเร็วเป้าหมาย ใน 2 วันที่ต่างกัน)", "Piece passed (3 clean in a row at target tempo on 2 different days)", "この曲に合格（目標テンポで3回連続クリーン、別々の2日）")
                update.passedToday -> tr("วันนี้เล่นสะอาดครบ 3 รอบติดที่ความเร็วเป้าหมายแล้ว ต้องทำอีกครั้งในวันอื่นเพื่อผ่านเพลงนี้", "3 clean in a row at target tempo today. Do it again on another day to pass the piece", "今日、目標テンポで3回連続クリーン達成。別の日にもう一度で合格")
                else -> null
            }
            val lines = listOfNotNull(
                if (s.id.startsWith("heroic-polonaise-")) progressLine?.replace(tr("เพลงนี้", "This piece", "この曲"), tr("ฉบับฝึกท่อนหลักนี้", "This main-section practice edition", "このメイン部分の練習版")) else progressLine,
            ) + passedLessons.map { tr("ผ่านบท ${it.id} ${it.title} แล้ว", "Lesson ${it.title} passed", "レッスン「${it.title}」合格") }
            _ui.update {
                it.copy(phase = Phase.RESULT, result = resultUi(result, coach, lines.joinToString("\n").ifEmpty { null }), bpm = update.newBpm ?: it.bpm, countIn = null)
            }
            refreshProgress()
        }
    }

    private fun finishFixed(j: PlayAlongJudge) {
        val s = score ?: return
        val result = j.result()
        val helper = helperUsedThisRun
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
        newPieceNext = generated && (clean || runsOnPiece >= 3) && source !is Source.Benchmark
        val seconds = if (result.hasNoteInput) (System.currentTimeMillis() - runStartedAt) / 1000 else 0L
        if (source !is Source.Check && source !is Source.Exam) services.appScope.launch {
            services.progress.log(via ?: when (source) { is Source.NewReading -> "sight"; is Source.Practice -> source.log; is Source.Benchmark -> "check"; else -> "ladder" }, seconds)
        }
        services.appScope.launch {
            services.progress.addMistakes(Mistakes.fromPlay(j, result, "score", System.currentTimeMillis(), LocalDate.now().toString()))
        }
        val sources = runInputSources.toSet()
        val early = stoppedEarly
        val sessionId = services.session.currentId()
        scope.launch {
            val lines = mutableListOf<CoachLine>()
            var progressLine: String? = null
            when (val src = source) {
                is Source.Exam -> {
                    val sum = result.summary
                    val acc = sum?.noteAccuracy ?: 0.0
                    val timing = if (result.timingJudged) sum?.fractionWithinTiming ?: 0.0 else 0.0
                    val before = exam()?.first?.mark(src.part)
                    when {
                        before != null -> progressLine = tr("สอบส่วนนี้ไปแล้ว ได้ ${before.marks} คะแนน (นับครั้งแรกเท่านั้น)", "You already took this part and got ${before.marks} marks (only the first try counts)", "この部は受験済みで${before.marks}点でした（1回目だけ数えます）")
                        helper -> {
                            lines += CoachLine(tr("เปิดชื่อโน้ตระหว่างสอบ ส่วนนี้จึงยังไม่นับ ปิดชื่อโน้ตแล้วเล่นใหม่ได้", "Note names were on during the exam, so this part doesn't count yet. Turn them off and play again", "試験中に音名を表示したため、この部はまだ数えません。音名を消してもう一度"))
                            if (src.part == ExamPart.SIGHT) {
                                lines += CoachLine(tr("รอบใหม่ใช้โน้ตใหม่ และได้ดูโน้ต 30 วินาทีอีกครั้ง", "A new round uses new music, with another 30 seconds to look", "新しい回は新しい楽譜で、また30秒見られます"))
                                sightNeedsLook = true
                            }
                        }
                        else -> {
                            val marks = Exams.playedMarks(src.part, acc, timing, result.stumbles, played = result.hasNoteInput, complete = !early)
                            val mark = PartMark(marks, System.currentTimeMillis(), acc, timing, result.stumbles)
                            withContext(io) {
                                services.progress.recordExam(src.sitting, src.part, mark)
                                services.progress.log("check", seconds)
                            }
                            benchDone = true
                            progressLine = tr("${src.part.title}: $marks จาก ${src.part.max} คะแนน · ${Exams.partBand(src.part, marks).title}", "${src.part.title}: $marks of ${src.part.max} marks · ${Exams.partBand(src.part, marks).title}", "${src.part.title}：${src.part.max}点中${marks}点 · ${Exams.partBand(src.part, marks).title}")
                            Exams.remark(mark).lines().drop(1).forEach { lines += CoachLine(it) }
                        }
                    }
                    if (before != null) benchDone = true
                    _ui.update { it.copy(phase = Phase.RESULT, result = resultUi(result, lines.distinct(), progressLine), countIn = null) }
                    return@launch
                }
                is Source.Check -> {
                    val level = src.level
                    val m = CheckMetrics(
                        accuracy = result.summary?.noteAccuracy ?: 0.0,
                        timing = if (result.timingJudged) result.summary?.fractionWithinTiming ?: 0.0 else 0.0,
                    )
                    val verdict = level.judge(m)
                    val passed = verdict.passed && !helper
                    val p = services.progress
                    withContext(io) {
                        p.addCheck(CheckRecord(level.skill, level.level, LocalDate.now().toString(), passed, m, at = System.currentTimeMillis()))
                        p.log("check", seconds)
                    }
                    val status = SkillBook.status(level.skill, p.checks.value, LocalDate.now())
                    verdict.lines.forEach { lines += CoachLine(it) }
                    if (helper) lines += CoachLine(tr("เปิดตัวช่วยระหว่างทดสอบ ผลนี้จึงไม่นับ", "Helpers were on during the test, so this result doesn't count", "テスト中に補助を使ったため、この結果は数えません"))
                    progressLine = if (passed) tr("ผ่านแล้ว: ${level.canDo}", "Passed: ${level.canDo}", "合格：${level.canDo}")
                    else if (status.lockedToday) tr("ยังไม่ผ่าน · ลองครบ 2 ครั้งของวันนี้แล้ว ฝึกต่อแล้วลองใหม่วันอื่น", "Not passed yet · both tries used today; keep practising and try another day", "まだ不合格 · 今日の2回を使いました。練習して別の日に")
                    else tr("ยังไม่ผ่าน · ยังลองได้อีกครั้งวันนี้ (โน้ตใหม่)", "Not passed yet · one more try today (new music)", "まだ不合格 · 今日あと1回挑戦できます（新しい楽譜）")
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
                    val passedNow = withContext(io) {
                        learning.saveAttempt(
                            exerciseId = "${l.id}:${src.drill.type}:${src.index}",
                            paramsJson = """{"seed":$seed,"drill":${src.index},"lesson":"${l.id}","bpm":${result.setup.bpm},"mode":"${result.setup.mode}","completed":${!early},"inputPresent":${result.hasNoteInput},"inputSources":"${sources.map { it.name }.sorted().joinToString("+")}","from":${result.setup.fromMeasure},"to":${result.setup.toMeasure},"toleranceMs":${result.setup.toleranceMs}}""",
                            startedAt = runStartedAt, tempoBpm = if (result.timingJudged) result.setup.bpm else null,
                            noteAccuracy = result.summary?.noteAccuracy, timingWithinTol = if (result.timingJudged) result.summary?.fractionWithinTiming else null,
                            clean = if (result.timingJudged) clean else null, helperUses = if (helper) 1 else 0,
                            events = emptyList(), sessionId = sessionId,
                            hand = result.setup.hands.joinToString("+") { it.name }, stumbles = result.stumbles,
                        )
                        learning.recordDrill(l, src.index, met, helper)
                    }
                    if (met) metThisSitting = true
                    progressLine = when {
                        passedNow && met -> tr("ผ่านบท ${l.id} แล้ว บทถัดไปปลดล็อก", "Lesson passed, the next one is open", "レッスン合格、次のレッスンが開きました")
                        met -> if (l.pass.requiredDrills.size > 1) tr("ส่วนนี้ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านส่วนอื่นของบทในวันเดียวกันด้วย", "This part met the criteria today; the lesson's other parts must pass the same day too", "この部分は今日基準を満たしました。同じ日にレッスンの他の部分も合格が必要です") else tr("ผ่านเกณฑ์วันนี้แล้ว ต้องผ่านอีกครั้งในวันอื่นเพื่อผ่านบทนี้", "Met the criteria today; pass again on another day to pass this lesson", "今日基準を満たしました。別の日にもう一度でこのレッスンに合格")
                        required && need != null && !metThisSitting -> if (l.pass.inRow) tr("เล่นสะอาดติดกัน $cleanStreak/$need ครั้ง", "Clean in a row $cleanStreak/$need", "連続クリーン $cleanStreak/${need}回") else tr("เล่นสะอาดแล้ว $cleanCount/$need ครั้ง", "Clean $cleanCount/$need", "クリーン $cleanCount/${need}回")
                        !result.timingJudged && waitMin == null -> tr("โหมดรอใช้ฝึกอ่าน แบบทดสอบใช้โหมดตามจังหวะ", "Wait mode is for reading practice; tests use in-time mode", "待ちモードは読譜練習用、テストはリズムモードで行います")
                        else -> null
                    }
                    if (helper && required) lines += CoachLine(tr("รอบนี้เปิดชื่อโน้ตไว้ จึงไม่นับเป็นการผ่าน", "Note names were on this round, so it doesn't count as a pass", "この回は音名を表示していたため合格に数えません"))
                    if (early) lines += CoachLine(tr("หยุดก่อนจบ รอบนี้จึงไม่นับเป็นรอบสะอาดและไม่นำมาเทียบพัฒนาการ", "Stopped before the end, so this round isn't clean and isn't compared for progress", "最後まで弾かずに止めたため、この回はクリーンに数えず上達の比較にも使いません"))
                }
                is Source.Practice -> withContext(io) {
                    learning.saveAttempt(
                        exerciseId = "PRACTICE:${src.log}:${src.drill.type}:${src.drill.pattern}", paramsJson = """{"seed":$seed,"mode":"${result.setup.mode}","completed":${!stoppedEarly}}""",
                        startedAt = runStartedAt, tempoBpm = if (result.timingJudged) result.setup.bpm else null, noteAccuracy = result.summary?.noteAccuracy,
                        timingWithinTol = if (result.timingJudged) result.summary?.fractionWithinTiming else null,
                        clean = if (result.timingJudged) clean else null, helperUses = if (helper) 1 else 0,
                        events = emptyList(), sessionId = sessionId, stumbles = result.stumbles,
                    )
                }
                is Source.NewReading -> withContext(io) {
                    learning.saveAttempt(
                        exerciseId = "NEW_READING:L${src.level}", paramsJson = """{"seed":$seed,"level":${src.level}}""",
                        startedAt = runStartedAt, tempoBpm = if (result.timingJudged) result.setup.bpm else null, noteAccuracy = result.summary?.noteAccuracy,
                        timingWithinTol = if (result.timingJudged) result.summary?.fractionWithinTiming else null,
                        clean = if (result.timingJudged) clean else null, helperUses = if (helper) 1 else 0,
                        events = emptyList(), sessionId = sessionId, stumbles = result.stumbles,
                    )
                }
                is Source.Benchmark -> {
                    val level = benchLevel
                    if (clean && !helper && level < Benchmarks.MAX_LEVEL) {
                        withContext(io) { learning.updateBenchmark { it.copy(sightReadingLevel = maxOf(it.sightReadingLevel ?: 0, level)) } }
                        benchLevel = level + 1
                        newPieceNext = true
                        progressLine = tr("เล่นสะอาด ไปเรื่องถัดไป", "Played clean, moving to the next level", "クリーンに弾けました。次のレベルへ")
                    } else {
                        val reached = if (clean && !helper) level else level - 1
                        withContext(io) { learning.updateBenchmark { it.copy(sightReadingLevel = maxOf(it.sightReadingLevel ?: 0, reached)) } }
                        progressLine = tr("บันทึกผลแล้ว", "Result saved", "結果を保存しました")
                        benchDone = true
                    }
                }
                is Source.Piece -> Unit
            }
            withContext(io) { enrolTrouble(j, result) }
            pedalLine()?.let { lines += it }
            lines += coachLines(s, result, (result.setup.bpm * 0.9).roundToInt(), null, false, drill = true)
            _ui.update { it.copy(phase = Phase.RESULT, result = resultUi(result, lines.distinct(), progressLine), countIn = null) }
        }
    }

    private fun enrolTrouble(j: PlayAlongJudge, result: PlayResult) {
        val ids = result.troubleNotes.map { p ->
            val clef = if (j.expected.firstOrNull { it.pitch == p }?.hand == Hand.LEFT) "bass" else "treble"
            "note:$p:$clef"
        }
        if (ids.isEmpty()) return
        learning.enrol(ids)
        ids.forEach { learning.review(it, correct = false, responseMs = 0) }
    }

    /** Benchmark: the sight-reading part is finished. */
    var benchDone = false
        private set

    fun nextLabelIsNew(): Boolean = newPieceNext

    private fun resultUi(r: PlayResult, coach: List<CoachLine>, progressLine: String?): ResultUi {
        val sum = r.summary
        val headline: String
        val details: String
        val clean: Boolean?
        if (sum == null) {
            headline = tr("ไม่มีโน้ตให้ตัดสิน", "No notes to judge", "判定する音符がありません"); details = ""; clean = null
        } else if (r.timingJudged) {
            clean = sum.isClean(learning.config.cleanRun)
            headline = (if (clean) tr("เล่นสะอาด · ", "Played clean · ", "クリーン · ") else "") + tr("โน้ตถูก ${(sum.noteAccuracy * 100).roundToInt()}% · ตรงจังหวะ ${(sum.fractionWithinTiming * 100).roundToInt()}%", "Notes ${(sum.noteAccuracy * 100).roundToInt()}% · on time ${(sum.fractionWithinTiming * 100).roundToInt()}%", "音 ${(sum.noteAccuracy * 100).roundToInt()}% · リズム ${(sum.fractionWithinTiming * 100).roundToInt()}%")
            details = tr("ถูก ${r.count(NoteResult.CORRECT)} · เร็วไป ${r.count(NoteResult.EARLY)} · ช้าไป ${r.count(NoteResult.LATE)} · ", "Right ${r.count(NoteResult.CORRECT)} · early ${r.count(NoteResult.EARLY)} · late ${r.count(NoteResult.LATE)} · ", "正解 ${r.count(NoteResult.CORRECT)} · 早い ${r.count(NoteResult.EARLY)} · 遅い ${r.count(NoteResult.LATE)} · ") +
                tr("ไม่ได้เล่น ${r.count(NoteResult.MISSED)} · กดผิด ${r.wrongPresses.size} · สะดุด ${r.stumbles} ครั้ง · ${r.setup.bpm.roundToInt()} bpm", "not played ${r.count(NoteResult.MISSED)} · wrong presses ${r.wrongPresses.size} · stumbles ${r.stumbles} · ${r.setup.bpm.roundToInt()} bpm", "弾かなかった ${r.count(NoteResult.MISSED)} · 押し間違い ${r.wrongPresses.size} · つまずき ${r.stumbles}回 · ${r.setup.bpm.roundToInt()} bpm")
        } else {
            clean = null
            headline = tr("ถูกตั้งแต่ครั้งแรก ${sum.correctNotes}/${sum.expectedNotes} (${(sum.noteAccuracy * 100).roundToInt()}%)", "Right first time ${sum.correctNotes}/${sum.expectedNotes} (${(sum.noteAccuracy * 100).roundToInt()}%)", "1回目で正解 ${sum.correctNotes}/${sum.expectedNotes}（${(sum.noteAccuracy * 100).roundToInt()}%）")
            details = tr("แก้หลังกดผิด ${r.count(NoteResult.CORRECTED)} · กดผิด ${r.wrongPresses.size} · หยุดนานเกิน 1 จังหวะ ${r.stumbles} ครั้ง (โหมดรอไม่ตัดสินจังหวะ)", "Corrected after a wrong press ${r.count(NoteResult.CORRECTED)} · wrong presses ${r.wrongPresses.size} · paused longer than a beat ${r.stumbles} times (wait mode doesn't judge timing)", "間違えてから修正 ${r.count(NoteResult.CORRECTED)} · 押し間違い ${r.wrongPresses.size} · 1拍以上止まった ${r.stumbles}回（待ちモードはリズムを判定しません）")
        }
        return ResultUi(headline, details, clean, coach, progressLine)
    }

    private fun coachLines(s: Score, r: PlayResult, slowerBpm: Int, newBpm: Int?, helper: Boolean, drill: Boolean = false): List<CoachLine> {
        if (!r.hasNoteInput) return listOf(CoachLine(tr("รอบนี้ยังไม่มีกดโน้ต จึงไม่เพิ่มเวลาซ้อม ลองเริ่มอีกครั้งแล้วเล่นตามโน้ต", "No notes were played this round, so no practice time was added. Start again and play along", "この回は音が弾かれなかったため練習時間は加算されません。もう一度始めて楽譜どおりに弾いてください")))
        val out = mutableListOf<CoachLine>()
        for (a in Coach.advise(r, slowerBpm)) {
            if (drill) {
                when (a) {
                    is Advice.HandAlone -> out += CoachLine("ผิดเฉพาะมือ${if (a.hand == Hand.LEFT) tr("ซ้าย", "left", "左") else tr("ขวา", "right", "右")} ลองดูโน้ตของมือนั้นช้าๆ ก่อนเล่นรอบต่อไป")
                    is Advice.SlowDown -> out += CoachLine(tr("พลาดหลายโน้ต ลองแบบฝึกในโหมด \"รอ\" ของบทนี้ก่อน แล้วค่อยกลับมา", "Many notes missed. Try this lesson's \"wait\" drill first, then come back", "多くの音を逃しました。まずこのレッスンの「待ち」練習をしてから戻りましょう"))
                    else -> Unit
                }
                if (a !is Advice.TroubleNote && a !is Advice.ConsistentlyOff && a !is Advice.OctaveOff) continue
            }
            out += when (a) {
                Advice.Clean -> CoachLine(if (r.setup.toMeasure - r.setup.fromMeasure < s.measures - 1) tr("ท่อนนี้เล่นสะอาดแล้ว ลองท่อนถัดไป หรือเล่นคร่อมรอยต่อกับท่อนข้างๆ", "This section is clean. Try the next one, or play across the join with its neighbour", "この区間はクリーン。次の区間か、隣の区間とのつなぎ目を練習") else tr("เล่นสะอาดทั้งเพลง", "Whole piece played clean", "全曲クリーン"))
                is Advice.TroubleNote -> CoachLine(tr("โน้ต ${a.pitch.name(NoteNameStyle.BOTH, withOctave = true)} พลาดบ่อย เพิ่มเข้ารายการทบทวนแล้ว (ฝึกได้ที่หน้า \"วันนี้\")", "Note ${a.pitch.name(NoteNameStyle.BOTH, withOctave = true)} is often missed, added to your review list (practise it on \"Today\")", "${a.pitch.name(NoteNameStyle.BOTH, withOctave = true)}をよく間違えるので復習リストに追加しました（「今日」で練習）"))
                is Advice.OctaveOff -> CoachLine("กดถูกตัวแต่${if (a.higher) tr("สูง", "high", "高い") else tr("ต่ำ", "low", "低い")}ไปหนึ่งอ็อกเทฟหลายครั้ง ก่อนเริ่มหา C กลางบนเปียโน แล้ววางมือตามตำแหน่งในโน้ต")
                is Advice.ConsistentlyOff -> {
                    val dir = if (a.early) tr("เร็วกว่า", "early", "早め") else tr("ช้ากว่า", "late", "遅め")
                    CoachLine(tr("ส่วนใหญ่กด${dir}จังหวะ ลองนับออกเสียงหรือเคาะเท้าไปด้วย", "Mostly ${dir}; try counting aloud or tapping your foot", "ほとんど${dir}に押しています。声に出して数えるか足でリズムを取ってみて"))
                }
                is Advice.Chain -> CoachLine(tr("พลาดตรงรอยต่อบาร์ ${a.fromMeasure + 1}–${a.toMeasure + 1} ฝึกคร่อมรอยต่อช้าลงก่อน", "Mistakes at the join of bars ${a.fromMeasure + 1}–${a.toMeasure + 1}; practise across it slowly first", "${a.fromMeasure + 1}–${a.toMeasure + 1}小節のつなぎ目でミス。まずゆっくりつなぎを練習"), ResultAction.Loop(a.fromMeasure, a.toMeasure, slowerBpm))
                is Advice.HandAlone -> {
                    val name = if (a.hand == Hand.LEFT) tr("ซ้าย", "left", "左") else tr("ขวา", "right", "右")
                    CoachLine(tr("ผิดเฉพาะมือ$name ฝึกมือ${name}คนเดียวให้แม่นก่อน", "Mistakes only in the $name hand; practise the $name hand alone first", "${name}手だけミス。まず${name}手だけを正確に"), ResultAction.Hands(if (a.hand == Hand.LEFT) HandChoice.LEFT else HandChoice.RIGHT, slowerBpm))
                }
                is Advice.SlowDown -> CoachLine(tr("ยังเร็วไปสำหรับตอนนี้ ลดเหลือ ${a.bpm} bpm ก่อน", "Still too fast for now; drop to ${a.bpm} bpm first", "今はまだ速すぎます。まず${a.bpm} bpmに下げましょう"), ResultAction.Tempo(a.bpm))
            }
        }
        if (!drill) r.problemMeasures.firstOrNull()?.let { m ->
            if (out.none { it.action is ResultAction.Loop } && r.setup.fromMeasure != r.setup.toMeasure) {
                out += CoachLine(tr("บาร์ ${m + 1} พลาดมากที่สุด ฝึกบาร์นี้ช้าๆ ก่อนกลับมาเล่นทั้งท่อน", "Most mistakes in bar ${m + 1}; practise it slowly before the whole section", "${m + 1}小節目のミスが最多。区間全体の前にそこをゆっくり練習"), ResultAction.Loop(m, m, slowerBpm))
            }
        }
        if (newBpm != null) {
            out += CoachLine(if (newBpm > r.setup.bpm) tr("เล่นสะอาด 3 รอบติด เพิ่มความเร็วเป็น $newBpm bpm", "3 clean in a row, speeding up to $newBpm bpm", "3回連続クリーン、$newBpm bpmに上げます") else tr("พลาด 2 รอบติด ลดความเร็วกลับเป็น $newBpm bpm", "2 misses in a row, back down to $newBpm bpm", "2回続けてミス、$newBpm bpmに戻します"))
        }
        if (helper) out += CoachLine(
            if (_ui.value.options.falling && !fixed) tr("รอบนี้เล่นแบบโน้ตไหล (ไม่ได้อ่านโน้ตดนตรี) จึงไม่นับเป็นการผ่านเพลง สลับกลับเป็นโน้ตดนตรีเมื่อพร้อม", "This round used falling notes (not reading music), so it doesn't count as passing. Switch back to notation when ready", "この回は流れる音符で弾いた（楽譜を読んでいない）ため合格に数えません。準備ができたら楽譜に戻しましょう")
            else tr("รอบนี้เปิดชื่อโน้ตไว้ จึงไม่นับเป็นการผ่านเพลง", "Note names were on this round, so it doesn't count as passing the piece", "この回は音名を表示していたため曲の合格に数えません"),
        )
        if (!r.timingJudged && (r.summary?.noteAccuracy ?: 0.0) >= 0.95) out += CoachLine(tr("อ่านโน้ตได้แม่นแล้ว ลองโหมด \"ตามจังหวะ\" ดู", "Your reading is accurate now; try \"in time\" mode", "読譜は正確になりました。「リズム」モードを試してみて"))
        return out
    }

    private fun saveAttempt(s: Score, j: PlayAlongJudge, r: PlayResult, helper: Boolean, sources: Set<InputSource>, early: Boolean, sessionId: Long?) {
        val sum = r.summary ?: return
        val events = j.expected.map { n ->
            val o = r.outcomes[n.key]
            AttemptEvent(expectedPitch = n.midi, actualPitch = hitPitch[n.key], expectedOnsetMs = ((n.beat - j.startBeat) * j.beatMs).toLong(), offsetMs = o?.offsetMs)
        } + r.wrongPresses.map { AttemptEvent(expectedPitch = null, actualPitch = it.midi, expectedOnsetMs = ((it.beat - j.startBeat) * j.beatMs).toLong()) }
        learning.saveAttempt(
            exerciseId = originDrill?.let { index -> originLesson?.takeIf { s.id in it.drills.getOrNull(index)?.pieces.orEmpty() }
                ?.let { "${it.id}:${it.drills[index].type}:$index" } } ?: "piece:${s.id}:${r.setup.mode}",
            paramsJson = """{"from":${r.setup.fromMeasure},"to":${r.setup.toMeasure},"bpm":${r.setup.bpm},"toleranceMs":${r.setup.toleranceMs},"piece":"${s.id}","mode":"${r.setup.mode}","completed":${!early},"inputPresent":${r.hasNoteInput},"inputSources":"${sources.map { it.name }.sorted().joinToString("+")}"}""",
            startedAt = runStartedAt,
            tempoBpm = if (r.timingJudged) r.setup.bpm else null,
            noteAccuracy = sum.noteAccuracy,
            timingWithinTol = if (r.timingJudged) sum.fractionWithinTiming else null,
            clean = if (r.timingJudged) sum.isClean(learning.config.cleanRun) else null,
            helperUses = if (helper) 1 else 0,
            events = events,
            sessionId = sessionId,
            hand = r.setup.hands.joinToString("+") { it.name },
            stumbles = r.stumbles,
        )
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun backToSetup() {
        if (newSightLook()) return
        if (_ui.value.phase == Phase.RESULT) _ui.update { it.copy(phase = Phase.IDLE, result = null, results = emptyMap(), fallingFeedback = emptyList()) }
    }

    companion object {
        const val PREVIEW_SECONDS = 30
        private val CLOSED_EXAM: String get() = tr("การสอบนี้ยกเลิกแล้วหรือหมดวันแล้ว เริ่มสอบใหม่ได้ที่หน้าเกรด", "This exam was cancelled or the day ended. Start a new one on the Grade page", "この試験は中止されたか日付が変わりました。グレードページで新しく受けてください")

        /** Timing tolerance follows the piece level: L1-L3 stage 1, L4-L5 stage 2, L6 stage 3 (learning-system.md 4, 6.2). */
        fun stageOf(s: Score): Int = when (s.level.removePrefix("L").toIntOrNull() ?: 1) {
            in 1..3 -> 1
            in 4..5 -> 2
            6 -> 3
            else -> 4
        }
    }
}
