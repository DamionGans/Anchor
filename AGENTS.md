# AGENTS.md — Anchor Rules

Canonical rules for **Anchor Metronome** (`DamionGans/Anchor`) on Android, iOS, and Windows. See `docs/architecture.md`.

## 1. General
- Keep factual tone, no changelog emojis. Never publish to app stores unless explicitly requested.
- Update this file in the same commit whenever architecture, audio invariants, or build setup changes.

## 2. Product & Channels
- **Name**: "Anchor Metronome" (short: "Anchor"). Tagline: *"Rock-solid time for serious play"*.
- **Monetization**: Free/open-source on GitHub + F-Droid; paid on Google Play + App Store. Never add ads, analytics, subscriptions, IAP, accounts, or feature gating. Copy never calls store builds free.
- **Channels**: `DistributionChannel` (`GITHUB`, `FDROID`, `PLAY_STORE`, `APP_STORE`). Only `GITHUB` self-updates; store builds drop `INTERNET` and update UI.
- **UI Principle**: Single-screen, non-scrolling UI with all core controls visible. No timers, trainers, or sub-screens.

## 3. Project Structure
- `shared/src/commonMain`: All logic, state, timing, synthesis, and persistence.
- `sharedUi`: Compose Multiplatform UI for Android and Desktop.
- `androidApp`, `desktopApp` (JVM 17), `iosApp` (SwiftUI): Platform hosts only.

## 4. Architecture Boundaries
- **`commonMain` Purity**: No platform imports (`android.*`, `java.*`), no `@Synchronized` (use `@Volatile`).
- **UI Parity**: Mirror all features between Compose Multiplatform (`sharedUi`) and SwiftUI (`iosApp`). Touch targets ≥48dp.
- **DI & Interop**: Keep `@JvmOverloads` on `MetronomeViewModel`. iOS talks only to `AnchorIosApp`.

## 5. Audio & Timing Invariants
- **Synthesis**: `ClickSynthesizer` (pre-rendered `ShortArray`s, 0 audio allocations), slew-limited BPM in `MetronomeSequencer`.
- **Engines**: Android (`AudioTrack` Fast Path), iOS (`AVAudioPlayerNode` with 4×256 frame lookahead), Desktop (`SourceDataLine` 64-bit frame pos).
- **Warmup & Standby (Never Remove)**: Cold start writes ≥55ms dither before Beat 1. Standby keeps lines primed while UI is active. `play()` calls `engine.start()` before `syncPlaybackSession()`.
- **Playhead Anchoring (Never Write-Head)**: UI, flash, and haptics anchor to hardware playhead via `PlayheadEstimator` (`playheadFrame >= beatAudioFrame`).
- **Offsets**: −200…+300 ms. Steady pulse accumulator: `beatPhase += bpm / (60 * sampleRate)`.

## 6. Playback Session & Updates
- **Media Session**: Android foreground media service with `MediaSession` (stays while paused); iOS Now Playing; Windows `NoOpPlaybackSession`.
- **Updates**: Shared `ReleaseVersioning` selects asset. Android installs via `ACTION_VIEW` (never `REQUEST_INSTALL_PACKAGES`).

## 7. UI & Persistence
- **Controls**: No in-app volume slider (system volume at unity gain). Accents: 16-entry remembered pattern per meter.
- **Persistence**: Save state through `ISettingsRepository`.

## 8. Android Constraints
- **Zero Google Services**: Pure AOSP compatible; no Play Services, Firebase, or external networking.
- **Permissions**: Minimum required (`INTERNET` only on `github` flavor; no storage/install permissions).

## 9. CI & Releases
- **Versioning**: SemVer (`versionCode = MAJOR*10000 + MINOR*100 + PATCH`). Never change Windows `upgradeUuid`.
- **Release Step**: Run `scripts/set-version.sh X.Y.Z`, then update changelogs (`CHANGELOG.md`, `ChangelogDialog.kt`, `website/index.html`, `website/version.json`, `fastlane/`).

## 10. Verify Commands
- **Android**: `./gradlew :androidApp:testGithubDebugUnitTest assembleGithubDebug`
- **Shared**: `./gradlew :shared:testAndroidHostTest :shared:desktopTest`
- **Windows**: `./gradlew :desktopApp:run` / `:desktopApp:packageMsi`
- **iOS**: `cd iosApp && xcodegen generate && xcodebuild -scheme Anchor -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`
