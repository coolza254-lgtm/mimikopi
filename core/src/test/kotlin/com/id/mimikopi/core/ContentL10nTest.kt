package com.id.mimikopi.core

import com.id.mimikopi.core.i18n.ContentL10n
import com.id.mimikopi.core.i18n.L10n
import com.id.mimikopi.core.i18n.Lang
import com.id.mimikopi.core.lesson.Lesson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentL10nTest {
    private fun contentDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "content") }.first { it.isDirectory }

    @Test
    fun everyReadableTextHasEnglishAndJapanese() {
        val thai = Regex("[฀-๿]")
        for (lang in listOf(Lang.EN, Lang.JA)) {
            L10n.lang = lang
            try {
                contentDir().walk().filter { it.extension == "json" && it.parentFile.name != "config" }.forEach { f ->
                    val out = ContentL10n.translate(f.readText())
                    val left = ContentL10n.FIELDS.flatMap { k -> Regex("\"$k\":(\"(?:[^\"\\\\]|\\\\.)*\"|\\[[^\\]]*\\])").findAll(out).map { it.value } }.filter { thai.containsMatchIn(it) }
                    assertTrue(left.isEmpty(), "${f.name} ($lang) still Thai: ${left.take(2)}")
                }
            } finally { L10n.lang = Lang.TH }
        }
    }

    @Test
    fun idsStayAndThaiIsUnchanged() {
        val f = File(contentDir(), "lessons/1.1.json").readText()
        val th = Lesson.parse(f)
        L10n.lang = Lang.JA
        try {
            val ja = Lesson.parse(f)
            assertEquals(th.id, ja.id)
            assertEquals("鍵盤の地図：C、F、G", ja.title)
        } finally { L10n.lang = Lang.TH }
        assertEquals(th, Lesson.parse(f))
        // Thai shows lesson titles without level numbers.
        contentDir().resolve("lessons").listFiles()!!.forEach { assertTrue(!Regex(" L\\d").containsMatchIn(Lesson.parse(it.readText()).title), it.name) }
    }
}
