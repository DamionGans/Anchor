# AGENTS.md — Anchor Metronome rules

Canonical rules for all AI agents and contributors. **Anchor Metronome** (short: **Anchor**, formerly Pulse Metronome) for Android, iPhone, and Windows (x64 + arm64). Background and reference: `docs/architecture.md`, `docs/distribution.md`, `docs/windows.md`.

## 1. Always
- Read this file before changing anything. Update it in the same commit when you change architecture, timing/audio behavior, UI rules, persisted settings, build/release setup, or standing user instructions. Keep rules here only (`CLAUDE.md`, `.github/copilot-instructions.md` just point here).
- Neutral, factual tone in commits, changelogs, release notes, docs; no emojis in changelogs.
- Never upload to Google Play, the App Store, F-Droid, or the Microsoft Store unless the user asks.

## 2. Product
- Name: full **Anchor Metronome** (titles, `app_full_name`, `metadata.json`, About sheet, media album, website `<title>`/OG, release titles); short **Anchor** (launcher `app_name`, iOS `CFBundleDisplayName`, "ANCHOR" top bar, `com.damiongans.anchor`, `Anchor-vX.Y.Z.apk`). Titles ≤ 30 chars, no emojis/buzzwords.
- Tagline everywhere: **"Rock-solid time for serious play"**. It is a musical metronome, never a "timekeeping" app.
- Repo `DamionGans/Anchor` (Pages `damiongans.github.io/Anchor`); update checks and `website/app.js` fall back to `DamionGans/PulseMetronome`.
- Channels (standing decision): free and open source on GitHub + F-Droid; same app as a one-time paid purchase on Google Play + App Store. Never add ads, subscriptions, IAP/billing SDKs, analytics, accounts, or channel feature gating. Copy must not call store versions free ("paid store versions support development"). Keep `website/privacy.html` accurate and linked.
- `DistributionChannel` (`GITHUB`, `FDROID`, `PLAY_STORE`, `APP_STORE`) via `AppVersionInfo.channel`; only `GITHUB` self-updates (`canCheckForUpdates`/`updateStoreName` hide the update UI). Store builds never download or link APK/IPA updates. Android flavors `github`/`fdroid`/`play`; `fdroid` and `play` drop `INTERNET`. iOS: `ANCHOR_DISTRIBUTION` → `AnchorDistribution` Info.plist key.
- No feature bloat: no Practice Timer / Speed Trainer, multi-screen navigation, accounts, or analytics unless asked. The main screen (`MetronomeScreen.kt`, `ContentView.swift`) stays single-screen and non-scrolling with every core control visible.
- Public copy (`README.md` with the logo on top, `website/index.html`) is musician-focused; technical depth lives in `docs/architecture.md` and `website/architecture.html`, linked from both.
- Website hero = non-interactive HTML/CSS app preview with a steady background (no web audio, no flash overlay, no raster screenshot). Pendulum and beat bar both derive from `--mock-beat` in `website/style.css` (0.5 s = 120 BPM; bar cycle `calc(var(--mock-beat) * 4)`); change timing only there.
- All platforms documented with current status in `README.md`, `website/index.html` (`#platforms`, hero pill, meta, FAQ), `website/architecture.html`, `docs/`: Android (available), iPhone (unsigned IPA / Xcode; App Store coming), Windows x64 + arm64 (unsigned MSIs on GitHub Releases; no Microsoft Store yet).

## 3. Layout
```text
androidApp/  Android platform code (flavors src/fdroid, src/play); hosts sharedUi
desktopApp/  Windows host (Compose Desktop window, shortcuts, MSI packaging, icons/)
iosApp/      SwiftUI iPhone app (XcodeGen project.yml; .xcodeproj git-ignored)
shared/      KMP: ALL app logic (commonMain) + iosMain + desktopMain (jvm "desktop")
sharedUi/    Compose Multiplatform UI for Android + Windows
scripts/     set-version.sh
docs/ website/ fastlane/ signing/(debug keystore only) gradle/ .github/
```
- Kotlin sources in `src/<sourceSet>/kotlin/`; packages under `com.damiongans.anchor`. New top-level folders: update this map, `README.md`, `CONTRIBUTING.md`. Markdown docs go in `docs/`, never `website/`. `metadata.json` and `.env.example` stay at the root (AI Studio).
- Module trees: `docs/architecture.md` §4.

## 4. Boundaries
- **All app logic in `shared/src/commonMain`** (state, actions, persistence keys/defaults/clamping/accent memory, timing, synthesis, sequencing, route rules, version comparison/asset selection). Platform code (`androidApp/`, `iosMain` + `iosApp/`, `desktopMain` + `desktopApp/`) does only OS calls and UI hosting.
- `commonMain`: no `android.*`, `androidx.*`, `java.*`, `platform.*`, `@Synchronized`; use `kotlin.concurrent.Volatile`. `DelayCompensationEngine` timeline methods are audio-thread only.
- **sharedUi** (Compose Multiplatform 1.12.x, material3 via `org.jetbrains.compose.material3`): `MetronomeScreen(controller: MetronomeController)`. `commonMain` UI has no `android.*`, `BuildConfig`, `Context`, `String.format`, `Math.*`; platform differences go in `ui/platform/PlatformUi` (`expect`/`actual`: dynamic color, system bars, labels, `supportsVibration`); hosts provide `LocalAppVersionName`.
- **UI parity**: features go into both `sharedUi` (Android + Windows) and SwiftUI, unless platform-specific.
- **Swift** talks only to `AnchorIosApp` (no default args, `change*` names, catalog lists, `observe(onChange:)` on main). Kotlin `description` is exposed via helpers (`soundSetSummary`, `buttonTriggerModeSummary`); Swift compares enums by `name`.
- **Windows** is the JVM target (`jvm("desktop")`, JVM 17) in `shared`/`sharedUi`; never add `mingwX64` (no Windows ARM64 in Kotlin/Native). `desktopApp` only hosts. `kotlinx-serialization-json` is desktopMain-only (never `commonMain`/APK).
- **DI**: `MetronomeController` gets every platform implementation, `AppVersionInfo`, and a `CoroutineScope` via its constructor (tests use `commonTest/.../fakes/TestFakes.kt` + `TestScope`). `MetronomeViewModel` delegates to `controller` and **must keep `@JvmOverloads constructor`** (AndroidViewModelFactory reflection; removing it crashes on launch).

## 5. Audio & timing invariants
- Synthesis (`ClickSynthesizer`, pre-rendered `ShortArray`s, zero audio-thread allocations), warmup, sequencing, slew-limited BPM, and playhead dispatch are shared (`MetronomeSequencer`). Engines (`MetronomeAudioEngine`, `IosAudioEngine`, `DesktopAudioEngine`) extend `BaseMetronomeAudioEngine` and only push chunks + report the hardware playhead.
- Android: `THREAD_PRIORITY_URGENT_AUDIO` thread, 16-bit mono at the native rate, streaming `AudioTrack` with `PERFORMANCE_MODE_LOW_LATENCY`. iOS: `.playback` session (background audio), `AVAudioEngine` + `AVAudioPlayerNode`, high-priority `NSThread` keeping 4×256 frames scheduled, restart on `AVAudioEngineConfigurationChangeNotification`; route chosen by the OS. Desktop: `SourceDataLine`, `longFramePosition`.
- **Warmup (do not remove)**: ≥ 55 ms of ±1 LSB dither before Beat 1 (`fillWarmupDither`/`warmupChunkCount`) on cold starts.
- **Output standby (instant first beat)**, via `IAudioEngine.setOutputStandby`:
  - Android: on while `MainActivity` is started (`onStart`/`onStop`); one `AudioTrack` writes dither; Play aligns with `prepare(firstBeatFrame)` + `alignToOutputFrame(Long)`, Beat 1 in the next chunk; warmup only covers frames not yet written; play-generation counter restarts fast stop→start at Beat 1; head counter sampled every standby chunk (32-bit wrap).
  - iOS: on unless `scenePhase == .background` (+ `onAppear`); engine runs with the player node stopped; each run restarts the node (sample time 0 = sequencer frame 0), Beat 1 queued before `node.play()`; warmup only if the engine started < warmup duration ago; idle `.playback` + `.mixWithOthers`, `start()` switches to non-mixing; pause = `node.stop()`.
  - Desktop: same loop as Android (line open with dither while the app runs; `Main.kt` enables it).
  - `MetronomeController.play()` calls `audioEngine.start()` **before** `syncPlaybackSession()`.
- Output list: `AudioRouteSelector.normalize` always includes Built-in Speaker; the top-bar selector and Settings audio tab always show all outputs (even one).
- **Playhead-anchored, never write-head**: visuals, flash, pendulum, vibration fire from the hardware playhead (`getPlaybackHeadPosition()`, player-node sample time, `longFramePosition`) smoothed by `PlayheadEstimator`. At 0 ms offset events fire at `playheadFrame >= beatAudioFrame`; no hidden latency constants.
- Offsets −200…+300 ms (negative = earlier). Bluetooth offset applies only while a Bluetooth output is routed and shifts visual + vibration together. Negative offsets may fire Beat 1 during the warmup (lookahead).
- **Steady pulse**: one beat-phase accumulator (`beatPhase += bpm / (60 * sampleRate)`); never a per-subdivision counter. BPM changes are slew-limited (`MAX_BPM_STEP_PER_CHUNK`); subdivision changes apply at the next subdivision boundary (no late ticks); time-signature changes at the next beat (shorter bar → new bar). Covered by `MetronomeSequencerTest`.
- Pendulum interpolates per-chunk phase checkpoints (`recordPhaseCheckpoint`, allocation-free ring buffer); tick interpolation only as fallback.
- `setTimeSignature`/`setSubdivision` never reset `currentBeat`/`currentSubdivisionIndex` while playing. Media-session updates from BPM/meter changes are debounced (`SESSION_SYNC_DEBOUNCE_MS` = 150); play/pause update immediately.
- `BeatBar`, pendulum, and flash share `flashDelayMs` and switch on with `snap()`.

## 6. Playback session & updates
- `MetronomeController` publishes `PlaybackSessionState` (title `"<BPM> BPM • <Marking>"`, subtitle `"Playing • 4/4 Time"`) to `IPlaybackSession`; commands come back via `PlaybackCommandListener`. Android: foreground `mediaPlayback` service with MediaSession, −1 BPM / Play-Pause / +1 BPM, stays while paused. iOS: Now Playing + remote commands (next/previous = ±1 BPM). Windows: `NoOpPlaybackSession`. Details: `docs/architecture.md` §5.
- Update checks: shared `ReleaseVersioning` (repo fallback, `.apk`/`.ipa`/per-arch `.msi` selection, SemVer); platforms only do HTTP/JSON and open the URL. Android opens updates with `ACTION_VIEW` (never `REQUEST_INSTALL_PACKAGES`).

## 7. UI & persistence
- No in-app volume slider (system media volume; `STREAM_MUSIC`, unity gain).
- Accents never reset on time-signature change: 16-entry `rememberedAccents` per count; switching restores `rememberedAccents.take(beats)`.
- `ButtonTriggerMode` (`ON_PRESS`/`ON_RELEASE`) for Play and Tap Tempo.
- Persist via `ISettingsRepository` (shared keys): bpm, timeSignature, subdivision, soundSet, pitchMultiplier, accents, rememberedAccents, buttonTriggerMode, flash/vibration enabled, flash/vibration/bluetooth delays, themeMode, accentColor.
- Each offset in Settings has a slider (−200…+300 ms) plus −10/−1/0/+1/+10 ms buttons.
- Tabular numbers (`"tnum"`) on BPM, counters, ms labels; ≥ 48 dp touch targets; Material You ("Android Phone Theme") + custom accents + System/Light/Dark.

## 8. Android constraints
- Never Google Play Services, Firebase, or the google-services plugin; must work on plain AOSP/LineageOS/GrapheneOS.
- Manifest least-privilege: `INTERNET` (github flavor only), `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`; `usesCleartextTraffic="false"`; never `REQUEST_INSTALL_PACKAGES`/storage permissions.
- APK ~2 MB: no `material-icons-extended` (use `AnchorIcons.kt`), no Retrofit/OkHttp/Moshi/Room/Firebase (use `HttpURLConnection`, `org.json`, `SharedPreferences`).
- Release builds: `isMinifyEnabled`/`isShrinkResources` true; reproducible archives (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`); release artifacts only from `assemble<Flavor>Release`/`bundlePlayRelease`; never sign Play builds with the debug keystore.

## 9. CI & releases
- Workflows: `android.yml` (every branch; GitHub Release only from `main`, `v*`, or manual), `ios.yml` (unsigned IPA artifact), `desktop.yml` (tests + MSI on `windows-latest` x64 and `windows-11-arm` arm64, must stay green on both; attaches MSIs to the release on `main`/`v*`), `pages.yml` (website). Store jobs skip until secrets exist (`docs/distribution.md`). No third-party Swift packages.
- Windows packaging: per-user MSI, jlink JRE, built per architecture on a matching runner; never change `upgradeUuid`; assets `Anchor-vX.Y.Z-windows-{x64,arm64}.msi` (+ `.sha256`), selected by `WindowsArch.assetMarker`. Unsigned until a certificate exists.
- **Versioning**: SemVer, `versionCode = MAJOR*10000 + MINOR*100 + PATCH`; PATCH = fixes/docs, MINOR = features, MAJOR = rare. Check `api.github.com/repos/DamionGans/Anchor/releases` first: if the repo version is **unreleased**, rewrite that single top entry (raise its level if needed); if **released**, bump.
- **Bump**: run `scripts/set-version.sh X.Y.Z` (updates every mechanical location: Gradle defaults, iOS `project.yml`, desktop defaults, code/CI fallbacks, `AppUpdateManagerTest`, `website/version.json`/`app.js`/`index.html` links, README APK name, fastlane file). Then write the text: `CHANGELOG.md` top `## [vX.Y.Z]` (feeds release notes), `CURRENT_RELEASE_CHANGELOG` in `sharedUi/.../dialogs/ChangelogDialog.kt`, `website/index.html` changelog entry (Latest/Previous/Earlier labels), `website/version.json` highlights, `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## 10. Verify
- `./gradlew :androidApp:testGithubDebugUnitTest assembleGithubDebug` (store-channel changes: `assemblePlayRelease bundlePlayRelease assembleFdroidRelease`)
- `./gradlew :shared:testAndroidHostTest :shared:desktopTest` (macOS: `:shared:iosSimulatorArm64Test`)
- Windows: `./gradlew :desktopApp:run` / `:desktopApp:packageMsi` (or `desktop.yml`)
- iOS: `cd iosApp && xcodegen generate && xcodebuild -scheme Anchor -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` (or `ios.yml`)
- Test inventory: `docs/architecture.md` §8.
