package com.id.mimikopi.desktop.update

import com.id.mimikopi.core.update.Sha256
import com.id.mimikopi.core.update.WindowsManifest
import com.id.mimikopi.core.update.WindowsUpdateCheck
import com.id.mimikopi.core.update.WindowsUpdatePolicy
import com.id.mimikopi.desktop.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Updates the Windows version from inside the program: reads windows.json from the latest release of the public
 * releases repo, downloads the MSI with its size and SHA-256 checked, then runs the installer (the same upgrade code,
 * so it replaces this version and keeps %APPDATA%\Mimikopi) and starts the program again. The check is the only
 * network use besides the optional piano sound download, as on the phone.
 */
class DesktopUpdater(private val dir: File, private val store: Store, private val installedVersion: String) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val latest: String) : State
        data class Available(val manifest: WindowsManifest) : State
        data class Downloading(val fraction: Float) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()

    /** True on the installed Windows program (a development run has no version and no installer to replace). */
    val supported: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows") && installedVersion != "dev"

    /** Checks once a day by itself (on start); [force] for the button in Settings. Runs on the calling (background) thread. */
    fun check(force: Boolean) {
        if (!supported) return
        val last = store.get(LAST_CHECK)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (!force && last != null && last.isAfter(Instant.now().minus(Duration.ofHours(20)))) return
        _state.value = State.Checking
        _state.value = try {
            val res = client.send(HttpRequest.newBuilder(URI(MANIFEST_URL)).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString())
            store.put(LAST_CHECK, Instant.now().toString())
            when {
                // The release exists before its Windows installer is attached (a few minutes): nothing to offer yet.
                res.statusCode() == 404 -> State.UpToDate(installedVersion)
                res.statusCode() != 200 -> State.Failed("ตรวจไม่สำเร็จ (HTTP ${res.statusCode()})")
                else -> when (val r = WindowsUpdatePolicy.evaluate(WindowsManifest.parse(res.body()), installedVersion)) {
                    is WindowsUpdateCheck.Available -> State.Available(r.manifest)
                    is WindowsUpdateCheck.UpToDate -> State.UpToDate(r.latestVersionName)
                    is WindowsUpdateCheck.Invalid -> State.Failed("ข้อมูลรุ่นใหม่ไม่ถูกต้อง (${r.reason})")
                }
            }
        } catch (e: Exception) {
            State.Failed("ตรวจไม่สำเร็จ: ต่ออินเทอร์เน็ตอยู่ไหม (${e::class.simpleName})")
        }
    }

    /**
     * Downloads and verifies the MSI, then starts the installer and returns true: the caller exits the program so
     * the installer can replace its files. Runs on the calling (background) thread.
     */
    fun downloadAndInstall(m: WindowsManifest): Boolean {
        val updates = File(dir, "updates").apply { mkdirs() }
        updates.listFiles()?.forEach { it.delete() }
        val msi = File(updates, "mimikopi-windows-${m.tag}.msi")
        try {
            _state.value = State.Downloading(0f)
            val res = client.send(HttpRequest.newBuilder(URI(m.msiUrl)).build(), HttpResponse.BodyHandlers.ofInputStream())
            if (res.statusCode() != 200) throw IOException("HTTP ${res.statusCode()}")
            val sha = Sha256()
            var received = 0L
            res.body().use { input ->
                msi.outputStream().buffered(1 shl 16).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var shown = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sha.update(buf, 0, n)
                        received += n
                        if (received > m.msiSize) throw IOException("larger than announced")
                        if (received - shown > 1 shl 20) { shown = received; _state.value = State.Downloading(received.toFloat() / m.msiSize) }
                    }
                }
            }
            if (received != m.msiSize) throw IOException("size $received, expected ${m.msiSize}")
            if (sha.hex() != m.sha256) throw IOException("checksum mismatch")
        } catch (e: Exception) {
            msi.delete()
            _state.value = State.Failed("ดาวน์โหลดไม่สำเร็จ ลองใหม่อีกครั้ง (${e.message})")
            return false
        }
        // A small script waits for this program to close, installs, then starts the new version.
        val app = System.getProperty("jpackage.app-path").orEmpty()
        val script = File(updates, "install.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "timeout /t 2 /nobreak >nul",
                "start \"\" /wait msiexec /i \"%~1\" /passive /norestart",
                "if not \"%~2\"==\"\" start \"\" \"%~2\"",
            ).joinToString("\r\n", postfix = "\r\n"),
        )
        return try {
            ProcessBuilder("cmd.exe", "/c", script.absolutePath, msi.absolutePath, app).directory(updates).start()
            _state.value = State.Installing
            true
        } catch (e: Exception) {
            _state.value = State.Failed("เปิดตัวติดตั้งไม่ได้ (${e.message}) ไฟล์อยู่ที่ ${msi.absolutePath}")
            false
        }
    }

    companion object {
        const val MANIFEST_URL = "https://github.com/coolza254-lgtm/mimikopi-releases/releases/latest/download/windows.json"
        /** Its own row, apart from the phone's last update check (backups carry settings both ways). */
        private const val LAST_CHECK = "update.windows.lastCheck"
    }
}
