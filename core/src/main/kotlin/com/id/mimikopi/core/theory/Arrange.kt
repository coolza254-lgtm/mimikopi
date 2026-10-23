package com.id.mimikopi.core.theory

import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.TimedNote
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.PieceFile
import kotlin.random.Random

/**
 * Left-hand accompaniment patterns, the ladder of learning-system.md 8.2:
 * 1 long root (or root-fifth), 2 arpeggio 1-5-8, 3 block chord, 4 comping on every beat,
 * 5 octave bass + off-beat chords, 6 flowing 8-note arpeggio, 7 16-beat syncopation.
 */
object Accompaniment {
    val LEVEL_NAMES: Map<Int, String> get() = mapOf(
        1 to tr("โน้ตรากยาว", "Long root notes", "長いルート音"), 2 to tr("อาร์เพจจิโอ 1-5-8", "1-5-8 arpeggio", "1-5-8アルペジオ"),
        3 to tr("คอร์ดบล็อกทั้งห้อง", "Whole-bar block chords", "全小節ブロックコード"), 4 to tr("คอร์ดทุกจังหวะ (comping)", "Chords on every beat (comping)", "毎拍コード（コンピング）"),
        5 to tr("เบสอ็อกเทฟ + คอร์ดจังหวะยก", "Octave bass + off-beat chords", "オクターブベース＋裏拍コード"), 6 to tr("อาร์เพจจิโอ 8 ตัว", "8-note arpeggio", "8音アルペジオ"),
        7 to tr("ซิงโคเพชัน 16 บีต", "16-beat syncopation", "16ビートのシンコペーション"),
    )

    fun name(level: Int): String = LEVEL_NAMES[level] ?: ""

    /** Bass root of a chord between E2 and D#3. */
    fun bassRoot(chord: Chord): Int {
        var r = (chord.bass ?: chord.root).pc
        while (r < 40) r += 12
        while (r >= 52) r -= 12
        return r
    }

    /**
     * Notes for one chord lasting [length] sixteenths from [start] (sixteenths), at [level]. Returns notes and the
     * block voicing used (for smooth voice leading into the next chord).
     */
    fun notes(chord: Chord, level: Int, start: Int, length: Int, prev: List<Int>): Pair<List<TimedNote>, List<Int>> {
        val r = bassRoot(chord)
        val third = r + (if (chord.isMinorThird) 3 else 4)
        val fifth = r + (if (chord.quality == "dim" || chord.quality == "m7b5" || chord.quality == "dim7") 6 else if (chord.quality == "aug") 8 else 7)
        val block = chord.closestTo(prev, 48).filter { it in 45..67 }.ifEmpty { chord.inversions(52).first() }
        val out = mutableListOf<TimedNote>()
        fun add(at: Int, len: Int, vararg midi: Int) { if (len > 0 && at < start + length) out += TimedNote(at, minOf(len, start + length - at), midi.toList().map { SpelledPitch.fromMidiSharp(it) }) }
        fun addList(at: Int, len: Int, midi: List<Int>) = add(at, len, *midi.toIntArray())
        when (level) {
            1 -> if (length >= 16) { add(start, 8, r); add(start + 8, length - 8, fifth) } else add(start, length, r)
            2 -> {
                val seq = listOf(r, fifth, r + 12, fifth)
                var t = start; var i = 0
                while (t < start + length) { add(t, 4, seq[i % 4]); t += 4; i++ }
            }
            3 -> addList(start, length, block)
            4 -> { var t = start; while (t < start + length) { addList(t, 4, block); t += 4 } }
            5 -> { var t = start; while (t < start + length) { add(t, 2, r, r + 12); addList(t + 2, 2, block); t += 4 } }
            6 -> {
                val seq = listOf(r, fifth, r + 12, third + 12, fifth + 12, third + 12, r + 12, fifth)
                var t = start; var i = 0
                while (t < start + length) { add(t, 2, seq[i % 8]); t += 2; i++ }
            }
            else -> {
                // Per two beats: root (dotted eighth), chord (16th), rest (eighth), chord (eighth).
                var t = start
                while (t + 8 <= start + length) { add(t, 3, r); addList(t + 3, 1, block); addList(t + 6, 2, block); t += 8 }
                if (t < start + length) add(t, start + length - t, r)
            }
        }
        return out to block
    }

    /** A whole left hand for chords placed at (start, symbol) over [totalLength] sixteenths. */
    fun leftHand(chords: List<Pair<Int, Chord>>, level: Int, totalLength: Int): List<TimedNote> {
        val out = mutableListOf<TimedNote>()
        var prev = emptyList<Int>()
        chords.forEachIndexed { i, (start, chord) ->
            val end = chords.getOrNull(i + 1)?.first ?: totalLength
            val (notes, block) = notes(chord, level, start, end - start, prev)
            out += notes
            prev = block
        }
        return out
    }
}

/** Generated reading and accompaniment material for stages 4-6. Deterministic per seed. */
object StageGenerators {
    /** Keys in the order of learning-system.md 9.3. */
    val KEY_ORDER = listOf(0, 1, -1, 2, -2, 3, 4, -3, -4)

    data class Prog(val name: String, val degrees: List<String>)

    /**
     * A progression's name to show: [name] is also its key (stored drills and lessons find it by that Thai name), so
     * only the shown label is translated. Japanese names get romaji.
     */
    fun progressionLabel(name: String): String = when (name) {
        "Axis เริ่มที่ vi" -> tr(name, "Axis from vi", "Axis（viから）")
        "วงจร 50s" -> tr(name, "50s progression", "50s進行")
        "โกมุโร" -> tr("โกมุโร (小室進行)", "小室進行 (Komuro shinkō)", "小室進行")
        "ทางหลวง (王道進行)" -> tr("王道進行 (Ōdō shinkō)", "王道進行 (Ōdō shinkō)", "王道進行")
        "ทางหลวงเต็มวง" -> tr("王道進行 เต็ม", "Full 王道進行 (Ōdō shinkō)", "王道進行（フル）")
        else -> name
    }

    /** Progressions of 8.1 as roman numerals (7ths where the table has them). */
    val PROGRESSIONS = listOf(
        Prog("Axis", listOf("I", "V", "vi", "IV")),
        Prog("Axis เริ่มที่ vi", listOf("vi", "IV", "I", "V")),
        Prog("วงจร 50s", listOf("I", "vi", "IV", "V")),
        Prog("Canon", listOf("I", "V", "vi", "iii", "IV", "I", "IV", "V")),
        Prog("โกมุโร", listOf("vi", "IV", "V", "I")),
        Prog("ทางหลวง (王道進行)", listOf("IVmaj7", "V7", "iii7", "vi")),
        Prog("ทางหลวงเต็มวง", listOf("IVmaj7", "V7", "iii7", "vi", "ii7", "V7", "I")),
        Prog("ii–V–I", listOf("ii7", "V7", "Imaj7")),
    )

    private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII")

    /**
     * Roman numeral -> chord in a key: "IVmaj7", "vi", "V7", "ii7", slash degrees "I/3" (3rd of the key in the bass),
     * secondary dominants "V7/vi", and borrowed chords on flat degrees "bVII", "bVImaj7".
     */
    fun degreeChord(text: String, fifths: Int): Chord {
        val scale = Keys.scale(fifths)
        // Secondary dominant: the dominant (7) of the target degree.
        Regex("""^V(7?)/([ivIV]+)$""").matchEntire(text)?.let { m ->
            val target = degreeChord(m.groupValues[2], fifths)
            return Chord(Keys.spell(target.root.pc + 7, fifths), if (m.groupValues[1] == "7") "7" else "")
        }
        // Slash degree: bass on a scale degree (1-7).
        Regex("""^(.+)/([1-7])$""").matchEntire(text)?.let { m ->
            return degreeChord(m.groupValues[1], fifths).copy(bass = scale[m.groupValues[2].toInt() - 1])
        }
        // Borrowed chord on a lowered degree (from the parallel minor).
        Regex("""^[b♭]([ivIV]+)(.*)$""").matchEntire(text)?.let { m ->
            val degree = NUMERALS.indexOf(m.groupValues[1].uppercase())
            require(degree >= 0) { "bad numeral $text" }
            val minor = m.groupValues[1].first().isLowerCase()
            val q = m.groupValues[2].let { sfx -> if (sfx.isEmpty()) (if (minor) "m" else "") else sfx }
            return Chord(Keys.spell(scale[degree].pc - 1, -6), q)
        }
        val m = Regex("""^([ivIV]+)(.*)$""").matchEntire(text) ?: error("bad numeral $text")
        val degree = NUMERALS.indexOf(m.groupValues[1].uppercase())
        require(degree >= 0) { "bad numeral $text" }
        val suffix = m.groupValues[2]
        val minor = m.groupValues[1].first().isLowerCase()
        val quality = when (suffix) {
            "" -> if (minor) (if (degree == 6) "dim" else "m") else ""
            "7" -> if (minor) (if (degree == 6) "m7b5" else "m7") else (if (degree == 4) "7" else "maj7")
            else -> suffix
        }
        return Chord(scale[degree], quality)
    }

    /** A progression filling [bars] bars (one chord per bar, the progression repeated). */
    fun progression(p: Prog, fifths: Int, bars: Int): List<Chord> = List(bars) { degreeChord(p.degrees[it % p.degrees.size], fifths) }

    /**
     * E_COMP_PATTERN: [bars] bars of a progression in a key, the left hand in a pattern of the 8.2 ladder, the right hand
     * a simple chord-tone melody (played by the app or by the player).
     */
    fun compPattern(fifths: Int, prog: Prog, level: Int, bars: Int, tempo: Int, seed: Long): PieceFile {
        val rnd = Random(seed)
        val chords = progression(prog, fifths, bars)
        val meta = ScoreMeta("gen-comp-$seed", tr("รูปแบบประกอบ: ", "Accompaniment: ", "伴奏: ") + Accompaniment.name(level), "สร้างโดยแอพ", "Generated by the app", "L6", fifths, listOf(4, 4), tempo)
        val placed = chords.mapIndexed { i, c -> i * 16 to c }
        val lh = Accompaniment.leftHand(placed, level, bars * 16).map { respell(it, fifths) }
        val rh = chordToneMelody(chords, fifths, rnd, simple = true)
        return Timeline(meta, bars, rh, lh, placed.map { it.first to it.second.symbol }).toPieceFile()
    }

    private fun respell(n: TimedNote, fifths: Int) = n.copy(pitches = n.pitches.map { Keys.spellMidi(it.midi, fifths) })

    /** Melody of chord tones (with passing notes when not [simple]) between C4 and D5, one or two notes per beat. */
    internal fun chordToneMelody(chords: List<Chord>, fifths: Int, rnd: Random, simple: Boolean, syncopation: Boolean = false): List<TimedNote> {
        val out = mutableListOf<TimedNote>()
        var last = 67
        chords.forEachIndexed { bar, chord ->
            val tones = (60..74).filter { Math.floorMod(it, 12) in chord.pitchClasses - (chord.bass?.pc ?: -1) }
            val scaleTones = (60..74).filter { Keys.inKey(it, fifths) }
            val rhythm = when {
                simple -> listOf(listOf(4, 4, 8), listOf(8, 8), listOf(4, 4, 4, 4), listOf(8, 4, 4))[rnd.nextInt(4)]
                syncopation && rnd.nextBoolean() -> listOf(2, 4, 2, 4, 4) // e q e q q: the classic push
                else -> listOf(listOf(4, 2, 2, 4, 4), listOf(2, 2, 4, 8), listOf(4, 4, 2, 2, 4), listOf(6, 2, 8))[rnd.nextInt(4)]
            }
            var t = bar * 16
            rhythm.forEachIndexed { i, len ->
                val pool = if (i == 0 || simple) tones else scaleTones
                val near = pool.sortedBy { kotlin.math.abs(it - last) }.take(3)
                val m = near[rnd.nextInt(near.size)]
                out += TimedNote(t, len, listOf(Keys.spellMidi(m, fifths)))
                last = m
                t += len
            }
        }
        return out
    }

    /**
     * Sight reading L6-L8 (learning-system.md 5.2):
     * L6 two hands in C G F D Bb, left-hand 1-5-8 arpeggio, 4/4 or 6/8;
     * L7 two hands with left-hand triads (smooth voicing), melody around Middle C with ledger lines, simple syncopation;
     * L8 lead sheet: melody + chord symbols (7ths) in the keys of the target music, the left hand left free.
     */
    fun sightReading(level: Int, fifthsChoice: List<Int>, tempo: Int, seed: Long, time: List<Int>? = null, backing: Boolean = false): PieceFile {
        val rnd = Random(seed)
        val keys = fifthsChoice.ifEmpty { if (level == 8) KEY_ORDER else listOf(0, 1, -1, 2, -2) }
        val fifths = keys[rnd.nextInt(keys.size)]
        return when (level) {
            6 -> levelSix(fifths, tempo, rnd, seed, time)
            7 -> levelSeven(fifths, tempo, rnd, seed)
            else -> leadSheet(fifths, tempo, rnd, seed, bars = if (rnd.nextBoolean()) 8 else 16, backing = backing)
        }
    }

    private fun levelSix(fifths: Int, tempo: Int, rnd: Random, seed: Long, time: List<Int>? = null): PieceFile {
        val sixEight = if (time != null) time == listOf(6, 8) else rnd.nextInt(3) == 0
        val time = if (sixEight) listOf(6, 8) else listOf(4, 4)
        val barLen = if (sixEight) 12 else 16
        val bars = 8
        val degrees = listOf(0, 3, 4, 0, 3, 0, 4, 0)
        val chords = degrees.map { Roman.chordOf(it, fifths) }
        val meta = ScoreMeta("gen-read-L6-$seed", tr("อ่านโน้ตใหม่", "Sight-reading", "初見"), "สร้างโดยแอพ", "Generated by the app", "L6", fifths, time, tempo)
        val tonic = Keys.tonic(fifths)
        var t0 = tonic.pc
        while (t0 < 60) t0 += 12
        if (t0 > 66) t0 -= 12
        val position = (0 until 5).map { scaleNote(t0, fifths, it) }
        val rh = mutableListOf<TimedNote>()
        var idx = listOf(0, 2, 4)[rnd.nextInt(3)]
        for (b in 0 until bars) {
            val rhythm = if (sixEight) listOf(listOf(6, 6), listOf(4, 2, 4, 2), listOf(2, 2, 2, 6), listOf(6, 2, 2, 2), listOf(12))[rnd.nextInt(5)]
            else listOf(listOf(4, 4, 4, 4), listOf(4, 4, 8), listOf(8, 4, 4), listOf(2, 2, 4, 8), listOf(12, 4))[rnd.nextInt(5)]
            var t = b * barLen
            val last = b == bars - 1
            rhythm.forEachIndexed { i, len ->
                idx = if (last && i == rhythm.size - 1) 0 else (idx + listOf(-2, -1, 1, 2, 0)[rnd.nextInt(5)]).coerceIn(0, 4)
                rh += TimedNote(t, len, listOf(Keys.spellMidi(position[idx], fifths)))
                t += len
            }
        }
        val lh = mutableListOf<TimedNote>()
        chords.forEachIndexed { b, c ->
            val r = Accompaniment.bassRoot(c)
            val start = b * barLen
            if (sixEight) {
                lh += TimedNote(start, 6, listOf(Keys.spellMidi(r, fifths)))
                lh += TimedNote(start + 6, 6, listOf(Keys.spellMidi(r + 7, fifths)))
            } else {
                listOf(r, r + 7, r + 12, r + 7).forEachIndexed { i, m -> lh += TimedNote(start + i * 4, 4, listOf(Keys.spellMidi(m, fifths))) }
            }
        }
        return Timeline(meta, bars, rh, clearLeft(lh, rh)).toPieceFile()
    }

    private fun levelSeven(fifths: Int, tempo: Int, rnd: Random, seed: Long): PieceFile {
        val bars = 8
        val prog = PROGRESSIONS.take(5)[rnd.nextInt(5)]
        val chords = progression(prog, fifths, bars - 1) + Roman.chordOf(0, fifths)
        val meta = ScoreMeta("gen-read-L7-$seed", tr("อ่านโน้ตใหม่", "Sight-reading", "初見"), "สร้างโดยแอพ", "Generated by the app", "L7", fifths, listOf(4, 4), tempo)
        val rh = chordToneMelody(chords, fifths, rnd, simple = false, syncopation = true).map { n ->
            // Reach below Middle C now and then (ledger lines, learning-system.md 4.1).
            if (rnd.nextInt(8) == 0 && n.pitches[0].midi >= 64) n.copy(pitches = listOf(Keys.spellMidi(n.pitches[0].midi - 12, fifths))) else n
        }.let { endOnTonic(it, fifths) }
        val lh = mutableListOf<TimedNote>()
        var prev = emptyList<Int>()
        chords.forEachIndexed { b, c ->
            val v = c.closestTo(prev, 48).filter { it in 45..60 }.ifEmpty { c.inversions(48).first() }
            lh += TimedNote(b * 16, 8, v.map { Keys.spellMidi(it, fifths) })
            lh += TimedNote(b * 16 + 8, 8, v.map { Keys.spellMidi(it, fifths) })
            prev = v
        }
        return Timeline(meta, bars, rh, clearLeft(lh, rh)).toPieceFile()
    }

    /**
     * The left hand without the keys the right hand sounds during each of its notes: one key cannot be pressed twice at
     * once, so the judge would count the second copy as missed. With [below], also without keys at or above the lowest
     * right-hand key sounding then, so the hands never cross. A note left with no keys becomes a rest.
     */
    internal fun clearLeft(lh: List<TimedNote>, rh: List<TimedNote>, below: Boolean = false): List<TimedNote> = lh.mapNotNull { n ->
        val over = rh.filter { it.start < n.start + n.length && n.start < it.start + it.length }.flatMap { it.pitches }
        val busy = over.map { it.midi }.toSet()
        val low = over.minOfOrNull { it.midi }
        val left = n.pitches.filter { it.midi !in busy && (!below || low == null || it.midi < low) }
        if (left.isEmpty()) null else n.copy(pitches = left)
    }

    internal fun endOnTonic(notes: List<TimedNote>, fifths: Int): List<TimedNote> {
        if (notes.isEmpty()) return notes
        val last = notes.last()
        var t = Keys.tonic(fifths).pc
        while (t < 60) t += 12
        if (t > 72) t -= 12
        return notes.dropLast(1) + last.copy(pitches = listOf(Keys.spellMidi(t, fifths)))
    }

    /** L8 lead sheet: melody and chord symbols; the left hand is free (comp from the symbols). */
    /**
     * L8 lead sheet: melody and chord symbols. With [backing], the left hand holds a bass line (root and fifth) that the
     * app plays as a backing track while the player reads the melody and comps freely.
     */
    fun leadSheet(fifths: Int, tempo: Int, rnd: Random, seed: Long, bars: Int, backing: Boolean = false): PieceFile {
        val prog = PROGRESSIONS[rnd.nextInt(PROGRESSIONS.size)]
        val chords = progression(prog, fifths, bars - 1) + Roman.chordOf(0, fifths)
        val meta = ScoreMeta("gen-lead-$seed", "Lead sheet: ${progressionLabel(prog.name)}", "สร้างโดยแอพ", "Generated by the app", "L8", fifths, listOf(4, 4), tempo)
        val rh = endOnTonic(chordToneMelody(chords, fifths, rnd, simple = false, syncopation = true), fifths)
        val placed = chords.mapIndexed { i, c -> i * 16 to c }
        val lh = if (backing) Accompaniment.leftHand(placed, 1, bars * 16).map { respell(it, fifths) } else emptyList()
        return Timeline(meta, bars, rh, lh, placed.map { it.first to it.second.symbol }).toPieceFile()
    }

    private fun scaleNote(tonicMidi: Int, fifths: Int, degree: Int): Int {
        val major = listOf(0, 2, 4, 5, 7, 9, 11)
        return tonicMidi + Math.floorDiv(degree, 7) * 12 + major[Math.floorMod(degree, 7)]
    }

    /**
     * E_TRANSPOSE: a short right-hand phrase in [fromFifths] and the same phrase in [toFifths] (learning-system.md 9.3:
     * practise in an easy key, then move it).
     */
    fun transposePair(fromFifths: Int, toFifths: Int, seed: Long): Pair<PieceFile, PieceFile> {
        val rnd = Random(seed)
        val chords = progression(PROGRESSIONS[rnd.nextInt(5)], fromFifths, 4)
        val meta = ScoreMeta("gen-transpose-$seed", tr("ย้ายคีย์", "Transpose", "移調"), "สร้างโดยแอพ", "Generated by the app", "L5", fromFifths, listOf(4, 4), 72)
        val original = Timeline(meta, 4, endOnTonic(chordToneMelody(chords, fromFifths, rnd, simple = true), fromFifths), emptyList()).toPieceFile()
        return original to Transpose.piece(original, semitonesBetween(fromFifths, toFifths), toFifths)
    }

    /** Smallest move (-6..+5 semitones) from one key's tonic to another's. */
    fun semitonesBetween(fromFifths: Int, toFifths: Int): Int {
        var d = Math.floorMod(Keys.tonic(toFifths).pc - Keys.tonic(fromFifths).pc, 12)
        if (d > 5) d -= 12
        return d
    }
}

/** Moving a piece to another key, notes respelled in the new key and chord symbols moved with it. */
object Transpose {
    fun piece(file: PieceFile, semitones: Int, toFifths: Int): PieceFile {
        val t = Timeline.fromPiece(file)
        fun move(list: List<TimedNote>) = list.map { n -> n.copy(pitches = n.pitches.map { Keys.spellMidi(it.midi + semitones, toFifths) }) }
        val chords = t.chords.map { (s, text) -> s to (Chord.parse(text)?.transpose(semitones, toFifths)?.symbol ?: text) }
        return t.copy(meta = t.meta.copy(key = toFifths), rh = move(t.rh), lh = move(t.lh), chords = chords).toPieceFile()
    }
}

/** Score templates (learning-system.md 8.4, Capstone A). */
object Templates {
    /**
     * 32 bars: intro 4, verse 8, pre-chorus 4, chorus 8, outro 4, over the royal-road progression (IVmaj7 V7 iii7 vi),
     * with the left hand in accompaniment pattern [level] and an empty right hand for the user's own melody.
     */
    fun capstoneA(id: String, title: String, fifths: Int, tempo: Int, level: Int): PieceFile {
        val royal = StageGenerators.PROGRESSIONS.first { it.name.startsWith("ทางหลวง (") }
        val chords = StageGenerators.progression(royal, fifths, 31) + Roman.chordOf(0, fifths, seventh = true)
        val placed = chords.mapIndexed { i, c -> i * 16 to c }
        val lh = Accompaniment.leftHand(placed, level, 32 * 16).map { n -> n.copy(pitches = n.pitches.map { Keys.spellMidi(it.midi, fifths) }) }
        val meta = ScoreMeta(id, title, "", "User arrangement (original)", "", fifths, listOf(4, 4), tempo)
        val marks = mapOf(0 to "Intro", 4 to "Verse", 12 to "Pre-chorus", 16 to "Chorus", 24 to "Chorus", 28 to "Outro")
        return Timeline(meta, 32, emptyList(), lh, placed.map { it.first to it.second.symbol }, marks = marks).toPieceFile()
    }
}
