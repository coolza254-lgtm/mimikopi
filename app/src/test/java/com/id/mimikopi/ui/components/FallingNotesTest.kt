package com.id.mimikopi.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.id.mimikopi.core.playalong.NoteResult
import com.id.mimikopi.ui.reader.ReaderOptions
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FallingNotesTest {
    private val now = 2_000_000_000L
    private val range = 48..84

    @Test fun touchMatchesTheDrawnWhiteAndBlackKeysAndRejectsOutsideTouches() {
        for ((w, h) in listOf(1080f to 460f, 390f to 560f)) {
            val g = FallingGeometry(range, w, h)
            for (k in g.keyboard.whiteKeys) assertEquals(k.note, g.hit((k.left + k.right) / 2, h - 1))
            for (k in g.keyboard.blackKeys) assertEquals(k.note, g.hit((k.left + k.right) / 2, g.hitY + k.bottom / 2))
            assertNull(g.hit(-1f, h - 1))
            assertNull(g.hit(w, h - 1))
            assertNull(g.hit(1f, g.hitY - 1))
            assertNull(g.hit(1f, h + 1))
        }
    }

    @Test fun oldPreferencesKeepLabelsOnAndTheNewToggleSurvivesPersistence() {
        val old = Json.decodeFromString<ReaderOptions>("""{"falling":true}""")
        assertTrue(old.fallingKeyLabels)
        val hidden = old.copy(fallingKeyLabels = false)
        assertEquals(hidden, Json.decodeFromString<ReaderOptions>(Json.encodeToString(hidden)))
        assertEquals(old.copy(fallingKeyLabels = false), hidden)
    }

    @Test fun pendingNotesNeverProduceHitFeedbackAndVerdictsStayDistinct() {
        assertNull(FallingFeedback.judged(60, NoteResult.PENDING, false, now))
        val outcomes = NoteResult.entries.filter { it != NoteResult.PENDING }.map {
            FallingFeedback.judged(60, it, true, now)!!
        }
        assertEquals(outcomes.size, outcomes.map { it.verdict }.toSet().size)
        assertTrue(outcomes.all { it.left })
        val expired = outcomes.map { it.copy(atNanos = 0) }
        val burst = List(80) { FallingFeedback(60, FallingVerdict.HIT, false, now + it) }
        val buffered = expired.withFeedback(burst, now)
        assertEquals(48, buffered.size)
        assertEquals(burst.takeLast(48), buffered)
        assertTrue(buffered.withFeedback(emptyList(), now + 900_000_000).isEmpty())
    }

    private fun render(w: Int, h: Int, names: Boolean, events: List<FallingFeedback> = emptyList()): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(RuntimeEnvironment.getApplication()), density, LayoutDirection.Ltr)
        val renderer = FallingRenderer(range, measurer)
        val notes = listOf(
            FallingNote(54, 1.0, 1.6, true, "ファ♯"), FallingNote(67, 1.0, .8, false, "ソ"),
            FallingNote(57, 2.4, .8, true, "ラ"), FallingNote(72, 2.8, .7, false, "ド"),
            FallingNote(59, 3.4, 1.8, true, "シ"), FallingNote(76, 4.0, .7, false, "ミ"),
            FallingNote(61, 5.5, .7, true, "ド♯"), FallingNote(74, 5.6, 1.6, false, "レ"),
            FallingNote(77, 7.4, .8, false, "ファ"),
        )
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            renderer.draw(this, notes, 1.8, .8, 8.0, listOf(0.0, 4.0, 8.0), setOf(54, 67, 70),
                { if (names) arrayOf("ド", "ド♯", "レ", "レ♯", "ミ", "ファ", "ファ♯", "ソ", "ソ♯", "ラ", "ラ♯", "シ")[it % 12] else null },
                events, now, 16.0)
        }
        return bitmap
    }

    @Test fun renderLandscapeAndPortraitWithLiveFeedbackAndLabelsOnBothKeyTypes() {
        val events = listOf(
            FallingFeedback(54, FallingVerdict.HIT, true, now - 100_000_000),
            FallingFeedback(67, FallingVerdict.HIT, false, now - 100_000_000),
            FallingFeedback(70, FallingVerdict.WRONG, false, now - 180_000_000),
        )
        val output = File("build/render").apply { mkdirs() }
        for ((w, h) in listOf(1080 to 460, 390 to 560)) {
            val named = render(w, h, true, events)
            val hidden = render(w, h, false, events)
            File(output, "falling-${w}-labels.png").outputStream().use { named.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(output, "falling-${w}-clean.png").outputStream().use { hidden.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val g = FallingGeometry(range, w.toFloat(), h.toFloat())
            // Labels change both white and black keys, and leave the music area untouched.
            var whiteDiff = 0
            var blackDiff = 0
            for (y in 0 until h) for (x in 0 until w) if (named.getPixel(x, y) != hidden.getPixel(x, y)) {
                assertTrue(y >= g.hitY)
                val key = g.hit(x.toFloat(), y.toFloat())!!
                if (g.byNote.getValue(key).black) blackDiff++ else whiteDiff++
            }
            assertTrue(whiteDiff > 20, "Missing white-key labels")
            assertTrue(blackDiff > 20, "Missing black-key labels")
            // Rendered hand bars retain recognisably different colours.
            val pixels = IntArray(w * h)
            named.getPixels(pixels, 0, w, 0, 0, w, h)
            assertTrue(pixels.count { android.graphics.Color.blue(it) > android.graphics.Color.red(it) + 30 } > 100)
            assertTrue(pixels.count { android.graphics.Color.green(it) > android.graphics.Color.blue(it) + 50 } > 100)
        }
        for (verdict in listOf(FallingVerdict.EARLY, FallingVerdict.LATE, FallingVerdict.MISS, FallingVerdict.CORRECTED)) {
            File(output, "falling-${verdict.name.lowercase()}.png").outputStream().use {
                render(1080, 460, true, listOf(FallingFeedback(67, verdict, false, now - 80_000_000))).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val expired = render(390, 560, false, events.map { it.copy(atNanos = 0) })
        assertTrue(expired.sameAs(render(390, 560, false)), "Expired effects must leave no marks")
    }
}
