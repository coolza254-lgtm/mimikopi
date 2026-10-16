package com.id.mimikopi.core.input

/**
 * Whether a connected MIDI instrument is actually switched on. Many keyboards keep their USB-MIDI chip powered by
 * the phone, so the device stays listed after the instrument is switched off. Instruments that send Active Sensing
 * (0xFE, about every 300 ms, MIDI 1.0) give it away: once seen, a pause longer than [timeoutNanos] means the
 * instrument went quiet (switched off or unplugged). Instruments that never send it are taken as on.
 */
class MidiLiveness(private val timeoutNanos: Long = 1_200_000_000L) {
    private var lastAny: Long? = null
    private var lastSense: Long? = null

    /** Records a received message; [bytes] are scanned for Active Sensing. */
    fun received(bytes: ByteArray, offset: Int, count: Int, nowNanos: Long) {
        lastAny = nowNanos
        for (i in offset until offset + count) if (bytes[i].toInt() and 0xFF == ACTIVE_SENSING) { lastSense = nowNanos; return }
    }

    /** The instrument sends Active Sensing, so its silence can be detected. */
    val sendsSensing: Boolean get() = lastSense != null

    /** True when the instrument sent Active Sensing before and has been silent for longer than the timeout. */
    fun silent(nowNanos: Long): Boolean {
        val s = lastSense ?: return false
        return nowNanos - maxOf(s, lastAny ?: s) > timeoutNanos
    }

    /** Nanos of the last message of any kind, or null. */
    val lastMessageNanos: Long? get() = lastAny

    companion object {
        const val ACTIVE_SENSING = 0xFE
    }
}
