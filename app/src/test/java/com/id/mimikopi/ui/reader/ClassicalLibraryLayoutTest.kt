package com.id.mimikopi.ui.reader

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.id.mimikopi.core.playalong.*
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.ui.songs.HeroicDashboard
import com.id.mimikopi.ui.theme.MimikopiTheme
import com.id.mimikopi.ui.components.ScreenScaffold
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
    private fun score(id: String, dir: String = "pieces") = Score.from(PieceFile.parse(File("../content/$dir/$id.json").readText()))
    private val pieces get() = listOf(score(HeroicPlan.EASY), score(HeroicPlan.THEME))
    private fun snapshot(name: String) {
        File("build/render").mkdirs()
        File("build/render/$name.png").outputStream().use {
            compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun onlyHeroicArrangementsAreListedAndCreditsRemainReadable() {
        var opened: Pair<String, String?>? = null
        compose.setContent { MimikopiTheme(false) {
            PieceLibrary(pieces, emptyMap(), onBack = {}, onOpenPiece = { id, hand -> opened = id to hand })
        } }
        compose.onNodeWithTag("all-pieces-filter").assertDoesNotExist()
        compose.onNodeWithTag("piece-heroic-polonaise-easy").performClick()
        assertEquals(HeroicPlan.EASY to "RIGHT", opened)
        compose.onNodeWithTag("piece-heroic-polonaise-theme").performScrollTo().performClick()
        assertEquals(HeroicPlan.THEME to "RIGHT", opened)
        snapshot("heroic-arrangements")
        compose.onNodeWithTag("source-heroic-polonaise-theme").performScrollTo().performClick()
        compose.onNodeWithText("https://creativecommons.org/licenses/by/4.0/", substring = true).assertExists()
        compose.onNodeWithText("The Fryderyk Chopin Institute", substring = true).assertExists()
        compose.onNodeWithText("ปิด").performClick()
        compose.onNodeWithTag("piece-source").assertDoesNotExist()
    }
    @Test fun dashboardRecommendsExactNextTargetAndManualReviewRemainsAvailable() {
        var opened: String? = null
        compose.setContent { MimikopiTheme(false) {
            ScreenScaffold("Heroic", {}) { HeroicDashboard(emptyMap(), { opened = it }, { _, _ -> }, {}) }
        } }
        compose.onNodeWithTag("heroic-next").performClick()
        assertEquals("c-0-right", opened)
        snapshot("heroic-plan-phone")
        compose.onNodeWithTag("heroic-progress").performScrollTo().assertTextEquals("ทำได้ตามเกณฑ์ 0 จาก 30 เป้าหมายฝึก")
        compose.onNodeWithTag("heroic-plan-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("heroic-target-ab-whole").performScrollTo().performClick()
        assertEquals("ab-whole", opened)
    }
    @Test fun darkDashboardShowsMeasuredEvidenceAndSkipsCompletedReadingTarget() {
        val p = mapOf(HeroicPlan.EASY to PieceProgress(HeroicPlan.EASY, studyTargets = mapOf(
            "c-0-right" to StudyEvidence(attempts = 2, days = listOf("2026-10-21")),
            "c-0-left" to StudyEvidence(attempts = 1, lastDay = "2026-10-21", lastAccuracy = .75, troubleMeasures = listOf(1)))))
        var opened: String? = null
        compose.setContent { MimikopiTheme(true) {
            ScreenScaffold("Heroic", {}) { HeroicDashboard(p, { opened = it }, { _, _ -> }, {}) }
        } }
        compose.onNodeWithText("ล่าสุด 2026-10-21 · โน้ต 75% · ไม่ได้วัดจังหวะ").assertExists()
        compose.onNodeWithText("ควรทวนห้อง 2 ของฉบับฝึก").assertExists()
        compose.onNodeWithTag("heroic-next").performScrollTo().performClick()
        assertEquals("c-0-left", opened)
        snapshot("heroic-plan-dark")
    }
    @Test fun lessonSelectionStillOpensFoundationalExerciseWithItsPrescribedHand() {
        var opened: Pair<String, String?>? = null
        compose.setContent { MimikopiTheme(false) {
            PieceLibrary(listOf(score("ex-c-major-scale-rh", "exercises")), emptyMap(), "บทเรียน", "ฝึกสเกล", "RIGHT", {}, { id, hand -> opened = id to hand })
        } }
        compose.onNodeWithTag("piece-ex-c-major-scale-rh").performClick()
        assertEquals("ex-c-major-scale-rh" to "RIGHT", opened)
    }
}
