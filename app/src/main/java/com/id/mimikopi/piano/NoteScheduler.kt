package com.id.mimikopi.piano

import java.util.PriorityQueue
import java.util.concurrent.locks.LockSupport

/** Playback destination used by the ear sequencer. */
interface ScheduledNotes {
    fun noteOn(atNanos: Long, note: Int, velocity: Int)
    fun noteOff(atNanos: Long, note: Int)
    fun clear()
}

/**
 * Plays piano notes at given System.nanoTime instants from one dedicated thread (accompaniment and
 * demo playback). parkNanos keeps the error to about a millisecond, much tighter than coroutine delays.
 */
class NoteScheduler(private val piano: NoteOutput) : ScheduledNotes {
    private data class Item(val atNanos: Long, val note: Int, val velocity: Int, val on: Boolean, val seq: Long)

    private val queue = PriorityQueue<Item>(compareBy<Item>({ it.atNanos }, { it.seq }))
    private val lock = Object()
    private var seq = 0L
    private var thread: Thread? = null
    @Volatile private var running = false
    private var closed = false
    private val sounding = mutableSetOf<Int>()

    override fun noteOn(atNanos: Long, note: Int, velocity: Int) = add(Item(atNanos, note, velocity, true, 0))
    override fun noteOff(atNanos: Long, note: Int) = add(Item(atNanos, note, 0, false, 0))

    private fun add(item: Item) {
        synchronized(lock) {
            if (closed) return
            queue += item.copy(seq = seq++)
            lock.notifyAll()
        }
        ensureThread()
    }

    private fun ensureThread() {
        synchronized(lock) {
            if (closed || thread != null) return
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
            // Delivery and clear share a lock: an old note cannot sneak in after clear has returned.
            synchronized(lock) {
                if (running && queue.peek() === next) {
                    queue.poll()
                    if (next.on) { piano.noteOn(next.note, next.velocity); sounding += next.note }
                    else { piano.noteOff(next.note); sounding -= next.note }
                }
            }
        }
    }

    /** Drops everything queued and silences notes this scheduler started. */
    override fun clear() {
        synchronized(lock) { clearLocked() }
    }

    private fun clearLocked() {
        queue.clear()
        sounding.forEach(piano::noteOff)
        sounding.clear()
    }

    /** Terminal; screen lifecycle pauses use clear(), and final ViewModel disposal uses shutdown(). */
    fun shutdown() {
        synchronized(lock) {
            closed = true
            running = false
            clearLocked()
            lock.notifyAll()
            thread?.let(LockSupport::unpark)
            thread = null
        }
    }
}
