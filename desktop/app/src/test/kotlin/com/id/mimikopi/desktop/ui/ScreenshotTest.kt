package com.id.mimikopi.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.data.AppDirs
import com.id.mimikopi.desktop.data.Database
import com.id.mimikopi.desktop.session.ReaderArgs
import com.id.mimikopi.desktop.ui.screens.EarHomeScreen
import com.id.mimikopi.desktop.ui.screens.LessonScreen
import com.id.mimikopi.desktop.ui.screens.MetronomeScreen
import com.id.mimikopi.desktop.ui.screens.ProgressScreen
import com.id.mimikopi.desktop.ui.screens.ReaderScreen
import com.id.mimikopi.desktop.ui.screens.SettingsScreen
import com.id.mimikopi.desktop.ui.screens.SkillsScreen
import com.id.mimikopi.desktop.ui.screens.SongsScreen
import com.id.mimikopi.desktop.ui.screens.TodayScreen
import com.id.mimikopi.desktop.ui.theme.MimikopiTheme
import org.jetbrains.skia.EncodedImageFormat
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * Renders the main pages offscreen in both looks (OLED black and white) at a laptop window size, to
 * desktop/app/build/screenshots. CI prints them so the pages can be reviewed without a Windows machine.
 */
class ScreenshotTest {
    companion object {
        private lateinit var services: Services
        private val out = File("build/screenshots").apply { mkdirs() }

        @BeforeClass @JvmStatic
        fun setUp() {
            val dir = Files.createTempDirectory("mimikopi-shots").toFile()
            val dirs = AppDirs(dir)
            services = Services(dirs, Database.open(dirs.database), Piano(nullDevice = true), "test", startMidi = false)
            // A little history so the lesson page shows real rows and a comparison.
            fun row(at: Long, acc: Double) = AttemptRow(0, null, "1.5:E_FINGER_PATTERN:0",
                """{"seed":$at,"drill":0,"lesson":"1.5","bpm":60.0,"mode":"WAIT","completed":true,"inputPresent":true,"inputSources":"USB_MIDI","from":0,"to":3,"toleranceMs":120}""",
                at, "RIGHT", null, acc, null, 1, null, 0)
            services.db.insertAttempt(row(1_760_000_000_000, 0.82), emptyList())
            services.db.insertAttempt(row(1_760_090_000_000, 0.94), emptyList())
        }

        @AfterClass @JvmStatic
        fun tearDown() = services.close()
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        for (dark in listOf(true, false)) {
            val ctx = AppContext(services, Navigator(), KeyRouting(), toggleFullscreen = {}, isFullscreen = { false })
            val scene = ImageComposeScene(1280, 800, Density(1f)) {
                MimikopiTheme(dark) { CompositionLocalProvider(LocalApp provides ctx) { content() } }
            }
            try {
                scene.render(0)
                scene.render(500_000_000)
                val png = scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
                val f = File(out, "$name-${if (dark) "dark" else "light"}.png")
                f.writeBytes(png)
                assertTrue(f.length() > 1000)
            } finally {
                scene.close()
            }
        }
    }

    @Test fun today() = shoot("today") { TodayScreen() }
    @Test fun lesson() = shoot("lesson-1.5") { LessonScreen("1.5") }
    @Test fun reader() = shoot("reader-heroic") { ReaderScreen(ReaderArgs(pieceId = "heroic-polonaise-theme")) }
    @Test fun readerFalling() {
        // Reader options are one stored row; falling notes on for this shot only.
        val key = com.id.mimikopi.desktop.learning.SettingKeys.READER_OPTIONS
        val before = services.db.get(key)
        services.db.put(key, kotlinx.serialization.json.Json.encodeToString(com.id.mimikopi.desktop.session.ReaderOptions.serializer(),
            com.id.mimikopi.desktop.session.ReaderOptions(falling = true)))
        try {
            shoot("reader-falling") { ReaderScreen(ReaderArgs(pieceId = "heroic-polonaise-theme")) }
        } finally {
            services.awaitPendingWrites()
            if (before != null) services.db.put(key, before) else services.db.put(key, kotlinx.serialization.json.Json.encodeToString(
                com.id.mimikopi.desktop.session.ReaderOptions.serializer(), com.id.mimikopi.desktop.session.ReaderOptions()))
        }
    }
    @Test fun readerLesson() = shoot("reader-lesson-1.5") { ReaderScreen(ReaderArgs(lessonId = "1.5", drill = 0)) }
    @Test fun songs() = shoot("songs") { SongsScreen() }
    @Test fun settings() = shoot("settings") { SettingsScreen() }
    @Test fun progress() = shoot("progress") { ProgressScreen() }
    @Test fun ear() = shoot("ear") { EarHomeScreen() }
    @Test fun skills() = shoot("skills") { SkillsScreen() }
    @Test fun metronome() = shoot("metronome") { MetronomeScreen() }
}
