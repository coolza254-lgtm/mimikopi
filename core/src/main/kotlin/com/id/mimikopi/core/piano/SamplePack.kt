package com.id.mimikopi.core.piano

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import kotlin.math.abs
import kotlin.math.pow

/** `samples-vN.json` published next to the pack zip (see tools/samples/build_samples.py). */
@Serializable
data class PackDescriptor(
    val schema: Int,
    val packVersion: Int,
    val url: String,
    val size: Long,
    val sha256: String,
    val decodedBytes: Long,
    val files: Int,
    val license: String,
) {
    /** Empty when the descriptor is safe to act on (download + install). */
    fun problems(): List<String> = buildList {
        if (schema != 1) add("unsupported schema $schema")
        if (!Regex("^[0-9a-f]{64}$").matches(sha256)) add("bad sha256")
        // `this@PackDescriptor`: inside buildList, bare `size` would be the list size.
        val bytes = this@PackDescriptor.size
        if (bytes !in 1..MAX_PACK_BYTES) add("bad size $bytes")
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host != "github.com") add("url not allowed: $url")
    }

    companion object {
        const val MAX_PACK_BYTES = 1L shl 30
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): PackDescriptor = json.decodeFromString(serializer(), text)
    }
}

/** `manifest.json` inside the pack zip. */
@Serializable
data class PackManifest(
    val format: Int,
    val packVersion: Int,
    val name: String,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val regions: List<PackRegion>,
) {
    /** Structural checks before the engine trusts the pack. Empty means valid. */
    fun problems(): List<String> = buildList {
        if (format != 1) add("unsupported format $format")
        if (channels != 2 || bitsPerSample != 16) add("expected 16-bit stereo")
        if (sampleRate !in 8000..192000) add("bad sample rate $sampleRate")
        for (note in PIANO_LOW..PIANO_HIGH) {
            val owners = regions.count { note in it.lo..it.hi }
            if (owners != 1) add("note $note covered by $owners regions")
        }
        regions.forEachIndexed { i, r ->
            if (r.keyCenter !in r.lo..r.hi) add("region $i key center outside range")
            if (abs(r.hi - r.keyCenter) > MAX_STRETCH || abs(r.keyCenter - r.lo) > MAX_STRETCH) add("region $i stretches too far")
            if (r.layers.isEmpty()) add("region $i has no layers")
            var expect = 1
            for (l in r.layers.sortedBy { it.loVel }) {
                if (l.loVel != expect) add("region $i velocity gap/overlap at ${l.loVel}")
                if (l.frames <= 0) add("region $i empty sample ${l.file}")
                if (!SAFE_FILE.matches(l.file)) add("region $i unsafe file name ${l.file}")
                expect = l.hiVel + 1
            }
            if (expect != 128) add("region $i velocities end at ${expect - 1}")
        }
    }

    companion object {
        const val PIANO_LOW = 21
        const val PIANO_HIGH = 108
        const val MAX_STRETCH = 2
        private val SAFE_FILE = Regex("^[A-Za-z0-9_.-]+\\.flac$")
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): PackManifest = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class PackRegion(
    val lo: Int,
    val hi: Int,
    val keyCenter: Int,
    val damped: Boolean,
    val releaseSec: Double,
    val layers: List<PackLayer>,
)

@Serializable
data class PackLayer(val file: String, val loVel: Int, val hiVel: Int, val frames: Int)

/**
 * Note/velocity -> sample lookup, shared logic with the native engine (which receives the flattened
 * tables from [noteToRegion] and [layerTable]).
 */
class SampleMap(private val manifest: PackManifest) {
    init {
        val problems = manifest.problems()
        require(problems.isEmpty()) { "invalid pack: ${problems.take(3)}" }
    }

    /** For MIDI notes 0..127: region index, or -1 outside the piano range. */
    val noteToRegion: IntArray = IntArray(128) { note -> manifest.regions.indexOfFirst { note in it.lo..it.hi } }

    /** For each region, layers sorted by velocity. */
    private val sortedLayers = manifest.regions.map { r -> r.layers.sortedBy { it.loVel } }

    fun layerIndex(region: Int, velocity: Int): Int {
        val v = velocity.coerceIn(1, 127)
        return sortedLayers[region].indexOfFirst { v in it.loVel..it.hiVel }
    }

    /** Playback-rate ratio for [note] when the device runs at [outputRate]. */
    fun pitchRatio(note: Int, outputRate: Int): Double {
        val region = manifest.regions[noteToRegion[note]]
        return 2.0.pow((note - region.keyCenter) / 12.0) * manifest.sampleRate / outputRate
    }

    /** Files in engine order: region-major, then velocity-sorted layers. */
    fun filesInOrder(): List<PackLayer> = sortedLayers.flatten()

    /** Per region: (first layer index into [filesInOrder], layer count). */
    fun layerTable(): List<Pair<Int, Int>> {
        var start = 0
        return sortedLayers.map { layers -> (start to layers.size).also { start += layers.size } }
    }
}
