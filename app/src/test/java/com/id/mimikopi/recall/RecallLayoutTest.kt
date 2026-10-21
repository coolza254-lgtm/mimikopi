package com.id.mimikopi.recall

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.id.mimikopi.core.recall.*
import com.id.mimikopi.ui.recall.*
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.MimikopiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h850dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecallLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val phrase = PrivatePhrase("p", "วลี A", 42, 48, "คำใบ้ที่ต้องซ่อน")
    private val song = PrivateSong("s", "เพลงส่วนตัวของฉัน", phrases = listOf(phrase))
    private fun snapshot(name: String) {
        val file = File("build/render/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun privateLibraryOpensTheChosenSongAndExplainsTheEvidenceType() {
        var opened: String? = null
        compose.setContent { MimikopiTheme(false) { ScreenScaffold("เพลงส่วนตัว", {}) {
            RecallLibrary(listOf(song), false, { opened = it }, {}, {})
        } } }
        compose.onNodeWithTag("recall-song-s").performClick()
        assertEquals("s", opened)
        compose.onNodeWithText("1 ช่วง · ประเมินการเล่นด้วยตนเอง").assertExists()
        snapshot("recall-library-phone")
    }
    @Test fun recommendedPhraseUsesTheChosenHandAndDoesNotExposeItsCue() {
        var started: String? = null
        var chosen = RecallHand.RIGHT
        compose.setContent { MimikopiTheme(false) { ScreenScaffold("ช่วงของฉัน", {}) {
            RecallSongContent(song, chosen, false, { chosen = it }, { started = it.id }, {}, {}, {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("recall-next").performClick()
        assertEquals("p", started)
        compose.onNodeWithText(phrase.cue).assertDoesNotExist()
        snapshot("recall-phrase-phone")
    }
    @Test fun trialRequiresAttestationAndHintUsePreventsAnIndependentResult() {
        var run by mutableStateOf(RecallRun("s", "p", 1, RecallHand.RIGHT, presses = 3))
        var result: RecallOutcome? = null
        compose.setContent { MimikopiTheme(true) { ScreenScaffold("ลองเล่นเอง", {}) {
            RecallTrial(song.title, phrase, run, false, { run = run.copy(helped = true) }, {}, false, {}, {},
                { outcome, _, _, _, _ -> result = outcome }, {})
        } } }
        compose.onNodeWithTag("recall-cue").assertDoesNotExist()
        compose.onNodeWithTag("recall-independent").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("recall-unaided").performScrollTo().performClick()
        compose.onNodeWithTag("recall-independent").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("recall-hint").performScrollTo().performClick()
        compose.onNodeWithTag("recall-cue").assertExists()
        compose.onNodeWithTag("recall-independent").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("เล่นได้โดยใช้ตัวช่วย").performScrollTo().performClick()
        assertEquals(RecallOutcome.HELPED, result)
        snapshot("recall-trial-dark")
    }
    @Test fun pausedOrEmptyTrialCannotRecordAnOutcomeAndRestartClearsAttestation() {
        var run by mutableStateOf(RecallRun("s", "p", 1, RecallHand.BOTH, presses = 3))
        compose.setContent { MimikopiTheme(false) { ScreenScaffold("ลองเล่นเอง", {}) {
            RecallTrial(song.title, phrase, run, false, {}, {}, false, {}, {}, { _, _, _, _, _ -> }, {})
        } } }
        compose.onNodeWithTag("recall-unaided").performScrollTo().performClick()
        compose.onNodeWithTag("recall-independent").performScrollTo().assertIsEnabled()
        compose.runOnIdle { run = run.copy(paused = true) }
        compose.onNodeWithTag("recall-independent").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { run = RecallRun("s", "p", 1, RecallHand.BOTH, presses = 3) }
        compose.onNodeWithTag("recall-independent").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { run = run.copy(presses = 0) }
        compose.onNodeWithText("เล่นได้โดยใช้ตัวช่วย").performScrollTo().assertIsNotEnabled()
    }
}
