import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Windows desktop app (x64 + arm64): Compose Multiplatform Desktop host for the shared UI
// (`:sharedUi`) and the shared logic + desktop platform code (`:shared`, desktopMain).
// Packaged as one MSI per architecture, each built on a matching runner (jpackage cannot
// cross-build): Anchor-vX.Y.Z-windows-x64.msi / Anchor-vX.Y.Z-windows-arm64.msi.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.kotlin.compose)
}

val appVersionName: String = (project.findProperty("versionName") as? String)
  ?: System.getenv("APP_VERSION_NAME")
  ?: "1.7.0"

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  implementation(project(":sharedUi"))
  // Skiko/AWT natives for the machine that builds the MSI (windows-x64 or windows-arm64).
  implementation(compose.desktop.currentOs)
  implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
  application {
    mainClass = "com.damiongans.anchor.desktop.MainKt"
    jvmArgs += listOf("-Danchor.version=$appVersionName")

    nativeDistributions {
      targetFormats(TargetFormat.Msi)
      packageName = "Anchor"
      packageVersion = appVersionName
      description = "Anchor Metronome"
      vendor = "Damion Gans"
      copyright = "Copyright Damion Gans. Apache License 2.0."
      licenseFile.set(rootProject.file("LICENSE"))
      // jlink modules: javax.sound (java.desktop), settings (java.prefs), update check (java.net.http)
      modules("java.desktop", "java.prefs", "java.net.http", "jdk.unsupported")

      windows {
        iconFile.set(project.file("icons/anchor.ico"))
        menuGroup = "Anchor"
        menu = true
        shortcut = true
        dirChooser = true
        // Installs per user (no administrator rights needed); keep this UUID forever so
        // new MSIs upgrade existing installations in place.
        perUserInstall = true
        upgradeUuid = "6f1c2b8e-4a7d-4e3b-9c55-0d8a1e2f7b31"
      }
    }
  }
}
