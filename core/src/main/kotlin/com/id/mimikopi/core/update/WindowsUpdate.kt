package com.id.mimikopi.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/** Mirror of `windows.json`, attached to each release by the release workflow's Windows job. */
@Serializable
data class WindowsManifest(
    val schema: Int,
    val versionName: String,
    val tag: String,
    val msiUrl: String,
    val msiSize: Long,
    val sha256: String,
    val notesTh: String = "",
    val publishedAt: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): WindowsManifest = json.decodeFromString(serializer(), text)
    }
}

sealed interface WindowsUpdateCheck {
    data class Available(val manifest: WindowsManifest) : WindowsUpdateCheck
    data class UpToDate(val latestVersionName: String) : WindowsUpdateCheck
    data class Invalid(val reason: String) : WindowsUpdateCheck
}

/**
 * Decides whether a Windows manifest is a valid, newer release. Strict for the same reason as [UpdatePolicy]: the
 * result leads to downloading and running an installer. HTTPS on github.com only, a SHA-256 hex checksum, a
 * plausible size, and a version (x.y.z) higher than the installed one.
 */
object WindowsUpdatePolicy {
    const val SUPPORTED_SCHEMA = 1
    const val MAX_MSI_BYTES = 400L * 1024 * 1024
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    private val VERSION = Regex("^\\d+(\\.\\d+){0,3}$")

    fun evaluate(manifest: WindowsManifest, installedVersion: String): WindowsUpdateCheck {
        if (manifest.schema != SUPPORTED_SCHEMA) return WindowsUpdateCheck.Invalid("unsupported schema ${manifest.schema}")
        if (!VERSION.matches(manifest.versionName)) return WindowsUpdateCheck.Invalid("bad version ${manifest.versionName}")
        if (!SHA256_HEX.matches(manifest.sha256)) return WindowsUpdateCheck.Invalid("bad sha256")
        if (manifest.msiSize !in 1..MAX_MSI_BYTES) return WindowsUpdateCheck.Invalid("bad msi size ${manifest.msiSize}")
        val uri = runCatching { URI(manifest.msiUrl) }.getOrNull() ?: return WindowsUpdateCheck.Invalid("bad msi url")
        if (uri.scheme != "https" || uri.host !in UpdatePolicy.ALLOWED_HOSTS || !uri.path.endsWith(".msi")) {
            return WindowsUpdateCheck.Invalid("msi url not allowed: ${manifest.msiUrl}")
        }
        if (!VERSION.matches(installedVersion)) return WindowsUpdateCheck.Invalid("unknown installed version $installedVersion")
        return if (compareVersions(manifest.versionName, installedVersion) > 0) WindowsUpdateCheck.Available(manifest)
        else WindowsUpdateCheck.UpToDate(manifest.versionName)
    }

    /** Numeric comparison of dotted versions ("0.18.10" > "0.18.9"); missing parts count as 0. */
    fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
