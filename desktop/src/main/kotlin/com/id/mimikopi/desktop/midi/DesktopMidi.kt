package com.id.mimikopi.desktop.midi

import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.MidiLiveness
import com.id.mimikopi.core.input.MidiParser
import com.id.mimikopi.core.input.shifted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.concurrent.ConcurrentHashMap
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.Sequencer
import javax.sound.midi.Synthesizer
import kotlin.concurrent.fixedRateTimer

/** One MIDI keyboard as the app sees it. */
data class MidiDeviceState(val key: String, val name: String, val open: Boolean, val error: String? = null, val sendsSensing: Boolean = false, val silent: Boolean = false) {
    val ready: Boolean get() = open && !silent
}

/**
 * MIDI input on the desktop (javax.sound.midi): every input device is opened and fed into [onEvent]. Java Sound has
 * no plug/unplug events, so the device list is polled every two seconds; Active Sensing silence marks an instrument
 * as switched off (same rule as on Android, [MidiLiveness]).
 */
class DesktopMidi(private val onEvent: (InputEvent) -> Unit, private val onSilent: () -> Unit) {
    private class Open(val device: MidiDevice, val liveness: MidiLiveness)

    private val open = ConcurrentHashMap<String, Open>()
    private val _devices = MutableStateFlow<List<MidiDeviceState>>(emptyList())
    val devices: StateFlow<List<MidiDeviceState>> = _devices.asStateFlow()
    private var timer: Timer? = null

    /** Semitones added to incoming notes (octave calibration). */
    @Volatile var octaveOffset: Int = 0

    fun start() {
        if (timer != null) return
        timer = fixedRateTimer("mimikopi-midi", daemon = true, initialDelay = 0, period = 500) { tick() }
    }

    fun stop() {
        timer?.cancel()
        timer = null
        open.values.forEach { runCatching { it.device.close() } }
        open.clear()
    }

    private var polls = 0

    private fun tick() {
        if (polls++ % 4 == 0) rescan()
        refreshLiveness()
    }

    /** Opens new input devices and forgets ones that disappeared. */
    @Synchronized
    fun rescan() {
        val infos = runCatching { MidiSystem.getMidiDeviceInfo().toList() }.getOrDefault(emptyList())
        val inputs = infos.mapNotNull { info ->
            val d = runCatching { MidiSystem.getMidiDevice(info) }.getOrNull() ?: return@mapNotNull null
            if (d is Sequencer || d is Synthesizer || d.maxTransmitters == 0) null else keyOf(info) to (info to d)
        }.toMap()
        // Gone: close.
        (open.keys - inputs.keys).forEach { k -> open.remove(k)?.let { runCatching { it.device.close() } } }
        val states = mutableListOf<MidiDeviceState>()
        for ((k, pair) in inputs) {
            val (info, d) = pair
            val existing = open[k]
            if (existing != null) {
                states += current(k) ?: MidiDeviceState(k, info.name, open = true)
                continue
            }
            val result = runCatching {
                d.open()
                val liveness = MidiLiveness()
                val parser = MidiParser(InputSource.USB_MIDI)
                d.transmitter.receiver = object : Receiver {
                    override fun send(message: MidiMessage, timeStamp: Long) {
                        val now = System.nanoTime()
                        val bytes = message.message
                        val len = message.length
                        synchronized(liveness) { liveness.received(bytes, 0, len, now) }
                        parser.parse(bytes, 0, len, now) { e -> e.shifted(octaveOffset)?.let(onEvent) }
                    }
                    override fun close() = Unit
                }
                open[k] = Open(d, liveness)
            }
            states += MidiDeviceState(k, info.name, open = result.isSuccess, error = result.exceptionOrNull()?.message)
        }
        _devices.value = states.sortedBy { it.name }
    }

    private fun current(k: String) = _devices.value.firstOrNull { it.key == k }

    private fun refreshLiveness() {
        val now = System.nanoTime()
        var changed = false
        var wentSilent = false
        val next = _devices.value.map { s ->
            val o = open[s.key] ?: return@map s
            val silent = synchronized(o.liveness) { o.liveness.silent(now) }
            val senses = synchronized(o.liveness) { o.liveness.sendsSensing }
            if (silent != s.silent || senses != s.sendsSensing) {
                changed = true
                if (silent) wentSilent = true
                s.copy(silent = silent, sendsSensing = senses)
            } else s
        }
        if (changed) _devices.value = next
        if (wentSilent) onSilent()
    }

    private fun keyOf(info: MidiDevice.Info) = "${info.name}|${info.vendor}|${info.description}"
}
