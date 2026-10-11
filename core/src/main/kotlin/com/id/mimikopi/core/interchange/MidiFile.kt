package com.id.mimikopi.core.interchange

import com.id.mimikopi.core.editor.ScoreMeta
import com.id.mimikopi.core.editor.TimedNote
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.score.Score
import java.io.ByteArrayOutputStream

/**
 * Standard MIDI Files. Export: format 1 with a tempo/meta track and one track per hand, 480 ticks per quarter,
 * repeats played out, tied notes held. Import: format 0 or 1, notes quantised to 16ths; two or more note tracks
 * give the hands (higher average pitch = right hand), one track is split at Middle C.
 */
object MidiFile {
    const val PPQ = 480

    // ---------------- export ----------------

    fun write(score: Score): ByteArray {
        val order = score.playOrder()
        val beatTicks = PPQ.toDouble()
        val measureTicks = (score.measureBeats * beatTicks).toLong()
        val sounding = score.sounding()
        fun trackFor(hand: Hand, channel: Int): ByteArray {
            data class Ev(val tick: Long, val on: Boolean, val note: Int)
            val evs = mutableListOf<Ev>()
            order.forEachIndexed { slot, m ->
                val base = slot * measureTicks
                val mStart = score.measureStart(m)
                for (n in sounding.filter { it.hand == hand && it.onset >= mStart - 1e-9 && it.onset < mStart + score.measureBeats - 1e-9 }) {
                    val on = base + ((n.onset - mStart) * beatTicks).toLong()
                    evs += Ev(on, true, n.midi)
                    evs += Ev(on + (n.duration * beatTicks).toLong() - 1, false, n.midi)
                }
            }
            evs.sortWith(compareBy({ it.tick }, { if (it.on) 1 else 0 }))
            val out = ByteArrayOutputStream()
            meta(out, 0, 0x03, (if (hand == Hand.RIGHT) "Right hand" else "Left hand").toByteArray())
            out.write(byteArrayOf(0x00, (0xC0 or channel).toByte(), 0x00)) // program 0: piano
            var last = 0L
            for (e in evs) {
                vlq(out, e.tick - last)
                last = e.tick
                out.write(if (e.on) 0x90 or channel else 0x80 or channel)
                out.write(e.note)
                out.write(if (e.on) (if (hand == Hand.RIGHT) 88 else 72) else 0)
            }
            endOfTrack(out)
            return out.toByteArray()
        }
        val tempoTrack = ByteArrayOutputStream().also { out ->
            meta(out, 0, 0x03, score.title.toByteArray(Charsets.UTF_8))
            val usPerQuarter = 60_000_000 / score.tempo
            meta(out, 0, 0x51, byteArrayOf((usPerQuarter shr 16).toByte(), (usPerQuarter shr 8).toByte(), usPerQuarter.toByte()))
            val denomPow = Integer.numberOfTrailingZeros(score.beatUnit)
            meta(out, 0, 0x58, byteArrayOf(score.beatsPerMeasure.toByte(), denomPow.toByte(), 24, 8))
            meta(out, 0, 0x59, byteArrayOf(score.keyFifths.toByte(), 0))
            endOfTrack(out)
        }.toByteArray()
        val tracks = listOf(tempoTrack, trackFor(Hand.RIGHT, 0), trackFor(Hand.LEFT, 1))
        val file = ByteArrayOutputStream()
        file.write("MThd".toByteArray())
        int32(file, 6)
        int16(file, 1)
        int16(file, tracks.size)
        int16(file, PPQ)
        for (t in tracks) {
            file.write("MTrk".toByteArray())
            int32(file, t.size)
            file.write(t)
        }
        return file.toByteArray()
    }

    private fun meta(out: ByteArrayOutputStream, delta: Long, type: Int, data: ByteArray) {
        vlq(out, delta)
        out.write(0xFF)
        out.write(type)
        vlq(out, data.size.toLong())
        out.write(data)
    }

    private fun endOfTrack(out: ByteArrayOutputStream) = meta(out, 0, 0x2F, ByteArray(0))

    private fun vlq(out: ByteArrayOutputStream, value: Long) {
        var v = value
        val bytes = ArrayDeque<Int>()
        bytes.addFirst((v and 0x7F).toInt())
        v = v shr 7
        while (v > 0) {
            bytes.addFirst(((v and 0x7F) or 0x80).toInt())
            v = v shr 7
        }
        bytes.forEach { out.write(it) }
    }

    private fun int32(out: ByteArrayOutputStream, v: Int) { for (s in listOf(24, 16, 8, 0)) out.write((v shr s) and 0xFF) }
    private fun int16(out: ByteArrayOutputStream, v: Int) { out.write((v shr 8) and 0xFF); out.write(v and 0xFF) }

    // ---------------- import ----------------

    private class Reader(val b: ByteArray, var p: Int = 0) {
        fun u8() = b[p++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun vlq(): Long { var v = 0L; while (true) { val c = u8(); v = (v shl 7) or (c and 0x7F).toLong(); if (c and 0x80 == 0) return v } }
        fun tag() = String(b, p, 4, Charsets.ISO_8859_1).also { p += 4 }
    }

    private data class Note(val track: Int, val start: Long, val end: Long, val midi: Int)

    fun read(bytes: ByteArray, id: String): Timeline {
        val r = Reader(bytes)
        try {
            if (r.tag() != "MThd") throw ImportException("ไม่ใช่ไฟล์ MIDI")
            val headerLen = r.u32()
            val format = r.u16()
            val trackCount = r.u16()
            val division = r.u16()
            r.p += headerLen - 6
            if (division and 0x8000 != 0) throw ImportException("ไฟล์ MIDI แบบ SMPTE ยังไม่รองรับ")
            if (format == 2) throw ImportException("ไฟล์ MIDI แบบ format 2 ยังไม่รองรับ")
            val ppq = division
            val notes = mutableListOf<Note>()
            var tempo: Int? = null
            var time: List<Int>? = null
            var key: Int? = null
            var title: String? = null
            for (t in 0 until trackCount) {
                if (r.p + 8 > bytes.size) break
                val tag = r.tag()
                val len = r.u32()
                val end = r.p + len
                if (tag != "MTrk") { r.p = end; continue }
                var tick = 0L
                var status = 0
                val open = mutableMapOf<Int, Long>()
                while (r.p < end) {
                    tick += r.vlq()
                    var s = r.u8()
                    if (s < 0x80) { r.p--; s = status } else if (s < 0xF0) status = s
                    when {
                        s == 0xFF -> {
                            val type = r.u8()
                            val l = r.vlq().toInt()
                            val data = bytes.copyOfRange(r.p, r.p + l)
                            r.p += l
                            when (type) {
                                0x51 -> if (tempo == null && l == 3) tempo = 60_000_000 / (((data[0].toInt() and 0xFF) shl 16) or ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF))
                                0x58 -> if (time == null && l >= 2) time = listOf(data[0].toInt(), 1 shl data[1].toInt())
                                0x59 -> if (key == null && l >= 1) key = data[0].toInt()
                                0x03 -> if (title == null && t == 0) title = String(data, Charsets.UTF_8).trim().ifEmpty { null }
                            }
                        }
                        s == 0xF0 || s == 0xF7 -> r.p += r.vlq().toInt()
                        else -> {
                            val type = s and 0xF0
                            val d1 = r.u8()
                            val d2 = if (type == 0xC0 || type == 0xD0) 0 else r.u8()
                            val channel = s and 0x0F
                            if (channel == 9) continue // drums
                            if (type == 0x90 && d2 > 0) open[d1] = tick
                            else if (type == 0x80 || (type == 0x90 && d2 == 0)) open.remove(d1)?.let { st -> notes += Note(t, st, tick, d1) }
                        }
                    }
                }
                r.p = end
            }
            if (notes.isEmpty()) throw ImportException("ไม่พบโน้ตในไฟล์ MIDI")
            val ts = time?.takeIf { it[1] in listOf(2, 4, 8) && it[0] in 1..16 } ?: listOf(4, 4)
            val sixteenth = ppq / 4.0
            fun q(tick: Long) = Math.round(tick / sixteenth).toInt()
            // Hands: by track when there are two or more note tracks, otherwise split at Middle C.
            val byTrack = notes.groupBy { it.track }
            val rightTrack = if (byTrack.size >= 2) byTrack.entries.maxBy { e -> e.value.map { it.midi }.average() }.key else null
            val handOf: (Note) -> Hand = { n ->
                when {
                    rightTrack != null -> if (n.track == rightTrack) Hand.RIGHT else Hand.LEFT
                    n.midi >= 60 -> Hand.RIGHT
                    else -> Hand.LEFT
                }
            }
            val keyFifths = (key ?: 0).coerceIn(-7, 7)
            fun notesFor(hand: Hand): List<TimedNote> {
                val mine = notes.filter { handOf(it) == hand }
                val grouped = mine.groupBy { q(it.start) }.toSortedMap()
                val list = grouped.map { (start, g) ->
                    val len = g.minOf { maxOf(1, q(it.end) - start) }
                    TimedNote(start, len, g.map { spell(it.midi, keyFifths) }.distinctBy { it.midi }.sortedBy { it.midi })
                }
                return list.mapIndexed { i, n -> list.getOrNull(i + 1)?.let { nx -> if (n.end > nx.start) n.copy(length = nx.start - n.start) else n } ?: n }
                    .filter { it.length > 0 }
            }
            val rh = notesFor(Hand.RIGHT)
            val lh = notesFor(Hand.LEFT)
            val measureLen = ts[0] * 16 / ts[1]
            val endTime = maxOf(rh.maxOfOrNull { it.end } ?: 0, lh.maxOfOrNull { it.end } ?: 0)
            val meta = ScoreMeta(id, title ?: "เพลงที่นำเข้าจาก MIDI", "", "Imported MIDI", "", keyFifths, ts, (tempo ?: 80).coerceIn(30, 240))
            return Timeline(meta, maxOf(1, (endTime + measureLen - 1) / measureLen), rh, lh)
        } catch (e: ImportException) {
            throw e
        } catch (e: IndexOutOfBoundsException) {
            throw ImportException("ไฟล์ MIDI ไม่สมบูรณ์")
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw ImportException("ไฟล์ MIDI ไม่สมบูรณ์")
        }
    }

    /** Spells a MIDI note with sharps in sharp keys and C major, flats in flat keys. */
    fun spell(midi: Int, keyFifths: Int): SpelledPitch {
        val sharp = SpelledPitch.fromMidiSharp(midi)
        if (keyFifths >= 0 || sharp.accidental == 0) return sharp
        val up = com.id.mimikopi.core.music.Letter.entries[(sharp.letter.ordinal + 1) % 7]
        val octave = if (sharp.letter == com.id.mimikopi.core.music.Letter.B) sharp.octave + 1 else sharp.octave
        return SpelledPitch(up, -1, octave)
    }
}
