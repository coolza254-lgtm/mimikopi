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
import com.id.mimikopi.core.input.IdentityProbe
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.MidiLiveness
import com.id.mimikopi.core.input.MidiParser
import com.id.mimikopi.core.input.shifted
import com.id.mimikopi.input.InputHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

data class MidiDeviceState(
    val id: Int,
    val name: String,
    val source: InputSource,
    val ports: Int,
    val open: Boolean,
    val error: String? = null,
    /** The instrument sends Active Sensing, so the app can tell when it is switched off. */
    val sendsSensing: Boolean = false,
    /** Still listed but silent: its Active Sensing or its identity answers stopped (switched off while still linked). */
    val silent: Boolean = false,
    /** The instrument answers the MIDI identity request, so the app can tell when it is switched off. */
    val answersProbe: Boolean = false,
    /** The last key played on it and when (System.nanoTime), to show that notes really arrive. */
    val lastNote: Int? = null,
    val lastNoteNanos: Long? = null,
) {
    /** The app can tell whether it is switched on (Active Sensing or identity answers). */
    val verified: Boolean get() = sendsSensing || answersProbe

    /** Connected and, as far as the app can tell, switched on. */
    val ready: Boolean get() = open && !silent
}

/**
 * Listens for MIDI devices and feeds every output port into the [InputHub]. USB devices are opened when they
 * are plugged in and closed when removed. Bluetooth LE MIDI devices are connected on request
 * ([connectBluetooth]) and remembered, so the app reconnects to them by itself when it starts or when the
 * connection drops. Several devices can play at once.
 *
 * "Connected" is checked three ways, because Android alone can keep listing an instrument that is off: Active
 * Sensing silence ([MidiLiveness], for instruments that send it), the Bluetooth link dropping (ACL disconnect, at
 * once instead of after the BLE timeout), and a re-check of the system's device list when the app comes back.
 */
class MidiInputManager(private val context: Context, private val hub: InputHub) {
    private val midi: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) context.getSystemService(MidiManager::class.java) else null
    private val handler = Handler(Looper.getMainLooper())
    private val open = mutableMapOf<Int, Pair<MidiDevice, List<MidiOutputPort>>>()
    private val liveness = java.util.concurrent.ConcurrentHashMap<Int, MidiLiveness>()
    /** Ports into the instrument (for the identity request), its probe state and the last key played on it. */
    private val inputs = mutableMapOf<Int, android.media.midi.MidiInputPort>()
    private val probes = java.util.concurrent.ConcurrentHashMap<Int, IdentityProbe>()
    private val lastNotes = java.util.concurrent.ConcurrentHashMap<Int, Pair<Int, Long>>()
    private var ticks = 0
    private val gattMisses = mutableMapOf<Int, Int>()

    private val _devices = MutableStateFlow<List<MidiDeviceState>>(emptyList())
    val devices: StateFlow<List<MidiDeviceState>> = _devices.asStateFlow()

    val supported: Boolean get() = midi != null

    /** Semitones added to incoming notes (octave calibration). */
    @Volatile var octaveOffset: Int = 0

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) = openDevice(device)
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            val wasBle = open[device.id]?.first?.info?.type == MidiDeviceInfo.TYPE_BLUETOOTH
            closeDevice(device.id, removed = true)
            if (wasBle) scheduleReconnect()
        }
    }

    // ---------------- Bluetooth LE MIDI ----------------

    /** Address of the remembered Bluetooth MIDI instrument (set by the MIDI screen, kept in settings). */
    @Volatile var bleAddress: String? = null
    private var reconnectTries = 0
    private val RECONNECT = Any()

    /** Opens a Bluetooth LE MIDI instrument by address (needs the Bluetooth connect permission). */
    @android.annotation.SuppressLint("MissingPermission")
    fun connectBluetooth(address: String) {
        val m = midi ?: return
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) return
        if (open.values.any { (d, _) -> d.info.type == MidiDeviceInfo.TYPE_BLUETOOTH && d.info.properties.getParcelableCompat()?.address == address }) return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        bleAddress = address
        runCatching {
            m.openBluetoothDevice(device, { midiDevice ->
                if (midiDevice == null) { scheduleReconnect(); return@openBluetoothDevice }
                reconnectTries = 0
                attach(midiDevice)
            }, handler)
        }
    }

    /** Disconnects and forgets the Bluetooth instrument. */
    fun forgetBluetooth() {
        bleAddress = null
        handler.removeCallbacksAndMessages(RECONNECT)
        open.filterValues { (d, _) -> d.info.type == MidiDeviceInfo.TYPE_BLUETOOTH }.keys.toList().forEach { closeDevice(it, removed = true) }
    }

    /** A dropped Bluetooth link is retried a few times (the piano may have been switched off or moved away). */
    private fun scheduleReconnect() {
        val address = bleAddress ?: return
        if (reconnectTries >= 6) return
        reconnectTries++
        handler.postAtTime({ connectBluetooth(address) }, RECONNECT, android.os.SystemClock.uptimeMillis() + 5_000L * reconnectTries)
    }

    @Suppress("DEPRECATION")
    private fun android.os.Bundle.getParcelableCompat(): android.bluetooth.BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE, android.bluetooth.BluetoothDevice::class.java)
        else getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE)

    /** Opens the output ports of an already opened device (Bluetooth path). */
    private fun attach(device: MidiDevice) {
        val info = device.info
        if (open.containsKey(info.id)) { runCatching { device.close() }; return }
        val source = sourceOf(info)
        val ports = (0 until info.outputPortCount).mapNotNull { i -> device.openOutputPort(i)?.also { port -> port.connect(receiverFor(source, info.id)) } }
        open[info.id] = device to ports
        openInput(device)
        update(MidiDeviceState(info.id, nameOf(info), source, ports.size, open = ports.isNotEmpty(), error = if (ports.isEmpty()) "no ports" else null))
    }

    /** The port into the instrument, for the identity request (instruments without one are judged by the other signs). */
    private fun openInput(device: MidiDevice) {
        val id = device.info.id
        probes[id] = IdentityProbe()
        if (device.info.inputPortCount > 0) runCatching { device.openInputPort(0) }.getOrNull()?.let { inputs[id] = it }
    }

    /**
     * Every 400 ms: asks instruments that answer whether they are still there, checks Active Sensing and identity
     * silence, and the real Bluetooth link (Android can keep listing a BLE instrument whose link is gone).
     */
    private val watchdog = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            for ((id, port) in inputs.toMap()) {
                val p = probes[id] ?: continue
                val send = synchronized(p) { p.shouldSend(now).also { if (it) p.sent(now) } }
                if (send) runCatching { port.send(IdentityProbe.REQUEST, 0, IdentityProbe.REQUEST.size) }
            }
            if (ticks++ % 3 == 0) checkBluetoothLinks()
            for (state in _devices.value) {
                val l = liveness[state.id]
                val p = probes[state.id]
                val silent = (l?.let { synchronized(it) { it.silent(now) } } ?: false) || (p?.let { synchronized(it) { it.silent(now) } } ?: false)
                val senses = l?.let { synchronized(it) { it.sendsSensing } } ?: state.sendsSensing
                val answers = p?.let { synchronized(it) { it.answers } } ?: state.answersProbe
                val note = lastNotes[state.id]
                if (silent != state.silent || senses != state.sendsSensing || answers != state.answersProbe || note?.second != state.lastNoteNanos) {
                    update(state.copy(silent = silent, sendsSensing = senses, answersProbe = answers, lastNote = note?.first ?: state.lastNote, lastNoteNanos = note?.second ?: state.lastNoteNanos))
                    if (silent && !state.silent) hub.releaseAll() // keys held when it went off must not ring forever
                }
            }
            handler.postDelayed(this, 400)
        }
    }

    /** A BLE instrument whose GATT link is down (two checks in a row) is closed and reconnected when it comes back. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun checkBluetoothLinks() {
        val bm = context.getSystemService(android.bluetooth.BluetoothManager::class.java) ?: return
        for ((id, pair) in open.toMap()) {
            val info = pair.first.info
            if (info.type != MidiDeviceInfo.TYPE_BLUETOOTH) continue
            val device = info.properties.getParcelableCompat() ?: continue
            val state = runCatching { bm.getConnectionState(device, android.bluetooth.BluetoothProfile.GATT) }.getOrNull() ?: continue
            if (state == android.bluetooth.BluetoothProfile.STATE_CONNECTED) { gattMisses.remove(id); continue }
            val misses = (gattMisses[id] ?: 0) + 1
            gattMisses[id] = misses
            if (misses >= 2) {
                gattMisses.remove(id)
                closeDevice(id, removed = true)
                scheduleReconnect()
            }
        }
    }

    /** Bluetooth: the link to the instrument dropped, or Bluetooth was switched off. */
    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: android.content.Intent) {
            when (intent.action) {
                android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val gone = intent.getDeviceCompat()?.address ?: return
                    val ids = open.filterValues { (d, _) -> d.info.type == MidiDeviceInfo.TYPE_BLUETOOTH && d.info.properties.getParcelableCompat()?.address == gone }.keys.toList()
                    if (ids.isEmpty()) return
                    ids.forEach { closeDevice(it, removed = true) }
                    scheduleReconnect()
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)
                    if (state == android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF || state == android.bluetooth.BluetoothAdapter.STATE_OFF) {
                        open.filterValues { (d, _) -> d.info.type == MidiDeviceInfo.TYPE_BLUETOOTH }.keys.toList().forEach { closeDevice(it, removed = true) }
                    } else if (state == android.bluetooth.BluetoothAdapter.STATE_ON) {
                        reconnectTries = 0
                        scheduleReconnect()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun android.content.Intent.getDeviceCompat(): android.bluetooth.BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
        else getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)

    /**
     * Re-checks the system's device list (when the app comes back to the screen): devices that are gone are closed,
     * new ones opened. Callbacks can be missed while the app is in the background.
     */
    @Suppress("DEPRECATION")
    fun resync() {
        val m = midi ?: return
        val listed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) m.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM) else m.devices.toSet()
        val ids = listed.map { it.id }.toSet()
        (open.keys - ids).toList().forEach { id ->
            val wasBle = open[id]?.first?.info?.type == MidiDeviceInfo.TYPE_BLUETOOTH
            closeDevice(id, removed = true)
            if (wasBle) scheduleReconnect()
        }
        _devices.value.filter { it.id !in ids && it.id !in open }.forEach { closeDevice(it.id, removed = true) }
        listed.forEach(::openDevice)
    }

    @Suppress("DEPRECATION")
    fun start() {
        val m = midi ?: return
        handler.post(watchdog)
        val filter = android.content.IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // System broadcasts only; ACL events need the Bluetooth connect permission (granted with the MIDI screen's scan).
        runCatching { androidx.core.content.ContextCompat.registerReceiver(context, bluetoothReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
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
        // Our own virtual devices (none today) and other apps' virtual ports are skipped. Bluetooth devices are
        // opened through connectBluetooth (the system lists them only once some app has connected them).
        if (source == InputSource.SIMULATED || source == InputSource.BLE_MIDI) return
        update(MidiDeviceState(info.id, nameOf(info), source, info.outputPortCount, open = false))
        m.openDevice(info, { device ->
            if (device == null) {
                update(MidiDeviceState(info.id, nameOf(info), source, info.outputPortCount, open = false, error = "open failed"))
                return@openDevice
            }
            val ports = (0 until info.outputPortCount).mapNotNull { i ->
                device.openOutputPort(i)?.also { port -> port.connect(receiverFor(source, info.id)) }
            }
            open[info.id] = device to ports
            openInput(device)
            update(MidiDeviceState(info.id, nameOf(info), source, ports.size, open = ports.isNotEmpty(), error = if (ports.isEmpty()) "no ports" else null))
        }, handler)
    }

    private fun receiverFor(source: InputSource, deviceId: Int) = object : MidiReceiver() {
        private val parser = MidiParser(source) // one parser per port: running status is per stream
        private val alive = liveness.getOrPut(deviceId) { MidiLiveness() } // one per device, shared by its ports

        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val now = System.nanoTime()
            synchronized(alive) { alive.received(msg, offset, count, now) }
            probes[deviceId]?.let { p -> synchronized(p) { p.received(msg, offset, count, now) } }
            // Timestamps are System.nanoTime() based (CLOCK_MONOTONIC), the same clock as audio timestamps.
            val t = if (timestamp > 0) timestamp else now
            parser.parse(msg, offset, count, t) { event ->
                if (event is InputEvent.NoteOn) lastNotes[deviceId] = event.note to now
                event.shifted(octaveOffset)?.let(hub::dispatch)
            }
        }
    }

    private fun closeDevice(id: Int, removed: Boolean) {
        liveness.remove(id)
        probes.remove(id)
        lastNotes.remove(id)
        inputs.remove(id)?.let { runCatching { it.close() } }
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
        val before = _devices.value.firstOrNull { it.id == state.id }
        // Re-opening keeps what is known about the instrument's liveness.
        val kept = if (before != null && state.open && !state.sendsSensing && before.sendsSensing) state.copy(sendsSensing = true, silent = before.silent) else state
        // A reopen keeps the last key shown.
        val merged = if (before != null && kept.lastNote == null) kept.copy(lastNote = before.lastNote, lastNoteNanos = before.lastNoteNanos) else kept
        _devices.value = _devices.value.filterNot { it.id == state.id } + merged
    }
}
