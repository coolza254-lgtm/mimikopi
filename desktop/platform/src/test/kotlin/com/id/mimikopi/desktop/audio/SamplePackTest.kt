package com.id.mimikopi.desktop.audio

import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Downloads and installs the real 79 MB sample pack, then plays it. Opt-in (MIMIKOPI_PACK_TEST=1): it needs the
 * network and takes a while, so ordinary test runs skip it.
 */
class SamplePackTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun downloadVerifyDecodeAndPlay() {
        assumeTrue(System.getenv("MIMIKOPI_PACK_TEST") == "1" && NativeAudio.available)
        val packs = SamplePacks(tmp.newFolder(), "test")
        val d = packs.fetchDescriptor()
        var decoded = 0
        val pack = packs.install(d, { false }) { if (it is PackProgress.Decoding) decoded = it.done }
        assertEquals(d.files, decoded)
        assertNotNull(packs.installed())
        Piano(nullDevice = true).use { p ->
            assertNull(packs.load(p, pack))
            p.noteOn(60, 100)
            val block = p.renderForTest(9600)
            assertTrue(block.maxOf { abs(it) } > 0.02f, "a sampled middle C sounds")
            assertTrue(p.status.value.samples, "the status says real samples are loaded")
        }
    }
}
