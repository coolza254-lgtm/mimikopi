package com.id.mimikopi.core

import com.id.mimikopi.core.backup.AttemptEventRow
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.backup.BackupCheck
import com.id.mimikopi.core.backup.BackupData
import com.id.mimikopi.core.backup.BackupFile
import com.id.mimikopi.core.backup.BackupValidator
import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.backup.SessionRow
import com.id.mimikopi.core.backup.SettingRow
import com.id.mimikopi.core.update.Sha256
import com.id.mimikopi.core.update.UpdateCheckResult
import com.id.mimikopi.core.update.UpdateManifest
import com.id.mimikopi.core.update.UpdatePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdatePolicyTest {
    // Shape of the real v0.1.0 latest.json produced by release.yml.
    private val sample = """
        {
          "schema": 1,
          "versionCode": 100,
          "versionName": "0.1.0",
          "tag": "v0.1.0",
          "apkUrl": "https://github.com/coolza254-lgtm/mimikopi-releases/releases/download/v0.1.0/mimikopi-v0.1.0.apk",
          "apkSize": 8894285,
          "sha256": "8ae0d6980c262673beff5837fe8bb646f3480cce1fb84cb2697ac9849e8ee995",
          "notesTh": "รุ่นแรก (M1)",
          "publishedAt": "2026-10-10T18:58:53Z",
          "futureField": true
        }
    """.trimIndent()

    @Test
    fun parsesRealManifestAndIgnoresUnknownFields() {
        val m = UpdateManifest.parse(sample)
        assertEquals(100, m.versionCode)
        assertEquals("รุ่นแรก (M1)", m.notesTh)
    }

    @Test
    fun newerVersionIsAvailableOtherwiseUpToDate() {
        val m = UpdateManifest.parse(sample)
        assertIs<UpdateCheckResult.Available>(UpdatePolicy.evaluate(m, installedVersionCode = 99))
        assertIs<UpdateCheckResult.UpToDate>(UpdatePolicy.evaluate(m, installedVersionCode = 100))
        assertIs<UpdateCheckResult.UpToDate>(UpdatePolicy.evaluate(m, installedVersionCode = 201))
    }

    @Test
    fun rejectsUnsafeOrMalformedManifests() {
        val m = UpdateManifest.parse(sample)
        fun reject(bad: UpdateManifest) = assertIs<UpdateCheckResult.Invalid>(UpdatePolicy.evaluate(bad, 1))
        reject(m.copy(apkUrl = "http://github.com/x.apk"))
        reject(m.copy(apkUrl = "https://evil.example.com/x.apk"))
        reject(m.copy(apkUrl = "https://github.com.evil.com/x.apk"))
        reject(m.copy(sha256 = "ABC"))
        reject(m.copy(sha256 = m.sha256.uppercase()))
        reject(m.copy(apkSize = 0))
        reject(m.copy(schema = 2))
    }

    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc".byteInputStream()),
        )
    }
}

class BackupTest {
    private val ids = setOf("com.id.mimikopi", "com.id.mimikopi.debug")

    private fun sample() = BackupFile(
        applicationId = "com.id.mimikopi",
        appVersionName = "0.2.0",
        appVersionCode = 200,
        databaseVersion = 1,
        createdAtUtc = "2026-10-11T08:00:00Z",
        data = BackupData(
            practiceSessions = listOf(SessionRow(1, 1000, 2000, 30, 1700)),
            attempts = listOf(AttemptRow(5, 1, "E_KEYFIND", """{"seed":42}""", 1100, "R", 60.0, 1.0, 0.95, 0, true, 0)),
            attemptEvents = listOf(AttemptEventRow(9, 5, 60, 60, 0, 12, 12, 100)),
            itemStats = listOf(ItemStatRow("key:C", 2, 3000, "11", 900)),
            settings = listOf(SettingRow("noteNameStyle", "BOTH")),
        ),
    )

    @Test
    fun roundTripKeepsEverything() {
        val text = sample().toJson()
        val check = BackupValidator.check(text, ids, currentDatabaseVersion = 1)
        assertIs<BackupCheck.Ok>(check)
        assertEquals(sample(), check.backup)
        assertEquals(5, check.backup.data.totalRows)
    }

    @Test
    fun rejectsForeignNewerOrInconsistentBackups() {
        fun rejected(b: BackupFile, dbVersion: Int = 1) =
            assertIs<BackupCheck.Rejected>(BackupValidator.check(b.toJson(), ids, dbVersion))
        rejected(sample().copy(applicationId = "com.other.app"))
        rejected(sample().copy(format = BackupFile.FORMAT + 1))
        rejected(sample().copy(databaseVersion = 2))
        rejected(sample().let { it.copy(data = it.data.copy(attemptEvents = listOf(AttemptEventRow(1, 999, null, null, null, null, null, null)))) })
        rejected(sample().let { it.copy(data = it.data.copy(settings = it.data.settings + SettingRow("noteNameStyle", "LETTER"))) })
        assertIs<BackupCheck.Rejected>(BackupValidator.check("{not json", ids, 1))
        assertIs<BackupCheck.Rejected>(BackupValidator.check("""{"hello":1}""", ids, 1))
    }

    @Test
    fun olderDatabaseVersionIsAccepted() {
        assertIs<BackupCheck.Ok>(BackupValidator.check(sample().toJson(), ids, currentDatabaseVersion = 3))
    }
}
