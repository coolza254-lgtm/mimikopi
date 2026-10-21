package com.id.mimikopi.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.session.DrillSource
import com.id.mimikopi.desktop.session.ReaderArgs

/** The main sections, in the left rail. */
enum class Tab(val title: String, val mark: String) {
    TODAY("วันนี้", "◎"),
    LESSONS("บทเรียน", "≡"),
    SKILLS("ทักษะ", "◆"),
    SONGS("เพลง", "♪"),
    EAR("ฝึกหู", "◉"),
    PROGRESS("พัฒนาการ", "↗"),
    PIANO("เปียโน", "▥"),
    SETTINGS("ตั้งค่า", "⚙"),
}

sealed interface Screen {
    data class Home(val tab: Tab) : Screen
    data class Lesson(val id: String) : Screen
    data class Drill(val source: DrillSource, val via: String? = null) : Screen
    data class Reader(val args: ReaderArgs) : Screen
    data object Recall : Screen
    data object Metronome : Screen
    data object Chords : Screen
    data class Skill(val id: com.id.mimikopi.core.progress.SkillId) : Screen
    data class Ear(val args: com.id.mimikopi.desktop.session.EarArgs) : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home(Tab.TODAY))
    val current: Screen get() = stack.last()

    fun open(s: Screen) { stack += s }

    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    fun home(tab: Tab) {
        stack.clear()
        stack += Screen.Home(tab)
    }
}

/**
 * The keys that work everywhere, routed to the page that is shown: Space starts or stops the run, Esc stops it (or
 * goes back when nothing runs). Pages register their handlers while they are visible. While a text field has focus,
 * typing goes to it and the computer keyboard plays no notes.
 */
class KeyRouting {
    var onSpace: (() -> Unit)? = null
    /** Returns true when it stopped something (otherwise Esc goes back). */
    var onEscape: (() -> Boolean)? = null
    var textInput by mutableStateOf(false)
}

class AppContext(
    val services: Services,
    val nav: Navigator,
    val keys: KeyRouting,
    val toggleFullscreen: () -> Unit,
    val isFullscreen: () -> Boolean,
    /** Saves and closes the program (an update installs once it has closed). */
    val quit: () -> Unit = {},
)

val LocalApp = staticCompositionLocalOf<AppContext> { error("no app") }
