package com.id.mimikopi.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.interchange.MidiFile
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.notation.Staff
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.KeySignature
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import com.id.mimikopi.piano.NoteScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Written lengths offered in the toolbar (sixteenths). */
enum class NoteLength(val sixteenths: Int, val label: String) { WHOLE(16, "ตัวกลม"), HALF(8, "ตัวขาว"), QUARTER(4, "ตัวดำ"), EIGHTH(2, "เขบ็ต 1"), SIXTEENTH(1, "เขบ็ต 2") }

data class EditorUi(
    val timeline: Timeline? = null,
    val score: Score? = null,
    val layout: ScoreLayout? = null,
    val hand: Hand = Hand.RIGHT,
    /** Cursor time in sixteenths. */
    val cursor: Int = 0,
    val length: NoteLength = NoteLength.QUARTER,
    val dotted: Boolean = false,
    /** Next note goes into the chord at the cursor instead of after it. */
    val chordMode: Boolean = false,
    /** Accidental used by touch entry: null = from the key signature, else -1 / 0 / +1. */
    val accidental: Int? = null,
    /** Keys (on screen or MIDI) enter notes; off = they only sound. */
    val stepInput: Boolean = true,
    /** Tapping on the staff writes a note at that line/space. */
    val touchEntry: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val playing: Boolean = false,
    val message: String? = null,
)

/**
 * The sheet editor (PROMPT.md section 4). Notes are entered at the cursor by the on-screen keyboard, a MIDI piano
 * (step entry) or by tapping a line/space on the staff; writing overwrites what is there and the score re-bars
 * itself. Every change is saved to the user's library at once; undo/redo keep the last 100 states.
 * Nav arg: "scoreId" (an existing user score) — the library creates new scores before opening the editor.
 */
class EditorViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val repo = services.pieces
    private val hub = services.inputHub
    private val piano = services.piano
    private val scheduler = NoteScheduler(piano)
    private val scoreId: String = args.get<String>("scoreId") ?: ""

    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()
    val pressed = hub.pressed

    private val undo = ArrayDeque<Timeline>()
    private val redo = ArrayDeque<Timeline>()
    private var holding = false
    private var saveJob: Job? = null
    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            repo.load()
            val file = repo.userScores.value[scoreId]
            if (file == null) {
                _ui.update { it.copy(message = "ไม่พบโน้ตนี้") }
                return@launch
            }
            show(Timeline.fromPiece(file))
        }
        viewModelScope.launch { hub.events.collect(::onInput) }
    }

    fun onStart() {
        if (holding) return
        holding = true
        viewModelScope.launch { withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    fun onStop() {
        stopPlayback()
        if (!holding) return
        holding = false
        hub.releaseAll()
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    private fun show(t: Timeline) {
        val score = runCatching { t.toScore() }.getOrNull()
        _ui.update {
            it.copy(timeline = t, score = score, layout = score?.let { s -> ScoreLayout.of(s) },
                cursor = it.cursor.coerceIn(0, t.totalLength), canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty())
        }
    }

    /** Applies an edit: remembers the old state for undo and saves the new one. */
    private fun edit(change: (Timeline) -> Timeline, cursor: Int? = null) {
        val t = _ui.value.timeline ?: return
        val next = runCatching { change(t) }.getOrElse { e -> _ui.update { it.copy(message = e.message) }; return }
        if (next == t) return
        undo.addLast(t)
        while (undo.size > 100) undo.removeFirst()
        redo.clear()
        if (cursor != null) _ui.update { it.copy(cursor = cursor) }
        show(next)
        save(next)
    }

    private fun save(t: Timeline) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(300)
            runCatching { repo.saveUserScore(t.toPieceFile()) }.onFailure { e -> _ui.update { it.copy(message = "บันทึกไม่ได้: ${e.message}") } }
        }
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        _ui.value.timeline?.let { redo.addLast(it) }
        show(prev)
        save(prev)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        _ui.value.timeline?.let { undo.addLast(it) }
        show(next)
        save(next)
    }

    // ---------------- toolbar state ----------------

    fun setLength(l: NoteLength) {
        _ui.update { it.copy(length = l) }
        // With a note selected, the new length applies to it (as in score editors).
        val u = _ui.value
        val n = u.timeline?.noteAt(u.hand, u.cursor)
        if (n != null && n.start == u.cursor) edit({ it.setLength(u.hand, u.cursor, currentLength()) })
    }

    fun toggleDot() {
        _ui.update { it.copy(dotted = !it.dotted) }
        val u = _ui.value
        val n = u.timeline?.noteAt(u.hand, u.cursor)
        if (n != null && n.start == u.cursor) edit({ it.setLength(u.hand, u.cursor, currentLength()) })
    }

    fun toggleChordMode() = _ui.update { it.copy(chordMode = !it.chordMode) }
    fun setAccidental(a: Int?) = _ui.update { it.copy(accidental = if (it.accidental == a) null else a) }
    fun toggleStepInput() = _ui.update { it.copy(stepInput = !it.stepInput) }
    fun toggleTouchEntry() = _ui.update { it.copy(touchEntry = !it.touchEntry) }
    fun setHand(h: Hand) = _ui.update { it.copy(hand = h, cursor = snap(h, it.cursor)) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }

    private fun currentLength(): Int = _ui.value.let { u -> if (u.dotted) u.length.sixteenths * 3 / 2 else u.length.sixteenths }.coerceAtLeast(1)

    // ---------------- cursor ----------------

    /** Starts of the written notes and rests of a hand (where the cursor can stop), plus the end of the piece. */
    private fun positions(hand: Hand): List<Int> {
        val t = _ui.value.timeline ?: return listOf(0)
        val starts = t.barred(hand).map { it.start }
        // A tied group counts as one stop at its first piece.
        val notes = t.notes(hand).map { it.start }.toSet()
        val inside = t.notes(hand).flatMap { n -> starts.filter { it > n.start && it < n.end } }.toSet()
        return (starts.filter { it !in inside || it in notes } + t.totalLength).distinct().sorted()
    }

    private fun snap(hand: Hand, time: Int): Int = positions(hand).lastOrNull { it <= time } ?: 0

    fun next() = _ui.update { u -> u.copy(cursor = positions(u.hand).firstOrNull { it > u.cursor } ?: u.cursor) }
    fun previous() = _ui.update { u -> u.copy(cursor = positions(u.hand).lastOrNull { it < u.cursor } ?: 0) }

    /** Taps on the score: select the note or rest there; with touch entry, write a note at the tapped line/space. */
    fun onScoreTap(beat: Double, hand: Hand, position: Int) {
        val u = _ui.value
        val t = u.timeline ?: return
        val time = snap(hand, (beat * 4).toInt().coerceIn(0, t.totalLength))
        _ui.update { it.copy(hand = hand, cursor = time) }
        if (!u.touchEntry) return
        val score = u.score ?: return
        val pitch = pitchAt(score, hand, position, u.accidental)
        enter(pitch, hand, time)
    }

    private fun pitchAt(score: Score, hand: Hand, position: Int, accidental: Int?): SpelledPitch {
        val clef = score.clefFor(hand)
        val index = clef.middleLine.diatonicIndex + position
        val letter = Letter.entries[Math.floorMod(index, 7)]
        val octave = Math.floorDiv(index, 7)
        val acc = accidental ?: KeySignature.accidentalFor(letter, score.keyFifths)
        return SpelledPitch(letter, acc, octave)
    }

    // ---------------- entry ----------------

    private fun onInput(e: InputEvent) {
        if (e !is InputEvent.NoteOn) return
        val u = _ui.value
        if (!u.stepInput || u.playing) return
        val key = u.score?.keyFifths ?: 0
        enter(MidiFile.spell(e.note, key), u.hand, u.cursor)
    }

    private fun enter(pitch: SpelledPitch, hand: Hand, at: Int) {
        val u = _ui.value
        val t = u.timeline ?: return
        val existing = t.notes(hand).firstOrNull { it.start == at }
        if (u.chordMode && existing != null) {
            edit({ it.addPitch(hand, at, pitch) })
            return
        }
        val len = currentLength()
        edit({ it.write(hand, at, len, listOf(pitch)) }, cursor = at + len)
    }

    fun rest() {
        val u = _ui.value
        val len = currentLength()
        edit({ it.write(u.hand, u.cursor, len, emptyList()) }, cursor = u.cursor + len)
    }

    fun delete() {
        val u = _ui.value
        edit({ it.clear(u.hand, u.cursor) })
    }

    fun toggleTie() {
        val u = _ui.value
        edit({ it.toggleTie(u.hand, u.cursor) })
    }

    /** Moves the selected note up or down a semitone (sharps up, flats down). */
    fun transpose(semitones: Int) {
        val u = _ui.value
        val t = u.timeline ?: return
        val n = t.noteAt(u.hand, u.cursor) ?: return
        val key = if (semitones > 0) 1 else -1
        val pitches = n.pitches.map { MidiFile.spell(it.midi + semitones, key) }
        edit({ it.write(u.hand, n.start, n.length, pitches, n.tie) })
    }

    fun setChordSymbol(text: String?) {
        val u = _ui.value
        edit({ it.setChord(u.cursor, text) })
    }

    fun chordSymbolAtCursor(): String? = _ui.value.let { u -> u.timeline?.chords?.firstOrNull { it.first == u.cursor }?.second }

    private fun cursorMeasure(): Int = _ui.value.let { u -> u.timeline?.let { minOf(u.cursor / it.measureLength, it.measures - 1) } ?: 0 }

    fun insertMeasure() {
        val m = cursorMeasure() + 1
        edit({ it.insertMeasure(m) })
    }

    fun deleteMeasure() {
        val m = cursorMeasure()
        val t = _ui.value.timeline ?: return
        edit({ it.deleteMeasure(m) }, cursor = (m * t.measureLength).coerceAtMost(maxOf(0, t.totalLength - 2 * t.measureLength)))
    }

    fun toggleRepeatStart() { val m = cursorMeasure(); edit({ it.toggleRepeatStart(m) }) }
    fun toggleRepeatEnd() { val m = cursorMeasure(); edit({ it.toggleRepeatEnd(m) }) }

    fun meta(): ScoreMeta? = _ui.value.timeline?.meta

    fun updateMeta(m: ScoreMeta) = edit({ it.withMeta(m) })

    // ---------------- playback ----------------

    /** Plays from the cursor's measure to the end (repeats are not followed here). */
    fun play() {
        val score = _ui.value.score ?: return
        val t = _ui.value.timeline ?: return
        stopPlayback()
        val fromBeat = (cursorMeasure() * t.measureLength) / 4.0
        val beatNanos = (60_000_000_000.0 / score.tempo).toLong()
        val t0 = System.nanoTime() + 300_000_000L
        val notes = score.sounding().filter { it.onset >= fromBeat - 1e-9 }
        for (n in notes) {
            val on = t0 + ((n.onset - fromBeat) * beatNanos).toLong()
            scheduler.noteOn(on, n.midi, if (n.hand == Hand.RIGHT) 76 else 62)
            scheduler.noteOff(on + (n.duration * beatNanos * 0.92).toLong(), n.midi)
        }
        _ui.update { it.copy(playing = true) }
        val endBeat = score.totalBeats
        playJob = viewModelScope.launch {
            delay(((endBeat - fromBeat) * beatNanos / 1_000_000).toLong() + 600)
            _ui.update { it.copy(playing = false) }
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        scheduler.clear()
        _ui.update { it.copy(playing = false) }
    }

    override fun onCleared() {
        onStop()
        scheduler.shutdown()
        _ui.value.timeline?.let { t -> services.appScope.launch { repo.saveUserScore(t.toPieceFile()) } }
    }

    companion object {
        /** Staff position of a pitch (for marking the selection). */
        fun position(score: Score, hand: Hand, p: SpelledPitch) = Staff.position(p, score.clefFor(hand))
    }
}
