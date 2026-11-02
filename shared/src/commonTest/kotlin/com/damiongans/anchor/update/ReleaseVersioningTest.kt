package com.damiongans.anchor.update

import com.damiongans.anchor.update.ReleaseVersioning.isVersionNewer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersioningTest {

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
}
