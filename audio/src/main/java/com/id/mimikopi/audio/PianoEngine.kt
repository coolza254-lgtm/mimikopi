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
    fun setMasterGain(gain: Float)

    fun metrics(): EngineMetrics
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
