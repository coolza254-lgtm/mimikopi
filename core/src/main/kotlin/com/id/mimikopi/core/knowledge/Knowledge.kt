package com.id.mimikopi.core.knowledge

import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.theory.Chord
import com.id.mimikopi.core.theory.StageGenerators
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A tap-to-hear example in an article:
 * "notes:C4 E4 G4" (one after another), "chord:Cmaj7" (together), "chords:C G Am F" (a chord per beat pair),
 * "prog:0:IVmaj7 V7 iii7 vi" (roman numerals in a key given as fifths).
 */
@Serializable
data class Example(val label: String, val play: String)

@Serializable
data class ArticleSection(val heading: String = "", val text: String, val examples: List<Example> = emptyList())

/** One page of the pop knowledge library (Thai). Bundled, original text; no song content. */
@Serializable
data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val tags: List<String> = emptyList(),
    /** Lessons that teach this topic. */
    val lessons: List<String> = emptyList(),
    val sections: List<ArticleSection>,
) {
    fun problems(): List<String> = buildList {
        if (title.isBlank() || summary.isBlank() || sections.isEmpty()) add("$id: incomplete")
        sections.flatMap { it.examples }.forEach { e -> if (Knowledge.sequence(e.play) == null) add("$id: bad example ${e.play}") }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }
        fun parse(text: String): Article = json.decodeFromString(serializer(), text)
    }
}

object Knowledge {
    /**
     * An example as a list of steps; each step is MIDI notes sounding together. Null when it cannot be read.
     */
    fun sequence(play: String): List<List<Int>>? = runCatching {
        val kind = play.substringBefore(':')
        val body = play.substringAfter(':')
        when (kind) {
            "notes" -> body.trim().split(Regex("""\s+""")).map { listOf(SpelledPitch.parse(it).midi) }
            "chord" -> listOf(voicing(Chord.parse(body.trim())!!))
            "chords" -> body.trim().split(Regex("""\s+""")).map { voicing(Chord.parse(it)!!) }
            "prog" -> {
                val fifths = body.substringBefore(':').toInt()
                body.substringAfter(':').trim().split(Regex("""\s+""")).map { voicing(StageGenerators.degreeChord(it, fifths)) }
            }
            else -> null
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() && it.all { s -> s.isNotEmpty() } }

    /** Close voicing around Middle C with the bass an octave below the root (or slash bass). */
    private fun voicing(c: Chord): List<Int> {
        val upper = c.inversions(55).first()
        var bass = (c.bass ?: c.root).pc
        while (bass < 40) bass += 12
        while (bass >= 52) bass -= 12
        return (listOf(bass) + upper).distinct()
    }

    /** Articles matching every word of [query] in title, summary, tags or text; titles matches first. */
    fun search(articles: List<Article>, query: String): List<Article> {
        val words = query.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return articles
        fun hay(a: Article) = (listOf(a.title, a.summary) + a.tags + a.sections.flatMap { listOf(it.heading, it.text) }).joinToString(" ").lowercase()
        return articles.filter { a -> val h = hay(a); words.all { it in h } }
            .sortedByDescending { a -> words.count { it in a.title.lowercase() || a.tags.any { t -> it in t.lowercase() } } }
    }
}
