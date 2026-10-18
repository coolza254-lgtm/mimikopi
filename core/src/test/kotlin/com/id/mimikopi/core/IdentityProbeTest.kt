package com.id.mimikopi.core

import com.id.mimikopi.core.input.IdentityProbe
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentityProbeTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private val reply = bytes(0xF0, 0x7E, 0x10, 0x06, 0x02, 0x43, 0x00, 0x41, 0x01, 0x02, 0x03, 0x04, 0xF7)
    private val s = 1_000_000_000L

    @Test
    fun recognisesAReplyEvenWhenSplitAcrossPackets() {
        val p = IdentityProbe()
        assertFalse(p.received(reply, 0, 3, 0))
        assertTrue(p.received(reply, 3, reply.size - 3, 0))
        assertTrue(p.answers)
    }

    @Test
    fun realTimeBytesInsideTheReplyAreIgnored() {
        val p = IdentityProbe()
        val withClock = bytes(0xF0, 0x7E, 0xFE, 0x10, 0x06, 0xF8, 0x02, 0xF7)
        assertTrue(p.received(withClock, 0, withClock.size, 0))
    }

    @Test
    fun anInstrumentThatStopsAnsweringIsSilent() {
        val p = IdentityProbe()
        p.sent(0)
        p.received(reply, 0, reply.size, s / 10)
        assertFalse(p.silent(3 * s))
        p.sent(3 * s) // asked again, no answer
        assertFalse(p.silent(4 * s))
        assertTrue(p.silent(5 * s))
        // Any message (a key) counts as alive.
        p.received(bytes(0x90, 60, 80), 0, 3, 5 * s)
        assertFalse(p.silent(6 * s))
    }

    @Test
    fun instrumentsThatNeverAnswerAreNotPolledForever() {
        val p = IdentityProbe(discoveryTries = 3)
        var t = 0L
        repeat(3) { assertTrue(p.shouldSend(t)); p.sent(t); t += 3 * s }
        assertFalse(p.shouldSend(t))
        assertFalse(p.silent(t + 100 * s))
    }
}
