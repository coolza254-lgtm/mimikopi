package com.id.mimikopi.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.data.AppDirs
import com.id.mimikopi.desktop.data.Database
import com.id.mimikopi.desktop.input.ComputerKeyboard
import com.id.mimikopi.desktop.ui.App
import com.id.mimikopi.desktop.ui.AppContext
import com.id.mimikopi.desktop.ui.KeyRouting
import com.id.mimikopi.desktop.ui.Navigator
import java.awt.event.KeyEvent
import java.io.File
import java.io.PrintWriter
import java.time.Instant

val APP_VERSION: String = System.getProperty("mimikopi.version") ?: "0.30.0-dev"

fun main() {
    val dirs = AppDirs.default()
    val started = runCatching {
        val db = Database.open(dirs.database)
        Services(dirs, db, Piano(), APP_VERSION).also { s ->
            // One automatic copy a day, before anything changes.
            runCatching { s.transfer.dailyBackupIfDue() }
        }
    }
    Thread.setDefaultUncaughtExceptionHandler { t, e -> crashLog(dirs, "thread ${t.name}", e) }
    application {
        val services = started.getOrNull()
        if (services == null) {
            Window(onCloseRequest = ::exitApplication, title = "ミミコピ") {
                Column(Modifier.padding(24.dp)) {
                    Text("เปิดข้อมูลไม่ได้: ${started.exceptionOrNull()?.message}")
                    Text("ข้อมูลอยู่ที่ ${dirs.root.absolutePath} (ไม่ได้ลบหรือเปลี่ยนอะไร)")
                }
            }
            return@application
        }
        val state = rememberWindowState(placement = WindowPlacement.Maximized, size = DpSize(1280.dp, 800.dp))
        val nav = remember { Navigator() }
        val keys = remember { KeyRouting() }
        var lastPlacement by remember { mutableStateOf(WindowPlacement.Maximized) }
        val ctx = remember {
            AppContext(services, nav, keys,
                toggleFullscreen = {
                    if (state.placement == WindowPlacement.Fullscreen) state.placement = lastPlacement
                    else { lastPlacement = state.placement; state.placement = WindowPlacement.Fullscreen }
                },
                isFullscreen = { state.placement == WindowPlacement.Fullscreen },
                quit = {
                    services.close()
                    exitApplication()
                },
            )
        }
        // Typing keys held now, so key repeat never restarts a held note.
        val held = remember { HashMap<Int, Int>() }
        Window(
            onCloseRequest = {
                services.close()
                exitApplication()
            },
            state = state,
            title = "ミミコピ ฝึกเปียโน",
            onPreviewKeyEvent = handler@{ e ->
                val code = e.key.nativeKeyCode
                val down = e.type == KeyEventType.KeyDown
                val up = e.type == KeyEventType.KeyUp
                when {
                    code == KeyEvent.VK_F11 && down -> { ctx.toggleFullscreen(); true }
                    code == KeyEvent.VK_ESCAPE && down -> {
                        if (keys.onEscape?.invoke() != true) {
                            if (state.placement == WindowPlacement.Fullscreen) ctx.toggleFullscreen() else nav.back()
                        }
                        true
                    }
                    keys.textInput -> false
                    code == KeyEvent.VK_SPACE && down -> { keys.onSpace?.invoke(); keys.onSpace != null }
                    code == KeyEvent.VK_SPACE -> keys.onSpace != null
                    (code == ComputerKeyboard.OCTAVE_DOWN || code == ComputerKeyboard.OCTAVE_UP) && down -> {
                        services.setKeysLowC(ComputerKeyboard.shiftOctave(services.keysLowC.value, code == ComputerKeyboard.OCTAVE_UP))
                        true
                    }
                    down && !e.isCtrlPressedOrMeta() -> {
                        if (code in held) return@handler true
                        val note = ComputerKeyboard.noteFor(code, services.keysLowC.value) ?: return@handler false
                        held[code] = note
                        services.hub.dispatch(InputEvent.NoteOn(note, ComputerKeyboard.VELOCITY, System.nanoTime(), InputSource.COMPUTER_KEYS))
                        true
                    }
                    up -> {
                        val note = held.remove(code) ?: return@handler false
                        services.hub.dispatch(InputEvent.NoteOff(note, System.nanoTime(), InputSource.COMPUTER_KEYS))
                        true
                    }
                    else -> false
                }
            },
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            App(ctx)
        }
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isCtrlPressedOrMeta(): Boolean =
    (nativeKeyEvent as? KeyEvent)?.let { it.isControlDown || it.isMetaDown || it.isAltDown } ?: false

internal fun crashLog(dirs: AppDirs, where: String, e: Throwable) {
    runCatching {
        File(dirs.logs, "crash.log").appendText(buildString {
            append("=== ").append(Instant.now()).append(' ').append(APP_VERSION).append(' ').append(where).append('\n')
            val sw = java.io.StringWriter()
            e.printStackTrace(PrintWriter(sw))
            append(sw).append('\n')
        })
    }
}
