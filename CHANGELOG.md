# Changelog

## [v1.3.0] — Anchor Rebrand, Multimedia Notification Controls, Multiplatform-Ready Core & AOSP / Play Store Readiness

### Anchor Rebrand & Multiplatform-Ready Core Architecture
- Rebranded the application from Pulse Metronome to **Anchor** with the tagline *"Rock-solid time for serious play"* across the Android app, package namespace (`com.damiongans.anchor`), GitHub workflows (`Anchor-vX.Y.Z.apk`), documentation, and GitHub Pages website.
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
