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
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.desktop.port.lesson.LessonScreen
import com.id.mimikopi.desktop.port.metronome.MetronomeScreen
import com.id.mimikopi.desktop.port.metronome.MetronomeViewModel
import com.id.mimikopi.desktop.port.lesson.LessonViewModel
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
import com.id.mimikopi.desktop.port.songs.SongsScreen
import com.id.mimikopi.desktop.port.songs.SongsViewModel
import com.id.mimikopi.desktop.port.stringResource
import com.id.mimikopi.desktop.ui.MimikopiTheme
import com.id.mimikopi.desktop.ui.SettingsScreen
import com.id.mimikopi.desktop.ui.Tokens
import com.id.mimikopi.desktop.port.ear.ToneEditScreen
import com.id.mimikopi.desktop.port.ear.ToneEditViewModel
import com.id.mimikopi.desktop.port.ear.ToneHomeScreen
import com.id.mimikopi.desktop.port.ear.ToneHomeViewModel
import com.id.mimikopi.desktop.port.ear.ToneSessionScreen
import com.id.mimikopi.desktop.port.ear.ToneSessionViewModel
import com.id.mimikopi.desktop.ui.midiReady
import com.id.mimikopi.desktop.port.components.MidiNoticeBar
import com.id.mimikopi.desktop.ui.sections.SectionEditScreen
import com.id.mimikopi.desktop.ui.sections.SectionPracticeScreen
import com.id.mimikopi.desktop.ui.sections.SectionsHome

/** Where the window is. */
sealed interface Screen {
    data object Reading : Screen
    data object Ear : Screen
    data object Songs : Screen
    data object Sections : Screen
    data object Settings : Screen
    data object Metronome : Screen
    data class ToneSession(val levelId: String) : Screen
    data class ToneEdit(val id: String?) : Screen
    data class SectionEdit(val id: String) : Screen
    data class SectionPractice(val id: String) : Screen
    // Reading and songs (the screens ported from the Android app).
    data class LessonPage(val lessonId: String) : Screen
    data class Drill(val lessonId: String, val index: Int) : Screen
    data object Review : Screen
    data class Pieces(val lessonId: String, val index: Int) : Screen
    data class Reader(val pieceId: String, val hand: String?) : Screen
    data class ScoreDrill(val lessonId: String, val index: Int) : Screen
}

/** The page that runs a step's drill, by its type (as on Android). */
private fun lessonDrill(lessonId: String, index: Int): Screen? {
    val lesson = Services.learning.lesson(lessonId) ?: return null
    val d = lesson.drills.getOrNull(index) ?: return null
    return when {
        d.type == ExerciseType.E_PIECE && d.pieces.size > 1 -> Screen.Pieces(lessonId, index)
        // A piece the step counts by the piece rule: practised in the reader with its own progress.
        d.type == ExerciseType.E_PIECE && lesson.pass.piecesPassed != null -> Screen.Reader(d.pieces.first(), d.hand)
        d.type.isScore -> Screen.ScoreDrill(lessonId, index)
        else -> Screen.Drill(lessonId, index)
    }
}

/** Phase 1 screens are laid out as one scrolling column; the ported ones manage their own height. */
private val Screen.scrollsInPage: Boolean
    get() = this is Screen.Sections || this is Screen.Settings || this is Screen.SectionEdit || this is Screen.SectionPractice

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

/** The window: a left rail (four tabs, MIDI state, settings) and the page, centred and width-limited for reading. */
@Composable
private fun App() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Reading)) }
    val screen = stack.last()
    fun go(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun tab(s: Screen) { stack = listOf(s) }
    val openMidi = { go(Screen.Settings) }

    Row(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
        Column(Modifier.width(220.dp).fillMaxHeight().padding(start = 28.dp, top = 32.dp, bottom = 28.dp)) {
            Text("ミミコピ", fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Tokens.Palette.Ink)
            Spacer(Modifier.height(36.dp))
            RailItem(stringResource(R.string.tab_reading), stack.first() == Screen.Reading) { tab(Screen.Reading) }
            RailItem(stringResource(R.string.tab_ear), stack.first() == Screen.Ear) { tab(Screen.Ear) }
            RailItem(stringResource(R.string.tab_songs), stack.first() == Screen.Songs) { tab(Screen.Songs) }
            RailItem(stringResource(R.string.tab_sections), stack.first() == Screen.Sections) { tab(Screen.Sections) }
            Spacer(Modifier.weight(1f))
            val ready = midiReady()
            Row(Modifier.heightIn(min = 40.dp).clickable(onClick = openMidi), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (ready) Tokens.Palette.Positive else Tokens.Palette.Faint, CircleShape))
                Text(if (ready) "  MIDI" else "  ไม่มี MIDI", fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = if (ready) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
            RailItem("ตั้งค่า", screen == Screen.Settings) { go(Screen.Settings) }
            val update by Services.updater.state.collectAsState()
            if (update is com.id.mimikopi.desktop.update.DesktopUpdater.State.Available) {
                Text("มีรุ่นใหม่ · ดูในตั้งค่า", fontFamily = Tokens.Fonts.mono, fontSize = 11.sp, color = Tokens.Palette.Ink,
                    modifier = Modifier.padding(start = 14.dp).clickable { go(Screen.Settings) })
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Tokens.Palette.Hair))
        Box(Modifier.weight(1f).fillMaxHeight()) {
        if (!screen.scrollsInPage) Box(Modifier.fillMaxSize()) {
            PortedPage(screen, go = ::go, back = ::back, openMidi = openMidi)
        } else Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(horizontal = 48.dp, vertical = 40.dp)) {
                when (screen) {
                    Screen.Sections -> SectionsHome(onPractice = { go(Screen.SectionPractice(it)) }, onEdit = { go(Screen.SectionEdit(it)) }, onOpenMidi = openMidi)
                    Screen.Settings -> SettingsScreen(onBack = ::back, onOpenMetronome = { go(Screen.Metronome) })
                    is Screen.SectionEdit -> SectionEditScreen(screen.id, onBack = ::back, onPractice = { stack = stack.dropLast(1) + Screen.SectionPractice(it) }, onOpenMidi = openMidi)
                    is Screen.SectionPractice -> SectionPracticeScreen(screen.id, onBack = ::back, onEdit = { stack = stack.dropLast(1) + Screen.SectionEdit(it) }, onOpenMidi = openMidi)
                    else -> Unit
                }
            }
        }
        // Plugged in or pulled out: told on whatever page is open.
        MidiNoticeBar(Services.midi.notice, onOpen = { if (screen != Screen.Settings) openMidi() }, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Reading, songs and their pages: the Android screens, with their view models made here instead of by navigation. */
@Composable
private fun PortedPage(screen: Screen, go: (Screen) -> Unit, back: () -> Unit, openMidi: () -> Unit) {
    val app = MimikopiApp
    fun args(vararg pairs: Pair<String, String?>) = SavedStateHandle(mapOf(*pairs))
    val openDrill: (String, Int) -> Unit = { id, i -> lessonDrill(id, i)?.let(go) }
    val openSettings = { go(Screen.Settings) }
    when (screen) {
        Screen.Reading -> SkillScreen(
            skill = Skill.READING,
            onOpenStep = { go(Screen.LessonPage(it)) },
            onOpenDrill = { openDrill(it, 0) },
            onReview = { go(Screen.Review) },
            onOpenMidi = openMidi,
            onOpenSettings = openSettings,
            vm = rememberViewModel(screen) { SkillViewModel(app) },
        )
        Screen.Ear -> ToneHomeScreen(
            onOpen = { go(Screen.ToneSession(it)) },
            onEdit = { go(Screen.ToneEdit(it)) },
            onOpenMidi = openMidi,
            onOpenSettings = openSettings,
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
            vm = rememberViewModel(screen) { DrillViewModel(app, args("lessonId" to screen.lessonId, "drill" to screen.index.toString())) })
        Screen.Review -> DrillScreen(onBack = back, vm = rememberViewModel(screen) { DrillViewModel(app, args("review" to "true")) })
        is Screen.Pieces -> PieceListScreen(onBack = back, onOpenPiece = { id, hand -> go(Screen.Reader(id, hand)) },
            vm = rememberViewModel(screen) { PieceListViewModel(app, args("lesson" to screen.lessonId, "drill" to screen.index.toString())) })
        is Screen.Reader -> ReaderScreen(onBack = back,
            vm = rememberViewModel(screen) { ReaderViewModel(app, args("pieceId" to screen.pieceId, "hand" to screen.hand)) })
        is Screen.ScoreDrill -> ReaderScreen(onBack = back,
            vm = rememberViewModel(screen) { ReaderViewModel(app, args("lessonId" to screen.lessonId, "drill" to screen.index.toString())) })
        else -> Unit
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
