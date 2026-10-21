package com.id.mimikopi.desktop.ui

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
import com.id.mimikopi.desktop.ui.components.StatusDot
import com.id.mimikopi.desktop.ui.screens.DrillScreen
import com.id.mimikopi.desktop.ui.screens.LatencyScreen
import com.id.mimikopi.desktop.ui.screens.LessonScreen
import com.id.mimikopi.desktop.ui.screens.LessonsScreen
import com.id.mimikopi.desktop.ui.screens.ReaderScreen
import com.id.mimikopi.desktop.ui.screens.SettingsScreen
import com.id.mimikopi.desktop.ui.screens.SongsScreen
import com.id.mimikopi.desktop.ui.screens.TodayScreen
import com.id.mimikopi.desktop.ui.theme.MimikopiTheme
import com.id.mimikopi.desktop.ui.theme.Tokens

/** The look chosen in Settings: DARK (the phone's default), LIGHT or SYSTEM. */
object Look {
    var mode by mutableStateOf("DARK")
}

@Composable
fun App(ctx: AppContext) {
    val s = ctx.services
    remember { Look.mode = s.db.get(SettingKeys.THEME_MODE) ?: "DARK" }
    val dark = when (Look.mode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }
    MimikopiTheme(dark) {
        CompositionLocalProvider(LocalApp provides ctx) {
            val screen = ctx.nav.current
            // Music pages use the whole window; the rail would only take room from the notes.
            val full = screen is Screen.Reader || screen is Screen.Drill || screen is Screen.Latency
            Row(Modifier.fillMaxSize().background(Tokens.Palette.Paper)) {
                if (!full) Rail(ctx)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    // A new page instance per stack entry, so going back recreates nothing it should keep.
                    key(ctx.nav.stack.size, screen) {
                        when (screen) {
                            is Screen.Home -> when (screen.tab) {
                                Tab.TODAY -> TodayScreen()
                                Tab.LESSONS -> LessonsScreen()
                                Tab.SONGS -> SongsScreen()
                                Tab.SETTINGS -> SettingsScreen()
                            }
                            is Screen.Lesson -> LessonScreen(screen.id)
                            is Screen.Drill -> DrillScreen(screen.source, screen.via)
                            is Screen.Reader -> ReaderScreen(screen.args)
                            Screen.Latency -> LatencyScreen()
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
    val audio by ctx.services.piano.status.collectAsState()
    val currentTab = (ctx.nav.stack.firstOrNull() as? Screen.Home)?.tab
    Column(
        Modifier.width(196.dp).fillMaxHeight().background(Tokens.Palette.Group).padding(vertical = 16.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("ミミコピ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
        for (t in Tab.entries) {
            val on = t == currentTab
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Tokens.Palette.Paper else Tokens.Palette.Group)
                    .clickable { ctx.nav.home(t) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(t.mark, fontSize = 15.sp, color = if (on) Tokens.Palette.Ink else Tokens.Palette.InkMuted, modifier = Modifier.width(18.dp))
                Text(t.title, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        val midi = ports.count { it.open }
        StatusDot(midi > 0, if (midi > 0) "MIDI ${ports.first { it.open }.name}" else "ไม่มี MIDI · ใช้เมาส์/คีย์บอร์ด") { ctx.nav.home(Tab.SETTINGS) }
        StatusDot(audio.open || audio.error == null, when {
            audio.error != null -> "เสียง: ${audio.error}"
            audio.samples -> "เสียงเปียโนจริง"
            else -> "เสียงพื้นฐาน"
        }) { ctx.nav.home(Tab.SETTINGS) }
        Text("F11 เต็มจอ · Esc กลับ", fontSize = 12.sp, color = Tokens.Palette.Dim, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
    }
}
