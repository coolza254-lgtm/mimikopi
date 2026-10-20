package com.id.mimikopi.core

import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.LatencyCalibration
import com.id.mimikopi.core.input.MidiParser
import com.id.mimikopi.core.input.OctaveCalibration
import com.id.mimikopi.core.input.VelocityProbe
import com.id.mimikopi.core.input.shifted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MidiParserTest {
    private fun parse(vararg bytes: Int): List<InputEvent> {
        val out = mutableListOf<InputEvent>()
        val p = MidiParser(InputSource.USB_MIDI)
        p.parse(ByteArray(bytes.size) { bytes[it].toByte() }, 0, bytes.size, 7L) { out += it }
        return out
    }

    @Test
    fun noteOnOffAndVelocityZero() {
        assertEquals(
            listOf(
                InputEvent.NoteOn(60, 100, 7, InputSource.USB_MIDI),
                InputEvent.NoteOff(60, 7, InputSource.USB_MIDI),
                InputEvent.NoteOff(62, 7, InputSource.USB_MIDI),
            ),
            parse(0x90, 60, 100, 0x90, 60, 0, 0x80, 62, 40),
        )
    }

    @Test
    fun runningStatusAndAnyChannel() {
        val events = parse(0x93, 60, 90, 64, 80, 60, 0)
        assertEquals(3, events.size)
        assertEquals(InputEvent.NoteOn(64, 80, 7, InputSource.USB_MIDI), events[1])
        assertEquals(InputEvent.NoteOff(60, 7, InputSource.USB_MIDI), events[2])
    }

    @Test
    fun realtimeBytesInsideAMessageAreIgnored() {
        assertEquals(listOf(InputEvent.NoteOn(60, 99, 7, InputSource.USB_MIDI)), parse(0x90, 0xF8, 60, 0xFE, 99))
    }

    @Test
    fun sysexIsSkippedAndSustainIsMapped() {
        val events = parse(0xF0, 0x7E, 0x01, 0x90, 0xF7, 0xB0, 64, 127, 0xB0, 64, 0, 0xB0, 1, 50)
        assertEquals(
            listOf(
                InputEvent.Sustain(true, 7, InputSource.USB_MIDI),
                InputEvent.Sustain(false, 7, InputSource.USB_MIDI),
                InputEvent.Control(1, 50, 7, InputSource.USB_MIDI),
            ),
            events,
        )
    }

    @Test
    fun messagesSplitAcrossCallsStillParse() {
        val out = mutableListOf<InputEvent>()
        val p = MidiParser(InputSource.USB_MIDI)
        p.parse(byteArrayOf(0x90.toByte(), 60), 0, 2, 1) { out += it }
        p.parse(byteArrayOf(70), 0, 1, 2) { out += it }
        assertEquals(listOf<InputEvent>(InputEvent.NoteOn(60, 70, 2, InputSource.USB_MIDI)), out)
    }

    @Test
    fun octaveShiftDropsOutOfRange() {
        assertEquals(72, (InputEvent.NoteOn(60, 1, 0, InputSource.USB_MIDI).shifted(12) as InputEvent.NoteOn).note)
        assertNull(InputEvent.NoteOn(120, 1, 0, InputSource.USB_MIDI).shifted(12))
    }
}

class CalibrationTest {
    @Test
    fun velocityProbe() {
        assertFalse(VelocityProbe.analyze(listOf(100, 100, 100), listOf(100, 100)).velocitySensitive)
        assertTrue(VelocityProbe.analyze(listOf(30, 35, 41), listOf(98, 110, 120)).velocitySensitive)
        assertFalse(VelocityProbe.analyze(listOf(60, 64), listOf(62, 66)).velocitySensitive) // varies but no real difference
        assertTrue(InputConfig().dynamicsAvailable) // unknown until probed
    }

    @Test
    fun octaveOffset() {
        assertEquals(0, OctaveCalibration.offsetFor(60))
        assertEquals(12, OctaveCalibration.offsetFor(48))
        assertEquals(-12, OctaveCalibration.offsetFor(72))
        assertEquals(12, OctaveCalibration.offsetFor(49)) // aimed at C, slipped onto C#
        assertNull(OctaveCalibration.offsetFor(54)) // F#: not an attempt at C
    }

    @Test
    fun latencyMedianIgnoresOutliersAndNeedsEnoughTaps() {
        val clicks = List(16) { it * 750_000_000.0 }
        val taps = clicks.mapIndexed { i, c -> (c + 1e6 * (95 + (i % 3) * 5)).toLong() } + listOf(5_000_000_000L + 330_000_000L)
        val r = assertNotNull(LatencyCalibration.analyze(clicks, taps))
        assertEquals(100.0, r.offsetMs, 0.01)
        assertEquals(16, r.taps) // at most one tap counts for each click
        assertNull(LatencyCalibration.analyze(clicks, taps.take(5)))
    }

    @Test
    fun aChordOrRepeatedPressesOnOneClickCannotCompleteCalibration() {
        val clicks = List(16) { it * 1_000_000_000.0 }
        val chord = List(12) { 100_000_000L + it * 1_000_000L }
        assertNull(LatencyCalibration.analyze(clicks, chord))
        val nineBeats = clicks.take(9).flatMap { c -> List(3) { (c + 100_000_000L + it * 1_000_000L).toLong() } }
        assertNull(LatencyCalibration.analyze(clicks, nineBeats))
    }

    @Test
    fun everyClickCountsOnceAndDuplicateNotesDoNotBiasTheOffset() {
        val clicks = List(16) { it * 1_000_000_000.0 }
        val taps = clicks.map { (it + 120_000_000L).toLong() }
        val duplicates = List(20) { 350_000_000L + it }
        val result = assertNotNull(LatencyCalibration.analyze(clicks, (taps + duplicates).reversed()))
        assertEquals(16, result.taps)
        assertEquals(120.0, result.offsetMs)
        assertEquals(0.0, result.spreadMs)
    }
}

class MidiLivenessTest {
    private val ms = 1_000_000L

    @Test
    fun instrumentsWithoutActiveSensingAreNeverSilent() {
        val l = com.id.mimikopi.core.input.MidiLiveness()
        kotlin.test.assertFalse(l.silent(10_000 * ms))
        l.received(byteArrayOf(0x90.toByte(), 60, 90), 0, 3, 0)
        kotlin.test.assertFalse(l.silent(60_000 * ms))
        kotlin.test.assertFalse(l.sendsSensing)
    }

    @Test
    fun activeSensingStoppingMeansSwitchedOff() {
        val l = com.id.mimikopi.core.input.MidiLiveness(timeoutNanos = 1_200 * ms)
        l.received(byteArrayOf(0xFE.toByte()), 0, 1, 0)
        l.received(byteArrayOf(0xFE.toByte()), 0, 1, 300 * ms)
        kotlin.test.assertTrue(l.sendsSensing)
        kotlin.test.assertFalse(l.silent(1_000 * ms))
        kotlin.test.assertTrue(l.silent(1_600 * ms))
        // Sensing again (switched back on): live.
        l.received(byteArrayOf(0x90.toByte(), 60, 90, 0xFE.toByte()), 0, 4, 5_000 * ms)
        kotlin.test.assertFalse(l.silent(5_500 * ms))
    }
}

