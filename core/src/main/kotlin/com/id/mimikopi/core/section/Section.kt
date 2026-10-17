package com.id.mimikopi.core.section

import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A song (or part of one) the user writes down, usually while watching a video of it: played note by note on the
 * MIDI piano or the on-screen keys, then practised part by part until it can be played through without a wrong note.
 * Only pitches and their order are kept: the user knows the rhythm from the song. Parts (phrases) can be named and
 * tied to a time in the video, and notes below [split] count as the left hand.
 */
@Serializable
data class Section(
    val id: String,
    val title: String,
    /** The notes in order; a step with several notes is a chord (all pressed together). */
    val steps: List<Step> = emptyList(),
    /** Step indices where a new phrase starts (0 is always a start and is not stored). */
    val breaks: List<Int> = emptyList(),
    /** Show black keys as flats (B♭) instead of sharps (A♯). */
    val flats: Boolean = false,
    val stats: SectionStats = SectionStats(),
    val createdAtMillis: Long = 0,
    /** The video the song is written from (a YouTube link), or empty. */
    val video: String = "",
    /** Part names ("อินโทร", "ฮุก"), by the step index where the part starts. */
    val phraseNames: Map<Int, String> = emptyMap(),
    /** Where each part starts in the video, in seconds, by the step index where the part starts. */
    val phraseTimes: Map<Int, Int> = emptyMap(),
    /** Notes below this MIDI note are the left hand (Middle C by default). */
    val split: Int = 60,
) {
    /** Phrases as step ranges, in order. */
    val phrases: List<IntRange> get() = Phrases.ranges(steps.size, breaks)

    fun phraseName(index: Int): String? = phrases.getOrNull(index)?.let { phraseNames[it.first] }?.takeIf { it.isNotBlank() }
    fun phraseTime(index: Int): Int? = phrases.getOrNull(index)?.let { phraseTimes[it.first] }

    /** True when some notes are for each hand (so practising one hand makes sense). */
    val bothHands: Boolean get() = steps.any { st -> st.notes.any { it < split } } && steps.any { st -> st.notes.any { it >= split } }

    /** A step added before [at] (at the end when [at] is the size): later part starts, names and times move along. */
    fun withStepInserted(at: Int, step: Step): Section {
        val i = at.coerceIn(0, steps.size)
        fun shift(k: Int) = if (k > i) k + 1 else k
        return copy(
            steps = steps.toMutableList().apply { add(i, step) },
            breaks = breaks.map(::shift),
            phraseNames = phraseNames.mapKeys { (k, _) -> shift(k) },
            phraseTimes = phraseTimes.mapKeys { (k, _) -> shift(k) },
        )
    }

    /** Step [at] removed: later part starts, names and times move back; a part left empty goes. */
    fun withStepDeleted(at: Int): Section {
        if (at !in steps.indices) return this
        val nextSize = steps.size - 1
        val newBreaks = Phrases.afterDelete(breaks, at)
        val starts = (listOf(0) + newBreaks).toSet()
        fun <V> remap(m: Map<Int, V>): Map<Int, V> =
            m.entries.mapNotNull { (k, v) -> val nk = if (k > at) k - 1 else k; if (nk in starts && nk < nextSize) nk to v else null }.toMap()
        return copy(steps = steps.filterIndexed { k, _ -> k != at }, breaks = newBreaks, phraseNames = remap(phraseNames), phraseTimes = remap(phraseTimes))
    }

    /** The notes one hand plays (steps left empty are dropped), with the parts that still have notes. */
    fun forHand(hand: Hand): HandPart {
        if (hand == Hand.BOTH) return HandPart(steps, phrases, phrases.indices.toList())
        val keepNote = { n: Int -> if (hand == Hand.LEFT) n < split else n >= split }
        val newSteps = mutableListOf<Step>()
        val ranges = mutableListOf<IntRange>()
        val sources = mutableListOf<Int>()
        phrases.forEachIndexed { pi, r ->
            val start = newSteps.size
            for (i in r) steps[i].notes.filter(keepNote).takeIf { it.isNotEmpty() }?.let { newSteps += Step(it) }
            if (newSteps.size > start) { ranges += start until newSteps.size; sources += pi }
        }
        return HandPart(newSteps, ranges, sources)
    }

    fun name(midi: Int): String = Spelling.name(midi, flats)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        private val listSerializer = ListSerializer(serializer())
        fun parseList(text: String): List<Section> = json.decodeFromString(listSerializer, text)
        fun toJson(list: List<Section>): String = json.encodeToString(listSerializer, list)
    }
}

enum class Hand { BOTH, RIGHT, LEFT }

/** One hand's notes of a song: steps, parts, and for each part the song's part it came from. */
data class HandPart(val steps: List<Step>, val phrases: List<IntRange>, val sourcePhrase: List<Int>)

/** Video links: a time like "1:23" and a link that opens at it. */
object VideoTime {
    /** "1:23", "01:02:03" or "83" in seconds; null when it cannot be read. */
    fun parse(text: String): Int? {
        val parts = text.trim().split(':').map { it.trim() }
        if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        return parts.fold(0) { acc, p -> acc * 60 + p.toInt() }
    }

    fun format(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** A web link (http or https) or null; a bare "youtu.be/..." gets https in front. */
    fun link(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
        return withScheme.takeIf { Regex("""^https?://[^\s/]+\.[^\s]+$""").matches(it) }
    }

    /** The link opening at [seconds] (YouTube's "t" parameter; an old one is replaced). */
    fun at(link: String, seconds: Int?): String {
        if (seconds == null) return link
        val base = link.replace(Regex("""([?&])t=[0-9hms]+&?"""), "$1").trimEnd('?', '&')
        return base + (if ('?' in base) "&" else "?") + "t=${seconds}s"
    }
}

/** One written step: a single note or a chord (MIDI numbers, ascending). */
@Serializable
data class Step(val notes: List<Int>) {
    init {
        require(notes.isNotEmpty()) { "empty step" }
    }
}

/** Measured practice of a section (no streak rewards: plain numbers). */
@Serializable
data class SectionStats(
    /** Full play-throughs finished, and those without a wrong note. */
    val fullRuns: Int = 0,
    val cleanFullRuns: Int = 0,
    /** Phrase index -> clean runs of that phrase (all time). */
    val phraseClean: Map<Int, Int> = emptyMap(),
    /** Every note pressed while practising, and the wrong ones. */
    val notesPlayed: Int = 0,
    val wrongNotes: Int = 0,
    /** The whole section met the goal (3 clean play-throughs in a row) at least once. */
    val mastered: Boolean = false,
    val lastPracticed: String? = null,
) {
    val accuracy: Double? get() = if (notesPlayed == 0) null else (notesPlayed - wrongNotes).toDouble() / notesPlayed
}

object Spelling {
    private val FLAT_LETTERS = mapOf(1 to Letter.D, 3 to Letter.E, 6 to Letter.G, 8 to Letter.A, 10 to Letter.B)

    fun pitch(midi: Int, flats: Boolean): SpelledPitch {
        val pc = midi % 12
        val flatLetter = FLAT_LETTERS[pc]
        return if (flats && flatLetter != null) SpelledPitch(flatLetter, -1, midi / 12 - 1) else SpelledPitch.fromMidiSharp(midi)
    }

    /** "C4", "F♯3", "B♭4". */
    fun name(midi: Int, flats: Boolean): String {
        val p = pitch(midi, flats)
        return p.letter.name + SpelledPitch.accidentalSymbol(p.accidental) + p.octave
    }
}

object Phrases {
    /** Phrases of at most [maxLen] steps, as even as possible (a 14-step section becomes 5 + 5 + 4). */
    fun autoBreaks(size: Int, maxLen: Int = 6): List<Int> {
        if (size <= maxLen) return emptyList()
        val count = (size + maxLen - 1) / maxLen
        val base = size / count
        val extra = size % count
        val out = mutableListOf<Int>()
        var at = 0
        for (i in 0 until count - 1) {
            at += base + if (i < extra) 1 else 0
            out += at
        }
        return out
    }

    fun ranges(size: Int, breaks: List<Int>): List<IntRange> {
        if (size == 0) return emptyList()
        val starts = (listOf(0) + breaks.filter { it in 1 until size }).distinct().sorted()
        return starts.mapIndexed { i, s -> s until (starts.getOrNull(i + 1) ?: size) }
    }

    /** Adds or removes a phrase start before step [index]. */
    fun toggle(breaks: List<Int>, index: Int): List<Int> =
        if (index in breaks) breaks - index else (breaks + index).sorted()

    /** Breaks after deleting step [index]: later starts move one back; a start that would land on 0 or repeat goes. */
    fun afterDelete(breaks: List<Int>, index: Int): List<Int> =
        breaks.mapNotNull { b -> if (b > index) b - 1 else b }.filter { it > 0 }.distinct().sorted()
}

/**
 * Turns played notes into steps: notes that start within [chordWindowNanos] of the first note of a step are one
 * chord. Rhythm is not kept.
 */
class StepRecorder(private val chordWindowNanos: Long = 60_000_000L) {
    private var openStart: Long? = null
    private val open = sortedSetOf<Int>()

    /** Returns the step being built after this note (a new step, or the current one grown into a chord). */
    fun noteOn(note: Int, timeNanos: Long): Pair<Step, Boolean> {
        val start = openStart
        val grows = start != null && timeNanos - start <= chordWindowNanos && note !in open
        if (!grows) {
            open.clear()
            openStart = timeNanos
        }
        open += note
        return Step(open.toList()) to grows
    }

    fun reset() {
        openStart = null
        open.clear()
    }
}

/**
 * Practice of one section, wait mode: the app waits for the right note (or every note of a chord). A wrong note sends
 * the user back to the start of the phrase. First each phrase on its own until it is played [cleanTarget] times in a
 * row without a mistake, then the whole section, where a mistake restarts only the phrase it was in; the section is
 * mastered after [cleanTarget] clean play-throughs in a row.
 */
class SectionTrainer(val steps: List<Step>, val phrases: List<IntRange>, private val cleanTarget: Int = 3) {
    sealed interface Mode {
        data class Phrase(val index: Int) : Mode
        data object Full : Mode
    }

    enum class Outcome { NOTE_OK, STEP_DONE, WRONG, PHRASE_RUN_DONE, PHRASE_PASSED, FULL_RUN_DONE, MASTERED, IGNORED }

    init {
        require(steps.isNotEmpty() && phrases.isNotEmpty()) { "nothing to practise" }
    }

    var mode: Mode = Mode.Phrase(0)
        private set
    /** Index of the step being waited for. */
    var position: Int = 0
        private set
    /** Clean runs in a row in the current mode. */
    var streak: Int = 0
        private set
    /** Mistakes in the current full play-through. */
    var runMistakes: Int = 0
        private set
    var mastered: Boolean = false
        private set
    /** Notes of the current step already pressed. */
    val pressed: Set<Int> get() = _pressed
    private val _pressed = mutableSetOf<Int>()

    // Counters for the stats.
    var notesPlayed = 0
        private set
    var wrongNotes = 0
        private set
    var fullRuns = 0
        private set
    var cleanFullRuns = 0
        private set
    val phraseClean = mutableMapOf<Int, Int>()

    val currentRange: IntRange get() = when (val m = mode) {
        is Mode.Phrase -> phrases[m.index]
        Mode.Full -> steps.indices
    }

    /** The phrase the current position is in. */
    val currentPhrase: Int get() = phrases.indexOfFirst { position in it }.coerceAtLeast(0)

    val expected: Step get() = steps[position]
    val goal: Int get() = cleanTarget

    fun select(next: Mode) {
        mode = next
        streak = 0
        restartRun()
    }

    private fun restartRun() {
        position = currentRange.first
        runMistakes = 0
        _pressed.clear()
    }

    fun press(note: Int): Outcome {
        val want = expected.notes
        if (note in _pressed) return Outcome.IGNORED // the same key struck again inside a chord
        notesPlayed++
        if (note !in want) {
            wrongNotes++
            if (mode is Mode.Phrase) streak = 0
            runMistakes++
            position = phrases[currentPhrase].first
            _pressed.clear()
            return Outcome.WRONG
        }
        _pressed += note
        if (_pressed.size < want.size) return Outcome.NOTE_OK
        _pressed.clear()
        if (position < currentRange.last) {
            position++
            return Outcome.STEP_DONE
        }
        return finishRun()
    }

    private fun finishRun(): Outcome {
        when (val m = mode) {
            is Mode.Phrase -> {
                // A mistake restarts the phrase, so a finished phrase run is always clean.
                streak++
                phraseClean[m.index] = (phraseClean[m.index] ?: 0) + 1
                if (streak >= cleanTarget) {
                    streak = 0
                    mode = if (m.index + 1 < phrases.size) Mode.Phrase(m.index + 1) else Mode.Full
                    if (phrases.size == 1) { // a one-phrase section: its phrase runs are full runs
                        fullRuns += cleanTarget; cleanFullRuns += cleanTarget; mastered = true
                        restartRun()
                        return Outcome.MASTERED
                    }
                    restartRun()
                    return Outcome.PHRASE_PASSED
                }
                restartRun()
                return Outcome.PHRASE_RUN_DONE
            }
            Mode.Full -> {
                fullRuns++
                if (runMistakes == 0) { cleanFullRuns++; streak++ } else streak = 0
                val nowMastered = !mastered && streak >= cleanTarget
                if (nowMastered) mastered = true
                restartRun()
                return if (nowMastered) Outcome.MASTERED else Outcome.FULL_RUN_DONE
            }
        }
    }

    /** Folds the counters gathered since the last call into the stored stats, and clears them. */
    fun drainInto(stats: SectionStats, day: String): SectionStats = stats.copy(
        fullRuns = stats.fullRuns + fullRuns,
        cleanFullRuns = stats.cleanFullRuns + cleanFullRuns,
        phraseClean = (stats.phraseClean.keys + phraseClean.keys).associateWith { (stats.phraseClean[it] ?: 0) + (phraseClean[it] ?: 0) },
        notesPlayed = stats.notesPlayed + notesPlayed,
        wrongNotes = stats.wrongNotes + wrongNotes,
        mastered = stats.mastered || mastered,
        lastPracticed = if (notesPlayed > 0) day else stats.lastPracticed,
    ).also {
        notesPlayed = 0; wrongNotes = 0; fullRuns = 0; cleanFullRuns = 0; phraseClean.clear()
    }
}
