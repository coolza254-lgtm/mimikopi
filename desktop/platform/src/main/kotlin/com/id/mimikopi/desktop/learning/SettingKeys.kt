package com.id.mimikopi.desktop.learning

/**
 * Settings rows, named exactly as in the Android app (data/db/Daos.kt SettingKeys) so backups mean the same on both.
 * Rows marked PC are this program's own and are never merged into another device.
 */
object SettingKeys {
    const val NOTE_NAME_STYLE = "notes.nameStyle"
    const val HELPER_NOTE_NAMES_COUNT = "helper.noteNames.count"
    const val METRONOME_SETTINGS = "metronome.settings"
    const val INPUT_CONFIG = "input.config"
    const val PIANO_SOUND = "piano.sound"
    const val VELOCITY_MODE = "midi.velocityMode"
    const val THEME_MODE = "ui.theme"
    const val LESSON_RECORDS = "lesson.records"
    const val PIECE_PROGRESS = "piece.progress"
    const val READER_OPTIONS = "reader.options"
    const val BENCHMARKS = "benchmarks"
    const val SESSION_MINUTES = "session.minutes"
    const val KEYBOARD_KEYS = "piano.keyCount"
    const val TIMING_MODE = "timing.mode"
    const val PATH_VERSION = "path.version"
    const val LESSON_RECORDS_V2 = "lesson.records.v2"
    const val CHORDS_SETTINGS = "chords.settings"
    const val CHORDS_RUNS = "chords.runs"
    const val FLOW_PREFIX = "flow:"

    /** PC: output buffer and exclusive mode (AudioPrefs). */
    const val PC_AUDIO = "pc.audio"
    /** PC: MIDI notes also play the program's piano (off: the piano's own speaker). */
    const val PC_MIDI_SOUND = "pc.midiSound"
    /** PC: the computer keyboard's lowest C. */
    const val PC_KEYS_LOW_C = "pc.keys.lowC"
    /** PC: the last click-timing self-test on this computer's output. */
    const val PC_CLICK_TEST = "pc.metronome.selfTest"
    /** PC: the window's size and fullscreen state. */
    const val PC_WINDOW = "pc.window"
}
