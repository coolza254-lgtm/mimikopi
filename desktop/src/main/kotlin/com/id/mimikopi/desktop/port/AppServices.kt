package com.id.mimikopi.desktop.port

import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.desktop.Services
import kotlinx.serialization.json.Json

/**
 * The desktop's stand-in for the Android Application object: the same service names the ported view models reach
 * for (`(app as MimikopiApp).learning`, `.piano`, `.database.settingsDao()` ...), backed by [Services].
 */
object MimikopiApp : Application() {
    val learning get() = Services.learning
    val pieces get() = Services.pieces
    val inputHub get() = Services.hub
    val piano get() = Services.pianoController
    val midiInput get() = Services.midi
    val inputConfig = InputConfigStore
    val database = AppDatabase
    val session = SessionTracker
    val samplePacks = SamplePacks
}

/** One row of the settings table (the desktop keeps the same rows in its JSON file). */
data class SettingEntity(val key: String, val value: String)

object AppDatabase {
    fun settingsDao() = SettingsDao
}

object SettingsDao {
    fun get(key: String): String? = Services.store.get(key)
    fun put(entity: SettingEntity) = Services.store.put(entity.key, entity.value)
}

/** Latency calibration lives on the phone; a backup brings its values along (keyed by input and output kind). */
object InputConfigStore {
    private val json = Json { ignoreUnknownKeys = true }
    fun load(): InputConfig = Services.store.get(SettingKeys.INPUT_CONFIG)
        ?.let { runCatching { json.decodeFromString(InputConfig.serializer(), it) }.getOrNull() } ?: InputConfig()
}

/** The desktop keeps no practice sessions. */
object SessionTracker {
    fun currentId(): Long? = null
}

/**
 * On the desktop Java's own synthesizer plays when the sampled piano is not installed, so the other hand and the
 * demo always sound: there is always "a pack".
 */
object SamplePacks {
    fun installed(): Any = Services.pack.installed() ?: Unit
}

/** Coarse output route used to key latency offsets; the desktop has one kind. */
object OutputRoute {
    const val UNKNOWN = "UNKNOWN"
    @Suppress("UNUSED_PARAMETER")
    fun current(context: Any?): String = UNKNOWN
}

/** One played note inside an attempt (the desktop does not store attempts; kept so the ported code is unchanged). */
data class AttemptEvent(
    val expectedPitch: Int?,
    val actualPitch: Int?,
    val expectedOnsetMs: Long? = null,
    val actualOnsetMs: Long? = null,
    val offsetMs: Long? = null,
    val velocity: Int? = null,
)

/** The Android settings keys (same rows, so backups move both ways). */
object SettingKeys {
    const val NOTE_NAME_STYLE = "notes.nameStyle"
    const val HELPER_NOTE_NAMES_COUNT = "helper.noteNames.count"
    const val INPUT_CONFIG = "input.config"
    const val MIDI_SOUND_THROUGH = "midi.soundThrough"
    const val THEME_MODE = "ui.theme"
    const val LESSON_RECORDS = "lesson.records"
    const val PIECE_PROGRESS = "piece.progress"
    const val READER_OPTIONS = "reader.options"
    const val BENCHMARKS = "benchmarks"
    const val KEYBOARD_KEYS = "piano.keyCount"
    const val TIMING_MODE = "timing.mode"
    const val PATH_VERSION = "path.version"
    const val SECTIONS = "sections"
}
