package com.id.mimikopi.audio

import com.id.mimikopi.core.piano.PackManifest
import com.id.mimikopi.core.piano.SampleMap
import java.io.File

/** [PianoEngine] backed by the C++ sampler in src/main/cpp (Oboe). One instance per process. */
class NativePiano : PianoEngine {
    private val handle: Long = nativeCreate()

    @Volatile override var isLoaded: Boolean = false
        private set

    @Volatile override var isRunning: Boolean = false
        private set

    @Synchronized
    override fun load(pcmDir: File, manifest: PackManifest): String? {
        if (isRunning) return "stop the engine before loading"
        val map = SampleMap(manifest)
        val files = map.filesInOrder()
        val table = map.layerTable()
        val error = nativeLoad(
            handle,
            files.map { File(pcmDir, pcmName(it.file)).path }.toTypedArray(),
            manifest.sampleRate,
            map.noteToRegion,
            table.map { it.first }.toIntArray(),
            table.map { it.second }.toIntArray(),
            manifest.regions.map { it.keyCenter }.toIntArray(),
            // Damped keys: a slightly longer fade than the pack default sounds more natural with the room.
            manifest.regions.map { (if (it.damped) maxOf(it.releaseSec, DAMPED_RELEASE_SEC) else it.releaseSec).toFloat() }.toFloatArray(),
            files.map { it.loVel }.toIntArray(),
            files.map { it.hiVel }.toIntArray(),
        )
        isLoaded = error == null
        return error
    }

    @Synchronized
    override fun start(): String? {
        // Always ask native: it knows whether the stream is really open (it may have died on a route change).
        val error = nativeStart(handle)
        isRunning = error == null
        return error
    }

    @Synchronized
    override fun stop() {
        nativeStop(handle)
        isRunning = false
    }

    override fun noteOn(note: Int, velocity: Int) = nativeNoteOn(handle, note, velocity)
    override fun noteOff(note: Int) = nativeNoteOff(handle, note)
    override fun sustain(down: Boolean) = nativeSustain(handle, down)
    override fun allNotesOff() = nativeAllNotesOff(handle)
    override fun setMasterGain(gain: Float) = nativeSetGain(handle, gain.coerceIn(0f, 4f))
    override fun setTone(reverb: Float, warmth: Float) = nativeSetTone(handle, reverb.coerceIn(0f, 1f), warmth.coerceIn(0f, 1f))

    override fun metrics(): EngineMetrics {
        val m = nativeMetrics(handle)
        return EngineMetrics(
            sampleRate = m[0].toInt(),
            framesPerBurst = m[1].toInt(),
            bufferSizeFrames = m[2].toInt(),
            estimatedLatencyMs = m[3].takeIf { it >= 0 },
            exclusive = m[4] > 0,
            lowLatency = m[5] > 0,
            xRuns = m[6].toInt().takeIf { it >= 0 },
            activeVoices = m[7].toInt(),
            maxActiveVoices = m[8].toInt(),
            maxCallbackMicros = m[9],
            callbacks = m[10].toLong(),
            restarts = m[11].toInt(),
            deviceId = m[12].toInt(),
            residentBytes = m[13].toLong(),
        )
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeLoad(
        handle: Long, paths: Array<String>, packRate: Int, noteToRegion: IntArray, layerStart: IntArray,
        layerCount: IntArray, keyCenter: IntArray, releaseSec: FloatArray, loVel: IntArray, hiVel: IntArray,
    ): String?
    private external fun nativeStart(handle: Long): String?
    private external fun nativeStop(handle: Long)
    private external fun nativeNoteOn(handle: Long, note: Int, velocity: Int)
    private external fun nativeNoteOff(handle: Long, note: Int)
    private external fun nativeSustain(handle: Long, down: Boolean)
    private external fun nativeAllNotesOff(handle: Long)
    private external fun nativeSetGain(handle: Long, gain: Float)
    private external fun nativeSetTone(handle: Long, reverb: Float, warmth: Float)
    private external fun nativeMetrics(handle: Long): DoubleArray

    companion object {
        init {
            System.loadLibrary("mimikopi_audio")
        }

        const val DAMPED_RELEASE_SEC = 0.6

        /** Decoded file name for a pack FLAC: r01_v02.flac -> r01_v02.pcm */
        fun pcmName(flacName: String): String = flacName.removeSuffix(".flac") + ".pcm"
    }
}
