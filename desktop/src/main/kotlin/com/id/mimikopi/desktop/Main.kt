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
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.desktop.ui.MimikopiTheme
import com.id.mimikopi.desktop.ui.SettingsScreen
import com.id.mimikopi.desktop.ui.Tokens
import com.id.mimikopi.desktop.ui.ear.EarDrillScreen
import com.id.mimikopi.desktop.ui.ear.EarHome
import com.id.mimikopi.desktop.ui.midiReady
import com.id.mimikopi.desktop.ui.sections.SectionEditScreen
import com.id.mimikopi.desktop.ui.sections.SectionPracticeScreen
import com.id.mimikopi.desktop.ui.sections.SectionsHome

/** Where the window is. */
sealed interface Screen {
    data object Ear : Screen
    data object Sections : Screen
    data object Settings : Screen
    data class EarDrill(val lesson: Lesson) : Screen
    data class SectionEdit(val id: String) : Screen
    data class SectionPractice(val id: String) : Screen
}

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

/** The window: a left rail (two tabs, MIDI state, settings) and the page, centred and width-limited for reading. */
@Composable
private fun App() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Ear)) }
    val screen = stack.last()
    fun go(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun tab(s: Screen) { stack = listOf(s) }
    val openMidi = { go(Screen.Settings) }

    Row(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
        Column(Modifier.width(220.dp).fillMaxHeight().padding(start = 28.dp, top = 32.dp, bottom = 28.dp)) {
            Text("ミミコピ", fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Tokens.Palette.Ink)
            Spacer(Modifier.height(36.dp))
            RailItem("ฝึกฟัง", stack.first() == Screen.Ear) { tab(Screen.Ear) }
            RailItem("ฝึกท่อน", stack.first() == Screen.Sections) { tab(Screen.Sections) }
            Spacer(Modifier.weight(1f))
            val ready = midiReady()
            Row(Modifier.heightIn(min = 40.dp).clickable(onClick = openMidi), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (ready) Tokens.Palette.Ink else Tokens.Palette.Faint, CircleShape))
                Text(if (ready) "  MIDI" else "  ไม่มี MIDI", fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = if (ready) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
            RailItem("ตั้งค่า", screen == Screen.Settings) { go(Screen.Settings) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Tokens.Palette.Hair))
        Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(horizontal = 48.dp, vertical = 40.dp)) {
                when (screen) {
                    Screen.Ear -> EarHome(onOpen = { go(Screen.EarDrill(it)) }, onOpenMidi = openMidi)
                    Screen.Sections -> SectionsHome(onPractice = { go(Screen.SectionPractice(it)) }, onEdit = { go(Screen.SectionEdit(it)) }, onOpenMidi = openMidi)
                    Screen.Settings -> SettingsScreen(onBack = ::back)
                    is Screen.EarDrill -> EarDrillScreen(screen.lesson, onBack = ::back, onOpenMidi = openMidi)
                    is Screen.SectionEdit -> SectionEditScreen(screen.id, onBack = ::back, onPractice = { stack = stack.dropLast(1) + Screen.SectionPractice(it) }, onOpenMidi = openMidi)
                    is Screen.SectionPractice -> SectionPracticeScreen(screen.id, onBack = ::back, onEdit = { stack = stack.dropLast(1) + Screen.SectionEdit(it) }, onOpenMidi = openMidi)
                }
            }
        }
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
