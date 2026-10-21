package com.id.mimikopi.desktop.data

import com.id.mimikopi.core.backup.AttemptEventRow
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.backup.BackupCheck
import com.id.mimikopi.core.backup.BackupData
import com.id.mimikopi.core.backup.BackupFile
import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.backup.SessionRow
import com.id.mimikopi.core.backup.SettingRow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataTransferTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun attempt(at: Long, exercise: String = "1.1:E_KEYFIND:0") =
        AttemptRow(0, null, exercise, """{"seed":$at,"drill":0,"lesson":"1.1","completed":true,"inputSources":"MIDI","mode":"ANSWER"}""",
            at, "RIGHT", null, 0.9, null, null, true, 0)

    /** A phone backup as Android writes it: its own ids, starting at 1 like the computer's. */
    private fun phoneBackup() = BackupFile(
        applicationId = "com.id.mimikopi", appVersionName = "0.29.0", appVersionCode = 2900, databaseVersion = 1,
        createdAtUtc = "2026-10-20T10:00:00Z",
        data = BackupData(
            practiceSessions = listOf(SessionRow(1, 1_000, 2_000, 15, 600)),
            attempts = listOf(attempt(1_100).copy(id = 1, sessionId = 1), attempt(1_200, "2.1:E_NOTEID:0").copy(id = 2, sessionId = 1)),
            attemptEvents = listOf(AttemptEventRow(1, 1, 60, 60, null, 800, null, 90), AttemptEventRow(2, 2, 64, 65, null, 1200, null, 70)),
            itemStats = listOf(ItemStatRow("key:C", 2, 99_000, "11", 900)),
            settings = listOf(
                SettingRow("lesson.records", """{"1.1":{"lessonId":"1.1","passDays":["2026-10-19"],"attempts":3}}"""),
                SettingRow("private.recall.songs.v1", "phone-only"),
                SettingRow("ui.theme", "DARK"),
            ),
        ),
    )

    private fun setup(): Triple<Database, DataTransfer, AppDirs> {
        val dirs = AppDirs(tmp.newFolder())
        val db = Database.open(dirs.database)
        return Triple(db, DataTransfer(db, dirs, "0.30.0") { 1 }, dirs)
    }

    @Test
    fun data_survives_closing_and_opening_the_program() {
        val dirs = AppDirs(tmp.newFolder("data"))
        Database.open(dirs.database).use { db ->
            db.insertAttempt(attempt(5_000), listOf(AttemptEventRow(0, 0, 60, 60, null, 500, null, 80)))
            db.put("ui.theme", "LIGHT")
        }
        Database.open(dirs.database).use { db ->
            val rows = db.lessonAttempts("1.1")
            assertEquals(1, rows.size)
            assertEquals(1, db.events(rows[0].id).size)
            assertEquals("LIGHT", db.get("ui.theme"))
            assertTrue(db.lessonAttempts("1.10").isEmpty(), "1.1 is not a prefix match for 1.10's rows")
        }
    }

    @Test
    fun export_is_a_valid_android_backup_and_restores_exactly() {
        val (db, transfer, _) = setup()
        db.insertAttempt(attempt(5_000), listOf(AttemptEventRow(0, 0, 60, 61, null, 500, -12, 80)))
        db.putItemStat(ItemStatRow("note:G4:treble", 3, 7, "101", null))
        val file = File(tmp.root, "backup.json")
        transfer.export(file)
        val check = assertIs<BackupCheck.Ok>(transfer.read(file))
        assertEquals("com.id.mimikopi", check.backup.applicationId)
        val (other, otherTransfer, _) = setup()
        otherTransfer.restore(check.backup)
        assertEquals(db.export().attempts, other.export().attempts)
        assertEquals(db.export().attemptEvents, other.export().attemptEvents)
        assertEquals(db.export().itemStats, other.export().itemStats)
    }

    @Test
    fun merging_a_phone_backup_keeps_both_histories_and_is_idempotent() {
        val (db, transfer, dirs) = setup()
        db.insertAttempt(attempt(9_000, "1.1:E_KEYFIND:0"), listOf(AttemptEventRow(0, 0, 60, 60, null, 400, null, 80)))
        db.put("ui.theme", "LIGHT")
        val report = transfer.merge(phoneBackup())
        assertTrue(report.safetyCopy!!.isFile, "a safety copy is written before the import")
        val after = db.export()
        assertEquals(3, after.attempts.size)
        assertEquals(3, after.attemptEvents.size)
        assertEquals(1, after.practiceSessions.size)
        // The phone's attempts point at the imported session under its new local id, events at their own attempts.
        val session = after.practiceSessions.single().id
        assertEquals(2, after.attempts.count { it.sessionId == session })
        val phoneNote = after.attempts.single { it.exerciseId == "2.1:E_NOTEID:0" }
        assertEquals(65, after.attemptEvents.single { it.attemptId == phoneNote.id }.actualPitch)
        // Progress rows merge; private songs and device settings stay where they were.
        assertTrue(db.get("lesson.records")!!.contains("2026-10-19"))
        assertNull(db.get("private.recall.songs.v1"))
        assertEquals("LIGHT", db.get("ui.theme"))
        assertEquals("key:C", db.itemStats().single().itemId)

        val again = transfer.merge(phoneBackup())
        assertEquals(after.attempts, db.export().attempts)
        assertEquals(after.attemptEvents, db.export().attemptEvents)
        assertTrue(again.lines.first().contains("มีอยู่แล้ว"))
        assertTrue(transfer.autoBackups().size >= 2)
        assertEquals(dirs.deviceId, AppDirs(dirs.root).deviceId, "the device id is stable")
    }

    @Test
    fun a_computer_backup_merged_back_into_the_same_computer_adds_nothing() {
        val (db, transfer, _) = setup()
        transfer.merge(phoneBackup())
        val file = File(tmp.root, "pc.json")
        transfer.export(file)
        val before = db.export()
        transfer.merge((transfer.read(file) as BackupCheck.Ok).backup)
        assertEquals(before.attempts, db.export().attempts)
        assertEquals(before.practiceSessions, db.export().practiceSessions)
    }

    @Test
    fun broken_files_are_refused_before_anything_changes() {
        val (db, transfer, _) = setup()
        val bad = File(tmp.root, "bad.json").apply { writeText("{ not json") }
        assertIs<BackupCheck.Rejected>(transfer.read(bad))
        val foreign = File(tmp.root, "foreign.json").apply { writeText(phoneBackup().copy(applicationId = "other.app").toJson()) }
        assertIs<BackupCheck.Rejected>(transfer.read(foreign))
        assertTrue(db.export().totalRows == 0)
    }

    @Test
    fun a_failed_transaction_leaves_nothing_half_written() {
        val (db, _, _) = setup()
        runCatching {
            db.transaction {
                db.put("a", "1")
                db.putAll(mapOf("b" to "2"))
                error("power cut")
            }
        }
        assertNull(db.get("a"))
        assertNull(db.get("b"))
    }
}
