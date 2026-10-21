package com.id.mimikopi.ui.reader

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.exercise.NoteIdItem
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import com.id.mimikopi.ui.practice.DrillUi
import com.id.mimikopi.ui.practice.Prompt
import com.id.mimikopi.ui.reading.NoteReadingQuestion
import com.id.mimikopi.ui.reading.ReadingKeyboard
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
class ScorePlayerLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val score = Score.from(PieceFile("layout-test", "ทดสอบ", "", "original", "L1",
        rh = listOf("C4/q D4/q E4/q F4/q", "G4/q F4/q E4/q D4/q"), lh = listOf("r/w", "r/w")))
    private fun snapshot(name: String) {
        File("build/render").mkdirs()
        val bitmap = compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        File("build/render/$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun midiUsesRemainingHeightAndPhoneKeepsPlayableKeysWithStopVisible() {
        var midi by mutableStateOf(true)
        var dark by mutableStateOf(false)
        var scale by mutableStateOf(1f)
        var stopped = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, scale)) {
                MimikopiTheme(dark) {
                    ScorePlayerViewport("โหมดรอ: กดโน้ตที่เส้นชี้", midi, { stopped++ }, keyboard = {
                        ReadingKeyboard(false, 60..72, NoteNameStyle.LETTER, false, emptySet(), { _, _ -> }, performance = true)
                    }) { mod ->
                        ScoreView(score, ScoreLayout.of(score), { 0.0 }, false, emptyMap(), emptyList(), null, emptyList(),
                            false, NoteNameStyle.LETTER, {}, mod)
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("score-player").getUnclippedBoundsInRoot()
        val music = compose.onNodeWithTag("score-player-music").getUnclippedBoundsInRoot()
        assertTrue((music.bottom - music.top) >= (root.bottom - root.top) * .78f, "MIDI must not reserve space for a keyboard or footer")
        assertEquals((root.right - root.left), (music.right - music.left))
        compose.onNodeWithTag("reading-stop").assertIsDisplayed().performClick()
        assertEquals(1, stopped)
        snapshot("score-player-midi-landscape")
        compose.runOnIdle { midi = false; dark = true }
        compose.onNodeWithTag("reading-keyboard").assertIsDisplayed()
        val keys = compose.onNodeWithTag("reading-keyboard").getUnclippedBoundsInRoot()
        val phoneMusic = compose.onNodeWithTag("score-player-music").getUnclippedBoundsInRoot()
        assertTrue(keys.bottom <= root.bottom && phoneMusic.bottom <= keys.top)
        assertTrue((phoneMusic.bottom - phoneMusic.top) >= (root.bottom - root.top) * .45f, "Single-bank phone input must leave readable music")
        snapshot("score-player-phone-landscape-dark")
        compose.runOnIdle { scale = 1.6f }
        compose.onNodeWithTag("reading-stop").assertIsDisplayed().performClick()
        assertEquals(2, stopped)
    }

    @Test fun singleNoteOnSmallLandscapePhoneShowsStaffAnswersAndStopTogether() {
        var midi by mutableStateOf(false)
        var stopped = false
        val q = DrillUi.Question(Prompt.Note(NoteIdItem(SpelledPitch.parse("E4"), Clef.TREBLE)), 1, 10, null)
        compose.setContent {
            MimikopiTheme(false) {
                Box(Modifier.fillMaxSize().testTag("note-root")) {
                    NoteReadingQuestion(q, midi, false, NoteNameStyle.LETTER, 60..72,
                        listOf("C4", "D4", "E4", "F4", "G4").map(SpelledPitch::parse), emptySet(), { _, _ -> }, {}, {}, {}, { stopped = true })
                }
            }
        }
        compose.onNodeWithTag("reading-staff").assertIsDisplayed()
        compose.onNodeWithTag("reading-answer-64").assertIsDisplayed()
        compose.onNodeWithTag("reading-stop").assertIsDisplayed().performClick()
        assertTrue(stopped)
        val staff = compose.onNodeWithTag("reading-staff").getUnclippedBoundsInRoot()
        assertTrue((staff.bottom - staff.top) > 100.dp)
        snapshot("note-player-phone-landscape")
        compose.runOnIdle { midi = true }
        compose.onNodeWithTag("reading-name-answers").assertDoesNotExist()
        compose.onNodeWithTag("reading-keyboard").assertDoesNotExist()
        compose.onNodeWithTag("reading-midi").assertIsDisplayed()
        snapshot("note-player-midi-landscape")
    }

    @Test fun intervalQuestionKeepsLargeStaffAndOnlyShowsKeysInThePlayingPhase() {
        var pair by mutableStateOf<Int?>(0)
        var midi by mutableStateOf(false)
        compose.setContent {
            MimikopiTheme(false) {
                com.id.mimikopi.ui.reading.IntervalReadingQuestion(
                    DrillUi.Interval(listOf("C4", "D4", "E4").map(SpelledPitch::parse), Clef.TREBLE, pair, 0, 1, 5, null, null, "MOTION", listOf(2, 3), true),
                    midi, false, NoteNameStyle.LETTER, 60..72, emptySet(), { _, _ -> }, {}, {},
                    answers = { androidx.compose.material3.Button(onClick = {}, modifier = Modifier.testTag("interval-answer")) { androidx.compose.material3.Text("ขั้นขึ้น") } })
            }
        }
        compose.onNodeWithTag("interval-answer").assertIsDisplayed()
        compose.onNodeWithTag("reading-keyboard").assertDoesNotExist()
        val staff = compose.onNodeWithTag("interval-staff").getUnclippedBoundsInRoot()
        assertTrue(staff.bottom - staff.top > 200.dp)
        compose.onNodeWithTag("reading-stop").assertIsDisplayed()
        snapshot("interval-player-landscape")
        compose.runOnIdle { pair = null }
        compose.onNodeWithTag("interval-answer").assertDoesNotExist()
        compose.onNodeWithTag("reading-keyboard").assertIsDisplayed()
        compose.runOnIdle { midi = true }
        compose.onNodeWithTag("reading-keyboard").assertDoesNotExist()
        compose.onNodeWithTag("reading-midi").assertIsDisplayed()
    }

    @Test fun landscapePolicyAllowsEitherSideAndRestoresTheOriginalRotationPolicy() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderLandscapeSession(activity).use {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
            }
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
        } finally { controller.close() }
    }
}
