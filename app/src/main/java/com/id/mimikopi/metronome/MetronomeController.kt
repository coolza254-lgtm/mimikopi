package com.id.mimikopi.metronome

import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.metronome.JitterReport
import com.id.mimikopi.core.metronome.MetronomeSettings
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.data.db.SettingsDao
import com.id.mimikopi.piano.PianoController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** Result of the built-in timing self-test, kept for Diagnostics. */
@Serializable
data class SelfTestResult(
    val atUtc: String,
    val bpm: Int,
    val ticksPerBeat: Int,
    val sampleRate: Int,
    val xRunsDuring: Int?,
    val report: JitterReport,
)

/**
 * App-wide metronome state shared by the metronome screen and the practice screens. The clicks
 * themselves are generated and timed by the native engine; this only holds settings and runs the
 * self-test.
 */
class MetronomeController(private val piano: PianoController, private val settingsDao: SettingsDao) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _settings = MutableStateFlow(MetronomeSettings())
    val settings: StateFlow<MetronomeSettings> = _settings.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var loaded = false

    init {
        piano.onStopped = { _running.value = false }
    }

    suspend fun load() {
        if (loaded) return
        loaded = true
        settingsDao.get(SettingKeys.METRONOME_SETTINGS)
            ?.let { runCatching { json.decodeFromString(MetronomeSettings.serializer(), it) }.getOrNull() }
            ?.let { _settings.value = it.normalized() }
    }

    suspend fun update(transform: (MetronomeSettings) -> MetronomeSettings) {
        _settings.update { transform(it).normalized() }
        apply()
        settingsDao.put(SettingEntity(SettingKeys.METRONOME_SETTINGS, json.encodeToString(MetronomeSettings.serializer(), _settings.value)))
    }

    fun setRunning(on: Boolean) {
        _running.value = on
        apply()
    }

    /** Pushes the current state to the engine (call after the screen has acquired the output). */
    fun apply() {
        val s = _settings.value
        piano.setMetronome(_running.value, s.bpm.toDouble(), s.ticksPerBeat, s.barPattern().map { it.code }.toIntArray(), s.volume)
    }

    fun tickCount(): Long = piano.metronomeTickCount()

    /**
     * Plays the metronome for [seconds] at the current settings while sampling the stream's
     * presentation timestamps, then measures click-to-click jitter (see [JitterAnalysis]).
     */
    suspend fun selfTest(seconds: Int = 20, onProgress: (Float) -> Unit): SelfTestResult? {
        val s = _settings.value
        setRunning(true)
        delay(300) // let the stream settle
        piano.drainMetronomeTicks()
        val xrunsBefore = piano.xRuns()
        val rate = piano.outputSampleRate().takeIf { it > 0 } ?: return null
        val stamps = mutableListOf<FrameTimestamp>()
        val ticks = mutableListOf<Long>()
        val steps = seconds * 20
        for (i in 0 until steps) {
            delay(50)
            piano.timestamp()?.let { stamps += FrameTimestamp(it[0], it[1]) }
            ticks += piano.drainMetronomeTicks().toList()
            onProgress((i + 1).toFloat() / steps)
        }
        if (stamps.size < 10 || ticks.size < 4) return null
        // Only clicks the timestamps actually cover (the newest ones may not be presented yet).
        val covered = ticks.filter { it <= stamps.maxOf { s -> s.framePosition } }
        val times = JitterAnalysis.presentationTimesNanos(covered, stamps, rate)
        val report = JitterAnalysis.analyze(times, idealIntervalMs = 60_000.0 / s.bpm / s.ticksPerBeat)
        val xrunsAfter = piano.xRuns()
        val result = SelfTestResult(
            atUtc = Instant.now().toString(),
            bpm = s.bpm,
            ticksPerBeat = s.ticksPerBeat,
            sampleRate = rate,
            xRunsDuring = if (xrunsBefore != null && xrunsAfter != null) xrunsAfter - xrunsBefore else null,
            report = report,
        )
        settingsDao.put(SettingEntity(SettingKeys.METRONOME_SELF_TEST, json.encodeToString(SelfTestResult.serializer(), result)))
        return result
    }

    suspend fun lastSelfTest(): SelfTestResult? =
        settingsDao.get(SettingKeys.METRONOME_SELF_TEST)?.let { runCatching { json.decodeFromString(SelfTestResult.serializer(), it) }.getOrNull() }
}
