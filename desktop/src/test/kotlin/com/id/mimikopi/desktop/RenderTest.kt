package com.id.mimikopi.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.id.mimikopi.desktop.ui.MimikopiTheme
import com.id.mimikopi.desktop.ui.SettingsScreen
import com.id.mimikopi.desktop.ui.ear.EarDrillScreen
import com.id.mimikopi.desktop.ui.ear.EarHome
import com.id.mimikopi.desktop.ui.sections.SectionsHome
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders each screen off-screen in both looks, so a screen that throws at runtime fails the build. */
class RenderTest {
    private fun render(dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        ImageComposeScene(width = 1100, height = 1600) {
            MimikopiTheme(dark) { Column { content() } }
        }.use { scene ->
            val image = scene.render()
            assertTrue(image.width == 1100)
        }
    }

    @Test
    fun screensRenderInBothLooks() {
        for (dark in listOf(true, false)) {
            render(dark) { EarHome(onOpen = {}, onOpenMidi = {}) }
            render(dark) { SectionsHome(onPractice = {}, onEdit = {}, onOpenMidi = {}) }
            render(dark) { SettingsScreen(onBack = {}) }
            render(dark) { EarDrillScreen(Services.learning.rungs.first(), onBack = {}, onOpenMidi = {}) }
        }
    }
}
