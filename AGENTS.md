# Anchor Metronome — AI Agent & Developer Guidelines (`AGENTS.md`)

This document is the authoritative source of truth for all AI coding agents (AI Studio, Gemini, Claude Code, GitHub Copilot, Cursor, Windsurf, Codex) and human contributors working on **Anchor Metronome** (short name **Anchor**, formerly Pulse Metronome) for Android and iPhone.

---

## 1. Self-Updating Living Document Mandate (READ FIRST)

1. **Always Read Before Acting**: Every AI agent working on this repository must read `AGENTS.md` before modifying code, build scripts, or documentation.
2. **Automatic Synchronization**: Whenever a change is made to the project—whether explicitly instructed by the user or decided by the agent to solve a problem—you **must update `AGENTS.md` in the same turn/commit** if the change alters:
   - Architectural patterns, modules, or file structure
   - Audio synthesis, timing, or delay compensation behavior
   - UI/UX constraints, features, or persisted settings
   - Build configuration, dependencies, or release workflows
   - Standing user preferences or workflows
3. **Keep Pointers Intact**: `CLAUDE.md`, `.github/copilot-instructions.md`, and `README.md` point to `AGENTS.md`. Do not duplicate diverging rules across files; keep all canonical guidelines in `AGENTS.md`.

---

## 2. Core Product Philosophy & Scope

- **Focused, High-Precision Instrument**: **Anchor Metronome** (**Anchor**) is a clean, distraction-free, sample-accurate metronome.
- **Brand Name (Full vs. Short) & Canonical Tagline**:
  - **Full App Name**: **Anchor Metronome** (used to prevent trademark infringement in `metadata.json`, `<application android:label="@string/app_full_name">`, the in-app About sheet in `SettingsBottomSheet.kt`, `MediaMetadata.METADATA_KEY_ALBUM`, `README.md` / `docs/architecture.md` headings, website `<title>` / Open Graph tags, and GitHub Release titles).
  - **Short Name / References**: **Anchor** (kept for the home screen launcher icon label `<activity android:label="@string/app_name">`, the iOS home screen name `CFBundleDisplayName` in `iosApp/project.yml`, the top-bar header `"ANCHOR"` in `MetronomeScreen.kt`, package namespace and applicationId `com.damiongans.anchor`, APK filename `Anchor-vX.Y.Z.apk`, and concise body copy).
  - **Canonical Tagline**: **"Rock-solid time for serious play"** across `README.md`, `website/index.html`, `metadata.json`, `MetronomeScreen.kt`, and the in-app About sheet (`SettingsBottomSheet.kt`). Never describe Anchor as a general "timekeeping" app—it is a musical metronome.
  - **Repository URL**: **`DamionGans/Anchor`** (`https://github.com/DamionGans/Anchor`, Pages `https://damiongans.github.io/Anchor/`). Both `AppUpdateManager.kt` and `website/app.js` check `DamionGans/Anchor` first and automatically fall back to `DamionGans/PulseMetronome` so releases resolve seamlessly before and after renaming the GitHub repository.
- **Distribution Channels & Monetization (standing decision)**: Anchor is **free and open source on GitHub and F-Droid** and sold as an **upfront paid app (one-time price, identical features) on Google Play and the Apple App Store** for side income. Rules:
  - **Never** add ads, subscriptions, in-app purchases/billing SDKs (Play Billing, StoreKit), analytics, accounts, or feature gating between channels. All channels ship the same features.
  - The channel is a shared `DistributionChannel` (`GITHUB`, `FDROID`, `PLAY_STORE`, `APP_STORE`) in `update/ReleaseVersioning.kt`, passed via `AppVersionInfo.channel`. Only `GITHUB` builds self-update (`selfUpdates`); `MetronomeController` skips update checks otherwise and exposes `canCheckForUpdates` / `updateStoreName` so both UIs hide the update section. Store builds must **never** download or link to APK/IPA updates (Play Device & Network Abuse policy, App Store guideline 2.5.2).
  - Android flavors (`androidApp/build.gradle.kts`, dimension `distribution`): `github` (GitHub Release APK), `fdroid`, `play` (AAB). `fdroid` and `play` remove `INTERNET` via `src/<flavor>/AndroidManifest.xml`, so those builds make no network requests. iOS: `ANCHOR_DISTRIBUTION` build setting → `AnchorDistribution` Info.plist key (`GITHUB` for the unsigned CI IPA, `APP_STORE` for App Store archives).
  - Public copy must not claim the store versions are free. Use: "free and open source on GitHub and F-Droid; paid store versions support development". Keep `website/privacy.html` accurate (no data collected) and linked from the website footer.
- **No Feature Bloat**: Do **not** re-introduce the removed *Practice Timer* or *Speed Trainer* features, and do not add multi-screen navigation, accounts, analytics, or bloatware unless explicitly requested.
- **Single-Screen Ergonomics**: The main metronome view (`MetronomeScreen.kt` on Android, `ContentView.swift` on iOS) must remain a non-scrolling, single-screen layout where BPM, beat indicators, pendulum, time signature, subdivisions, sound profile, and transport controls are immediately accessible.
- **Musician-First Public Copy vs. Dedicated Technical Docs**:
  - The main `README.md` (featuring the `website/assets/anchor-logo.svg` app logo at the top) and landing page (`website/index.html`) must remain **musician-focused and benefit-driven** (written for players who want a reliable, high-precision metronome without low-level audio DSP jargon front-and-center).
  - The website hero (`website/index.html`) displays a **non-interactive animated app preview** constructed from HTML/CSS UI elements (swinging pendulum, pulsing 4-beat bar, and tempo/transport cards mirroring `MetronomeScreen.kt`) with a **steady background color** (as if the Screen Flash background setting is disabled)—do not re-introduce an interactive web audio simulator, a flashing background overlay on the website preview, or a static raster image screenshot.
  - Deep technical implementation details (44.1 kHz PCM synthesis, 55ms sub-LSB warmup, `AudioTrack` / `AVAudioPlayerNode` playhead math, the shared Kotlin Multiplatform architecture) must be maintained in dedicated technical documentation (`docs/architecture.md` and `website/architecture.html` on the website) and linked cleanly from `README.md` and `website/index.html`.
- **Neutral, Factual Communication**: All changelogs, release notes, commit messages, and technical documentation must use a neutral, concise, factual engineering tone. Avoid emojis in changelogs or overly enthusiastic AI phrasing.

---

## 3. Architecture: Kotlin Multiplatform Core (Android + iPhone) & Clean SDLC

### Repository Layout (keep this structure)

```text
Anchor/
├── androidApp/            # Android app module (Jetpack Compose UI + Android platform code)
│   └── src/{main,test,androidTest}/kotlin/   + flavor overlays src/fdroid, src/play
├── iosApp/                # iPhone app (SwiftUI) + XcodeGen project.yml
├── shared/                # Kotlin Multiplatform module: ALL shared app logic (commonMain) + iosMain
├── docs/                  # Developer documentation (Markdown): architecture.md, distribution.md
├── website/               # GitHub Pages site (index, architecture, privacy, version.json, assets)
├── fastlane/metadata/     # F-Droid / store listing metadata
├── signing/               # Debug/CI keystore (base64) — never a production upload key
├── gradle/                # Gradle wrapper + libs.versions.toml (version catalog)
├── .github/               # workflows (android.yml, ios.yml), scripts, issue/PR templates, dependabot
├── AGENTS.md · CLAUDE.md  # Contributor & AI agent rules (this file is canonical)
├── README.md · CHANGELOG.md · CONTRIBUTING.md · SECURITY.md · LICENSE
└── build.gradle.kts · settings.gradle.kts · gradle.properties · .editorconfig
```
- Kotlin sources always live in `src/<sourceSet>/kotlin/` (never `java/`). New top-level folders require updating this layout, `README.md`, and `CONTRIBUTING.md`.
- `website/` is published by the `deploy-pages` job in `android.yml` (GitHub Pages source = GitHub Actions). Markdown documentation belongs in `docs/`, never in `website/`.
- `metadata.json` and `.env.example` (Secrets Gradle Plugin defaults) stay at the root for Google AI Studio compatibility.

Anchor ships an **Android app** (`androidApp/`, Jetpack Compose) and an **iPhone app** (`iosApp/`, SwiftUI) on top of one **shared Kotlin Multiplatform module** (`shared/`). Packages stay under `com.damiongans.anchor`.

```text
shared/                                   # Kotlin Multiplatform (androidLibrary + iosX64/iosArm64/iosSimulatorArm64)
├── build.gradle.kts                      # com.android.kotlin.multiplatform.library + static XCFramework "AnchorShared"
└── src/
    ├── commonMain/kotlin/com/damiongans/anchor/   # ALL shared app logic (no platform imports)
    │   ├── model/MetronomeModels.kt               # Immutable domain models
    │   ├── domain/DelayCompensationEngine.kt      # Frame-accurate visual/haptic/BT scheduler
    │   ├── domain/TapTempoCalculator.kt           # Tap tempo math
    │   ├── audio/IAudioEngine.kt                  # Audio engine contract
    │   ├── audio/BaseMetronomeAudioEngine.kt      # Shared engine state, clamping, synthesis, route publishing
    │   ├── audio/ClickSynthesizer.kt              # Click waveform synthesis for every SoundSet
    │   ├── audio/MetronomeSequencer.kt            # Warmup dither, sequencing, playhead dispatch, PlayheadEstimator, WrappingFrameCounter
    │   ├── audio/AudioRouteSelector.kt            # Output list normalization (speaker always listed) & default route priority
    │   ├── haptics/IHapticsManager.kt             # Haptics contract
    │   ├── playback/IPlaybackSession.kt           # System media controls contract (+ NoOpPlaybackSession)
    │   ├── repository/ISettingsRepository.kt      # Settings contract & default accent rules
    │   ├── repository/KeyValueSettingsRepository.kt # Shared keys/defaults/clamping over IKeyValueStore (+ InMemoryKeyValueStore)
    │   ├── update/IUpdateRepository.kt            # Update contract & AppReleaseInfo
    │   ├── update/ReleaseVersioning.kt            # GitHubReleaseConfig, ReleaseAsset, SemVer comparison, AppVersionInfo
    │   └── viewmodel/MetronomeController.kt       # MetronomeUiState + every user action (shared state holder)
    ├── commonTest/                                # Shared tests (kotlin.test), run on Android host + iOS simulator
    └── iosMain/kotlin/com/damiongans/anchor/      # iOS platform implementations
        ├── audio/IosAudioEngine.kt                # AVAudioEngine + AVAudioPlayerNode I/O loop
        ├── haptics/IosHapticsManager.kt           # UIImpactFeedbackGenerator
        ├── repository/NSUserDefaultsKeyValueStore.kt
        ├── update/IosUpdateRepository.kt          # NSURLSession + NSJSONSerialization, .ipa asset preference
        ├── playback/IosPlaybackSession.kt         # MPNowPlayingInfoCenter + MPRemoteCommandCenter
        └── ios/AnchorIosApp.kt                    # Swift entry point wiring iOS implementations into MetronomeController

androidApp/src/main/kotlin/com/damiongans/anchor/  # Android platform layer only
├── MainActivity.kt                       # Edge-to-edge host, lifecycle & update prompt trigger
├── audio/MetronomeAudioEngine.kt         # AudioTrack Fast Path I/O, device discovery/routing, wake lock
├── haptics/AndroidHapticsManager.kt      # VibrationEffect haptics
├── repository/SharedPreferencesSettingsRepository.kt # SharedPreferencesKeyValueStore + legacy pulse_metronome_prefs migration
├── service/MetronomePlaybackService.kt   # Foreground mediaPlayback service, MediaSession & WakeLock
├── service/AndroidPlaybackSession.kt     # IPlaybackSession bridge to MetronomePlaybackService
├── update/AppUpdateManager.kt            # HttpURLConnection + org.json transport, ACTION_VIEW (GitHubUpdateRepository)
├── viewmodel/MetronomeViewModel.kt       # AndroidViewModel hosting MetronomeController (viewModelScope)
└── ui/                                   # Compose UI: MetronomeScreen, components/, dialogs/, icons/, theme/

iosApp/                                   # SwiftUI iPhone app (iOS 16+)
├── project.yml                           # XcodeGen spec (generate with `xcodegen generate`; .xcodeproj is git-ignored)
└── Anchor/
    ├── App/                              # AnchorApp.swift (entry), MetronomeStore.swift (Kotlin bridge), Theme.swift
    ├── Views/                            # ContentView, SettingsView, TimeSignatureView
    └── Resources/Assets.xcassets         # App icon & accent color
```

### Shared-Logic Boundary (CRITICAL)
- **All app logic lives in `shared/src/commonMain`**: state, user actions, persistence rules (keys, defaults, clamping, accent memory), timing/delay compensation, click synthesis, sequencing/warmup/dispatch, route list rules, update version comparison and asset selection. Never duplicate this logic in `androidApp/`, `iosMain`, or Swift.
- **Platform code only does platform work**: Android (`androidApp/`) and iOS (`shared/src/iosMain` + `iosApp/`) contain only OS API calls (audio I/O & playhead, device routing, haptics, key-value storage, HTTP/JSON transport, system media controls, URL opening) and UI. If new behavior is not inherently platform-specific, add it to `commonMain` and call it from both platforms.
- **Zero platform imports in `commonMain`**: no `android.*`, `androidx.*`, `java.*`, `platform.*`, or JVM-only annotations (`@Synchronized`). Use `kotlin.concurrent.Volatile`. `DelayCompensationEngine` timeline methods are single-threaded (called only from the platform audio loop).
- **Swift interop**: Swift talks only to `AnchorIosApp` (methods without default arguments, `change*` naming, catalog lists, `observe(onChange:)` delivering on the main thread). Kotlin `description` properties are exposed through helpers (`soundSetSummary`, `buttonTriggerModeSummary`) because `description` collides with `NSObject`. Swift compares Kotlin enums by `name`.
- **UI parity**: Feature changes must be applied to both the Compose UI and the SwiftUI UI unless explicitly platform-specific. Desktop targets are not added unless requested.

### Critical DI & ViewModel Rule
- `MetronomeController` takes all platform implementations (`IAudioEngine`, `IHapticsManager`, `ISettingsRepository`, `IUpdateRepository`, `IPlaybackSession`), `AppVersionInfo`, and a `CoroutineScope` via its constructor so shared tests inject fakes (`shared/src/commonTest/.../fakes/TestFakes.kt`) and a `TestScope`.
- `MetronomeViewModel` keeps constructor injection with default parameters for production Android implementations and delegates every action to `controller`.
- **CRITICAL**: Always keep `@JvmOverloads constructor(...)` on `MetronomeViewModel`. `ViewModelProvider.AndroidViewModelFactory` instantiates `AndroidViewModel` subclasses via reflection using the single-argument `(Application)` constructor. Removing `@JvmOverloads` causes an immediate crash on app launch (`RuntimeException: Cannot create an instance of class MetronomeViewModel`).

---

## 4. Audio Engine & Delay Compensation Invariants

### 4.1 Real-Time PCM Audio (shared `MetronomeSequencer` + platform engines)
- Click synthesis (`ClickSynthesizer`), the warmup dither, sample-accurate sequencing, slew-limited BPM changes, and playhead-anchored dispatch are shared. Platform engines (`MetronomeAudioEngine` on Android, `IosAudioEngine` on iOS) extend `BaseMetronomeAudioEngine` and only push rendered chunks to the OS and report the hardware playhead.
- **iOS**: `AVAudioSession` playback category (background `audio` mode), `AVAudioEngine` + `AVAudioPlayerNode`, a high-priority `NSThread` loop that keeps `LOOKAHEAD_CHUNKS` (4 × 256 frames) scheduled ahead of the player-node sample time, and `AVAudioEngineConfigurationChangeNotification` restarts. iOS chooses outputs via Control Center / AirPlay, so `selectAudioOutputDevice` only refreshes the reported route.
- **Android**: uses a dedicated high-priority thread (`Process.THREAD_PRIORITY_URGENT_AUDIO`) writing 16-bit mono PCM at native low-latency sample rate to a streaming `AudioTrack` configured with `AudioTrack.PERFORMANCE_MODE_LOW_LATENCY`.
- All click waveforms (`SoundSet`: Woodblock, Mechanical, Digital Beep, Rimshot, 808 Percussion) are pre-synthesized by `ClickSynthesizer` into `ShortArray` buffers to guarantee zero allocations on the audio thread.
- **First-Beat Hardware Warmup (Do Not Remove)**: Before sample `0` of Beat 1 is written, both platforms write **at least 55ms of sub-LSB dither (`±1` PCM amplitude)** (`MetronomeSequencer.fillWarmupDither` / `warmupChunkCount`). This forces Android's AudioFlinger mixer and the device DAC out of idle standby so the first beat is never clipped or swallowed, without introducing audible noise.
- **Always-Visible Audio Output Selection**: Platform engines enumerate media outputs and `AudioRouteSelector.normalize` guarantees `Built-in Speaker` is always included even when it is the only output. Both the top bar output selector and `SettingsBottomSheet` (`AudioSettingsTab`) must always display all available outputs (even when only 1 output exists) and allow routing via `AudioTrack.setPreferredDevice`.

### 4.2 Vendor-Agnostic Delay Compensation (`DelayCompensationEngine`)
- **Playhead-Anchored (Never Write-Head-Anchored)**: Visual beats, screen flashes, pendulum motion, and vibrations must **always** be scheduled and triggered against the hardware playhead (Android `AudioTrack.getPlaybackHeadPosition()`, iOS `AVAudioPlayerNode` player sample time, both smoothed by `PlayheadEstimator`) (monotonic 64-bit `playheadFrame`, offset by `WARMUP_FRAMES`), never `writeFrame`. Because `AudioTrack.write()` blocks based on OEM buffer size, anchoring to `writeFrame` causes severe timing drift across devices.
- **Zero Hidden Magic Constants**: At `0 ms` offset, events fire at the exact frame `playheadFrame >= beatAudioFrame`. Do not add hidden latency guesses or arbitrary frame offsets.
- **Bidirectional Offsets (`-200 ms` to `+300 ms`)**:
  - **Negative offset (`< 0 ms`)**: Fires *earlier* than the audio frame to compensate for display rendering pipeline latency or slower haptic motor spin-up.
  - **Positive offset (`> 0 ms`)**: Fires *later* than the audio frame to delay visuals or haptics (e.g., when using external/Bluetooth speakers).
  - **Bluetooth Audio Offset (`-200 ms` to `+300 ms`)**: Only applied when a Bluetooth A2DP/LE/SCO output device is actively routed (`isBluetoothOutputConnected`), shifting both visual and vibration schedules together relative to the delayed Bluetooth audio stream.
- **Beat 1 Lookahead**: When a user configures a negative (early) offset, Beat 1's target frame can occur during the 55ms hardware warmup window (`targetFrame < 0`). `MetronomeAudioEngine` tracks warmup playhead frames on `-WARMUP_FRAMES until 0` so Beat 1 still plays its full audio buffer while early visual/haptic triggers fire on schedule.
- **Unified Visual Timing**: The main beat indicator (`BeatBar`), pendulum phase, and full-screen flash overlay share the exact same `flashDelayMs` schedule and trigger simultaneously via `snap()` on beat onset (zero fade-in lag).

---

## 5. Background Playback, Multimedia Notification & Update Checker

- **Shared contract**: `MetronomeController` publishes `PlaybackSessionState` (`title` = `"<BPM> BPM • <TempoMarking>"`, `subtitle` = `"Playing • 4/4 Time"`) to `IPlaybackSession` and receives commands through `PlaybackCommandListener`. Android: `AndroidPlaybackSession` → `MetronomePlaybackService`. iOS: `IosPlaybackSession` (Now Playing info; play/pause/toggle/stop; next/previous track = `+1`/`-1 BPM`).
- **Update checks**: `ReleaseVersioning` / `GitHubReleaseConfig` (shared) handle repo fallback, asset selection (`.apk` on Android, `.ipa` on iOS, else the release page), and SemVer comparison; platforms only perform HTTP + JSON parsing and open the URL.

- **Foreground Service & Multimedia Notification (`MetronomePlaybackService`)**: `MetronomePlaybackService` runs with `foregroundServiceType="mediaPlayback"`, holds a partial `WakeLock` while playing (releasing it when paused), and maintains an active `MediaSession` + `Notification.MediaStyle` controller in the notification drawer and lock screen.
  - **Displayed Info**: Shows the current tempo and marking (`"<BPM> BPM • <TempoMarking>"`, e.g., `"120 BPM • Allegro"`) as the notification/media title and playback status + time signature (`"Playing • 4/4 Time"` or `"Paused • 4/4 Time"`) as the subtitle.
  - **Transport Controls**: Provides three actions in both compact notification view (`Notification.Action`) and Android 13+ System UI Media Carousel (`PlaybackState.CustomAction` + `Play/Pause`): **Decrease Tempo (`-1 BPM`)**, **Play / Pause**, and **Increase Tempo (`+1 BPM`)**.
  - **Persistent While Paused**: Pausing playback updates the notification to the Paused state (with a `Play` button) rather than dismissing it, allowing users to start/pause playback and adjust BPM directly from the notification drawer at any time.
- **GitHub Releases Update Checker (`AppUpdateManager`)**: Checks `DamionGans/Anchor` (with automatic fallback to `DamionGans/PulseMetronome`) via `HttpURLConnection` and launches updates via a standard zero-permission `Intent.ACTION_VIEW` intent so the app requires no restricted `REQUEST_INSTALL_PACKAGES` permission and remains 100% compliant with Google Play Store policies while preserving full update functionality.

---

## 6. UI, Visual Design & Persistence Rules

- **System Media Volume Only (No In-App Volume Slider)**: Do **not** add an in-app volume slider or software volume multiplier on either platform (iOS uses the hardware volume buttons as well). `MainActivity` sets `volumeControlStream = AudioManager.STREAM_MUSIC` and `MetronomeAudioEngine` outputs waveforms at unity gain so loudness is controlled exclusively via the Android device's physical volume buttons and system media stream.
- **Per-Count Accent Memory Across Time Signatures**: Beat accents must **never** reset when changing time signatures. `MetronomeViewModel` and `ISettingsRepository` maintain a 16-beat `rememberedAccents` list indexed by beat count (`0..15`). Modifying a beat's accent updates that count in `rememberedAccents`, and switching time signatures restores `rememberedAccents.take(beats)` so every count keeps its remembered accent unless altered in the meantime.
- **Play & Tap Tempo Button Trigger Mode (`ButtonTriggerMode`)**: Users can configure whether the Play/Pause and Tap Tempo buttons in `QuickControlsBar` register immediately on finger touch-down (`ON_PRESS` / button tap) or on finger lift (`ON_RELEASE` / button release).
- **Settings Persistence**: Every user-configurable setting (`bpm`, `timeSignature`, `subdivision`, `soundSet`, `pitchMultiplier`, `accents`, `rememberedAccents`, `buttonTriggerMode`, `isFlashEnabled`, `isVibrationEnabled`, `flashDelayMs`, `vibrationDelayMs`, `bluetoothCompensationMs`, `themeMode`, `accentColor`) must be saved and restored via `ISettingsRepository` (shared `KeyValueSettingsRepository` over `SharedPreferences` on Android and `NSUserDefaults` on iOS; storage keys are shared).
- **Fine-Grained Calibration Controls**: In `SettingsBottomSheet`, each latency offset (Visual & Screen Flash, Vibration, Bluetooth Audio) must provide both a slider (`-200 ms` to `+300 ms`) and quick fine-tuning step buttons (`-10ms`, `-1ms`, `0ms`, `+1ms`, `+10ms`).
- **Typography & Accessibility**:
  - Use tabular numbers (`fontFeatureSettings = "tnum"`) on BPM readouts, beat counters, and ms offset labels to prevent layout jitter.
  - Maintain a minimum `48.dp` touch target on all interactive buttons and steppers.
  - Support Material 3 Dynamic Color ("Android Phone Theme" on Android 12+) alongside curated custom accent palettes and System/Light/Dark modes.

---

## 7. APK Size, Plain AOSP Independence, Google Play Store Compliance & Reproducible Builds

- **Workflows**: `.github/workflows/android.yml` (Android build/test/release + GitHub Pages deploy of `website/`) and `.github/workflows/ios.yml` (iOS). Dependabot (`.github/dependabot.yml`) opens weekly update PRs for Gradle and GitHub Actions.
- **Android CI** (`.github/workflows/android.yml`): builds, lints (`lintGithubDebug`), and tests (`testGithubDebugUnitTest`, `:shared:testAndroidHostTest`) on pushes to **every branch** and on pull requests; builds `assembleGithubRelease` (GitHub Release APK), `assembleFdroidRelease`, and `assemblePlayRelease` + `bundlePlayRelease` (Play AAB workflow artifact) and fails if the play APK requests `INTERNET`. On `v*` tags, `publish-play` uploads the AAB to the Play internal track (draft) once `PLAY_SERVICE_ACCOUNT_JSON` and a real `SIGNING_KEYSTORE_BASE64` upload key exist (the first Play release must be uploaded manually). For Play, never sign with the committed debug keystore; GitHub Releases are published only for pushes to `main`, `v*` tags, or a manual `workflow_dispatch` with `create_release`, and GitHub Pages deploys only from `main`, `v*` tags, or `workflow_dispatch`.
- **iOS builds**: `.github/workflows/ios.yml` (macOS runner, Xcode 16.4) runs `:shared:iosSimulatorArm64Test`, generates the project with XcodeGen, builds for the simulator, and uploads an unsigned `Anchor-vX.Y.Z-unsigned.ipa` (+ `.sha256`) as a workflow artifact. The shared framework is built by the Xcode pre-build phase `./gradlew :shared:embedAndSignAppleFrameworkForXcode`. The iOS app must not add third-party Swift packages, analytics, or tracking. On `v*` tags the `appstore` job archives with `ANCHOR_DISTRIBUTION=APP_STORE` and uploads to App Store Connect / TestFlight once `APPLE_TEAM_ID`, `APPLE_DIST_CERT_P12_BASE64`, `APPLE_DIST_CERT_PASSWORD`, `APPLE_PROVISIONING_PROFILE_BASE64`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, and `ASC_KEY_P8` secrets exist; otherwise it skips.
- **F-Droid**: metadata lives in `fastlane/metadata/android/en-US/` (title, descriptions, `changelogs/<versionCode>.txt`, icon); add a `changelogs/<versionCode>.txt` for every release. F-Droid builds the `fdroid` flavor from source; the repository is licensed Apache 2.0 (`LICENSE`).

- **Zero Google Play Services / 100% Plain AOSP Compatibility (CRITICAL)**:
  - The app must **never** depend on Google Play Services (`com.google.android.gms.*`), Firebase (`com.google.firebase.*`), or the `com.google.gms.google-services` Gradle plugin.
  - It must always build and run with full functionality on plain AOSP, LineageOS, GrapheneOS, and de-Googled Android phones as well as standard Google Play devices.
- **Google Play Store Compliance**:
  - Keep `AndroidManifest.xml` strictly least-privilege (`INTERNET` — github flavor only, removed for `fdroid`/`play` — `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`) with `android:usesCleartextTraffic="false"` and no prohibited permissions (`REQUEST_INSTALL_PACKAGES`, `READ_EXTERNAL_STORAGE`, etc.).
  - Keep app titles (`app_name` = `"Anchor"` for launcher icon, `app_full_name` and `metadata.json` = `"Anchor Metronome"` for full app name) concise (`<= 30` chars) without emojis or promotional buzzwords.
- **Minimal Footprint**: Keep the release APK lightweight (~2 MB):
  - Do **not** add `androidx.compose.material:material-icons-extended` (adds several MBs); add custom vector icons in `AnchorIcons.kt`.
  - Do **not** add heavy networking/serialization/database libraries (Retrofit, OkHttp, Moshi, Room, Firebase) when Android platform APIs (`HttpURLConnection`, `org.json`, `SharedPreferences`) suffice.
- **Release Optimization (`androidApp/build.gradle.kts`)**:
  - Release builds must keep `isMinifyEnabled = true` and `isShrinkResources = true`.
  - Release artifacts are built with `assemble<Flavor>Release` / `bundlePlayRelease` (never ship debug builds as releases).
  - Reproducible build flags (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`) must remain enabled on `AbstractArchiveTask`.

---

## 8. Mandatory Release, Semantic Versioning & Website Sync Checklist

### Semantic Versioning Policy (`MAJOR.MINOR.PATCH`)
Version numbers use standard Semantic Versioning (`MAJOR.MINOR.PATCH`, with `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`, e.g., `1.3.0` → `10300`)—**never** use date-based version strings. Use engineering judgment to determine the appropriate version level:
- **PATCH / Hotfix (`MAJOR.MINOR.(PATCH+1)`)**: Bug fixes, hotfixes, copy/documentation updates, or small internal adjustments.
- **MINOR (`MAJOR.(MINOR+1).0`)**: New user-facing features, controls, or capabilities (resets patch to `0`). It is fine for minor versions to advance quickly as features are introduced.
- **MAJOR (`(MAJOR+1).0.0`)**: Be conservative with major bumps; reserve them for major new feature milestones, architectural overhauls, or after a substantial progression of minor updates.

### GitHub Release Gate Rule (CRITICAL — Check Before Creating a New Version Section)
1. **Check Published GitHub Releases First**: Before adding a new version section, check `https://api.github.com/repos/DamionGans/Anchor/releases` (and fallback `https://api.github.com/repos/DamionGans/PulseMetronome/releases`) to see what version was most recently published as an APK release on GitHub.
2. **If the Current Version in the Repo Has NOT Been Released on GitHub Yet**:
   - **Do NOT stack a second unreleased version entry.** Maintain a single unreleased version entry at the top of `CHANGELOG.md`.
   - **Adjust the Unreleased SemVer Level If Needed & Combine the Changelog**: You may adjust the unreleased version's semantic level relative to the last published GitHub release (e.g., upgrading an unreleased patch bump to a minor bump `1.3.0` if new features were added since the last published release), and merge/rewrite all changes since the last GitHub release into that single unreleased version section across `CHANGELOG.md`, `ChangelogDialog.kt`, `website/version.json`, and `website/index.html`.
3. **If the Current Version in the Repo HAS Already Been Released on GitHub**:
   - Increment the semantic version (`PATCH`, `MINOR`, or `MAJOR` as appropriate) and prepend a new changelog entry.

Whenever updating the version or rewriting the current unreleased version's changelog, you **must keep all 6 locations in lockstep**:

1. **`androidApp/build.gradle.kts`**:
   - Keep or update `val appVersionName = "X.Y.Z"` and `val appVersionCode = X * 10000 + Y * 100 + Z`.
2. **`CHANGELOG.md`**:
   - Prepend (if bumping after a published release) or rewrite (if unreleased on GitHub) the top `## [vX.Y.Z]` section in a neutral, concise, factual tone.
   - Note: `.github/workflows/android.yml` automatically extracts the top section of `CHANGELOG.md` for GitHub Release notes.
3. **`androidApp/src/main/kotlin/com/damiongans/anchor/ui/dialogs/ChangelogDialog.kt`**:
   - Update `CURRENT_RELEASE_CHANGELOG` to match the combined changes in `CHANGELOG.md`.
4. **`website/version.json`, `website/index.html` & `website/app.js` (GitHub Pages Website)**:
   - Keep `version`, `versionCode`, `releaseDate`, `downloadUrl`, and `highlights` synchronized in `website/version.json`.
   - Keep the fallback version badge, download link, and changelog list synchronized in `website/index.html` and `website/app.js`.
5. **`androidApp/src/test/kotlin/com/damiongans/anchor/AppUpdateManagerTest.kt`** (plus `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` for F-Droid):
   - Ensure the `BuildConfig.VERSION_NAME` assertion in `testVersionComparison()` matches the active `X.Y.Z` version.
6. **iOS version (`iosApp/project.yml`)**:
   - Keep `MARKETING_VERSION: "X.Y.Z"` and `CURRENT_PROJECT_VERSION: "<versionCode>"` in sync, plus the `"X.Y.Z"` fallbacks in `AnchorIosApp.currentAppVersion()` and `MetronomeViewModel` (`BuildConfig.VERSION_NAME.ifBlank { ... }`). CI overrides both from `CHANGELOG.md`.

---

## 9. Verification & Testing Workflow

Before completing any task that modifies code or configuration:

1. **Compile Check**: Verify the project compiles cleanly (`gradle assembleGithubDebug`; store-channel changes also `assemblePlayRelease bundlePlayRelease assembleFdroidRelease`).
2. **Shared tests** (`shared/src/commonTest`, `kotlin.test`): run `./gradlew :shared:testAndroidHostTest` (any OS) and `./gradlew :shared:iosSimulatorArm64Test` (macOS):
   - `TapTempoCalculatorTest`, `DelayCompensationEngineTest` (0ms exact frame match, negative/positive offsets, Bluetooth shift, subdivisions)
   - `MetronomeControllerTest` (state, persistence, calibration clamping, playback session commands, update flow, store channels never self-update)
   - `MetronomeSequencerTest` (synthesis, warmup, exact beat frames, playhead estimator, route selection)
   - `KeyValueSettingsRepositoryTest`, `ReleaseVersioningTest`
3. **Android Unit & Robolectric Tests**: run `./gradlew :androidApp:testGithubDebugUnitTest`:
   - `MetronomeUnitTest`, `MetronomeViewModelTest` (ViewModel wiring, notification service integration), `AppUpdateManagerTest` (`BuildConfig.VERSION_NAME` alignment), `ExampleRobolectricTest`
4. **iOS build**: on macOS, `cd iosApp && xcodegen generate && xcodebuild -scheme Anchor -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` (or rely on the `Build iOS App` workflow).
5. **Update `AGENTS.md`**: Confirm whether your changes introduced or modified any architectural rules or user requirements, and update this file if so.
