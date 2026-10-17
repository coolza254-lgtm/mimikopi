package com.id.mimikopi.core.section

/**
 * Writing a song down, the same on the phone and the desktop: every note played is added (notes struck together become
 * a chord). A selected step is replaced by the next one played, or with [inserting] the next ones go before it. The
 * next note added at the end can start a new part ([newPartNext]). Parts are split automatically until the user
 * starts, moves, names or times a part by hand. Callers save [section] after each change.
 */
class SongEditor(initial: Section) {
    var section: Section = initial
        private set
    /** Step to replace (or insert before) with the next one played; null: add at the end. */
    var selected: Int? = null
        private set
    var inserting: Boolean = false
        private set
    var newPartNext: Boolean = false
        private set

    private var manualBreaks = initial.breaks != Phrases.autoBreaks(initial.steps.size) || initial.phraseNames.isNotEmpty() || initial.phraseTimes.isNotEmpty()
    private val recorder = StepRecorder()
    /** Index of the step a chord may still grow into. */
    private var filling: Int? = null

    private fun resetRecorder() {
        recorder.reset()
        filling = null
    }

    fun noteOn(note: Int, timeNanos: Long) {
        val s = section
        val (step, grows) = recorder.noteOn(note, timeNanos)
        val sel = selected
        val f = filling
        var next: Section
        val index: Int
        when {
            grows && f != null && f in s.steps.indices -> { next = s.copy(steps = s.steps.toMutableList().apply { this[f] = step }); index = f }
            sel != null && sel in s.steps.indices && inserting -> {
                next = s.withStepInserted(sel, step); index = sel
                selected = sel + 1 // keep inserting before the same note
            }
            sel != null && sel in s.steps.indices -> {
                next = s.copy(steps = s.steps.toMutableList().apply { this[sel] = step }); index = sel
                selected = null
            }
            else -> {
                next = s.withStepInserted(s.steps.size, step); index = next.steps.lastIndex
                if (newPartNext) {
                    newPartNext = false
                    if (index > 0) { manualBreaks = true; next = next.copy(breaks = (next.breaks + index).distinct().sorted()) }
                }
            }
        }
        filling = index
        update(next)
    }

    private fun update(next: Section) {
        val broken = if (manualBreaks) next.copy(breaks = next.breaks.filter { it in 1 until next.steps.size })
        else next.copy(breaks = Phrases.autoBreaks(next.steps.size))
        // Names and times belong to part starts; ones left on a step that no longer starts a part go.
        val starts = broken.phrases.map { it.first }.toSet()
        section = broken.copy(phraseNames = broken.phraseNames.filterKeys { it in starts }, phraseTimes = broken.phraseTimes.filterKeys { it in starts })
    }

    fun select(index: Int) {
        resetRecorder()
        selected = if (selected == index) null else index
        if (selected == null) inserting = false
        newPartNext = false
    }

    fun setInserting(on: Boolean) {
        resetRecorder()
        inserting = on && selected != null
    }

    fun toggleNewPart() {
        resetRecorder()
        selected = null
        inserting = false
        newPartNext = !newPartNext
    }

    fun deleteSelected() {
        val i = selected ?: return
        resetRecorder()
        selected = null
        inserting = false
        update(section.withStepDeleted(i))
    }

    fun undo() {
        if (section.steps.isEmpty()) return
        resetRecorder()
        selected = null
        inserting = false
        update(section.withStepDeleted(section.steps.lastIndex))
    }

    fun clear() {
        resetRecorder()
        selected = null
        inserting = false
        manualBreaks = false
        update(section.copy(steps = emptyList(), breaks = emptyList(), phraseNames = emptyMap(), phraseTimes = emptyMap(), stats = SectionStats()))
    }

    /** Starts (or removes) a part at the selected step. */
    fun togglePhraseStart() {
        val i = selected ?: return
        if (i == 0) return
        manualBreaks = true
        selected = null
        inserting = false
        update(section.copy(breaks = Phrases.toggle(section.breaks, i)))
    }

    fun autoPhrases() {
        manualBreaks = false
        update(section)
    }

    /** Names a part and sets where it starts in the video ("1:23"; empty clears either). */
    fun setPart(phrase: Int, name: String, time: String) {
        val start = section.phrases.getOrNull(phrase)?.first ?: return
        manualBreaks = true
        val seconds = VideoTime.parse(time)
        update(section.copy(
            phraseNames = if (name.isBlank()) section.phraseNames - start else section.phraseNames + (start to name.trim().take(40)),
            phraseTimes = if (seconds == null) section.phraseTimes - start else section.phraseTimes + (start to seconds),
        ))
    }

    fun rename(title: String) = update(section.copy(title = title.take(60)))
    fun setFlats(on: Boolean) = update(section.copy(flats = on))
    fun setVideo(text: String) = update(section.copy(video = text.trim().take(300)))

    /** Moves the split between the hands by [delta] semitones (kept inside the keyboard). */
    fun moveSplit(delta: Int) = update(section.copy(split = (section.split + delta).coerceIn(36, 84)))
}
