package com.id.mimikopi.core

import com.id.mimikopi.core.input.MidiNotice
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MidiNoticeTest {
    @Test
    fun theSameEventTwiceIsTwoNotices() {
        val a = MidiNotice(MidiNotice.Kind.UNPLUGGED, "Digital piano", 1)
        val b = MidiNotice(MidiNotice.Kind.UNPLUGGED, "Digital piano", 2)
        assertTrue(a != b)
    }

    @Test
    fun problemsAreTheOnesTheUserMayNeedToActOn() {
        assertTrue(MidiNotice(MidiNotice.Kind.UNPLUGGED, "x").isProblem)
        assertTrue(MidiNotice(MidiNotice.Kind.BLE_FAILED, "x").isProblem)
        assertFalse(MidiNotice(MidiNotice.Kind.PLUGGED, "x").isProblem)
        assertTrue(MidiNotice(MidiNotice.Kind.PLUGGED, "Digital piano").text().contains("Digital piano"))
    }
}
