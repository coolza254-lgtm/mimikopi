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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Everything the desktop app keeps, in one JSON file in the user's app-data folder (%APPDATA%\Mimikopi on Windows).
 * The same key/value settings rows as the Android settings table, plus spaced-repetition item stats, so an Android
 * backup can be imported as it is and a desktop backup restored on the phone. Writes go to a temp file then replace
 * the old one, so a crash never leaves a half-written file. Nothing leaves the computer.
 *
 * Changes are saved shortly after they are made on a background thread (several changes in a row share one write),
 * so the screens never wait for the disk; [flush] writes at once (closing the window, and at shutdown).
 */
class Store(private val file: File) {
    @Serializable
    private data class Content(val settings: Map<String, String> = emptyMap(), val itemStats: Map<String, ItemStatRow> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val lock = Any()
    private var content: Content = load()
    /** Guarded by [lock]: changes not written yet, and the background save that will write them. */
    private var dirty = false
    private var pending: ScheduledFuture<*>? = null
    /** Held for every disk access, so writes (and the pre-sync copy) never overlap. Taken before [lock], never inside it. */
    private val diskLock = Any()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ runCatching { flush() } }, "mimikopi-store-flush"))
    }

    private fun load(): Content = runCatching { if (file.exists()) json.decodeFromString(Content.serializer(), file.readText()) else null }
        .getOrNull() ?: Content()

    private fun save(snapshot: Content) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Content.serializer(), snapshot))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Called under [lock] after a change: the background save is due [SAVE_DELAY_MS] after the first unsaved change. */
    private fun saveLater() {
        dirty = true
        if (pending == null) pending = writer.schedule(Runnable { runCatching { flush() } }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    /** Writes the unsaved changes now, if there are any. A failed write keeps them for the next try and rethrows. */
    fun flush() {
        synchronized(diskLock) {
            val snapshot = synchronized(lock) {
                pending?.cancel(false)
                pending = null
                if (!dirty) return
                dirty = false
                content
            }
            try {
                save(snapshot)
            } catch (e: Exception) {
                synchronized(lock) { dirty = true }
                throw e
            }
        }
    }

    fun get(key: String): String? = synchronized(lock) { content.settings[key] }

    fun put(key: String, value: String) = synchronized(lock) {
        content = content.copy(settings = content.settings + (key to value))
        saveLater()
    }

    fun delete(key: String) = synchronized(lock) {
        content = content.copy(settings = content.settings - key)
        saveLater()
    }

    fun itemStats(): Map<String, ItemStatRow> = synchronized(lock) { content.itemStats }

    fun putItemStat(row: ItemStatRow) = synchronized(lock) {
        content = content.copy(itemStats = content.itemStats + (row.itemId to row))
        saveLater()
    }

    /** The settings rows named in [keys] and every review item, read in one step (what a sync sends). */
    fun snapshot(keys: Set<String>): Pair<Map<String, String>, List<ItemStatRow>> = synchronized(lock) {
        content.settings.filterKeys { it in keys } to content.itemStats.values.sortedBy { it.itemId }
    }

    /**
     * Writes a sync's merged rows and items in one step and one save, on disk before it returns; a copy of the file
     * as it was is kept first (data.before-sync.json), so a bad sync can be undone by hand.
     */
    fun applyMerge(settings: Map<String, String>, items: List<ItemStatRow>) {
        if (settings.isEmpty() && items.isEmpty()) return
        synchronized(diskLock) {
            flush() // the copy below then holds every change made before the sync
            if (file.exists()) runCatching { file.copyTo(File(file.parentFile, "data.before-sync.json"), overwrite = true) }
            synchronized(lock) {
                content = content.copy(settings = content.settings + settings, itemStats = content.itemStats + items.associateBy { it.itemId })
                dirty = true
            }
            flush()
        }
    }

    // ---------------- backups (the Android format, so data moves both ways) ----------------

    /** A backup in the Android app's format: settings rows and item stats (sessions and attempts stay empty). */
    fun exportBackup(appVersion: String): String = synchronized(lock) {
        val settings = content.settings.map { (k, v) -> SettingRow(k, v) }
        val stats = content.itemStats.values.toList()
        BackupFile(
            applicationId = APPLICATION_ID, appVersionName = appVersion, appVersionCode = 0, databaseVersion = DATABASE_VERSION,
            createdAtUtc = Instant.now().toString(),
            data = BackupData(itemStats = stats, settings = settings),
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
            dirty = true
        }
        flush()
        return null
    }

    companion object {
        /** The Android app's id: backups from either app are accepted by both. */
        const val APPLICATION_ID = "com.id.mimikopi"
        /** Android Room schema version the settings rows belong to. */
        const val DATABASE_VERSION = 1

        /** How long after a change it is written, so a burst of changes is written once. */
        private const val SAVE_DELAY_MS = 250L

        /** One background thread writes every store; a daemon, so it never keeps the program running. */
        private val writer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "mimikopi-store").apply { isDaemon = true }
        }

        /** %APPDATA%\Mimikopi on Windows, ~/.mimikopi elsewhere. */
        fun defaultDir(): File {
            val appData = System.getenv("APPDATA")
            return if (!appData.isNullOrBlank()) File(appData, "Mimikopi") else File(System.getProperty("user.home"), ".mimikopi")
        }
    }
}
