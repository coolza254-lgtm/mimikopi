package com.id.mimikopi.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.desktop.port.components.FallingNote
import com.id.mimikopi.desktop.port.components.FallingNotesView
import com.id.mimikopi.desktop.port.components.FallingState
import com.id.mimikopi.desktop.port.components.fallingKeyRange
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
import com.id.mimikopi.desktop.port.ear.ToneEditScreen
import com.id.mimikopi.desktop.port.ear.ToneEditViewModel
import com.id.mimikopi.desktop.port.ear.ToneHomeScreen
import com.id.mimikopi.desktop.port.ear.ToneHomeViewModel
import com.id.mimikopi.desktop.port.ear.ToneSessionScreen
import com.id.mimikopi.desktop.port.ear.ToneSessionViewModel
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
            render(dark) { SectionsHome(onPractice = {}, onEdit = {}, onOpenMidi = {}) }
            render(dark) { SettingsScreen(onBack = {}) }
        }
    }

    @Test
    fun fallingNotesRenderInBothLooks() {
        val notes = listOf(
            FallingNote(60, 0.0, 1.0, left = false, label = "C"),
            FallingNote(48, 1.0, 2.0, left = true, label = "C", state = FallingState.DONE),
            FallingNote(61, 2.0, 1.0, left = false, label = "C#", state = FallingState.MISSED),
            FallingNote(64, 3.0, 1.0, left = false, label = "E", state = FallingState.BACKGROUND),
            FallingNote(67, 20.0, 1.0, left = false, label = "G"), // far above: not drawn
        )
        for (dark in listOf(true, false)) render(dark) {
            FallingNotesView(
                notes = notes, cursor = { 0.5 }, animate = false, keyRange = fallingKeyRange(48, 72), pressed = setOf(60, 61),
                keyLabel = { "C" }, modifier = Modifier.fillMaxWidth().height(500.dp), lines = listOf(0.0, 4.0),
                marks = listOf(62 to System.nanoTime()),
            )
        }
    }

    private fun args(vararg pairs: Pair<String, String?>) = SavedStateHandle(mapOf(*pairs))

    @Test
    fun readingAndSongScreensRenderInBothLooks() {
        val app = MimikopiApp
        for (dark in listOf(true, false)) {
            render(dark) { SkillScreen(Skill.READING, {}, {}, {}, {}, {}, vm = rememberViewModel { SkillViewModel(app) }) }
            render(dark) { ToneHomeScreen(onOpen = {}, onEdit = {}, onOpenMidi = {}, onOpenSettings = {}, vm = rememberViewModel { ToneHomeViewModel(app) }) }
            render(dark) { ToneSessionScreen(onBack = {}, vm = rememberViewModel { ToneSessionViewModel(app, args("levelId" to "intro1")) }) }
            render(dark) { ToneEditScreen(onBack = {}, vm = rememberViewModel { ToneEditViewModel(app, args("id" to null)) }) }
            render(dark) { com.id.mimikopi.desktop.port.metronome.MetronomeScreen(onBack = {}, vm = rememberViewModel { com.id.mimikopi.desktop.port.metronome.MetronomeViewModel(app) }) }
            render(dark) { SongsScreen(onPlay = { _, _ -> }, onOpenMidi = {}, onOpenSettings = {}, vm = rememberViewModel { SongsViewModel(app) }) }
            render(dark) { LessonScreen(onBack = {}, onOpenDrill = { _, _ -> }, vm = rememberViewModel { LessonViewModel(app, args("lessonId" to "1.1")) }) }
            render(dark) { DrillScreen(onBack = {}, vm = rememberViewModel { DrillViewModel(app, args("lessonId" to "1.14", "drill" to "0")) }) }
            render(dark) { DrillScreen(onBack = {}, vm = rememberViewModel { DrillViewModel(app, args("review" to "true")) }) }
            render(dark) { com.id.mimikopi.desktop.port.chords.ChordHomeScreen(onStart = {}, onOpenMidi = {}, onOpenSettings = {}, vm = rememberViewModel { com.id.mimikopi.desktop.port.chords.ChordHomeViewModel(app) }) }
            render(dark) { DrillScreen(onBack = {}, vm = rememberViewModel { DrillViewModel(app, args("practice" to "chords")) }) }
            render(dark) { PieceListScreen(onBack = {}, onOpenPiece = { _, _ -> }, vm = rememberViewModel { PieceListViewModel(app, args("lesson" to "2.23", "drill" to "0")) }) }
            render(dark) { ReaderScreen(onBack = {}, vm = rememberViewModel { ReaderViewModel(app, args("pieceId" to "ode-to-joy", "hand" to "RIGHT")) }) }
            render(dark) { ReaderScreen(onBack = {}, vm = rememberViewModel { ReaderViewModel(app, args("lessonId" to "1.18", "drill" to "0")) }) }
        }
    }
}
