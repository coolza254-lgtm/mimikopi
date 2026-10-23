package com.id.mimikopi.core.sync

import com.id.mimikopi.core.i18n.tr
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

/**
 * Where the phone and the PC meet to sync (learning-system.md 2.11): the branch [BRANCH] of the app's own repository
 * (one repository for code, releases and sync), holding one file per device, `devices/<device id>.json` (a
 * [SyncPayload]). Each device writes only its own file and reads the others, so two devices never edit the same file
 * and there is nothing to conflict. The branch is made on the first sync. The files hold practice progress only
 * (private songs stay on the PC). [token] is a fine-grained token with Contents read and write on [repo] only; it is
 * kept in a file of its own on each device, never in the database (so never in a backup or in the synced data).
 */
@Serializable
data class GitHubSyncConfig(val repo: String = DEFAULT_REPO, val token: String = "") {
    val ready: Boolean get() = token.isNotBlank() && REPO.matches(repo)

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val DEFAULT_REPO = "coolza254-lgtm/mimikopi"
        const val BRANCH = "sync-data"
        private val REPO = Regex("[A-Za-z0-9-]+/[A-Za-z0-9._-]+")
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String?): GitHubSyncConfig =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: GitHubSyncConfig()
    }
}

/** A sync that could not finish, with what to tell the person. */
class SyncFailure(val thai: String, cause: Throwable? = null) : IOException(thai, cause)

/** The devices' files in the data repository. */
interface SyncStore {
    /** Device id -> the file's version (to replace it), for every device file there. */
    fun list(): Map<String, String>
    fun read(deviceId: String): String?
    /** Writes this device's file; [version] from [list] when it exists. */
    fun write(deviceId: String, text: String, version: String?)
}

/** [SyncStore] on the GitHub contents API (plain java.net, so the phone and the PC use the same code). */
class GitHubContents(private val config: GitHubSyncConfig, private val api: String = "https://api.github.com") : SyncStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun list(): Map<String, String> {
        val (code, body) = call("GET", "contents/$DIR?ref=$BRANCH")
        if (code == 404) {
            // No sync branch or no device has synced yet: tell those apart from a missing repo, which needs fixing.
            val (repoCode, _) = call("GET", "")
            if (repoCode == 404) throw SyncFailure(tr("ไม่พบ repo ${config.repo} หรือ token เข้าถึงไม่ได้ ตรวจชื่อ repo และสิทธิ์ Contents ของ token", "Repo ${config.repo} not found or the token can't reach it; check the repo name and the token's Contents permission", "リポジトリ${config.repo}が見つからないか、トークンでアクセスできません。リポジトリ名とトークンのContents権限を確認してください"))
            // The first sync of all: make the branch now, so this device's file can be written to it.
            if (call("GET", "git/ref/heads/$BRANCH").first == 404) createBranch()
            return emptyMap()
        }
        check(code, body)
        return runCatching {
            json.parseToJsonElement(body).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!name.endsWith(".json")) return@mapNotNull null
                name.removeSuffix(".json") to (o["sha"]?.jsonPrimitive?.content ?: "")
            }.toMap()
        }.getOrElse { throw SyncFailure(tr("ข้อมูลจาก GitHub อ่านไม่ได้", "Couldn't read the data from GitHub", "GitHubのデータを読めません"), it) }
    }

    override fun read(deviceId: String): String? {
        val (code, body) = call("GET", "contents/$DIR/$deviceId.json?ref=$BRANCH", raw = true)
        if (code == 404) return null
        check(code, body)
        return body
    }

    override fun write(deviceId: String, text: String, version: String?) {
        val payload = buildJsonObject {
            put("message", "sync $deviceId")
            put("branch", BRANCH)
            put("content", Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)))
            if (!version.isNullOrBlank()) put("sha", version)
        }
        var (code, body) = call("PUT", "contents/$DIR/$deviceId.json", body = payload.toString())
        if ((code == 404 || code == 422) && createBranch()) {
            val retry = call("PUT", "contents/$DIR/$deviceId.json", body = payload.toString())
            code = retry.first; body = retry.second
        }
        check(code, body)
    }

    /** Makes [BRANCH] from the default branch (the first sync of all); false when it could not. */
    private fun createBranch(): Boolean {
        val (repoCode, repoBody) = call("GET", "")
        if (repoCode != 200) return false
        val main = runCatching { json.parseToJsonElement(repoBody).jsonObject["default_branch"]?.jsonPrimitive?.content }.getOrNull() ?: return false
        val (refCode, refBody) = call("GET", "git/ref/heads/$main")
        if (refCode != 200) return false
        val sha = runCatching { json.parseToJsonElement(refBody).jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content }.getOrNull() ?: return false
        val (code, _) = call("POST", "git/refs", body = buildJsonObject { put("ref", "refs/heads/$BRANCH"); put("sha", sha) }.toString())
        return code == 201 || code == 422 // 422: another device made it meanwhile
    }

    private fun check(code: Int, body: String) {
        when {
            code in 200..299 -> Unit
            code == 401 -> throw SyncFailure(tr("token ใช้ไม่ได้หรือหมดอายุ สร้าง token ใหม่แล้วใส่อีกครั้ง", "The token is invalid or expired; make a new one and enter it again", "トークンが無効か期限切れです。新しく作って入力し直してください"))
            code == 403 || code == 404 -> throw SyncFailure(tr("token ไม่มีสิทธิ์เขียน ${config.repo} (ต้องการ Contents: Read and write)", "The token can't write to ${config.repo} (needs Contents: Read and write)", "トークンに${config.repo}への書き込み権限がありません（Contents: Read and writeが必要）"))
            code == 409 || code == 422 -> throw SyncFailure(tr("มีอีกเครื่องซิงค์พร้อมกัน ลองใหม่อีกครั้ง", "Another device is syncing at the same time; try again", "別の端末が同時に同期しています。もう一度お試しください"))
            else -> throw SyncFailure(tr("GitHub ตอบ $code: ${body.take(120)}", "GitHub replied $code: ${body.take(120)}", "GitHubの応答 $code：${body.take(120)}"))
        }
    }

    private fun call(method: String, path: String, raw: Boolean = false, body: String? = null): Pair<Int, String> {
        val url = "$api/repos/${config.repo}" + (if (path.isEmpty()) "" else "/$path")
        val c = try {
            URI(url).toURL().openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw SyncFailure(tr("ที่อยู่ repo ไม่ถูกต้อง", "Invalid repo address", "リポジトリのアドレスが正しくありません"), e)
        }
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Authorization", "Bearer ${config.token.trim()}")
            c.setRequestProperty("Accept", if (raw) "application/vnd.github.raw" else "application/vnd.github+json")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "mimikopi-sync")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            return code to (stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "")
        } catch (e: SyncFailure) {
            throw e
        } catch (e: IOException) {
            throw SyncFailure(tr("ต่อ GitHub ไม่ได้ ตรวจอินเทอร์เน็ตแล้วลองใหม่", "Couldn't reach GitHub; check the internet and try again", "GitHubに接続できません。インターネットを確認してもう一度"), e)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val DIR = "devices"
        const val BRANCH = GitHubSyncConfig.BRANCH
    }
}

/** What one sync did, for the settings page. */
data class SyncReport(val devices: List<String>, val changed: Int, val uploaded: Boolean) {
    /** One line in Thai. */
    fun text(): String = buildList {
        add(if (devices.isEmpty()) tr("ยังไม่มีเครื่องอื่นในที่ซิงค์", "No other device in the sync yet", "同期先にほかの端末はまだありません") else tr("รวมจาก ${devices.joinToString()}", "Merged from ${devices.joinToString()}", "${devices.joinToString()}から統合"))
        add(if (changed > 0) tr("อัปเดต $changed รายการ", "Updated $changed items", "${changed}件を更新") else tr("ความคืบหน้าตรงกันแล้ว", "Progress already matches", "進捗はすでに一致しています"))
        if (uploaded) add(tr("ส่งของเครื่องนี้ขึ้นแล้ว", "This device's data was sent", "この端末のデータを送信しました"))
    }.joinToString(" · ")
}

object GitHubSync {
    /**
     * One sync: merges every other device's file into this one ([apply] writes a [MergeResult], after a backup, and
     * reloads the pages), then writes this device's file when it differs from what is stored. [local] is read again
     * after each merge, so what goes up already holds what came down. Safe to run any number of times.
     */
    fun run(
        store: SyncStore,
        deviceId: String,
        local: () -> SyncPayload,
        intervalDays: (Int) -> Int,
        apply: (MergeResult) -> Unit,
    ): SyncReport {
        val files = store.list()
        val names = mutableListOf<String>()
        var changed = 0
        for (id in files.keys.sorted()) {
            if (id == deviceId) continue
            val remote = store.read(id)?.let(SyncPayload::fromJson) ?: continue
            names += remote.deviceName
            val result = ProgressMerge.merge(local(), remote, intervalDays)
            if (result.changed) {
                apply(result)
                changed += result.settings.size + result.itemStats.size
            }
        }
        val mine = local().toJson()
        val stored = if (deviceId in files) store.read(deviceId) else null
        val upload = stored != mine
        if (upload) store.write(deviceId, mine, files[deviceId])
        return SyncReport(names, changed, upload)
    }
}
