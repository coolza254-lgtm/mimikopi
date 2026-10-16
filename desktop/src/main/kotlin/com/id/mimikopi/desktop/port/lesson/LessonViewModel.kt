package com.id.mimikopi.desktop.port.lesson

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/lesson/LessonViewModel.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.desktop.port.SettingEntity
import com.id.mimikopi.desktop.port.SettingKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keyboard sizes offered for "how many Cs" (learning-system.md 10: count from the configured range). */
enum class KeyboardSize(val keys: Int, val range: IntRange) {
    K61(61, 36..96), K76(76, 28..103), K88(88, 21..108)
}

class LessonViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val repo = services.learning
    private val hub = services.inputHub
    private val piano = services.piano

    val lesson: Lesson? = args.get<String>("lessonId")?.let { repo.lesson(it) }
    val pressed = hub.pressed

    val record: StateFlow<LessonRecord?> = repo.records.combine(MutableStateFlow(lesson?.id)) { r, id -> id?.let { r[it] } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val status: StateFlow<LessonStatus> = repo.records.combine(MutableStateFlow(lesson)) { r, l -> l?.let { ProgressRules.status(it, r) } ?: LessonStatus.LOCKED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LessonStatus.LOCKED)

    /** Keys highlighted by a demonstration step. */
    private val _marked = MutableStateFlow<Set<Int>>(emptySet())
    val marked: StateFlow<Set<Int>> = _marked.asStateFlow()
    /** Letter whose names appear when pressed during a try-it step (C / โด). */
    private val _namedLetter = MutableStateFlow<Letter?>(null)
    val namedLetter: StateFlow<Letter?> = _namedLetter.asStateFlow()

    private val _keyboard = MutableStateFlow(KeyboardSize.K88)
    val keyboard: StateFlow<KeyboardSize> = _keyboard.asStateFlow()
    private val _countAnswer = MutableStateFlow<Pair<Int, Boolean>?>(null)
    val countAnswer: StateFlow<Pair<Int, Boolean>?> = _countAnswer.asStateFlow()

    private var holding = false

    init {
        viewModelScope.launch {
            repo.load()
            services.database.settingsDao().get(SettingKeys.KEYBOARD_KEYS)?.toIntOrNull()
                ?.let { k -> KeyboardSize.entries.firstOrNull { it.keys == k } }?.let { _keyboard.value = it }
        }
    }

    fun onStart() {
        if (holding) return
        viewModelScope.launch { holding = withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    fun onStop() {
        if (!holding) return
        holding = false
        hub.releaseAll()
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    /**
     * "highlight:C": plays and highlights every C in the on-screen range one after another, then keeps
     * them marked; pressing one shows its name (C / โด).
     */
    fun runAction(action: String?, range: IntRange) {
        val (kind, arg) = action?.split(":", limit = 2)?.let { it[0] to it.getOrNull(1) } ?: return
        when (kind) {
            "highlight" -> {
                val letter = arg?.let { runCatching { Letter.valueOf(it) }.getOrNull() } ?: return
                _namedLetter.value = letter
                viewModelScope.launch {
                    val keys = range.filter { it % 12 == letter.semitonesFromC }
                    _marked.value = emptySet()
                    for (k in keys) {
                        _marked.value = _marked.value + k
                        piano.noteOn(k, 80)
                        delay(500)
                        piano.noteOff(k)
                    }
                }
            }
            "play" -> arg?.let { p -> com.id.mimikopi.core.music.SpelledPitch.parse(p).midi }?.let { m ->
                viewModelScope.launch { piano.noteOn(m, 85); delay(900); piano.noteOff(m) }
            }
        }
    }

    fun setKeyboard(size: KeyboardSize) {
        _keyboard.value = size
        _countAnswer.value = null
        viewModelScope.launch { services.database.settingsDao().put(SettingEntity(SettingKeys.KEYBOARD_KEYS, size.keys.toString())) }
    }

    fun answerCount(letter: Letter, guess: Int) {
        val actual = _keyboard.value.range.count { it % 12 == letter.semitonesFromC }
        _countAnswer.value = actual to (guess == actual)
    }

    fun touch(note: Int, down: Boolean) {
        hub.dispatch(if (down) InputEvent.NoteOn(note, 80, System.nanoTime(), com.id.mimikopi.core.input.InputSource.TOUCH) else InputEvent.NoteOff(note, System.nanoTime(), com.id.mimikopi.core.input.InputSource.TOUCH))
    }

    override fun onCleared() = onStop()
}
