# Changelog

## [v1.6.3] — Tablet & Desktop Studio Layout

### Shared UI & iOS
- Adaptive two-pane studio layout on tablets, landscape screens, and desktop windows (visual stage with dynamically scaling pendulum and beat bar on the left; tempo and transport control deck on the right), while keeping the single-column layout on phones and narrow windows.
- Pendulum visualizer now scales its arm length, swing arc, tick marks, and counterweight proportionally to the available canvas size.
- Beat bar pills scale cleanly from wide studio layouts down to dense 16-beat meters.

### Windows & Documentation
- Desktop window now opens in a landscape studio layout (900x620 dp) that fits standard laptop and desktop displays out of the box and adapts seamlessly when resized.
- Condensed AGENTS.md for lower token usage while preserving all rules and identifiers.

## [v1.6.2] — Multiplatform Showcase, Build Fixes & Release Guards

### Android & Build Setup
- Fixed Compose Preview unit test generation failure on release variants with Android Gradle Plugin 9.5+.
- Resolved Gradle 10 deprecation warnings in shared module dependencies and enabled parallel tooling execution.
- Added comprehensive unit tests for tap tempo calculation.

### Website & Documentation
- Website expanded to showcase multiplatform support equally across Windows (x64 & arm64), Android, and iPhone, including platform-specific downloads and architecture deep-dive.
- Added VS Code launch configurations and development tasks for running and debugging desktop and Android targets.
- Contributor guidelines restored with mandatory branch/PR workflow, squash-merge checks, and store upload protection.

### CI & Release Infrastructure
- Release steps in `android.yml` and `desktop.yml` now guard against overwriting existing releases if a tag or release was already published for a different commit.

## [v1.6.1] — Touch-Friendly Windows App

### Windows
- The audio output list (top bar menu and Settings) is limited in height and scrolls, so PCs with many output devices no longer push the rest of the screen out of view.
- Touchscreen support: lists, Settings, and dialogs scroll by dragging with a finger (Windows delivers touch as mouse input, which Compose Desktop did not drag-scroll), with a wide scrollbar where content overflows.
- Settings, the time signature dialog, and the update dialog scroll when the window is short.

### Shared UI
- Android uses the same scroll limits for the output list and Settings; behavior is otherwise unchanged.

## [v1.6.0] — Anchor for Windows (x64 & arm64)

### Windows
- New Windows app for Intel/AMD (x64) and ARM (arm64) PCs, built with Compose Multiplatform Desktop on the shared core: same timing engine, sounds, settings, and interface as on Android.
- Per-architecture MSI installers (`Anchor-vX.Y.Z-windows-x64.msi`, `Anchor-vX.Y.Z-windows-arm64.msi`) with a bundled Java runtime; per-user installation without administrator rights. Installers are not code-signed yet.
- Keyboard shortcuts: Space (play/pause), Up/Down or +/- (tempo ±1 BPM, with Shift ±5), T (tap tempo).
- In-app update checks offer the installer matching the PC's architecture.
- The audio output stays open while the app runs, so the first beat starts immediately (same approach as Android).

### Shared UI
- The Compose interface moved from the Android app into a new shared Compose Multiplatform module (`sharedUi`) used by Android and Windows. Android behavior and appearance are unchanged; Material You colors and vibration settings remain Android-only.

### CI & Documentation
- `desktop.yml` now builds and tests the Windows app on x64 and arm64 runners and attaches both MSIs to the GitHub Release.
- README, website (Windows downloads, platforms, FAQ), and developer documentation updated for the Windows app.

## [v1.5.5] — Instant First Beat on Android & iPhone, Multi-Platform Documentation

### Android
- Pressing Play now starts the first beat without the previous start-up delay. While the app is on screen, the audio output stays open in standby (inaudible dither), so Beat 1 is placed in the next audio chunk instead of re-creating the output and waking the audio hardware first. The output closes when the app leaves the foreground while paused.
- Playback starts before the media notification/service is updated, so service start-up no longer delays the first beat.
- Starting playback from the notification while the app is in the background keeps the full hardware warmup.

### iPhone
- Same instant start: while the app is open, the audio engine stays running with the player stopped, so Play only queues Beat 1 and starts the player instead of building and starting a new audio engine and running the warmup.
- While idle, Anchor's audio session mixes with other apps, so opening Anchor no longer interrupts music from other apps; pressing Play takes over playback as before.
- Pausing drops already-queued clicks immediately.

### Shared Core
- Added `MetronomeSequencer.alignToOutputFrame` for warm starts on an already-running output and an optional `IAudioEngine.setOutputStandby` hook (implemented on Android and iPhone; no-op on desktop).

### Documentation & Website
- README, website (new Platforms section, FAQ, hero, technical page), and developer docs now describe Android, iPhone, and the in-preparation Windows app (x64 and arm64).

## [v1.5.4] — Windows Preparation (x64 & arm64)

### Shared Core
- Added a JVM `desktop` target to the shared module so the shared logic is built and tested for a future Windows app on both x64 and arm64 (Kotlin/Native has no Windows ARM64 target).
- Added desktop platform implementations without a desktop app: `javax.sound` audio output on the shared sequencer, `java.util.prefs` settings storage, a GitHub update check that selects the installer for the machine's architecture, Windows architecture detection, and a composition root for the future Compose Multiplatform Desktop app.
- Release version selection accepts an optional per-architecture asset marker (`windows-x64` / `windows-arm64`).

### CI & Documentation
- Added the `desktop.yml` workflow, which runs the shared and desktop tests on Windows x64 and Windows arm64 runners.
- Added `docs/windows.md` with the Windows architecture and remaining steps (desktop app module, shared Compose UI, per-architecture MSI packaging, signing, media keys).
- No changes to the Android or iPhone apps.

## [v1.5.3] — Toolchain & Library Updates

### Build
- Updated the Android Gradle Plugin to 9.5.0, Kotlin to 2.4.21, KSP to 2.3.12, and kotlinx-coroutines to 1.11.0 (Gradle 9.8.2 and Robolectric 4.17 were updated separately).
- Updated the Jetpack Compose BOM to 2026.09.00 and AndroidX Core to 1.19.1, and raised `compileSdk` to 37 as the new Compose libraries require (`targetSdk` unchanged).
- No changes to app behavior.

## [v1.5.2] — Steady Pulse During Live Tempo & Time-Signature Changes

### Timing
- Reworked the shared sequencer around a single beat-phase accumulator. Changing the subdivision or time signature during playback no longer shifts the downbeat; subdivision changes apply at the next subdivision of the current beat and time-signature changes at the next beat.
- BPM changes, including rapid slider movement, continue to glide smoothly without interrupting the pulse.

### Visuals
- The pendulum position is now interpolated from per-chunk beat-phase checkpoints recorded by the sequencer, so it stays continuous through tempo, subdivision, and time-signature changes.
- The beat bar and subdivision indicator no longer reset to the first beat when the time signature or subdivision changes during playback.
- Notification (Android) and Now Playing (iOS) updates during rapid BPM or meter changes are debounced (150 ms) to avoid UI stutter; play and pause still update immediately.

### Website & CI
- Synchronized the website's animated preview: the pendulum and the highlighted beat share one clock, so each swing ends exactly on the next lit beat at the displayed 120 BPM.
- Moved the GitHub Pages deployment into its own workflow (`.github/workflows/pages.yml`), separate from the Android build.

## [v1.5.1] — Repository Restructure

### Project Structure
- Renamed the Android module from `app/` to `androidApp/` (Kotlin Multiplatform convention alongside `shared/` and `iosApp/`) and moved Kotlin sources from `src/*/java/` to `src/*/kotlin/`.
- Moved the GitHub Pages website from `docs/` to `website/`; `docs/` now holds developer documentation (`docs/architecture.md`, formerly `ARCHITECTURE.md`, and the new `docs/distribution.md`).
- Grouped iOS sources into `iosApp/Anchor/App/`, `Views/`, and `Resources/`.
- Renamed the Android workflow to `.github/workflows/android.yml` and removed the unused `generate_release_notes.py` script.

### Contributor Tooling
- Added `CONTRIBUTING.md`, `SECURITY.md`, issue and pull request templates, `.editorconfig`, and Dependabot updates for Gradle and GitHub Actions.

## [v1.5.0] — Store-Ready Distribution Builds, Privacy Policy & License

### Distribution Channels
- Added a shared `DistributionChannel` (`GITHUB`, `FDROID`, `PLAY_STORE`, `APP_STORE`). Only GitHub builds check GitHub Releases for updates; store and F-Droid builds receive updates from their store and hide the update check in Settings.
- Added Android product flavors `github`, `fdroid`, and `play`. The `fdroid` and `play` builds remove the `INTERNET` permission, so they make no network requests.
- Added the `ANCHOR_DISTRIBUTION` iOS build setting (`GITHUB` for the unsigned CI build, `APP_STORE` for App Store archives).

### Build & CI
- The Android workflow builds the `github` APK for GitHub Releases, verifies the `fdroid` flavor, produces a Google Play App Bundle (`.aab`) artifact, and checks that the Play build does not request `INTERNET`.
- Added optional tag-triggered uploads to the Google Play internal track and to App Store Connect / TestFlight; both are skipped until their signing and API secrets are configured.

### Documentation & Compliance
- Added a privacy policy (`docs/privacy.html`), the Apache 2.0 `LICENSE` file, and F-Droid metadata (`fastlane/metadata/android`).
- Documented the distribution model: free and open source on GitHub and F-Droid, paid on Google Play and the App Store.

## [v1.4.0] — iPhone App & Shared Kotlin Multiplatform Core

### iPhone App
- Added a native iPhone app (SwiftUI, iOS 16+) with the same single-screen metronome: BPM controls and slider, tempo marking, pendulum, beat bar with per-count accent cycling, time signature presets and custom meters, subdivisions, sound sets, tap tempo, half/double time, screen flash, and haptic feedback.
- Added iOS audio output built on `AVAudioEngine` with background playback, Lock Screen / Control Center "Now Playing" info, and remote transport commands (play, pause, next/previous track for +1/-1 BPM).
- Added the iOS settings sheet with sound set, pitch, feedback toggles, visual/vibration/Bluetooth calibration offsets (slider plus -10/-1/0/+1/+10 ms steps), theme and accent color, Play/Tap trigger mode, audio route information, and a GitHub release update check.

### Shared Kotlin Multiplatform Core
- Moved all platform-independent logic into a new `shared` Kotlin Multiplatform module used by both apps: domain models, `DelayCompensationEngine`, `TapTempoCalculator`, click synthesis (`ClickSynthesizer`), the warmup/sequencing/dispatch core (`MetronomeSequencer`), audio route normalization, the settings repository (`KeyValueSettingsRepository`), release version comparison, and the `MetronomeController` state holder.
- Reduced the Android app to platform implementations only (`AudioTrack` I/O, `Vibrator` haptics, `SharedPreferences` storage, `HttpURLConnection` update transport, foreground media service); `MetronomeViewModel` now hosts the shared controller.
- Moved domain unit tests into the shared module and added tests for the controller, sequencer, settings repository, route selection, and release versioning.

### Build & CI
- Added the `Build iOS App` GitHub Actions workflow (`ios.yml`): runs the shared tests on the iOS simulator, builds the app for the simulator, and produces an unsigned `Anchor-vX.Y.Z-unsigned.ipa` artifact with a SHA-256 checksum.
- The Android workflow now also runs the shared module tests (`:shared:testAndroidHostTest`).

## [v1.3.0] — Anchor Metronome Rebrand, Multimedia Notification Controls, Multiplatform-Ready Core & AOSP / Play Store Readiness

### Anchor Metronome Rebrand & Multiplatform-Ready Core Architecture
- Rebranded the application from Pulse Metronome to **Anchor Metronome** (short name **Anchor**) with the tagline *"Rock-solid time for serious play"* across the Android app, package namespace (`com.damiongans.anchor`), GitHub workflows (`Anchor-vX.Y.Z.apk`), documentation, and GitHub Pages website.
- Updated GitHub repository links, release workflows, and the in-app/website update checkers to target `DamionGans/Anchor` with automatic fallback to `DamionGans/PulseMetronome` and automatic migration of existing `SharedPreferences` user settings.
- Separated core domain models (`MetronomeModels.kt`), calculators (`DelayCompensationEngine.kt`, `TapTempoCalculator.kt`), and service contracts (`IAudioEngine.kt`, `IHapticsManager.kt`, `ISettingsRepository.kt`, `IUpdateRepository.kt`) into 100% platform-agnostic Kotlin (zero `android.*` or `androidx.*` imports) while isolating Android implementations (`AndroidHapticsManager.kt`, `SharedPreferencesSettingsRepository.kt`, `MetronomeAudioEngine.kt`) to allow future iOS and desktop implementations.

### Notification Controls, Rhythm & Audio Output
- Upgraded the background playback notification to a full multimedia (`MediaStyle` / `MediaSession`) controller in the notification drawer and lock screen, displaying the current tempo (`BPM` and tempo marking) alongside `-1 BPM`, `Play/Pause`, and `+1 BPM` transport buttons that work both while playing and while paused.
- Preserved beat accents by count (1–16) across time signature changes and sessions so switching meters restores remembered accents for each count unless explicitly modified.
- Updated audio output selection in the top bar and Settings to always display all available outputs, even when only a single output (such as the built-in speaker) is present.
- Added a persisted setting (`ButtonTriggerMode`) allowing the Play/Pause and Tap Tempo buttons to register either on finger touch-down (`ON_PRESS` / button tap) or on finger lift (`ON_RELEASE`).

### Build, AOSP & Google Play Store Compatibility
- Removed the `com.google.gms.google-services` Gradle plugin and all unused Google Play Services / Firebase catalog references so the application builds and runs with zero Google Play Services dependencies on both Google Play devices and plain AOSP distributions.
- Configured explicit Android 12+ `sharedpref` backup and device-transfer extraction rules (`backup_rules.xml` and `data_extraction_rules.xml`) for Google Play Store compliance.
- Removed the background strobe flash from the website app preview so the animated preview maintains a steady background color.

## [v1.2.5] — System Media Volume & Documentation Updates

### Audio & Interface
- Removed the in-app volume slider from Settings and rendered audio waveforms at unity gain so loudness is controlled exclusively through Android system media volume (`AudioManager.STREAM_MUSIC` hardware volume buttons).
- Updated project documentation (`README.md`, `ARCHITECTURE.md`, `AGENTS.md`) and website (`docs/index.html`, `docs/architecture.html`) to separate musician-facing overview content from low-level technical documentation and display an animated app UI preview.

## [v1.2.4] — Playhead-Anchored Delay Compensation Rewrite

### Audio, Visual & Haptic Synchronization
- Replaced the coroutine-based delay logic with `DelayCompensationEngine`, anchoring visual beats, screen flash, pendulum phase, and haptic triggers directly to the hardware `AudioTrack.getPlaybackHeadPosition()` frame counter.
- Eliminated device-dependent ring-buffer and 55ms warmup pre-roll timing offsets while preserving full, unclipped playback of the first beat sound.
- Added bidirectional (`-200ms` earlier to `+300ms` later) calibration with 1ms/10ms fine-tuning steppers for Visual & Screen Flash Offset, Vibration Offset, and Bluetooth Audio Offset.
- Fixed pendulum direction continuity across bar boundaries in odd time signatures (`3/4`, `5/4`, `7/8`) and removed onset spring lag from `BeatBar` indicators.

## [v1.2.3] — Audio Warmup, Background Playback, APK Size Reduction & Release Build

### Audio & Background Execution
- Added a 55ms sub-LSB dither warmup phase on `AudioTrack` startup so the Android AudioFlinger mixer and hardware DAC finish ramping up before sample 0 of the first beat is rendered.
- Added `MetronomePlaybackService` (`mediaPlayback` foreground service) with a partial `WakeLock` and ongoing notification so playback continues uninterrupted when the app is minimized.

### Build, Size & Interface
- Removed Practice Timer and Speed Trainer components to simplify the interface and codebase.
- Replaced unused third-party libraries (`Room`, `Retrofit`, `Moshi`, `OkHttp`, `material-icons-extended`) with built-in platform APIs and inline vector icons.
- Enabled R8 code minification and resource shrinking for release builds and configured GitHub Actions to publish signed Release APKs (`assembleRelease`).

## [v1.2.2] — Timing Controls, Persistent Settings & Version Alignment

### Timing & Visual Synchronization
- Separated timing offset into independent controls for Screen Flash & Visual Delay and Vibration Delay.
- Synchronized screen flash strobe and main screen beat indicators (`BeatBar` and pendulum phase) to share the same visual delay state update.

### Persistence & Versioning
- Persisted all user settings across sessions, including BPM, time signature, subdivision, beat accents, vibration/flash toggles, delay offsets, and Bluetooth compensation.
- Unified version numbers across `CHANGELOG.md`, Gradle APK `versionName`, and GitHub Releases (`v1.2.2`) without build-date suffixes.

## [v1.2.1] — Stability Fix

### Bug Fixes
- Resolved an application crash on startup (`NoSuchMethodException`) caused by missing single-argument constructor bytecode for `AndroidViewModelFactory` reflection in `MetronomeViewModel`.
- Verified `MainActivity` initialization via Robolectric integration testing.

## [v1.2] — Architecture, Reproducible Builds & UI Refinements

### Architecture & Testing
- Decoupled core subsystems (`IAudioEngine`, `IHapticsManager`, `ISettingsRepository`, `IUpdateRepository`) for dependency injection and unit testability.
- Extracted pure domain calculators for Tap Tempo (`TapTempoCalculator`) and Speed Trainer (`SpeedTrainerEngine`).
- Added unit and Robolectric test suites covering domain logic and ViewModel state transitions.

### Build & Security
- Configured reproducible builds by normalizing archive file ordering, stripping build timestamps, and deriving version metadata deterministically from Git commit history.
- Consolidated CI/CD into a single workflow with secret scanning, manifest permission audits, Android Lint checks, APK signature verification, and SHA-256 checksum generation.
- Removed unused third-party Play Services / Firebase dependencies for standalone AOSP compatibility.

### Audio, Haptics & Interface
- Pre-primed the initial PCM buffer prior to starting `AudioTrack` playback to prevent clipping on the first beat.
- Synchronized screen flash strobe (`snap` onset) and hardware-defined haptic clicks with the audio engine.
- Updated UI components with tabular figures (`tnum`), Material You system dynamic color support, and 48dp minimum touch targets.

---

## [v1.0] — Initial Release

### Audio & Timing
- Low-latency PCM synthesis engine with real-time tempo interpolation.
- Hardware output routing detection for built-in speaker, wired headphones, USB DAC, and Bluetooth.
- 5 synthesized sound sets: Woodblock, Mechanical, Digital Beep, Rimshot, and 808 Percussion.
- Configurable beat accents (Accent, Normal, Soft, Mute) and subdivisions (Quarter, Eighth, Triplet, 16th).

### Practice Tools & Interface
- Physical pendulum visualizer, Tap Tempo with outlier filtering, Speed Trainer, and Practice Timer.
- Adjustable Bluetooth visual delay compensation and timing offset.
- Light, Dark, and System theme modes with Android 13+ themed icon support.
- GitHub Releases update checker.
