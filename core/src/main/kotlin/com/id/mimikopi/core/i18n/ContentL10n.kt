package com.id.mimikopi.core.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The editable content (content/: lessons, pieces, exercises) is written in Thai; its English and Japanese live in
 * i18n/content-<lang>.json, keyed by the Thai text. Files are translated as they are read, and only the fields people
 * read ([FIELDS]): ids, actions and lookup names stay as written. A text with no translation stays Thai.
 */
object ContentL10n {
    val FIELDS = setOf("title", "goal", "explain", "more", "tips", "passText", "text", "composer", "caveats", "source")

    private val tables = HashMap<Lang, Map<String, String>>()

    private fun table(lang: Lang): Map<String, String> = synchronized(tables) {
        tables.getOrPut(lang) {
            val text = ContentL10n::class.java.getResourceAsStream("/i18n/content-${lang.code}.json")?.use { it.readBytes().toString(Charsets.UTF_8) }
            text?.let { t -> Json.parseToJsonElement(t).jsonObject.mapValues { it.value.jsonPrimitive.content } } ?: emptyMap()
        }
    }

    /** [text] (a content file) with its readable fields in the language in use. */
    fun translate(text: String): String {
        val lang = L10n.lang
        if (lang == Lang.TH) return text
        val t = table(lang)
        if (t.isEmpty()) return text
        return walk(Json.parseToJsonElement(text), t, false).toString()
    }

    private fun walk(e: JsonElement, t: Map<String, String>, readable: Boolean): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { (k, v) -> walk(v, t, k in FIELDS) })
        is JsonArray -> JsonArray(e.map { walk(it, t, readable) })
        is JsonPrimitive -> if (readable && e.isString) t[e.content]?.let { JsonPrimitive(it) } ?: e else e
    }
}
