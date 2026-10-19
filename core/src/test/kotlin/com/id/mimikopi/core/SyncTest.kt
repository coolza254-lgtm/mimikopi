package com.id.mimikopi.core

import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.core.ear.ToneRun
import com.id.mimikopi.core.ear.ToneStats
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.progress.CheckMetrics
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.session.DayLog
import com.id.mimikopi.core.session.PracticeLog
import com.id.mimikopi.core.sync.ProgressMerge
import com.id.mimikopi.core.sync.SyncPayload
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncTest {
    private val interval = { box: Int -> listOf(1, 2, 4, 8, 16)[box - 1] }
    private val json = Json { ignoreUnknownKeys = true }
    private val lessonsSer = MapSerializer(String.serializer(), LessonRecord.serializer())

    private fun device(id: String, settings: Map<String, String>, items: List<ItemStatRow> = emptyList()) =
        SyncPayload(deviceId = id, deviceName = id, appVersion = "t", settings = settings, itemStats = items)

    /** What a device holds after applying a merge result. */
    private fun apply(local: SyncPayload, remote: SyncPayload): SyncPayload {
        val r = ProgressMerge.merge(local, remote, interval)
        val items = (local.itemStats.associateBy { it.itemId } + r.itemStats.associateBy { it.itemId }).values.sortedBy { it.itemId }
        return local.copy(settings = local.settings + r.settings, itemStats = items)
    }

    private fun log(vararg days: Pair<String, Map<String, Int>>) = PracticeLog.encode(days.map { DayLog(it.first, it.second) })

    private fun shown(p: SyncPayload) = ProgressMerge.combinedLog(PracticeLog.parse(p.settings[ProgressMerge.LOG]), ProgressMerge.parsePeers(p.settings[ProgressMerge.PEERS]))

    @Test
    fun practiceMinutesAreNeverCountedTwiceHoweverOftenDevicesSync() {
        var phone = device("phone", mapOf(ProgressMerge.LOG to log("2026-10-17" to mapOf("ladder" to 600, "ear" to 120))))
        var pc = device("pc", mapOf(ProgressMerge.LOG to log("2026-10-17" to mapOf("ladder" to 300))))
        repeat(3) {
            val p2 = apply(phone, pc)
            val c2 = apply(pc, phone)
            phone = p2; pc = c2
        }
        val expected = listOf(DayLog("2026-10-17", mapOf("ladder" to 900, "ear" to 120)))
        assertEquals(expected, shown(phone))
        assertEquals(expected, shown(pc))
        // The own logs were never touched.
        assertEquals(mapOf("ladder" to 600, "ear" to 120), PracticeLog.parse(phone.settings[ProgressMerge.LOG]).single().seconds)

        // More practice on the phone, then a sync: the PC's copy grows to the phone's new total, not added twice.
        phone = phone.copy(settings = phone.settings + (ProgressMerge.LOG to log("2026-10-17" to mapOf("ladder" to 660, "ear" to 120))))
        pc = apply(pc, phone)
        pc = apply(pc, phone)
        assertEquals(mapOf("ladder" to 960, "ear" to 120), shown(pc).single().seconds)
    }

    @Test
    fun aThirdDeviceTravelsThroughTheMiddleOne() {
        val laptop = device("laptop", mapOf(ProgressMerge.LOG to log("2026-10-16" to mapOf("song" to 200))))
        var pc = device("pc", mapOf(ProgressMerge.LOG to log("2026-10-16" to mapOf("song" to 100))))
        var phone = device("phone", mapOf(ProgressMerge.LOG to log("2026-10-16" to mapOf("song" to 50))))
        pc = apply(pc, laptop)
        phone = apply(phone, pc)
        assertEquals(350, shown(phone).single().seconds["song"])
        // The phone's own id coming back from the PC is ignored.
        pc = apply(pc, phone)
        phone = apply(phone, pc)
        assertEquals(350, shown(phone).single().seconds["song"])
        assertEquals(350, shown(pc).single().seconds["song"])
    }

    @Test
    fun mergingTwiceChangesNothingAndBothSidesEndTheSame() {
        val today = "2026-10-17"
        val a = device("a", mapOf(
            ProgressMerge.LESSONS to json.encodeToString(lessonsSer, mapOf("1.1" to LessonRecord("1.1", passDays = setOf("2026-10-10"), attempts = 3))),
            ProgressMerge.CHECKS to CheckRecord.encode(listOf(CheckRecord(SkillId.KEYS, 1, today, true, CheckMetrics(accuracy = 1.0), at = 10))),
            ProgressMerge.HELPER_COUNT to "4",
        ), listOf(ItemStatRow("note:C4:treble", 2, 2_000_000_000L, "11", 900)))
        val b = device("b", mapOf(
            ProgressMerge.LESSONS to json.encodeToString(lessonsSer, mapOf("1.1" to LessonRecord("1.1", triedDays = setOf("2026-10-11"), attempts = 5), "1.2" to LessonRecord("1.2", attempts = 1))),
            ProgressMerge.CHECKS to CheckRecord.encode(listOf(CheckRecord(SkillId.NOTES, 1, today, false, CheckMetrics(accuracy = 0.5), at = 5))),
            ProgressMerge.HELPER_COUNT to "7",
        ), listOf(ItemStatRow("key:C", 1, 1_000_000_000L, "1", null)))
        val a2 = apply(a, b)
        val b2 = apply(b, a)
        for (k in ProgressMerge.KEYS - setOf(ProgressMerge.LOG, ProgressMerge.PEERS)) assertEquals(a2.settings[k], b2.settings[k], k)
        assertEquals(a2.itemStats, b2.itemStats)
        assertTrue(!ProgressMerge.merge(a2, b, interval).changed)
        assertTrue(!ProgressMerge.merge(a2, b2, interval).changed)
        val lessons = json.decodeFromString(lessonsSer, a2.settings.getValue(ProgressMerge.LESSONS))
        assertEquals(LessonRecord("1.1", passDays = setOf("2026-10-10"), attempts = 5, triedDays = setOf("2026-10-11")), lessons["1.1"])
        assertEquals("7", a2.settings[ProgressMerge.HELPER_COUNT])
        // Tests of one day keep their time order across devices.
        assertEquals(listOf(SkillId.NOTES, SkillId.KEYS), CheckRecord.parse(a2.settings[ProgressMerge.CHECKS]).map { it.skill })
    }

    @Test
    fun aStepSentBackToPracticeStaysSentBack() {
        val reopened = LessonRecord("2.1", passDays = setOf("2026-10-15"), reopenedOn = "2026-10-14")
        val old = LessonRecord("2.1", passDays = setOf("2026-10-10"), drillDays = mapOf(0 to setOf("2026-10-10")))
        val merged = ProgressMerge.lessons(mapOf("2.1" to reopened), mapOf("2.1" to old)).getValue("2.1")
        assertEquals(setOf("2026-10-15"), merged.passDays)
        assertTrue(merged.drillDays.isEmpty())
        assertEquals("2026-10-14", merged.reopenedOn)
    }

    @Test
    fun theReviewItemSeenLastWinsAndANeverReviewedOneAlwaysLoses() {
        val day = 86_400_000L
        // Box 1 due day 11 = reviewed day 10; box 3 due day 13 = reviewed day 9: the first was reviewed later.
        val recent = ItemStatRow("note:E4:treble", 1, 11 * day, "10", 1500)
        val older = ItemStatRow("note:E4:treble", 3, 13 * day, "111", 800)
        assertEquals(listOf(recent), ProgressMerge.itemStats(listOf(older), listOf(recent), interval))
        assertEquals(emptyList(), ProgressMerge.itemStats(listOf(recent), listOf(older), interval))
        val fresh = ItemStatRow("note:E4:treble", 1, 0, "", null)
        assertEquals(emptyList(), ProgressMerge.itemStats(listOf(older), listOf(fresh), interval))
        assertEquals(listOf(older), ProgressMerge.itemStats(listOf(fresh), listOf(older), interval))
    }

    @Test
    fun aDeletedEarLevelDoesNotComeBack() {
        val level = FunctionalEar.custom("custom-1", 0, com.id.mimikopi.core.ear.EarScale.MAJOR, 1, false, listOf(0, 2, 4), 10, com.id.mimikopi.core.ear.CadenceMode.ALWAYS)
        val withLevel = device("pc", mapOf(
            ProgressMerge.EAR_LEVELS to FunctionalEar.levelsJson(listOf(level)),
            ProgressMerge.EAR_STATS to FunctionalEar.statsJson(mapOf("custom-1" to ToneStats(listOf(ToneRun("2026-10-01", 10, 8))))),
        ))
        val deletedHere = device("phone", mapOf(ProgressMerge.EAR_DELETED to "[\"custom-1\"]", ProgressMerge.EAR_LEVELS to "[]"))
        val phone = apply(deletedHere, withLevel)
        assertEquals(emptyList(), FunctionalEar.parseLevels(phone.settings[ProgressMerge.EAR_LEVELS]))
        assertTrue(FunctionalEar.parseStats(phone.settings[ProgressMerge.EAR_STATS]).isEmpty())
        val pc = apply(withLevel, deletedHere)
        assertEquals(emptyList(), FunctionalEar.parseLevels(pc.settings[ProgressMerge.EAR_LEVELS]))
    }

    @Test
    fun aRowThisDeviceCannotReadIsLeftAsItIs() {
        val local = device("a", mapOf(ProgressMerge.CHECKS to "{broken"))
        val remote = device("b", mapOf(ProgressMerge.CHECKS to CheckRecord.encode(listOf(CheckRecord(SkillId.EAR, 1, "2026-10-17", true)))))
        assertNull(ProgressMerge.merge(local, remote, interval).settings[ProgressMerge.CHECKS])
        assertEquals(setOf(ProgressMerge.LESSONS), ProgressMerge.progressRows(mapOf(ProgressMerge.LESSONS to "{}", "input.config" to "x", "ui.theme" to "DARK")).keys)
    }
}
