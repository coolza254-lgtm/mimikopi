package com.id.mimikopi.desktop.port

import java.util.PriorityQueue
import java.util.concurrent.locks.LockSupport

/**
 * Plays piano notes at given System.nanoTime instants from one dedicated thread (accompaniment and
 * demo playback). parkNanos keeps the error to about a millisecond, much tighter than coroutine delays.
 * Copied from the Android app (piano/NoteScheduler.kt).
 */
class NoteScheduler(private val piano: PianoController) {
    private data class Item(val atNanos: Long, val note: Int, val velocity: Int, val on: Boolean, val seq: Long)

    private val queue = PriorityQueue<Item>(compareBy<Item>({ it.atNanos }, { it.seq }))
    private val lock = Object()
    private var seq = 0L
    private var thread: Thread? = null
    @Volatile private var running = false
    private val sounding = mutableSetOf<Int>()

    fun noteOn(atNanos: Long, note: Int, velocity: Int) = add(Item(atNanos, note, velocity, true, 0))
    fun noteOff(atNanos: Long, note: Int) = add(Item(atNanos, note, 0, false, 0))

    private fun add(item: Item) {
        synchronized(lock) {
            queue += item.copy(seq = seq++)
            lock.notifyAll()
        }
        ensureThread()
    }

    private fun ensureThread() {
        synchronized(lock) {
            if (thread != null) return
            running = true
            thread = Thread({ loop() }, "mimikopi-scheduler").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
                start()
            }
        }
    }

    private fun loop() {
        while (running) {
            val next: Item
            synchronized(lock) {
                while (running && queue.isEmpty()) lock.wait(500)
                if (!running) return
                next = queue.peek()!!
            }
            val wait = next.atNanos - System.nanoTime()
            if (wait > 0) {
                LockSupport.parkNanos(minOf(wait, 20_000_000L))
                continue
            }
            synchronized(lock) { if (queue.peek() === next) queue.poll() else null } ?: continue
            if (next.on) {
                piano.noteOn(next.note, next.velocity)
                synchronized(sounding) { sounding += next.note }
            } else {
                piano.noteOff(next.note)
                synchronized(sounding) { sounding -= next.note }
            }
        }
    }

    /** Drops everything queued and silences notes this scheduler started. */
    fun clear() {
        synchronized(lock) { queue.clear() }
        val notes = synchronized(sounding) { sounding.toList().also { sounding.clear() } }
        notes.forEach { piano.noteOff(it) }
    }

    fun shutdown() {
        clear()
        synchronized(lock) {
            running = false
            lock.notifyAll()
            thread = null
        }
    }
}
