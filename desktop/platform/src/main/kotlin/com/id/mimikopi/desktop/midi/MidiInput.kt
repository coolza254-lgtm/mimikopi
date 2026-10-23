package com.id.mimikopi.desktop.midi

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.MidiParser
import com.id.mimikopi.core.input.shifted
import com.id.mimikopi.desktop.input.InputHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.Sequencer
import javax.sound.midi.Synthesizer

/** A MIDI input port as the program sees it. */
data class MidiPort(val name: String, val description: String, val open: Boolean, val error: String? = null)

/**
 * MIDI keyboards (USB and any other port Windows lists). Every input port is opened when it appears and closed when
 * it disappears, checked every [POLL_MS] ms, so unplugging and plugging the piano back in needs no action. Notes
 * still held when a port goes away are released so nothing hangs.
 *
 * Only input is read here (javax.sound.midi's input ports; no Java sound output is used). Each message is stamped
 * with System.nanoTime when it arrives, the clock the judge, audio and keyboard input share.
 */
class MidiInput(private val hub: InputHub, private val octaveOffset: () -> Int) : AutoCloseable {
    private class Open(val info: MidiDevice.Info, val device: MidiDevice, val parser: MidiParser)

    private val open = LinkedHashMap<String, Open>()
    private val failed = HashMap<String, String>()
    private val _ports = MutableStateFlow<List<MidiPort>>(emptyList())
    val ports: StateFlow<List<MidiPort>> = _ports.asStateFlow()

    /** Last time any MIDI note arrived (System.nanoTime), so pages can tell "connected and playing" apart. */
    @Volatile var lastNoteNanos: Long = 0L
        private set

    private val poller = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mimikopi-midi").apply { isDaemon = true } }

    fun start() {
        poller.scheduleWithFixedDelay({ runCatching { scan() } }, 0, POLL_MS, TimeUnit.MILLISECONDS)
    }

    /** Looks for ports right away (the "ค้นหาใหม่" button). */
    fun rescan() {
        poller.execute { runCatching { scan() } }
    }

    private fun key(i: MidiDevice.Info) = "${i.name}|${i.vendor}|${i.description}"

    @Synchronized
    private fun scan() {
        val inputs = MidiSystem.getMidiDeviceInfo().mapNotNull { info ->
            val device = runCatching { MidiSystem.getMidiDevice(info) }.getOrNull() ?: return@mapNotNull null
            if (device is Sequencer || device is Synthesizer || device.maxTransmitters == 0) null else info to device
        }
        val present = inputs.associateBy { key(it.first) }
        // Gone: close and release whatever it held.
        for (k in open.keys.toList()) {
            if (k !in present) {
                open.remove(k)?.let { runCatching { it.device.close() } }
                hub.releaseAll()
            }
        }
        failed.keys.retainAll(present.keys)
        // New: open and listen.
        for ((k, pair) in present) {
            if (k in open || k in failed) continue
            val (info, device) = pair
            try {
                if (!device.isOpen) device.open()
                val parser = MidiParser(InputSource.USB_MIDI)
                device.transmitter.receiver = object : Receiver {
                    override fun send(message: MidiMessage, timeStamp: Long) {
                        val now = System.nanoTime()
                        val bytes = message.message
                        parser.parse(bytes, 0, message.length, now) { e -> deliver(e) }
                    }

                    override fun close() = Unit
                }
                open[k] = Open(info, device, parser)
            } catch (e: Exception) {
                runCatching { device.close() }
                failed[k] = e.message ?: e.javaClass.simpleName
            }
        }
        _ports.value = present.map { (k, pair) ->
            MidiPort(pair.first.name, pair.first.description, k in open, failed[k]?.let { tr("เปิดไม่ได้ (โปรแกรมอื่นอาจใช้อยู่): $it", "Couldn't open (another program may be using it): $it", "開けません（他のソフトが使用中かもしれません）：$it") })
        }
    }

    private fun deliver(e: InputEvent) {
        val shifted = e.shifted(octaveOffset()) ?: return
        if (shifted is InputEvent.NoteOn) lastNoteNanos = shifted.timeNanos
        hub.dispatch(shifted)
    }

    /** Ports that failed to open are tried again (another program may have let go of them). */
    @Synchronized
    fun retryFailed() {
        failed.clear()
        rescan()
    }

    override fun close() {
        poller.shutdownNow()
        synchronized(this) {
            open.values.forEach { runCatching { it.device.close() } }
            open.clear()
        }
    }

    companion object {
        const val POLL_MS = 1500L
    }
}
