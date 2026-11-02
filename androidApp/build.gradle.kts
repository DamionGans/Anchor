plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.damiongans.anchor"
  compileSdk { version = release(37) }

  val appVersionName: String = (project.findProperty("versionName") as? String)
    ?: System.getenv("APP_VERSION_NAME")
    ?: "1.5.5"
  val appVersionCode: Int = (project.findProperty("versionCode") as? String)?.toIntOrNull()
    ?: System.getenv("APP_VERSION_CODE")?.toIntOrNull()
    ?: 10505
  val appReleaseTag: String = (project.findProperty("releaseTag") as? String)
    ?: System.getenv("RELEASE_TAG")
    ?: "v$appVersionName"

  defaultConfig {
    applicationId = "com.damiongans.anchor"
    minSdk = 24
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName

    buildConfigField("String", "APP_RELEASE_TAG", "\"$appReleaseTag\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Distribution channels (see AGENTS.md "Distribution Channels & Monetization"):
  // - github: APK on GitHub Releases; checks GitHub Releases for updates (self-updating).
  // - fdroid: built by F-Droid; updates come from F-Droid, no update checker and no INTERNET
  //           permission (src/fdroid/AndroidManifest.xml).
  // - play:   paid Google Play listing (AAB); updates come from Play, no update checker and
  //           no INTERNET permission (src/play/AndroidManifest.xml).
  // No flavor adds billing, ads, analytics, or Google Play Services dependencies.
  flavorDimensions += "distribution"
  productFlavors {
    create("github") {
      dimension = "distribution"
      buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"GITHUB\"")
    }
    create("fdroid") {
      dimension = "distribution"
      buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"FDROID\"")
    }
    create("play") {
      dimension = "distribution"
      buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"PLAY_STORE\"")
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

// Reproducible builds: strip environment-specific timestamps and enforce deterministic file ordering in archives
tasks.withType<AbstractArchiveTask>().configureEach {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Shared KMP logic module + pure AndroidX & Kotlin Coroutines dependencies (zero Google Play Services dependencies for full AOSP compatibility)
dependencies {
  implementation(project(":shared"))
  implementation(project(":sharedUi"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
