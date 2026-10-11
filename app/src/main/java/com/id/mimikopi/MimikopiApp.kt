package com.id.mimikopi

import android.app.Application
import com.id.mimikopi.audio.NativePiano
import com.id.mimikopi.backup.BackupService
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.input.InputConfigStore
import com.id.mimikopi.input.InputHub
import com.id.mimikopi.metronome.MetronomeController
import com.id.mimikopi.midi.MidiInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.id.mimikopi.piano.PianoController
import com.id.mimikopi.piano.SamplePackManager
import com.id.mimikopi.update.UpdateService

/** Hand-written dependency container (no DI framework needed at this size). */
class MimikopiApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val backupService: BackupService by lazy { BackupService(this, database) }
    val updateService: UpdateService by lazy { UpdateService(this, database) }
    val samplePacks: SamplePackManager by lazy { SamplePackManager(this) }
    val piano: PianoController by lazy { PianoController({ NativePiano() }, samplePacks) }
    val metronome: MetronomeController by lazy { MetronomeController(piano, database.settingsDao()) }
    val inputHub: InputHub by lazy { InputHub(piano) }
    val inputConfig: InputConfigStore by lazy { InputConfigStore(database.settingsDao()) }
    val midiInput: MidiInputManager by lazy { MidiInputManager(this, inputHub) }
    val learning: com.id.mimikopi.learning.LearningRepository by lazy { com.id.mimikopi.learning.LearningRepository(this, database) }
    val pieces: com.id.mimikopi.pieces.PieceRepository by lazy { com.id.mimikopi.pieces.PieceRepository(this, database) }
    val songs: com.id.mimikopi.songs.SongRepository by lazy { com.id.mimikopi.songs.SongRepository(this, database) }
    val session: com.id.mimikopi.learning.SessionTracker by lazy { com.id.mimikopi.learning.SessionTracker(learning) }

    /** Process-lifetime scope for small startup work. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        com.id.mimikopi.diag.CrashLog.install(this)
        // MIDI devices are listened for app-wide, so a connected piano works on every practice screen.
        midiInput.start()
        appScope.launch {
            midiInput.octaveOffset = inputConfig.load().octaveOffset
            inputHub.soundThrough = database.settingsDao().get(com.id.mimikopi.data.db.SettingKeys.MIDI_SOUND_THROUGH)?.toBooleanStrictOrNull() ?: true
        }
    }
}
