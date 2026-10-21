package com.id.mimikopi.ui.reading

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.id.mimikopi.ui.theme.Tokens
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.exercise.NoteIdItem
import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.lesson.Drill
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Clef
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.playalong.PlayAlongJudge
import com.id.mimikopi.core.playalong.PlaySetup
import com.id.mimikopi.ui.practice.*
import com.id.mimikopi.ui.reader.*
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
class ReadingLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val choices get() = ReadingPractice().drill().pool.map { NoteIdItem.parse(it).pitch }
    private fun question() = DrillUi.Question(Prompt.Note(NoteIdItem(SpelledPitch.parse("E4"), Clef.TREBLE)), 1, 20, null)
    private fun image(name: String) {
        val bitmap = compose.onNodeWithTag("reading-root").captureToImage().asAndroidBitmap()
        File("build/render").mkdirs()
        File("build/render/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun content(dark: Boolean = false, body: @Composable () -> Unit) {
        compose.setContent { MimikopiTheme(dark) { Box(Modifier.fillMaxSize().background(Tokens.Palette.Paper).testTag("reading-root")) { body() } } }
    }

    @Test fun phoneCanAnswerExactNamesAndMidiReplacesAnswerButtonsImmediately() {
        var midi by mutableStateOf(false)
        var answered: Int? = null
        content {
            NoteReadingQuestion(question(), midi, false, NoteNameStyle.BOTH, 60..67, choices, emptySet(), { _, _ -> }, { answered = it }, {}, {})
        }
        compose.onNodeWithTag("reading-answer-64").assertIsDisplayed().performClick()
        assertEquals(64, answered)
        compose.onNodeWithText("คำตอบคือ", substring = true).assertDoesNotExist()
        image("reading-note-phone")
        compose.runOnIdle { midi = true }
        compose.onNodeWithTag("reading-name-answers").assertDoesNotExist()
        compose.onNodeWithTag("reading-keyboard").assertDoesNotExist()
        compose.onNodeWithTag("reading-midi").assertIsDisplayed()
        image("reading-note-midi")
        compose.runOnIdle { midi = false }
        compose.onNodeWithTag("reading-answer-60").assertIsEnabled()
    }

    @Test fun wrongAnswerShowsCorrectionAndWaitsForNextWithoutAcceptingAnotherScoredAnswer() {
        var next = 0; var answered = 0
        content(dark = true) {
            NoteReadingQuestion(question().copy(feedback = Feedback.WRONG, answeredMidi = 62, firstAnswered = 1, canContinue = true),
                false, true, NoteNameStyle.BOTH, 60..67, choices, emptySet(), { _, _ -> }, { answered++ }, { next++ }, {})
        }
        compose.onNodeWithText("คำตอบคือ", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("reading-answer-64").assertIsNotEnabled()
        compose.onNodeWithTag("reading-next").assertIsDisplayed().performClick()
        assertEquals(1, next); assertEquals(0, answered)
        image("reading-note-correction-dark")
    }

    @Test fun changingOctaveDisablingAnswersOrConnectingMidiReleasesEveryHeldTouchKey() {
        var midi by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        val events = mutableListOf<Pair<Int, Boolean>>()
        content {
            Column { ReadingKeyboard(midi, 48..84, NoteNameStyle.BOTH, false, emptySet(), { note, down -> events += note to down }, enabled = enabled) }
        }
        compose.onNodeWithTag("reading-octave").assertTextEquals("C4–C5")
        compose.onNodeWithTag("reading-keyboard").performTouchInput { down(Offset(8f, height - 8f)) }
        assertEquals(60 to true, events.last())
        compose.onNodeWithTag("reading-octave-up").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        assertEquals(60 to false, events.last())
        compose.onNodeWithTag("reading-octave").assertTextEquals("C5–C6")
        compose.onNodeWithTag("reading-keyboard").performTouchInput { up() }
        compose.onNodeWithTag("reading-keyboard").performTouchInput { down(Offset(8f, height - 8f)) }
        assertEquals(72 to true, events.last())
        compose.runOnIdle { enabled = false }
        compose.waitForIdle()
        assertEquals(72 to false, events.last())
        compose.onNodeWithTag("reading-keyboard").assertIsNotEnabled().performTouchInput { up() }
        val count = events.size
        compose.onNodeWithTag("reading-keyboard").performTouchInput { click() }
        assertEquals(count, events.size)
        compose.runOnIdle { enabled = true }
        compose.onNodeWithTag("reading-keyboard").performTouchInput { down(Offset(8f, height - 8f)) }
        assertEquals(72 to true, events.last())
        compose.runOnIdle { midi = true }
        compose.waitForIdle()
        assertEquals(72 to false, events.last())
        compose.onNodeWithTag("reading-keyboard").assertDoesNotExist()
    }

    @Test fun twoPhoneBanksCanHoldATwoHandChordAndSharedMiddleCReleasesOnlyAfterBothFingers() {
        val score = Score.from(PieceFile("phone-chord", "สองมือ", "", "original", "L5", rh = listOf("C4/w"), lh = listOf("C3/w")))
        val judge = PlayAlongJudge(score, PlaySetup(PlayMode.WAIT, setOf(Hand.LEFT, Hand.RIGHT), 0, 0, 60.0, 100)).apply { start(0) }
        val events = mutableListOf<Pair<Int, Boolean>>()
        var pressed by mutableStateOf(emptySet<Int>())
        content {
            Column(Modifier.fillMaxSize()) {
                PagedScoreView(score, { 0.0 }, false, emptyMap(), emptyList(), null, emptyList(), false, NoteNameStyle.BOTH, {}, Modifier.weight(1f).fillMaxWidth(), paper = false)
                ReadingRunBar("โหมดรอ · กดสองมือพร้อมกันได้", {})
                ReadingKeyboard(false, 48..72, NoteNameStyle.BOTH, false, pressed, { note, down ->
                    events += note to down
                    pressed = if (down) pressed + note else pressed - note
                    if (down) judge.onNoteOn(note, 0) else judge.onNoteOff(note)
                }, twoHands = true)
            }
        }
        val upper = compose.onNodeWithTag("reading-keyboard").fetchSemanticsNode().boundsInRoot
        val lower = compose.onNodeWithTag("reading-keyboard-low").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(0, Offset(upper.left + 8, upper.bottom - 8))
            down(1, Offset(lower.left + 8, lower.bottom - 8))
        }
        assertEquals(listOf(60 to true, 48 to true), events)
        assertTrue(judge.isFinished(0), "Both notes must be held together to finish the real WAIT judge's chord")
        image("reading-melody-two-banks")
        compose.onRoot().performTouchInput { up(0); up(1) }
        events.clear()
        // Middle C appears in both banks; the second finger must not create or release another voice.
        compose.onRoot().performTouchInput {
            down(0, Offset(upper.left + 8, upper.bottom - 8))
            down(1, Offset(lower.right - 8, lower.bottom - 8))
        }
        assertEquals(listOf(60 to true), events)
        compose.onRoot().performTouchInput { up(0) }
        assertEquals(listOf(60 to true), events)
        compose.onRoot().performTouchInput { up(1) }
        assertEquals(listOf(60 to true, 60 to false), events)
    }

    @Test fun melodySetupHasDirectPaceAndTempoControlsAndARealScorePreview() {
        var ui by mutableStateOf(ReaderUi(options = ReaderOptions(mode = PlayMode.WAIT)))
        var started = 0
        val score = Score.from(ScoreGenerators.sightReading(Drill(ExerciseType.E_READ_PIECE, "test", level = "L1"), 42))
        content {
            ReadingScoreSetup(ui, false, true, false, true, { ui = ui.copy(options = ui.options.copy(mode = it)) }, {},
                { ui = ui.copy(bpm = ui.bpm + it) }, { started++ }, {}, {}, {}, {}) { mod ->
                PagedScoreView(score, { 0.0 }, false, emptyMap(), emptyList(), null, emptyList(), false, NoteNameStyle.BOTH, {}, mod, paper = false)
            }
        }
        compose.onNodeWithTag("reading-mode-wait").assertIsSelected()
        compose.onNodeWithTag("reading-score-start").assertIsDisplayed()
        image("reading-melody-setup")
        compose.onNodeWithTag("reading-mode-tempo").performClick()
        compose.onNodeWithTag("reading-slower").performClick()
        compose.onNodeWithTag("reading-score-start").performClick()
        assertEquals(55, ui.bpm); assertEquals(1, started)
    }

    @Test fun homeLetsUsersStartSingleNotesOrSelectAMelodyLevel() {
        var single = 0; var melody: Int? = null
        content { ReadingHomeContent(false, {}, { single++ }, { melody = it }, {}) }
        compose.onNodeWithText("เริ่มอ่านทีละตัว").performClick()
        compose.onNodeWithText("L2").performScrollTo().performClick()
        compose.onNodeWithText("เริ่มอ่านทำนอง").performScrollTo().performClick()
        assertEquals(1, single); assertEquals(2, melody)
        image("reading-home")
    }

    @Test fun stopRemainsVisibleAndClickableAtLargeFontScaleOnANarrowPhone() {
        var stopped = 0
        content {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Box(Modifier.width(320.dp)) { ReadingRunBar("กดโน้ตตามเส้นชี้ · โหมดรอให้กดถูกก่อน · ต่อ MIDI", { stopped++ }) }
            }
        }
        compose.onNodeWithTag("reading-stop").assertIsDisplayed().performClick()
        assertEquals(1, stopped)
        val stop = compose.onNodeWithTag("reading-stop").getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithTag("reading-run-bar").getUnclippedBoundsInRoot()
        assertTrue(stop.right <= bar.right)
    }

    @Test
    @Config(qualifiers = "w900dp-h420dp-land-xhdpi")
    fun landscapeMidiKeepsTheStaffAndCorrectionBesideEachOtherWithNextAccessible() {
        content {
            NoteReadingQuestion(question().copy(feedback = Feedback.WRONG, answeredMidi = 62, firstAnswered = 1, canContinue = true),
                true, false, NoteNameStyle.BOTH, 60..67, choices, emptySet(), { _, _ -> }, {}, {}, {})
        }
        compose.onNodeWithTag("reading-next").assertIsDisplayed()
        compose.onNodeWithTag("reading-staff").assertIsDisplayed()
        val staff = compose.onNodeWithTag("reading-staff").getUnclippedBoundsInRoot()
        val answer = compose.onNodeWithText("คำตอบคือ", substring = true).getUnclippedBoundsInRoot()
        assertTrue(staff.right < answer.left)
        image("reading-note-midi-landscape")
    }
}
