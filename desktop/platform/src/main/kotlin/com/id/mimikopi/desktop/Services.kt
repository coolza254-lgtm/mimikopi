package com.id.mimikopi.desktop

import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.PianoNames
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.piano.PianoSound
import com.id.mimikopi.desktop.audio.AudioPrefs
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.audio.SamplePacks
import com.id.mimikopi.desktop.data.AppDirs
import com.id.mimikopi.desktop.data.DataTransfer
import com.id.mimikopi.desktop.data.Database
import com.id.mimikopi.desktop.input.ComputerKeyboard
import com.id.mimikopi.desktop.input.InputHub
import com.id.mimikopi.desktop.learning.InputConfigStore
import com.id.mimikopi.desktop.learning.LearningRepository
import com.id.mimikopi.desktop.learning.PieceRepository
import com.id.mimikopi.desktop.learning.ProgressStore
import com.id.mimikopi.desktop.learning.SessionTracker
import com.id.mimikopi.desktop.learning.SettingKeys
import com.id.mimikopi.desktop.midi.MidiInput
import com.id.mimikopi.desktop.recall.PrivateSongs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the program shares between its pages, created once at start: the data on this computer, the piano,
 * the one input stream and the stores of progress. Pages and practice sessions get it instead of globals.
 */
class Services(
    val dirs: AppDirs,
    val db: Database,
    val piano: Piano,
    val appVersion: String,
    /** Tests: no MIDI polling. */
    private val startMidi: Boolean = true,
) : AutoCloseable {
    val hub = InputHub(piano)
    val learning = LearningRepository(db)
    val pieces = PieceRepository(db)
    val progress = ProgressStore(db)
    val session = SessionTracker(db)
    val inputConfig = InputConfigStore(db)
    val midi = MidiInput(hub) { inputConfig.config.value.octaveOffset }
    val transfer = DataTransfer(db, dirs, appVersion) { learning.leitner.intervalDays(it) }
    val samples = SamplePacks(dirs.samples, appVersion)
    val updater = com.id.mimikopi.desktop.update.Updater(java.io.File(dirs.root, "updates"), appVersion)
    val privateSongs = PrivateSongs(db)
    /** Work that must finish even when the page that started it closes (saving a result). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _nameStyle = MutableStateFlow(db.get(SettingKeys.NOTE_NAME_STYLE)?.let { runCatching { NoteNameStyle.valueOf(it) }.getOrNull() } ?: NoteNameStyle.BOTH)
    val nameStyle: StateFlow<NoteNameStyle> = _nameStyle.asStateFlow()

    private val _keysLowC = MutableStateFlow(db.get(SettingKeys.PC_KEYS_LOW_C)?.toIntOrNull() ?: ComputerKeyboard.DEFAULT_LOW_C)
    /** The computer keyboard's lowest C (moved with [ and ]). */
    val keysLowC: StateFlow<Int> = _keysLowC.asStateFlow()

    private val _pianoNames = MutableStateFlow(PianoNames.decode(db.get(PianoNames.SETTING_KEY)))
    /** The user's own names for their pianos ([PianoNames]); shown in place of the name the piano reports. */
    val pianoNames: StateFlow<Map<String, String>> = _pianoNames.asStateFlow()

    init {
        learning.load()
        pieces.load()
        progress.load()
        piano.prefs = AudioPrefs.LOCKED
        piano.sound = PianoSound.parse(db.get(SettingKeys.PIANO_SOUND))
        hub.soundThrough = db.get(SettingKeys.PC_MIDI_SOUND) == "true"
        hub.velocity = VelocityMode.parse(db.get(SettingKeys.VELOCITY_MODE))
        if (startMidi) midi.start()
        // The installed piano sound loads in the background; the built-in tone plays until then.
        appScope.launch { samples.installed()?.let { samples.load(piano, it) } }
    }

    /**
     * A piano is on a cable (every MIDI port here is), so timing can be judged (learning-system.md 2.11). Tests
     * without MIDI polling play the cable's part themselves.
     */
    fun hasCable(): Boolean = !startMidi || midi.ports.value.any { it.open }

    fun pianoName(reported: String): String = PianoNames.display(reported, _pianoNames.value)

    /** Names the piano that reports [reported]; blank returns it to the default name. */
    fun renamePiano(reported: String, name: String) {
        _pianoNames.value = PianoNames.rename(_pianoNames.value, reported, name)
        db.put(PianoNames.SETTING_KEY, PianoNames.encode(_pianoNames.value))
    }

    fun setNameStyle(style: NoteNameStyle) {
        _nameStyle.value = style
        db.put(SettingKeys.NOTE_NAME_STYLE, style.name)
    }

    fun setKeysLowC(lowC: Int) {
        _keysLowC.value = lowC
        db.put(SettingKeys.PC_KEYS_LOW_C, lowC.toString())
    }

    fun setMidiSound(on: Boolean) {
        hub.soundThrough = on
        db.put(SettingKeys.PC_MIDI_SOUND, on.toString())
    }

    fun setPianoSound(sound: PianoSound) {
        piano.sound = sound
        db.put(SettingKeys.PIANO_SOUND, sound.encode())
    }

    /**
     * Extra delay to subtract for [source]: none. The output is locked ([AudioPrefs.LOCKED]) and the engine's clock
     * already counts the time until a sound is heard; USB MIDI, the computer keyboard and the mouse add only a few
     * milliseconds, well inside the timing windows. There is nothing for the user to measure or set.
     */
    @Suppress("UNUSED_PARAMETER")
    fun latencyMs(source: InputSource): Double? = null

    /** Everything stored was replaced or merged (an import): read it again. */
    fun reloadAll() {
        learning.reload()
        pieces.reload()
        progress.reload()
        inputConfig.reload()
        _pianoNames.value = PianoNames.decode(db.get(PianoNames.SETTING_KEY))
        runCatching { privateSongs.reload() }
    }

    /** Waits (a few seconds at most) for saves still running in [appScope], so closing never loses a result. */
    fun awaitPendingWrites(timeoutMs: Long = 5_000) {
        runBlocking {
            withTimeoutOrNull(timeoutMs) { appScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() } }
        }
    }

    override fun close() {
        awaitPendingWrites()
        runCatching { session.finish() }
        midi.close()
        piano.close()
        db.close()
    }
}
