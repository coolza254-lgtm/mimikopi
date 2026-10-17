package com.id.mimikopi.desktop.port.ear

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/ear/ToneSession.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.id.mimikopi.desktop.port.painterResource
import com.id.mimikopi.desktop.port.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.Lifecycle
import com.id.mimikopi.desktop.port.SavedStateHandle
import com.id.mimikopi.desktop.port.LifecycleEventEffect
import com.id.mimikopi.desktop.port.collectAsStateWithLifecycle
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.core.ear.CadenceMode
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.core.ear.ToneLevel
import com.id.mimikopi.core.ear.ToneRun
import com.id.mimikopi.core.input.InputEvent
import com.id.mimikopi.desktop.port.NoteScheduler
import com.id.mimikopi.desktop.port.components.DrillProgress
import com.id.mimikopi.desktop.port.components.Hairline
import com.id.mimikopi.desktop.port.components.MeterBar
import com.id.mimikopi.desktop.port.components.paper
import com.id.mimikopi.desktop.ui.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

enum class TonePhase { LOADING, LISTEN, ASK, SOLVED, DONE }

data class ToneUi(
    val level: ToneLevel? = null,
    val tonic: Int = 0,
    val phase: TonePhase = TonePhase.LOADING,
    /** Question number (1-based) and how many. */
    val index: Int = 0,
    val total: Int = 0,
    val right: Int = 0,
    val wrong: Int = 0,
    /** Places tried wrongly on this question; the right place once found (or shown). */
    val tried: Set<Int> = emptySet(),
    val solved: Int? = null,
    val revealed: Boolean = false,
    /** Per place: asked, right at the first try. */
    val perPlace: Map<Int, Pair<Int, Int>> = emptyMap(),
)

/**
 * One run of an ear-training level: a cadence sets the key (before every question, or only the first), then a note;
 * the answer is its place in the key, from the keyboard (any octave) or the buttons. A right answer resolves the note
 * step by step to do, then the next question follows. Wrong tries stay marked; only a first-try answer counts.
 */
class ToneSessionViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val store = ToneStore(services)
    private val piano = services.piano
    private val hub = services.inputHub
    private val scheduler = NoteScheduler(piano)
    private val levelId: String = args.get<String>("levelId").orEmpty()
    private val random = Random(System.nanoTime())

    private val _ui = MutableStateFlow(ToneUi())
    val ui: StateFlow<ToneUi> = _ui.asStateFlow()

    private var base = 60
    private var note = 0
    private var holding = false
    private var flow: Job? = null

    init {
        viewModelScope.launch {
            val level = store.level(levelId) ?: return@launch
            _ui.value = ToneUi(level = level)
            delay(500)
            start()
        }
        viewModelScope.launch {
            hub.events.collect { e -> if (e is InputEvent.NoteOn && e.velocity > 0) answer(Math.floorMod(e.note - base, 12), fromKeyboard = true) }
        }
    }

    fun onStart() {
        if (holding) return
        viewModelScope.launch { holding = withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    fun onStop() {
        flow?.cancel()
        scheduler.clear()
        if (!holding) return
        holding = false
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    /** A new run: the key (fixed, or a new one for "any key" levels), then the first question with its cadence. */
    fun start() {
        val level = _ui.value.level ?: return
        val tonic = level.tonic ?: random.nextInt(12)
        base = FunctionalEar.base(tonic)
        _ui.value = ToneUi(level = level, tonic = tonic, total = level.questions, index = 1)
        ask(cadence = true, previous = null)
    }

    private fun ask(cadence: Boolean, previous: Int?) {
        val level = _ui.value.level ?: return
        note = FunctionalEar.pick(level, random, previous)
        _ui.update { it.copy(phase = TonePhase.LISTEN, tried = emptySet(), solved = null, revealed = false) }
        play(cadence)
    }

    /** Plays the cadence (optional) and the question note; answers are taken from the moment the note sounds. */
    private fun play(cadence: Boolean) {
        val level = _ui.value.level ?: return
        flow?.cancel()
        scheduler.clear()
        flow = viewModelScope.launch {
            var t = System.nanoTime() + 150_000_000L
            if (cadence) {
                for (chord in FunctionalEar.cadence(level.scale)) {
                    chord.forEach { scheduler.noteOn(t, base + it, 68) }
                    chord.forEach { scheduler.noteOff(t + CHORD - 60_000_000L, base + it) }
                    t += CHORD
                }
                t += GAP
            }
            scheduler.noteOn(t, base + note, 90)
            scheduler.noteOff(t + NOTE, base + note)
            delay(((t - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
            _ui.update { if (it.phase == TonePhase.LISTEN) it.copy(phase = TonePhase.ASK) else it }
        }
    }

    fun repeatNote() {
        val u = _ui.value
        if (u.phase != TonePhase.ASK && u.phase != TonePhase.LISTEN) return
        play(cadence = false)
    }

    fun replayCadence() {
        val u = _ui.value
        if (u.phase != TonePhase.ASK && u.phase != TonePhase.LISTEN) return
        play(cadence = true)
    }

    /** The user names a place (0..11 from do). */
    fun answer(place: Int, fromKeyboard: Boolean = false) {
        val u = _ui.value
        if (u.phase != TonePhase.ASK && u.phase != TonePhase.LISTEN) return
        val want = Math.floorMod(note, 12)
        if (place != want) {
            if (place in u.tried) return
            // A button sounds the note the user picked, in the question's octave (a key sounds by itself).
            if (!fromKeyboard) {
                val midi = base + note - want + place
                scheduler.noteOn(System.nanoTime(), midi, 80)
                scheduler.noteOff(System.nanoTime() + 600_000_000L, midi)
            }
            _ui.update { it.copy(tried = it.tried + place) }
            return
        }
        solve(revealed = false)
    }

    /** Shows the answer: the question counts as wrong. */
    fun reveal() {
        val u = _ui.value
        if (u.phase != TonePhase.ASK && u.phase != TonePhase.LISTEN) return
        solve(revealed = true)
    }

    private fun solve(revealed: Boolean) {
        val level = _ui.value.level ?: return
        val want = Math.floorMod(note, 12)
        val firstTry = !revealed && _ui.value.tried.isEmpty()
        _ui.update { u ->
            val (asked, ok) = u.perPlace[want] ?: (0 to 0)
            u.copy(
                phase = TonePhase.SOLVED, solved = want, revealed = revealed,
                right = u.right + if (firstTry) 1 else 0, wrong = u.wrong + if (firstTry) 0 else 1,
                perPlace = u.perPlace + (want to ((asked + 1) to (ok + if (firstTry) 1 else 0))),
            )
        }
        flow?.cancel()
        scheduler.clear()
        flow = viewModelScope.launch {
            // The note walks home to do: that pull is what each place sounds like.
            var t = System.nanoTime() + 120_000_000L
            for (n in FunctionalEar.resolution(note, level.scale)) {
                scheduler.noteOn(t, base + n, 84)
                scheduler.noteOff(t + STEP - 30_000_000L, base + n)
                t += STEP
            }
            delay(((t - System.nanoTime()) / 1_000_000) + if (revealed) 1200 else 450)
            next()
        }
    }

    private fun next() {
        val u = _ui.value
        val level = u.level ?: return
        if (u.index >= u.total) { finish(); return }
        _ui.update { it.copy(index = it.index + 1) }
        ask(cadence = level.cadence == CadenceMode.ALWAYS, previous = note)
    }

    private fun finish() {
        val u = _ui.value
        _ui.update { it.copy(phase = TonePhase.DONE) }
        viewModelScope.launch {
            val stats = store.stats()
            val run = ToneRun(LocalDate.now().toString(), u.total, u.right)
            store.saveStats(stats + (levelId to (stats[levelId] ?: com.id.mimikopi.core.ear.ToneStats()).plus(run)))
        }
    }

    override fun onCleared() {
        onStop()
        scheduler.shutdown()
    }

    private companion object {
        const val CHORD = 700_000_000L
        const val GAP = 350_000_000L
        const val NOTE = 1_200_000_000L
        const val STEP = 300_000_000L
    }
}

@Composable
fun ToneSessionScreen(onBack: () -> Unit, vm: ToneSessionViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }
    val level = ui.level

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back)) }
                Column(Modifier.weight(1f)) {
                    Text(level?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (level != null && ui.phase != TonePhase.LOADING) Text("คีย์ " + FunctionalEar.keyName(ui.tonic, level.scale),
                        style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
                }
            }
            if (level == null) return@Column
            if (ui.phase == TonePhase.DONE) { ToneResult(ui, vm::start, onBack); return@Column }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DrillProgress(ui.index, ui.total)
                Text("ถูก ${ui.right} · ผิด ${ui.wrong}", fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }

            // The middle: what is happening, and the answer once found.
            Box(Modifier.fillMaxWidth().heightIn(min = 220.dp), contentAlignment = Alignment.Center) {
                val solved = ui.solved
                if (solved != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(FunctionalEar.solfege(solved), fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 72.sp, lineHeight = 80.sp)
                    Text(
                        (if (ui.revealed) "เฉลย · " else if (ui.tried.isEmpty()) "ถูก · " else "ถูก (ครั้งที่ ${ui.tried.size + 1}) · ") +
                            FunctionalEar.degree(solved) + " · " + FunctionalEar.letter(ui.tonic + solved, ui.tonic),
                        fontFamily = Tokens.Fonts.mono, fontSize = 14.sp, color = Tokens.Palette.InkMuted,
                    )
                } else Text(
                    if (ui.phase == TonePhase.LISTEN) "ฟัง…" else "โน้ตนี้คือตัวไหน?",
                    fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Medium, fontSize = 30.sp,
                    color = if (ui.phase == TonePhase.LISTEN) Tokens.Palette.InkMuted else Tokens.Palette.Ink, textAlign = TextAlign.Center,
                )
            }

            AnswerButtons(level.answers, ui, vm::answer)

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val active = ui.phase == TonePhase.ASK || ui.phase == TonePhase.LISTEN
                Button(onClick = vm::repeatNote, enabled = active, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("ฟังโน้ตอีกครั้ง") }
                OutlinedButton(onClick = vm::replayCadence, enabled = active, modifier = Modifier.heightIn(min = 56.dp)) { Text("คอร์ดนำ") }
                TextButton(onClick = vm::reveal, enabled = active) { Text("เฉลย", color = Tokens.Palette.InkMuted) }
            }
            Text("ตอบด้วยคีย์บอร์ดได้: กดโน้ตตัวนั้นอ็อกเทฟไหนก็ได้", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted,
                modifier = Modifier.padding(top = 14.dp, bottom = 24.dp))
        }
    }
}

/** One button per place: the do-re-mi name large, the letter in this key small. Wrong tries fade, the answer fills. */
@Composable
private fun AnswerButtons(places: List<Int>, ui: ToneUi, onAnswer: (Int) -> Unit) {
    val rows = if (places.size > 8) places.chunked((places.size + 1) / 2) else listOf(places)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val isAnswer = ui.solved == p
                    val wrong = p in ui.tried
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.weight(1f).heightIn(min = 76.dp)
                            .then(if (isAnswer) Modifier.background(Tokens.Palette.Ink, shape) else Modifier.border(1.dp, if (wrong) Tokens.Palette.Line else Tokens.Palette.Ink, shape))
                            .clickable(enabled = ui.solved == null && !wrong) { onAnswer(p) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val color = when { isAnswer -> Tokens.Palette.OnInk; wrong -> Tokens.Palette.Faint; else -> Tokens.Palette.Ink }
                        Text(FunctionalEar.solfege(p), style = MaterialTheme.typography.titleMedium, color = color,
                            textDecoration = if (wrong) TextDecoration.LineThrough else null, maxLines = 1)
                        Text(FunctionalEar.letter(ui.tonic + p, ui.tonic), fontFamily = Tokens.Fonts.mono, fontSize = 11.sp,
                            color = if (isAnswer) Tokens.Palette.OnInk else Tokens.Palette.InkMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneResult(ui: ToneUi, onAgain: () -> Unit, onDone: () -> Unit) {
    val pct = if (ui.total == 0) 0 else ui.right * 100 / ui.total
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text("$pct%", fontFamily = Tokens.Fonts.mono, fontWeight = FontWeight.Light, fontSize = 96.sp, lineHeight = 100.sp)
        Text("ถูกตั้งแต่ครั้งแรก ${ui.right} จาก ${ui.total} ข้อ", style = MaterialTheme.typography.bodyLarge, color = Tokens.Palette.InkMuted)
        Spacer(Modifier.height(24.dp))
        Text("แยกตามโน้ต", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        ui.perPlace.toSortedMap().forEach { (p, v) ->
            val (asked, ok) = v
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(FunctionalEar.solfege(p), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 56.dp))
                MeterBar(if (asked == 0) 0f else ok.toFloat() / asked, Modifier.weight(1f))
                Text("$ok/$asked", fontFamily = Tokens.Fonts.mono, fontSize = 13.sp)
            }
            Hairline()
        }
        val weakest = ui.perPlace.filter { it.value.first > 0 }.minByOrNull { it.value.second.toDouble() / it.value.first }
        if (weakest != null && weakest.value.second < weakest.value.first) Text(
            "พลาดบ่อยที่สุด: ${FunctionalEar.solfege(weakest.key)} ลองฟังว่ามันอยากเดินกลับไปหา ド ทางไหน",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp),
        )
        Row(Modifier.padding(top = 28.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("อีกรอบ") }
            Button(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("เสร็จ") }
        }
    }
}
