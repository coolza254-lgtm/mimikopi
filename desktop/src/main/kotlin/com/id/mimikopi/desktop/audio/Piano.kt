package com.id.mimikopi.desktop.audio

import com.id.mimikopi.core.piano.PackManifest
import com.id.mimikopi.core.piano.SampleMap
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import javax.sound.midi.MidiSystem
import javax.sound.midi.Synthesizer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.pow

/** The app's own piano sound (questions, playback, and keys when "sound through" is on). */
interface Piano {
    fun noteOn(note: Int, velocity: Int)
    fun noteOff(note: Int)
    fun sustain(down: Boolean)
    fun allNotesOff()
    fun close()
    /** A metronome click; [level] 0 strong .. 3 sub (as on Android), [volume] 0..1. */
    fun click(level: Int, volume: Float)
    /** About how long after a call the sound is heard (the output buffer). */
    val latencyNanos: Long
}

/**
 * Java's built-in software synthesizer (Gervill) with its General MIDI piano: always there, no download, plainer
 * sound. Used until the sampled piano is installed.
 */
class GervillPiano : Piano {
    private val synth: Synthesizer? = runCatching { MidiSystem.getSynthesizer().also { it.open() } }.getOrNull()
    private val channel = synth?.channels?.firstOrNull()?.also { it.programChange(0) }

    override fun noteOn(note: Int, velocity: Int) { channel?.noteOn(note, velocity) }
    override fun noteOff(note: Int) { channel?.noteOff(note) }
    override fun sustain(down: Boolean) { channel?.controlChange(64, if (down) 127 else 0) }
    override fun allNotesOff() { channel?.allNotesOff() }
    override fun close() { runCatching { synth?.close() } }

    /** General MIDI percussion: high and low wood block. */
    private val drums = synth?.channels?.getOrNull(9)
    override fun click(level: Int, volume: Float) {
        val v = (volume * (if (level == 0) 120 else if (level == 1) 100 else 80)).toInt().coerceIn(1, 127)
        drums?.noteOn(if (level == 0) 76 else 77, v)
    }
    override val latencyNanos: Long = (synth?.latency ?: 0L) * 1000L
}

/**
 * The sampled grand piano (the same Salamander pack as the Android app, decoded to raw 16-bit stereo PCM files and
 * memory-mapped, so the 400+ MB never sit on the Java heap). One mixer thread renders short blocks into a
 * SourceDataLine; notes pick the region and velocity layer from [SampleMap] and are pitched by resampling.
 */
class SamplerPiano(private val dir: File, private val manifest: PackManifest) : Piano {
    private val map = SampleMap(manifest)
    private val files = map.filesInOrder()
    private val table = map.layerTable()
    private val samples: List<ShortBuffer> = files.map { layer ->
        RandomAccessFile(File(dir, pcmName(layer.file)), "r").use { raf ->
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        }
    }
    private val rate = manifest.sampleRate.toFloat()

    private class Voice(val note: Int, val data: ShortBuffer, val frames: Int, val step: Double, val gain: Float, val releaseFrames: Int, val damped: Boolean) {
        var pos = 0.0
        var held = true
        /** Frames left of the release fade, or -1 while the note sounds. */
        var releaseLeft = -1
        val done: Boolean get() = pos >= frames - 2 || releaseLeft == 0
    }

    private val voices = ArrayList<Voice>()
    /** Metronome clicks: a short decaying tone mixed in with the piano. */
    private class Click(val freq: Float, val gain: Float) { var n = 0 }
    private val clicks = ArrayList<Click>()
    private val clickFrames = (0.05f * manifest.sampleRate).toInt()
    private val lock = Any()
    private var sustainDown = false
    @Volatile private var running = true
    private val line: SourceDataLine
    private val thread: Thread

    init {
        val format = AudioFormat(rate, 16, 2, true, false)
        line = AudioSystem.getSourceDataLine(format)
        line.open(format, BLOCK * 4 * 8) // about 40 ms of buffer at 48 kHz
        line.start()
        thread = Thread({ render() }, "mimikopi-sampler").apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }
    }

    override fun noteOn(note: Int, velocity: Int) {
        val region = map.noteToRegion.getOrNull(note) ?: return
        if (region < 0) return
        val layer = map.layerIndex(region, velocity).coerceAtLeast(0)
        val index = table[region].first + layer
        val info = manifest.regions[region]
        val v = Voice(
            note, samples[index], files[index].frames, map.pitchRatio(note, rate.toInt()),
            gain = (0.35f + 0.65f * (velocity.coerceIn(1, 127) / 127f).toDouble().pow(1.4).toFloat()) * 0.5f,
            releaseFrames = (info.releaseSec * rate).toInt().coerceAtLeast(64), damped = info.damped,
        )
        synchronized(lock) {
            // The same key struck again: the old sound fades quickly.
            voices.filter { it.note == note && it.releaseLeft < 0 }.forEach { it.held = false; it.releaseLeft = minOf(it.releaseFrames, (0.08f * rate).toInt()) }
            voices += v
            if (voices.size > MAX_VOICES) voices.removeAt(0)
        }
    }

    override fun noteOff(note: Int) = synchronized(lock) {
        voices.filter { it.note == note && it.held }.forEach { it.held = false; if (!sustainDown && it.damped) it.releaseLeft = it.releaseFrames }
    }

    override fun sustain(down: Boolean) = synchronized(lock) {
        sustainDown = down
        if (!down) voices.filter { !it.held && it.releaseLeft < 0 && it.damped }.forEach { it.releaseLeft = it.releaseFrames }
    }

    override fun allNotesOff() = synchronized(lock) { voices.forEach { it.held = false; if (it.releaseLeft < 0) it.releaseLeft = (0.05f * rate).toInt() } }

    override fun click(level: Int, volume: Float) = synchronized(lock) {
        clicks += Click(if (level == 0) 1760f else 1320f, volume.coerceIn(0f, 1f) * (if (level == 0) 9000f else if (level == 1) 7000f else 5500f))
    }

    override val latencyNanos: Long get() = (line.bufferSize / 4).toLong() * 1_000_000_000L / rate.toLong() + 10_000_000L

    override fun close() {
        running = false
        thread.join(500)
        runCatching { line.drain(); line.close() }
    }

    private fun render() {
        val mix = FloatArray(BLOCK * 2)
        val out = ByteArray(BLOCK * 4)
        while (running) {
            java.util.Arrays.fill(mix, 0f)
            synchronized(lock) {
                val it = voices.iterator()
                while (it.hasNext()) {
                    val v = it.next()
                    for (f in 0 until BLOCK) {
                        if (v.done) break
                        val i = v.pos.toInt()
                        val frac = (v.pos - i).toFloat()
                        val l0 = v.data.get(i * 2).toFloat(); val r0 = v.data.get(i * 2 + 1).toFloat()
                        val l1 = v.data.get(i * 2 + 2).toFloat(); val r1 = v.data.get(i * 2 + 3).toFloat()
                        var g = v.gain
                        if (v.releaseLeft > 0) { g *= v.releaseLeft.toFloat() / v.releaseFrames; v.releaseLeft-- }
                        mix[f * 2] += (l0 + (l1 - l0) * frac) * g
                        mix[f * 2 + 1] += (r0 + (r1 - r0) * frac) * g
                        v.pos += v.step
                    }
                    if (v.done) it.remove()
                }
                val c = clicks.iterator()
                while (c.hasNext()) {
                    val k = c.next()
                    for (f in 0 until BLOCK) {
                        if (k.n >= clickFrames) break
                        val t = k.n / rate
                        val s = (kotlin.math.sin(2.0 * Math.PI * k.freq * t) * kotlin.math.exp(-t * 90.0)).toFloat() * k.gain
                        mix[f * 2] += s
                        mix[f * 2 + 1] += s
                        k.n++
                    }
                    if (k.n >= clickFrames) c.remove()
                }
            }
            for (k in mix.indices) {
                val s = mix[k].coerceIn(-32768f, 32767f).toInt()
                out[k * 2] = (s and 0xFF).toByte()
                out[k * 2 + 1] = (s shr 8 and 0xFF).toByte()
            }
            line.write(out, 0, out.size) // blocks until the device takes it: paces the loop
        }
    }

    companion object {
        const val BLOCK = 256
        const val MAX_VOICES = 64
        fun pcmName(flac: String) = flac.removeSuffix(".flac") + ".pcm"
    }
}
