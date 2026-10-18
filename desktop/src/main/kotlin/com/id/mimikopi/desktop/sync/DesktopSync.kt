package com.id.mimikopi.desktop.sync

import com.id.mimikopi.core.sync.LanSync
import com.id.mimikopi.core.sync.MergeResult
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.core.sync.SyncException
import com.id.mimikopi.core.sync.SyncPayload
import com.id.mimikopi.desktop.Services
import java.io.File
import java.util.UUID

/** What a sync did, for the settings page. */
data class SyncOutcome(val from: String, val changed: Int)

/**
 * The PC's side of phone-to-PC sync: connects to the address and code the phone shows (outbound only, so Windows
 * asks nothing), or reads a sync file the phone exported. Either way the phone's progress is merged into this
 * computer's by [ProgressMerge]; nothing is replaced and device settings are never touched.
 */
object DesktopSync {
    /** This computer's sync id, in its own file next to data.json (so importing a backup never copies another device's id). */
    val deviceId: String by lazy {
        val f = File(Services.dir, "device-id")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { f.parentFile?.mkdirs(); f.writeText(it) }
    }

    private val lock = Any()

    fun snapshot(): SyncPayload {
        val (settings, items) = Services.store.snapshot(ProgressMerge.KEYS)
        val name = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() } ?: "คอม"
        return SyncPayload(deviceId = deviceId, deviceName = name, appVersion = System.getProperty("jpackage.app-version") ?: "dev", settings = settings, itemStats = items)
    }

    /** Merges [remote] into this computer's progress and refreshes what the pages show. */
    fun merge(remote: SyncPayload): SyncOutcome = synchronized(lock) {
        if (remote.deviceId == deviceId) throw SyncException(SyncException.Kind.BAD_DATA, "this file came from this computer")
        val r: MergeResult = ProgressMerge.merge(snapshot(), remote, Services.learning.leitner::intervalDays)
        if (r.changed) {
            Services.store.applyMerge(r.settings, r.itemStats)
            Services.learning.reload()
            Services.pieces.reload()
            kotlinx.coroutines.runBlocking { com.id.mimikopi.desktop.port.MimikopiApp.progress.reload() }
        }
        SyncOutcome(remote.deviceName, r.settings.size + r.itemStats.size)
    }

    /** Wi-Fi: "192.168.1.23:48123" and the 6-digit code from the phone. Blocking: call off the UI thread. */
    fun connect(address: String, code: String): SyncOutcome {
        val (host, port) = LanSync.parseAddress(address) ?: throw SyncException(SyncException.Kind.NO_CONNECTION, "bad address")
        val theirs = LanSync.connect(host, port, code.filter { it.isDigit() }, { snapshot() })
        return merge(theirs)
    }

    fun exportTo(file: File) = file.writeText(snapshot().toJson())

    fun importFrom(file: File): SyncOutcome =
        merge(SyncPayload.fromJson(file.readText()) ?: throw SyncException(SyncException.Kind.BAD_DATA, "not a sync file"))

    /** Thai words for a failed sync. */
    fun explain(e: Throwable): String = when ((e as? SyncException)?.kind) {
        SyncException.Kind.WRONG_CODE -> "รหัสไม่ตรง ดูรหัส 6 หลักบนมือถืออีกครั้ง (กดเริ่มใหม่บนมือถือจะได้รหัสใหม่)"
        SyncException.Kind.NO_CONNECTION -> "เชื่อมต่อมือถือไม่ได้: ตรวจว่าที่อยู่ถูก มือถือเปิดหน้าซิงค์ค้างไว้ และอยู่ Wi-Fi วงเดียวกัน (Wi-Fi สำหรับแขกบางที่ห้ามเครื่องคุยกัน)"
        SyncException.Kind.TIMEOUT -> "มือถือไม่ตอบ ลองกดเริ่มซิงค์บนมือถือใหม่"
        SyncException.Kind.VERSION -> "แอพบนมือถือกับโปรแกรมบนคอมเป็นคนละรุ่น อัปเดตทั้งสองเครื่องก่อน"
        SyncException.Kind.BAD_DATA -> "ไฟล์หรือข้อมูลอ่านไม่ได้ (${e.message})"
        null -> "ซิงค์ไม่สำเร็จ: ${e.message}"
    }
}
