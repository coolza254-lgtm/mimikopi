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
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavBackStackEntry
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.ui.about.AboutScreen
import com.id.mimikopi.ui.about.CreditsScreen
import com.id.mimikopi.ui.chords.ChordHomeScreen
import com.id.mimikopi.ui.components.BottomNav
import com.id.mimikopi.ui.components.MidiNoticeBar
import com.id.mimikopi.ui.diag.DiagnosticsScreen
import com.id.mimikopi.ui.ear.EarHomeScreen
import com.id.mimikopi.ui.ear.EarSessionScreen
import com.id.mimikopi.ui.lesson.LessonScreen
import com.id.mimikopi.ui.metronome.MetronomeScreen
import com.id.mimikopi.ui.midi.MidiScreen
import com.id.mimikopi.ui.practice.DrillScreen
import com.id.mimikopi.ui.reader.PieceListScreen
import com.id.mimikopi.ui.reader.ReaderScreen
import com.id.mimikopi.ui.settings.SettingsScreen
import com.id.mimikopi.ui.skill.Skill
import com.id.mimikopi.ui.skill.SkillScreen
import com.id.mimikopi.ui.piano.FreePianoScreen
import com.id.mimikopi.ui.piano.PianoSoundSetup
import com.id.mimikopi.ui.skills.SkillDetailScreen
import com.id.mimikopi.ui.skills.SkillsScreen
import com.id.mimikopi.ui.songs.SongsScreen
import com.id.mimikopi.ui.today.Open
import com.id.mimikopi.ui.today.TodayScreen
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.ui.theme.MimikopiTheme
import com.id.mimikopi.ui.theme.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Black UI: light status and navigation bar icons on a transparent bar.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as MimikopiApp
        setContent {
            val mode by app.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Status and navigation bar icons follow the look (light icons on black, dark icons on white).
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MimikopiTheme(dark) { AppNavigation() }
        }
    }

    /** Coming back to the app: re-check which MIDI instruments are really there. */
    override fun onResume() {
        super.onResume()
        (application as MimikopiApp).midiInput.resync()
    }
}

private object Routes {
    // The four tabs.
    const val TODAY = "today"
    const val SKILLS = "skills"
    const val PIANO = "piano"
    const val SONGS = "songs"
    val TABS = setOf(TODAY, SKILLS, PIANO, SONGS)

    // Pages opened from the tabs.
    const val SKILL = "skill/{skill}"
    const val LADDER = "ladder"
    const val READING = "reading"
    const val NOTE_READING = "reading/drill/{practice}"
    const val EAR = "ear"
    const val CHORDS = "chords"
    const val CHORD_DRILL = "chorddrill/{practice}"
    const val CHECK_DRILL = "check/drill?check={check}"
    const val CHECK_SCORE = "check/score?check={check}"
    const val CHECK_EAR = "check/ear?check={check}"
    const val NEW_READING = "newread/{newread}?via={via}"

    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
    const val CREDITS = "credits"
    const val METRONOME = "metronome"
    const val MIDI = "midi"
    const val PROGRESS = "progress"
    const val GOAL = "goal"
    const val EXAMS = "exams"
    const val EXAM_GRADE = "exams/grade/{grade}"
    const val EXAM_RESULT = "exams/result/{id}"
    const val EXAM_PLAY = "exams/play/{exam}/{part}"
    const val EXAM_AURAL = "exams/aural/{exam}"
    const val LESSON = "lesson/{lessonId}"
    const val DRILL = "drill/{lessonId}/{drill}?via={via}"
    const val REVIEW = "review/{review}"
    const val PIECES = "pieces?lesson={lesson}&drill={drill}"
    fun piecesFor(lessonId: String, drill: Int) = "pieces?lesson=$lessonId&drill=$drill"
    const val READER = "reader/{pieceId}?hand={hand}&target={target}&originLesson={originLesson}&originDrill={originDrill}"
    fun reader(id: String, hand: String?, lesson: String? = null, drill: Int? = null): String {
        val query = listOfNotNull(hand?.let { "hand=$it" }, lesson?.let { "originLesson=$it" }, drill?.let { "originDrill=$it" })
        return "reader/$id" + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }
    fun heroicTarget(id: String): String {
        val t = com.id.mimikopi.core.playalong.HeroicPlan.target(id) ?: error("Unknown study target")
        return "reader/${t.piece}?target=${t.id}"
    }
    const val SCORE_DRILL = "scoredrill/{lessonId}/{drill}?via={via}"
    const val EAR_SESSION = "ear/session?focus={focus}"
    fun earSession(focus: String?) = if (focus == null) "ear/session" else "ear/session?focus=$focus"
    fun lesson(id: String) = "lesson/$id"
    fun withVia(route: String, via: String?) = if (via == null) route else "$route?via=$via"
    const val REVIEW_NOW = "review/true"
}

/** A move from one tab to another (not a page opened on top). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    initialState.destination.route in Routes.TABS && targetState.destination.route in Routes.TABS

/** Opens a lesson drill in the runner that fits its type ([via]: the Today plan part it counts for). */
private fun openLessonDrill(nav: NavController, app: MimikopiApp, lessonId: String, index: Int, via: String? = null) {
    val lesson = app.learning.lesson(lessonId) ?: return
    val d = lesson.drills.getOrNull(index) ?: return
    when {
        d.type == ExerciseType.E_PIECE && d.pieces.size > 1 -> nav.navigate(Routes.piecesFor(lessonId, index))
        // A piece the lesson counts by the piece rule: practise it in the reader with its own progress.
        d.type == ExerciseType.E_PIECE && lesson.pass.piecesPassed != null -> nav.navigate(Routes.reader(d.pieces.first(), d.hand, lessonId, index))
        d.type.isScore -> nav.navigate(Routes.withVia("scoredrill/$lessonId/$index", via))
        else -> nav.navigate(Routes.withVia("drill/$lessonId/$index", via))
    }
}

/** Where a row or button of the Today and Skills pages leads. */
private fun openPage(nav: NavController, app: MimikopiApp, o: Open, selectTab: (String) -> Unit) {
    when (o) {
        is Open.LessonDrill -> openLessonDrill(nav, app, o.lessonId, o.index, o.via)
        Open.Reading -> nav.navigate(Routes.READING)
        Open.Review -> nav.navigate(Routes.REVIEW_NOW)
        Open.Ladder -> nav.navigate(Routes.LADDER)
        is Open.Sight -> nav.navigate("newread/${o.level}")
        is Open.Ear -> nav.navigate(Routes.earSession(o.focus))
        Open.EarHome -> nav.navigate(Routes.EAR)
        Open.ChordsToday -> nav.navigate("chorddrill/chords-today")
        Open.ChordHome -> nav.navigate(Routes.CHORDS)
        is Open.Check -> {
            val key = android.net.Uri.encode(o.key)
            nav.navigate(
                when (o.run) {
                    is CheckRun.DrillRun -> "check/drill?check=$key"
                    is CheckRun.ScoreRun -> "check/score?check=$key"
                    is CheckRun.EarRun -> "check/ear?check=$key"
                },
            )
        }
        Open.PrivateSongs -> nav.navigate("private-songs")
        Open.Songs -> selectTab(Routes.SONGS)
        is Open.Skill -> nav.navigate("skill/${o.skill.name}")
        Open.Skills -> selectTab(Routes.SKILLS)
        Open.Piano -> selectTab(Routes.PIANO)
        Open.Metronome -> nav.navigate(Routes.METRONOME)
        Open.Progress -> nav.navigate(Routes.PROGRESS)
        Open.Goal -> nav.navigate(Routes.GOAL)
        is Open.Piece -> nav.navigate(Routes.reader(o.id, null))
        Open.Exams -> nav.navigate(Routes.EXAMS)
        is Open.ExamGrade -> nav.navigate("exams/grade/${o.grade}")
        is Open.ExamResult -> nav.navigate("exams/result/${o.id}")
        is Open.ExamTake -> nav.navigate(if (o.part == com.id.mimikopi.core.exam.ExamPart.AURAL.name) "exams/aural/${o.sitting}" else "exams/play/${o.sitting}/${o.part}")
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
    val showBar = route in Routes.TABS
    val selectTab: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(Routes.TODAY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val open: (Open) -> Unit = { openPage(nav, app, it, selectTab) }
    val openMidi = { nav.navigate(Routes.MIDI) }
    val openSettings = { nav.navigate(Routes.SETTINGS) }
    Scaffold(
        containerColor = com.id.mimikopi.ui.theme.Tokens.Palette.Paper,
        // Playing screens own their insets, including portrait and fixed exercise/exam routes.
        contentWindowInsets = if (route in setOf(Routes.READER, Routes.SCORE_DRILL, Routes.NEW_READING,
                Routes.CHECK_SCORE, Routes.EXAM_PLAY, Routes.NOTE_READING, Routes.DRILL, Routes.REVIEW,
                Routes.CHECK_DRILL, Routes.CHORD_DRILL)) WindowInsets(0, 0, 0, 0)
            else ScaffoldDefaults.contentWindowInsets,
        bottomBar = { if (showBar) BottomNav(route, selectTab) },
    ) { inner ->
    Box(Modifier.fillMaxSize()) {
    // Screens arrive with a short fade and a small slide, and leave with a fade (quiet, not flashy).
    NavHost(
        navController = nav,
        startDestination = Routes.TODAY,
        modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        // Between tabs only a quick cross-fade: tabs sit side by side, not on top of each other.
        enterTransition = { if (betweenTabs()) fadeIn(tween(160)) else fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 14 } },
        exitTransition = { fadeOut(tween(if (betweenTabs()) 120 else 180)) },
        popEnterTransition = { if (betweenTabs()) fadeIn(tween(160)) else fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 14 } },
        popExitTransition = { if (betweenTabs()) fadeOut(tween(120)) else fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 14 } },
    ) {
        composable(Routes.TODAY) { TodayScreen(onOpen = open, onOpenMidi = openMidi, onOpenSettings = openSettings) }
        composable(Routes.SKILLS) { SkillsScreen(onOpen = open, onOpenMidi = openMidi, onOpenSettings = openSettings) }
        composable(Routes.PIANO) {
            FreePianoScreen(onOpenMidi = openMidi, onOpenSettings = openSettings, soundSetup = { onReady -> PianoSoundSetup(onReady) })
        }
        composable(Routes.SONGS) {
            SongsScreen(onPlay = { id, hand -> nav.navigate(Routes.reader(id, hand)) }, onOpenMidi = openMidi, onOpenSettings = openSettings, onOpenLibrary = { nav.navigate("pieces") }, onOpenPrivate = { nav.navigate("private-songs") }, onTarget = { nav.navigate(Routes.heroicTarget(it)) })
        }
        composable("private-songs") { com.id.mimikopi.ui.recall.RecallScreen(onBack = back, onMidi = openMidi, onSound = { nav.navigate(Routes.PIANO) }) }
        composable(Routes.SKILL) { e ->
            val skill = e.arguments?.getString("skill")?.let { runCatching { SkillId.valueOf(it) }.getOrNull() } ?: SkillId.KEYS
            SkillDetailScreen(skill, onBack = back, onOpen = open)
        }
        composable(Routes.LADDER) {
            SkillScreen(
                skill = Skill.READING,
                onOpenStep = { nav.navigate(Routes.lesson(it)) },
                onOpenDrill = { openDrill(it, 0) },
                onReview = { nav.navigate(Routes.REVIEW_NOW) },
                onOpenMidi = openMidi,
                onOpenSettings = openSettings,
                onBack = back,
            )
        }
        composable(Routes.EAR) {
            EarHomeScreen(
                onRound = { kind -> nav.navigate(Routes.earSession(kind?.name)) },
                onTest = { nav.navigate("skill/${SkillId.EAR.name}") },
                onOpenMidi = openMidi,
                onOpenSettings = openSettings,
                onBack = back,
            )
        }
        composable(Routes.EAR_SESSION, arguments = listOf(optional("focus"))) { EarSessionScreen(onBack = back) }
        composable(Routes.CHECK_EAR, arguments = listOf(optional("check"))) { EarSessionScreen(onBack = back) }
        composable(Routes.READING) {
            com.id.mimikopi.ui.reading.ReadingHomeScreen(back,
                onSingle = { nav.navigate("reading/drill/notes") },
                onMelody = { level -> nav.navigate("newread/$level") }, onMidi = openMidi)
        }
        composable(Routes.NOTE_READING) { DrillScreen(onBack = back) }
        composable(Routes.CHORDS) { ChordHomeScreen(onStart = { nav.navigate("chorddrill/chords") }, onOpenMidi = openMidi, onOpenSettings = openSettings, onBack = back) }
        composable(Routes.CHORD_DRILL) { DrillScreen(onBack = back) }
        composable(Routes.CHECK_DRILL, arguments = listOf(optional("check"))) { DrillScreen(onBack = back) }
        composable(Routes.CHECK_SCORE, arguments = listOf(optional("check"))) { ReaderScreen(onBack = back, onOpenMidi = openMidi) }
        composable(Routes.NEW_READING, arguments = listOf(optional("via"))) { ReaderScreen(onBack = back, onOpenMidi = openMidi) }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = back) }
        composable(Routes.ABOUT) { AboutScreen(onBack = back, onOpenCredits = { nav.navigate(Routes.CREDITS) }) }
        composable(Routes.CREDITS) { CreditsScreen(onBack = back) }
        composable(Routes.METRONOME) { MetronomeScreen(onBack = back) }
        composable(Routes.MIDI) { MidiScreen(onBack = back) }
        composable(Routes.PROGRESS) { com.id.mimikopi.ui.progress.ProgressScreen(onBack = back, onOpen = open) }
        composable(Routes.GOAL) { com.id.mimikopi.ui.progress.GoalScreen(onBack = back, onTarget = { nav.navigate(Routes.heroicTarget(it)) }, onFree = { id, hand -> nav.navigate(Routes.reader(id, hand)) }, onSource = { nav.navigate("pieces") }) }
        composable(Routes.EXAMS) { com.id.mimikopi.ui.exam.ExamHomeScreen(onBack = back, onOpen = open) }
        composable(Routes.EXAM_GRADE) { com.id.mimikopi.ui.exam.ExamGradeScreen(onBack = back, onOpen = open) }
        composable(Routes.EXAM_RESULT) { com.id.mimikopi.ui.exam.ExamResultScreen(onBack = back, onOpen = open) }
        composable(Routes.EXAM_PLAY) { ReaderScreen(onBack = back, onOpenMidi = openMidi) }
        composable(Routes.EXAM_AURAL) { EarSessionScreen(onBack = back) }
        composable(Routes.LESSON) { LessonScreen(onBack = back, onOpenDrill = openDrill) }
        composable(Routes.DRILL, arguments = listOf(optional("via"))) { DrillScreen(onBack = back) }
        composable(Routes.REVIEW) { DrillScreen(onBack = back) }
        composable(Routes.PIECES, arguments = listOf(optional("lesson"), optional("drill"))) {
            PieceListScreen(onBack = back, onOpenPiece = { id, hand, lesson, drill -> nav.navigate(Routes.reader(id, hand, lesson, drill)) })
        }
        composable(Routes.READER, arguments = listOf(optional("hand"), optional("target"), optional("originLesson"), optional("originDrill"))) { ReaderScreen(onBack = back, onOpenMidi = openMidi) }
        composable(Routes.SCORE_DRILL, arguments = listOf(optional("via"))) { ReaderScreen(onBack = back, onOpenMidi = openMidi) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onOpenMidi = openMidi,
                onOpenMetronome = { nav.navigate(Routes.METRONOME) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
            )
        }
    }
    // Plugged in, pulled out, Bluetooth connected or lost: told on whatever screen is open.
    MidiNoticeBar(app.midiInput.notice, onOpen = { if (route != Routes.MIDI) openMidi() }, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(), pianoName = app.pianoNames::display)
    }
    }
}
