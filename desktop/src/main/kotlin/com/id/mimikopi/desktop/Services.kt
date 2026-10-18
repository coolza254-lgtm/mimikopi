package com.id.mimikopi.desktop

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.desktop.audio.GervillPiano
import com.id.mimikopi.desktop.audio.PackInstaller
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.audio.SamplerPiano
import com.id.mimikopi.desktop.data.Store
import com.id.mimikopi.desktop.learning.LearningRepository
import com.id.mimikopi.desktop.learning.PieceRepository
import com.id.mimikopi.desktop.midi.DesktopMidi
import com.id.mimikopi.desktop.port.PianoController
import com.id.mimikopi.desktop.update.DesktopUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.PriorityQueue
import java.util.concurrent.locks.LockSupport

/** The user's choice of look. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Setting keys shared with the Android app (same rows, so backups move both ways). */
object Keys {
    const val LESSON_RECORDS = "lesson.records"
    const val THEME_MODE = "ui.theme"
    const val SOUND_THROUGH = "midi.soundThrough"
    const val VELOCITY_MODE = "midi.velocityMode"
    const val NOTE_NAME_STYLE = "notes.nameStyle"
    const val PIANO_SOUND = "piano.sound"
    /** Window size, position and maximised, as last closed (this computer only, never synced). */
    const val WINDOW = "ui.window"
}

/**
 * The single input stream, as on Android: MIDI calls [dispatch]; the app's piano sounds at once (when "sound through"
 * is on, for keyboards without their own speaker) and screens observe [events].
 */
class InputHub(private val piano: () -> Piano) {
    private val _events = MutableSharedFlow<InputEvent>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<InputEvent> = _events.asSharedFlow()
    private val _pressed = MutableStateFlow<Set<Int>>(emptySet())
    val pressed: StateFlow<Set<Int>> = _pressed.asStateFlow()
    @Volatile var soundThrough: Boolean = false
    /** Whether key strength changes the program's sound (off: every note at one level). */
    @Volatile var velocity: com.id.mimikopi.core.input.VelocityMode = com.id.mimikopi.core.input.VelocityMode()
    /** Semitones added to the program's sound (the piano page only; it resets when the page closes). */
    val transpose = com.id.mimikopi.core.input.Transposer()

    fun dispatch(event: InputEvent) {
        // On-screen keys always sound: they have no speaker of their own.
        val sound = soundThrough || event.source == com.id.mimikopi.core.input.InputSource.TOUCH
        when (event) {
            is InputEvent.NoteOn -> { if (sound) transpose.press(event.note)?.let { piano().noteOn(it, velocity.sound(event.velocity)) }; synchronized(this) { _pressed.value = _pressed.value + event.note } }
            is InputEvent.NoteOff -> { transpose.release(event.note)?.let { piano().noteOff(it) }; synchronized(this) { _pressed.value = _pressed.value - event.note } }
            is InputEvent.Sustain -> if (soundThrough) piano().sustain(event.down)
            is InputEvent.Control -> Unit
        }
        _events.tryEmit(event)
    }

    fun releaseAll() {
        synchronized(this) { _pressed.value = emptySet() }
        transpose.clear()
        piano().allNotesOff()
    }
}

/** Plays notes at System.nanoTime instants from one thread (questions, listening back), about 1 ms accurate. */
class NoteScheduler(private val piano: () -> Piano) {
    private data class Item(val at: Long, val note: Int, val velocity: Int, val on: Boolean, val seq: Long)
    private val queue = PriorityQueue(compareBy<Item>({ it.at }, { it.seq }))
    private val lock = Object()
    private var seq = 0L
    private val sounding = mutableSetOf<Int>()

    init {
        Thread({ loop() }, "mimikopi-scheduler").apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }
    }

    fun noteOn(at: Long, note: Int, velocity: Int) = add(Item(at, note, velocity, true, 0))
    fun noteOff(at: Long, note: Int) = add(Item(at, note, 0, false, 0))

    private fun add(i: Item) = synchronized(lock) { queue += i.copy(seq = seq++); lock.notifyAll() }

    private fun loop() {
        while (true) {
            val next = synchronized(lock) {
                while (queue.isEmpty()) lock.wait(500)
                queue.peek()
            }
            val wait = next.at - System.nanoTime()
            if (wait > 0) { LockSupport.parkNanos(minOf(wait, 20_000_000L)); continue }
            synchronized(lock) { if (queue.peek() === next) queue.poll() else null } ?: continue
            if (next.on) { piano().noteOn(next.note, next.velocity); synchronized(sounding) { sounding += next.note } }
            else { piano().noteOff(next.note); synchronized(sounding) { sounding -= next.note } }
        }
    }

    fun clear() {
        synchronized(lock) { queue.clear() }
        val notes = synchronized(sounding) { sounding.toList().also { sounding.clear() } }
        notes.forEach { piano().noteOff(it) }
    }
}

/** Hand-written dependency container for the desktop app. */
object Services {
    val dir = Store.defaultDir()
    val store = Store(java.io.File(dir, "data.json"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val pack = PackInstaller(dir)
    private val _sampled = MutableStateFlow(false)
    /** True when the sampled grand piano is installed and playing; otherwise Java's built-in synth plays. */
    val sampled: StateFlow<Boolean> = _sampled.asStateFlow()
    @Volatile private var current: Piano = GervillPiano()
    fun piano(): Piano = current

    /** Switches to the sampled piano when it is installed. */
    fun loadSampledPiano() {
        val (d, manifest) = pack.installed() ?: return
        val sampler = runCatching { SamplerPiano(d, manifest) }.getOrNull() ?: return
        sampler.setLevel(pianoController.sound.factor)
        val old = current
        current = sampler
        _sampled.value = true
        old.close()
    }

    val hub = InputHub(::piano)
    val scheduler = NoteScheduler(::piano)
    val midi = DesktopMidi(onEvent = hub::dispatch, onSilent = hub::releaseAll)
    val learning = LearningRepository(store)
    val pieces = PieceRepository(store)
    /** The reading drills and the score reader drive the piano and the metronome through this (as on Android). */
    val pianoController = PianoController(::piano)
    val updater = DesktopUpdater(dir, store, System.getProperty("jpackage.app-version") ?: "dev")

    val themeMode = MutableStateFlow(store.get(Keys.THEME_MODE)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK)
    fun setThemeMode(mode: ThemeMode) { themeMode.value = mode; store.put(Keys.THEME_MODE, mode.name) }

    fun start() {
        hub.soundThrough = store.get(Keys.SOUND_THROUGH)?.toBooleanStrictOrNull() ?: false
        hub.velocity = com.id.mimikopi.core.input.VelocityMode.parse(store.get(Keys.VELOCITY_MODE))
        pianoController.sound = com.id.mimikopi.core.piano.PianoSound.parse(store.get(Keys.PIANO_SOUND))
        midi.start()
        Thread { loadSampledPiano() }.apply { isDaemon = true; start() }
        Thread { updater.check(force = false) }.apply { isDaemon = true; start() }
    }
}
