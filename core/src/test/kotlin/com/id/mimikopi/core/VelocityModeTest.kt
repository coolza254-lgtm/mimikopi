package com.id.mimikopi.core

import com.id.mimikopi.core.input.VelocityMode
import kotlin.test.Test
import kotlin.test.assertEquals

class VelocityModeTest {
    @Test
    fun offPlaysEveryKeyAtOneLevel() {
        val off = VelocityMode(on = false, fixed = VelocityMode.SOFT)
        assertEquals(listOf(50, 50, 50), listOf(1, 64, 127).map(off::sound))
        assertEquals(listOf(1, 64, 127), listOf(1, 64, 127).map(VelocityMode(on = true)::sound))
    }

    @Test
    fun startsOffAtMediumAndSurvivesARoundTrip() {
        assertEquals(VelocityMode(false, VelocityMode.MEDIUM), VelocityMode.parse(null))
        assertEquals(VelocityMode(false, VelocityMode.MEDIUM), VelocityMode.parse("junk"))
        for (m in listOf(VelocityMode(true), VelocityMode(false, 50), VelocityMode(false, 105))) assertEquals(m.sound(90), VelocityMode.parse(m.encode()).sound(90))
        assertEquals(127, VelocityMode.parse("off:500").fixed)
    }
}
