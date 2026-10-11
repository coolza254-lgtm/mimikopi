package com.id.mimikopi.diag

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import com.id.mimikopi.BuildConfig
import com.id.mimikopi.core.diag.AppInfo
import com.id.mimikopi.core.diag.AudioInfo
import com.id.mimikopi.core.diag.DatabaseInfo
import com.id.mimikopi.core.diag.DeviceInfo
import com.id.mimikopi.core.diag.DiagnosticsReport
import com.id.mimikopi.core.diag.MidiDeviceSummary
import com.id.mimikopi.core.diag.MidiInfo
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.core.diag.EngineInfo
import com.id.mimikopi.piano.PianoController
import com.id.mimikopi.metronome.MetronomeController
import com.id.mimikopi.core.diag.JitterResult
import com.id.mimikopi.piano.SamplePackManager
import java.time.Instant

/** Reads device facts for the Diagnostics screen. Only reads; never changes system state. */
class DiagnosticsCollector(
    private val context: Context,
    private val database: AppDatabase,
    private val piano: PianoController,
    private val packs: SamplePackManager,
    private val metronome: MetronomeController,
    private val inputConfig: com.id.mimikopi.input.InputConfigStore,
) {

    suspend fun collect(): DiagnosticsReport {
        val metrics = runCatching { piano.probeMetrics() }.getOrNull()
        val notes = mutableListOf(
            "engine.estimatedLatencyMs is Oboe's estimate for the current route; true touch-to-sound latency needs the external test in docs/DEVICE-TESTING.md.",
            "metronomeJitter: from the last self-test on the metronome screen (null if never run).",
            "midi.probe: from the velocity test on the MIDI screen (null if never run).",
        )
        inputConfig.load().let { cfg ->
            notes += "input.octaveOffset=${cfg.octaveOffset}; latencyMs=${cfg.latencyMs}"
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notes += "audio.currentRoute needs Android 13+; empty on this device."
        }
        return DiagnosticsReport(
            generatedAtUtc = Instant.now().toString(),
            app = AppInfo(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), BuildConfig.BUILD_TYPE),
            device = DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                device = Build.DEVICE,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
            ),
            features = features(),
            audio = audio().copy(
                framesPerBurst = metrics?.framesPerBurst,
                estimatedOutputLatencyMs = metrics?.estimatedLatencyMs,
            ),
            engine = metrics?.let {
                EngineInfo(
                    sampleRate = it.sampleRate,
                    framesPerBurst = it.framesPerBurst,
                    bufferSizeFrames = it.bufferSizeFrames,
                    estimatedLatencyMs = it.estimatedLatencyMs,
                    exclusive = it.exclusive,
                    lowLatency = it.lowLatency,
                    xRuns = it.xRuns,
                    maxActiveVoices = it.maxActiveVoices,
                    maxCallbackMicros = it.maxCallbackMicros,
                    streamRestarts = it.restarts,
                    samplePackInstalled = packs.installed() != null,
                    samplePackBytes = packs.installedBytes(),
                    residentSampleBytes = it.residentBytes,
                )
            },
            midi = inputConfig.load().let { cfg ->
                MidiInfo(midiDevices(), probe = cfg.velocityProbe?.describe())
            },
            metronomeJitter = metronome.lastSelfTest()?.let {
                JitterResult(it.report.meanAbsMs, it.report.maxAbsMs, it.report.clicks,
                    "${it.report.method} At ${it.bpm} bpm x${it.ticksPerBeat}, ${it.sampleRate} Hz, xruns during test: ${it.xRunsDuring ?: "?"}, ${it.atUtc}.")
            },
            lastUpdateCheck = database.settingsDao().get(SettingKeys.LAST_UPDATE_CHECK),
            database = databaseInfo(),
            notes = notes,
            lastCrash = CrashLog.read(context),
        )
    }

    private fun features(): Map<String, Boolean> {
        val pm = context.packageManager
        return listOf(
            PackageManager.FEATURE_MIDI,
            PackageManager.FEATURE_USB_HOST,
            PackageManager.FEATURE_AUDIO_LOW_LATENCY,
            PackageManager.FEATURE_AUDIO_PRO,
            PackageManager.FEATURE_AUDIO_OUTPUT,
            PackageManager.FEATURE_BLUETOOTH_LE,
        ).associateWith { pm.hasSystemFeature(it) }
    }

    private fun audio(): AudioInfo {
        val am = context.getSystemService(AudioManager::class.java)
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map(::describe).distinct()
        val route = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            am.getAudioDevicesForAttributes(media).map(::describe)
        } else {
            emptyList()
        }
        return AudioInfo(
            outputSampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull(),
            framesPerBufferHint = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull(),
            currentRoute = route,
            availableOutputs = outputs,
        )
    }

    private fun describe(device: AudioDeviceInfo): String {
        val name = device.productName?.toString().orEmpty()
        val type = audioDeviceTypeName(device.type)
        return if (name.isBlank() || name == Build.MODEL) type else "$type ($name)"
    }

    @Suppress("DEPRECATION")
    private fun midiDevices(): List<MidiDeviceSummary> {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) return emptyList()
        val midi = context.getSystemService(MidiManager::class.java) ?: return emptyList()
        val infos: Collection<MidiDeviceInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            midi.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
        } else {
            midi.devices.toList()
        }
        return infos.map { info ->
            MidiDeviceSummary(
                name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "?",
                manufacturer = info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER),
                type = when (info.type) {
                    MidiDeviceInfo.TYPE_USB -> "USB"
                    MidiDeviceInfo.TYPE_BLUETOOTH -> "BLUETOOTH"
                    MidiDeviceInfo.TYPE_VIRTUAL -> "VIRTUAL"
                    else -> "OTHER(${info.type})"
                },
                inputPorts = info.inputPortCount,
                outputPorts = info.outputPortCount,
            )
        }
    }

    private suspend fun databaseInfo(): DatabaseInfo {
        val dao = database.statsDao()
        return DatabaseInfo(
            schemaVersion = AppDatabase.VERSION,
            tableRowCounts = mapOf(
                "practice_sessions" to dao.countSessions(),
                "attempts" to dao.countAttempts(),
                "attempt_events" to dao.countAttemptEvents(),
                "item_stats" to dao.countItemStats(),
                "settings" to dao.countSettings(),
            ),
        )
    }
}

/** Readable names for AudioDeviceInfo.TYPE_* (constants are plain ints, safe on every API level). */
internal fun audioDeviceTypeName(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
    AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
    AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
    AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "REMOTE_SUBMIX"
    else -> "TYPE_$type"
}
