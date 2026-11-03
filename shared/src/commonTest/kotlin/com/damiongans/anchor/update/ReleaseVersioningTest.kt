package com.damiongans.anchor.update

import com.damiongans.anchor.update.ReleaseVersioning.isVersionNewer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersioningTest {

    /** SPEC-UPDATE-001 */
    @Test
    fun versionComparison() {
        assertTrue(isVersionNewer("v1.4.1", "1.4.0"))
        assertTrue(isVersionNewer("v1.5.0", "1.4.0"))
        assertTrue(isVersionNewer("v1.10.0", "1.4.0"))
        assertTrue(isVersionNewer("v2.0.0", "1.4.0"))
        assertTrue(isVersionNewer("v1.1", "1.0"))
        assertTrue(isVersionNewer("1.0.1", "1.0"))

        assertFalse(isVersionNewer("v1.4.0", "1.4.0"))
        assertFalse(isVersionNewer("v1.3.0", "1.4.0"))
        assertFalse(isVersionNewer("v1.4.0", "1.3.9", currentReleaseTag = "v1.4.0"))
        assertFalse(isVersionNewer("v1.0.20261101-0021-f1bedef", "1.4.0"))
        assertFalse(isVersionNewer("v1.0.0", "1.0"))
        assertFalse(isVersionNewer("0.9", "1.0"))
        assertFalse(isVersionNewer("", "1.4.0"))
        assertFalse(isVersionNewer("   ", "1.4.0"))
    }

    /** SPEC-UPDATE-001 */
    @Test
    fun buildReleaseInfo_picksPlatformAssetOrFallsBackToReleasePage() {
        val assets = listOf(
            ReleaseAsset("Anchor-v1.5.0.apk", "https://example.test/a.apk"),
            ReleaseAsset("Anchor-v1.5.0-unsigned.ipa", "https://example.test/a.ipa")
        )
        val android = ReleaseVersioning.buildReleaseInfo(
            "v1.5.0", "", null, "https://example.test/page", assets, ".apk", "1.4.0", "v1.4.0"
        )
        assertEquals("https://example.test/a.apk", android?.downloadUrl)
        assertEquals("v1.5.0", android?.releaseName)
        assertEquals(GitHubReleaseConfig.DEFAULT_RELEASE_NOTES, android?.releaseNotes)
        assertEquals(true, android?.isNewerVersion)

        val ios = ReleaseVersioning.buildReleaseInfo(
            "v1.5.0", "Anchor", "Notes", null, assets.take(1), ".ipa", "1.4.0", "v1.4.0"
        )
        assertEquals(GitHubReleaseConfig.RELEASES_PAGE_URL, ios?.downloadUrl)

        assertNull(ReleaseVersioning.buildReleaseInfo(" ", null, null, null, emptyList(), ".apk", "1.4.0", ""))
    }

    /** SPEC-UPDATE-001 */
    @Test
    fun buildReleaseInfo_picksThePerArchitectureWindowsInstaller() {
        val assets = listOf(
            ReleaseAsset("Anchor-v2.0.0.apk", "https://example.test/a.apk"),
            ReleaseAsset("Anchor-v2.0.0-windows-x64.msi", "https://example.test/x64.msi"),
            ReleaseAsset("Anchor-v2.0.0-windows-arm64.msi", "https://example.test/arm64.msi")
        )
        fun pick(marker: String?) = ReleaseVersioning.buildReleaseInfo(
            "v2.0.0", null, null, "https://example.test/page", assets, ".msi", "1.5.4", "",
            preferredAssetMarker = marker
        )?.downloadUrl
        assertEquals("https://example.test/arm64.msi", pick("windows-arm64"))
        assertEquals("https://example.test/x64.msi", pick("windows-x64"))
        // No marker, or a marker without a match: first installer with the extension
        assertEquals("https://example.test/x64.msi", pick(null))
        assertEquals("https://example.test/x64.msi", pick("windows-riscv64"))
    }
}
