package com.id.mimikopi.core

import com.id.mimikopi.core.recall.PrivatePhrase
import com.id.mimikopi.core.recall.PrivateRecall
import com.id.mimikopi.core.recall.PrivateSong
import com.id.mimikopi.core.recall.PrivateSongSync
import com.id.mimikopi.core.recall.RecallAttempt
import com.id.mimikopi.core.recall.RecallHand
import com.id.mimikopi.core.recall.RecallOutcome
import com.id.mimikopi.core.recall.TargetSong
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.core.sync.SyncPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivateSongSyncTest {
    private fun played(p: PrivatePhrase, id: String, day: String) =
        PrivateRecall.record(p, RecallAttempt(id, day, RecallHand.RIGHT, RecallOutcome.INDEPENDENT, p.revision, presses = 4, unaidedConfirmed = true, beforeViewing = true))

    private val base = PrivatePhrase("p1", "ช่วง 1", 10, 20)

    @Test
    fun practiceFromBothDevicesIsKeptAndTheNewerEditWins() {
        val phone = PrivateSong("s", "I WISH", phrases = listOf(played(base, "a1", "2026-10-20")))
        val pc = PrivateSong("s", "I WISH", phrases = listOf(PrivateRecall.editChords(played(base, "a2", "2026-10-21"), listOf("A♭", "B♭", "Gm7", "Cm"), at = 5)))
        val merged = PrivateSongSync.merge(listOf(phone), listOf(pc), emptySet())
        val p = merged.single().phrases.single()
        assertEquals(listOf("a1", "a2"), p.attempts.map { it.id })
        assertEquals(listOf("2026-10-20", "2026-10-21"), PrivateRecall.rememberedDays(p, RecallHand.RIGHT))
        assertEquals(listOf("A♭", "B♭", "Gm7", "Cm"), p.chords)
        merged.forEach(PrivateRecall::validate)
        // The same from either side, and merging again changes nothing.
        assertEquals(merged.single().phrases, PrivateSongSync.merge(listOf(pc), listOf(phone), emptySet()).single().phrases)
        assertEquals(merged, PrivateSongSync.merge(merged, listOf(pc), emptySet()))
    }

    @Test
    fun newSongsArriveAndDeletedOnesStayGone() {
        val a = PrivateSong("a", "恋愛レボリューション21", phrases = listOf(base, PrivatePhrase("p2", "ช่วง 2", 0, 8)))
        val b = PrivateSong("b", "心のプラカード")
        val merged = PrivateSongSync.merge(listOf(b), listOf(a), setOf(PrivateSongSync.phraseKey("a", "p1")))
        assertEquals(listOf("b", "a"), merged.map { it.id })
        assertEquals(listOf("p2"), merged[1].phrases.map { it.id })
        assertEquals(listOf("b"), PrivateSongSync.merge(listOf(b), listOf(a), setOf(PrivateSongSync.songKey("a"))).map { it.id })
    }

    @Test
    fun theProgressMergeCarriesSongsAndTheLatestGoal() {
        val song = PrivateSong("s", "I WISH")
        val local = SyncPayload(deviceId = "phone", deviceName = "โทรศัพท์", appVersion = "x",
            settings = mapOf(ProgressMerge.TARGET to TargetSong.encodeChoice("old", 1)))
        val remote = SyncPayload(deviceId = "pc", deviceName = "คอม", appVersion = "x", settings = mapOf(
            PrivateSongSync.SONGS to PrivateSongSync.encodeSongs(listOf(song)),
            ProgressMerge.TARGET to TargetSong.encodeChoice("s", 2),
        ))
        val r = ProgressMerge.merge(local, remote) { 1 }
        assertEquals(listOf(song), PrivateSongSync.decodeSongs(r.settings[PrivateSongSync.SONGS]))
        assertEquals("s", TargetSong.chosenId(r.settings[ProgressMerge.TARGET]))
        assertTrue(ProgressMerge.merge(remote, local) { 1 }.settings[ProgressMerge.TARGET] == null, "the later choice is already there")
    }
}
