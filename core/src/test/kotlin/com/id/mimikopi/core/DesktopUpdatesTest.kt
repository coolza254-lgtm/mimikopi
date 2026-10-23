package com.id.mimikopi.core

import com.id.mimikopi.core.update.DesktopUpdates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopUpdatesTest {
    private fun release(tag: String, msiUrl: String = "https://github.com/coolza254-lgtm/mimikopi/releases/download/$tag/Mimikopi.msi", size: Long = 79_000_000, draft: Boolean = false) = """
        {"tag_name":"$tag","draft":$draft,"body":"notes $tag","assets":[
          {"name":"Mimikopi-x.msi","browser_download_url":"$msiUrl","size":$size},
          {"name":"SHA256SUMS.txt","browser_download_url":"https://github.com/coolza254-lgtm/mimikopi/releases/download/$tag/SHA256SUMS.txt","size":192}]}
    """

    private fun list(vararg r: String) = DesktopUpdates.parse("[" + r.joinToString(",") + "]")

    @Test
    fun theNewestHigherDesktopBuildIsOffered() {
        val releases = list(release("desktop-v0.31.2"), release("desktop-v0.31.10"), release("desktop-v0.30.0-89905ae"), release("v0.29.0"))
        assertEquals("0.31.10", DesktopUpdates.pick(releases, "0.31.2")?.version)
        assertEquals("0.31.10", DesktopUpdates.pick(releases, "0.30.0")?.version)
        assertNull(DesktopUpdates.pick(releases, "0.31.10"))
        assertNull(DesktopUpdates.pick(releases, "0.32.0"))
    }

    @Test
    fun onlyAcceptableInstallersCount() {
        assertNull(DesktopUpdates.pick(list(release("desktop-v0.40.0", msiUrl = "http://github.com/x.msi")), "0.31.0"))
        assertNull(DesktopUpdates.pick(list(release("desktop-v0.40.0", msiUrl = "https://example.com/x.msi")), "0.31.0"))
        assertNull(DesktopUpdates.pick(list(release("desktop-v0.40.0", size = 0)), "0.31.0"))
        assertNull(DesktopUpdates.pick(list(release("desktop-v0.40.0", draft = true)), "0.31.0"))
    }

    @Test
    fun checksumsAreReadFromTheSumsFile() {
        val hex = "a".repeat(64)
        val sums = "${"b".repeat(64)} *out/Mimikopi-0.31.0-windows-app.zip\n$hex *out/Mimikopi-0.31.0.msi\n"
        assertEquals(hex, DesktopUpdates.checksum(sums, "Mimikopi-0.31.0.msi"))
        assertNull(DesktopUpdates.checksum(sums, "other.msi"))
        assertNull(DesktopUpdates.checksum("xyz *Mimikopi.msi", "Mimikopi.msi"))
    }
}
