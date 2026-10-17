package com.id.mimikopi.midi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A Bluetooth LE MIDI device seen while scanning. */
data class BleMidiDevice(val address: String, val name: String)

/**
 * Finds Bluetooth LE MIDI instruments (the standard BLE-MIDI service). A scan runs only when the user presses
 * search, ends after [scan]'s seconds (or earlier when stopped) and then says what it found, if anything. It needs the "nearby devices" permission (Android 12+) or location (older Android). Nothing is sent
 * anywhere: the scan only lists instruments in range.
 */
class BleMidiScanner(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _found = MutableStateFlow<List<BleMidiDevice>>(emptyList())
    val found: StateFlow<List<BleMidiDevice>> = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _finished = MutableStateFlow(false)
    /** A scan ran to its end (or failed): with nothing in [found], nothing was found. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()
    private val STOP = Any()

    val supported: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true

    fun hasPermissions(): Boolean = REQUIRED.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private val callback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device ?: return
            val name = result.scanRecord?.deviceName ?: runCatching { d.name }.getOrNull() ?: d.address
            if (_found.value.none { it.address == d.address }) _found.value = _found.value + BleMidiDevice(d.address, name)
        }

        override fun onScanFailed(errorCode: Int) {
            handler.removeCallbacksAndMessages(STOP)
            _scanning.value = false
            _finished.value = true
        }
    }

    @SuppressLint("MissingPermission")
    fun scan(seconds: Long = 10) {
        if (!hasPermissions() || !enabled || _scanning.value) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        _found.value = emptyList()
        _finished.value = false
        _scanning.value = true
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(MIDI_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(listOf(filter), settings, callback) }.onFailure { _scanning.value = false; _finished.value = true; return }
        handler.postAtTime({ stop() }, STOP, android.os.SystemClock.uptimeMillis() + seconds * 1000)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacksAndMessages(STOP)
        if (!_scanning.value) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        _scanning.value = false
        _finished.value = true
    }

    companion object {
        /** The BLE-MIDI service (MIDI Manufacturers Association). */
        val MIDI_SERVICE: java.util.UUID = java.util.UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")

        /** Runtime permissions for scanning and connecting. */
        val REQUIRED: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
