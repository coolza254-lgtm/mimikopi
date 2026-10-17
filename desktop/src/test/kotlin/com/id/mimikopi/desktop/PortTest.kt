package com.id.mimikopi.desktop

import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.desktop.audio.Metronome
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.data.Store
import com.id.mimikopi.desktop.learning.LearningRepository
import com.id.mimikopi.desktop.learning.PieceRepository
import com.id.mimikopi.desktop.learning.contentFiles
import com.id.mimikopi.desktop.port.DRAWABLE_FILES
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.desktop.port.loadVectorDrawable
import com.id.mimikopi.desktop.port.stringResource
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pieces that let the Android reading screens run on the desktop. */
class PortTest {
    private fun tempStore() = Store(File(Files.createTempDirectory("mimikopi").toFile(), "data.json"))

    @Test
    fun stringsComeFromTheAndroidResources() {
        assertEquals("ทักษะ", stringResource(R.string.tab_skills))
        // Formatted as Resources.getString(id, args).
        assertEquals("ล่าสุดถูก 80%", stringResource(R.string.ear_accuracy, 80))
    }

    @Test
    fun iconsLoadFromTheAndroidVectors() {
        for (id in listOf(R.drawable.ic_arrow_back, R.drawable.ic_check, R.drawable.ic_chevron_right, R.drawable.ic_play, R.drawable.ic_review, R.drawable.ic_settings)) {
            val v = loadVectorDrawable(DRAWABLE_FILES[id])
            assertTrue(v.viewportWidth > 0f)
        }
    }

    @Test
    fun readingStepsAndPiecesLoadFromTheSharedContent() {
        val learning = LearningRepository(tempStore())
        assertEquals(contentFiles("lessons").size, learning.lessons.size)
        assertTrue(learning.lessons.size >= 90)
        assertEquals("1.1", learning.lessons.first().id)
        // Numeric order: 1.2 before 1.10.
        assertTrue(learning.lessons.indexOfFirst { it.id == "1.2" } < learning.lessons.indexOfFirst { it.id == "1.10" })
        val pieces = PieceRepository(tempStore())
        assertTrue(pieces.pieces.size >= 20)
        assertTrue(pieces.pieceOrExercise("ex-c-major-scale-rh") != null)
    }

    @Test
    fun fallingKeyboardStartsAndEndsOnCAndCoversTheNotes() {
        for ((lo, hi) in listOf(60 to 64, 47 to 79, 21 to 108, 66 to 66)) {
            val r = com.id.mimikopi.desktop.port.components.fallingKeyRange(lo, hi)
            assertEquals(0, r.first % 12, "$r")
            assertEquals(0, r.last % 12, "$r")
            assertTrue(lo in r && hi in r, "$r")
            assertTrue(r.last - r.first >= 24, "$r")
        }
    }

    @Test
    fun desktopRecordsCarryTheLadderMarker() {
        val store = tempStore()
        LearningRepository(store)
        // Without it the Android app would restart progress when a desktop backup is restored.
        assertEquals("3", store.get("path.version"))
    }

    private class SilentPiano : Piano {
        var clicks = 0
        override fun noteOn(note: Int, velocity: Int) = Unit
        override fun noteOff(note: Int) = Unit
        override fun sustain(down: Boolean) = Unit
        override fun allNotesOff() = Unit
        override fun close() = Unit
        override fun click(level: Int, volume: Float) { clicks++ }
        override val latencyNanos: Long = 30_000_000L
        override fun setLevel(factor: Float) = Unit
    }

    @Test
    fun metronomeClicksMapToRealTimesThroughTheSharedTimingCode() {
        val piano = SilentPiano()
        val m = Metronome { piano }
        m.set(true, 600.0, 1, intArrayOf(0, 2), 0.9f) // a click every 100 ms
        Thread.sleep(480)
        m.set(false, 600.0, 1, intArrayOf(0), 0f)
        val ticks = m.drain().toList()
        assertTrue(ticks.size in 3..6, "ticks: $ticks")
        assertTrue(piano.clicks > 0)
        // Frames are nanoseconds since the epoch at a "sample rate" of 1e9, heard after the piano's latency.
        val times = JitterAnalysis.presentationTimesNanos(ticks, listOf(FrameTimestamp(0, m.epoch)), 1_000_000_000)
        times.zipWithNext { a, b -> assertTrue(abs((b - a) / 1e6 - 100.0) < 1.0, "interval ${(b - a) / 1e6} ms") }
        assertTrue(times.first() > m.epoch + 30_000_000.0)
    }
}
