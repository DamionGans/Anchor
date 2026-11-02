package com.damiongans.anchor.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsArchTest {

    @Test
    fun mapsJvmArchitectureNames() {
        assertEquals(WindowsArch.X64, WindowsArch.fromOsArch("amd64"))
        assertEquals(WindowsArch.X64, WindowsArch.fromOsArch("x86_64"))
        assertEquals(WindowsArch.ARM64, WindowsArch.fromOsArch("aarch64"))
        assertEquals(WindowsArch.ARM64, WindowsArch.fromOsArch(" ARM64 "))
        assertEquals(WindowsArch.X64, WindowsArch.fromOsArch(null))
    }

    @Test
    fun assetMarkersAndOsDetection() {
        assertEquals("windows-x64", WindowsArch.X64.assetMarker)
        assertEquals("windows-arm64", WindowsArch.ARM64.assetMarker)
        assertTrue(WindowsArch.isWindows("Windows 11"))
        assertFalse(WindowsArch.isWindows("Linux"))
        assertFalse(WindowsArch.isWindows(null))
    }
}
