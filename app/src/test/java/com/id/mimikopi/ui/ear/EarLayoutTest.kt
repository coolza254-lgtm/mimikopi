package com.id.mimikopi.ui.ear

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.id.mimikopi.core.ear.*
import com.id.mimikopi.ui.theme.MimikopiTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h850dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EarLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun q(item: String = "deg:4") = EarCourse.question(item,
        if (item.startsWith("deg:")) EarCourse.test(1)!!.items else EarCourse.ALL_ITEMS,
        KeyPool.C_ONLY, false, 0.0, Random(2))
    private fun ui(question: EarQuestion = q()) = EarUi(phase = EarPhase.ANSWER, questions = List(15) { question }, index = 2, title = "ทดสอบระดับหู 1")
    private fun image(name: String) {
        val bitmap = compose.onNodeWithTag("ear-session").captureToImage().asAndroidBitmap()
        File("build/render").mkdirs()
        File("build/render/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun midiPitchAnswerHidesTouchKeysAndDisconnectRestoresPhoneAnswers() {
        var midi by mutableStateOf(true)
        compose.setContent {
            MimikopiTheme(false) { EarSessionContent(ui(), midi, {}, {}, {}, {}, {}, {}, {}) {} }
        }
        compose.onNodeWithTag("ear-midi-answer").assertIsDisplayed()
        compose.onNodeWithTag("ear-touch-answers").assertDoesNotExist()
        compose.onNodeWithTag("ear-option-deg:4").assertDoesNotExist()
        compose.onNodeWithTag("ear-replay").assertIsEnabled()
        image("ear-b-midi")
        compose.runOnIdle { midi = false }
        compose.onNodeWithTag("ear-touch-answers").assertExists()
        compose.onNodeWithTag("ear-option-deg:4").assertIsEnabled()
        compose.onNodeWithTag("ear-midi-answer").assertDoesNotExist()
        image("ear-b-phone")
    }

    @Test fun feedbackAndReplayLockAllConflictingActionsUntilTheSoundFinishes() {
        var current by mutableStateOf(ui())
        var next = 0
        compose.setContent {
            MimikopiTheme(false) {
                EarSessionContent(current, false, {}, { current = current.copy(phase = EarPhase.FEEDBACK, right = true, audio = EarAudioStage.WAIT_RELEASE) },
                    { current = current.copy(phase = EarPhase.LISTEN, audio = EarAudioStage.QUESTION) }, {}, {}, { next++ }, {}) {}
            }
        }
        compose.onNodeWithTag("ear-option-deg:4").performClick()
        compose.onNodeWithTag("ear-next").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("ear-replay-feedback").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, next) }
        image("ear-b-release")
        compose.runOnIdle { current = current.copy(audio = EarAudioStage.IDLE) }
        compose.onNodeWithTag("ear-replay-feedback").assertIsEnabled().performClick()
        compose.onNodeWithTag("ear-replay").assertIsNotEnabled()
        compose.onNodeWithTag("ear-cadence").assertIsNotEnabled()
        compose.onNodeWithTag("ear-option-deg:4").assertIsNotEnabled()
    }

    @Test fun midiChordQuestionsShowNonClickableKeyMappingsInsteadOfAnswerButtons() {
        compose.setContent {
            MimikopiTheme(true) { EarSessionContent(ui(q("q:m")), true, {}, {}, {}, {}, {}, {}, {}) {} }
        }
        compose.onNodeWithTag("ear-touch-answers").assertDoesNotExist()
        compose.onNodeWithText("C", useUnmergedTree = true).assertExists().assertHasNoClickAction()
        compose.onNodeWithText("D", useUnmergedTree = true).assertExists().assertHasNoClickAction()
        compose.onNodeWithTag("ear-replay").assertIsDisplayed()
        image("ear-b-midi-chord-dark")
    }

    @Test
    @Config(qualifiers = "w900dp-h420dp-land-xhdpi")
    fun landscapeKeepsQuestionAndMidiAnswerBesideEachOtherAndControlsVisible() {
        compose.setContent {
            MimikopiTheme(false) { EarSessionContent(ui(), true, {}, {}, {}, {}, {}, {}, {}) {} }
        }
        val stage = compose.onNodeWithTag("ear-stage").getUnclippedBoundsInRoot()
        val answer = compose.onNodeWithTag("ear-midi-answer").getUnclippedBoundsInRoot()
        assertTrue(stage.left < answer.left)
        val footer = compose.onNodeWithTag("ear-replay").getUnclippedBoundsInRoot()
        assertTrue(stage.bottom < footer.top, "The active phase must remain above the fixed controls")
        compose.onNodeWithTag("ear-stage").assertIsDisplayed()
        compose.onNodeWithTag("ear-replay").assertIsDisplayed()
        compose.onNodeWithTag("ear-cadence").assertIsDisplayed()
        image("ear-b-midi-landscape")
    }
}
