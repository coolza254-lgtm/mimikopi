package com.id.mimikopi.desktop.session

import com.id.mimikopi.core.editor.PlayedNote
import com.id.mimikopi.core.editor.Recording
import com.id.mimikopi.core.editor.TimedNote
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.metronome.FrameTimestamp
import com.id.mimikopi.core.metronome.JitterAnalysis
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.desktop.Services
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the recording page shows. */
data class RecordUi(
    val timeline: Timeline? = null,
    val hand: Hand = Hand.RIGHT,
    val from: Int = 0,
    val to: Int = 1,
    val bpm: Int = 60,
    val grid: Int = 2,
    /** Count-in beats left, while counting in. */
    val countIn: Int? = null,
    /** The bar being recorded (from 0), while recording. */
    val bar: Int? = null,
    /** The last take, snapped, waiting to be kept or redone. */
    val take: List<TimedNote>? = null,
    /** The note (by start, in the take) whose pitch the next key press replaces. */
    val fixing: Int? = null,
    val message: String? = null,
)

/**
 * Records one hand of a few bars along with the metronome (one bar of count-in), snaps the take to the grid, lets the
 * player fix wrong pitches by selecting a note and pressing the right key, and keeps it in the private score.
 */
class RecordSession(private val services: Services, private val scope: CoroutineScope, private val scoreId: String) {
    private val piano = services.piano
    private val hub = services.hub
    private val scores = services.privateScores
    private val _ui = MutableStateFlow(RecordUi())
    val ui: StateFlow<RecordUi> = _ui.asStateFlow()
    private var holding = false
    private var job: Job? = null
    private var listener: Job? = null

    fun enter() {
        if (!holding) holding = piano.acquire()
        val t = scores.timeline(scoreId)
        _ui.value = RecordUi(timeline = t, bpm = (t?.meta?.tempo ?: 60).coerceAtMost(80), to = 1.coerceAtMost((t?.measures ?: 1) - 1).coerceAtLeast(0))
        listener = scope.launch { hub.events.collect(::onFixKey) }
    }

    fun leave() {
        stop()
        listener?.cancel()
        if (holding) piano.release()
        holding = false
    }

    fun setHand(h: Hand) = edit { it.copy(hand = h) }
    fun setRange(from: Int, to: Int) = edit { it.copy(from = from.coerceAtLeast(0), to = to.coerceAtLeast(from.coerceAtLeast(0))) }
    fun setBpm(bpm: Int) = edit { it.copy(bpm = bpm.coerceIn(30, 200)) }
    fun setGrid(g: Int) = edit { it.copy(grid = g) }
    fun fix(start: Int?) = _ui.value.let { _ui.value = it.copy(fixing = start) }

    private fun edit(change: (RecordUi) -> RecordUi) {
        if (_ui.value.countIn != null || _ui.value.bar != null) return
        _ui.value = change(_ui.value)
    }

    fun stop() {
        job?.cancel(); job = null
        piano.setMetronome(false, 60.0, 1, intArrayOf(0), 0f)
        _ui.value = _ui.value.copy(countIn = null, bar = null)
    }

    /** Count in one bar, record bars [RecordUi.from]..[RecordUi.to], then show the snapped take. */
    fun record() {
        val u = _ui.value
        val t = u.timeline ?: return
        if (job != null || !holding) return
        val beatsPerBar = t.meta.time[0]
        // The click follows the written beat unit; bpm counts quarter notes.
        val clickBpm = u.bpm * t.meta.time[1] / 4.0
        val bars = u.to - u.from + 1
        job = scope.launch {
            piano.drainMetronomeTicks()
            val levels = IntArray(beatsPerBar) { if (it == 0) 0 else 2 }
            piano.setMetronome(true, clickBpm, 1, levels, 0.9f)
            val stamps = mutableListOf<FrameTimestamp>()
            val ticks = mutableListOf<Long>()
            val on = HashMap<Int, Long>()
            val played = mutableListOf<PlayedNote>()
            var source = InputSource.USB_MIDI
            val collect = launch {
                hub.events.collect { e ->
                    if (ticks.size < beatsPerBar - 1) return@collect
                    when (e) {
                        is InputEvent.NoteOn -> { on[e.note] = e.timeNanos / 1_000_000; source = e.source }
                        is InputEvent.NoteOff -> on.remove(e.note)?.let { synchronized(played) { played += PlayedNote(e.note, it, e.timeNanos / 1_000_000) } }
                        else -> {}
                    }
                }
            }
            val total = beatsPerBar * (bars + 1)
            val beatMs = 60_000.0 / clickBpm
            val deadline = System.nanoTime() + ((total + 4) * beatMs * 1e6).toLong()
            while (ticks.size < total && System.nanoTime() < deadline) {
                delay(30)
                piano.timestamp()?.let { stamps += FrameTimestamp(it[0], it[1]) }
                ticks += piano.drainMetronomeTicks().toList()
                val n = ticks.size
                _ui.value = _ui.value.copy(
                    countIn = if (n < beatsPerBar) beatsPerBar - n else null,
                    bar = if (n >= beatsPerBar) u.from + ((n - beatsPerBar) / beatsPerBar).coerceAtMost(bars - 1) else null,
                )
            }
            delay((beatMs / 2).toLong())
            piano.setMetronome(false, clickBpm, 1, intArrayOf(0), 0f)
            collect.cancel()
            val now = System.nanoTime() / 1_000_000
            synchronized(played) { on.forEach { (k, v) -> played += PlayedNote(k, v, now) } }
            val rate = piano.outputSampleRate()
            val times = if (rate > 0 && stamps.size > 2 && ticks.size > beatsPerBar) JitterAnalysis.presentationTimesNanos(ticks, stamps, rate) else emptyList()
            val latency = services.latencyMs(source) ?: 0.0
            val startMs = times.getOrNull(beatsPerBar)?.let { (it / 1e6 + latency).toLong() }
            job = null
            _ui.value = if (startMs == null || played.isEmpty()) {
                _ui.value.copy(countIn = null, bar = null, take = null,
                    message = com.id.mimikopi.core.i18n.tr("ไม่ได้ยินโน้ต ลองใหม่อีกครั้ง (ต่อเปียโนและเปิดเสียงไว้)", "No notes heard; try again (piano connected, sound on)", "音が聞こえませんでした。もう一度（ピアノ接続・音声オン）"))
            } else {
                _ui.value.copy(countIn = null, bar = null, message = null,
                    take = Recording.transcribe(synchronized(played) { played.toList() }, startMs, u.bpm.toDouble(), u.grid, t.meta.key))
            }
        }
    }

    /** While a take note is selected, the next key pressed becomes its pitch. */
    private fun onFixKey(e: InputEvent) {
        val u = _ui.value
        val at = u.fixing ?: return
        if (e !is InputEvent.NoteOn || u.take == null) return
        val p = com.id.mimikopi.core.interchange.MidiFile.spell(e.note, u.timeline?.meta?.key ?: 0)
        _ui.value = u.copy(take = u.take.map { if (it.start == at) it.copy(pitches = listOf(p)) else it }, fixing = null)
    }

    fun deleteTakeNote(start: Int) = _ui.value.let { u -> _ui.value = u.copy(take = u.take?.filterNot { it.start == start }) }

    fun discardTake() { _ui.value = _ui.value.copy(take = null, fixing = null) }

    /** Keeps the take in the score and moves the range to the next bars. */
    fun keepTake() {
        val u = _ui.value
        val t = u.timeline ?: return
        val take = u.take ?: return
        val next = Recording.place(t, u.hand, u.from, u.to, take)
        scores.save(next)
        val len = u.to - u.from + 1
        _ui.value = u.copy(timeline = next, take = null, fixing = null, from = u.to + 1, to = u.to + len)
    }

    fun addBars(n: Int) {
        val t = _ui.value.timeline ?: return
        val next = t.copy(measures = t.measures + n)
        scores.save(next)
        _ui.value = _ui.value.copy(timeline = next)
    }
}
