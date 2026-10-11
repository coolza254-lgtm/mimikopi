package com.id.mimikopi.piano

import android.content.Context
import android.os.StatFs
import com.id.mimikopi.BuildConfig
import com.id.mimikopi.audio.FlacDecoder
import com.id.mimikopi.audio.NativePiano
import com.id.mimikopi.core.piano.PackDescriptor
import com.id.mimikopi.core.piano.PackManifest
import com.id.mimikopi.core.update.Sha256
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** An installed, decoded sample pack. */
data class InstalledPack(val dir: File, val manifest: PackManifest)

sealed interface PackProgress {
    data class Downloading(val fraction: Float, val totalBytes: Long) : PackProgress
    data class Decoding(val done: Int, val total: Int) : PackProgress
}

class PackException(val kind: Kind, detail: String) : IOException(detail) {
    enum class Kind { NETWORK, BAD_DESCRIPTOR, CHECKSUM, NO_SPACE, BAD_PACK, DECODE }
}

/**
 * Downloads the piano sample pack from the public releases repo, verifies it, and decodes it once
 * to raw PCM that the native engine memory-maps. Installation happens in a temporary directory that
 * is renamed only when complete, so an interrupted install never leaves a half-usable pack.
 */
class SamplePackManager(private val context: Context) {
    private val root: File get() = File(context.filesDir, "piano").apply { mkdirs() }
    private fun packDir(version: Int) = File(root, "v$version")

    /** The ready pack, or null if none is installed. */
    fun installed(): InstalledPack? {
        val dir = packDir(PACK_VERSION)
        val manifestFile = File(dir, "manifest.json")
        if (!File(dir, READY_MARKER).exists() || !manifestFile.exists()) return null
        return runCatching { InstalledPack(dir, PackManifest.parse(manifestFile.readText())) }.getOrNull()
    }

    fun installedBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    suspend fun fetchDescriptor(): PackDescriptor {
        val text = try {
            open(DESCRIPTOR_URL).let { c -> try { c.inputStream.readBytes().toString(Charsets.UTF_8) } finally { c.disconnect() } }
        } catch (e: IOException) {
            throw PackException(PackException.Kind.NETWORK, e.message ?: "network error")
        }
        val d = runCatching { PackDescriptor.parse(text) }.getOrElse { throw PackException(PackException.Kind.BAD_DESCRIPTOR, it.message ?: "bad json") }
        val problems = d.problems()
        if (problems.isNotEmpty() || d.packVersion != PACK_VERSION) {
            throw PackException(PackException.Kind.BAD_DESCRIPTOR, (problems + "version ${d.packVersion}").joinToString())
        }
        return d
    }

    suspend fun install(descriptor: PackDescriptor, onProgress: (PackProgress) -> Unit): InstalledPack {
        val need = descriptor.size + descriptor.decodedBytes + SPACE_MARGIN
        val free = StatFs(context.filesDir.path).availableBytes
        if (free < need) throw PackException(PackException.Kind.NO_SPACE, "need ${need / 1_000_000} MB, free ${free / 1_000_000} MB")

        val zip = File(context.cacheDir, "samples-v${descriptor.packVersion}.zip")
        val work = File(root, "v${descriptor.packVersion}.tmp")
        work.deleteRecursively()
        work.mkdirs()
        try {
            download(descriptor, zip, onProgress)
            val manifest = unzip(zip, work)
            zip.delete()
            val layers = manifest.regions.flatMap { it.layers }
            layers.forEachIndexed { i, layer ->
                currentCoroutineContext().ensureActive()
                val flac = File(work, layer.file)
                val pcm = File(work, NativePiano.pcmName(layer.file))
                val frames = try {
                    FlacDecoder.decode(flac, pcm, manifest.sampleRate)
                } catch (e: Exception) {
                    throw PackException(PackException.Kind.DECODE, "${layer.file}: ${e.message}")
                }
                if (frames != layer.frames.toLong()) throw PackException(PackException.Kind.DECODE, "${layer.file}: $frames frames, expected ${layer.frames}")
                flac.delete()
                onProgress(PackProgress.Decoding(i + 1, layers.size))
            }
            File(work, READY_MARKER).writeText("${descriptor.packVersion} ${descriptor.sha256}")
            val target = packDir(descriptor.packVersion)
            target.deleteRecursively()
            if (!work.renameTo(target)) throw IOException("cannot finalize pack")
            return InstalledPack(target, manifest)
        } catch (e: Exception) {
            work.deleteRecursively()
            zip.delete()
            throw e
        }
    }

    fun uninstall() {
        root.deleteRecursively()
    }

    private suspend fun download(d: PackDescriptor, dest: File, onProgress: (PackProgress) -> Unit) {
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
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            sha.update(buffer, 0, n)
                            received += n
                            if (received > d.size) throw PackException(PackException.Kind.CHECKSUM, "larger than announced")
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
            throw PackException(PackException.Kind.NETWORK, e.message ?: "network error")
        }
        if (received != d.size) throw PackException(PackException.Kind.CHECKSUM, "size $received != ${d.size}")
        if (sha.hex() != d.sha256) throw PackException(PackException.Kind.CHECKSUM, "sha256 mismatch")
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
        val manifestFile = entries["manifest.json"] ?: throw PackException(PackException.Kind.BAD_PACK, "no manifest")
        val manifest = runCatching { PackManifest.parse(manifestFile.readText()) }
            .getOrElse { throw PackException(PackException.Kind.BAD_PACK, "manifest: ${it.message}") }
        val problems = manifest.problems()
        if (problems.isNotEmpty()) throw PackException(PackException.Kind.BAD_PACK, problems.take(3).joinToString())
        val missing = manifest.regions.flatMap { it.layers }.map { it.file }.filter { it !in entries }
        if (missing.isNotEmpty()) throw PackException(PackException.Kind.BAD_PACK, "missing ${missing.take(3)}")
        return manifest
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "mimikopi/${BuildConfig.VERSION_NAME}")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return conn
    }

    companion object {
        const val PACK_VERSION = 1
        const val DESCRIPTOR_URL =
            "https://github.com/coolza254-lgtm/mimikopi-releases/releases/download/samples-v1/samples-v1.json"
        private const val READY_MARKER = "READY"
        private const val SPACE_MARGIN = 100L * 1024 * 1024
        private val SAFE_NAME = Regex("^[A-Za-z0-9_.-]+$")
    }
}
