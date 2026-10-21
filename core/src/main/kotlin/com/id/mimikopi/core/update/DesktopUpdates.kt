package com.id.mimikopi.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URI

/** One file of a GitHub release, as the releases API lists it. */
@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

/** One GitHub release, as the releases API lists it (only the fields the updater reads). */
@Serializable
data class ReleaseEntry(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean = false,
    val body: String? = null,
    val assets: List<ReleaseAsset> = emptyList(),
)

/** A Windows build that can replace the one installed: its version, installer and checksum list. */
data class DesktopRelease(val version: String, val tag: String, val msi: ReleaseAsset, val sums: ReleaseAsset, val notes: String)

/**
 * The Windows program's updates: desktop builds are releases tagged `desktop-v<version>` in the public source
 * repository (not the phone's releases repository). The check only reads; downloading and installing happen when the
 * user presses the button. Strict like [UpdatePolicy]: HTTPS on GitHub only, a plausible size, a SHA-256 to verify.
 */
object DesktopUpdates {
    const val RELEASES_API = "https://api.github.com/repos/coolza254-lgtm/mimikopi/releases?per_page=30"
    val ALLOWED_HOSTS = setOf("github.com")
    const val MAX_MSI_BYTES = 400L * 1024 * 1024
    private const val TAG_PREFIX = "desktop-v"
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<ReleaseEntry> = json.decodeFromString(ListSerializer(ReleaseEntry.serializer()), text)

    /** "0.31.4" (or "0.30.0-89905ae", the part after a dash ignored) as numbers; null if it is not a version. */
    fun version(text: String): List<Int>? {
        val core = text.removePrefix(TAG_PREFIX).substringBefore('-')
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.size in 2..4 }
    }

    /** Whether version [a] is higher than [b] (missing parts count as 0). */
    fun newer(a: List<Int>, b: List<Int>): Boolean {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The newest desktop build above [installed] with a valid installer and checksum list, or null. */
    fun pick(releases: List<ReleaseEntry>, installed: String): DesktopRelease? {
        val current = version(installed) ?: listOf(0)
        return releases.asSequence()
            .filter { !it.draft && it.tag.startsWith(TAG_PREFIX) }
            .mapNotNull { r ->
                val v = version(r.tag) ?: return@mapNotNull null
                val msi = r.assets.firstOrNull { it.name.endsWith(".msi") && allowed(it.url) && it.size in 1..MAX_MSI_BYTES } ?: return@mapNotNull null
                val sums = r.assets.firstOrNull { it.name == "SHA256SUMS.txt" && allowed(it.url) } ?: return@mapNotNull null
                v to DesktopRelease(r.tag.removePrefix(TAG_PREFIX).substringBefore('-'), r.tag, msi, sums, r.body.orEmpty())
            }
            .filter { (v, _) -> newer(v, current) }
            .sortedWith { a, b -> if (newer(a.first, b.first)) -1 else if (newer(b.first, a.first)) 1 else 0 }
            .firstOrNull()?.second
    }

    /** The SHA-256 listed for [file] in a `sha256sum` output ("<hex> *<name>" or "<hex>  <path>"), or null. */
    fun checksum(sums: String, file: String): String? = sums.lineSequence().mapNotNull { line ->
        val hex = line.substringBefore(' ').trim().lowercase()
        val name = line.substringAfter(' ').trim().trimStart('*').substringAfterLast('/')
        hex.takeIf { name == file && SHA256_HEX.matches(it) }
    }.firstOrNull()

    private fun allowed(url: String): Boolean = runCatching { URI(url) }.getOrNull()?.let { it.scheme == "https" && it.host in ALLOWED_HOSTS } == true
}
