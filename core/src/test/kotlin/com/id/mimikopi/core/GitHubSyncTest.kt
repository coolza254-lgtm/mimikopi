package com.id.mimikopi.core

import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.sync.GitHubContents
import com.id.mimikopi.core.sync.GitHubSync
import com.id.mimikopi.core.sync.GitHubSyncConfig
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.core.sync.SyncFailure
import com.id.mimikopi.core.sync.SyncPayload
import com.id.mimikopi.core.sync.SyncStore
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubSyncTest {
    private val interval = { box: Int -> listOf(1, 2, 4, 8, 16)[box - 1] }
    private val lessonsSer = MapSerializer(String.serializer(), LessonRecord.serializer())

    /** The data repository in memory: device id -> (version, text). */
    private class MemoryStore : SyncStore {
        val files = linkedMapOf<String, Pair<String, String>>()
        var writes = 0
        override fun list() = files.mapValues { it.value.first }
        override fun read(deviceId: String) = files[deviceId]?.second
        override fun write(deviceId: String, text: String, version: String?) {
            assertEquals(files[deviceId]?.first, version, "replacing needs the stored version")
            writes++
            files[deviceId] = "v$writes" to text
        }
    }

    /** A device: its progress rows, applied as the apps do (rows from a merge result overwrite). */
    private class Device(val id: String, var settings: Map<String, String>) {
        fun payload() = SyncPayload(deviceId = id, deviceName = id, appVersion = "t", settings = settings)
    }

    private fun lessons(vararg passDays: Pair<String, String>) =
        Json.encodeToString(lessonsSer, passDays.groupBy({ it.first }, { it.second }).mapValues { (id, days) -> LessonRecord(id, passDays = days.toSet()) })

    private fun sync(store: SyncStore, d: Device) =
        GitHubSync.run(store, d.id, d::payload, interval) { r -> d.settings = d.settings + r.settings }

    @Test
    fun twoDevicesEndWithTheSameProgressAndASecondSyncChangesNothing() {
        val store = MemoryStore()
        val phone = Device("phone", mapOf(ProgressMerge.LESSONS to lessons("1.1" to "2026-10-21")))
        val pc = Device("pc", mapOf(ProgressMerge.LESSONS to lessons("1.3" to "2026-10-22")))

        val first = sync(store, phone)
        assertTrue(first.uploaded)
        assertEquals(emptyList(), first.devices)
        sync(store, pc)
        sync(store, phone)
        val records = Json.decodeFromString(lessonsSer, phone.settings.getValue(ProgressMerge.LESSONS))
        assertEquals(setOf("1.1", "1.3"), records.keys)
        assertEquals(phone.settings[ProgressMerge.LESSONS], pc.settings[ProgressMerge.LESSONS].let { sync(store, pc); pc.settings[ProgressMerge.LESSONS] })

        val writes = store.writes
        val again = sync(store, phone)
        assertEquals(0, again.changed)
        assertFalse(again.uploaded)
        assertEquals(writes, store.writes)
    }

    @Test
    fun theGitHubStoreUsesTheContentsApiWithTheToken() {
        val stored = linkedMapOf<String, Pair<String, String>>()
        val auth = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/repos/me/data") { ex ->
            auth += ex.requestHeaders.getFirst("Authorization")
            val path = ex.requestURI.path.removePrefix("/repos/me/data")
            val (code, body) = when {
                ex.requestMethod == "GET" && path == "" -> 200 to "{}"
                ex.requestMethod == "GET" && path == "/contents/devices" ->
                    if (stored.isEmpty()) 404 to "{}" else 200 to stored.entries.joinToString(",", "[", "]") { (id, f) -> """{"name":"$id.json","sha":"${f.first}"}""" }
                ex.requestMethod == "GET" && path.startsWith("/contents/devices/") -> {
                    val id = path.substringAfterLast('/').removeSuffix(".json")
                    stored[id]?.let { 200 to it.second } ?: (404 to "{}")
                }
                ex.requestMethod == "PUT" -> {
                    val id = path.substringAfterLast('/').removeSuffix(".json")
                    val o = Json.parseToJsonElement(ex.requestBody.readBytes().toString(Charsets.UTF_8)).jsonObject
                    if (o["sha"]?.jsonPrimitive?.content != stored[id]?.first) 409 to "{}"
                    else {
                        stored[id] = "sha${stored.size + 1}-${System.nanoTime()}" to String(Base64.getDecoder().decode(o.getValue("content").jsonPrimitive.content), Charsets.UTF_8)
                        201 to "{}"
                    }
                }
                else -> 400 to "{}"
            }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = "http://127.0.0.1:${server.address.port}"
            val store = GitHubContents(GitHubSyncConfig("me/data", "tok123"), api)
            assertEquals(emptyMap(), store.list())
            store.write("phone", "สวัสดี", null)
            val v = store.list().getValue("phone")
            assertEquals("สวัสดี", store.read("phone"))
            store.write("phone", "two", v)
            assertEquals("two", store.read("phone"))
            assertFailsWith<SyncFailure> { store.write("phone", "stale", v) }
            assertTrue(auth.all { it == "Bearer tok123" })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aMissingRepositoryOrBadTokenSaysSoInThai() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val code = if (ex.requestHeaders.getFirst("Authorization") == "Bearer good") 404 else 401
            ex.sendResponseHeaders(code, -1); ex.close()
        }
        server.start()
        try {
            val api = "http://127.0.0.1:${server.address.port}"
            val missing = assertFailsWith<SyncFailure> { GitHubContents(GitHubSyncConfig("me/none", "good"), api).list() }
            assertTrue(missing.thai.contains("ไม่พบ repo"))
            val bad = assertFailsWith<SyncFailure> { GitHubContents(GitHubSyncConfig("me/none", "bad"), api).list() }
            assertTrue(bad.thai.contains("token"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun configNeedsATokenAndAnOwnerSlashRepo() {
        assertFalse(GitHubSyncConfig().ready)
        assertTrue(GitHubSyncConfig(token = "x").ready)
        assertFalse(GitHubSyncConfig("no-slash", "x").ready)
        assertEquals(GitHubSyncConfig("a/b", "t"), GitHubSyncConfig.fromJson(GitHubSyncConfig("a/b", "t").toJson()))
        assertEquals(GitHubSyncConfig(), GitHubSyncConfig.fromJson("garbage"))
    }
}
