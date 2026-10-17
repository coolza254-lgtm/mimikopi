package com.id.mimikopi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.desktop.port.chords.ChordHomeScreen
import com.id.mimikopi.desktop.port.chords.ChordHomeViewModel
import com.id.mimikopi.desktop.port.components.MidiNoticeBar
import com.id.mimikopi.desktop.port.ear.ToneEditScreen
import com.id.mimikopi.desktop.port.ear.ToneEditViewModel
import com.id.mimikopi.desktop.port.ear.ToneHomeScreen
import com.id.mimikopi.desktop.port.ear.ToneHomeViewModel
import com.id.mimikopi.desktop.port.ear.ToneSessionScreen
import com.id.mimikopi.desktop.port.ear.ToneSessionViewModel
import com.id.mimikopi.desktop.port.lesson.LessonScreen
import com.id.mimikopi.desktop.port.lesson.LessonViewModel
import com.id.mimikopi.desktop.port.metronome.MetronomeScreen
import com.id.mimikopi.desktop.port.metronome.MetronomeViewModel
import com.id.mimikopi.desktop.port.piano.FreePianoScreen
import com.id.mimikopi.desktop.port.piano.FreePianoViewModel
import com.id.mimikopi.desktop.port.practice.DrillScreen
import com.id.mimikopi.desktop.port.practice.DrillViewModel
import com.id.mimikopi.desktop.port.reader.PieceListScreen
import com.id.mimikopi.desktop.port.reader.PieceListViewModel
import com.id.mimikopi.desktop.port.reader.ReaderScreen
import com.id.mimikopi.desktop.port.reader.ReaderViewModel
import com.id.mimikopi.desktop.port.rememberViewModel
import com.id.mimikopi.desktop.port.skill.Skill
import com.id.mimikopi.desktop.port.skill.SkillScreen
import com.id.mimikopi.desktop.port.skill.SkillViewModel
import com.id.mimikopi.desktop.port.skills.SkillDetailScreen
import com.id.mimikopi.desktop.port.skills.SkillsScreen
import com.id.mimikopi.desktop.port.skills.SkillsViewModel
import com.id.mimikopi.desktop.port.songs.SongsScreen
import com.id.mimikopi.desktop.port.songs.SongsViewModel
import com.id.mimikopi.desktop.port.stringResource
import com.id.mimikopi.desktop.port.today.Open
import com.id.mimikopi.desktop.port.today.TodayScreen
import com.id.mimikopi.desktop.port.today.TodayViewModel
import com.id.mimikopi.desktop.ui.MimikopiTheme
import com.id.mimikopi.desktop.ui.SettingsScreen
import com.id.mimikopi.desktop.ui.Tokens
import com.id.mimikopi.desktop.ui.midiReady

/** Where the window is. */
sealed interface Screen {
    // The four tabs, as on the phone.
    data object Today : Screen
    data object Skills : Screen
    data object Piano : Screen
    data object Songs : Screen
    // Pages opened from them.
    data class SkillDetail(val skill: SkillId) : Screen
    data object Ladder : Screen
    data object Ear : Screen
    data object Chords : Screen
    data class ChordDrill(val practice: String) : Screen
    data class Check(val key: String, val run: CheckRun) : Screen
    data class NewReading(val level: Int) : Screen
    data object Settings : Screen
    data object Metronome : Screen
    data class ToneSession(val levelId: String) : Screen
    data class ToneEdit(val id: String?) : Screen
    data class LessonPage(val lessonId: String) : Screen
    data class Drill(val lessonId: String, val index: Int, val via: String? = null) : Screen
    data object Review : Screen
    data class Pieces(val lessonId: String, val index: Int) : Screen
    data class Reader(val pieceId: String, val hand: String?) : Screen
    data class ScoreDrill(val lessonId: String, val index: Int, val via: String? = null) : Screen
}

private val TABS: List<Screen> = listOf(Screen.Today, Screen.Skills, Screen.Piano, Screen.Songs)

/** The page that runs a step's drill, by its type (as on Android). */
private fun lessonDrill(lessonId: String, index: Int, via: String? = null): Screen? {
    val lesson = Services.learning.lesson(lessonId) ?: return null
    val d = lesson.drills.getOrNull(index) ?: return null
    return when {
        d.type == ExerciseType.E_PIECE && d.pieces.size > 1 -> Screen.Pieces(lessonId, index)
        // A piece the step counts by the piece rule: practised in the reader with its own progress.
        d.type == ExerciseType.E_PIECE && lesson.pass.piecesPassed != null -> Screen.Reader(d.pieces.first(), d.hand)
        d.type.isScore -> Screen.ScoreDrill(lessonId, index, via)
        else -> Screen.Drill(lessonId, index, via)
    }
}

/** Where a row or button of the Today and Skills pages leads: a page on top, or another tab. */
private fun target(o: Open): Pair<Screen, Boolean>? = when (o) {
    is Open.LessonDrill -> lessonDrill(o.lessonId, o.index, o.via)?.let { it to false }
    Open.Review -> Screen.Review to false
    Open.Ladder -> Screen.Ladder to false
    is Open.Sight -> Screen.NewReading(o.level) to false
    is Open.Ear -> Screen.ToneSession(o.levelId) to false
    Open.EarHome -> Screen.Ear to false
    Open.ChordsToday -> Screen.ChordDrill("chords-today") to false
    Open.ChordHome -> Screen.Chords to false
    is Open.Check -> Screen.Check(o.key, o.run) to false
    Open.Songs -> Screen.Songs to true
    is Open.Skill -> Screen.SkillDetail(o.skill) to false
    Open.Skills -> Screen.Skills to true
    Open.Piano -> Screen.Piano to true
    Open.Metronome -> Screen.Metronome to false
}

/** Settings is laid out as one scrolling column; the ported pages manage their own height. */
private val Screen.scrollsInPage: Boolean
    get() = this is Screen.Settings

/** The window icon (the same art as the Android launcher icon). */
private val appIcon by lazy {
    BitmapPainter(javax.imageio.ImageIO.read(Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")).toComposeImageBitmap())
}

fun main() {
    Services.start()
    application {
        val state = rememberWindowState(size = DpSize(1180.dp, 820.dp))
        Window(onCloseRequest = { Services.midi.stop(); exitApplication() }, title = "ミミコピ", state = state, icon = appIcon) {
            val mode by Services.themeMode.collectAsState()
            val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
            MimikopiTheme(dark) { App() }
        }
    }
}

/** The window: a left rail (four tabs, MIDI state, settings) and the page. */
@Composable
private fun App() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Today)) }
    val screen = stack.last()
    fun go(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun tab(s: Screen) { stack = listOf(s) }
    val openMidi = { go(Screen.Settings) }
    val open: (Open) -> Unit = { o -> target(o)?.let { (s, isTab) -> if (isTab) tab(s) else go(s) } }
    val labels = listOf(R.string.tab_today, R.string.tab_skills, R.string.tab_piano, R.string.tab_songs)

    Row(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
        Column(Modifier.width(220.dp).fillMaxHeight().padding(start = 28.dp, top = 32.dp, bottom = 28.dp)) {
            Text("ミミコピ", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Tokens.Palette.Ink)
            Spacer(Modifier.height(36.dp))
            TABS.forEachIndexed { i, t -> RailItem(stringResource(labels[i]), stack.first() == t) { tab(t) } }
            Spacer(Modifier.weight(1f))
            val ready = midiReady()
            Row(Modifier.heightIn(min = 40.dp).clickable(onClick = openMidi), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (ready) Tokens.Palette.Positive else Tokens.Palette.Faint, CircleShape))
                Text(if (ready) "  MIDI" else "  ไม่มี MIDI", fontSize = 13.sp, color = if (ready) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
            RailItem("ตั้งค่า", screen == Screen.Settings) { go(Screen.Settings) }
            val update by Services.updater.state.collectAsState()
            if (update is com.id.mimikopi.desktop.update.DesktopUpdater.State.Available) {
                Text("มีรุ่นใหม่ · ดูในตั้งค่า", fontSize = 12.sp, color = Tokens.Palette.Ink,
                    modifier = Modifier.padding(start = 14.dp).clickable { go(Screen.Settings) })
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Tokens.Palette.Hair))
        // Clipped: a score strip draws past its edges and must not cover the rail.
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
        if (!screen.scrollsInPage) Box(Modifier.fillMaxSize()) {
            PortedPage(screen, go = ::go, back = ::back, open = open, openMidi = openMidi)
        } else Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(horizontal = 48.dp, vertical = 40.dp)) {
                if (screen == Screen.Settings) SettingsScreen(onBack = ::back, onOpenMetronome = { go(Screen.Metronome) })
            }
        }
        // Plugged in or pulled out: told on whatever page is open.
        MidiNoticeBar(Services.midi.notice, onOpen = { if (screen != Screen.Settings) openMidi() }, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Every page but settings: the Android screens, with their view models made here instead of by navigation. */
@Composable
private fun PortedPage(screen: Screen, go: (Screen) -> Unit, back: () -> Unit, open: (Open) -> Unit, openMidi: () -> Unit) {
    val app = MimikopiApp
    fun args(vararg pairs: Pair<String, String?>) = SavedStateHandle(mapOf(*pairs))
    val openDrill: (String, Int) -> Unit = { id, i -> lessonDrill(id, i)?.let(go) }
    val openSettings = { go(Screen.Settings) }
    when (screen) {
        Screen.Today -> TodayScreen(onOpen = open, onOpenMidi = openMidi, onOpenSettings = openSettings, vm = rememberViewModel(screen) { TodayViewModel(app) })
        Screen.Skills -> SkillsScreen(onOpen = open, onOpenMidi = openMidi, onOpenSettings = openSettings, vm = rememberViewModel(screen) { SkillsViewModel(app) })
        is Screen.SkillDetail -> SkillDetailScreen(screen.skill, onBack = back, onOpen = open, vm = rememberViewModel(screen) { SkillsViewModel(app) })
        Screen.Piano -> FreePianoScreen(onOpenMidi = openMidi, onOpenSettings = openSettings, vm = rememberViewModel(screen) { FreePianoViewModel(app) })
        Screen.Ladder -> SkillScreen(
            skill = Skill.READING,
            onOpenStep = { go(Screen.LessonPage(it)) },
            onOpenDrill = { openDrill(it, 0) },
            onReview = { go(Screen.Review) },
            onOpenMidi = openMidi,
            onOpenSettings = openSettings,
            onBack = back,
            vm = rememberViewModel(screen) { SkillViewModel(app) },
        )
        Screen.Ear -> ToneHomeScreen(
            onOpen = { go(Screen.ToneSession(it)) },
            onEdit = { go(Screen.ToneEdit(it)) },
            onOpenMidi = openMidi,
            onOpenSettings = openSettings,
            onBack = back,
            vm = rememberViewModel(screen) { ToneHomeViewModel(app) },
        )
        is Screen.ToneSession -> ToneSessionScreen(onBack = back, vm = rememberViewModel(screen) { ToneSessionViewModel(app, args("levelId" to screen.levelId)) })
        is Screen.ToneEdit -> ToneEditScreen(onBack = back, vm = rememberViewModel(screen) { ToneEditViewModel(app, args("id" to screen.id)) })
        Screen.Metronome -> MetronomeScreen(onBack = back, vm = rememberViewModel(screen) { MetronomeViewModel(app) })
        Screen.Songs -> SongsScreen(
            onPlay = { id, hand -> go(Screen.Reader(id, hand)) },
            onOpenMidi = openMidi,
            onOpenSettings = openSettings,
            vm = rememberViewModel(screen) { SongsViewModel(app) },
        )
        is Screen.LessonPage -> LessonScreen(onBack = back, onOpenDrill = openDrill,
            vm = rememberViewModel(screen) { LessonViewModel(app, args("lessonId" to screen.lessonId)) })
        is Screen.Drill -> DrillScreen(onBack = back,
            vm = rememberViewModel(screen) { DrillViewModel(app, args("lessonId" to screen.lessonId, "drill" to screen.index.toString(), "via" to screen.via)) })
        Screen.Review -> DrillScreen(onBack = back, vm = rememberViewModel(screen) { DrillViewModel(app, args("review" to "true")) })
        Screen.Chords -> ChordHomeScreen(onStart = { go(Screen.ChordDrill("chords")) }, onOpenMidi = openMidi, onOpenSettings = openSettings, onBack = back,
            vm = rememberViewModel(screen) { ChordHomeViewModel(app) })
        is Screen.ChordDrill -> DrillScreen(onBack = back, vm = rememberViewModel(screen) { DrillViewModel(app, args("practice" to screen.practice)) })
        is Screen.Check -> when (screen.run) {
            is CheckRun.DrillRun -> DrillScreen(onBack = back, vm = rememberViewModel(screen) { DrillViewModel(app, args("check" to screen.key)) })
            is CheckRun.ScoreRun -> ReaderScreen(onBack = back, vm = rememberViewModel(screen) { ReaderViewModel(app, args("check" to screen.key)) })
            is CheckRun.EarRun -> ToneSessionScreen(onBack = back, vm = rememberViewModel(screen) { ToneSessionViewModel(app, args("check" to screen.key)) })
        }
        is Screen.NewReading -> ReaderScreen(onBack = back, vm = rememberViewModel(screen) { ReaderViewModel(app, args("newread" to screen.level.toString())) })
        is Screen.Pieces -> PieceListScreen(onBack = back, onOpenPiece = { id, hand -> go(Screen.Reader(id, hand)) },
            vm = rememberViewModel(screen) { PieceListViewModel(app, args("lesson" to screen.lessonId, "drill" to screen.index.toString())) })
        is Screen.Reader -> ReaderScreen(onBack = back,
            vm = rememberViewModel(screen) { ReaderViewModel(app, args("pieceId" to screen.pieceId, "hand" to screen.hand)) })
        is Screen.ScoreDrill -> ReaderScreen(onBack = back,
            vm = rememberViewModel(screen) { ReaderViewModel(app, args("lessonId" to screen.lessonId, "drill" to screen.index.toString(), "via" to screen.via)) })
        Screen.Settings -> Unit
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(4.dp).background(if (selected) Tokens.Palette.Ink else Tokens.Palette.Paper, CircleShape))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
    }
}
