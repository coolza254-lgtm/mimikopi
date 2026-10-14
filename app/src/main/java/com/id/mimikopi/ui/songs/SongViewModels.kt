package com.id.mimikopi.ui.songs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.songs.Song
import com.id.mimikopi.core.songs.SongAnalysis
import com.id.mimikopi.core.songs.SongLadder
import com.id.mimikopi.core.songs.SongSection
import com.id.mimikopi.core.theory.KeyFinder
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.StageGenerators
import com.id.mimikopi.core.theory.Transpose
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Lessons passed at least once count for the ladder (the second day can come later, like unlocking). */
private fun passedLessons(app: MimikopiApp): Set<String> = app.learning.legacyPassed()

/** Song Ladder (learning-system.md 9.4): the library ordered by what is still missing, then difficulty. */
class SongListViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val songs = services.songs
    private val pieces = services.pieces

    val ladder: StateFlow<List<Pair<Song, SongAnalysis>>> = combine(songs.songs, services.learning.records, pieces.userScores) { list, _, _ ->
        val passed = passedLessons(services)
        SongLadder.ladder(list.map { it to SongLadder.analyze(it, it.scoreId?.let(pieces::userScore), passed) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { services.learning.load(); pieces.load(); songs.load() }
    }

    fun add(title: String, artist: String, then: (String) -> Unit) {
        viewModelScope.launch {
            val s = Song(id = songs.newId(), title = title.ifBlank { "เพลงใหม่" }, artist = artist, order = songs.nextOrder())
            songs.save(s)
            then(s.id)
        }
    }
}

/** One song: the facts the user verifies, sections with chords, the 7-day pipeline and the linked score. */
class SongViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val songs = services.songs
    private val pieces = services.pieces
    private val id: String = args.get<String>("songId") ?: ""

    private val _song = MutableStateFlow<Song?>(null)
    val song: StateFlow<Song?> = _song.asStateFlow()
    val userScores = pieces.userScores

    val analysis: StateFlow<SongAnalysis?> = combine(_song, services.learning.records, pieces.userScores) { s, _, _ ->
        s?.let { SongLadder.analyze(it, linkedScore(it), passedLessons(services)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            services.learning.load(); pieces.load(); songs.load()
            _song.value = songs.song(id)
        }
    }

    private fun linkedScore(s: Song): Score? = s.scoreId?.let(pieces::userScore)

    /** Edits are saved shortly after the last change. */
    fun edit(change: (Song) -> Song) {
        val next = _song.value?.let(change) ?: return
        _song.value = next
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); songs.save(next) }
    }

    fun setPipelineDay(day: Int, done: Boolean) {
        edit { s -> s.copy(pipeline = s.pipeline.map { if (it.day == day) it.copy(done = done, date = if (done) LocalDate.now().toString() else null) else it }) }
        viewModelScope.launch {
            saveJob?.join()
            checkCapstone()
        }
    }

    fun addSection() = edit { it.copy(sections = it.sections + SongSection(nextSectionName(it))) }
    fun removeSection(i: Int) = edit { it.copy(sections = it.sections.filterIndexed { j, _ -> j != i }) }
    fun updateSection(i: Int, section: SongSection) = edit { it.copy(sections = it.sections.mapIndexed { j, s -> if (j == i) section else s }) }

    private fun nextSectionName(s: Song): String {
        val order = listOf("Intro", "Verse", "Pre-chorus", "Chorus", "Verse", "Pre-chorus", "Chorus", "Bridge", "Chorus", "Outro")
        return order.getOrElse(s.sections.size) { "Section ${s.sections.size + 1}" }
    }

    /** Roman numerals of a section's chords in its key (the section key, else the song key). */
    fun romans(s: Song, section: SongSection): String {
        val key = section.key ?: s.key ?: return ""
        return Song.parseChords(section.chords).joinToString(" ") { com.id.mimikopi.core.theory.Roman.analyze(it, key).numeral }
    }

    /** A new user score for this song (its key, tempo and title), linked and opened in the editor. */
    fun createScore(then: (String) -> Unit) {
        val s = _song.value ?: return
        viewModelScope.launch {
            val scoreId = pieces.newUserId()
            val meta = ScoreMeta(scoreId, s.title, s.artist, "User transcription (private, stays on this device)", "", s.key ?: 0, listOf(4, 4), s.bpm ?: 80)
            pieces.saveUserScore(Timeline.empty(meta, 8).toPieceFile())
            edit { it.copy(scoreId = scoreId) }
            then(scoreId)
        }
    }

    fun linkScore(scoreId: String?) = edit { it.copy(scoreId = scoreId) }

    /** Key finder on the notes of the linked score (step 1 of 9.5): the three best candidates. */
    fun findKey(): List<Pair<Int, String>> {
        val s = _song.value?.let(::linkedScore) ?: return emptyList()
        val notes = s.events.filter { !it.isRest }.flatMap { e -> e.pitches.map { it.midi } }
        if (notes.size < 8) return emptyList()
        return KeyFinder.rankNotes(notes).take(3).map { c ->
            val name = Keys.spellMidi(60 + c.tonicPc, KeyFinder.fifthsOf(c))
            KeyFinder.fifthsOf(c) to "${name.name(com.id.mimikopi.core.music.NoteNameStyle.LETTER, withOctave = false)} ${if (c.minor) "ไมเนอร์" else "เมเจอร์"}"
        }
    }

    fun useKey(fifths: Int) = edit { it.copy(key = fifths, keyVerified = false) }

    /** A copy of the linked score in another key (practise in an easy key first, learning-system.md 9.6). */
    fun transposedCopy(toFifths: Int, then: (String) -> Unit) {
        val s = _song.value ?: return
        val file = s.scoreId?.let { pieces.userScores.value[it] } ?: return
        viewModelScope.launch {
            val newId = pieces.newUserId()
            val moved = Transpose.piece(file, StageGenerators.semitonesBetween(file.key, toFifths), toFifths)
            pieces.saveUserScore(moved.copy(id = newId, title = "${file.title} (คีย์ ${Keys.name(toFifths)})"))
            _message.value = "สร้างสำเนาในคีย์ ${Keys.name(toFifths)} แล้ว อยู่ในหน้าโน้ตของฉัน"
            then(newId)
        }
    }

    fun delete(then: () -> Unit) {
        viewModelScope.launch { songs.delete(id); then() }
    }

    fun dismiss() { _message.value = null }

    fun isComplete(s: Song) = songs.isComplete(s, pieces)

    private suspend fun checkCapstone() {
        val passed = services.learning.checkSongLessons(songs.completedCount(pieces))
        passed.firstOrNull()?.let { _message.value = "ผ่านบท ${it.id} ${it.title} แล้ว" }
    }
}
