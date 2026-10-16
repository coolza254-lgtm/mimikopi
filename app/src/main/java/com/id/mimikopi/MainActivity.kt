package com.id.mimikopi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.ui.about.AboutScreen
import com.id.mimikopi.ui.about.CreditsScreen
import com.id.mimikopi.ui.components.BottomNav
import com.id.mimikopi.ui.diag.DiagnosticsScreen
import com.id.mimikopi.ui.ear.EarDrillScreen
import com.id.mimikopi.ui.lesson.LessonScreen
import com.id.mimikopi.ui.metronome.MetronomeScreen
import com.id.mimikopi.ui.midi.MidiScreen
import com.id.mimikopi.ui.practice.DrillScreen
import com.id.mimikopi.ui.reader.PieceListScreen
import com.id.mimikopi.ui.reader.ReaderScreen
import com.id.mimikopi.ui.sections.SectionEditScreen
import com.id.mimikopi.ui.sections.SectionPracticeScreen
import com.id.mimikopi.ui.sections.SectionsScreen
import com.id.mimikopi.ui.settings.SettingsScreen
import com.id.mimikopi.ui.skill.Skill
import com.id.mimikopi.ui.skill.SkillScreen
import com.id.mimikopi.ui.songs.SongsScreen
import com.id.mimikopi.ui.theme.MimikopiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Black UI: light status and navigation bar icons on a transparent bar.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MimikopiTheme { AppNavigation() }
        }
    }
}

private object Routes {
    // The four tabs (M15).
    const val READING = "reading"
    const val EAR = "ear"
    const val SONGS = "songs"
    const val SECTIONS = "sections"

    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
    const val CREDITS = "credits"
    const val METRONOME = "metronome"
    const val MIDI = "midi"
    const val LESSON = "lesson/{lessonId}"
    const val DRILL = "drill/{lessonId}/{drill}"
    const val REVIEW = "review/{review}"
    const val PIECES = "pieces?lesson={lesson}&drill={drill}"
    fun piecesFor(lessonId: String, drill: Int) = "pieces?lesson=$lessonId&drill=$drill"
    const val READER = "reader/{pieceId}?hand={hand}"
    fun reader(id: String, hand: String?) = if (hand == null) "reader/$id" else "reader/$id?hand=$hand"
    const val SCORE_DRILL = "scoredrill/{lessonId}/{drill}"
    const val EAR_DRILL = "eardrill/{level}?bench={bench}"
    const val LESSON_EAR = "lessonear/{lessonId}/{drill}"
    const val SECTION_EDIT = "section/{sectionId}/edit"
    const val SECTION_PRACTICE = "section/{sectionId}/practice"
    fun lesson(id: String) = "lesson/$id"
    fun drill(id: String, index: Int) = "drill/$id/$index"
    const val REVIEW_NOW = "review/true"
    const val EAR_REVIEW = "eardrill/mix"
}

/** Opens a lesson drill in the runner that fits its type. */
private fun openLessonDrill(nav: NavController, app: MimikopiApp, lessonId: String, index: Int) {
    val lesson = app.learning.lesson(lessonId) ?: return
    val d = lesson.drills.getOrNull(index) ?: return
    when {
        d.type == ExerciseType.E_PIECE && d.pieces.size > 1 -> nav.navigate(Routes.piecesFor(lessonId, index))
        // A piece the lesson counts by the piece rule: practise it in the reader with its own progress.
        d.type == ExerciseType.E_PIECE && lesson.pass.piecesPassed != null -> nav.navigate(Routes.reader(d.pieces.first(), d.hand))
        d.type == ExerciseType.E_EAR -> nav.navigate("lessonear/$lessonId/$index")
        d.type.isScore -> nav.navigate("scoredrill/$lessonId/$index")
        else -> nav.navigate(Routes.drill(lessonId, index))
    }
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as MimikopiApp
    val openDrill: (String, Int) -> Unit = { id, i -> openLessonDrill(nav, app, id, i) }
    val optional = { name: String -> navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null } }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in setOf(Routes.READING, Routes.EAR, Routes.SONGS, Routes.SECTIONS)
    val selectTab: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(Routes.READING) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openMidi = { nav.navigate(Routes.MIDI) }
    val openSettings = { nav.navigate(Routes.SETTINGS) }
    Scaffold(
        containerColor = com.id.mimikopi.ui.theme.Tokens.Palette.Paper,
        bottomBar = { if (showBar) BottomNav(route, selectTab) },
    ) { inner ->
    // Screens arrive with a short fade and a small slide, and leave with a fade (quiet, not flashy).
    NavHost(
        navController = nav,
        startDestination = Routes.READING,
        modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 14 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 14 } },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 14 } },
    ) {
        composable(Routes.READING) {
            SkillScreen(
                skill = Skill.READING,
                onOpenStep = { nav.navigate(Routes.lesson(it)) },
                onOpenDrill = { openDrill(it, 0) },
                onReview = { nav.navigate(Routes.REVIEW_NOW) },
                onOpenMidi = openMidi,
                onOpenSettings = openSettings,
            )
        }
        composable(Routes.EAR) {
            SkillScreen(
                skill = Skill.EAR,
                onOpenStep = { openDrill(it, 0) },
                onOpenDrill = { openDrill(it, 0) },
                onReview = { nav.navigate(Routes.EAR_REVIEW) },
                onOpenMidi = openMidi,
                onOpenSettings = openSettings,
            )
        }
        composable(Routes.SONGS) {
            SongsScreen(onPlay = { id, hand -> nav.navigate(Routes.reader(id, hand)) }, onOpenMidi = openMidi, onOpenSettings = openSettings)
        }
        composable(Routes.SECTIONS) {
            SectionsScreen(
                onPractice = { nav.navigate("section/$it/practice") },
                onEdit = { nav.navigate("section/$it/edit") },
                onOpenMidi = openMidi,
                onOpenSettings = openSettings,
            )
        }
        composable(Routes.SECTION_EDIT) {
            SectionEditScreen(onBack = { nav.popBackStack() }, onPractice = {
                nav.navigate("section/$it/practice") { popUpTo(Routes.SECTIONS) }
            })
        }
        composable(Routes.SECTION_PRACTICE) {
            SectionPracticeScreen(onBack = { nav.popBackStack() }, onEdit = {
                nav.navigate("section/$it/edit") { popUpTo(Routes.SECTIONS) }
            })
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }, onOpenCredits = { nav.navigate(Routes.CREDITS) }) }
        composable(Routes.CREDITS) { CreditsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.METRONOME) { MetronomeScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.MIDI) { MidiScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LESSON) { LessonScreen(onBack = { nav.popBackStack() }, onOpenDrill = openDrill) }
        composable(Routes.DRILL) { DrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.REVIEW) { DrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PIECES, arguments = listOf(optional("lesson"), optional("drill"))) {
            PieceListScreen(onBack = { nav.popBackStack() }, onOpenPiece = { id, hand -> nav.navigate(Routes.reader(id, hand)) })
        }
        composable(Routes.READER, arguments = listOf(optional("hand"))) { ReaderScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SCORE_DRILL) { ReaderScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.EAR_DRILL, arguments = listOf(optional("bench"))) { EarDrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LESSON_EAR) { EarDrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenMidi = openMidi,
                onOpenMetronome = { nav.navigate(Routes.METRONOME) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
            )
        }
    }
    }
}
