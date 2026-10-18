package com.id.mimikopi.desktop.port.metronome

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/metronome/MetronomeViewModel.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
import com.id.mimikopi.desktop.port.SystemClock
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.core.metronome.MetronomeSettings
import com.id.mimikopi.core.metronome.TapTempo
import com.id.mimikopi.core.metronome.TimeSignature
import com.id.mimikopi.desktop.port.SelfTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SelfTestUi {
    data object Idle : SelfTestUi
    data class Running(val progress: Float) : SelfTestUi
    data class Done(val result: SelfTestResult) : SelfTestUi
    data object Failed : SelfTestUi
}

/** Shared by the metronome screen and the compact metronome bar on practice screens. */
class MetronomeViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val controller = services.metronome
    private val piano = services.piano

    val settings: StateFlow<MetronomeSettings> = controller.settings
    val running: StateFlow<Boolean> = controller.running

    private val _outputReady = MutableStateFlow(false)
    val outputReady: StateFlow<Boolean> = _outputReady.asStateFlow()

    private val _selfTest = MutableStateFlow<SelfTestUi>(SelfTestUi.Idle)
    val selfTest: StateFlow<SelfTestUi> = _selfTest.asStateFlow()

    private val tapTempo = TapTempo()
    private var holding = false

    init {
        viewModelScope.launch {
            controller.load()
            controller.lastSelfTest()?.let { _selfTest.value = SelfTestUi.Done(it) }
        }
    }

    /** Standalone metronome screen visible: open the output (no piano samples needed). */
    fun onStart() {
        if (holding) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { piano.acquire(needPack = false) }
            holding = ok
            _outputReady.value = ok
            if (ok) controller.apply()
        }
    }

    fun onStop() {
        if (!holding) return
        holding = false
        _outputReady.value = false
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    fun toggle() = controller.setRunning(!running.value)

    fun setBpm(bpm: Int) = edit { it.copy(bpm = bpm) }
    fun nudge(delta: Int) = edit { it.copy(bpm = it.bpm + delta) }
    fun setSignature(sig: TimeSignature) = edit { it.copy(signature = sig) }
    fun setSubdivision(n: Int) = edit { it.copy(subdivision = n) }
    fun setAccent(on: Boolean) = edit { it.copy(accent = on) }
    fun setVolume(v: Float) = edit { it.copy(volume = v) }

    fun tap() {
        tapTempo.tap(SystemClock.uptimeMillis())?.let { setBpm(it) }
    }

    fun tickCount(): Long = controller.tickCount()

    fun runSelfTest() {
        if (_selfTest.value is SelfTestUi.Running) return
        viewModelScope.launch {
            _selfTest.value = SelfTestUi.Running(0f)
            val r = controller.selfTest(seconds = 20) { p -> _selfTest.value = SelfTestUi.Running(p) }
            _selfTest.value = if (r != null) SelfTestUi.Done(r) else SelfTestUi.Failed
        }
    }

    private fun edit(transform: (MetronomeSettings) -> MetronomeSettings) {
        viewModelScope.launch { controller.update(transform) }
    }

    override fun onCleared() = onStop()
}
