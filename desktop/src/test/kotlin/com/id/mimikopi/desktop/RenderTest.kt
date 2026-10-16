package com.id.mimikopi.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.desktop.port.lesson.LessonScreen
import com.id.mimikopi.desktop.port.lesson.LessonViewModel
import com.id.mimikopi.desktop.port.practice.DrillScreen
import com.id.mimikopi.desktop.port.practice.DrillViewModel
import com.id.mimikopi.desktop.port.reader.PieceListScreen
import com.id.mimikopi.desktop.port.reader.PieceListViewModel
import com.id.mimikopi.desktop.port.reader.ReaderScreen
import com.id.mimikopi.desktop.port.reader.ReaderViewModel
import com.id.mimikopi.desktop.port.rememberViewModel
import com.id.mimikopi.desktop.port.skill.Skill
import com.id.mimikopi.desktop.port.skill.SkillScreen
import com.id.mimikopi.desktop.port.skill.SkillViewModel
import com.id.mimikopi.desktop.port.songs.SongsScreen
import com.id.mimikopi.desktop.port.songs.SongsViewModel
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

    private fun args(vararg pairs: Pair<String, String?>) = SavedStateHandle(mapOf(*pairs))

    @Test
    fun readingAndSongScreensRenderInBothLooks() {
        val app = MimikopiApp
        for (dark in listOf(true, false)) {
            render(dark) { SkillScreen(Skill.READING, {}, {}, {}, {}, {}, vm = rememberViewModel { SkillViewModel(app) }) }
            render(dark) { SongsScreen(onPlay = { _, _ -> }, onOpenMidi = {}, onOpenSettings = {}, vm = rememberViewModel { SongsViewModel(app) }) }
            render(dark) { LessonScreen(onBack = {}, onOpenDrill = { _, _ -> }, vm = rememberViewModel { LessonViewModel(app, args("lessonId" to "1.1")) }) }
            render(dark) { DrillScreen(onBack = {}, vm = rememberViewModel { DrillViewModel(app, args("lessonId" to "1.14", "drill" to "0")) }) }
            render(dark) { DrillScreen(onBack = {}, vm = rememberViewModel { DrillViewModel(app, args("review" to "true")) }) }
            render(dark) { PieceListScreen(onBack = {}, onOpenPiece = { _, _ -> }, vm = rememberViewModel { PieceListViewModel(app, args("lesson" to "2.23", "drill" to "0")) }) }
            render(dark) { ReaderScreen(onBack = {}, vm = rememberViewModel { ReaderViewModel(app, args("pieceId" to "ode-to-joy", "hand" to "RIGHT")) }) }
            render(dark) { ReaderScreen(onBack = {}, vm = rememberViewModel { ReaderViewModel(app, args("lessonId" to "1.18", "drill" to "0")) }) }
        }
    }
}
