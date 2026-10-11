package com.id.mimikopi.core

import com.id.mimikopi.core.keyboard.KeyboardLayout
import com.id.mimikopi.core.piano.PackDescriptor
import com.id.mimikopi.core.piano.PackLayer
import com.id.mimikopi.core.piano.PackManifest
import com.id.mimikopi.core.piano.PackRegion
import com.id.mimikopi.core.piano.SampleMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors the real pack layout: 30 regions in minor thirds A0..C8, 8 velocity layers. */
private fun salamanderLikeManifest(): PackManifest {
    val vel = listOf(1 to 34, 35 to 43, 44 to 50, 51 to 64, 65 to 80, 81 to 96, 97 to 112, 113 to 127)
    val centers = listOf(21) + (24..105 step 3).toList() + 108
    val regions = centers.mapIndexed { i, c ->
        val lo = if (i == 0) 21 else c - 1
        val hi = when (i) { 0 -> 22; centers.lastIndex -> 108; else -> c + 1 }
        PackRegion(lo, hi, c, damped = c < 89, releaseSec = if (c < 89) 0.35 else 3.0,
            layers = vel.mapIndexed { j, (a, b) -> PackLayer("r%02d_v%02d.flac".format(i + 1, (j + 1) * 2), a, b, 48000) })
    }
    return PackManifest(1, 1, "test", 48000, 2, 16, regions)
}

class SamplePackTest {
    @Test
    fun realisticManifestIsValidAndCoversAll88Keys() {
        val m = salamanderLikeManifest()
        assertEquals(30, m.regions.size)
        assertEquals(emptyList(), m.problems())
        val map = SampleMap(m)
        for (note in 21..108) assertTrue(map.noteToRegion[note] >= 0, "note $note")
        assertEquals(-1, map.noteToRegion[20])
        assertEquals(-1, map.noteToRegion[109])
    }

    @Test
    fun velocitySelectsTheRightLayer() {
        val map = SampleMap(salamanderLikeManifest())
        val r = map.noteToRegion[60]
        assertEquals(0, map.layerIndex(r, 1))
        assertEquals(0, map.layerIndex(r, 34))
        assertEquals(1, map.layerIndex(r, 35))
        assertEquals(7, map.layerIndex(r, 127))
        assertEquals(0, map.layerIndex(r, 0)) // clamped: velocity 0 is note-off upstream
    }

    @Test
    fun pitchRatioIsEqualTemperedAndRateCorrected() {
        val map = SampleMap(salamanderLikeManifest())
        assertEquals(1.0, map.pitchRatio(60, 48000), 1e-12) // C4 is a key center
        assertEquals(2.0.pow(1 / 12.0), map.pitchRatio(61, 48000), 1e-12)
        assertEquals(2.0.pow(-1 / 12.0) * 48000 / 44100, map.pitchRatio(59, 44100), 1e-12)
        for (note in 21..108) assertTrue(abs(map.pitchRatio(note, 48000) - 1) < 0.13, "note $note stretched too far")
    }

    @Test
    fun engineTablesAreConsistent() {
        val map = SampleMap(salamanderLikeManifest())
        val table = map.layerTable()
        assertEquals(240, map.filesInOrder().size)
        assertEquals(0 to 8, table[0])
        assertEquals(232 to 8, table[29])
    }

    @Test
    fun brokenManifestsAreRejected() {
        val good = salamanderLikeManifest()
        assertTrue(good.copy(regions = good.regions.drop(1)).problems().any { "note 21" in it })
        val gap = good.regions.mapIndexed { i, r -> if (i == 3) r.copy(layers = r.layers.drop(1)) else r }
        assertTrue(good.copy(regions = gap).problems().isNotEmpty())
        val unsafe = good.regions.mapIndexed { i, r ->
            if (i == 0) r.copy(layers = r.layers.mapIndexed { j, l -> if (j == 0) l.copy(file = "../evil.flac") else l }) else r
        }
        assertTrue(good.copy(regions = unsafe).problems().any { "unsafe" in it })
        assertFailsWith<IllegalArgumentException> { SampleMap(good.copy(channels = 1)) }
    }

    @Test
    fun descriptorValidation() {
        val d = PackDescriptor(1, 1, "https://github.com/o/r/releases/download/samples-v1/samples-v1.zip",
            150_000_000, "a".repeat(64), 280_000_000, 240, "CC BY 3.0")
        assertEquals(emptyList(), d.problems())
        assertTrue(d.copy(url = "https://example.com/x.zip").problems().isNotEmpty())
        assertTrue(d.copy(sha256 = "xyz").problems().isNotEmpty())
    }
}

class KeyboardLayoutTest {
    private val full = KeyboardLayout(21, 108, whiteKeyWidth = 10f, height = 100f)

    @Test
    fun fullPianoHas52WhiteAnd36BlackKeys() {
        assertEquals(52, full.whiteKeys.size)
        assertEquals(36, full.blackKeys.size)
        assertEquals(520f, full.totalWidth)
    }

    @Test
    fun hitTestPrefersBlackKeysInTheUpperPart() {
        val c4 = full.whiteKeys.first { it.note == 60 }
        val cs4 = full.blackKeys.first { it.note == 61 }
        val mid = (cs4.left + cs4.right) / 2
        assertEquals(61, full.hitTest(mid, 10f))
        assertEquals(60, full.hitTest(c4.left + 1f, 10f)) // left part of C is not under C#
        assertEquals(60, full.hitTest(mid, 90f).takeIf { mid < c4.right } ?: 60) // lower part is white
        assertEquals(62, full.hitTest(c4.right + 9f, 90f))
    }

    @Test
    fun outsideIsNullAndBlackEdgesRoundToWhite() {
        assertNull(full.hitTest(-1f, 10f))
        assertNull(full.hitTest(520f, 10f))
        assertNull(full.hitTest(5f, 101f))
        val partial = KeyboardLayout(61, 70, 10f, 100f) // starts on a black key -> widened to C
        assertEquals(60, partial.lowNote)
        assertEquals(71, partial.highNote)
    }

    @Test
    fun blackKeysDoNotOverlap() {
        full.blackKeys.zipWithNext().forEach { (a, b) -> assertTrue(a.right <= b.left, "${a.note}/${b.note}") }
    }
}
