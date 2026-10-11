package com.id.mimikopi.core.interchange

import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.TimedNote
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.music.Letter
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.Score
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** An import that could not be read, with a Thai message for the user. */
class ImportException(message: String) : Exception(message)

/**
 * MusicXML 3.1/4.0 partwise, the subset the app's scores use: one piano part with two staves, notes and rests down
 * to 16ths (with dots), chords, ties, accidentals, key and time signature, clefs, tempo, chord symbols and repeat
 * bar lines. Import keeps the first voice of each staff and needs every duration on the 16th grid (no tuplets).
 */
object MusicXml {
    private const val DIVISIONS = 4 // per quarter note: one division = one 16th

    // ---------------- export ----------------

    fun write(score: Score): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="no"?>""").append('\n')
        sb.append("""<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">""").append('\n')
        sb.append("""<score-partwise version="4.0">""").append('\n')
        sb.append("  <work><work-title>").append(esc(score.title)).append("</work-title></work>\n")
        sb.append("  <identification>")
        if (score.composer.isNotBlank()) sb.append("<creator type=\"composer\">").append(esc(score.composer)).append("</creator>")
        sb.append("<encoding><software>Mimi Kopi</software></encoding></identification>\n")
        sb.append("""  <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>""").append('\n')
        sb.append("""  <part id="P1">""").append('\n')
        val measureLen = (score.measureBeats * DIVISIONS).toInt()
        val continuations = score.tiedContinuations()
        for (m in 0 until score.measures) {
            sb.append("    <measure number=\"${m + 1}\">\n")
            if (m in score.repeatStart) sb.append("""      <barline location="left"><bar-style>heavy-light</bar-style><repeat direction="forward"/></barline>""").append('\n')
            if (m == 0) {
                sb.append("      <attributes><divisions>$DIVISIONS</divisions><key><fifths>${score.keyFifths}</fifths></key>")
                sb.append("<time><beats>${score.beatsPerMeasure}</beats><beat-type>${score.beatUnit}</beat-type></time><staves>2</staves>")
                for ((i, hand) in listOf(Hand.RIGHT, Hand.LEFT).withIndex()) {
                    val treble = score.clefFor(hand) == com.id.mimikopi.core.notation.Clef.TREBLE
                    sb.append("<clef number=\"${i + 1}\"><sign>${if (treble) "G" else "F"}</sign><line>${if (treble) 2 else 4}</line></clef>")
                }
                sb.append("</attributes>\n")
                sb.append("      <direction placement=\"above\"><direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>${score.tempo}</per-minute></metronome></direction-type><sound tempo=\"${score.tempo}\"/></direction>\n")
            }
            val start = score.measureStart(m)
            for (c in score.chordSymbols.filter { it.beat >= start - 1e-9 && it.beat < start + score.measureBeats - 1e-9 }) {
                harmony(c.text)?.let { h ->
                    sb.append("      <harmony>$h")
                    val off = ((c.beat - start) * DIVISIONS).toInt()
                    if (off > 0) sb.append("<offset>$off</offset>")
                    sb.append("</harmony>\n")
                }
            }
            for ((staff, hand) in listOf(1 to Hand.RIGHT, 2 to Hand.LEFT)) {
                if (staff == 2) sb.append("      <backup><duration>$measureLen</duration></backup>\n")
                val voice = if (staff == 1) 1 else 5
                val evs = score.events.filter { it.hand == hand && it.measure == m }
                val prev = score.events.filter { it.hand == hand }.sortedBy { it.onset }
                for (e in evs) {
                    val dur = (e.duration * DIVISIONS).toInt()
                    val (type, dot) = typeOf(e.duration)
                    if (e.isRest) {
                        val whole = e.duration == score.measureBeats
                        sb.append("      <note><rest${if (whole) " measure=\"yes\"" else ""}/><duration>$dur</duration><voice>$voice</voice>")
                        if (!whole) sb.append("<type>$type</type>${if (dot) "<dot/>" else ""}")
                        sb.append("<staff>$staff</staff></note>\n")
                        continue
                    }
                    val stop = e.id in continuations
                    val idx = prev.indexOf(e)
                    val startTie = e.tieToNext && idx >= 0 && idx + 1 < prev.size && prev[idx + 1].id in continuations
                    e.pitches.forEachIndexed { i, p ->
                        sb.append("      <note>")
                        if (i > 0) sb.append("<chord/>")
                        sb.append("<pitch><step>${p.letter.name}</step>")
                        if (p.accidental != 0) sb.append("<alter>${p.accidental}</alter>")
                        sb.append("<octave>${p.octave}</octave></pitch><duration>$dur</duration>")
                        if (stop) sb.append("<tie type=\"stop\"/>")
                        if (startTie) sb.append("<tie type=\"start\"/>")
                        sb.append("<voice>$voice</voice><type>$type</type>${if (dot) "<dot/>" else ""}<staff>$staff</staff>")
                        val fingering = e.fingers.getOrNull(i)
                        if (stop || startTie || fingering != null) {
                            sb.append("<notations>")
                            if (stop) sb.append("<tied type=\"stop\"/>")
                            if (startTie) sb.append("<tied type=\"start\"/>")
                            if (fingering != null) sb.append("<technical><fingering>$fingering</fingering></technical>")
                            sb.append("</notations>")
                        }
                        sb.append("</note>\n")
                    }
                }
            }
            if (m in score.repeatEnd) sb.append("""      <barline location="right"><bar-style>light-heavy</bar-style><repeat direction="backward"/></barline>""").append('\n')
            else if (m == score.measures - 1) sb.append("""      <barline location="right"><bar-style>light-heavy</bar-style></barline>""").append('\n')
            sb.append("    </measure>\n")
        }
        sb.append("  </part>\n</score-partwise>\n")
        return sb.toString()
    }

    private fun typeOf(d: Double): Pair<String, Boolean> = when (d) {
        4.0 -> "whole" to false
        3.0 -> "half" to true
        2.0 -> "half" to false
        1.5 -> "quarter" to true
        1.0 -> "quarter" to false
        0.75 -> "eighth" to true
        0.5 -> "eighth" to false
        0.25 -> "16th" to false
        else -> error("no note type for $d")
    }

    private val KINDS = mapOf(
        "" to "major", "m" to "minor", "7" to "dominant", "maj7" to "major-seventh", "m7" to "minor-seventh",
        "dim" to "diminished", "aug" to "augmented", "sus4" to "suspended-fourth", "sus2" to "suspended-second",
        "6" to "major-sixth", "m6" to "minor-sixth", "9" to "dominant-ninth", "dim7" to "diminished-seventh", "m7b5" to "half-diminished",
    )
    private val CHORD = Regex("""^([A-G])([#b]?)(.*?)(?:/([A-G])([#b]?))?$""")

    /** The <harmony> content for a chord symbol like "Am7" or "F/A"; null if it is not a chord symbol. */
    fun harmony(text: String): String? {
        val m = CHORD.matchEntire(text) ?: return null
        val (step, acc, suffix, bassStep, bassAcc) = m.destructured
        fun alter(a: String) = when (a) { "#" -> "<root-alter>1</root-alter>"; "b" -> "<root-alter>-1</root-alter>"; else -> "" }
        val kind = KINDS[suffix]
        val kindXml = if (kind != null) "<kind text=\"${esc(suffix)}\">$kind</kind>" else "<kind text=\"${esc(suffix)}\">other</kind>"
        val bass = if (bassStep.isNotEmpty()) "<bass><bass-step>$bassStep</bass-step>${alter(bassAcc).replace("root", "bass")}</bass>" else ""
        return "<root><root-step>$step</root-step>${alter(acc)}</root>$kindXml$bass"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ---------------- import ----------------

    /** Reads .musicxml / .xml text or compressed .mxl bytes. */
    fun read(bytes: ByteArray, id: String): Timeline {
        val xml = if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) unzip(bytes) else bytes
        val doc = runCatching {
            val f = DocumentBuilderFactory.newInstance()
            runCatching { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            f.isExpandEntityReferences = false
            f.isNamespaceAware = false
            f.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        }.getOrElse { throw ImportException("อ่านไฟล์ XML ไม่ได้ (${it.message})") }
        val root = doc.documentElement
        if (root.tagName == "score-timewise") throw ImportException("ยังไม่รองรับ MusicXML แบบ timewise (ส่งออกแบบ partwise จากโปรแกรมเดิม)")
        if (root.tagName != "score-partwise") throw ImportException("ไม่ใช่ไฟล์ MusicXML")
        val title = root.child("work")?.child("work-title")?.text() ?: root.child("movement-title")?.text() ?: "เพลงที่นำเข้า"
        val composer = root.child("identification")?.children("creator")?.firstOrNull { it.getAttribute("type") == "composer" }?.text() ?: ""
        val part = root.children("part").firstOrNull() ?: throw ImportException("ไม่มีแนวเพลง (part) ในไฟล์")

        var divisions = 1
        var key: Int? = null
        var time: List<Int>? = null
        var tempo: Int? = null
        var staves = 1
        val clefs = mutableMapOf<Int, String>()
        val firstVoice = mutableMapOf<Int, String>()
        data class Raw(val staff: Int, val start: Int, val dur: Int, val pitch: SpelledPitch?, val chord: Boolean, val tieStart: Boolean)
        val raws = mutableListOf<Raw>()
        val chords = mutableListOf<Pair<Int, String>>()
        val repeatStart = mutableSetOf<Int>()
        val repeatEnd = mutableSetOf<Int>()
        var measureStart16 = 0
        val measures = part.children("measure")
        var warnings = 0

        fun to16(d: Int): Int {
            if ((d * 4) % divisions != 0) throw ImportException("มีโน้ตที่ละเอียดกว่าเขบ็ตสองชั้นหรือเป็นไตรเพลต ซึ่งยังไม่รองรับ")
            return d * 4 / divisions
        }

        for ((mi, measure) in measures.withIndex()) {
            var pos = 0 // divisions within the measure
            var maxPos = 0
            var lastStart = 0
            val measureRaws = mutableListOf<Raw>()
            for (el in measure.childElements()) {
                when (el.tagName) {
                    "attributes" -> {
                        el.child("divisions")?.text()?.toIntOrNull()?.let { divisions = it }
                        el.child("key")?.child("fifths")?.text()?.toIntOrNull()?.let { if (key == null) key = it else if (key != it) warnings++ }
                        el.child("time")?.let { t ->
                            val beats = t.child("beats")?.text()?.toIntOrNull()
                            val type = t.child("beat-type")?.text()?.toIntOrNull()
                            if (beats != null && type != null) {
                                val ts = listOf(beats, type)
                                if (time == null) time = ts else if (time != ts) throw ImportException("เพลงนี้เปลี่ยนจังหวะ (time signature) กลางเพลง ซึ่งยังไม่รองรับ")
                            }
                        }
                        el.child("staves")?.text()?.toIntOrNull()?.let { staves = it }
                        for (c in el.children("clef")) {
                            val n = c.getAttribute("number").toIntOrNull() ?: 1
                            if (n !in clefs) clefs[n] = if (c.child("sign")?.text() == "F") "bass" else "treble"
                        }
                    }
                    "direction" -> el.child("sound")?.getAttribute("tempo")?.toDoubleOrNull()?.let { if (tempo == null) tempo = it.toInt() }
                    "sound" -> el.getAttribute("tempo").toDoubleOrNull()?.let { if (tempo == null) tempo = it.toInt() }
                    "harmony" -> {
                        val step = el.child("root")?.child("root-step")?.text()
                        if (step != null) {
                            val alter = el.child("root")?.child("root-alter")?.text()?.toDoubleOrNull()?.toInt() ?: 0
                            val kindEl = el.child("kind")
                            val kindText = kindEl?.getAttribute("text")?.takeIf { kindEl.hasAttribute("text") }
                            val suffix = kindText ?: KINDS.entries.firstOrNull { it.value == kindEl?.text() }?.key ?: ""
                            val bass = el.child("bass")?.let { b ->
                                val bs = b.child("bass-step")?.text() ?: return@let ""
                                val ba = b.child("bass-alter")?.text()?.toDoubleOrNull()?.toInt() ?: 0
                                "/" + bs + accText(ba)
                            } ?: ""
                            val offset = el.child("offset")?.text()?.toIntOrNull() ?: 0
                            chords += (measureStart16 + to16(pos + offset)) to (step + accText(alter) + suffix + bass)
                        }
                    }
                    "backup" -> pos -= el.child("duration")?.text()?.toIntOrNull() ?: 0
                    "forward" -> { pos += el.child("duration")?.text()?.toIntOrNull() ?: 0; maxPos = maxOf(maxPos, pos) }
                    "barline" -> el.child("repeat")?.getAttribute("direction")?.let { d ->
                        if (d == "forward") repeatStart += mi
                        if (d == "backward") repeatEnd += mi
                    }
                    "note" -> {
                        if (el.child("grace") != null || el.child("cue") != null) continue
                        val dur = el.child("duration")?.text()?.toIntOrNull() ?: continue
                        val staff = el.child("staff")?.text()?.toIntOrNull() ?: 1
                        val voice = el.child("voice")?.text() ?: "1"
                        val isChord = el.child("chord") != null
                        val start = if (isChord) lastStart else pos
                        if (!isChord) { lastStart = pos; pos += dur; maxPos = maxOf(maxPos, pos) }
                        val fv = firstVoice.getOrPut(staff) { voice }
                        if (voice != fv) { warnings++; continue }
                        val pitch = el.child("pitch")?.let { p ->
                            val step = p.child("step")?.text() ?: return@let null
                            val alter = p.child("alter")?.text()?.toDoubleOrNull()?.toInt() ?: 0
                            val octave = p.child("octave")?.text()?.toIntOrNull() ?: return@let null
                            if (alter !in -2..2) throw ImportException("มีเครื่องหมายแปลงเสียงที่ไม่รองรับ")
                            SpelledPitch(Letter.valueOf(step), alter, octave)
                        }
                        if (el.child("unpitched") != null) { warnings++; continue }
                        val tieStart = el.children("tie").any { it.getAttribute("type") == "start" }
                        measureRaws += Raw(staff, start, dur, pitch, isChord, tieStart)
                    }
                }
            }
            val ts = time ?: listOf(4, 4)
            val measureLen16 = ts[0] * 16 / ts[1]
            val content16 = to16(maxPos)
            // A pickup (anacrusis) shorter than the bar is aligned to the end of the first measure.
            val shift = if (mi == 0 && measure.getAttribute("implicit") == "yes" && content16 < measureLen16) measureLen16 - content16 else 0
            for (r in measureRaws) raws += r.copy(start = measureStart16 + shift + to16(r.start), dur = to16(r.dur))
            if (content16 > measureLen16) throw ImportException("ห้องที่ ${mi + 1} มีโน้ตเกินจังหวะ")
            measureStart16 += measureLen16
        }
        val ts = time ?: listOf(4, 4)
        if (ts[1] !in listOf(2, 4, 8)) throw ImportException("ยังไม่รองรับจังหวะ ${ts[0]}/${ts[1]}")
        val handOf = { staff: Int ->
            when {
                staves >= 2 -> if (staff == 1) Hand.RIGHT else Hand.LEFT
                clefs[1] == "bass" -> Hand.LEFT
                else -> Hand.RIGHT
            }
        }
        fun notes(hand: Hand): List<TimedNote> {
            val mine = raws.filter { handOf(it.staff) == hand && it.pitch != null && (staves < 3 || it.staff <= 2) }
            return mine.groupBy { it.start }.toSortedMap().map { (start, group) ->
                TimedNote(start, group.minOf { it.dur }, group.mapNotNull { it.pitch }.distinctBy { it.midi }.sortedBy { it.midi }, group.any { it.tieStart })
            }.let { list ->
                // Overlaps inside one voice cannot be written: cut a note where the next one starts.
                list.mapIndexed { i, n -> list.getOrNull(i + 1)?.let { nx -> if (n.end > nx.start) n.copy(length = nx.start - n.start, tie = false) else n } ?: n }
                    .filter { it.length > 0 }
            }
        }
        val meta = ScoreMeta(id, title.trim(), composer.trim(), "Imported MusicXML", "", (key ?: 0).coerceIn(-7, 7), ts, tempo ?: 80,
            if (staves >= 2) clefs[1] ?: "treble" else "treble", if (staves >= 2) clefs[2] ?: "bass" else "bass")
        val count = maxOf(1, measures.size)
        return Timeline(meta, count, notes(Hand.RIGHT), notes(Hand.LEFT), chords.distinctBy { it.first }, repeatStart, repeatEnd)
            .also { lastWarnings = warnings }
    }

    /** Elements skipped by the last import (extra voices, unpitched notes, key changes): shown to the user. */
    @Volatile var lastWarnings: Int = 0
        private set

    private fun accText(alter: Int) = when (alter) { 1 -> "#"; -1 -> "b"; 2 -> "##"; -2 -> "bb"; else -> "" }

    private fun unzip(bytes: ByteArray): ByteArray {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            var total = 0L
            while (e != null) {
                if (!e.isDirectory) {
                    val data = z.readBytes()
                    total += data.size
                    if (total > 50_000_000) throw ImportException("ไฟล์ใหญ่เกินไป")
                    entries[e.name] = data
                }
                e = z.nextEntry
            }
        }
        val container = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8)
        val rootPath = container?.let { Regex("""full-path="([^"]+)"""").find(it)?.groupValues?.get(1) }
        return rootPath?.let { entries[it] } ?: entries.entries.firstOrNull { it.key.endsWith(".xml") || it.key.endsWith(".musicxml") }?.value
            ?: throw ImportException("ไม่พบไฟล์เพลงในไฟล์ .mxl")
    }

    private fun Element.childElements(): List<Element> = (0 until childNodes.length).map { childNodes.item(it) }.filter { it.nodeType == Node.ELEMENT_NODE }.map { it as Element }
    private fun Element.children(tag: String): List<Element> = childElements().filter { it.tagName == tag }
    private fun Element.child(tag: String): Element? = childElements().firstOrNull { it.tagName == tag }
    private fun Element.text(): String = textContent.trim()
}
