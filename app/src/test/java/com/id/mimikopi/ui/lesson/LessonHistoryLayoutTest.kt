package com.id.mimikopi.ui.lesson

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.data.db.AttemptEntity
import com.id.mimikopi.ui.theme.MimikopiTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h850dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LessonHistoryLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun historyShowsExistingResultsFiltersDrillsAndSupportsBothThemes() {
        val base = Lesson.parse(File("../content/lessons/1.5.json").readText())
        val lesson = base.copy(drills = listOf(base.drills.first(), base.drills.first().copy(title = "แบบฝึกที่สอง")))
        fun row(id: Long, accuracy: Double, drill: Int = 0) = AttemptEntity(id, null, "1.5:E_FINGER_PATTERN:$drill",
            """{"mode":"WAIT","completed":true,"bpm":60}""", 1_792_540_800_000L + id * 86_400_000L,
            "RIGHT", null, accuracy, null, 0, null)
        var rows by mutableStateOf(listOf(row(2, .95), row(1, .7)))
        var dark by mutableStateOf(false)
        compose.setContent { MimikopiTheme(dark) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { LessonHistory(lesson, rows) }
        } }
        compose.onNodeWithTag("lesson-history-comparison").assertTextContains("70% → 95%", substring = true)
        compose.onNodeWithTag("lesson-history-filter-1").performClick()
        compose.onNodeWithTag("lesson-history-comparison").assertDoesNotExist()
        compose.onNodeWithText("0 รอบ · 0 วันที่มีผลบันทึก").assertIsDisplayed()
        compose.onNodeWithTag("lesson-history-filter-0").performClick()
        fun image(name: String) {
            val bitmap = compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
            File("build/render").mkdirs()
            File("build/render/$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        image("lesson-history-phone")
        compose.runOnIdle { dark = true }
        compose.onNodeWithTag("lesson-history-comparison").assertIsDisplayed()
        image("lesson-history-phone-dark")
        compose.runOnIdle { rows = emptyList() }
        compose.onNodeWithTag("lesson-history-empty").assertIsDisplayed()
        compose.onNodeWithTag("lesson-history-comparison").assertDoesNotExist()
    }
}
