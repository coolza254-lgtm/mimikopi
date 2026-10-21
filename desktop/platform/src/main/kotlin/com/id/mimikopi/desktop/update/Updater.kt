package com.id.mimikopi.desktop.update

import com.id.mimikopi.core.update.DesktopRelease
import com.id.mimikopi.core.update.DesktopUpdates
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class UpdateException(message: String) : Exception(message)

/**
 * The program's own updates, only when the user asks (Settings): [check] reads the list of desktop releases,
 * [download] fetches the installer and verifies its SHA-256 against the published list, [install] starts the Windows
 * installer (per-user, no administrator prompt) and reopens the program after it; the caller then quits so the
 * files can be replaced. Data in %APPDATA%\MimikopiPC is untouched by an update.
 */
class Updater(private val dir: File, private val appVersion: String) {
    fun check(): DesktopRelease? {
        val text = try {
            open(DesktopUpdates.RELEASES_API).let { c -> try { c.inputStream.readBytes().toString(Charsets.UTF_8) } finally { c.disconnect() } }
        } catch (e: IOException) {
            throw UpdateException("ต่ออินเทอร์เน็ตไม่ได้: ${e.message}")
        }
        val releases = runCatching { DesktopUpdates.parse(text) }.getOrElse { throw UpdateException("ข้อมูลรุ่นใหม่ไม่ถูกต้อง") }
        return DesktopUpdates.pick(releases, appVersion)
    }

    /** Downloads [r]'s installer to the updates folder, checks it and returns it; [cancelled] stops it between chunks. */
    fun download(r: DesktopRelease, cancelled: () -> Boolean, progress: (Float) -> Unit): File {
        val sums = try {
            open(r.sums.url).let { c -> try { c.inputStream.readBytes().toString(Charsets.UTF_8) } finally { c.disconnect() } }
        } catch (e: IOException) {
            throw UpdateException("โหลดรายการตรวจสอบไม่ได้: ${e.message}")
        }
        val expected = DesktopUpdates.checksum(sums, r.msi.name) ?: throw UpdateException("ไม่มีค่าตรวจสอบของ ${r.msi.name}")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, r.msi.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val c = open(r.msi.url)
            try {
                c.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            if (cancelled()) throw UpdateException("ยกเลิกแล้ว")
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            progress((done.toFloat() / r.msi.size).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
        } catch (e: IOException) {
            part.delete()
            throw UpdateException("ดาวน์โหลดไม่สำเร็จ: ${e.message}")
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            part.delete()
            throw UpdateException("ไฟล์ที่ได้ไม่ตรงกับค่าตรวจสอบ ไม่ได้ติดตั้ง")
        }
        val msi = File(dir, r.msi.name)
        msi.delete()
        if (!part.renameTo(msi)) throw UpdateException("บันทึกไฟล์ติดตั้งไม่ได้")
        return msi
    }

    /**
     * Starts the installer for [msi] in passive mode (a progress bar, no questions) and, when it has finished, opens
     * the program again from where it runs now. Windows only; returns false elsewhere.
     */
    fun install(msi: File): Boolean {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
        val exe = ProcessHandle.current().info().command().orElse(null)?.takeIf { it.endsWith(".exe", ignoreCase = true) }
        val reopen = exe?.let { " && start \"\" \"$it\"" } ?: ""
        // A short wait first (ping: `timeout` needs a console) so the program has closed before its files are replaced.
        ProcessBuilder("cmd", "/c", "ping -n 4 127.0.0.1 >nul && msiexec /i \"${msi.absolutePath}\" /passive /norestart$reopen")
            .redirectErrorStream(true).redirectOutput(File(dir, "install.log")).start()
        return true
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "mimikopi-pc/$appVersion")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code")
        }
        return conn
    }
}
