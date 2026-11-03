package com.damiongans.anchor.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class PackagingSpecTest {

    /** SPEC-PROD-004 */
    @Test
    fun windowsMsiPackaging_configuresPerUserInstallFixedUpgradeUuidAndBothArchitectures() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "desktopApp/build.gradle.kts").isFile }
            ?: fail("repo root not found")
        val buildScript = File(root, "desktopApp/build.gradle.kts").readText()
        assertTrue(buildScript.contains("TargetFormat.Msi"))
        assertTrue(buildScript.contains("perUserInstall = true"))
        assertTrue(buildScript.contains("upgradeUuid = \"6f1c2b8e-4a7d-4e3b-9c55-0d8a1e2f7b31\""))

        val workflow = File(root, ".github/workflows/desktop.yml").readText()
        assertTrue(workflow.contains("windows-latest"))
        assertTrue(workflow.contains("windows-11-arm"))
        assertTrue(workflow.contains("arch: x64"))
        assertTrue(workflow.contains("arch: arm64"))
        assertTrue(workflow.contains("-windows-\${{ matrix.arch }}.msi"))
    }
}
