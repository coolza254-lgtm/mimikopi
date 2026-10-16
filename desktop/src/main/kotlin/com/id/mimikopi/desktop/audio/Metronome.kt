package com.id.mimikopi.desktop.audio

import java.util.concurrent.locks.LockSupport

/**
 * Metronome clicks for the reading drills and the score reader (the Android engine counts them in audio frames).
 * One thread sends each click at its System.nanoTime instant through the current [Piano]; every click is recorded
 * as the moment it is heard (sent plus the piano's output latency), in nanoseconds since [epoch], until drained.
 */
class Metronome(private val piano: () -> Piano) {
    val epoch: Long = System.nanoTime()

    private class Run(val startAt: Long, val tickNanos: Double, val levels: IntArray, val volume: Float) {
        var index = 0L
    }

    private val lock = Object()
    private var run: Run? = null
    private val heard = ArrayList<Long>()
    private var thread: Thread? = null

    /** [levels]: one click level per tick of a bar (0 strong .. 3 sub), as on Android. */
    fun set(on: Boolean, bpm: Double, ticksPerBeat: Int, levels: IntArray, volume: Float) {
        synchronized(lock) {
            run = if (on && bpm > 0) Run(System.nanoTime() + START_DELAY, 60e9 / bpm / ticksPerBeat.coerceAtLeast(1),
                levels.copyOf().takeIf { it.isNotEmpty() } ?: intArrayOf(0), volume) else null
            if (thread == null && run != null) {
                thread = Thread({ loop() }, "mimikopi-metronome").apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }
            }
            lock.notifyAll()
        }
    }

    /** Clicks heard since the last call, in nanoseconds since [epoch]. */
    fun drain(): LongArray = synchronized(lock) { heard.toLongArray().also { heard.clear() } }

    private fun loop() {
        while (true) {
            val r: Run
            synchronized(lock) {
                while (run == null) lock.wait(1000)
                r = run!!
            }
            val at = r.startAt + (r.index * r.tickNanos).toLong()
            val wait = at - System.nanoTime()
            if (wait > 0) {
                LockSupport.parkNanos(minOf(wait, 5_000_000L))
                continue
            }
            val level = synchronized(lock) {
                if (run !== r) null else {
                    val l = r.levels[(r.index % r.levels.size).toInt()]
                    r.index++
                    heard += at - epoch + piano().latencyNanos
                    l
                }
            } ?: continue
            if (r.volume > 0f) piano().click(level, r.volume)
        }
    }

    private companion object {
        const val START_DELAY = 80_000_000L
    }
}
