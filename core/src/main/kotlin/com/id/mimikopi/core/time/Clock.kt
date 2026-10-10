package com.id.mimikopi.core.time

/** Wall-clock source. Injected everywhere so scheduling logic is testable with a fake clock. */
fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000
