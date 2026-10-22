package com.id.mimikopi.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.id.mimikopi.BuildConfig
import com.id.mimikopi.core.update.Sha256
import com.id.mimikopi.core.update.UpdateCheckResult
import com.id.mimikopi.core.update.UpdateManifest
import com.id.mimikopi.core.update.UpdatePolicy
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Reasons an update cannot proceed; shown to the user in Thai by the UI. */
class UpdateException(val kind: Kind, detail: String) : IOException(detail) {
    enum class Kind { NETWORK, BAD_MANIFEST, CHECKSUM, WRONG_APK, INSTALL }
}

/**
 * Checks the public releases repo for a newer version, downloads the APK, verifies it, and hands
 * it to the system installer. Nothing is ever installed without the system confirmation dialog.
 */
class UpdateService(private val context: Context, private val db: AppDatabase) {

    private val updatesDir: File get() = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Fetches latest.json (unauthenticated) and decides whether it is a valid newer release. */
    suspend fun check(): UpdateCheckResult {
        val text = try {
            httpGet(LATEST_JSON_URL).use { it.inputStream.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            recordCheck("error: ${e.javaClass.simpleName}")
            throw UpdateException(UpdateException.Kind.NETWORK, e.message ?: "network error")
        }
        val manifest = runCatching { UpdateManifest.parse(text) }.getOrElse {
            recordCheck("error: bad manifest")
            throw UpdateException(UpdateException.Kind.BAD_MANIFEST, it.message ?: "bad manifest")
        }
        val result = UpdatePolicy.evaluate(manifest, BuildConfig.VERSION_CODE.toLong())
        recordCheck(
            when (result) {
                is UpdateCheckResult.Available -> "available ${manifest.versionName}"
                is UpdateCheckResult.UpToDate -> "up to date (latest ${result.latestVersionName})"
                is UpdateCheckResult.Invalid -> "invalid: ${result.reason}"
            },
        )
        return result
    }

    /**
     * Downloads the APK to app cache, computing SHA-256 while streaming. Only a fully downloaded file
     * whose checksum and package match is returned; anything else is deleted (no partial state).
     */
    suspend fun download(manifest: UpdateManifest, onProgress: (Float) -> Unit): File {
        clearDownloads()
        val part = File(updatesDir, "mimikopi-${manifest.tag}.apk.part")
        val done = File(updatesDir, "mimikopi-${manifest.tag}.apk")
        try {
            val sha = Sha256()
            var received = 0L
            httpGet(manifest.apkUrl).use { conn ->
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            sha.update(buffer, 0, n)
                            received += n
                            if (received > manifest.apkSize) throw UpdateException(UpdateException.Kind.CHECKSUM, "file larger than announced")
                            onProgress(received.toFloat() / manifest.apkSize)
                        }
                    }
                }
            }
            if (received != manifest.apkSize) throw UpdateException(UpdateException.Kind.CHECKSUM, "size $received != ${manifest.apkSize}")
            val actual = sha.hex()
            if (actual != manifest.sha256) throw UpdateException(UpdateException.Kind.CHECKSUM, "sha256 $actual != ${manifest.sha256}")
            verifyApk(part, manifest)
            if (!part.renameTo(done)) throw IOException("cannot finalize download")
            return done
        } catch (e: Exception) {
            part.delete()
            done.delete()
            throw if (e is UpdateException || e is kotlinx.coroutines.CancellationException) e
            else UpdateException(UpdateException.Kind.NETWORK, e.message ?: e.javaClass.simpleName)
        }
    }

    /** True when the user has allowed this app to install packages ("Install unknown apps"). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Debug builds have a different package name and must not try to install release APKs. */
    val installSupported: Boolean get() = BuildConfig.APPLICATION_ID == RELEASE_APP_ID

    /**
     * Starts a PackageInstaller session. The result (including the system confirmation step) arrives
     * in [InstallResultReceiver] and is published on [InstallEvents].
     */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                // Mutable: the installer adds the status extras to this intent.
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw UpdateException(UpdateException.Kind.INSTALL, e.message ?: e.javaClass.simpleName)
        }
    }

    fun clearDownloads() {
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun lastCheck(): String? = db.settingsDao().get(SettingKeys.LAST_UPDATE_CHECK)

    private suspend fun recordCheck(result: String) {
        db.settingsDao().put(SettingEntity(SettingKeys.LAST_UPDATE_CHECK, "${Instant.now()} $result"))
    }

    /** The APK must be ours, newer than the installed app, and signed with the same certificate. */
    private fun verifyApk(apk: File, manifest: UpdateManifest) {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Signing details need API 28; the system installer still rejects a different signer.
            @Suppress("DEPRECATION")
            val archive = pm.getPackageArchiveInfo(apk.path, 0)
                ?: throw UpdateException(UpdateException.Kind.WRONG_APK, "not a valid APK")
            if (archive.packageName != context.packageName) {
                throw UpdateException(UpdateException.Kind.WRONG_APK, "package ${archive.packageName}")
            }
            return
        }
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive: PackageInfo = pm.getPackageArchiveInfo(apk.path, flags)
            ?: throw UpdateException(UpdateException.Kind.WRONG_APK, "not a valid APK")
        if (archive.packageName != context.packageName) {
            throw UpdateException(UpdateException.Kind.WRONG_APK, "package ${archive.packageName}")
        }
        if (archive.longVersionCode != manifest.versionCode) {
            throw UpdateException(UpdateException.Kind.WRONG_APK, "versionCode ${archive.longVersionCode} != ${manifest.versionCode}")
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val newCerts = archive.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet().orEmpty()
        val oldCerts = installed.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet().orEmpty()
        if (newCerts.isEmpty() || newCerts != oldCerts) {
            throw UpdateException(UpdateException.Kind.WRONG_APK, "signing certificate differs")
        }
    }

    private fun httpGet(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true // github.com -> release asset CDN
        conn.setRequestProperty("Accept", "application/octet-stream, application/json")
        conn.setRequestProperty("User-Agent", "mimikopi/${BuildConfig.VERSION_NAME}")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return conn
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try { block(this) } finally { disconnect() }

    companion object {
        const val RELEASE_APP_ID = "com.id.mimikopi"

        /**
         * The phone's update file: the release workflow keeps the newest latest.json on the fixed release "phone-update"
         * of the source repository (the Windows release is the one marked latest there).
         */
        const val LATEST_JSON_URL =
            "https://github.com/coolza254-lgtm/mimikopi/releases/download/phone-update/latest.json"
    }
}
