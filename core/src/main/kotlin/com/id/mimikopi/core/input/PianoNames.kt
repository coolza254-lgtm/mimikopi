package com.id.mimikopi.core.input

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What both apps call each instrument: the user's own name for it, else a known nicer name, else the name the
 * instrument reports. Names are kept by the reported name (the same on every connection of that kind), stored as one
 * JSON object in the setting [SETTING_KEY].
 */
object PianoNames {
    const val SETTING_KEY = "piano.names"
    const val MAX_LENGTH = 40

    /**
     * Reported names that are not what the instrument is: the user's Badger C-918 announces itself over Bluetooth
     * as "Dight Piano MiDi" (seen in their backup of 2026-10-22).
     */
    val KNOWN: Map<String, String> = mapOf("Dight Piano MiDi" to "Badger Playon")

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    fun display(reported: String, own: Map<String, String>): String {
        val key = reported.trim()
        return own[key]?.takeIf { it.isNotBlank() } ?: KNOWN[key] ?: reported
    }

    /** [own] with [reported] named [name]; a blank name returns it to the default ([KNOWN] or the reported name). */
    fun rename(own: Map<String, String>, reported: String, name: String): Map<String, String> {
        val key = reported.trim()
        val clean = name.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH)
        return if (clean.isEmpty() || clean == (KNOWN[key] ?: key)) own - key else own + (key to clean)
    }

    fun decode(text: String?): Map<String, String> =
        text?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() } ?: emptyMap()

    fun encode(own: Map<String, String>): String = json.encodeToString(mapSerializer, own)
}
