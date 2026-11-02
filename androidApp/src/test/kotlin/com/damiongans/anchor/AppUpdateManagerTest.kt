package com.damiongans.anchor

import com.damiongans.anchor.update.AppUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testVersionComparison() {
        // Current APK version is 1.7.0
        assertEquals("1.7.0", BuildConfig.VERSION_NAME)

        // Newer patch, minor, and major versions than 1.3.0
        assertTrue(AppUpdateManager.isVersionNewer("v1.3.1", "1.3.0"))
        assertTrue(AppUpdateManager.isVersionNewer("v1.4.0", "1.3.0"))
        assertTrue(AppUpdateManager.isVersionNewer("v1.10.0", "1.3.0"))
        assertTrue(AppUpdateManager.isVersionNewer("v2.0.0", "1.3.0"))
        assertTrue(AppUpdateManager.isVersionNewer("v1.1", "1.0"))
        assertTrue(AppUpdateManager.isVersionNewer("1.0.1", "1.0"))

        // Same or older versions (must return false so update dialog is never shown)
        assertFalse(AppUpdateManager.isVersionNewer("v1.3.0", "1.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("1.3.0", "1.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.2.6", "1.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.2.5", "1.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.2.4", "1.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.2.3", "1.2.5"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.2.2", "1.2.4"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.2.1", "1.2.3"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.2.0", "1.2.3"))
        // Legacy date-tagged v1.0.x releases must not be treated as newer than 1.2.x / 1.3.x
        assertFalse(AppUpdateManager.isVersionNewer("v1.0.20261101-0021-f1bedef", "1.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("1.0", "1.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.0", "1.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.0.0", "1.0"))
        assertFalse(AppUpdateManager.isVersionNewer("0.9", "1.0"))
        assertFalse(AppUpdateManager.isVersionNewer("", "1.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("   ", "1.3.0"))
    }
}
