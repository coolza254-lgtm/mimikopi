package com.id.mimikopi.core

import com.id.mimikopi.core.config.LearningConfig
import com.id.mimikopi.core.diag.AppInfo
import com.id.mimikopi.core.diag.AudioInfo
import com.id.mimikopi.core.diag.DeviceInfo
import com.id.mimikopi.core.diag.DiagnosticsReport
import com.id.mimikopi.core.diag.MidiInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigAndDiagnosticsTest {
    @Test
    fun contentConfigFileMatchesCodeDefaults() {
        // Tests run with the module directory as working dir.
        val file = File("../content/config/learning.json")
        assertTrue(file.exists(), "missing ${file.absolutePath}")
        assertEquals(LearningConfig(), LearningConfig.fromJson(file.readText()))
    }

    @Test
    fun diagnosticsRoundTripKeepsExplicitNulls() {
        val report = DiagnosticsReport(
            generatedAtUtc = "2026-10-10T12:00:00Z",
            app = AppInfo("com.id.mimikopi", "0.1.0", 100, "release"),
            device = DeviceInfo("samsung", "SM-A546E", "a54x", "16", 36, listOf("arm64-v8a")),
            features = mapOf("android.software.midi" to true),
            audio = AudioInfo(48000, 192, currentRoute = listOf("BUILTIN_SPEAKER"), availableOutputs = listOf("BUILTIN_SPEAKER")),
            midi = MidiInfo(emptyList()),
            notes = listOf("framesPerBurst: measured from M3"),
        )
        val json = report.toJson()
        assertTrue("\"framesPerBurst\": null" in json, json)
        assertEquals(report, DiagnosticsReport.fromJson(json))
    }
}
