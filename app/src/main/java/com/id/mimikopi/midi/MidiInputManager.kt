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
import com.id.mimikopi.core.input.MidiNotice
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

/** Where a Bluetooth connection the user asked for stands (a connected instrument is in the device list). */
sealed interface BleLink {
    data object Idle : BleLink
    data class Connecting(val address: String, val name: String) : BleLink
    data class Failed(val address: String, val name: String) : BleLink
}

/**
 * Listens for MIDI devices and feeds every output port into the [InputHub]. USB devices are opened the moment
 * they are plugged in and closed when the cable is pulled out, each time with a [notice]. Bluetooth LE MIDI
 * instruments are connected only when the user asks ([connectBluetooth]): the app never searches or reconnects
 * by itself, and a connection attempt ends after [CONNECT_TIMEOUT_MS] with an answer. Several devices can play
 * at once.
 *
 * A listed, open device is connected. Android can keep listing an instrument that is off, so silence is also
 * watched: Active Sensing ([MidiLiveness]) and identity answers ([IdentityProbe]) that stop, and a Bluetooth
 * link that is gone (ACL disconnect, GATT state).
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

    private val _notice = MutableStateFlow<MidiNotice?>(null)
    /** The latest plug, unplug or Bluetooth change, for the short notice shown on every screen. */
    val notice: StateFlow<MidiNotice?> = _notice.asStateFlow()

    private val _bleLink = MutableStateFlow<BleLink>(BleLink.Idle)
    val bleLink: StateFlow<BleLink> = _bleLink.asStateFlow()

    private fun tell(kind: MidiNotice.Kind, name: String) {
        _notice.value = MidiNotice(kind, name)
    }

    /** Semitones added to incoming notes (octave calibration). */
    @Volatile var octaveOffset: Int = 0

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) = openDevice(device, announce = true)
        override fun onDeviceRemoved(device: MidiDeviceInfo) = drop(device.id)
    }

    /** Closes a device that went away and says so (cable out, or the Bluetooth link lost). */
    private fun drop(id: Int) {
        val state = _devices.value.firstOrNull { it.id == id }
        closeDevice(id, removed = true)
        if (state != null) tell(if (state.source == InputSource.BLE_MIDI) MidiNotice.Kind.BLE_LOST else MidiNotice.Kind.UNPLUGGED, state.name)
    }

    // ---------------- Bluetooth LE MIDI ----------------

    private val CONNECT_TIMEOUT = Any()
    /** A connection the user stopped: if it still completes, it is closed at once. */
    private var cancelled: String? = null

    /**
     * Opens a Bluetooth LE MIDI instrument by address, once (needs the Bluetooth connect permission). Answers within
     * [CONNECT_TIMEOUT_MS]: connected (it appears in [devices]) or [BleLink.Failed]. Nothing is retried by itself.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun connectBluetooth(address: String, name: String) {
        val m = midi ?: return
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled || _bleLink.value is BleLink.Connecting) return
        if (open.values.any { (d, _) -> d.info.type == MidiDeviceInfo.TYPE_BLUETOOTH && d.info.properties.getParcelableCompat()?.address == address }) return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return fail(address, name)
        cancelled = null
        _bleLink.value = BleLink.Connecting(address, name)
        handler.postAtTime({ if ((_bleLink.value as? BleLink.Connecting)?.address == address) fail(address, name) },
            CONNECT_TIMEOUT, android.os.SystemClock.uptimeMillis() + CONNECT_TIMEOUT_MS)
        val started = runCatching {
            m.openBluetoothDevice(device, { midiDevice ->
                handler.removeCallbacksAndMessages(CONNECT_TIMEOUT)
                when {
                    midiDevice == null -> if (_bleLink.value == BleLink.Connecting(address, name)) fail(address, name)
                    address == cancelled -> runCatching { midiDevice.close() }
                    // Connected, even if later than the time limit.
                    else -> { _bleLink.value = BleLink.Idle; attach(midiDevice, announce = true) }
                }
            }, handler)
        }
        if (started.isFailure) fail(address, name)
    }

    private fun fail(address: String, name: String) {
        handler.removeCallbacksAndMessages(CONNECT_TIMEOUT)
        _bleLink.value = BleLink.Failed(address, name)
        tell(MidiNotice.Kind.BLE_FAILED, name)
    }

    /** A new search starts from a clean slate (the last failure is no longer the news). */
    fun clearBluetoothFailure() {
        if (_bleLink.value is BleLink.Failed) _bleLink.value = BleLink.Idle
    }

    /** Disconnects the Bluetooth instrument (and stops a connection attempt). */
    fun disconnectBluetooth() {
        handler.removeCallbacksAndMessages(CONNECT_TIMEOUT)
        (_bleLink.value as? BleLink.Connecting)?.let { cancelled = it.address }
        _bleLink.value = BleLink.Idle
        open.filterValues { (d, _) -> d.info.type == MidiDeviceInfo.TYPE_BLUETOOTH }.keys.toList().forEach { closeDevice(it, removed = true) }
    }

    @Suppress("DEPRECATION")
    private fun android.os.Bundle.getParcelableCompat(): android.bluetooth.BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE, android.bluetooth.BluetoothDevice::class.java)
        else getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE)

    /** Opens the output ports of an already opened device (Bluetooth path). */
    private fun attach(device: MidiDevice, announce: Boolean) {
        val info = device.info
        if (open.containsKey(info.id)) { runCatching { device.close() }; return }
        val source = sourceOf(info)
        val ports = (0 until info.outputPortCount).mapNotNull { i -> device.openOutputPort(i)?.also { port -> port.connect(receiverFor(source, info.id)) } }
        open[info.id] = device to ports
        openInput(device)
        update(MidiDeviceState(info.id, nameOf(info), source, ports.size, open = ports.isNotEmpty(), error = if (ports.isEmpty()) "no ports" else null))
        if (announce && ports.isNotEmpty()) tell(MidiNotice.Kind.BLE_CONNECTED, nameOf(info))
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

    /** A BLE instrument whose GATT link is down (two checks in a row) is closed; the user connects it again when they want. */
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
                drop(id)
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
                    ids.forEach(::drop)
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)
                    if (state == android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF || state == android.bluetooth.BluetoothAdapter.STATE_OFF) {
                        open.filterValues { (d, _) -> d.info.type == MidiDeviceInfo.TYPE_BLUETOOTH }.keys.toList().forEach(::drop)
                        (_bleLink.value as? BleLink.Connecting)?.let { fail(it.address, it.name) }
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
     * Re-checks the system's device list (when the app comes back, or the user asks): devices that are gone are
     * closed, new ones opened, each with a notice. Callbacks can be missed while the app is in the background.
     */
    @Suppress("DEPRECATION")
    fun resync() {
        val m = midi ?: return
        val listed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) m.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM) else m.devices.toSet()
        val ids = listed.map { it.id }.toSet()
        // Bluetooth instruments are watched by their link (GATT, ACL) instead: the list can lag behind them.
        val usb = _devices.value.filter { it.source != InputSource.BLE_MIDI }.map { it.id }
        (usb - ids).forEach(::drop)
        listed.forEach { openDevice(it, announce = true) }
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
            m.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).forEach { openDevice(it, announce = false) }
        } else {
            m.registerDeviceCallback(callback, handler)
            m.devices.forEach { openDevice(it, announce = false) }
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

    /** Opens a USB instrument; [announce] tells the user (not for the ones already there when the app starts). */
    private fun openDevice(info: MidiDeviceInfo, announce: Boolean) {
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
            if (announce && ports.isNotEmpty()) tell(MidiNotice.Kind.PLUGGED, nameOf(info))
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

    companion object {
        /** How long one Bluetooth connection attempt may take before the user is told it failed. */
        const val CONNECT_TIMEOUT_MS = 15_000L
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
