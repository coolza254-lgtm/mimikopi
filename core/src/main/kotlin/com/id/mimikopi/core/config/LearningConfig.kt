package com.id.mimikopi.core.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tunable numbers from docs/learning-system.md. They are design defaults, not research results,
 * so they live in content/config/learning.json and are never hard-coded in the engine.
 * The defaults here mirror that file (a test keeps them in sync).
 */
@Serializable
data class LearningConfig(
    val cleanRun: CleanRunConfig = CleanRunConfig(),
    val timing: TimingConfig = TimingConfig(),
    val tempoRamp: TempoRampConfig = TempoRampConfig(),
    val srs: SrsConfig = SrsConfig(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = false; prettyPrint = true }

        fun fromJson(text: String): LearningConfig = json.decodeFromString(serializer(), text)
    }
}

/** learning-system.md 6.1 */
@Serializable
data class CleanRunConfig(
    val minNoteAccuracy: Double = 0.95,
    val minFractionWithinTiming: Double = 0.90,
    /** A stumble is a stop longer than one beat. */
    val maxStumbles: Int = 1,
)

/** learning-system.md 6.2 */
@Serializable
data class TimingConfig(
    /** Allowed onset error (± ms, after latency compensation) per stage 1..6, at tempo <= [slowTempoBpm]. */
    val stageToleranceMs: Map<Int, Int> = mapOf(1 to 150, 2 to 120, 3 to 110, 4 to 100, 5 to 90, 6 to 90),
    val slowTempoBpm: Int = 80,
    /** Above [slowTempoBpm] the tolerance is min(stage value, this fraction of one beat). */
    val fastTempoBeatFraction: Double = 0.25,
    val strictMultiplier: Double = 0.75,
    val normalMultiplier: Double = 1.0,
    val forgivingMultiplier: Double = 1.3,
)

/** learning-system.md 6.3 */
@Serializable
data class TempoRampConfig(
    val startFraction: Double = 0.5,
    val minStartBpm: Int = 40,
    val stepBpm: Int = 5,
    /** Above this tempo the step is a percentage instead of [stepBpm]. */
    val percentStepAboveBpm: Int = 80,
    val stepPercent: Double = 0.10,
    val cleanRunsToStepUp: Int = 3,
    val failedRunsToStepDown: Int = 2,
)

/** learning-system.md 6.6 */
@Serializable
data class SrsConfig(
    /** Review interval in days for boxes 1..5. */
    val boxIntervalsDays: List<Int> = listOf(1, 2, 4, 8, 16),
    /** Max response time for a note-reading answer to count as "fast enough". */
    val noteReadingMaxResponseMs: Long = 3000,
    val maxItemsPerSession: Int = 20,
)
