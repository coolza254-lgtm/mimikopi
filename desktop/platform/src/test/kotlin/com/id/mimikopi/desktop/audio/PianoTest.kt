package com.id.mimikopi.desktop.audio

import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the native engine (desktop/native, built by CMake) without a sound card. */
class PianoTest {
    @Before
    fun nativeLibrary() = assumeTrue("native library not built: ${NativeAudio.loadError}", NativeAudio.available)

    @Test
    fun builtInToneSoundsWithoutASamplePack() {
        Piano(nullDevice = true).use { p ->
            assertTrue(p.renderForTest(256).all { it == 0f }, "silent before any note")
            p.noteOn(60, 100)
            val block = p.renderForTest(4800)
            assertTrue(block.maxOf { abs(it) } > 0.05f, "middle C sounds")
            assertTrue(block.all { abs(it) <= 1f }, "the limiter keeps samples in range")
            p.noteOff(60)
            repeat(40) { p.renderForTest(4800) }
            assertTrue(p.renderForTest(4800).maxOf { abs(it) } < 0.01f, "the note dies away after release")
        }
    }

    @Test
    fun nullDeviceRunsTheMetronomeOnTheJvmClock() {
        Piano(nullDevice = true).use { p ->
            assertTrue(p.acquire())
            val s = p.status.value
            assertTrue(s.open)
            assertTrue(s.sampleRate > 0)
            assertNull(s.error)
            p.setMetronome(true, 240.0, 1, intArrayOf(0, 2, 2, 2), 0.8f)
            Thread.sleep(700)
            val ticks = p.drainMetronomeTicks()
            assertTrue(ticks.size >= 2, "ticks counted in frames: ${ticks.toList()}")
            val stamp = assertNotNull(p.timestamp())
            assertTrue(abs(stamp[1] - System.nanoTime()) < 1_000_000_000L, "timestamp is on System.nanoTime")
            p.release()
            assertEquals(false, p.status.value.open)
        }
    }

    @Test
    fun prefsRoundTrip() {
        assertEquals(AudioPrefs(128, false), AudioPrefs.decode(AudioPrefs(128, false).encode()))
        assertEquals(AudioPrefs(), AudioPrefs.decode("999|maybe"))
    }
}
