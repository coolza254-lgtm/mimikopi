package com.id.mimikopi.desktop.session

import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.audio.NativeAudio
import com.id.mimikopi.desktop.audio.Piano
import com.id.mimikopi.desktop.data.AppDirs
import com.id.mimikopi.desktop.data.Database
import com.id.mimikopi.desktop.learning.SettingKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The click self-test on the null output: clicks are counted in frames, so their spacing must be exact. */
class MetronomeSessionTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun selfTestMeasuresEvenClicksAndSettingsAreSaved() {
        assumeTrue(NativeAudio.available)
        val dirs = AppDirs(tmp.newFolder())
        val s = Services(dirs, Database.open(dirs.database), Piano(nullDevice = true), "test", startMidi = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val m = MetronomeSession(s, scope)
            m.enter()
            assertTrue(m.ready.value)
            m.setBpm(150)
            val r = assertNotNull(runBlocking { m.selfTest(seconds = 3) })
            assertEquals(150, r.bpm)
            assertTrue(r.report.clicks >= 5, "clicks: ${r.report.clicks}")
            assertTrue(r.report.maxAbsMs < 0.1, "frame-counted clicks are evenly spaced on the fitted clock: ${r.report}")
            assertTrue(r.clockNoiseMaxMs < 50, "stamps stay near the fitted clock: $r")
            println("click self-test on the null output: $r")
            assertEquals(false, m.running.value, "the click stops after the test")
            m.leave()
            s.awaitPendingWrites()
            assertTrue(s.db.get(SettingKeys.METRONOME_SETTINGS)!!.contains("150"))
            assertTrue(s.db.get(SettingKeys.PC_CLICK_TEST) != null)
        } finally {
            scope.cancel()
            s.close()
        }
    }
}
