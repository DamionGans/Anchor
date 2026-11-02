import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Shared Kotlin Multiplatform module: all platform-agnostic Anchor logic (models, timing/delay
// compensation, click synthesis & sequencing, settings, update checks, and the MetronomeController
// state holder). Platform implementations live in `:androidApp` (Android), in
// `iosMain` + `iosApp/` (iOS), and in `desktopMain` (JVM desktop: Windows x64 + arm64, see
// docs/windows.md; no desktop app module yet).
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
  android {
    namespace = "com.damiongans.anchor.shared"
    compileSdk = 37
    minSdk = 24

    withHostTestBuilder {}
  }

  // JVM desktop target (Windows x64 + arm64). Kotlin/Native has no Windows-on-ARM64 target, so
  // Windows support runs on the JVM.
  jvm("desktop") {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }

  val xcFramework = XCFramework("AnchorShared")
  listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "AnchorShared"
      isStatic = true
      xcFramework.add(this)
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
    // Desktop only (GitHub release JSON parsing); never part of commonMain or the Android APK.
    val desktopMain by getting {
      dependencies {
        implementation(libs.kotlinx.serialization.json)
      }
    }
    // Kotlin/Native cinterop opt-ins for every iOS source set (dependent source sets such as
    // iosArm64Main must use the same opt-ins as iosMain).
    matching { it.name.startsWith("ios") }.configureEach {
      languageSettings {
        optIn("kotlinx.cinterop.ExperimentalForeignApi")
        optIn("kotlinx.cinterop.BetaInteropApi")
      }
    }
  }
}

// Android compilations match the Android app's Java 11 bytecode level; the desktop target uses 17.
tasks.withType<KotlinJvmCompile>().configureEach {
  if (!name.contains("Desktop")) {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
  }
}
