package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.metronome.JitterReport
import com.id.mimikopi.core.metronome.MetronomeSettings
import com.id.mimikopi.core.metronome.TapTempo
import com.id.mimikopi.core.metronome.TimeSignature
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.learning.SettingKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The click-timing self-test result, kept on this computer only. [report] is click-to-click spacing on the fitted
 * output clock; [clockNoiseMeanMs]/[clockNoiseMaxMs] are how far single (frame, time) stamps stray from that fit (the
 * uncertainty when lining sound up with MIDI); [driftPpm] is how fast the sound card's clock runs against the system one.
 */
@Serializable
data class ClickTest(
    val atUtc: String,
    val bpm: Int,
    val ticksPerBeat: Int,
    val sampleRate: Int,
    val report: JitterReport,
    val clockNoiseMeanMs: Double = 0.0,
    val clockNoiseMaxMs: Double = 0.0,
    val driftPpm: Double = 0.0,
)

/** Least-squares line time = a + b·frame through the output stamps, and how far each stamp lies from it. */
internal data class ClockFit(val a: Double, val b: Double, val meanAbsMs: Double, val maxAbsMs: Double) {
    fun at(frame: Long) = a + b * frame

    companion object {
        fun of(stamps: List<FrameTimestamp>): ClockFit {
            val f0 = stamps.first().framePosition.toDouble()
            val t0 = stamps.first().timeNanos.toDouble()
            val xs = stamps.map { it.framePosition - f0 }
            val ys = stamps.map { it.timeNanos - t0 }
            val mx = xs.average()
            val my = ys.average()
            val b = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
            val a = my - b * mx
            val res = xs.indices.map { kotlin.math.abs(ys[it] - (a + b * xs[it])) / 1e6 }
            return ClockFit(t0 + a - b * f0, b, res.average(), res.max())
        }
    }
}

sealed interface ClickTestUi {
    data object Idle : ClickTestUi
    data class Running(val progress: Float) : ClickTestUi
    data class Done(val result: ClickTest) : ClickTestUi
    data object Failed : ClickTestUi
}

/**
 * The metronome page (the Android MetronomeController and MetronomeViewModel): settings shared with the phone in the
 * same setting row, clicks generated and timed by the native engine, and a self-test that maps click frames to the
 * output clock and measures click-to-click jitter.
 */
class MetronomeSession(private val services: Services, private val scope: CoroutineScope) {
    private val piano = services.piano
    private val db = services.db
    private val json = Json { ignoreUnknownKeys = true }
    private val tapTempo = TapTempo()
    private var holding = false

    private val _settings = MutableStateFlow(
        db.get(SettingKeys.METRONOME_SETTINGS)?.let { runCatching { json.decodeFromString(MetronomeSettings.serializer(), it) }.getOrNull() }?.normalized()
            ?: MetronomeSettings(),
    )
    val settings: StateFlow<MetronomeSettings> = _settings.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _test = MutableStateFlow<ClickTestUi>(
        db.get(SettingKeys.PC_CLICK_TEST)?.let { runCatching { json.decodeFromString(ClickTest.serializer(), it) }.getOrNull() }
            ?.let { ClickTestUi.Done(it) } ?: ClickTestUi.Idle,
    )
    val test: StateFlow<ClickTestUi> = _test.asStateFlow()

    fun enter() {
        if (!holding) holding = piano.acquire()
        _ready.value = holding
        apply()
    }

    fun leave() {
        _running.value = false
        apply()
        if (holding) piano.release()
        holding = false
        _ready.value = false
    }

    fun toggle() = setRunning(!_running.value)

    fun setRunning(on: Boolean) {
        _running.value = on && holding
        apply()
    }

    fun setBpm(bpm: Int) = edit { it.copy(bpm = bpm) }
    fun nudge(delta: Int) = edit { it.copy(bpm = it.bpm + delta) }
    fun setSignature(sig: TimeSignature) = edit { it.copy(signature = sig) }
    fun setSubdivision(n: Int) = edit { it.copy(subdivision = n) }
    fun setAccent(on: Boolean) = edit { it.copy(accent = on) }
    fun setVolume(v: Float, save: Boolean = true) = edit(save) { it.copy(volume = v) }

    fun tap() {
        tapTempo.tap(System.nanoTime() / 1_000_000)?.let(::setBpm)
    }

    fun tickCount(): Long = piano.metronomeTickCount()

    private fun edit(save: Boolean = true, transform: (MetronomeSettings) -> MetronomeSettings) {
        _settings.update { transform(it).normalized() }
        apply()
        if (save) {
            val text = json.encodeToString(MetronomeSettings.serializer(), _settings.value)
            services.appScope.launch { db.put(SettingKeys.METRONOME_SETTINGS, text) }
        }
    }

    private fun apply() {
        val s = _settings.value
        piano.setMetronome(_running.value, s.bpm.toDouble(), s.ticksPerBeat, s.barPattern().map { it.code }.toIntArray(), s.volume)
    }

    /**
     * Plays the click for [seconds] while sampling the output's (frame, time) stamps, then measures how far each
     * click-to-click interval is from 60/bpm/subdivision.
     */
    fun runSelfTest(seconds: Int = 20) {
        if (_test.value is ClickTestUi.Running || !holding) return
        scope.launch {
            _test.value = ClickTestUi.Running(0f)
            val r = selfTest(seconds) { _test.value = ClickTestUi.Running(it) }
            _test.value = if (r != null) ClickTestUi.Done(r) else ClickTestUi.Failed
        }
    }

    internal suspend fun selfTest(seconds: Int, onProgress: (Float) -> Unit = {}): ClickTest? {
        val s = _settings.value
        setRunning(true)
        try {
            delay(300)
            piano.drainMetronomeTicks()
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
            val fit = ClockFit.of(stamps.distinctBy { it.framePosition })
            val covered = ticks.filter { it <= stamps.maxOf { st -> st.framePosition } }
            // Ideal spacing on the card's own clock: a card running slightly fast or slow shows up as drift, not jitter.
            val report = JitterAnalysis.analyze(covered.map(fit::at), idealIntervalMs = s.framesPerTick(rate) * fit.b / 1e6).copy(method = METHOD)
            val result = ClickTest(Instant.now().toString(), s.bpm, s.ticksPerBeat, rate, report,
                fit.meanAbsMs, fit.maxAbsMs, (fit.b * rate / 1e9 - 1) * 1e6)
            db.put(SettingKeys.PC_CLICK_TEST, json.encodeToString(ClickTest.serializer(), result))
            return result
        } finally {
            setRunning(false)
        }
    }

    companion object {
        const val METHOD = "Click frames from the audio callback mapped to time through a least-squares line over the " +
            "output's (frame, System.nanoTime) stamps, the clock MIDI is judged on; jitter = deviation of click-to-click " +
            "intervals from 60/bpm/subdivision on the output's own clock (its rate against the system clock is the drift)."
    }
}
