import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Shared Compose Multiplatform UI: the single-screen metronome UI (theme, components, dialogs,
// icons) used by the Android app (`:androidApp`) and the Windows app (`:desktopApp`). The iPhone
// app keeps its native SwiftUI UI. Platform differences are limited to `ui/platform/PlatformUi`.
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  android {
    namespace = "com.damiongans.anchor.ui"
    compileSdk = 37
    minSdk = 24
  }

  jvm("desktop") {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":shared"))
      api(libs.jetbrains.compose.runtime)
      api(libs.jetbrains.compose.foundation)
      api(libs.jetbrains.compose.ui)
      api(libs.jetbrains.compose.material3)
    }
    androidMain.dependencies {
      implementation(libs.androidx.core.ktx)
    }
  }
}

// Android compilations match the Android app's Java 11 bytecode level; the desktop target uses 17.
tasks.withType<KotlinJvmCompile>().configureEach {
  if (!name.contains("Desktop")) {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
  }
}
