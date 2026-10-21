package com.id.mimikopi.core

import com.id.mimikopi.core.score.MeterPulse
import kotlin.test.*
import org.junit.Test

class MeterPulseTest {
    @Test fun compoundMetersKeepThreeEighthsPerClickAndWholeBarAccents() {
        for (n in listOf(6, 9, 12)) {
            val pulse = MeterPulse.of(n, 8)
            assertEquals(n / 3, pulse.clicksPerMeasure)
            assertEquals(1.5, pulse.quarterBeatsPerClick)
            assertEquals(60.0, pulse.nativeBpm(90.0))
            assertEquals(1000.0, pulse.clickMillis(90.0))
            assertEquals(n / 2.0, pulse.clicksPerMeasure * pulse.quarterBeatsPerClick)
            assertEquals(0, pulse.levels.first())
            assertTrue(pulse.levels.drop(1).all { it == 2 })
        }
        assertEquals(4, MeterPulse.of(6, 8).countInClicks)
        assertEquals(4, MeterPulse.of(12, 8).countInClicks)
    }

    @Test fun nativeSubdivisionAndScoreClockDescribeTheSameClickInterval() {
        for ((n, d) in listOf(4 to 4, 3 to 4, 2 to 4, 2 to 2, 3 to 8, 6 to 8, 12 to 8)) {
            val p = MeterPulse.of(n, d)
            for (bpm in listOf(30.0, 90.0, 220.0)) {
                assertEquals(p.clickMillis(bpm), 60000 / p.nativeBpm(bpm) / p.ticksPerBeat, .00001)
            }
        }
        assertEquals(90.0, MeterPulse.of(4, 4).nativeBpm(90.0))
        assertEquals(4, MeterPulse.of(2, 2).clicksPerMeasure)
        assertEquals(2, MeterPulse.of(3, 8).ticksPerBeat)
    }
}
