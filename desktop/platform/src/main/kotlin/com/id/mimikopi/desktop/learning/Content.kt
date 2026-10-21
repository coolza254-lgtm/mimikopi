package com.id.mimikopi.desktop.learning

/** The bundled content (the Android app's content/ folder) read from the classpath. */
object Content {
    private val index: List<String> by lazy {
        read("content/index.txt")?.lines()?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun read(path: String): String? =
        Content::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

    /** Files directly in [folder] ("lessons", "pieces", ...), sorted by name. */
    fun list(folder: String): List<String> = index.filter { it.startsWith("$folder/") && '/' !in it.removePrefix("$folder/") }
}
