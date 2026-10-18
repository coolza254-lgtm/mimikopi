package com.id.mimikopi.sync

import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import com.id.mimikopi.BuildConfig
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.sync.LanSync
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.core.sync.SyncException
import com.id.mimikopi.core.sync.SyncPayload
import com.id.mimikopi.data.db.ItemStatEntity
import com.id.mimikopi.data.db.SettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.UUID

/** What a sync did, for the sync page. */
data class SyncOutcome(val from: String, val changed: Int)

/**
 * The phone's side of phone-to-PC sync. Over Wi-Fi the phone waits for the PC on a free port ([open] then [serve]);
 * by file it exports its progress or merges a file from the PC. Either way the other device's progress is merged in by
 * [ProgressMerge] (nothing replaced, device settings never touched), after an automatic backup, and the pages reload.
 */
class SyncService(private val app: MimikopiApp) {
    private val mutex = Mutex()

    /** This phone's sync id, in its own file (not in the database, so a backup restore never copies another device's id). */
    val deviceId: String by lazy {
        val f = File(app.filesDir, "device-id")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also { f.writeText(it) }
    }

    suspend fun snapshot(): SyncPayload = withContext(Dispatchers.IO) {
        val dao = app.database.settingsDao()
        val settings = ProgressMerge.KEYS.mapNotNull { k -> dao.get(k)?.let { k to it } }.toMap()
        val items = app.database.learningDao().itemStats().map { ItemStatRow(it.itemId, it.box, it.dueAtMillis, it.lastResults, it.medianResponseMs) }
        SyncPayload(deviceId = deviceId, deviceName = "มือถือ ${Build.MODEL}", appVersion = BuildConfig.VERSION_NAME, settings = settings, itemStats = items.sortedBy { it.itemId })
    }

    /** Merges [remote] into this phone's progress (one transaction, a backup first) and reloads the pages. */
    suspend fun merge(remote: SyncPayload): SyncOutcome = mutex.withLock {
        if (remote.deviceId == deviceId) throw SyncException(SyncException.Kind.BAD_DATA, "this file came from this phone")
        val r = ProgressMerge.merge(snapshot(), remote, app.learning.leitner::intervalDays)
        if (r.changed) {
            withContext(Dispatchers.IO) {
                app.backupService.autoBackup("before-sync")
                app.database.withTransaction {
                    val dao = app.database.settingsDao()
                    r.settings.forEach { (k, v) -> dao.put(SettingEntity(k, v)) }
                    val learning = app.database.learningDao()
                    r.itemStats.forEach { learning.putItemStat(ItemStatEntity(it.itemId, it.box, it.dueAt, it.lastResults, it.medianResponseMs)) }
                }
            }
            app.learning.reload()
            app.pieces.reload()
            app.progress.reload()
        }
        SyncOutcome(remote.deviceName, r.settings.size + r.itemStats.size)
    }

    /** A listener on a free port of every interface, for one PC; close it to stop waiting. */
    fun open(): ServerSocket = ServerSocket(0, 1)

    /** Waits for the PC on [server] (up to [WAIT_MS]), exchanges progress and merges the PC's. */
    suspend fun serve(server: ServerSocket, code: String): SyncOutcome {
        val theirs = withContext(Dispatchers.IO) { LanSync.serve(server, code, WAIT_MS) { runBlocking { snapshot() } } }
        return merge(theirs)
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("cannot open $uri")
        out.use { it.write(snapshot().toJson().toByteArray(Charsets.UTF_8)) }
    }

    suspend fun importFrom(uri: Uri): SyncOutcome {
        val text = withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw IOException("cannot open $uri")
        }
        return merge(SyncPayload.fromJson(text) ?: throw SyncException(SyncException.Kind.BAD_DATA, "not a sync file"))
    }

    companion object {
        const val WAIT_MS = 3 * 60 * 1000

        /** Thai words for a failed sync. */
        fun explain(e: Throwable): String = when ((e as? SyncException)?.kind) {
            SyncException.Kind.WRONG_CODE -> "คอมใส่รหัสไม่ตรง กดเริ่มใหม่เพื่อรับรหัสใหม่"
            SyncException.Kind.NO_CONNECTION -> "การเชื่อมต่อหลุด ลองใหม่อีกครั้ง"
            SyncException.Kind.TIMEOUT -> "ไม่มีคอมเชื่อมต่อภายใน 3 นาที กดเริ่มใหม่ได้เลย"
            SyncException.Kind.VERSION -> "แอพบนมือถือกับโปรแกรมบนคอมเป็นคนละรุ่น อัปเดตทั้งสองเครื่องก่อน"
            SyncException.Kind.BAD_DATA -> "ไฟล์หรือข้อมูลอ่านไม่ได้ (${e.message})"
            null -> "ซิงค์ไม่สำเร็จ: ${e.message}"
        }
    }
}
