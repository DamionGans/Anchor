package com.damiongans.anchor.update

import com.damiongans.anchor.platform.WindowsArch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopUpdateRepositoryTest {

    private val release = """
        {
          "tag_name": "v2.0.0",
          "name": "Anchor 2.0",
          "body": "Notes",
          "html_url": "https://github.com/DamionGans/Anchor/releases/tag/v2.0.0",
          "assets": [
            {"name": "Anchor-v2.0.0.apk", "browser_download_url": "https://example.test/a.apk"},
            {"name": "Anchor-v2.0.0-windows-x64.msi", "browser_download_url": "https://example.test/x64.msi"},
            {"name": "Anchor-v2.0.0-windows-arm64.msi", "browser_download_url": "https://example.test/arm64.msi"}
          ]
        }
    """.trimIndent()

    @Test
    fun picksTheInstallerForThisArchitecture() {
        val arm = DesktopUpdateRepository.parseRelease(release, "1.5.4", "v1.5.4", WindowsArch.ARM64)
        val x64 = DesktopUpdateRepository.parseRelease(release, "1.5.4", "v1.5.4", WindowsArch.X64)
        assertEquals("https://example.test/arm64.msi", arm?.downloadUrl)
        assertEquals("https://example.test/x64.msi", x64?.downloadUrl)
        assertEquals("Anchor 2.0", arm?.releaseName)
        assertTrue(arm?.isNewerVersion == true)
    }

    @Test
    fun malformedOrTaglessReleasesAreIgnored() {
        assertNull(DesktopUpdateRepository.parseRelease("not json", "1.5.4", "", WindowsArch.X64))
        assertNull(DesktopUpdateRepository.parseRelease("""{"name": "x"}""", "1.5.4", "", WindowsArch.X64))
    }
}
