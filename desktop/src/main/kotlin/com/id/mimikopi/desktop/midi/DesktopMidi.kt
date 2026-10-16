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

/** One MIDI keyboard as the app sees it; [messages] and [lastMessage] show whether anything arrives from it. */
data class MidiDeviceState(
    val key: String,
    val name: String,
    val open: Boolean,
    val error: String? = null,
    val sendsSensing: Boolean = false,
    val silent: Boolean = false,
    val messages: Long = 0,
    val lastMessage: String? = null,
) {
    val ready: Boolean get() = open && !silent
}

/**
 * MIDI input on the desktop (javax.sound.midi): every input device is opened and fed into [onEvent]. Java Sound has
 * no plug/unplug events, so the device list is polled every two seconds; Active Sensing silence marks an instrument
 * as switched off (same rule as on Android, [MidiLiveness]).
 */
class DesktopMidi(private val onEvent: (InputEvent) -> Unit, private val onSilent: () -> Unit) {
    private class Open(val device: MidiDevice, val liveness: MidiLiveness) {
        @Volatile var messages = 0L
        @Volatile var last: String? = null
    }

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
        // An exception escaping a TimerTask stops the Timer for good: the keyboard would never be found again.
        runCatching {
            if (polls++ % 4 == 0) rescan()
            refreshLiveness()
        }.onFailure { lastFailure = "${it::class.simpleName}: ${it.message}" }
    }

    @Volatile private var lastFailure: String? = null

    /** Opens new input devices and forgets ones that disappeared. */
    @Synchronized
    fun rescan() {
        val infos = runCatching { MidiSystem.getMidiDeviceInfo().toList() }
            .onFailure { lastFailure = "getMidiDeviceInfo: ${it.message}" }.getOrDefault(emptyList())
        // Ports with the same name (some keyboards, or Windows' own MIDI service) are numbered, so none hides another.
        val seen = HashMap<String, Int>()
        val inputs = infos.mapNotNull { info ->
            val d = runCatching { MidiSystem.getMidiDevice(info) }.getOrNull() ?: return@mapNotNull null
            if (d is Sequencer || d is Synthesizer || runCatching { d.maxTransmitters }.getOrDefault(0) == 0) return@mapNotNull null
            val base = keyOf(info)
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            (if (n == 1) base else "$base#$n") to (info to d)
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
                val o = Open(d, liveness)
                d.transmitter.receiver = object : Receiver {
                    override fun send(message: MidiMessage, timeStamp: Long) {
                        val now = System.nanoTime()
                        val bytes = message.message
                        val len = message.length
                        synchronized(liveness) { liveness.received(bytes, 0, len, now) }
                        if (len > 0 && bytes[0].toInt() and 0xFF != MidiLiveness.ACTIVE_SENSING) {
                            o.messages++
                            o.last = describe(bytes, len)
                        }
                        parser.parse(bytes, 0, len, now) { e -> e.shifted(octaveOffset)?.let(onEvent) }
                    }
                    override fun close() = Unit
                }
                open[k] = o
            }
            if (result.isFailure) runCatching { d.close() }
            states += MidiDeviceState(k, info.name, open = result.isSuccess, error = result.exceptionOrNull()?.let { explain(it) })
        }
        _devices.value = states.sortedBy { it.name }
    }

    private fun current(k: String) = _devices.value.firstOrNull { it.key == k }

    @Synchronized
    private fun refreshLiveness() {
        val now = System.nanoTime()
        var changed = false
        var wentSilent = false
        val next = _devices.value.map { s ->
            val o = open[s.key] ?: return@map s
            val silent = synchronized(o.liveness) { o.liveness.silent(now) }
            val senses = synchronized(o.liveness) { o.liveness.sendsSensing }
            if (silent != s.silent || senses != s.sendsSensing || o.messages != s.messages) {
                changed = true
                if (silent && !s.silent) wentSilent = true
                s.copy(silent = silent, sendsSensing = senses, messages = o.messages, lastMessage = o.last)
            } else s
        }
        if (changed) _devices.value = next
        if (wentSilent) onSilent()
    }

    private fun keyOf(info: MidiDevice.Info) = "${info.name}|${info.vendor}|${info.description}"

    /** A short readable form of a message ("โน้ต C4 (60) แรง 72"). */
    private fun describe(bytes: ByteArray, len: Int): String {
        val st = bytes[0].toInt() and 0xFF
        val d1 = if (len > 1) bytes[1].toInt() and 0xFF else 0
        val d2 = if (len > 2) bytes[2].toInt() and 0xFF else 0
        return when (st and 0xF0) {
            0x90 -> if (d2 > 0) "โน้ต ${noteName(d1)} ($d1) แรง $d2" else "ปล่อย ${noteName(d1)}"
            0x80 -> "ปล่อย ${noteName(d1)}"
            0xB0 -> if (d1 == 64) "แป้นค้างเสียง ${if (d2 >= 64) "เหยียบ" else "ปล่อย"}" else "CC $d1 = $d2"
            else -> bytes.take(len).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        }
    }

    private fun noteName(n: Int) = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")[n % 12] + (n / 12 - 1)

    /** Windows' MIDI errors in Thai where the cause is known. */
    private fun explain(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            "already in use" in m || "allocated" in m.lowercase() ->
                "โปรแกรมอื่นกำลังใช้คีย์บอร์ดนี้อยู่ (Windows ให้ใช้ได้ทีละโปรแกรม) ปิดโปรแกรมนั้นแล้วกดตรวจใหม่"
            else -> "เปิดไม่ได้: $m"
        }
    }

    /**
     * Everything Java Sound sees, for finding out why a keyboard does not connect: every MIDI device with its kind,
     * this app's state for each input, and the Java and Windows versions. Shown in Settings and copied on request.
     */
    fun report(): String = buildString {
        appendLine("Mimikopi MIDI report")
        appendLine("Java ${System.getProperty("java.version")} · ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        val infos = runCatching { MidiSystem.getMidiDeviceInfo().toList() }
        infos.exceptionOrNull()?.let { appendLine("getMidiDeviceInfo failed: ${it::class.simpleName}: ${it.message}") }
        val list = infos.getOrDefault(emptyList())
        appendLine("Devices seen by Java: ${list.size}")
        for (info in list) {
            val d = runCatching { MidiSystem.getMidiDevice(info) }
            val kind = d.getOrNull()?.let { dev ->
                when {
                    dev is Synthesizer -> "synth"
                    dev is Sequencer -> "sequencer"
                    else -> {
                        val tx = runCatching { dev.maxTransmitters }.getOrDefault(0)
                        val rx = runCatching { dev.maxReceivers }.getOrDefault(0)
                        listOfNotNull("in".takeIf { tx != 0 }, "out".takeIf { rx != 0 }).joinToString("+").ifEmpty { "none" } +
                            " (${dev::class.java.simpleName}, open=${dev.isOpen})"
                    }
                }
            } ?: "error: ${d.exceptionOrNull()?.message}"
            appendLine("- ${info.name} | ${info.vendor} | ${info.description} | $kind")
        }
        appendLine("Inputs opened by the app:")
        if (_devices.value.isEmpty()) appendLine("  (none)")
        for (s in _devices.value) {
            appendLine("  ${s.name}: open=${s.open} error=${s.error} sensing=${s.sendsSensing} silent=${s.silent} messages=${s.messages} last=${s.lastMessage}")
        }
        lastFailure?.let { appendLine("Last failure: $it") }
        appendLine("Polling: ${if (timer != null) "on" else "off"}")
    }
}
