# Anchor Metronome — AI Agent & Developer Guidelines (`AGENTS.md`)

This document is the authoritative source of truth for all AI coding agents (AI Studio, Gemini, Claude Code, GitHub Copilot, Cursor, Windsurf, Codex) and human contributors working on **Anchor Metronome** (short name **Anchor**, formerly Pulse Metronome).

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
  - **Full App Name**: **Anchor Metronome** (used to prevent trademark infringement in `metadata.json`, `<application android:label="@string/app_full_name">`, the in-app About sheet in `SettingsBottomSheet.kt`, `MediaMetadata.METADATA_KEY_ALBUM`, `README.md` / `ARCHITECTURE.md` headings, website `<title>` / Open Graph tags, and GitHub Release titles).
  - **Short Name / References**: **Anchor** (kept for the home screen launcher icon label `<activity android:label="@string/app_name">`, the top-bar header `"ANCHOR"` in `MetronomeScreen.kt`, package namespace and applicationId `com.damiongans.anchor`, APK filename `Anchor-vX.Y.Z.apk`, and concise body copy).
  - **Canonical Tagline**: **"Rock-solid time for serious play"** across `README.md`, `docs/index.html`, `metadata.json`, `MetronomeScreen.kt`, and the in-app About sheet (`SettingsBottomSheet.kt`). Never describe Anchor as a general "timekeeping" app—it is a musical metronome.
  - **Repository URL**: **`DamionGans/Anchor`** (`https://github.com/DamionGans/Anchor`, Pages `https://damiongans.github.io/Anchor/`). Both `AppUpdateManager.kt` and `docs/app.js` check `DamionGans/Anchor` first and automatically fall back to `DamionGans/PulseMetronome` so releases resolve seamlessly before and after renaming the GitHub repository.
- **No Feature Bloat**: Do **not** re-introduce the removed *Practice Timer* or *Speed Trainer* features, and do not add multi-screen navigation, accounts, analytics, or bloatware unless explicitly requested.
- **Single-Screen Ergonomics**: The main metronome view (`MetronomeScreen.kt`) must remain a non-scrolling, single-screen layout where BPM, beat indicators, pendulum, time signature, subdivisions, sound profile, and transport controls are immediately accessible.
- **Musician-First Public Copy vs. Dedicated Technical Docs**:
  - The main `README.md` (featuring the `docs/assets/anchor-logo.svg` app logo at the top) and landing page (`docs/index.html`) must remain **musician-focused and benefit-driven** (written for players who want a reliable, high-precision metronome without low-level audio DSP jargon front-and-center).
  - The website hero (`docs/index.html`) displays a **non-interactive animated app preview** constructed from HTML/CSS UI elements (swinging pendulum, pulsing 4-beat bar, and tempo/transport cards mirroring `MetronomeScreen.kt`) with a **steady background color** (as if the Screen Flash background setting is disabled)—do not re-introduce an interactive web audio simulator, a flashing background overlay on the website preview, or a static raster image screenshot.
  - Deep technical implementation details (44.1 kHz PCM synthesis, 55ms sub-LSB warmup, `AudioTrack` playhead math, multiplatform-ready clean architecture) must be maintained in dedicated technical documentation (`ARCHITECTURE.md` in the repo root and `docs/architecture.html` on the website) and linked cleanly from `README.md` and `docs/index.html`.
- **Neutral, Factual Communication**: All changelogs, release notes, commit messages, and technical documentation must use a neutral, concise, factual engineering tone. Avoid emojis in changelogs or overly enthusiastic AI phrasing.

---

## 3. Architecture, Multiplatform-Ready Core & Clean SDLC

The codebase follows a clean, testable MVVM + Repository + Domain architecture under `com.damiongans.anchor`, designed so that core domain logic and service contracts can be shared with future iOS and desktop targets without implementing those platforms yet:

```text
app/src/main/java/com/damiongans/anchor/
├── MainActivity.kt                  # Edge-to-edge host, lifecycle & update prompt trigger
├── audio/
│   ├── IAudioEngine.kt              # Pure Kotlin audio engine contract (zero Android imports)
│   └── MetronomeAudioEngine.kt      # Android real-time PCM synthesis & playhead-anchored timing
├── domain/
│   ├── DelayCompensationEngine.kt   # Pure Kotlin sample-frame-accurate visual/haptic/BT scheduler
│   └── TapTempoCalculator.kt        # Pure Kotlin BPM clamping & tap-tempo outlier math
├── haptics/
│   ├── IHapticsManager.kt           # Pure Kotlin haptics contract (zero Android imports)
│   └── AndroidHapticsManager.kt     # Android precomputed Waveform/OneShot hardware haptics
├── model/
│   └── MetronomeModels.kt           # Pure Kotlin immutable domain models (zero Android/Compose imports)
├── repository/
│   ├── ISettingsRepository.kt       # Pure Kotlin settings persistence contract & default accent rules
│   └── SharedPreferencesSettingsRepository.kt # Android SharedPreferences implementation (with legacy migration)
├── service/
│   └── MetronomePlaybackService.kt  # Android foreground mediaPlayback service, MediaSession & WakeLock
├── ui/
│   ├── MetronomeScreen.kt           # Primary single-screen UI, output dropdown & screen flash overlay
│   ├── components/                  # BeatBar, PendulumVisualizer, QuickControlsBar, TempoControls
│   ├── dialogs/                     # SettingsBottomSheet, TimeSignatureDialog, ChangelogDialog, UpdateAvailableDialog
│   ├── icons/                       # AnchorIcons.kt custom Compose vector icons
│   └── theme/                       # Material 3 theming + dynamic Android color support + AccentColorOption.toComposeColor()
├── update/
│   ├── IUpdateRepository.kt         # Pure Kotlin update check contract & AppReleaseInfo model
│   └── AppUpdateManager.kt          # Semantic version comparison & GitHub Release checker (DamionGans/Anchor)
└── viewmodel/
    └── MetronomeViewModel.kt        # StateFlow state holder with constructor-injected dependencies
```

### Multiplatform-Ready Core Boundary (CRITICAL)
- **Zero Platform Imports in Core Contracts/Models**: `model/MetronomeModels.kt`, `domain/DelayCompensationEngine.kt`, `domain/TapTempoCalculator.kt`, `audio/IAudioEngine.kt`, `haptics/IHapticsManager.kt`, `repository/ISettingsRepository.kt`, and `update/IUpdateRepository.kt` must remain **100% pure Kotlin** with zero `android.*` or `androidx.*` imports (e.g., `AccentColorOption` stores `argbHex: Long` and maps to Compose `Color` via an extension in `ui/theme/Color.kt`).
- **Android-Only Scope Until Requested**: Do **not** add iOS or desktop Gradle targets/modules yet unless explicitly requested by the user; simply preserve the clean separation between pure Kotlin contracts/domain and Android implementations.

### Critical DI & ViewModel Rule
- `MetronomeViewModel` uses constructor injection with default parameters for production implementations (`IAudioEngine`, `IHapticsManager`, `ISettingsRepository`, `IUpdateRepository`) so unit tests can inject fakes without a DI framework.
- **CRITICAL**: Always keep `@JvmOverloads constructor(...)` on `MetronomeViewModel`. `ViewModelProvider.AndroidViewModelFactory` instantiates `AndroidViewModel` subclasses via reflection using the single-argument `(Application)` constructor. Removing `@JvmOverloads` causes an immediate crash on app launch (`RuntimeException: Cannot create an instance of class MetronomeViewModel`).

---

## 4. Audio Engine & Delay Compensation Invariants

### 4.1 Real-Time PCM Audio (`MetronomeAudioEngine`)
- Uses a dedicated high-priority thread (`Process.THREAD_PRIORITY_URGENT_AUDIO`) writing 16-bit mono PCM at native low-latency sample rate to a streaming `AudioTrack` configured with `AudioTrack.PERFORMANCE_MODE_LOW_LATENCY`.
- All click waveforms (`Tick`, `Click`, `Woodblock`, `Cowbell`, `Beep`, `Hi-Hat`) are pre-synthesized into `ShortArray` buffers at startup to guarantee zero allocations on the audio thread.
- **First-Beat Hardware Warmup (Do Not Remove)**: Before sample `0` of Beat 1 is written, the engine writes **55ms of sub-LSB dither (`±1` PCM amplitude)**. This forces Android's AudioFlinger mixer and the device DAC out of idle standby so the first beat is never clipped or swallowed, without introducing audible noise.
- **Always-Visible Audio Output Selection**: `MetronomeAudioEngine` enumerates all media output devices (`availableDevices`) and guarantees `Built-in Speaker` is always included even when it is the only output. Both the top bar output selector and `SettingsBottomSheet` (`AudioSettingsTab`) must always display all available outputs (even when only 1 output exists) and allow routing via `AudioTrack.setPreferredDevice`.

### 4.2 Vendor-Agnostic Delay Compensation (`DelayCompensationEngine`)
- **Playhead-Anchored (Never Write-Head-Anchored)**: Visual beats, screen flashes, pendulum motion, and vibrations must **always** be scheduled and triggered against `AudioTrack.getPlaybackHeadPosition()` (monotonic 64-bit `playheadFrame`, offset by `WARMUP_FRAMES`), never `writeFrame`. Because `AudioTrack.write()` blocks based on OEM buffer size, anchoring to `writeFrame` causes severe timing drift across devices.
- **Zero Hidden Magic Constants**: At `0 ms` offset, events fire at the exact frame `playheadFrame >= beatAudioFrame`. Do not add hidden latency guesses or arbitrary frame offsets.
- **Bidirectional Offsets (`-200 ms` to `+300 ms`)**:
  - **Negative offset (`< 0 ms`)**: Fires *earlier* than the audio frame to compensate for display rendering pipeline latency or slower haptic motor spin-up.
  - **Positive offset (`> 0 ms`)**: Fires *later* than the audio frame to delay visuals or haptics (e.g., when using external/Bluetooth speakers).
  - **Bluetooth Audio Offset (`-200 ms` to `+300 ms`)**: Only applied when a Bluetooth A2DP/LE/SCO output device is actively routed (`isBluetoothOutputConnected`), shifting both visual and vibration schedules together relative to the delayed Bluetooth audio stream.
- **Beat 1 Lookahead**: When a user configures a negative (early) offset, Beat 1's target frame can occur during the 55ms hardware warmup window (`targetFrame < 0`). `MetronomeAudioEngine` tracks warmup playhead frames on `-WARMUP_FRAMES until 0` so Beat 1 still plays its full audio buffer while early visual/haptic triggers fire on schedule.
- **Unified Visual Timing**: The main beat indicator (`BeatBar`), pendulum phase, and full-screen flash overlay share the exact same `flashDelayMs` schedule and trigger simultaneously via `snap()` on beat onset (zero fade-in lag).

---

## 5. Background Playback, Multimedia Notification & Update Checker

- **Foreground Service & Multimedia Notification (`MetronomePlaybackService`)**: `MetronomePlaybackService` runs with `foregroundServiceType="mediaPlayback"`, holds a partial `WakeLock` while playing (releasing it when paused), and maintains an active `MediaSession` + `Notification.MediaStyle` controller in the notification drawer and lock screen.
  - **Displayed Info**: Shows the current tempo and marking (`"<BPM> BPM • <TempoMarking>"`, e.g., `"120 BPM • Allegro"`) as the notification/media title and playback status + time signature (`"Playing • 4/4 Time"` or `"Paused • 4/4 Time"`) as the subtitle.
  - **Transport Controls**: Provides three actions in both compact notification view (`Notification.Action`) and Android 13+ System UI Media Carousel (`PlaybackState.CustomAction` + `Play/Pause`): **Decrease Tempo (`-1 BPM`)**, **Play / Pause**, and **Increase Tempo (`+1 BPM`)**.
  - **Persistent While Paused**: Pausing playback updates the notification to the Paused state (with a `Play` button) rather than dismissing it, allowing users to start/pause playback and adjust BPM directly from the notification drawer at any time.
- **GitHub Releases Update Checker (`AppUpdateManager`)**: Checks `DamionGans/Anchor` (with automatic fallback to `DamionGans/PulseMetronome`) via `HttpURLConnection` and launches updates via a standard zero-permission `Intent.ACTION_VIEW` intent so the app requires no restricted `REQUEST_INSTALL_PACKAGES` permission and remains 100% compliant with Google Play Store policies while preserving full update functionality.

---

## 6. UI, Visual Design & Persistence Rules

- **System Media Volume Only (No In-App Volume Slider)**: Do **not** add an in-app volume slider or software volume multiplier. `MainActivity` sets `volumeControlStream = AudioManager.STREAM_MUSIC` and `MetronomeAudioEngine` outputs waveforms at unity gain so loudness is controlled exclusively via the Android device's physical volume buttons and system media stream.
- **Per-Count Accent Memory Across Time Signatures**: Beat accents must **never** reset when changing time signatures. `MetronomeViewModel` and `ISettingsRepository` maintain a 16-beat `rememberedAccents` list indexed by beat count (`0..15`). Modifying a beat's accent updates that count in `rememberedAccents`, and switching time signatures restores `rememberedAccents.take(beats)` so every count keeps its remembered accent unless altered in the meantime.
- **Play & Tap Tempo Button Trigger Mode (`ButtonTriggerMode`)**: Users can configure whether the Play/Pause and Tap Tempo buttons in `QuickControlsBar` register immediately on finger touch-down (`ON_PRESS` / button tap) or on finger lift (`ON_RELEASE` / button release).
- **Settings Persistence**: Every user-configurable setting (`bpm`, `timeSignature`, `subdivision`, `soundSet`, `pitchMultiplier`, `accents`, `rememberedAccents`, `buttonTriggerMode`, `isFlashEnabled`, `isVibrationEnabled`, `flashDelayMs`, `vibrationDelayMs`, `bluetoothCompensationMs`, `themeMode`, `accentColor`) must be saved and restored via `ISettingsRepository` (`SharedPreferencesSettingsRepository`).
- **Fine-Grained Calibration Controls**: In `SettingsBottomSheet`, each latency offset (Visual & Screen Flash, Vibration, Bluetooth Audio) must provide both a slider (`-200 ms` to `+300 ms`) and quick fine-tuning step buttons (`-10ms`, `-1ms`, `0ms`, `+1ms`, `+10ms`).
- **Typography & Accessibility**:
  - Use tabular numbers (`fontFeatureSettings = "tnum"`) on BPM readouts, beat counters, and ms offset labels to prevent layout jitter.
  - Maintain a minimum `48.dp` touch target on all interactive buttons and steppers.
  - Support Material 3 Dynamic Color ("Android Phone Theme" on Android 12+) alongside curated custom accent palettes and System/Light/Dark modes.

---

## 7. APK Size, Plain AOSP Independence, Google Play Store Compliance & Reproducible Builds

- **Zero Google Play Services / 100% Plain AOSP Compatibility (CRITICAL)**:
  - The app must **never** depend on Google Play Services (`com.google.android.gms.*`), Firebase (`com.google.firebase.*`), or the `com.google.gms.google-services` Gradle plugin.
  - It must always build and run with full functionality on plain AOSP, LineageOS, GrapheneOS, and de-Googled Android phones as well as standard Google Play devices.
- **Google Play Store Compliance**:
  - Keep `AndroidManifest.xml` strictly least-privilege (`INTERNET`, `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`) with `android:usesCleartextTraffic="false"` and no prohibited permissions (`REQUEST_INSTALL_PACKAGES`, `READ_EXTERNAL_STORAGE`, etc.).
  - Keep app titles (`app_name` = `"Anchor"` for launcher icon, `app_full_name` and `metadata.json` = `"Anchor Metronome"` for full app name) concise (`<= 30` chars) without emojis or promotional buzzwords.
- **Minimal Footprint**: Keep the release APK lightweight (~2 MB):
  - Do **not** add `androidx.compose.material:material-icons-extended` (adds several MBs); add custom vector icons in `AnchorIcons.kt`.
  - Do **not** add heavy networking/serialization/database libraries (Retrofit, OkHttp, Moshi, Room, Firebase) when Android platform APIs (`HttpURLConnection`, `org.json`, `SharedPreferences`) suffice.
- **Release Optimization (`app/build.gradle.kts`)**:
  - Release builds must keep `isMinifyEnabled = true` and `isShrinkResources = true`.
  - Release artifacts are built with `assembleRelease` (never ship debug builds as releases).
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
   - **Adjust the Unreleased SemVer Level If Needed & Combine the Changelog**: You may adjust the unreleased version's semantic level relative to the last published GitHub release (e.g., upgrading an unreleased patch bump to a minor bump `1.3.0` if new features were added since the last published release), and merge/rewrite all changes since the last GitHub release into that single unreleased version section across `CHANGELOG.md`, `ChangelogDialog.kt`, `docs/version.json`, and `docs/index.html`.
3. **If the Current Version in the Repo HAS Already Been Released on GitHub**:
   - Increment the semantic version (`PATCH`, `MINOR`, or `MAJOR` as appropriate) and prepend a new changelog entry.

Whenever updating the version or rewriting the current unreleased version's changelog, you **must keep all 5 locations in lockstep**:

1. **`app/build.gradle.kts`**:
   - Keep or update `val appVersionName = "X.Y.Z"` and `val appVersionCode = X * 10000 + Y * 100 + Z`.
2. **`CHANGELOG.md`**:
   - Prepend (if bumping after a published release) or rewrite (if unreleased on GitHub) the top `## [vX.Y.Z]` section in a neutral, concise, factual tone.
   - Note: `.github/workflows/release.yml` automatically extracts the top section of `CHANGELOG.md` for GitHub Release notes.
3. **`app/src/main/java/com/damiongans/anchor/ui/dialogs/ChangelogDialog.kt`**:
   - Update `CURRENT_RELEASE_CHANGELOG` to match the combined changes in `CHANGELOG.md`.
4. **`docs/version.json`, `docs/index.html` & `docs/app.js` (GitHub Pages Website)**:
   - Keep `version`, `versionCode`, `releaseDate`, `downloadUrl`, and `highlights` synchronized in `docs/version.json`.
   - Keep the fallback version badge, download link, and changelog list synchronized in `docs/index.html` and `docs/app.js`.
5. **`app/src/test/java/com/damiongans/anchor/AppUpdateManagerTest.kt`**:
   - Ensure the `BuildConfig.VERSION_NAME` assertion in `testVersionComparison()` matches the active `X.Y.Z` version.

---

## 9. Verification & Testing Workflow

Before completing any task that modifies code or configuration:

1. **Compile Check**: Verify the project compiles cleanly (`compile_applet` or `gradle assembleDebug`).
2. **Unit & Robolectric Tests**: Run `gradle :app:testDebugUnitTest` and ensure all tests pass:
   - `TapTempoCalculatorTest` & `MetronomeUnitTest` (BPM bounds, interval math, tap tempo)
   - `DelayCompensationEngineTest` (0ms exact frame match, negative early offsets, positive delayed offsets, Bluetooth shift, subdivision triggering)
   - `MetronomeViewModelTest` (state flows, persistence, calibration clamping)
   - `AppUpdateManagerTest` (semantic version comparison and current `BuildConfig.VERSION_NAME` alignment)
3. **Update `AGENTS.md`**: Confirm whether your changes introduced or modified any architectural rules or user requirements, and update this file if so.
