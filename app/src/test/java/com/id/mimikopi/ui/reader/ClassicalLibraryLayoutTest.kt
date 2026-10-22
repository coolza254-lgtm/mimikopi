package com.id.mimikopi.ui.reader

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.ui.theme.MimikopiTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h850dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicalLibraryLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val pieces get() = File("../content/pieces").listFiles()!!.filter { it.extension == "json" }
        .map { Score.from(PieceFile.parse(it.readText())) }.sortedBy { it.level }
    private fun snapshot(name: String) {
        File("build/render").mkdirs()
        File("build/render/$name.png").outputStream().use {
            compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun suggestedOrderOpensTheRecommendedHandAndAllSongsRemainAccessible() {
        var opened: Pair<String, String?>? = null
        compose.setContent { MimikopiTheme(false) { Box(Modifier.fillMaxSize().testTag("library")) {
            PieceLibrary(pieces, emptyMap(), onBack = {}, onOpenPiece = { id, hand -> opened = id to hand })
        } } }
        compose.onNodeWithTag("classical-filter").assertIsSelected()
        snapshot("classical-library-phone")
        compose.onNodeWithTag("piece-ode-to-joy-first").performClick()
        assertEquals("ode-to-joy-first" to "RIGHT", opened)
        compose.onNodeWithTag("piece-canon-in-d-study").performScrollTo().performClick()
        assertEquals("canon-in-d-study" to "LEFT", opened)
        compose.onNodeWithTag("piece-chopin-prelude-e-minor-easy").performScrollTo()
        snapshot("classical-chopin-easy")
        compose.onNodeWithTag("piece-chopin-prelude-e-minor-chords").performScrollTo()
        snapshot("classical-chopin-chords")
        compose.onNodeWithTag("piece-heroic-polonaise-easy").performScrollTo().performClick()
        assertEquals("heroic-polonaise-easy" to "RIGHT", opened)
        snapshot("classical-heroic-easy")
        compose.onNodeWithTag("piece-heroic-polonaise-theme").performScrollTo().performClick()
        assertEquals("heroic-polonaise-theme" to "RIGHT", opened)
        snapshot("classical-heroic-theme")
        compose.onNodeWithTag("piece-moonlight-intro-easy").performScrollTo().performClick()
        assertEquals("moonlight-intro-easy" to "RIGHT", opened)
        snapshot("classical-moonlight-easy")
        compose.onNodeWithTag("piece-moonlight-first-theme").performScrollTo().performClick()
        assertEquals("moonlight-first-theme" to "LEFT", opened)
        snapshot("classical-moonlight-theme")
        compose.onNodeWithTag("all-pieces-filter").performScrollTo().performClick()
        compose.onNodeWithTag("piece-twinkle-twinkle").performScrollTo().performClick()
        assertEquals("twinkle-twinkle" to null, opened)
    }

    @Test fun attributionIsReadableWithoutStartingPracticeAndDialogCloses() {
        var opens = 0
        compose.setContent { MimikopiTheme(true) { Box(Modifier.fillMaxSize().testTag("library")) {
            PieceLibrary(pieces, emptyMap(), onBack = {}, onOpenPiece = { _, _ -> opens++ })
        } } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("1. Ode to Joy · เริ่มห้าโน้ต (ท่อนย่อ)", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(Color.White, layouts.single().layoutInput.style.color, "Dark card title must contrast with its black background")
        snapshot("classical-library-dark")
        compose.onNodeWithTag("source-canon-in-d-study").performScrollTo().performClick()
        assertEquals(0, opens)
        compose.onNodeWithText("Jim Paterson", substring = true).assertExists()
        compose.onNodeWithText("https://creativecommons.org/licenses/by/3.0/", substring = true).assertExists()
        compose.onNodeWithText("ปิด").performClick()
        compose.onNodeWithTag("piece-source").assertDoesNotExist()
    }

    @Test fun lessonSelectionPreservesItsPieceSubsetAndPrescribedHand() {
        var opened: Pair<String, String?>? = null
        compose.setContent { MimikopiTheme(false) {
            PieceLibrary(pieces.filter { it.id == "twinkle-twinkle" }, emptyMap(), "บทเรียน", "ฝึกสองมือ", "BOTH", {}, { id, hand -> opened = id to hand })
        } }
        compose.onNodeWithTag("classical-filter").assertDoesNotExist()
        compose.onNodeWithTag("piece-twinkle-twinkle").performClick()
        assertEquals("twinkle-twinkle" to "BOTH", opened)
    }
}
