package com.id.mimikopi.desktop.ui.sections

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.section.Hand
import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.SectionTrainer
import com.id.mimikopi.core.section.Step
import com.id.mimikopi.core.section.SongEditor
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

/** Writing a song down with [SongEditor] (the same rules as on the phone); each change is saved. */
class SectionEditModel(private val id: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = Services.sections
    private val editor: SongEditor? = repo.section(id)?.let { SongEditor(it) }
    private val _section = MutableStateFlow(editor?.section)
    val section: StateFlow<Section?> = _section.asStateFlow()
    private val _selected = MutableStateFlow<Int?>(null)
    val selected: StateFlow<Int?> = _selected.asStateFlow()
    private val _inserting = MutableStateFlow(false)
    val inserting: StateFlow<Boolean> = _inserting.asStateFlow()
    private val _newPartNext = MutableStateFlow(false)
    val newPartNext: StateFlow<Boolean> = _newPartNext.asStateFlow()

    init {
        scope.launch { Services.hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) edit { noteOn(e.note, e.timeNanos) } } }
    }

    fun dispose() { Services.scheduler.clear(); scope.cancel() }

    private fun edit(change: SongEditor.() -> Unit) {
        val e = editor ?: return
        val before = e.section
        e.change()
        _selected.value = e.selected
        _inserting.value = e.inserting
        _newPartNext.value = e.newPartNext
        if (e.section != before) { _section.value = e.section; repo.save(e.section) }
    }

    fun select(i: Int) = edit { select(i) }
    fun setInserting(on: Boolean) = edit { setInserting(on) }
    fun toggleNewPart() = edit { toggleNewPart() }
    fun deleteSelected() = edit { deleteSelected() }
    fun undo() = edit { undo() }
    fun clear() = edit { clear() }
    fun togglePhraseStart() = edit { togglePhraseStart() }
    fun autoPhrases() = edit { autoPhrases() }
    fun setPart(phrase: Int, name: String, time: String) = edit { setPart(phrase, name, time) }
    fun rename(title: String) = edit { rename(title) }
    fun setFlats(on: Boolean) = edit { setFlats(on) }
    fun setVideo(text: String) = edit { setVideo(text) }
    fun moveSplit(delta: Int) = edit { moveSplit(delta) }
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
    val hand: Hand = Hand.BOTH,
    val steps: List<Step> = emptyList(),
    val phrases: List<IntRange> = emptyList(),
    val sourcePhrase: List<Int> = emptyList(),
)

/** Practising a section in wait mode with [SectionTrainer]; results go into the section's stats. */
class SectionPracticeModel(id: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = Services.sections
    private val _section = MutableStateFlow(repo.section(id))
    val section: StateFlow<Section?> = _section.asStateFlow()
    private var hand = Hand.BOTH
    private var sourcePhrase: List<Int> = emptyList()
    private var trainer: SectionTrainer? = _section.value?.takeIf { it.steps.isNotEmpty() }?.let { s ->
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
            before.wrongCount + if (outcome == SectionTrainer.Outcome.WRONG) 1 else 0,
            hand, t.steps, t.phrases, sourcePhrase.ifEmpty { t.phrases.indices.toList() })
    }

    /** Practises one hand (notes below the song's split are the left hand) or both. */
    fun setHand(next: Hand) {
        val s = _section.value ?: return
        saveStats()
        val part = s.forHand(next)
        if (part.steps.isEmpty()) return
        hand = next
        sourcePhrase = part.sourcePhrase
        trainer = SectionTrainer(part.steps, part.phrases)
        _ui.value = _ui.value.copy(last = null)
        publish(null)
    }

    fun select(mode: SectionTrainer.Mode) { trainer?.select(mode); _ui.value = _ui.value.copy(last = null); publish(null) }

    fun listen() {
        val s = _section.value ?: return
        val t = trainer ?: return
        playSteps(t.steps.slice(t.currentRange))
    }

    private fun saveStats() {
        val s = _section.value ?: return
        val t = trainer ?: return
        val drained = t.drainInto(s.stats, LocalDate.now().toString())
        // One hand's parts are numbered differently: only the counts are kept, not part progress or mastery.
        val next = s.copy(stats = if (hand == Hand.BOTH) drained else drained.copy(phraseClean = s.stats.phraseClean, mastered = s.stats.mastered))
        _section.value = next
        repo.save(next)
    }
}
