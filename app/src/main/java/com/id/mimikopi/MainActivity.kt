package com.id.mimikopi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.id.mimikopi.ui.about.AboutScreen
import com.id.mimikopi.ui.about.CreditsScreen
import com.id.mimikopi.ui.piano.PianoScreen
import com.id.mimikopi.ui.metronome.MetronomeScreen
import com.id.mimikopi.ui.midi.MidiScreen
import com.id.mimikopi.ui.lesson.LessonScreen
import com.id.mimikopi.ui.practice.DrillScreen
import com.id.mimikopi.ui.today.TodayScreen
import com.id.mimikopi.ui.diag.DiagnosticsScreen
import com.id.mimikopi.ui.home.HomeScreen
import com.id.mimikopi.ui.settings.SettingsScreen
import com.id.mimikopi.ui.theme.MimikopiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MimikopiTheme { AppNavigation() }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
    const val PIANO = "piano"
    const val CREDITS = "credits"
    const val METRONOME = "metronome"
    const val MIDI = "midi"
    const val TODAY = "today"
    const val LESSON = "lesson/{lessonId}"
    const val DRILL = "drill/{lessonId}/{drill}"
    const val REVIEW = "review/{review}"
    fun lesson(id: String) = "lesson/$id"
    fun drill(id: String, index: Int) = "drill/$id/$index"
    const val REVIEW_NOW = "review/true"
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenPiano = { nav.navigate(Routes.PIANO) },
                onOpenMetronome = { nav.navigate(Routes.METRONOME) },
                onOpenMidi = { nav.navigate(Routes.MIDI) },
                onOpenToday = { nav.navigate(Routes.TODAY) },
            )
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }, onOpenCredits = { nav.navigate(Routes.CREDITS) }) }
        composable(Routes.CREDITS) { CreditsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PIANO) { PianoScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.METRONOME) { MetronomeScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.MIDI) { MidiScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TODAY) {
            TodayScreen(
                onBack = { nav.popBackStack() },
                onOpenLesson = { nav.navigate(Routes.lesson(it)) },
                onOpenDrill = { id, i -> nav.navigate(Routes.drill(id, i)) },
                onOpenReview = { nav.navigate(Routes.REVIEW_NOW) },
                onOpenPiano = { nav.navigate(Routes.PIANO) },
            )
        }
        composable(Routes.LESSON) { LessonScreen(onBack = { nav.popBackStack() }, onOpenDrill = { id, i -> nav.navigate(Routes.drill(id, i)) }) }
        composable(Routes.DRILL) { DrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.REVIEW) { DrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
