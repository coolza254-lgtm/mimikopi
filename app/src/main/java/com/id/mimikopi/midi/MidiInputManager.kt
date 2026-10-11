package com.id.mimikopi.midi

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.MidiParser
import com.id.mimikopi.core.input.shifted
import com.id.mimikopi.input.InputHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

data class MidiDeviceState(val id: Int, val name: String, val source: InputSource, val ports: Int, val open: Boolean, val error: String? = null)

/**
 * Listens for MIDI devices (USB first; Bluetooth MIDI devices that the system has paired also show up)
 * and feeds every output port into the [InputHub]. Hot-plug: devices are opened when added and closed
 * when removed; several devices can play at once.
 */
class MidiInputManager(private val context: Context, private val hub: InputHub) {
    private val midi: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) context.getSystemService(MidiManager::class.java) else null
    private val handler = Handler(Looper.getMainLooper())
    private val open = mutableMapOf<Int, Pair<MidiDevice, List<MidiOutputPort>>>()

    private val _devices = MutableStateFlow<List<MidiDeviceState>>(emptyList())
    val devices: StateFlow<List<MidiDeviceState>> = _devices.asStateFlow()

    val supported: Boolean get() = midi != null

    /** Semitones added to incoming notes (octave calibration). */
    @Volatile var octaveOffset: Int = 0

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) = openDevice(device)
        override fun onDeviceRemoved(device: MidiDeviceInfo) = closeDevice(device.id, removed = true)
    }

    @Suppress("DEPRECATION")
    fun start() {
        val m = midi ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            m.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, { handler.post(it) }, callback)
            m.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).forEach(::openDevice)
        } else {
            m.registerDeviceCallback(callback, handler)
            m.devices.forEach(::openDevice)
        }
    }

    private fun sourceOf(info: MidiDeviceInfo) = when (info.type) {
        MidiDeviceInfo.TYPE_USB -> InputSource.USB_MIDI
        MidiDeviceInfo.TYPE_BLUETOOTH -> InputSource.BLE_MIDI
        else -> InputSource.SIMULATED
    }

    private fun nameOf(info: MidiDeviceInfo): String =
        info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: listOfNotNull(info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER), info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)).joinToString(" ").ifBlank { "MIDI ${info.id}" }

    private fun openDevice(info: MidiDeviceInfo) {
        val m = midi ?: return
        if (info.outputPortCount == 0 || open.containsKey(info.id)) return
        val source = sourceOf(info)
        // Our own virtual devices (none today) and other apps' virtual ports are skipped.
        if (source == InputSource.SIMULATED) return
        update(MidiDeviceState(info.id, nameOf(info), source, info.outputPortCount, open = false))
        m.openDevice(info, { device ->
            if (device == null) {
                update(MidiDeviceState(info.id, nameOf(info), source, info.outputPortCount, open = false, error = "open failed"))
                return@openDevice
            }
            val ports = (0 until info.outputPortCount).mapNotNull { i ->
                device.openOutputPort(i)?.also { port -> port.connect(receiverFor(source)) }
            }
            open[info.id] = device to ports
            update(MidiDeviceState(info.id, nameOf(info), source, ports.size, open = ports.isNotEmpty(), error = if (ports.isEmpty()) "no ports" else null))
        }, handler)
    }

    private fun receiverFor(source: InputSource) = object : MidiReceiver() {
        private val parser = MidiParser(source) // one parser per port: running status is per stream

        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            // Timestamps are System.nanoTime() based (CLOCK_MONOTONIC), the same clock as audio timestamps.
            val t = if (timestamp > 0) timestamp else System.nanoTime()
            parser.parse(msg, offset, count, t) { event -> event.shifted(octaveOffset)?.let(hub::dispatch) }
        }
    }

    private fun closeDevice(id: Int, removed: Boolean) {
        open.remove(id)?.let { (device, ports) ->
            ports.forEach { runCatching { it.close() } }
            try {
                device.close()
            } catch (_: IOException) {
            }
        }
        if (removed) {
            _devices.value = _devices.value.filterNot { it.id == id }
            hub.releaseAll() // a key held while unplugging must not ring forever
        }
    }

    private fun update(state: MidiDeviceState) {
        _devices.value = _devices.value.filterNot { it.id == state.id } + state
    }
}
