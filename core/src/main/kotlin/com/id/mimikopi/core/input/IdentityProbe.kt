package com.id.mimikopi.core.input

/**
 * Asks an instrument whether it is there: the MIDI Universal Identity Request ([REQUEST]), which many instruments
 * answer with an Identity Reply (F0 7E <device> 06 02 ... F7). An instrument that answered once is asked again every
 * few seconds; when its answers stop (and nothing else arrives either) for longer than [timeoutNanos], it is taken
 * as switched off even though the system still lists it. Instruments that never answer are left alone (after a few
 * tries) and judged by the other signs. Bytes may arrive split across packets, so the last few are kept.
 */
class IdentityProbe(private val timeoutNanos: Long = 4_500_000_000L, private val discoveryTries: Int = 3) {
    private val window = IntArray(5) { -1 }
    private var lastReply: Long? = null
    private var lastAny: Long? = null
    private var lastSent: Long? = null
    private var sentCount = 0

    /** The instrument answered at least once. */
    var answers: Boolean = false
        private set

    /** Records received bytes; returns true when they completed an Identity Reply. */
    fun received(bytes: ByteArray, offset: Int, count: Int, nowNanos: Long): Boolean {
        var reply = false
        for (i in offset until offset + count) {
            val b = bytes[i].toInt() and 0xFF
            if (b >= 0xF8) continue // real-time bytes may sit inside a SysEx
            lastAny = nowNanos
            System.arraycopy(window, 1, window, 0, window.size - 1)
            window[window.size - 1] = b
            if (window[0] == 0xF0 && window[1] == 0x7E && window[3] == 0x06 && window[4] == 0x02) {
                answers = true
                lastReply = nowNanos
                reply = true
            }
        }
        return reply
    }

    /** Whether to send a request now: a few tries to find out, then every [intervalNanos] for instruments that answer. */
    fun shouldSend(nowNanos: Long, intervalNanos: Long = 2_000_000_000L): Boolean {
        val last = lastSent
        if (last != null && nowNanos - last < intervalNanos) return false
        return answers || sentCount < discoveryTries
    }

    fun sent(nowNanos: Long) {
        lastSent = nowNanos
        sentCount++
    }

    /** True when an instrument that answers has stopped answering (and sent nothing else) for longer than the timeout. */
    fun silent(nowNanos: Long): Boolean {
        if (!answers) return false
        val heard = maxOf(lastReply ?: return false, lastAny ?: 0L)
        val asked = lastSent ?: return false
        return asked > heard && nowNanos - heard > timeoutNanos
    }

    companion object {
        /** Universal Non-Real-Time Identity Request to all devices. */
        val REQUEST: ByteArray = byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte())
    }
}
