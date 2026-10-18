package com.id.mimikopi.desktop.audio

import com.id.mimikopi.core.piano.PackDescriptor
import com.id.mimikopi.core.piano.PackManifest
import com.id.mimikopi.core.update.Sha256
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

/**
 * Downloads the piano sample pack (the same public release asset the Android app uses), checks its size and SHA-256,
 * and decodes every FLAC once to raw PCM next to the manifest. Installation happens in a temp folder that is renamed
 * only when complete. The only network use besides the update check.
 */
class PackInstaller(private val root: File) {
    sealed interface Progress {
        data class Downloading(val fraction: Float) : Progress
        data class Decoding(val done: Int, val total: Int) : Progress
    }

    private val dir get() = File(root, "piano/v${PACK_VERSION}")
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()

    /** The installed pack's folder and manifest, or null. */
    fun installed(): Pair<File, PackManifest>? {
        val d = dir
        if (!File(d, READY).exists()) return null
        return runCatching { d to PackManifest.parse(File(d, "manifest.json").readText()) }.getOrNull()
    }

    fun install(onProgress: (Progress) -> Unit) {
        val descriptor = PackDescriptor.parse(get(DESCRIPTOR_URL).toString(Charsets.UTF_8))
        val problems = descriptor.problems()
        if (problems.isNotEmpty() || descriptor.packVersion != PACK_VERSION) throw IOException("bad descriptor: ${problems.joinToString()}")
        val work = File(root, "piano/v${PACK_VERSION}.tmp").apply { deleteRecursively(); mkdirs() }
        val zip = File(root, "piano/samples.zip")
        try {
            download(descriptor, zip, onProgress)
            unzip(zip, work)
            zip.delete()
            val manifest = PackManifest.parse(File(work, "manifest.json").readText())
            require(manifest.problems().isEmpty()) { "invalid pack" }
            val layers = manifest.regions.flatMap { it.layers }
            layers.forEachIndexed { i, layer ->
                val flac = File(work, layer.file)
                val frames = decode(flac, File(work, SamplerPiano.pcmName(layer.file)), manifest.sampleRate)
                if (frames != layer.frames.toLong()) throw IOException("${layer.file}: $frames frames, expected ${layer.frames}")
                flac.delete()
                onProgress(Progress.Decoding(i + 1, layers.size))
            }
            File(work, READY).writeText("${descriptor.packVersion} ${descriptor.sha256}")
            dir.deleteRecursively()
            if (!work.renameTo(dir)) throw IOException("cannot finalize the pack")
        } catch (e: Exception) {
            work.deleteRecursively()
            zip.delete()
            throw e
        }
    }

    private fun get(url: String): ByteArray {
        val res = client.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofByteArray())
        if (res.statusCode() != 200) throw IOException("HTTP ${res.statusCode()}")
        return res.body()
    }

    private fun download(d: PackDescriptor, dest: File, onProgress: (Progress) -> Unit) {
        val res = client.send(HttpRequest.newBuilder(URI(d.url)).build(), HttpResponse.BodyHandlers.ofInputStream())
        if (res.statusCode() != 200) throw IOException("HTTP ${res.statusCode()}")
        val sha = Sha256()
        var received = 0L
        res.body().use { input ->
            dest.parentFile.mkdirs()
            dest.outputStream().buffered(1 shl 16).use { out ->
                val buf = ByteArray(1 shl 16)
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sha.update(buf, 0, n)
                    received += n
                    if (received > d.size) throw IOException("larger than announced")
                    if (received - last > 512 * 1024) { last = received; onProgress(Progress.Downloading(received.toFloat() / d.size)) }
                }
            }
        }
        if (received != d.size) throw IOException("size $received != ${d.size}")
        if (sha.hex() != d.sha256) throw IOException("checksum mismatch")
    }

    private fun unzip(zip: File, work: File) {
        val base = work.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.isDirectory) continue
                val out = File(work, e.name).canonicalFile
                // Zip-slip guard: entries must stay inside the work folder.
                if (!out.path.startsWith(base.path + File.separator)) throw IOException("unsafe entry ${e.name}")
                out.parentFile.mkdirs()
                out.outputStream().use { zin.copyTo(it) }
            }
        }
    }

    /** FLAC (16-bit stereo) to raw little-endian PCM; returns the frames written. */
    private fun decode(input: File, output: File, expectedRate: Int): Long {
        var frames = 0L
        output.outputStream().buffered(1 shl 16).use { out ->
            input.inputStream().buffered().use { inp ->
                val decoder = FLACDecoder(inp)
                decoder.addPCMProcessor(object : PCMProcessor {
                    override fun processStreamInfo(info: StreamInfo) {
                        if (info.channels != 2 || info.bitsPerSample != 16 || info.sampleRate != expectedRate) throw IOException("${input.name}: not 16-bit stereo at $expectedRate")
                    }
                    override fun processPCM(pcm: ByteData) {
                        out.write(pcm.data, 0, pcm.len)
                        frames += pcm.len / 4
                    }
                })
                decoder.decode()
            }
        }
        return frames
    }

    companion object {
        const val PACK_VERSION = 1
        const val DESCRIPTOR_URL = "https://github.com/coolza254-lgtm/mimikopi-releases/releases/download/samples-v1/samples-v1.json"
        private const val READY = "READY"
    }
}
