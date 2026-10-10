package com.id.mimikopi.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/** Mirror of `latest.json` published by .github/workflows/release.yml to the releases repo. */
@Serializable
data class UpdateManifest(
    val schema: Int,
    val versionCode: Long,
    val versionName: String,
    val tag: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
    val notesTh: String,
    val publishedAt: String,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): UpdateManifest = json.decodeFromString(serializer(), text)
    }
}

sealed interface UpdateCheckResult {
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult
    data class UpToDate(val latestVersionName: String) : UpdateCheckResult
    data class Invalid(val reason: String) : UpdateCheckResult
}

/**
 * Decides whether a manifest is a valid, newer release. Validation is strict because the result
 * leads to downloading and installing an APK: the URL must be HTTPS on an allowed GitHub host,
 * the checksum must be a SHA-256 hex string, and the size must be plausible.
 */
object UpdatePolicy {
    const val SUPPORTED_SCHEMA = 1
    val ALLOWED_HOSTS = setOf("github.com")
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    fun evaluate(manifest: UpdateManifest, installedVersionCode: Long): UpdateCheckResult {
        if (manifest.schema != SUPPORTED_SCHEMA) return UpdateCheckResult.Invalid("unsupported schema ${manifest.schema}")
        if (!SHA256_HEX.matches(manifest.sha256)) return UpdateCheckResult.Invalid("bad sha256")
        if (manifest.apkSize !in 1..MAX_APK_BYTES) return UpdateCheckResult.Invalid("bad apk size ${manifest.apkSize}")
        val uri = runCatching { URI(manifest.apkUrl) }.getOrNull()
            ?: return UpdateCheckResult.Invalid("bad apk url")
        if (uri.scheme != "https" || uri.host !in ALLOWED_HOSTS) {
            return UpdateCheckResult.Invalid("apk url not allowed: ${manifest.apkUrl}")
        }
        return if (manifest.versionCode > installedVersionCode) {
            UpdateCheckResult.Available(manifest)
        } else {
            UpdateCheckResult.UpToDate(manifest.versionName)
        }
    }
}
