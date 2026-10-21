package com.id.mimikopi.ui.reader

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import com.id.mimikopi.ui.components.FallingNote
import com.id.mimikopi.ui.components.FallingNotesView
import com.id.mimikopi.ui.theme.MimikopiTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w640dp-h280dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FallingFullscreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapeCanvasFillsTheViewportAndOverlayControlsStillWork() {
        var labels by mutableStateOf(true)
        var stopped = false
        var fullscreen by mutableStateOf(true)
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
            MimikopiTheme(dark = false) {
                FallingViewport(fullscreen, labels, { labels = it }, "โหมดตามจังหวะ · 48 bpm", { stopped = true }, Modifier.fillMaxSize()) { modifier ->
                    FallingNotesView(
                        notes = listOf(FallingNote(60, 1.0, 2.0, true, "C"), FallingNote(67, 3.0, 1.0, false, "G")),
                        cursor = { 0.0 }, animate = false, keyRange = 48..84, pressed = emptySet(),
                        keyLabel = { if (labels) "C" else null }, modifier = modifier,
                    )
                }
            }
            }
        }
        val viewport = compose.onNodeWithTag("falling-viewport").getUnclippedBoundsInRoot()
        assertTrue(viewport.right - viewport.left > viewport.bottom - viewport.top)
        assertEquals(viewport, compose.onNodeWithTag("falling-canvas").getUnclippedBoundsInRoot(), "Toolbar must not reserve canvas space")
        compose.onNodeWithTag("falling-key-labels").assertIsDisplayed().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("falling-stop").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(stopped) }
        val image = compose.onNodeWithTag("falling-viewport").captureToImage().asAndroidBitmap()
        File("build/render").mkdirs()
        File("build/render/falling-fullscreen.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle { fontScale = 1.6f }
        compose.onNodeWithTag("falling-stop").assertIsDisplayed()
        val stop = compose.onNodeWithTag("falling-stop").getUnclippedBoundsInRoot()
        assertTrue(stop.right <= viewport.right && stop.bottom <= viewport.bottom, "Large text must not push Stop outside the screen")
        compose.runOnIdle { fullscreen = false }
        val normalCanvas = compose.onNodeWithTag("falling-canvas").getUnclippedBoundsInRoot()
        assertTrue(normalCanvas.top > viewport.top, "The regular portrait/setup layout retains its toolbar")
        compose.onNodeWithTag("falling-key-labels").assertIsOff()
    }

    @Test fun leavingFullscreenRestoresTheWindowCutoutPolicy() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val window = activity.get().window
            val old = window.attributes.layoutInDisplayCutoutMode
            ReaderFullscreenSession(window).use {
                assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, window.attributes.layoutInDisplayCutoutMode)
            }
            assertEquals(old, window.attributes.layoutInDisplayCutoutMode)
        } finally { activity.close() }
    }
}
