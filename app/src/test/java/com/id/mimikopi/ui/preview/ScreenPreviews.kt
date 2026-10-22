package com.id.mimikopi.ui.preview

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.ui.components.BottomNav
import com.id.mimikopi.ui.skills.SkillsScreen
import com.id.mimikopi.ui.songs.SongsScreen
import com.id.mimikopi.ui.theme.MimikopiTheme
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.TodayScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main pages as the phone shows them (390 dp wide, @2x like the Design J pictures) into
 * build/render/design-*.png, which CI publishes on the "ui-preview" prerelease to compare with docs/design-J.
 * A fresh install: no progress yet. Checks only that each page draws. Pages that load the native piano sound
 * (the piano tab) cannot run under Robolectric and are left out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MimikopiApp::class, qualifiers = "w390dp-h900dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenPreviews {
    @get:Rule val compose = createComposeRule()

    private fun render(name: String, tab: String?, page: @Composable () -> Unit) {
        compose.setContent {
            MimikopiTheme(false) {
                Scaffold(containerColor = Tokens.Palette.Paper, bottomBar = { if (tab != null) BottomNav(tab) {} }) { inner ->
                    Box(Modifier.fillMaxSize().padding(inner)) { page() }
                }
            }
        }
        // Pages read their data off the main thread: let it arrive before taking the picture.
        repeat(10) { Thread.sleep(200); compose.waitForIdle() }
        File("build/render").mkdirs()
        File("build/render/design-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun today() = render("01-today", "today") { TodayScreen(onOpen = {}, onOpenMidi = {}, onOpenSettings = {}) }
    @Test fun keys() = render("03-keys", "piano") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(20.dp)) {
            com.id.mimikopi.ui.components.TouchKeyboardSurface(
                60..76, emptySet(), true, com.id.mimikopi.core.music.NoteNameStyle.LETTER, setOf(64, 67, 72), { _, _ -> },
                androidx.compose.ui.Modifier.fillMaxWidth().height(260.dp),
            )
        }
    }
    @Test fun skills() = render("02-skills", "skills") { SkillsScreen(onOpen = {}, onOpenMidi = {}, onOpenSettings = {}) }
    @Test fun songs() = render("04-songs", "songs") {
        SongsScreen(onOpen = {}, onOpenLibrary = {})
    }
}
