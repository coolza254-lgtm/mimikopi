package com.id.mimikopi.desktop.ui

import com.id.mimikopi.core.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.session.DrillSource
import com.id.mimikopi.desktop.session.ReaderArgs

/** The main sections, in the left rail: a name (read aloud and shown nowhere else) and a line icon. */
enum class Tab(val title: String, val icon: List<String>) {
    TODAY(tr("วันนี้", "Today", "今日"), com.id.mimikopi.desktop.ui.components.LineIcons.today),
    LESSONS(tr("บทเรียน", "Lessons", "レッスン"), com.id.mimikopi.desktop.ui.components.LineIcons.lessons),
    SKILLS(tr("ทักษะ", "Skills", "スキル"), com.id.mimikopi.desktop.ui.components.LineIcons.skills),
    SONGS(tr("เพลง", "Songs", "曲"), com.id.mimikopi.desktop.ui.components.LineIcons.songs),
    EAR(tr("ฝึกหู", "Ear training", "耳トレ"), com.id.mimikopi.desktop.ui.components.LineIcons.ear),
    PROGRESS(tr("พัฒนาการ", "Progress", "上達"), com.id.mimikopi.desktop.ui.components.LineIcons.progress),
    PIANO(tr("เปียโน", "Piano", "ピアノ"), com.id.mimikopi.desktop.ui.components.LineIcons.piano),
    SETTINGS(tr("ตั้งค่า", "Settings", "設定"), com.id.mimikopi.desktop.ui.components.LineIcons.settings),
}

sealed interface Screen {
    data class Home(val tab: Tab) : Screen
    data class Lesson(val id: String) : Screen
    data class Drill(val source: DrillSource, val via: String? = null) : Screen
    data class Reader(val args: ReaderArgs) : Screen
    /** Private songs, opened on [song] and [hand] (a RecallHand name) when given. */
    data class Recall(val song: String? = null, val hand: String? = null) : Screen
    data object Metronome : Screen
    /** Bought sheets entered from the piano, and one of them being recorded. */
    data object Scores : Screen
    data class Record(val id: String) : Screen
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
    /** Left arrow; returns true when the page used it. */
    var onLeft: (() -> Boolean)? = null
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
