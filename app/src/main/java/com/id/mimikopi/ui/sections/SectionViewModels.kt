package com.id.mimikopi.ui.sections

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.section.Phrases
import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.SectionTrainer
import com.id.mimikopi.core.section.Step
import com.id.mimikopi.core.section.StepRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Holds the app's piano while a section screen is open (for the app's sound and "listen"). */
abstract class PianoHoldingViewModel(app: Application) : AndroidViewModel(app) {
    protected val services = app as MimikopiApp
    protected val piano = services.piano
    protected val hub = services.inputHub
    private var holding = false
    private var playJob: Job? = null

    fun onStart() {
        if (holding) return
        viewModelScope.launch { holding = withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    open fun onStop() {
        stopPlaying()
        if (!holding) return
        holding = false
        hub.releaseAll()
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    /** Plays steps evenly (pitches only, so an even pulse) for listening back. */
    fun play(steps: List<Step>) {
        stopPlaying()
        playJob = viewModelScope.launch {
            for (s in steps) {
                s.notes.forEach { piano.noteOn(it, 80) }
                delay(480)
                s.notes.forEach { piano.noteOff(it) }
                delay(40)
            }
        }
    }

    fun stopPlaying() {
        playJob?.cancel()
        playJob = null
        piano.allNotesOff()
    }

    override fun onCleared() = onStop()
}

class SectionListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MimikopiApp).sections
    val sections: StateFlow<List<Section>> = repo.sections

    init { viewModelScope.launch { repo.load() } }

    /** Makes an empty section and returns its id (the recorder opens next). */
    suspend fun create(title: String): String {
        val s = Section(repo.newId(), title, createdAtMillis = System.currentTimeMillis())
        repo.save(s)
        return s.id
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
}

/**
 * Writing a section down: every note played on the MIDI piano is added (notes struck together become a chord).
 * A selected step is replaced by the next one played. Phrases are split automatically until the user moves a split.
 */
class SectionEditViewModel(app: Application, args: SavedStateHandle) : PianoHoldingViewModel(app) {
    private val repo = services.sections
    private val id: String = args.get<String>("sectionId").orEmpty()
    private val recorder = StepRecorder()

    private val _section = MutableStateFlow<Section?>(null)
    val section: StateFlow<Section?> = _section.asStateFlow()
    /** Step to replace with the next one played (null: add at the end). */
    private val _selected = MutableStateFlow<Int?>(null)
    val selected: StateFlow<Int?> = _selected.asStateFlow()
    /** Phrase splits were moved by hand: no longer re-split automatically. */
    private var manualBreaks = false
    /** Index of the step the recorder is filling (a chord may still grow). */
    private var filling: Int? = null

    init {
        viewModelScope.launch {
            repo.load()
            _section.value = repo.section(id)
            manualBreaks = _section.value?.let { it.breaks != Phrases.autoBreaks(it.steps.size) } ?: false
        }
        viewModelScope.launch {
            hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) onNote(e.note, e.timeNanos) }
        }
    }

    private fun onNote(note: Int, time: Long) {
        val s = _section.value ?: return
        val (step, grows) = recorder.noteOn(note, time)
        val steps = s.steps.toMutableList()
        val target = if (grows) filling else _selected.value
        val index = when {
            target != null && target in steps.indices -> { steps[target] = step; target }
            else -> { steps += step; steps.lastIndex }
        }
        filling = index
        if (!grows && _selected.value != null) _selected.value = null
        update(s.copy(steps = steps))
    }

    private fun update(next: Section) {
        val fixed = if (manualBreaks) next.copy(breaks = next.breaks.filter { it in 1 until next.steps.size })
        else next.copy(breaks = Phrases.autoBreaks(next.steps.size))
        _section.value = fixed
        services.appScope.launch { repo.save(fixed) }
    }

    fun select(index: Int) {
        recorder.reset()
        filling = null
        _selected.value = if (_selected.value == index) null else index
    }

    fun deleteSelected() {
        val s = _section.value ?: return
        val i = _selected.value ?: return
        recorder.reset()
        filling = null
        _selected.value = null
        update(s.copy(steps = s.steps.filterIndexed { k, _ -> k != i }, breaks = Phrases.afterDelete(s.breaks, i)))
    }

    fun undo() {
        val s = _section.value ?: return
        if (s.steps.isEmpty()) return
        recorder.reset()
        filling = null
        _selected.value = null
        update(s.copy(steps = s.steps.dropLast(1), breaks = Phrases.afterDelete(s.breaks, s.steps.lastIndex)))
    }

    fun clear() {
        val s = _section.value ?: return
        recorder.reset()
        filling = null
        _selected.value = null
        manualBreaks = false
        update(s.copy(steps = emptyList(), breaks = emptyList(), stats = com.id.mimikopi.core.section.SectionStats()))
    }

    /** Starts (or removes) a phrase at the selected step. */
    fun togglePhraseStart() {
        val s = _section.value ?: return
        val i = _selected.value ?: return
        if (i == 0) return
        manualBreaks = true
        _selected.value = null
        update(s.copy(breaks = Phrases.toggle(s.breaks, i)))
    }

    fun autoPhrases() {
        val s = _section.value ?: return
        manualBreaks = false
        update(s)
    }

    fun rename(title: String) {
        val s = _section.value ?: return
        update(s.copy(title = title.take(60)))
    }

    fun setFlats(on: Boolean) {
        val s = _section.value ?: return
        update(s.copy(flats = on))
    }

    fun listen() = _section.value?.let { play(it.steps) }
}

data class PracticeUi(
    val mode: SectionTrainer.Mode = SectionTrainer.Mode.Phrase(0),
    val position: Int = 0,
    val pressed: Set<Int> = emptySet(),
    val streak: Int = 0,
    val goal: Int = 3,
    val runMistakes: Int = 0,
    val last: SectionTrainer.Outcome? = null,
    /** Counts up on every wrong note (to flash the message and buzz once per mistake). */
    val wrongCount: Int = 0,
    val mastered: Boolean = false,
)

/** Practising a section in wait mode with [SectionTrainer]; results go into the section's stats. */
class SectionPracticeViewModel(app: Application, args: SavedStateHandle) : PianoHoldingViewModel(app) {
    private val repo = services.sections
    private val id: String = args.get<String>("sectionId").orEmpty()

    private val _section = MutableStateFlow<Section?>(null)
    val section: StateFlow<Section?> = _section.asStateFlow()
    private var trainer: SectionTrainer? = null
    private val _ui = MutableStateFlow(PracticeUi())
    val ui: StateFlow<PracticeUi> = _ui.asStateFlow()
    /** Keys held on the MIDI keyboard (for the falling-notes keyboard). */
    val keys = hub.pressed
    private val _falling = MutableStateFlow(false)
    val falling: StateFlow<Boolean> = _falling.asStateFlow()

    fun setFalling(on: Boolean) {
        _falling.value = on
        viewModelScope.launch { services.database.settingsDao().put(com.id.mimikopi.data.db.SettingEntity(com.id.mimikopi.data.db.SettingKeys.SECTIONS_FALLING, on.toString())) }
    }

    init {
        viewModelScope.launch {
            _falling.value = services.database.settingsDao().get(com.id.mimikopi.data.db.SettingKeys.SECTIONS_FALLING) == "true"
        }
        viewModelScope.launch {
            repo.load()
            val s = repo.section(id) ?: return@launch
            _section.value = s
            if (s.steps.isNotEmpty()) {
                trainer = SectionTrainer(s.steps, s.phrases).also { t ->
                    // A section mastered before starts with full play-throughs; the phrases stay one tap away.
                    if (s.stats.mastered && s.phrases.size > 1) t.select(SectionTrainer.Mode.Full)
                }
                publish(null)
            }
        }
        viewModelScope.launch {
            hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) onNote(e.note) }
        }
    }

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
        _ui.value = PracticeUi(
            mode = t.mode, position = t.position, pressed = t.pressed, streak = t.streak, goal = t.goal, runMistakes = t.runMistakes,
            last = outcome ?: before.last,
            wrongCount = before.wrongCount + if (outcome == SectionTrainer.Outcome.WRONG) 1 else 0,
            mastered = t.mastered || (_section.value?.stats?.mastered == true),
        )
    }

    fun select(mode: SectionTrainer.Mode) {
        trainer?.select(mode)
        _ui.value = _ui.value.copy(last = null)
        publish(null)
    }

    /** Plays the part being practised (the current phrase, or the whole section). */
    fun listen() {
        val s = _section.value ?: return
        val t = trainer ?: return
        play(s.steps.slice(t.currentRange))
    }

    private fun saveStats() {
        val s = _section.value ?: return
        val t = trainer ?: return
        val next = s.copy(stats = t.drainInto(s.stats, LocalDate.now().toString()))
        _section.value = next
        services.appScope.launch { repo.save(next) } // also runs while the screen is closing
    }

    override fun onStop() {
        saveStats()
        super.onStop()
    }
}
