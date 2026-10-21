package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.LatencyCalibration
import com.id.mimikopi.core.input.LatencyResult
import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.desktop.Services
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LatencyUi {
    data object Idle : LatencyUi
    data class CountIn(val beat: Int) : LatencyUi
    data class Tapping(val taps: Int, val clicks: Int) : LatencyUi
    data class Done(val result: LatencyResult, val key: String, val source: InputSource) : LatencyUi
    data class Failed(val reason: String) : LatencyUi
}

/**
 * Measuring the delay of one input on the current output path (learning-system.md 2.3 item 1), as on the phone:
 * 4 count-in clicks, then 16 clicks a second apart; the person presses any key on each click. Clicks are timed by
 * the audio frames they were played at; core's LatencyCalibration keeps one press per click and needs at least 10.
 * Presses from different sources are never mixed: the source used most is measured.
 */
class LatencySession(private val services: Services, private val scope: CoroutineScope) {
    private val piano = services.piano
    private val _ui = MutableStateFlow<LatencyUi>(LatencyUi.Idle)
    val ui: StateFlow<LatencyUi> = _ui.asStateFlow()
    private var job: Job? = null
    private val taps = mutableListOf<InputEvent.NoteOn>()
    private var holding = false

    init {
        scope.launch {
            services.hub.events.collect { e -> if (e is InputEvent.NoteOn && _ui.value is LatencyUi.Tapping) synchronized(taps) { taps += e } }
        }
    }

    fun start() {
        if (job?.isActive == true) return
        if (!holding) holding = piano.acquire()
        job = scope.launch {
            synchronized(taps) { taps.clear() }
            piano.drainMetronomeTicks()
            piano.setMetronome(true, 60.0, 1, intArrayOf(0, 2, 2, 2), 0.9f)
            val stamps = mutableListOf<FrameTimestamp>()
            val ticks = mutableListOf<Long>()
            var counted = 0
            val deadline = System.nanoTime() + 25_000_000_000L
            while (counted < COUNT_IN + CLICKS && System.nanoTime() < deadline) {
                delay(50)
                piano.timestamp()?.let { stamps += FrameTimestamp(it[0], it[1]) }
                ticks += piano.drainMetronomeTicks().toList()
                counted = ticks.size
                _ui.value = if (counted <= COUNT_IN) LatencyUi.CountIn(counted) else LatencyUi.Tapping(synchronized(taps) { taps.size }, counted - COUNT_IN)
            }
            delay(800)
            piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
            val rate = piano.outputSampleRate()
            val presses = synchronized(taps) { taps.toList() }
            val clicks = ticks.drop(COUNT_IN)
            val source = presses.groupingBy { it.source }.eachCount().maxByOrNull { it.value }?.key ?: InputSource.TOUCH
            val result = if (rate > 0 && stamps.size > 10 && clicks.isNotEmpty()) {
                LatencyCalibration.analyze(JitterAnalysis.presentationTimesNanos(clicks, stamps, rate), presses.filter { it.source == source }.map { it.timeNanos })
            } else null
            _ui.value = when {
                rate <= 0 -> LatencyUi.Failed("ไม่มีเสียงออก จึงวัดไม่ได้ ตรวจอุปกรณ์เสียงที่หน้าเสียงก่อน")
                result == null -> LatencyUi.Failed("กดตรงคลิกได้ไม่ถึง ${LatencyCalibration.MIN_TAPS} ครั้ง (กดซ้ำหรือคอร์ดในคลิกเดียวนับครั้งเดียว) ลองใหม่")
                else -> {
                    val key = InputConfig.latencyKey(source, services.routeKey())
                    services.inputConfig.update { it.copy(latencyMs = it.latencyMs + (key to result.offsetMs)) }
                    LatencyUi.Done(result, key, source)
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        _ui.value = LatencyUi.Idle
    }

    fun close() {
        cancel()
        if (holding) { holding = false; piano.release() }
    }

    companion object {
        const val COUNT_IN = 4
        const val CLICKS = 16
    }
}
