package com.id.mimikopi.piano

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Test
import kotlin.test.*

class NoteSchedulerTest {
    @Test fun clearWaitsForInFlightNoteThenSilencesItAndShutdownRejectsFutureNotes() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val out = object : NoteOutput {
            override fun noteOn(note: Int, velocity: Int) {
                events += "on:$note"
                entered.countDown()
                check(release.await(2, TimeUnit.SECONDS))
            }
            override fun noteOff(note: Int) { events += "off:$note" }
            override fun sustain(down: Boolean) = Unit
        }
        val scheduler = NoteScheduler(out)
        val pool = Executors.newSingleThreadExecutor()
        try {
            scheduler.noteOn(System.nanoTime(), 60, 80)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val started = CountDownLatch(1)
            val cleared = pool.submit { started.countDown(); scheduler.clear() }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { cleared.get(80, TimeUnit.MILLISECONDS) }
            release.countDown()
            cleared.get(1, TimeUnit.SECONDS)
            assertEquals(listOf("on:60", "off:60"), events.toList())
            scheduler.shutdown()
            scheduler.noteOn(System.nanoTime(), 64, 80)
            assertEquals(listOf("on:60", "off:60"), events.toList())
        } finally { release.countDown(); scheduler.shutdown(); pool.shutdownNow() }
    }
}
