package com.id.mimikopi.ui.sections

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.section.Hand
import com.id.mimikopi.core.section.Section
import com.id.mimikopi.core.section.SectionTrainer
import com.id.mimikopi.core.section.Step
import com.id.mimikopi.core.section.SongEditor
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

/** Writing a song down with [SongEditor] (the rules are shared with the desktop); each change is saved. */
class SectionEditViewModel(app: Application, args: SavedStateHandle) : PianoHoldingViewModel(app) {
    private val repo = services.sections
    private val id: String = args.get<String>("sectionId").orEmpty()
    private var editor: SongEditor? = null

    private val _section = MutableStateFlow<Section?>(null)
    val section: StateFlow<Section?> = _section.asStateFlow()
    /** Step to replace (or insert before) with the next one played (null: add at the end). */
    private val _selected = MutableStateFlow<Int?>(null)
    val selected: StateFlow<Int?> = _selected.asStateFlow()
    private val _inserting = MutableStateFlow(false)
    val inserting: StateFlow<Boolean> = _inserting.asStateFlow()
    /** The next note added at the end starts a new part. */
    private val _newPartNext = MutableStateFlow(false)
    val newPartNext: StateFlow<Boolean> = _newPartNext.asStateFlow()

    init {
        viewModelScope.launch {
            repo.load()
            repo.section(id)?.let { editor = SongEditor(it); _section.value = it }
        }
        viewModelScope.launch {
            hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) edit { noteOn(e.note, e.timeNanos) } }
        }
    }

    /** Applies a change, publishes the editor's state and saves the song if it changed. */
    private fun edit(change: SongEditor.() -> Unit) {
        val e = editor ?: return
        val before = e.section
        e.change()
        _selected.value = e.selected
        _inserting.value = e.inserting
        _newPartNext.value = e.newPartNext
        if (e.section != before) {
            val s = e.section
            _section.value = s
            services.appScope.launch { repo.save(s) }
        }
    }

    fun select(index: Int) = edit { select(index) }
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
    val hand: Hand = Hand.BOTH,
    /** The hand's notes and parts being practised (all of them for both hands). */
    val steps: List<Step> = emptyList(),
    val phrases: List<IntRange> = emptyList(),
    /** For each practised part, the song's part (its name and video time). */
    val sourcePhrase: List<Int> = emptyList(),
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

    private var hand = Hand.BOTH
    private var sourcePhrase: List<Int> = emptyList()

    private fun publish(outcome: SectionTrainer.Outcome?) {
        val t = trainer ?: return
        val before = _ui.value
        _ui.value = PracticeUi(
            mode = t.mode, position = t.position, pressed = t.pressed, streak = t.streak, goal = t.goal, runMistakes = t.runMistakes,
            last = outcome ?: before.last,
            wrongCount = before.wrongCount + if (outcome == SectionTrainer.Outcome.WRONG) 1 else 0,
            mastered = t.mastered || (hand == Hand.BOTH && _section.value?.stats?.mastered == true),
            hand = hand, steps = t.steps, phrases = t.phrases,
            sourcePhrase = sourcePhrase.ifEmpty { t.phrases.indices.toList() },
        )
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

    fun select(mode: SectionTrainer.Mode) {
        trainer?.select(mode)
        _ui.value = _ui.value.copy(last = null)
        publish(null)
    }

    /** Plays the part being practised (the current phrase, or the whole section). */
    fun listen() {
        val s = _section.value ?: return
        val t = trainer ?: return
        play(t.steps.slice(t.currentRange))
    }

    private fun saveStats() {
        val s = _section.value ?: return
        val t = trainer ?: return
        val drained = t.drainInto(s.stats, LocalDate.now().toString())
        // One hand's parts are numbered differently: only the counts are kept, not part progress or mastery.
        val next = s.copy(stats = if (hand == Hand.BOTH) drained else drained.copy(phraseClean = s.stats.phraseClean, mastered = s.stats.mastered))
        _section.value = next
        services.appScope.launch { repo.save(next) } // also runs while the screen is closing
    }

    override fun onStop() {
        saveStats()
        super.onStop()
    }
}
