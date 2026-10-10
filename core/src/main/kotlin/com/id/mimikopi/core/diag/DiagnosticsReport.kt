package com.id.mimikopi.core.diag

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the user copies from the Diagnostics screen and sends back (PROMPT.md section 7).
 * Fields that a later milestone fills are nullable; [notes] says why a value is missing, so a
 * null is never mistaken for a measurement.
 */
@Serializable
data class DiagnosticsReport(
    val schema: Int = 1,
    val generatedAtUtc: String,
    val app: AppInfo,
    val device: DeviceInfo,
    val features: Map<String, Boolean>,
    val audio: AudioInfo,
    val midi: MidiInfo,
    val metronomeJitter: JitterResult? = null,
    val lastUpdateCheck: String? = null,
    val database: DatabaseInfo? = null,
    val notes: List<String> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }

        fun fromJson(text: String): DiagnosticsReport = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class AppInfo(val applicationId: String, val versionName: String, val versionCode: Long, val buildType: String)

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
)

@Serializable
data class AudioInfo(
    /** AudioManager PROPERTY_OUTPUT_SAMPLE_RATE: the device's preferred output rate. */
    val outputSampleRate: Int?,
    /** AudioManager PROPERTY_OUTPUT_FRAMES_PER_BUFFER: a hint, not the Oboe burst size. */
    val framesPerBufferHint: Int?,
    /** Measured from an open Oboe stream (filled from M3). */
    val framesPerBurst: Int? = null,
    /** System-estimated output latency in ms. An estimate, not a measurement (filled from M3). */
    val estimatedOutputLatencyMs: Double? = null,
    /** Device(s) currently used for media playback, e.g. "BUILTIN_SPEAKER" or "BLUETOOTH_A2DP". */
    val currentRoute: List<String>,
    val availableOutputs: List<String>,
)

@Serializable
data class MidiInfo(
    val devices: List<MidiDeviceSummary>,
    /** Velocity capability probe result (filled from M5). */
    val probe: String? = null,
)

@Serializable
data class MidiDeviceSummary(val name: String, val manufacturer: String?, val type: String, val inputPorts: Int, val outputPorts: Int)

@Serializable
data class JitterResult(val meanAbsMs: Double, val maxAbsMs: Double, val samples: Int, val method: String)

@Serializable
data class DatabaseInfo(val schemaVersion: Int, val tableRowCounts: Map<String, Long>)
