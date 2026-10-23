package com.id.mimikopi.desktop.data

import java.io.File
import java.util.UUID

/**
 * Where the program keeps its files: %APPDATA%\MimikopiPC on Windows (~/.local/share/MimikopiPC elsewhere, or
 * the folder in MIMIKOPI_DATA for tests and trial runs). Nothing here is ever sent anywhere.
 */
class AppDirs(val root: File) {
    val database: File get() = File(root, "mimikopi.db")
    val backups: File get() = File(root, "backups").apply { mkdirs() }
    val samples: File get() = File(root, "samples").apply { mkdirs() }
    val logs: File get() = File(root, "logs").apply { mkdirs() }

    /** A random id of this computer for merging progress; kept outside the database so a restore never copies it. */
    val deviceId: String by lazy {
        val f = File(root, "device-id")
        f.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { id -> root.mkdirs(); f.writeText(id) }
    }

    companion object {
        fun default(): AppDirs {
            System.getenv("MIMIKOPI_DATA")?.takeIf { it.isNotBlank() }?.let { return AppDirs(File(it)) }
            val appData = System.getenv("APPDATA")
            val root = if (!appData.isNullOrBlank()) File(appData, "MimikopiPC")
            else File(System.getProperty("user.home"), ".local/share/MimikopiPC")
            return AppDirs(root)
        }
    }
}
