package com.id.mimikopi.desktop.ui.ear

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
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.core.playalong.PlayAlongJudge
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.playalong.PlaySetup
import com.id.mimikopi.core.practice.TimingMode
import com.id.mimikopi.core.practice.TimingRules
import com.id.mimikopi.core.practice.isClean
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.desktop.Services
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Choice(val label: String, val value: String)

sealed interface EarUi {
    data class Ready(val title: String, val count: Int) : EarUi
    data class Asking(
        val q: EarQuestion,
        val index: Int,
        val total: Int,
        val prompt: String,
        val choices: List<Choice>,
        val keysAnswer: Boolean,
        val marked: Set<Int>,
        val entered: List<Int>,
        val correct: Boolean?,
        val correction: String?,
        val rhythmState: String? = null,
    ) : EarUi
    data class Done(val headline: String, val lines: List<String>, val met: Boolean) : EarUi
}

/**
 * One ear-ladder rung's set of questions on the desktop (the same flow as the Android EarDrillViewModel): answers
 * come from buttons or from the MIDI keyboard; results update the rung's record and the ear items' repetition.
 */
class EarDrillModel(private val lesson: Lesson) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val learning = Services.learning
    private val hub = Services.hub
    private val scheduler = Services.scheduler
    private val drill = lesson.drills.first()
    private val level = EarLevel.valueOf(drill.level)
    private val count = drill.items

    private val _ui = MutableStateFlow<EarUi>(EarUi.Ready(lesson.title, count))
    val ui: StateFlow<EarUi> = _ui.asStateFlow()

    private var questions: List<EarQuestion> = emptyList()
    private var index = 0
    private var step = 0
    private var stepsAllRight = true
    private var mixProgOk = true
    private val itemResults = mutableListOf<Pair<String, Boolean>>()
    private val outcomes = mutableMapOf<Int, Boolean>()
    private var job: Job? = null

    init {
        scope.launch { hub.events.collect(::onInput) }
        scope.launch { _ui.collect { u -> if (u is EarUi.Asking && u.correct != null) outcomes[u.index - 1] = u.correct } }
    }

    fun dispose() {
        job?.cancel()
        scheduler.clear()
        scope.cancel()
    }

    fun start() {
        val weights = EarStats.weights(learning.earStats())
        val g = EarGenerator(System.currentTimeMillis(), weights)
        questions = List(count) { g.next(level, drill.ear) }
        index = 0
        itemResults.clear()
        outcomes.clear()
        show()
    }

    private fun current() = questions[index]

    private fun show() {
        step = 0
        mixProgOk = true
        stepsAllRight = true
        _ui.value = asking(current())
        if (current() !is EarQuestion.Rhythm) play()
    }

    private fun asking(q: EarQuestion, marked: Set<Int>? = null): EarUi.Asking {
        val (prompt, choices, keys) = stepSpec(q)
        val given = when (q) {
            is EarQuestion.Interval -> setOf(q.root)
            is EarQuestion.Melody -> setOf(q.notes.first())
            is EarQuestion.Mixed -> if (step >= 4) setOf(q.melody.first()) else emptySet()
            else -> emptySet()
        }
        return EarUi.Asking(q, index + 1, questions.size, prompt, choices, keys, marked ?: given, emptyList(), null, null)
    }

    private fun name(midi: Int): String = SpelledPitch.fromMidiSharp(midi).let { it.letter.name + SpelledPitch.accidentalSymbol(it.accidental) + it.octave }

    private fun stepSpec(q: EarQuestion): Triple<String, List<Choice>, Boolean> = when (q) {
        is EarQuestion.Higher -> Triple("เสียงที่สองสูงขึ้น ต่ำลง หรือเท่ากัน?",
            listOf(Choice("สูงขึ้น", "HIGHER"), Choice("ต่ำลง", "LOWER"), Choice("เท่ากัน", "SAME")).filter { it.value != "SAME" || drill.ear?.same != false }, false)
        is EarQuestion.Direction -> Triple("โน้ตตัวที่ ${step + 1} → ${step + 2} ไปทางไหน?",
            listOf(Choice("ขึ้น", "UP"), Choice("ลง", "DOWN"), Choice("ซ้ำ", "SAME")), false)
        is EarQuestion.Interval -> Triple(
            when (q.play) {
                IntervalPlay.TOGETHER -> "สองเสียงพร้อมกัน ห่างกันเท่าไร? (เสียงล่างคือ ${name(q.root)} หรือกดเสียงบนบนคีย์บอร์ด)"
                IntervalPlay.DOWN -> "เสียงที่สองต่ำลงเท่าไร? (เสียงแรกคือ ${name(q.root)} หรือกดเสียงที่สองบนคีย์บอร์ด)"
                IntervalPlay.UP -> "เสียงที่สองสูงขึ้นเท่าไร? (เสียงแรกคือ ${name(q.root)} หรือกดเสียงที่สองบนคีย์บอร์ด)"
            },
            (drill.ear?.intervals?.takeIf { it.isNotEmpty() }?.sorted() ?: if (q.level == EarLevel.E2) (1..7).toList() else (1..12).toList())
                .map { Choice(EarAnswers.INTERVAL_NAMES.getValue(it), "$it") }, true)
        is EarQuestion.Triad -> Triple("คอร์ดนี้เมเจอร์ (สว่าง) หรือไมเนอร์ (หม่น)?", listOf(Choice("เมเจอร์", "MAJOR"), Choice("ไมเนอร์", "MINOR")), false)
        is EarQuestion.Melody -> Triple("ฟังแล้วเล่นตามบนคีย์บอร์ด: โน้ตแรกให้แล้ว เล่นต่ออีก ${q.notes.size - 1} ตัว", emptyList(), true)
        is EarQuestion.Rhythm -> Triple("ฟังจังหวะ ${q.bars} บาร์ แล้วเคาะตามในบาร์ถัดไป (กดคีย์ใดก็ได้)", emptyList(), false)
        is EarQuestion.Progression -> Triple("คอร์ดแรกคือ ${degreeLabel(q.key, q.degrees[0])} · คอร์ดที่ ${step + 2} คืออะไร?",
            progressionChoices(q.degrees).map { Choice(degreeLabel(q.key, it), it.name) }, false)
        is EarQuestion.Seventh -> Triple("คอร์ดเซเวนท์ชนิดไหน?",
            listOf(Choice("เมเจอร์ 7 (maj7)", "MAJ7"), Choice("ไมเนอร์ 7 (m7)", "MIN7"), Choice("โดมิแนนท์ 7 (7)", "DOM7")), false)
        is EarQuestion.BassLine -> Triple("เบสของคอร์ดที่ ${step + 1} คือขั้นที่เท่าไรของคีย์ ${q.key.label}? (หรือกดเสียงเบสบนคีย์บอร์ด)",
            (1..7).map { Choice("$it", "$it") }, true)
        is EarQuestion.Mixed -> when (step) {
            0 -> Triple("ท่อนนี้อยู่คีย์อะไร? กดโน้ตหลักของคีย์ (โด) ที่อ็อกเทฟไหนก็ได้", emptyList(), true)
            in 1..3 -> Triple("คอร์ดแรกคือ ${degreeLabel(q.key, q.degrees[0])} · คอร์ดที่ ${step + 1} คืออะไร?",
                progressionChoices(q.degrees).map { Choice(degreeLabel(q.key, it), it.name) }, false)
            else -> Triple("เล่นทำนอง: โน้ตแรกให้แล้ว เล่นต่ออีก ${q.melody.size - 1} ตัว", emptyList(), true)
        }
    }

    private fun progressionChoices(degrees: List<Degree>): List<Degree> {
        val basic = listOf(Degree.I, Degree.IV, Degree.V, Degree.vi)
        return if (degrees.all { it in basic }) basic else Degree.entries
    }

    private fun degreeLabel(key: EarKey, d: Degree): String {
        val root = earNoteName(scaleNote(key.tonic, d.step), key)
        return "${d.name} (${root.letter.name}${SpelledPitch.accidentalSymbol(root.accidental)}${if (d.minor) "m" else ""})"
    }

    fun play() {
        val q = current()
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
        when (val q = ui.q) {
            is EarQuestion.Higher -> finish1(Compare.valueOf(value) == q.answer, "คำตอบ: ${when (q.answer) { Compare.HIGHER -> "สูงขึ้น"; Compare.LOWER -> "ต่ำลง"; Compare.SAME -> "เท่ากัน" }}")
            is EarQuestion.Direction -> stepAnswer(Dir.valueOf(value) == q.answers[step], q.answers.size, "ตัวที่ ${step + 1} → ${step + 2}: ${when (q.answers[step]) { Dir.UP -> "ขึ้น"; Dir.DOWN -> "ลง"; Dir.SAME -> "ซ้ำ" }}")
            is EarQuestion.Interval -> finish1(value.toInt() == q.semitones, "คำตอบ: ${EarAnswers.INTERVAL_NAMES.getValue(q.semitones)} (${name(q.root)} → ${name(q.target)})", setOf(q.root, q.target))
            is EarQuestion.Triad -> finish1(TriadQuality.valueOf(value) == q.quality, "คำตอบ: ${if (q.quality == TriadQuality.MAJOR) "เมเจอร์" else "ไมเนอร์"}", q.notes.toSet())
            is EarQuestion.Progression -> {
                val d = q.degrees[step + 1]
                itemResults += "ear:deg:${d.name}" to (Degree.valueOf(value) == d)
                stepAnswer(Degree.valueOf(value) == d, 3, "คอร์ดที่ ${step + 2}: ${degreeLabel(q.key, d)}", recordItems = false)
            }
            is EarQuestion.Seventh -> finish1(SeventhQuality.valueOf(value) == q.quality, "คำตอบ: ${when (q.quality) { SeventhQuality.MAJ7 -> "เมเจอร์ 7"; SeventhQuality.MIN7 -> "ไมเนอร์ 7"; SeventhQuality.DOM7 -> "โดมิแนนท์ 7" }}", q.notes.toSet())
            is EarQuestion.BassLine -> stepAnswer(value.toInt() == q.bassDegrees[step], 4, "เบสคอร์ดที่ ${step + 1}: ขั้นที่ ${q.bassDegrees[step]}")
            is EarQuestion.Mixed -> if (step in 1..3) { val d = q.degrees[step]; mixedStep(Degree.valueOf(value) == d, "คอร์ดที่ ${step + 1}: ${degreeLabel(q.key, d)}") }
            else -> Unit
        }
    }

    private fun onInput(e: InputEvent) {
        if (e !is InputEvent.NoteOn) return
        val ui = _ui.value as? EarUi.Asking ?: return
        if (!ui.keysAnswer || ui.correct != null) return
        when (val q = ui.q) {
            is EarQuestion.Interval -> if (e.note != q.root) finish1(e.note == q.target, "คำตอบ: ${EarAnswers.INTERVAL_NAMES.getValue(q.semitones)} (${name(q.root)} → ${name(q.target)})", setOf(q.root, q.target))
            is EarQuestion.Melody -> {
                val entered = ui.entered + e.note
                if (entered.size < q.notes.size - 1) _ui.value = ui.copy(entered = entered)
                else finish1(EarAnswers.melodyMatches(q.notes, entered), null, q.notes.toSet(), entered)
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
                        learning.review("ear:mix:mel", ok)
                        _ui.value = ui.copy(entered = entered, correct = ok && stepsAllRight, marked = q.melody.toSet())
                    }
                }
            }
            else -> Unit
        }
    }

    private fun stepAnswer(ok: Boolean, steps: Int, correction: String, recordItems: Boolean = true) {
        val ui = _ui.value as? EarUi.Asking ?: return
        if (!ok) stepsAllRight = false
        if (step + 1 < steps) {
            step++
            _ui.value = asking(ui.q).copy(correction = if (ok) null else "ไม่ใช่ · $correction")
            return
        }
        if (recordItems) finish1(stepsAllRight, if (ok) null else correction)
        else {
            ui.q.itemIds.forEach { id -> itemResults.lastOrNull { it.first == id }?.let { learning.review(id, it.second) } }
            val reveal = (ui.q as? EarQuestion.Progression)?.name?.let { "โปรเกรสชันนี้คือ $it" }
            _ui.value = ui.copy(correct = stepsAllRight, correction = if (ok) reveal else listOfNotNull(correction, reveal).joinToString(" · "))
        }
    }

    private fun mixedStep(ok: Boolean, correction: String) {
        val ui = _ui.value as? EarUi.Asking ?: return
        val q = ui.q as EarQuestion.Mixed
        if (!ok) stepsAllRight = false
        if (step == 0) { itemResults += "ear:mix:key" to ok; learning.review("ear:mix:key", ok) }
        if (step in 1..3 && !ok) mixProgOk = false
        if (step == 3) { itemResults += "ear:mix:prog" to mixProgOk; learning.review("ear:mix:prog", mixProgOk) }
        step++
        _ui.value = asking(q).copy(correction = if (ok) null else "ไม่ใช่ · $correction")
    }

    private fun finish1(ok: Boolean, correction: String?, answerKeys: Set<Int>? = null, entered: List<Int> = emptyList()) {
        val ui = _ui.value as? EarUi.Asking ?: return
        ui.q.itemIds.forEach { id -> itemResults += id to ok; learning.review(id, ok) }
        _ui.value = ui.copy(correct = ok, correction = if (ok) null else correction, marked = answerKeys ?: ui.marked, entered = entered)
    }

    fun retryEntry() {
        val ui = _ui.value as? EarUi.Asking ?: return
        _ui.value = ui.copy(entered = emptyList())
    }

    fun next() {
        index++
        if (index < questions.size) show() else finish()
    }

    // ---------------- rhythm echo ----------------

    fun startRhythm() {
        val ui = _ui.value as? EarUi.Asking ?: return
        val q = ui.q as? EarQuestion.Rhythm ?: return
        if (ui.rhythmState != null || ui.correct != null) return
        job?.cancel()
        job = scope.launch {
            val score = Score.from(q.piece)
            val bpm = 72.0
            val beat = (60_000_000_000.0 / bpm).toLong()
            val bar = beat * 4
            // Count-in clicks (high and short), then the bars to copy, then the answer bars with clicks.
            val t0 = System.nanoTime() + 300_000_000L + 4 * beat
            for (i in -4 until q.bars * 8) {
                val at = t0 + i * beat
                val n = if (Math.floorMod(i, 4) == 0) 96 else 91
                scheduler.noteOn(at, n, 70); scheduler.noteOff(at + 60_000_000L, n)
            }
            for (e in score.events) if (!e.isRest) {
                val on = t0 + (e.onset * beat).toLong()
                scheduler.noteOn(on, 84, 90); scheduler.noteOff(on + (e.duration * beat * 0.8).toLong(), 84)
            }
            val tolerance = TimingRules(learning.config.timing).toleranceMs(2, bpm, TimingMode.NORMAL)
            val judge = PlayAlongJudge(score, PlaySetup(PlayMode.TEMPO, setOf(Hand.RIGHT), 0, q.bars - 1, bpm, tolerance, 0, anyKey = true))
            val userStart = t0 + q.bars * bar
            judge.start(userStart / 1_000_000)
            val collector = launch {
                hub.events.collect { ev -> if (ev is InputEvent.NoteOn && System.nanoTime() > userStart - beat / 2) judge.onNoteOn(ev.note, ev.timeNanos / 1_000_000) }
            }
            while (!judge.isFinished(System.nanoTime() / 1_000_000)) {
                val now = System.nanoTime()
                val state = when { now < t0 -> "นับเข้า"; now < userStart -> "ฟัง…"; else -> "ตาคุณ: เคาะตาม" }
                (_ui.value as? EarUi.Asking)?.let { if (it.rhythmState != state) _ui.value = it.copy(rhythmState = state) }
                judge.advance(now / 1_000_000)
                delay(20)
            }
            collector.cancel()
            scheduler.clear()
            val r = judge.result()
            val ok = r.summary?.isClean(learning.config.cleanRun) == true
            val hit = r.outcomes.values.count { it.result == NoteResult.CORRECT }
            (_ui.value as? EarUi.Asking)?.let { cur ->
                _ui.value = cur.copy(rhythmState = null)
                finish1(ok, "ตรงจังหวะ $hit จาก ${r.outcomes.size} โน้ต · เร็ว ${r.count(NoteResult.EARLY)} · ช้า ${r.count(NoteResult.LATE)} · ไม่ได้เคาะ ${r.count(NoteResult.MISSED)}")
            }
        }
    }

    // ---------------- summary ----------------

    private fun finish() {
        val total = questions.size
        val right = questions.indices.count { outcomes[it] == true }
        val acc = if (total == 0) 0.0 else right.toDouble() / total
        val need = lesson.pass.minAccuracy ?: 0.8
        val met = acc >= need
        val passedNow = learning.recordDrill(lesson, 0, met)
        val lines = mutableListOf(
            when {
                passedNow -> "ผ่านขั้น ${lesson.id} แล้ว"
                met -> "ผ่านเกณฑ์วันนี้แล้ว พรุ่งนี้ทำให้ผ่านอีกครั้งเพื่อยืนยัน"
                else -> "ยังไม่ถึงเกณฑ์ ${(need * 100).toInt()}% ลองอีกรอบได้เลย"
            },
        )
        val weak = itemResults.groupBy { it.first }.filter { (_, rs) -> rs.count { !it.second } >= 2 }.keys
        if (weak.isNotEmpty()) lines += "ที่พลาดบ่อยรอบนี้ รอบหน้าแอพจะถามบ่อยขึ้น"
        _ui.value = EarUi.Done("ถูก $right จาก $total ข้อ (${(acc * 100).toInt()}%)", lines, met)
    }
}
