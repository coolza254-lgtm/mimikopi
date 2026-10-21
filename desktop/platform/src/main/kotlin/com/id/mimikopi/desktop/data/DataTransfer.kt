package com.id.mimikopi.desktop.data

import com.id.mimikopi.core.backup.BackupCheck
import com.id.mimikopi.core.backup.BackupFile
import com.id.mimikopi.core.backup.BackupValidator
import com.id.mimikopi.core.backup.SettingRow
import com.id.mimikopi.core.sync.HistoryMerge
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.core.sync.SyncPayload
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What importing a file did, in words for the person. */
data class ImportReport(val lines: List<String>, val safetyCopy: File?)

/**
 * Backups and moving data between the phone and this computer, all through files the person picks:
 *
 * - [export]: everything in the Android backup format (the phone restores it as it restores its own).
 * - [merge]: adds a phone (or another computer) backup to what is here: progress rows by the shared ProgressMerge
 *   rules, review items, and practice history row by row (HistoryMerge). Nothing here is lost; importing the same
 *   file again changes nothing. Private songs and device settings never travel this way.
 * - [restore]: replaces everything with a backup (moving to a new computer).
 *
 * A safety copy of the current data is written before any import, and automatic copies are kept every day.
 */
class DataTransfer(
    private val db: Database,
    private val dirs: AppDirs,
    private val appVersion: String,
    private val intervalDays: (Int) -> Int,
) {
    fun snapshot(): BackupFile {
        val data = db.export()
        // Names the sending computer, so merging its file elsewhere keeps its practice log apart from others.
        val settings = data.settings.filter { it.key != SOURCE_KEY } + SettingRow(SOURCE_KEY, "${dirs.deviceId}|PC")
        return BackupFile(
            applicationId = APPLICATION_ID,
            appVersionName = "$appVersion-pc",
            appVersionCode = 0,
            databaseVersion = Database.VERSION,
            createdAtUtc = Instant.now().toString(),
            data = data.copy(settings = settings.sortedBy { it.key }),
        )
    }

    fun export(target: File): Int {
        val backup = snapshot()
        writeAtomically(target, backup.toJson())
        return backup.data.totalRows
    }

    /** An automatic copy in the backups folder; keeps the newest [KEEP_AUTO]. */
    fun autoBackup(reason: String): File {
        val stamp = Instant.now().toString().replace(":", "").replace(".", "")
        val file = File(dirs.backups, "auto-$stamp-$reason.json")
        writeAtomically(file, snapshot().toJson())
        autoBackups().drop(KEEP_AUTO).forEach { it.delete() }
        return file
    }

    /** One automatic copy per day, made when the program starts. */
    fun dailyBackupIfDue(today: LocalDate = LocalDate.now()): File? {
        val last = autoBackups().firstOrNull { it.name.contains("-daily") }?.lastModified()
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        if (last == today) return null
        return autoBackup("daily")
    }

    fun autoBackups(): List<File> =
        dirs.backups.listFiles { f -> f.name.startsWith("auto-") && f.name.endsWith(".json") }?.sortedByDescending { it.name }.orEmpty()

    fun read(file: File): BackupCheck {
        if (file.length() > MAX_FILE_BYTES) return BackupCheck.Rejected("ไฟล์ใหญ่เกิน ${MAX_FILE_BYTES shr 20} MB")
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { return BackupCheck.Rejected("อ่านไฟล์ไม่ได้: ${it.message}") }
        return when (val c = BackupValidator.check(text, setOf(APPLICATION_ID, "$APPLICATION_ID.debug"), Database.VERSION)) {
            is BackupCheck.Ok -> c
            is BackupCheck.Rejected -> BackupCheck.Rejected("ไม่ใช่ไฟล์สำรองที่ใช้ได้: ${c.reason}")
        }
    }

    /** Adds [backup] to this computer's data. */
    fun merge(backup: BackupFile): ImportReport {
        val safety = autoBackup("before-merge")
        val local = db.export()
        val remoteSettings = backup.data.settings.associate { it.key to it.value }
        val source = remoteSettings[SOURCE_KEY]?.substringBefore('|')?.takeIf { it.isNotBlank() && it != dirs.deviceId } ?: "phone-backup"
        val result = ProgressMerge.merge(
            SyncPayload(deviceId = dirs.deviceId, deviceName = "PC", appVersion = appVersion,
                settings = ProgressMerge.progressRows(local.settings.associate { it.key to it.value }), itemStats = local.itemStats),
            SyncPayload(deviceId = source, deviceName = "backup", appVersion = backup.appVersionName,
                settings = ProgressMerge.progressRows(remoteSettings), itemStats = backup.data.itemStats),
            intervalDays,
        )
        val plan = HistoryMerge.plan(local, backup.data)
        var added = 0
        db.transaction {
            db.putAll(result.settings)
            db.putItemStats(result.itemStats)
            added = db.applyHistory(plan)
        }
        val lines = buildList {
            add(if (added > 0) "เพิ่มประวัติการเล่น $added รอบ" else "ประวัติการเล่นในไฟล์มีอยู่แล้วทั้งหมด")
            if (plan.newSessions.isNotEmpty()) add("เพิ่มช่วงฝึก ${plan.newSessions.size} ครั้ง")
            add(if (result.settings.isNotEmpty()) "รวมความคืบหน้า ${result.settings.size} รายการ" else "ความคืบหน้าตรงกันแล้ว")
            if (result.itemStats.isNotEmpty()) add("อัปเดตรายการทบทวน ${result.itemStats.size} รายการ")
            add("ไม่ได้นำเพลงส่วนตัวและการตั้งค่าเครื่องมารวม")
        }
        return ImportReport(lines, safety)
    }

    /** Replaces this computer's data with [backup] (a safety copy is kept first). */
    fun restore(backup: BackupFile): ImportReport {
        val safety = autoBackup("before-restore")
        db.replaceAll(backup.data)
        return ImportReport(listOf("แทนที่ข้อมูลด้วยไฟล์สำรองแล้ว (${backup.data.totalRows} แถว)"), safety)
    }

    private fun writeAtomically(target: File, text: String) {
        target.absoluteFile.parentFile?.mkdirs()
        val tmp = File(target.absoluteFile.parentFile, target.name + ".part")
        tmp.writeText(text, Charsets.UTF_8)
        runCatching {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse { tmp.delete(); throw IOException("เขียนไฟล์ ${target.name} ไม่ได้: ${it.message}", it) }
    }

    companion object {
        /** The Android app's id: backups from either program are accepted by both. */
        const val APPLICATION_ID = "com.id.mimikopi"
        /** Settings row naming the computer a backup came from ("<device id>|PC"). */
        const val SOURCE_KEY = "sync.source"
        const val KEEP_AUTO = 10
        const val MAX_FILE_BYTES = 256L shl 20
    }
}
