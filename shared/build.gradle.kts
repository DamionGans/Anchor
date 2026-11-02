import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Shared Kotlin Multiplatform module: all platform-agnostic Anchor logic (models, timing/delay
// compensation, click synthesis & sequencing, settings, update checks, and the MetronomeController
// state holder). Platform implementations live in `:app` (Android) and in
// `iosMain` + `iosApp/` (iOS).
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
  android {
    namespace = "com.damiongans.anchor.shared"
    compileSdk = 36
    minSdk = 24

    withHostTestBuilder {}
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

// Match the Android app's Java 11 bytecode level.
tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}
