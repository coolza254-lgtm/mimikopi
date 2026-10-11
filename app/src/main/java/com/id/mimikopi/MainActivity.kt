package com.id.mimikopi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.ui.progress.ProgressScreen
import com.id.mimikopi.ui.ear.EarDrillScreen
import com.id.mimikopi.ui.ear.EarScreen
import com.id.mimikopi.ui.editor.EditorScreen
import com.id.mimikopi.ui.editor.LibraryScreen
import com.id.mimikopi.ui.about.AboutScreen
import com.id.mimikopi.ui.about.CreditsScreen
import com.id.mimikopi.ui.piano.PianoScreen
import com.id.mimikopi.ui.metronome.MetronomeScreen
import com.id.mimikopi.ui.midi.MidiScreen
import com.id.mimikopi.ui.lesson.LessonScreen
import com.id.mimikopi.ui.practice.DrillScreen
import com.id.mimikopi.ui.today.TodayScreen
import com.id.mimikopi.ui.reader.PieceListScreen
import com.id.mimikopi.ui.reader.ReaderScreen
import com.id.mimikopi.ui.diag.DiagnosticsScreen
import com.id.mimikopi.ui.home.HomeScreen
import com.id.mimikopi.ui.settings.SettingsScreen
import com.id.mimikopi.ui.theme.MimikopiTheme
import com.id.mimikopi.pieces.PieceRepository
import com.id.mimikopi.ui.knowledge.ArticleScreen
import com.id.mimikopi.ui.knowledge.KnowledgeScreen
import com.id.mimikopi.ui.songs.SongListScreen
import com.id.mimikopi.ui.songs.SongScreen
import kotlinx.coroutines.launch

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
    const val PIECES = "pieces?lesson={lesson}&drill={drill}"
    const val PIECES_ALL = "pieces"
    fun piecesFor(lessonId: String, drill: Int) = "pieces?lesson=$lessonId&drill=$drill"
    const val READER = "reader/{pieceId}?hand={hand}"
    fun reader(id: String, hand: String?) = if (hand == null) "reader/$id" else "reader/$id?hand=$hand"
    const val SCORE_DRILL = "scoredrill/{lessonId}/{drill}"
    const val NEW_READING = "newread/{newread}"
    const val BENCH_READ = "benchread/{bench}"
    const val BENCH_DRILL = "benchdrill/{benchmark}"
    const val PROGRESS = "progress"
    const val EAR = "ear"
    const val LIBRARY = "library"
    const val EDITOR = "editor/{scoreId}"
    const val EAR_DRILL = "eardrill/{level}?bench={bench}"
    const val LESSON_EAR = "lessonear/{lessonId}/{drill}"
    const val SONGS = "songs"
    const val SONG = "song/{songId}"
    const val KNOWLEDGE = "knowledge"
    const val ARTICLE = "article/{articleId}"
    fun lesson(id: String) = "lesson/$id"
    fun drill(id: String, index: Int) = "drill/$id/$index"
    const val REVIEW_NOW = "review/true"
}

/** Opens a lesson drill in the runner that fits its type. */
private fun openLessonDrill(nav: NavController, app: MimikopiApp, lessonId: String, index: Int) {
    val lesson = app.learning.lesson(lessonId) ?: return
    val d = lesson.drills.getOrNull(index) ?: return
    val byPiecePass = lesson.pass.piecesPassed != null || lesson.pass.songsCompleted != null
    when {
        d.type == ExerciseType.E_PIECE && d.pieces == listOf(PieceRepository.SONG) -> nav.navigate(Routes.SONGS)
        d.type == ExerciseType.E_PIECE && d.pieces == listOf(PieceRepository.CAPSTONE) -> openCapstone(nav, app)
        d.type == ExerciseType.E_PIECE && d.pieces.size > 1 -> nav.navigate(Routes.piecesFor(lessonId, index))
        // A piece the lesson counts by the piece rule: practise it in the reader with its own progress.
        d.type == ExerciseType.E_PIECE && byPiecePass -> nav.navigate(Routes.reader(d.pieces.first(), d.hand))
        d.type == ExerciseType.E_EAR -> nav.navigate("lessonear/$lessonId/$index")
        d.type.isScore -> nav.navigate("scoredrill/$lessonId/$index")
        else -> nav.navigate(Routes.drill(lessonId, index))
    }
}

/** Capstone A: the editor while the melody is still being written, then the reader. */
private fun openCapstone(nav: NavController, app: MimikopiApp) {
    app.appScope.launch {
        val id = app.pieces.createCapstone()
        val melody = app.pieces.userScore(id)?.events?.count { it.hand == com.id.mimikopi.core.score.Hand.RIGHT && !it.isRest } ?: 0
        nav.navigate(if (melody >= 16) Routes.reader(id, "BOTH") else "editor/$id")
    }
}

/** Lesson "try with the app" steps that open another part of the app. */
private fun runAppAction(nav: NavController, app: MimikopiApp, action: String) {
    when (action) {
        "template:capstone" -> app.appScope.launch { nav.navigate("editor/${app.pieces.createCapstone()}") }
        "open:songs" -> nav.navigate(Routes.SONGS)
        "open:knowledge" -> nav.navigate(Routes.KNOWLEDGE)
    }
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as MimikopiApp
    val openDrill: (String, Int) -> Unit = { id, i -> openLessonDrill(nav, app, id, i) }
    val optional = { name: String -> navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null } }
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
                onOpenReader = { nav.navigate(Routes.PIECES_ALL) },
                onOpenProgress = { nav.navigate(Routes.PROGRESS) },
                onOpenEar = { nav.navigate(Routes.EAR) },
                onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                onOpenSongs = { nav.navigate(Routes.SONGS) },
                onOpenKnowledge = { nav.navigate(Routes.KNOWLEDGE) },
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
                onOpenDrill = openDrill,
                onOpenReview = { nav.navigate(Routes.REVIEW_NOW) },
                onOpenPiano = { nav.navigate(Routes.PIANO) },
                onOpenPieces = { nav.navigate(Routes.PIECES_ALL) },
                onOpenNewReading = { level -> nav.navigate("newread/$level") },
                onOpenEar = { nav.navigate("eardrill/mix") },
            )
        }
        composable(Routes.LESSON) { LessonScreen(onBack = { nav.popBackStack() }, onOpenDrill = openDrill, onAppAction = { runAppAction(nav, app, it) }) }
        composable(Routes.DRILL) { DrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.REVIEW) { DrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PIECES, arguments = listOf(optional("lesson"), optional("drill"))) {
            PieceListScreen(onBack = { nav.popBackStack() }, onOpenPiece = { id, hand -> nav.navigate(Routes.reader(id, hand)) })
        }
        composable(Routes.READER, arguments = listOf(optional("hand"))) { ReaderScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SCORE_DRILL) { ReaderScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.NEW_READING) { ReaderScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.BENCH_READ) { ReaderScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.BENCH_DRILL) { DrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PROGRESS) {
            ProgressScreen(
                onBack = { nav.popBackStack() },
                onBenchNotes = { nav.navigate("benchdrill/noteid") },
                onBenchPulse = { nav.navigate("benchdrill/pulse") },
                onBenchReading = { nav.navigate("benchread/1") },
                onBenchEar = { nav.navigate("eardrill/bench?bench=true") },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate("editor/$it") }, onPlay = { nav.navigate(Routes.reader(it, null)) })
        }
        composable(Routes.EDITOR) { EditorScreen(onBack = { nav.popBackStack() }, onPlayAlong = { nav.navigate(Routes.reader(it, null)) }) }
        composable(Routes.EAR) {
            EarScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("eardrill/$it") }, onTranscribe = { nav.navigate(Routes.PIECES_ALL) })
        }
        composable(Routes.EAR_DRILL, arguments = listOf(optional("bench"))) { EarDrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LESSON_EAR) { EarDrillScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SONGS) {
            SongListScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("song/$it") })
        }
        composable(Routes.SONG) {
            SongScreen(
                onBack = { nav.popBackStack() },
                onOpenLesson = { nav.navigate(Routes.lesson(it)) },
                onEditScore = { nav.navigate("editor/$it") },
                onPlayScore = { nav.navigate(Routes.reader(it, null)) },
            )
        }
        composable(Routes.KNOWLEDGE) { KnowledgeScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("article/$it") }) }
        composable(Routes.ARTICLE) { ArticleScreen(onBack = { nav.popBackStack() }, onOpenLesson = { nav.navigate(Routes.lesson(it)) }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
