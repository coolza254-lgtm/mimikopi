package com.id.mimikopi.core

import com.id.mimikopi.core.metronome.ClickLevel.MEDIUM
import com.id.mimikopi.core.metronome.ClickLevel.STRONG
import com.id.mimikopi.core.metronome.ClickLevel.SUB
import com.id.mimikopi.core.metronome.ClickLevel.WEAK
import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.metronome.MetronomeSettings
import com.id.mimikopi.core.metronome.TapTempo
import com.id.mimikopi.core.metronome.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetronomeTest {
    @Test
    fun barPatterns() {
        assertEquals(listOf(STRONG, WEAK, WEAK, WEAK), MetronomeSettings().barPattern())
        assertEquals(listOf(STRONG, WEAK, WEAK), MetronomeSettings(signature = TimeSignature.THREE_FOUR).barPattern())
        assertEquals(listOf(STRONG, SUB, WEAK, SUB), MetronomeSettings(signature = TimeSignature.TWO_FOUR, subdivision = 2).barPattern())
        // 6/8 = two dotted-quarter beats of three eighths, accent 1 and 4; subdivision setting ignored.
        assertEquals(listOf(STRONG, SUB, SUB, MEDIUM, SUB, SUB), MetronomeSettings(signature = TimeSignature.SIX_EIGHT, subdivision = 4).barPattern())
        assertEquals(listOf(WEAK, WEAK, WEAK, WEAK), MetronomeSettings(accent = false).barPattern())
    }

    @Test
    fun tickLengthIsExactAndFractional() {
        assertEquals(24000.0, MetronomeSettings(bpm = 120).framesPerTick(48000), 1e-9)
        assertEquals(48000.0 * 60 / 70 / 3, MetronomeSettings(bpm = 70, subdivision = 3).framesPerTick(48000), 1e-9)
        assertEquals(30, MetronomeSettings(bpm = 5).normalized().bpm)
        assertEquals(240, MetronomeSettings(bpm = 999).normalized().bpm)
    }

    @Test
    fun tapTempoUsesMedianAndResetsAfterAPause() {
        val tap = TapTempo()
        assertNull(tap.tap(0))
        assertEquals(120, tap.tap(500))
        assertEquals(120, tap.tap(1000))
        assertEquals(120, tap.tap(1700)) // one sloppy tap does not move the median much
        assertNull(tap.tap(10_000)) // long pause: start over
        assertEquals(60, tap.tap(11_000))
    }

    @Test
    fun jitterOfPerfectClicksIsZeroAndDisturbancesShow() {
        val rate = 48000
        val clicks = List(17) { it * 24000L } // 120 bpm
        val stamps = List(40) { i -> FrameTimestamp(i * 9600L, 1_000_000_000L + i * 200_000_000L) }
        val times = JitterAnalysis.presentationTimesNanos(clicks, stamps, rate)
        val perfect = JitterAnalysis.analyze(times, idealIntervalMs = 500.0)
        assertEquals(17, perfect.clicks)
        assertTrue(perfect.maxAbsMs < 1e-6)
        // An underrun that delays everything after frame 96000 by 10 ms shows up once.
        val shifted = stamps.map { if (it.framePosition >= 96000) it.copy(timeNanos = it.timeNanos + 10_000_000) else it }
        val r = JitterAnalysis.analyze(JitterAnalysis.presentationTimesNanos(clicks, shifted, rate), 500.0)
        assertEquals(10.0, r.maxAbsMs, 1e-6)
    }
}
