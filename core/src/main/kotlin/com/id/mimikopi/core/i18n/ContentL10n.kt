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
 * read ([FIELDS]): ids, actions and lookup names stay as written. A text with no translation stays Thai. Thai has a small
 * table too (content-th.json): the shown wording without chapter and level numbers (user, 2026-10-23).
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
        val t = table(L10n.lang)
        if (t.isEmpty()) return text
        return walk(Json.parseToJsonElement(text), t, false).toString()
    }

    private fun walk(e: JsonElement, t: Map<String, String>, readable: Boolean): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { (k, v) -> walk(v, t, k in FIELDS) })
        is JsonArray -> JsonArray(e.map { walk(it, t, readable) })
        is JsonPrimitive -> if (readable && e.isString) t[e.content]?.let { JsonPrimitive(it) } ?: e else e
    }
}

/** The lesson ladder's stages, shown by name rather than number (user, 2026-10-23). */
object Stages {
    fun name(stage: Int): String = when (stage) {
        1 -> tr("คีย์บอร์ด จังหวะ และการอ่านเบื้องต้น", "Keyboard, rhythm and first reading", "鍵盤、リズム、読譜の基礎")
        2 -> tr("อ่านโน้ตและมือขวา", "Note reading and the right hand", "読譜と右手")
        3 -> tr("มือซ้ายและสองมือ", "Left hand and both hands", "左手と両手")
        4 -> tr("สองมือ สเกล และเพลงพื้นฐาน", "Both hands, scales and first pieces", "両手、スケール、はじめての曲")
        else -> tr("ด่านถัดไป", "Next stage", "次の段階")
    }
}
