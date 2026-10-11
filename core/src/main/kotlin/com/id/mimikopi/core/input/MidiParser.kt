package com.id.mimikopi.core.input

/**
 * Turns a raw MIDI 1.0 byte stream into [InputEvent]s. Handles running status, real-time bytes
 * interleaved anywhere (ignored), SysEx (skipped), note-on with velocity 0 as note-off, and CC64
 * as sustain (>= 64 = down). All channels are accepted. One parser per input port (it keeps state).
 */
class MidiParser(private val source: InputSource) {
    private var status = 0
    private val data = IntArray(2)
    private var count = 0
    private var inSysEx = false

    fun parse(bytes: ByteArray, offset: Int, length: Int, timeNanos: Long, out: (InputEvent) -> Unit) {
        for (i in offset until offset + length) feed(bytes[i].toInt() and 0xFF, timeNanos, out)
    }

    private fun feed(b: Int, t: Long, out: (InputEvent) -> Unit) {
        when {
            b >= 0xF8 -> return // real-time (clock, active sensing...): may appear mid-message
            b == 0xF0 -> { inSysEx = true; status = 0; return }
            b == 0xF7 -> { inSysEx = false; return }
            b >= 0xF1 -> { status = 0; count = 0; return } // system common: cancels running status
            b >= 0x80 -> { inSysEx = false; status = b; count = 0; return }
        }
        if (inSysEx || status == 0) return
        data[count++] = b
        val needed = when (status and 0xF0) { 0xC0, 0xD0 -> 1; else -> 2 }
        if (count < needed) return
        count = 0 // running status: the next data bytes reuse `status`
        when (status and 0xF0) {
            0x90 -> out(if (data[1] == 0) InputEvent.NoteOff(data[0], t, source) else InputEvent.NoteOn(data[0], data[1], t, source))
            0x80 -> out(InputEvent.NoteOff(data[0], t, source))
            0xB0 -> out(if (data[0] == 64) InputEvent.Sustain(data[1] >= 64, t, source) else InputEvent.Control(data[0], data[1], t, source))
        }
    }
}
