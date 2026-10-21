package com.id.mimikopi.recall

import com.id.mimikopi.core.recall.*
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.data.db.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.Test

class PrivateSongRepositoryTest {
    private class Dao : SettingsDao {
        val values = mutableMapOf<String, String>()
        var fail = false
        override suspend fun get(key: String) = values[key]
        override suspend fun put(setting: SettingEntity) { check(!fail); values[setting.key] = setting.value }
        override suspend fun delete(key: String) { values.remove(key) }
        override suspend fun withPrefix(prefix: String) = values.filterKeys { it.startsWith(prefix) }.map { SettingEntity(it.key, it.value) }
    }
    @Test fun saveReopenAndImportCopyKeepDataWithoutOverwritingTheOriginal() = runBlocking {
        val dao = Dao(); val repo = PrivateSongRepository(dao)
        repo.add("เพลงส่วนตัว", "")
        val id = repo.songs.value.single().id
        repo.addPhrase(id, PrivatePhrase("p", "วลี", 0, 8, "คำใบ้"))
        val first = repo.songs.value.single()
        val reopened = PrivateSongRepository(dao); reopened.reload()
        assertEquals(first, reopened.songs.value.single())
        reopened.importCopy(PrivateRecall.export(first))
        assertEquals(2, reopened.songs.value.size)
        assertEquals(2, reopened.songs.value.map { it.id }.distinct().size)
        assertEquals(first, reopened.songs.value.first())
        assertEquals(first.phrases, reopened.songs.value.last().phrases)
    }
    @Test fun changingReferencePreservesOldAttemptsButRequiresNewEvidence() = runBlocking {
        val repo = PrivateSongRepository(Dao());repo.add("เพลง", "")
        val id = repo.songs.value.single().id
        val a = RecallAttempt("a", "2026-10-21", RecallHand.RIGHT, RecallOutcome.INDEPENDENT, 1, presses = 3, unaidedConfirmed = true)
        repo.addPhrase(id, PrivateRecall.record(PrivatePhrase("p", "วลี", 0, 8), a))
        repo.editSong(id, "ชื่อใหม่", "https://youtu.be/abcdefghijk")
        val p = repo.songs.value.single().phrases.single()
        assertEquals(2, p.revision)
        assertEquals(listOf(a), p.attempts)
        assertTrue(PrivateRecall.current(p, RecallHand.RIGHT).isEmpty())
    }
    @Test fun malformedStoredDataAndFailedWritesDoNotSilentlyReplaceTheOriginal() = runBlocking {
        val dao = Dao(); val repo = PrivateSongRepository(dao);repo.add("เพลง", "")
        val previous = repo.songs.value
        val raw = dao.values[PrivateSongRepository.KEY]
        dao.fail = true
        assertFails { repo.add("อีกเพลง", "") }
        assertEquals(previous, repo.songs.value)
        assertEquals(raw, dao.values[PrivateSongRepository.KEY])
        dao.fail = false
        dao.values[PrivateSongRepository.KEY] = "bad data"
        assertFails { repo.add("อีกเพลง", "") }
        assertEquals("bad data", dao.values[PrivateSongRepository.KEY])
    }
    @Test fun privateSongsStayOutsideTheLanSyncAllowlist() {
        assertFalse(PrivateSongRepository.KEY in ProgressMerge.KEYS)
    }
}
