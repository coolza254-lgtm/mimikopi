package com.id.mimikopi.desktop.data

import com.id.mimikopi.core.backup.BackupData
import com.id.mimikopi.core.backup.BackupFile
import com.id.mimikopi.core.backup.BackupValidator
import com.id.mimikopi.core.backup.BackupCheck
import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.backup.SettingRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Everything the desktop app keeps, in one JSON file in the user's app-data folder (%APPDATA%\Mimikopi on Windows).
 * The same key/value settings rows as the Android settings table, plus spaced-repetition item stats, so an Android
 * backup can be imported as it is and a desktop backup restored on the phone. Writes go to a temp file then replace
 * the old one, so a crash never leaves a half-written file. Nothing leaves the computer.
 */
class Store(private val file: File) {
    @Serializable
    private data class Content(val settings: Map<String, String> = emptyMap(), val itemStats: Map<String, ItemStatRow> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val lock = Any()
    private var content: Content = load()

    private fun load(): Content = runCatching { if (file.exists()) json.decodeFromString(Content.serializer(), file.readText()) else null }
        .getOrNull() ?: Content()

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Content.serializer(), content))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun get(key: String): String? = synchronized(lock) { content.settings[key] }

    fun put(key: String, value: String) = synchronized(lock) {
        content = content.copy(settings = content.settings + (key to value))
        save()
    }

    fun delete(key: String) = synchronized(lock) {
        content = content.copy(settings = content.settings - key)
        save()
    }

    fun itemStats(): Map<String, ItemStatRow> = synchronized(lock) { content.itemStats }

    fun putItemStat(row: ItemStatRow) = synchronized(lock) {
        content = content.copy(itemStats = content.itemStats + (row.itemId to row))
        save()
    }

    // ---------------- backups (the Android format, so data moves both ways) ----------------

    /** A backup in the Android app's format: settings rows and item stats (sessions and attempts stay empty). */
    fun exportBackup(appVersion: String): String = synchronized(lock) {
        val settings = content.settings.map { (k, v) -> SettingRow(k, v) }
        val stats = content.itemStats.values.toList()
        BackupFile(
            applicationId = APPLICATION_ID, appVersionName = appVersion, appVersionCode = 0, databaseVersion = DATABASE_VERSION,
            createdAtUtc = Instant.now().toString(),
            data = BackupData(itemStats = stats, settings = settings, totalRows = settings.size + stats.size),
        ).toJson()
    }

    /** Replaces everything with a backup's settings and item stats. Returns null when done, or why it was refused. */
    fun importBackup(text: String): String? {
        val backup = when (val c = BackupValidator.check(text, setOf(APPLICATION_ID, "$APPLICATION_ID.debug"), DATABASE_VERSION)) {
            is BackupCheck.Ok -> c.backup
            is BackupCheck.Rejected -> return c.reason
        }
        synchronized(lock) {
            content = Content(
                settings = backup.data.settings.associate { it.key to it.value },
                itemStats = backup.data.itemStats.associateBy { it.itemId },
            )
            save()
        }
        return null
    }

    companion object {
        /** The Android app's id: backups from either app are accepted by both. */
        const val APPLICATION_ID = "com.id.mimikopi"
        /** Android Room schema version the settings rows belong to. */
        const val DATABASE_VERSION = 1

        /** %APPDATA%\Mimikopi on Windows, ~/.mimikopi elsewhere. */
        fun defaultDir(): File {
            val appData = System.getenv("APPDATA")
            return if (!appData.isNullOrBlank()) File(appData, "Mimikopi") else File(System.getProperty("user.home"), ".mimikopi")
        }
    }
}
