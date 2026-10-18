package com.id.mimikopi.core

import com.id.mimikopi.core.update.WindowsManifest
import com.id.mimikopi.core.update.WindowsUpdateCheck
import com.id.mimikopi.core.update.WindowsUpdatePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WindowsUpdateTest {
    private val sha = "a".repeat(64)
    private fun manifest(version: String = "0.18.2", url: String = "https://github.com/coolza254-lgtm/mimikopi-releases/releases/download/v$version/mimikopi-windows-v$version.msi") =
        WindowsManifest(1, version, "v$version", url, 66_000_000, sha)

    @Test
    fun parsesTheReleaseFile() {
        val m = WindowsManifest.parse("""{"schema":1,"versionName":"0.18.2","tag":"v0.18.2","msiUrl":"https://github.com/x/y.msi","msiSize":5,"sha256":"$sha","notesTh":"ใหม่","extra":true}""")
        assertEquals("0.18.2", m.versionName)
        assertEquals("ใหม่", m.notesTh)
    }

    @Test
    fun newerVersionIsAvailableOnlyWhenNewer() {
        assertIs<WindowsUpdateCheck.Available>(WindowsUpdatePolicy.evaluate(manifest("0.18.2"), "0.18.1"))
        assertIs<WindowsUpdateCheck.UpToDate>(WindowsUpdatePolicy.evaluate(manifest("0.18.1"), "0.18.1"))
        assertIs<WindowsUpdateCheck.UpToDate>(WindowsUpdatePolicy.evaluate(manifest("0.18.0"), "0.18.1"))
        assertIs<WindowsUpdateCheck.Available>(WindowsUpdatePolicy.evaluate(manifest("0.18.10"), "0.18.9"))
    }

    @Test
    fun rejectsUnsafeManifests() {
        assertIs<WindowsUpdateCheck.Invalid>(WindowsUpdatePolicy.evaluate(manifest(url = "http://github.com/a.msi"), "0.18.1"))
        assertIs<WindowsUpdateCheck.Invalid>(WindowsUpdatePolicy.evaluate(manifest(url = "https://example.com/a.msi"), "0.18.1"))
        assertIs<WindowsUpdateCheck.Invalid>(WindowsUpdatePolicy.evaluate(manifest(url = "https://github.com/a.exe"), "0.18.1"))
        assertIs<WindowsUpdateCheck.Invalid>(WindowsUpdatePolicy.evaluate(manifest().copy(sha256 = "xyz"), "0.18.1"))
        assertIs<WindowsUpdateCheck.Invalid>(WindowsUpdatePolicy.evaluate(manifest().copy(msiSize = 0), "0.18.1"))
        assertIs<WindowsUpdateCheck.Invalid>(WindowsUpdatePolicy.evaluate(manifest().copy(schema = 2), "0.18.1"))
        // A development build has no version to compare.
        assertIs<WindowsUpdateCheck.Invalid>(WindowsUpdatePolicy.evaluate(manifest(), "dev"))
    }

    @Test
    fun comparesDottedVersionsNumerically() {
        assertTrue(WindowsUpdatePolicy.compareVersions("1.0", "0.99.99") > 0)
        assertEquals(0, WindowsUpdatePolicy.compareVersions("0.18", "0.18.0"))
    }
}
