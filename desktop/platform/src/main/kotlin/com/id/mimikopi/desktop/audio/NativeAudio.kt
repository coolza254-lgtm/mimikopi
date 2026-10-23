package com.id.mimikopi.desktop.audio

import java.io.File

/**
 * JNI bindings of the desktop piano engine (desktop/native: miniaudio + the Android engine's sampler). One engine
 * per handle; every call is cheap and thread-safe on the native side (notes go through a lock-free queue).
 */
internal object NativeAudio {
    /** Why the library could not be loaded, or null when it is loaded. */
    val loadError: String? = runCatching { load() }.exceptionOrNull()?.let { it.message ?: it.toString() }

    private fun load() {
        val name = System.mapLibraryName("mimikopi_audio")
        val dirs = listOfNotNull(
            // The installed program: Compose packaging puts the DLL in its resources folder.
            System.getProperty("compose.application.resources.dir"),
            // Development runs and tests: the CMake output folder.
            System.getProperty("mimikopi.native.dir"),
        )
        val file = dirs.map { File(it, name) }.firstOrNull { it.isFile }
        if (file != null) System.load(file.absolutePath) else System.loadLibrary("mimikopi_audio")
    }

    val available: Boolean get() = loadError == null

    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(h: Long)
    @JvmStatic external fun nativeNowNanos(): Long
    @JvmStatic external fun nativeLoad(
        h: Long, paths: Array<String>, packRate: Int, noteToRegion: IntArray, layerStart: IntArray, layerCount: IntArray,
        keyCenter: IntArray, releaseSec: FloatArray, loVel: IntArray, hiVel: IntArray,
    ): String?
    @JvmStatic external fun nativeUnload(h: Long)
    @JvmStatic external fun nativeStart(h: Long, periodFrames: Int, exclusive: Boolean, nullBackend: Boolean): String?
    @JvmStatic external fun nativeStop(h: Long)
    @JvmStatic external fun nativeRunning(h: Long): Boolean
    @JvmStatic external fun nativeNoteOn(h: Long, note: Int, velocity: Int)
    @JvmStatic external fun nativeNoteOff(h: Long, note: Int)
    @JvmStatic external fun nativeSustain(h: Long, down: Boolean)
    @JvmStatic external fun nativeAllNotesOff(h: Long)
    @JvmStatic external fun nativeSetMasterGain(h: Long, gain: Float)
    @JvmStatic external fun nativeSetTone(h: Long, reverb: Float, warmth: Float)
    @JvmStatic external fun nativeSetMetronome(h: Long, on: Boolean, bpm: Double, ticksPerBeat: Int, levels: IntArray, volume: Float)
    @JvmStatic external fun nativeMetronomeTickCount(h: Long): Long
    @JvmStatic external fun nativeDrainTicks(h: Long): LongArray
    @JvmStatic external fun nativeTimestamp(h: Long): LongArray?
    @JvmStatic external fun nativeMetrics(h: Long): DoubleArray
    @JvmStatic external fun nativeRender(h: Long, frames: Int): FloatArray
    @JvmStatic external fun nativeDecodeToPcm16(src: String, dst: String): LongArray?
}
