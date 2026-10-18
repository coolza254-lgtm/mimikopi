package com.id.mimikopi.desktop

import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.desktop.data.Store
import com.id.mimikopi.desktop.learning.LearningRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreTest {
    private fun tempStore() = Store(kotlin.io.path.createTempDirectory("mimikopi").toFile().resolve("data.json"))

    @Test
    fun valuesSurviveReopening() {
        val dir = kotlin.io.path.createTempDirectory("mimikopi").toFile()
        Store(dir.resolve("data.json")).apply { put("a", "1"); putItemStat(ItemStatRow("ear:cmp", 2, 5L, "10", null)); flush() }
        val again = Store(dir.resolve("data.json"))
        assertEquals("1", again.get("a"))
        assertEquals(2, again.itemStats()["ear:cmp"]?.box)
    }

    @Test
    fun backupsRoundTripInTheAndroidFormat() {
        val a = tempStore()
        a.put(Keys.THEME_MODE, "[]")
        a.put(Keys.LESSON_RECORDS, "{}")
        a.putItemStat(ItemStatRow("ear:int:7", 3, 9L, "111", null))
        val text = a.exportBackup("0.17.0")
        val b = tempStore()
        assertNull(b.importBackup(text))
        assertEquals("[]", b.get(Keys.THEME_MODE))
        assertEquals(3, b.itemStats()["ear:int:7"]?.box)
        // Not a backup: refused, nothing changed.
        assertNotNull(b.importBackup("{\"hello\": 1}"))
        assertEquals("[]", b.get(Keys.THEME_MODE))
    }

    @Test
    fun stepRecordsAreKeptInTheSharedRow() {
        val store = tempStore()
        val l = LearningRepository(store)
        val step = l.lessons.first()
        l.recordDrill(step, step.pass.requiredDrills.first(), met = true)
        assertEquals(1, l.records.value[step.id]?.passDays?.size)
        // Read back from the same row.
        assertEquals(l.records.value, LearningRepository(store).records.value)
    }
}
