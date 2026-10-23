package com.id.mimikopi.desktop

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.launch
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
            com.id.mimikopi.core.i18n.L10n.lang = com.id.mimikopi.core.i18n.Lang.parse(s.db.get(com.id.mimikopi.core.i18n.L10n.KEY))
            // One automatic copy a day, before anything changes.
            runCatching { s.transfer.dailyBackupIfDue() }
            // Bring in the phone's progress (when sync is set up), without holding the window.
            s.appScope.launch { s.sync.sync(quiet = true) }
        }
    }
    Thread.setDefaultUncaughtExceptionHandler { t, e -> crashLog(dirs, "thread ${t.name}", e) }
    application {
        val services = started.getOrNull()
        if (services == null) {
            Window(onCloseRequest = ::exitApplication, title = "ミミコピ") {
                Column(Modifier.padding(24.dp)) {
                    Text(tr("เปิดข้อมูลไม่ได้: ${started.exceptionOrNull()?.message}", "Couldn't open the data: ${started.exceptionOrNull()?.message}", "データを開けません：${started.exceptionOrNull()?.message}"))
                    Text(tr("ข้อมูลอยู่ที่ ${dirs.root.absolutePath} (ไม่ได้ลบหรือเปลี่ยนอะไร)", "The data is at ${dirs.root.absolutePath} (nothing was deleted or changed)", "データは${dirs.root.absolutePath}にあります（何も削除・変更していません）"))
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
        fun releaseHeld() {
            held.values.forEach { services.hub.dispatch(InputEvent.NoteOff(it, System.nanoTime(), InputSource.COMPUTER_KEYS)) }
            held.clear()
        }
        Window(
            onCloseRequest = {
                services.close()
                exitApplication()
            },
            state = state,
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(com.id.mimikopi.desktop.ui.components.MimikopiLogo),
            title = tr("ミミコピ ฝึกเปียโน", "ミミコピ piano practice", "ミミコピ ピアノ練習"),
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
                    up && code in held -> {
                        services.hub.dispatch(InputEvent.NoteOff(held.remove(code)!!, System.nanoTime(), InputSource.COMPUTER_KEYS))
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
                    else -> false
                }
            },
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            // A key let go in another window (Alt+Tab while holding it) sends its key-up there: let the notes go now.
            DisposableEffect(window) {
                val focus = object : java.awt.event.WindowAdapter() {
                    override fun windowLostFocus(e: java.awt.event.WindowEvent) = releaseHeld()
                }
                window.addWindowFocusListener(focus)
                onDispose { window.removeWindowFocusListener(focus) }
            }
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
