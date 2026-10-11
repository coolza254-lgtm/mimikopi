package com.id.mimikopi.audio

import com.id.mimikopi.core.piano.PackManifest
import java.io.File

/**
 * The app's piano sound source. All note methods are safe to call from any thread and never block
 * on audio work; they are applied at the start of the next audio callback.
 */
interface PianoEngine {
    val isLoaded: Boolean
    val isRunning: Boolean

    /** Loads decoded samples from [pcmDir]. Must be called while stopped. Returns an error or null. */
    fun load(pcmDir: File, manifest: PackManifest): String?

    /** Opens the low-latency output stream. Returns an error or null. */
    fun start(): String?
    fun stop()

    fun noteOn(note: Int, velocity: Int)
    fun noteOff(note: Int)
    fun sustain(down: Boolean)
    fun allNotesOff()
    /** Linear output gain before the compressor/limiter (1.0 = unity, up to 4.0). */
    fun setMasterGain(gain: Float)
    /** Room reverb amount 0..1 and warmth 0..1 (softer highs, fuller mids). */
    fun setTone(reverb: Float, warmth: Float)

    fun metrics(): EngineMetrics

    /** Metronome counted in audio frames inside the callback. [levels]: one ClickLevel code per tick of a bar. */
    fun setMetronome(on: Boolean, bpm: Double, ticksPerBeat: Int, levels: IntArray, volume: Float)
    /** Ticks played since the metronome last started (drives the visual beat). */
    fun metronomeTickCount(): Long
    /** Stream frame positions of ticks played since the previous call (self-test). */
    fun drainMetronomeTicks(): LongArray
    /** (framePosition, CLOCK_MONOTONIC nanos) presentation timestamp of the output, or null. */
    fun timestamp(): LongArray?
}

/** Snapshot of the output stream and engine state for Diagnostics. */
data class EngineMetrics(
    val sampleRate: Int,
    val framesPerBurst: Int,
    val bufferSizeFrames: Int,
    /** From Oboe's calculateLatencyMillis(): an estimate based on the stream's timestamps. */
    val estimatedLatencyMs: Double?,
    val exclusive: Boolean,
    val lowLatency: Boolean,
    val xRuns: Int?,
    val activeVoices: Int,
    val maxActiveVoices: Int,
    val maxCallbackMicros: Double,
    val callbacks: Long,
    val restarts: Int,
    val deviceId: Int,
    val residentBytes: Long,
)
