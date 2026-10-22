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

@Composable
private fun Rail(ctx: AppContext) {
    val ports by ctx.services.midi.ports.collectAsState()
    val pianoNames by ctx.services.pianoNames.collectAsState()
    val audio by ctx.services.piano.status.collectAsState()
    val currentTab = (ctx.nav.stack.firstOrNull() as? Screen.Home)?.tab
    val shape = RoundedCornerShape(Tokens.Radius.card)
    Box(Modifier.width(232.dp).fillMaxHeight().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)) {
        Column(
            Modifier.fillMaxSize().softShadow(shape).clip(shape).background(Tokens.Palette.Group).padding(vertical = 22.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.padding(start = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Tokens.Palette.AccentSoft), contentAlignment = Alignment.Center) {
                    Text("\uE050", fontFamily = Bravura, fontSize = 18.sp, color = Tokens.Palette.Accent, modifier = Modifier.offset(y = (-13).dp))
                }
                Text("ミミコピ", fontFamily = Tokens.Fonts.kana, fontSize = 20.sp, color = Tokens.Palette.Ink)
            }
            for (t in Tab.entries) {
                if (t == Tab.SETTINGS) continue
                RailItem(t, t == currentTab) { ctx.nav.home(t) }
            }
            Spacer(Modifier.weight(1f))
            val midi = ports.count { it.open }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.box)).background(Tokens.Palette.Paper)
                    .clickable { ctx.nav.home(Tab.SETTINGS) }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                StatusDot(midi > 0, if (midi > 0) "MIDI ${com.id.mimikopi.core.input.PianoNames.display(ports.first { it.open }.name, pianoNames)}" else "ไม่มี MIDI · ใช้เมาส์/คีย์บอร์ด")
                StatusDot(audio.open || audio.error == null, when {
                    audio.error != null -> "เสียง: ${audio.error}"
                    audio.samples -> "เสียงเปียโนจริง"
                    else -> "เสียงพื้นฐาน"
                })
            }
            RailItem(Tab.SETTINGS, currentTab == Tab.SETTINGS) { ctx.nav.home(Tab.SETTINGS) }
            Text("F11 เต็มจอ · Esc กลับ", fontSize = 12.sp, color = Tokens.Palette.Dim, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        }
    }
}

/** One page in the sidebar: the current one an accentSoft capsule in accentDeep. */
@Composable
private fun RailItem(t: Tab, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp))
            .background(if (on) Tokens.Palette.AccentSoft else Tokens.Palette.Group)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t.mark, fontSize = 16.sp, color = if (on) Tokens.Palette.AccentDeep else Tokens.Palette.InkMuted, modifier = Modifier.width(20.dp))
        Text(t.title, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Tokens.Palette.AccentDeep else Tokens.Palette.Ink)
    }
}
