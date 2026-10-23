package com.id.mimikopi.desktop.audio

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.piano.PackDescriptor
import com.id.mimikopi.core.piano.PackManifest
import com.id.mimikopi.core.piano.SampleMap
import com.id.mimikopi.core.update.Sha256
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

sealed interface PackProgress {
    data class Downloading(val fraction: Float, val totalBytes: Long) : PackProgress
    data class Decoding(val done: Int, val total: Int) : PackProgress
}

class PackException(message: String) : IOException(message)

class InstalledPack(val dir: File, val manifest: PackManifest)

/**
 * The piano sample pack (Salamander, the same pack the phone uses), installed only when the person asks: downloaded
 * from the public releases repository, or read from a zip file they already have. Verified (size and SHA-256 from
 * the descriptor, manifest checks from core), decoded once to the engine's raw PCM in a temporary folder that is
 * renamed only when complete, so an interrupted install never leaves a half-usable pack.
 */
class SamplePacks(private val root: File, private val appVersion: String) {
    private fun packDir(version: Int) = File(root, "v$version")

    fun installed(): InstalledPack? {
        val dir = packDir(PACK_VERSION)
        val manifestFile = File(dir, "manifest.json")
        if (!File(dir, READY_MARKER).exists() || !manifestFile.exists()) return null
        return runCatching { InstalledPack(dir, PackManifest.parse(manifestFile.readText())) }.getOrNull()
    }

    fun installedBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Loads [pack] into the piano; returns an error message or null. */
    fun load(piano: Piano, pack: InstalledPack): String? {
        val map = runCatching { SampleMap(pack.manifest) }.getOrElse { return it.message }
        return piano.loadPack(pack.dir, map, pack.manifest.sampleRate, pack.manifest.regions.map { it.releaseSec }, pack.manifest.regions.map { it.keyCenter })
    }

    fun fetchDescriptor(): PackDescriptor {
        val text = try {
            open(DESCRIPTOR_URL).let { c -> try { c.inputStream.readBytes().toString(Charsets.UTF_8) } finally { c.disconnect() } }
        } catch (e: IOException) {
            throw PackException(tr("ต่ออินเทอร์เน็ตไม่ได้: ${e.message}", "No internet connection: ${e.message}", "インターネットに接続できません：${e.message}"))
        }
        val d = runCatching { PackDescriptor.parse(text) }.getOrElse { throw PackException(tr("ข้อมูลชุดเสียงไม่ถูกต้อง: ${it.message}", "Invalid sound pack data: ${it.message}", "音源パックのデータが不正です：${it.message}")) }
        val problems = d.problems()
        if (problems.isNotEmpty() || d.packVersion != PACK_VERSION) throw PackException("ข้อมูลชุดเสียงไม่ถูกต้อง: ${(problems + "version ${d.packVersion}").joinToString()}")
        return d
    }

    /** Downloads, verifies and installs. [cancelled] is checked between blocks. */
    fun install(descriptor: PackDescriptor, cancelled: () -> Boolean, onProgress: (PackProgress) -> Unit): InstalledPack {
        root.mkdirs()
        val need = descriptor.size + descriptor.decodedBytes + SPACE_MARGIN
        if (root.usableSpace in 1 until need) throw PackException(tr("พื้นที่ว่างไม่พอ: ต้องการ ${need / 1_000_000} MB", "Not enough free space: ${need / 1_000_000} MB needed", "空き容量不足：${need / 1_000_000} MB必要"))
        val zip = File(root, "samples-v${descriptor.packVersion}.zip.part")
        try {
            download(descriptor, zip, cancelled, onProgress)
            return installZip(zip, cancelled, onProgress, "${descriptor.packVersion} ${descriptor.sha256}")
        } finally {
            zip.delete()
        }
    }

    /** Installs from a zip the person already downloaded (checked by its manifest; no descriptor to compare with). */
    fun installFromFile(zip: File, cancelled: () -> Boolean, onProgress: (PackProgress) -> Unit): InstalledPack =
        installZip(zip, cancelled, onProgress, "file ${zip.name}")

    private fun installZip(zip: File, cancelled: () -> Boolean, onProgress: (PackProgress) -> Unit, marker: String): InstalledPack {
        val work = File(root, "v$PACK_VERSION.tmp")
        work.deleteRecursively()
        work.mkdirs()
        try {
            val manifest = unzip(zip, work)
            val layers = manifest.regions.flatMap { it.layers }
            layers.forEachIndexed { i, layer ->
                if (cancelled()) throw PackException(tr("ยกเลิกแล้ว", "Cancelled", "キャンセルしました"))
                val flac = File(work, layer.file)
                val pcm = File(work, layer.file.removeSuffix(".flac") + ".pcm")
                val r = NativeAudio.nativeDecodeToPcm16(flac.absolutePath, pcm.absolutePath) ?: throw PackException(tr("ถอดรหัส ${layer.file} ไม่ได้", "Couldn't decode ${layer.file}", "${layer.file}をデコードできません"))
                if (r[1].toInt() != manifest.sampleRate) throw PackException(tr("${layer.file}: ${r[1]} Hz ไม่ตรงกับ ${manifest.sampleRate} Hz", "${layer.file}: ${r[1]} Hz doesn't match ${manifest.sampleRate} Hz", "${layer.file}：${r[1]} Hzが${manifest.sampleRate} Hzと一致しません"))
                if (r[0] != layer.frames.toLong()) throw PackException(tr("${layer.file}: ${r[0]} เฟรม ไม่ตรงกับ ${layer.frames}", "${layer.file}: ${r[0]} frames doesn't match ${layer.frames}", "${layer.file}：${r[0]}フレームが${layer.frames}と一致しません"))
                flac.delete()
                onProgress(PackProgress.Decoding(i + 1, layers.size))
            }
            File(work, READY_MARKER).writeText(marker)
            val target = packDir(PACK_VERSION)
            target.deleteRecursively()
            if (!work.renameTo(target)) throw PackException(tr("ย้ายโฟลเดอร์ชุดเสียงไม่ได้", "Couldn't move the sound pack folder", "音源パックのフォルダーを移動できません"))
            return InstalledPack(target, manifest)
        } catch (e: Exception) {
            work.deleteRecursively()
            throw e
        }
    }

    fun uninstall() {
        root.deleteRecursively()
    }

    private fun download(d: PackDescriptor, dest: File, cancelled: () -> Boolean, onProgress: (PackProgress) -> Unit) {
        val sha = Sha256()
        var received = 0L
        try {
            val conn = open(d.url)
            try {
                conn.inputStream.use { input ->
                    dest.outputStream().buffered(1 shl 16).use { out ->
                        val buffer = ByteArray(1 shl 16)
                        var lastReport = 0L
                        while (true) {
                            if (cancelled()) throw PackException(tr("ยกเลิกแล้ว", "Cancelled", "キャンセルしました"))
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            sha.update(buffer, 0, n)
                            received += n
                            if (received > d.size) throw PackException(tr("ไฟล์ใหญ่กว่าที่ระบุ", "File is larger than stated", "ファイルが記載より大きいです"))
                            if (received - lastReport > 256 * 1024) {
                                lastReport = received
                                onProgress(PackProgress.Downloading(received.toFloat() / d.size, d.size))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: PackException) {
            throw e
        } catch (e: IOException) {
            throw PackException(tr("ดาวน์โหลดไม่สำเร็จ: ${e.message}", "Download failed: ${e.message}", "ダウンロード失敗：${e.message}"))
        }
        if (received != d.size) throw PackException(tr("ขนาดไฟล์ไม่ตรง ($received/${d.size})", "File size doesn't match ($received/${d.size})", "ファイルサイズが一致しません（$received/${d.size}）"))
        if (sha.hex() != d.sha256) throw PackException(tr("ตรวจ SHA-256 ไม่ผ่าน", "SHA-256 check failed", "SHA-256の確認に失敗"))
    }

    /** Extracts only the files the manifest names (never paths), after validating the manifest. */
    private fun unzip(zip: File, work: File): PackManifest {
        val entries = mutableMapOf<String, File>()
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val name = e.name
                if (e.isDirectory || !SAFE_NAME.matches(name)) continue
                val out = File(work, name)
                out.outputStream().buffered().use { zin.copyTo(it) }
                entries[name] = out
            }
        }
        val manifestFile = entries["manifest.json"] ?: throw PackException(tr("ไม่ใช่ชุดเสียงของโปรแกรม (ไม่มี manifest)", "Not a sound pack for this app (no manifest)", "このアプリの音源パックではありません（manifestなし）"))
        val manifest = runCatching { PackManifest.parse(manifestFile.readText()) }.getOrElse { throw PackException("manifest: ${it.message}") }
        val problems = manifest.problems()
        if (problems.isNotEmpty()) throw PackException(problems.take(3).joinToString())
        val missing = manifest.regions.flatMap { it.layers }.map { it.file }.filter { it !in entries }
        if (missing.isNotEmpty()) throw PackException(tr("ไฟล์ขาด ${missing.take(3)}", "Missing files ${missing.take(3)}", "ファイルが不足 ${missing.take(3)}"))
        return manifest
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "mimikopi-pc/$appVersion")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code")
        }
        return conn
    }

    companion object {
        const val PACK_VERSION = 1
        const val DESCRIPTOR_URL = "https://github.com/coolza254-lgtm/mimikopi/releases/download/samples-v1/samples-v1.json"
        private const val READY_MARKER = "READY"
        private const val SPACE_MARGIN = 100L * 1024 * 1024
        private val SAFE_NAME = Regex("^[A-Za-z0-9_.-]+$")
    }
}
