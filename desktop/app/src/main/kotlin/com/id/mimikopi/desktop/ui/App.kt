package com.id.mimikopi.desktop.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.learning.SettingKeys
import com.id.mimikopi.desktop.ui.components.Bravura
import com.id.mimikopi.desktop.ui.components.StatusDot
import com.id.mimikopi.desktop.ui.components.softShadow
import com.id.mimikopi.desktop.ui.screens.DrillScreen
import com.id.mimikopi.desktop.ui.screens.EarHomeScreen
import com.id.mimikopi.desktop.ui.screens.EarSessionScreen
import com.id.mimikopi.desktop.ui.screens.ChordsScreen
import com.id.mimikopi.desktop.ui.screens.MetronomeScreen
import com.id.mimikopi.desktop.ui.screens.ProgressScreen
import com.id.mimikopi.desktop.ui.screens.FreePianoScreen
import com.id.mimikopi.desktop.ui.screens.LessonScreen
import com.id.mimikopi.desktop.ui.screens.LessonsScreen
import com.id.mimikopi.desktop.ui.screens.ReaderScreen
import com.id.mimikopi.desktop.ui.screens.RecallScreen
import com.id.mimikopi.desktop.ui.screens.SettingsScreen
import com.id.mimikopi.desktop.ui.screens.SkillDetailScreen
import com.id.mimikopi.desktop.ui.screens.SkillsScreen
import com.id.mimikopi.desktop.ui.screens.SongsScreen
import com.id.mimikopi.desktop.ui.screens.TodayScreen
import com.id.mimikopi.desktop.ui.theme.MimikopiTheme
import com.id.mimikopi.desktop.ui.theme.Tokens

/** The look chosen in Settings: LIGHT (the pastel default), DARK or SYSTEM. */
object Look {
    var mode by mutableStateOf("LIGHT")

    /** Stored once the pastel look became the default: an older "dark" choice is moved to it a single time. */
    const val PASTEL_KEY = "ui.theme.pastel"
}

/** The language in use, as Compose state: changing it rebuilds every screen (core's L10n holds the language itself). */
object LangState {
    var version by mutableStateOf(0)

    fun set(services: com.id.mimikopi.desktop.Services, lang: com.id.mimikopi.core.i18n.Lang) {
        services.db.put(com.id.mimikopi.core.i18n.L10n.KEY, lang.code)
        com.id.mimikopi.core.i18n.L10n.lang = lang
        version++
    }
}

@Composable
fun App(ctx: AppContext) {
    val s = ctx.services
    remember {
        if (s.db.get(Look.PASTEL_KEY) == null) {
            s.db.put(Look.PASTEL_KEY, "1")
            if (s.db.get(SettingKeys.THEME_MODE) == "DARK") s.db.put(SettingKeys.THEME_MODE, "LIGHT")
        }
        Look.mode = s.db.get(SettingKeys.THEME_MODE) ?: "LIGHT"
    }
    val dark = when (Look.mode) {
        "DARK" -> true
        "SYSTEM" -> isSystemInDarkTheme()
        else -> false
    }
    MimikopiTheme(dark) {
        // A new language rebuilds every screen, so each text is read again in it.
        key(LangState.version) {
        CompositionLocalProvider(LocalApp provides ctx) {
            val screen = ctx.nav.current
            // Music pages use the whole window; the rail would only take room from the notes.
            val full = screen is Screen.Reader || screen is Screen.Drill || screen is Screen.Ear
            Row(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
                if (!full) Rail(ctx)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    // A new page instance per stack entry, so going back recreates nothing it should keep.
                    key(ctx.nav.stack.size, screen) {
                        when (screen) {
                            is Screen.Home -> when (screen.tab) {
                                Tab.TODAY -> TodayScreen()
                                Tab.LESSONS -> LessonsScreen()
                                Tab.SKILLS -> SkillsScreen()
                                Tab.SONGS -> SongsScreen()
                                Tab.EAR -> EarHomeScreen()
                                Tab.PROGRESS -> ProgressScreen()
                                Tab.PIANO -> FreePianoScreen()
                                Tab.SETTINGS -> SettingsScreen()
                            }
                            is Screen.Lesson -> LessonScreen(screen.id)
                            is Screen.Drill -> DrillScreen(screen.source, screen.via)
                            is Screen.Reader -> ReaderScreen(screen.args)
                            is Screen.Recall -> RecallScreen(screen.song, screen.hand)
                            Screen.Metronome -> MetronomeScreen()
                            Screen.Chords -> ChordsScreen()
                            is Screen.Skill -> SkillDetailScreen(screen.id)
                            is Screen.Ear -> EarSessionScreen(screen.args)
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * The sidebar (approved design 2026-10-23): a narrow white column with the app's logo on top and one icon per page,
 * no words (as on the phone's tab bar); the current page in an accentSoft capsule. A green dot above settings shows a
 * MIDI piano is connected; its name and the sound are on the settings page.
 */
@Composable
private fun Rail(ctx: AppContext) {
    val ports by ctx.services.midi.ports.collectAsState()
    val currentTab = (ctx.nav.stack.firstOrNull() as? Screen.Home)?.tab
    val shape = RoundedCornerShape(Tokens.Radius.card)
    Box(Modifier.width(96.dp).fillMaxHeight().padding(start = 16.dp, top = 16.dp, bottom = 16.dp)) {
        Column(
            Modifier.fillMaxSize().softShadow(shape).clip(shape).background(Tokens.Palette.Group).padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.id.mimikopi.desktop.ui.components.Logo(48.dp, Modifier.padding(bottom = 14.dp).clickable { ctx.nav.home(Tab.TODAY) })
            for (t in Tab.entries) {
                if (t == Tab.SETTINGS) continue
                RailItem(t, t == currentTab) { ctx.nav.home(t) }
            }
            Spacer(Modifier.weight(1f))
            val midi = ports.any { it.open }
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (midi) Tokens.Palette.Positive else Tokens.Palette.Dim))
            RailItem(Tab.SETTINGS, currentTab == Tab.SETTINGS) { ctx.nav.home(Tab.SETTINGS) }
        }
    }
}

/** One page in the sidebar: its line icon, the current one in an accentSoft capsule. */
@Composable
private fun RailItem(t: Tab, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(if (on) Tokens.Palette.AccentSoft else Tokens.Palette.Group)
            .clickable(onClickLabel = t.title, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            remember(t) { com.id.mimikopi.desktop.ui.components.lineIcon(t.name, androidx.compose.ui.graphics.Color.Black, *t.icon.toTypedArray()) },
            contentDescription = t.title,
            tint = if (on) Tokens.Palette.AccentDeep else Tokens.Palette.InkMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}
