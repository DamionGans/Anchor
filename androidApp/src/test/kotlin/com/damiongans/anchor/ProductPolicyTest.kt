package com.damiongans.anchor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level checks of product identity and the Android permission policy (AGENTS.md §2, §8).
 * Gradle runs unit tests with the module directory (`androidApp/`) as working directory.
 */
class ProductPolicyTest {

    private fun read(path: String): String = File(path).readText()

    private fun permissions(manifest: String): List<String> =
        Regex("""<uses-permission\s+android:name="([^"]+)"\s*/>""").findAll(manifest).map { it.groupValues[1] }.toList()

    /** SPEC-PROD-001 */
    @Test
    fun productNames_andTaglineMatchTheBrand() {
        val strings = read("src/main/res/values/strings.xml")
        assertTrue(strings.contains("""<string name="app_name">Anchor</string>"""))
        assertTrue(strings.contains("""<string name="app_full_name">Anchor Metronome</string>"""))

        val metadata = read("../metadata.json")
        val name = Regex(""""name":\s*"([^"]+)"""").find(metadata)!!.groupValues[1]
        assertEquals("Anchor Metronome", name)
        assertTrue("store titles must fit 30 characters", name.length <= 30)
        assertTrue(metadata.contains("Rock-solid time for serious play"))
        assertTrue("no emojis in the product name", name.all { it.code < 0x2000 })
    }

    /** SPEC-PROD-003 */
    @Test
    fun mainManifest_requestsOnlyTheAllowedPermissions() {
        val allowed = setOf(
            "android.permission.INTERNET",
            "android.permission.VIBRATE",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.POST_NOTIFICATIONS"
        )
        val main = read("src/main/AndroidManifest.xml")
        assertEquals(allowed, permissions(main).toSet())
        assertTrue(main.contains("""android:usesCleartextTraffic="false""""))
        listOf("REQUEST_INSTALL_PACKAGES", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE")
            .forEach { assertTrue("$it must never be requested", !main.contains(it)) }
    }

    /** SPEC-PROD-003 */
    @Test
    fun storeFlavors_removeTheInternetPermission() {
        for (flavor in listOf("fdroid", "play")) {
            val manifest = read("src/$flavor/AndroidManifest.xml").replace(Regex("""\s+"""), " ")
            assertTrue(
                "$flavor must remove INTERNET",
                manifest.contains("""android:name="android.permission.INTERNET" tools:node="remove"""")
            )
        }
    }
}
