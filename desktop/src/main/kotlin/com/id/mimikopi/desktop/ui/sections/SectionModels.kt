package com.id.mimikopi.desktop.ui.sections

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.section.Phrases
import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.SectionStats
import com.id.mimikopi.core.section.SectionTrainer
import com.id.mimikopi.core.section.Step
import com.id.mimikopi.core.section.StepRecorder
import com.id.mimikopi.desktop.Services
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Plays steps evenly (pitches only) for listening back. */
fun playSteps(steps: List<Step>) {
    val s = Services.scheduler
    s.clear()
    var t = System.nanoTime() + 100_000_000L
    for (step in steps) {
        step.notes.forEach { s.noteOn(t, it, 80) }
        t += 480_000_000L
        step.notes.forEach { s.noteOff(t, it) }
        t += 40_000_000L
    }
}

/** Writing a section down by playing it (same rules as the Android recorder). */
class SectionEditModel(private val id: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = Services.sections
    private val recorder = StepRecorder()
    private val _section = MutableStateFlow(repo.section(id))
    val section: StateFlow<Section?> = _section.asStateFlow()
    private val _selected = MutableStateFlow<Int?>(null)
    val selected: StateFlow<Int?> = _selected.asStateFlow()
    private var manualBreaks = _section.value?.let { it.breaks != Phrases.autoBreaks(it.steps.size) } ?: false
    private var filling: Int? = null

    init {
        scope.launch { Services.hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) onNote(e.note, e.timeNanos) } }
    }

    fun dispose() { Services.scheduler.clear(); scope.cancel() }

    private fun onNote(note: Int, time: Long) {
        val s = _section.value ?: return
        val (step, grows) = recorder.noteOn(note, time)
        val steps = s.steps.toMutableList()
        val target = if (grows) filling else _selected.value
        val index = if (target != null && target in steps.indices) { steps[target] = step; target } else { steps += step; steps.lastIndex }
        filling = index
        if (!grows && _selected.value != null) _selected.value = null
        update(s.copy(steps = steps))
    }

    private fun update(next: Section) {
        val fixed = if (manualBreaks) next.copy(breaks = next.breaks.filter { it in 1 until next.steps.size }) else next.copy(breaks = Phrases.autoBreaks(next.steps.size))
        _section.value = fixed
        repo.save(fixed)
    }

    private fun resetRecorder() { recorder.reset(); filling = null }

    fun select(i: Int) { resetRecorder(); _selected.value = if (_selected.value == i) null else i }

    fun deleteSelected() {
        val s = _section.value ?: return
        val i = _selected.value ?: return
        resetRecorder(); _selected.value = null
        update(s.copy(steps = s.steps.filterIndexed { k, _ -> k != i }, breaks = Phrases.afterDelete(s.breaks, i)))
    }

    fun undo() {
        val s = _section.value ?: return
        if (s.steps.isEmpty()) return
        resetRecorder(); _selected.value = null
        update(s.copy(steps = s.steps.dropLast(1), breaks = Phrases.afterDelete(s.breaks, s.steps.lastIndex)))
    }

    fun clear() {
        val s = _section.value ?: return
        resetRecorder(); _selected.value = null; manualBreaks = false
        update(s.copy(steps = emptyList(), breaks = emptyList(), stats = SectionStats()))
    }

    fun togglePhraseStart() {
        val s = _section.value ?: return
        val i = _selected.value ?: return
        if (i == 0) return
        manualBreaks = true; _selected.value = null
        update(s.copy(breaks = Phrases.toggle(s.breaks, i)))
    }

    fun autoPhrases() { val s = _section.value ?: return; manualBreaks = false; update(s) }
    fun rename(title: String) { val s = _section.value ?: return; update(s.copy(title = title.take(60))) }
    fun setFlats(on: Boolean) { val s = _section.value ?: return; update(s.copy(flats = on)) }
    fun listen() { _section.value?.let { playSteps(it.steps) } }
}

data class PracticeUi(
    val mode: SectionTrainer.Mode = SectionTrainer.Mode.Phrase(0),
    val position: Int = 0,
    val pressed: Set<Int> = emptySet(),
    val streak: Int = 0,
    val goal: Int = 3,
    val last: SectionTrainer.Outcome? = null,
    val wrongCount: Int = 0,
)

/** Practising a section in wait mode with [SectionTrainer]; results go into the section's stats. */
class SectionPracticeModel(id: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = Services.sections
    private val _section = MutableStateFlow(repo.section(id))
    val section: StateFlow<Section?> = _section.asStateFlow()
    private val trainer: SectionTrainer? = _section.value?.takeIf { it.steps.isNotEmpty() }?.let { s ->
        SectionTrainer(s.steps, s.phrases).also { if (s.stats.mastered && s.phrases.size > 1) it.select(SectionTrainer.Mode.Full) }
    }
    private val _ui = MutableStateFlow(PracticeUi())
    val ui: StateFlow<PracticeUi> = _ui.asStateFlow()
    /** Falling notes instead of the big note (remembered, the same setting row as on the phone). */
    private val _falling = MutableStateFlow(Services.store.get(FALLING_KEY) == "true")
    val falling: StateFlow<Boolean> = _falling.asStateFlow()

    fun setFalling(on: Boolean) {
        _falling.value = on
        Services.store.put(FALLING_KEY, on.toString())
    }

    private companion object {
        const val FALLING_KEY = "sections.falling"
    }

    init {
        publish(null)
        scope.launch { Services.hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) onNote(e.note) } }
    }

    fun dispose() { saveStats(); Services.scheduler.clear(); scope.cancel() }

    private fun onNote(note: Int) {
        val t = trainer ?: return
        val outcome = t.press(note)
        if (outcome == SectionTrainer.Outcome.IGNORED) return
        publish(outcome)
        if (outcome in setOf(SectionTrainer.Outcome.PHRASE_PASSED, SectionTrainer.Outcome.FULL_RUN_DONE, SectionTrainer.Outcome.MASTERED, SectionTrainer.Outcome.PHRASE_RUN_DONE)) saveStats()
    }

    private fun publish(outcome: SectionTrainer.Outcome?) {
        val t = trainer ?: return
        val before = _ui.value
        _ui.value = PracticeUi(t.mode, t.position, t.pressed, t.streak, t.goal, outcome ?: before.last,
            before.wrongCount + if (outcome == SectionTrainer.Outcome.WRONG) 1 else 0)
    }

    fun select(mode: SectionTrainer.Mode) { trainer?.select(mode); _ui.value = _ui.value.copy(last = null); publish(null) }

    fun listen() {
        val s = _section.value ?: return
        val t = trainer ?: return
        playSteps(s.steps.slice(t.currentRange))
    }

    private fun saveStats() {
        val s = _section.value ?: return
        val t = trainer ?: return
        val next = s.copy(stats = t.drainInto(s.stats, LocalDate.now().toString()))
        _section.value = next
        repo.save(next)
    }
}
