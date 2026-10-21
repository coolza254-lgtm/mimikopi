package com.id.mimikopi.ui.midi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.LatencyCalibration
import com.id.mimikopi.core.input.LatencyResult
import com.id.mimikopi.core.input.MidiParser
import com.id.mimikopi.core.input.OctaveCalibration
import com.id.mimikopi.core.input.VelocityProbe
import com.id.mimikopi.core.input.VelocityProbeResult
import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.input.OutputRoute
import com.id.mimikopi.midi.BleLink
import com.id.mimikopi.midi.BleMidiDevice
import com.id.mimikopi.midi.MidiDeviceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class BleProblem { PERMISSION, OFF }

/** One line of the MIDI monitor. */
data class MonitorLine(val millis: Long, val text: String)

sealed interface ProbeUi {
    data object Idle : ProbeUi
    data class Soft(val velocities: List<Int>) : ProbeUi
    data class Hard(val soft: List<Int>, val velocities: List<Int>) : ProbeUi
    data class Done(val result: VelocityProbeResult) : ProbeUi
}

sealed interface OctaveUi {
    data object Idle : OctaveUi
    data object Waiting : OctaveUi
    data class Done(val offset: Int) : OctaveUi
    data class Rejected(val note: Int) : OctaveUi
}

sealed interface LatencyUi {
    data object Idle : LatencyUi
    data class CountIn(val beat: Int) : LatencyUi
    data class Tapping(val taps: Int, val clicks: Int) : LatencyUi
    data class Done(val result: LatencyResult, val key: String) : LatencyUi
    data object Failed : LatencyUi
}

class MidiViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val hub = services.inputHub
    private val piano = services.piano
    private val midi = services.midiInput
    private val store = services.inputConfig
    private val settings = services.database.settingsDao()

    val devices: StateFlow<List<MidiDeviceState>> = midi.devices
    private val ble = services.bleScanner
    val bleFound: StateFlow<List<BleMidiDevice>> = ble.found
    val bleScanning: StateFlow<Boolean> = ble.scanning
    /** The last search ended without finding a Bluetooth MIDI instrument. */
    val bleScanFinished: StateFlow<Boolean> = ble.finished
    val bleLink: StateFlow<BleLink> = midi.bleLink
    /** The latest plug or unplug, to say "unplugged" with the instrument's name. */
    val notice: StateFlow<com.id.mimikopi.core.input.MidiNotice?> = midi.notice
    val bleSupported: Boolean = ble.supported && midi.supported
    private val _bleSaved = MutableStateFlow<BleMidiDevice?>(null)
    /** The remembered Bluetooth instrument: one tap connects it, and a search connects it when it is found. */
    val bleSaved: StateFlow<BleMidiDevice?> = _bleSaved.asStateFlow()
    private val _bleProblem = MutableStateFlow<BleProblem?>(null)
    val bleProblem: StateFlow<BleProblem?> = _bleProblem.asStateFlow()
    val midiSupported: Boolean = midi.supported
    val config: StateFlow<InputConfig> = store.config

    private val _monitor = MutableStateFlow<List<MonitorLine>>(emptyList())
    val monitor: StateFlow<List<MonitorLine>> = _monitor.asStateFlow()

    private val _soundThrough = MutableStateFlow(hub.soundThrough)
    val soundThrough: StateFlow<Boolean> = _soundThrough.asStateFlow()
    private val _velocity = MutableStateFlow(hub.velocity)
    val velocity: StateFlow<com.id.mimikopi.core.input.VelocityMode> = _velocity.asStateFlow()

    private val _probe = MutableStateFlow<ProbeUi>(ProbeUi.Idle)
    val probe: StateFlow<ProbeUi> = _probe.asStateFlow()
    private val _octave = MutableStateFlow<OctaveUi>(OctaveUi.Idle)
    val octave: StateFlow<OctaveUi> = _octave.asStateFlow()
    private val _latency = MutableStateFlow<LatencyUi>(LatencyUi.Idle)
    val latency: StateFlow<LatencyUi> = _latency.asStateFlow()

    private var holding = false
    private var latencyJob: Job? = null
    private val latencyTaps = mutableListOf<InputEvent.NoteOn>()
    private val startNanos = System.nanoTime()
    private val simParser = MidiParser(InputSource.SIMULATED)

    init {
        viewModelScope.launch { store.load() }
        viewModelScope.launch { hub.events.collect(::onEvent) }
        viewModelScope.launch {
            _bleSaved.value = settings.get(SettingKeys.MIDI_BLE_DEVICE)?.split('|', limit = 2)?.takeIf { it.size == 2 }?.let { BleMidiDevice(it[0], it[1]) }
        }
        // The user's search connects the remembered instrument as soon as it shows up.
        viewModelScope.launch {
            ble.found.collect { found ->
                val saved = _bleSaved.value ?: return@collect
                val connected = midi.devices.value.any { it.source == InputSource.BLE_MIDI && it.open }
                if (!connected && ble.scanning.value && found.any { it.address == saved.address }) bleConnect(saved)
            }
        }
    }

    // --- Bluetooth LE MIDI ---

    /** Bluetooth can be used now; otherwise says why not. */
    private fun bleReady(): Boolean {
        _bleProblem.value = when {
            !ble.hasPermissions() -> BleProblem.PERMISSION
            !ble.enabled -> BleProblem.OFF
            else -> null
        }
        return _bleProblem.value == null
    }

    /** One search (10 s at most), after the permission dialog (or right away when already allowed). */
    fun bleScan() {
        if (!bleReady()) return
        midi.clearBluetoothFailure()
        ble.scan()
    }

    fun bleConnect(device: BleMidiDevice) {
        ble.stop()
        if (!bleReady()) return
        _bleSaved.value = device
        viewModelScope.launch { settings.put(SettingEntity(SettingKeys.MIDI_BLE_DEVICE, "${device.address}|${device.name.replace('|', ' ')}")) }
        midi.connectBluetooth(device.address, device.name)
    }

    /** Connects the remembered instrument now (e.g. after switching the piano on). */
    fun bleConnectSaved() {
        _bleSaved.value?.let(::bleConnect)
    }

    fun bleDisconnect() = midi.disconnectBluetooth()

    fun bleForget() {
        ble.stop()
        _bleSaved.value = null
        midi.disconnectBluetooth()
        viewModelScope.launch { settings.delete(SettingKeys.MIDI_BLE_DEVICE) }
    }

    fun onStart() {
        if (holding) return
        viewModelScope.launch {
            holding = withContext(Dispatchers.IO) { piano.acquire(needPack = false) }
        }
    }

    fun onStop() {
        if (!holding) return
        holding = false
        hub.releaseAll()
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    /** Re-reads the system's device list (cables plugged in or pulled out while the app was not watching). */
    fun recheck() = midi.resync()

    fun setSoundThrough(on: Boolean) {
        hub.soundThrough = on
        _soundThrough.value = on
        viewModelScope.launch { settings.put(SettingEntity(SettingKeys.MIDI_SOUND_THROUGH, on.toString())) }
    }

    /** Key strength on or off (and the one level when off) for the app's sound. */
    fun setVelocity(mode: com.id.mimikopi.core.input.VelocityMode) {
        hub.velocity = mode
        _velocity.value = mode
        viewModelScope.launch { settings.put(SettingEntity(SettingKeys.VELOCITY_MODE, mode.encode())) }
    }

    private fun onEvent(e: InputEvent) {
        val ms = (e.timeNanos - startNanos) / 1_000_000
        val text = when (e) {
            is InputEvent.NoteOn -> "${e.source.label()}  ON   ${noteName(e.note)} (${e.note})  vel ${e.velocity}"
            is InputEvent.NoteOff -> "${e.source.label()}  OFF  ${noteName(e.note)} (${e.note})"
            is InputEvent.Sustain -> "${e.source.label()}  PEDAL ${if (e.down) "ลง" else "ขึ้น"}"
            is InputEvent.Control -> "${e.source.label()}  CC${e.controller} = ${e.value}"
        }
        _monitor.update { (listOf(MonitorLine(ms, text)) + it).take(40) }
        if (e is InputEvent.NoteOn) feedCalibrations(e)
    }

    private fun feedCalibrations(e: InputEvent.NoteOn) {
        when (val p = _probe.value) {
            is ProbeUi.Soft -> _probe.value = (p.velocities + e.velocity).let { if (it.size >= PROBE_PRESSES) ProbeUi.Hard(it, emptyList()) else ProbeUi.Soft(it) }
            is ProbeUi.Hard -> {
                val hard = p.velocities + e.velocity
                if (hard.size >= PROBE_PRESSES) {
                    val result = VelocityProbe.analyze(p.soft, hard)
                    _probe.value = ProbeUi.Done(result)
                    viewModelScope.launch { store.update { it.copy(velocityProbe = result) } }
                } else {
                    _probe.value = ProbeUi.Hard(p.soft, hard)
                }
            }
            else -> Unit
        }
        if (_octave.value == OctaveUi.Waiting && e.source != InputSource.TOUCH) {
            val raw = e.note - midi.octaveOffset
            val offset = OctaveCalibration.offsetFor(raw)
            if (offset == null) {
                _octave.value = OctaveUi.Rejected(raw)
            } else {
                midi.octaveOffset = offset
                _octave.value = OctaveUi.Done(offset)
                viewModelScope.launch { store.update { it.copy(octaveOffset = offset) } }
            }
        }
        if (_latency.value is LatencyUi.Tapping) synchronized(latencyTaps) { latencyTaps += e }
    }

    fun startProbe() { _probe.value = ProbeUi.Soft(emptyList()) }
    fun startOctave() { _octave.value = OctaveUi.Waiting }

    /**
     * learning-system.md 2.3 item 1: 4 count-in clicks then 16 clicks at 60 BPM; the user plays any
     * key on each click. The click times come from the audio stream timestamps (the same clock as
     * MIDI timestamps), so the result is this input + this output route's real delay.
     */
    fun startLatency() {
        if (latencyJob?.isActive == true) return
        latencyJob = viewModelScope.launch {
            synchronized(latencyTaps) { latencyTaps.clear() }
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
                _latency.value = if (counted <= COUNT_IN) LatencyUi.CountIn(counted)
                else LatencyUi.Tapping(synchronized(latencyTaps) { latencyTaps.size }, counted - COUNT_IN)
            }
            delay(800) // allow the last tap
            piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
            val rate = piano.outputSampleRate()
            val taps = synchronized(latencyTaps) { latencyTaps.toList() }
            val clicks = ticks.drop(COUNT_IN)
            val source = taps.groupingBy { it.source }.eachCount().maxByOrNull { it.value }?.key ?: InputSource.TOUCH
            val result = if (rate > 0 && stamps.size > 10 && clicks.isNotEmpty()) {
                LatencyCalibration.analyze(JitterAnalysis.presentationTimesNanos(clicks, stamps, rate), taps.filter { it.source == source }.map { it.timeNanos })
            } else null
            if (result == null) {
                _latency.value = LatencyUi.Failed
            } else {
                val key = InputConfig.latencyKey(source, OutputRoute.current(getApplication()))
                store.update { it.copy(latencyMs = it.latencyMs + (key to result.offsetMs)) }
                _latency.value = LatencyUi.Done(result, key)
            }
        }
    }

    /** On-screen tap pad for calibrating touch input (and for trying calibration without a piano). */
    fun tapPad() {
        hub.dispatch(InputEvent.NoteOn(60, 90, System.nanoTime(), InputSource.TOUCH))
        hub.dispatch(InputEvent.NoteOff(60, System.nanoTime(), InputSource.TOUCH))
    }

    // --- Simulator: raw MIDI bytes through the same parser and hub as a real device. ---

    private fun sendRaw(vararg bytes: Int) {
        simParser.parse(ByteArray(bytes.size) { bytes[it].toByte() }, 0, bytes.size, System.nanoTime()) { hub.dispatch(it) }
    }

    fun simNote(note: Int, velocity: Int) {
        viewModelScope.launch {
            sendRaw(0x90, note, velocity)
            delay(400)
            sendRaw(0x90, note, 0) // note-off sent as note-on velocity 0, like many keyboards
        }
    }

    fun simRandomVelocity() = simNote(60, Random.nextInt(20, 128))

    fun simChord() {
        viewModelScope.launch {
            sendRaw(0x90, 60, 80, 64, 80, 67, 80) // running status
            delay(700)
            sendRaw(0x80, 60, 0, 0x80, 64, 0, 0x80, 67, 0)
        }
    }

    fun simScale() {
        viewModelScope.launch {
            for (n in listOf(60, 62, 64, 65, 67, 69, 71, 72)) {
                sendRaw(0x90, n, 90)
                delay(250)
                sendRaw(0x80, n, 0)
            }
        }
    }

    fun simPedal(down: Boolean) = sendRaw(0xB0, 64, if (down) 127 else 0)

    private fun noteName(note: Int) = SpelledPitch.fromMidiSharp(note).name(NoteNameStyle.BOTH, withOctave = true)

    private fun InputSource.label() = when (this) {
        InputSource.TOUCH -> "จอ "
        InputSource.USB_MIDI -> "USB"
        InputSource.BLE_MIDI -> "BLE"
        InputSource.SIMULATED -> "SIM"
        InputSource.COMPUTER_KEYS -> "PC "
    }

    override fun onCleared() {
        ble.stop()
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        onStop()
    }

    companion object {
        const val PROBE_PRESSES = 5
        const val COUNT_IN = 4
        const val CLICKS = 16
    }
}
