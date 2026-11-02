# Anchor Metronome — Technical Architecture & Implementation Guide

Wait-free, sample-accurate timing on Android requires bypassing high-level media players and timer loops. This document explains how **Anchor Metronome** (**Anchor**) achieves rock-solid audio synthesis, zero-clipping first beats, frame-accurate visual and haptic synchronization, and a Kotlin Multiplatform core shared by the Android and iPhone apps (and prepared for Windows x64 + arm64).

> Looking for the musician overview and download links? See the main [README.md](../README.md) or visit the [Anchor Metronome Website](https://damiongans.github.io/Anchor/).

---

## 1. Real-Time PCM Audio Engine (`MetronomeAudioEngine`)

Standard Android timing APIs (`Handler`, `Timer`, `ScheduledExecutorService`, or `SoundPool`) suffer from garbage collection pauses, OS thread scheduling jitter, and variable mixer latency. Anchor avoids these entirely by synthesizing continuous raw audio on a dedicated real-time thread.

### Continuous 44.1 kHz Audio Stream
- **Sample Rate & Format**: 44,100 Hz (`SAMPLE_RATE = 44100`), 16-bit mono linear PCM (`AudioFormat.ENCODING_PCM_16BIT`, `CHANNEL_OUT_MONO`).
- **Low-Latency Routing**: Configured via `AudioAttributes.USAGE_MEDIA` and `AudioTrack.PERFORMANCE_MODE_LOW_LATENCY`.
- **Thread Priority**: Runs on a dedicated thread promoted to `Process.THREAD_PRIORITY_URGENT_AUDIO`.
- **Zero-Allocation Loop**: All 6 click voices (`Tick`, `Click`, `Woodblock`, `Cowbell`, `Beep`, `Hi-Hat`) and their pitch variants (Accent, Regular, Subdivision) are synthesized mathematically into `ShortArray` buffers at initialization using sine/triangle/noise oscillators and cosine-tapered decay envelopes. The real-time loop performs zero heap allocations.

### Solving the "Swallowed First Beat" Problem (55ms Sub-LSB Hardware Warmup)
On Android, transitioning an `AudioTrack` from `STATE_INITIALIZED` to `PLAYSTATE_PLAYING` requires waking the OS `AudioFlinger` mixer thread and the device's physical DAC/amplifier out of power-saving standby. This hardware wake-up typically takes **25ms to 50ms**. If Beat 1's transient (`4–15ms` duration) is written at sample `0` immediately upon playback start, the hardware wake-up clips or completely swallows the first click.

To guarantee Beat 1 plays in full without adding noticeable startup lag:
1. Before sample `0` of Beat 1, `MetronomeAudioEngine` writes **`WARMUP_FRAMES = (44100 * 55) / 1000` (2,425 frames / 55ms)** of alternating `±1` PCM amplitude (sub-LSB dither at `-90.3 dBFS`).
2. Why `±1` instead of `0`? Many OEM audio HALs employ zero-sample silence detection that keeps the amplifier gated until non-zero samples arrive. A `±1` LSB toggle forces the entire signal path awake while remaining completely inaudible to the human ear.
3. Sample `0` of Beat 1 is written immediately after frame `2,425`, hitting an already-active DAC so the very first transient is crisp and intact.

### Instant Start: Output Standby (Android & iPhone)
Creating an `AudioTrack`, starting it, and running the warmup on every press of Play adds a noticeable delay before Beat 1. While the app is visible (`MainActivity.onStart` → `setOutputStandby(true)`, `onStop` → `false`), `MetronomeAudioEngine` keeps **one `AudioTrack` and its output thread open in standby**, continuously writing the same inaudible `±1` dither:
- On Play, the output thread aligns the sequencer with the running output timeline (`prepare(firstBeatFrame)` + `alignToOutputFrame(outputFrames)`) and places Beat 1 at the start of the very next chunk. The hardware is already awake, so no extra warmup is needed (the warmup only covers the part of the 55 ms not yet written).
- On Pause, the output returns to standby dither; when the app leaves the foreground while paused, the track is closed (no background audio or battery cost). Playback started from the notification while the app is in the background takes the cold-start path with the full warmup.
- `MetronomeController.play()` starts the audio engine **before** syncing the media session / foreground service, so service start-up never delays Beat 1.
- A play-generation counter guarantees that a fast Pause → Play restarts cleanly at Beat 1.

**iPhone** (`IosAudioEngine`): while the scene is not in the background (`AnchorApp` `scenePhase`), the `AVAudioEngine` keeps running with the player node stopped, which keeps the hardware awake. Play restarts the player node (sample time 0 = sequencer frame 0), queues Beat 1, and calls `node.play()`; the warmup only runs if the engine started less than ~55 ms ago. Pause calls `node.stop()`, dropping already-queued clicks immediately. While idle, the session uses `.playback` + `.mixWithOthers` so opening Anchor never interrupts other apps; Play switches to the non-mixing category (Now Playing owner) as before.

---

## 2. Playhead-Anchored Delay Compensation (`DelayCompensationEngine`)

A common pitfall in mobile metronomes is triggering UI flashes or vibrations when the audio thread *writes* a beat buffer (`writeFrame`), or using `Thread.sleep()` estimates. Because `AudioTrack.write()` blocks based on the OEM hardware buffer size—which varies wildly across devices and Bluetooth codecs—write-head triggering causes visuals and vibrations to fire far ahead of the audible click.

### Hardware Playhead Synchronization
Anchor anchors all non-audio events to **`AudioTrack.getPlaybackHeadPosition()`**:
- The 32-bit unsigned frame counter returned by `getPlaybackHeadPosition()` is unwrapped into a monotonic 64-bit frame index and shifted by `-WARMUP_FRAMES` so that `playheadFrame == 0` corresponds to the exact sample frame when Beat 1 exits the audio pipeline.
- At `0 ms` user offset, `DelayCompensationEngine` fires visual and vibration callbacks on the exact frame `playheadFrame >= beatAudioFrame`—with zero hidden magic constants or arbitrary frame guesses.

### Bidirectional Offset Math (`-200 ms` to `+300 ms`)
Different devices exhibit different display pipeline latencies (60Hz vs. 120Hz panels) and haptic motor spin-up times (linear resonant actuators vs. eccentric rotating mass motors). Furthermore, wireless Bluetooth headphones introduce significant A2DP/LE audio encoding delay.

`DelayCompensationEngine` computes separated target frames for every scheduled beat and subdivision:

```text
effectiveVisualDelayMs    = flashDelayMs + (if (isBluetoothConnected) bluetoothCompensationMs else 0)
effectiveVibrationDelayMs = vibrationDelayMs + (if (isBluetoothConnected) bluetoothCompensationMs else 0)

visualTargetFrame    = beatAudioFrame + round((effectiveVisualDelayMs / 1000.0) * 44100)
vibrationTargetFrame = beatAudioFrame + round((effectiveVibrationDelayMs / 1000.0) * 44100)
```

- **Negative Offsets (`-200 ms` to `-1 ms`)**: Shifts `targetFrame` *earlier* than `beatAudioFrame`. Because the audio stream begins at `playheadFrame = -WARMUP_FRAMES` (`-55 ms`), Beat 1 can also trigger early during the warmup window (`Beat 1 Lookahead`) without clipping the first audio click.
- **Positive Offsets (`+1 ms` to `+300 ms`)**: Shifts `targetFrame` *later* than `beatAudioFrame`, allowing visuals and haptics to wait for delayed external or Bluetooth speaker output.
- **Unified Visual Trigger**: The beat indicator (`BeatBar`), pendulum phase, and full-screen flash overlay share the same `flashDelayMs` schedule and snap immediately on beat onset (`Animatable.snapTo`) with zero fade-in lag before decaying smoothly.

---

## 3. Background Playback & System Media Controls

When a musician switches to a PDF sheet music reader or locks their phone screen, background execution limits can throttle CPU threads. Both platforms implement the shared `IPlaybackSession` contract; the shared `MetronomeController` publishes one `PlaybackSessionState` (`"<BPM> BPM • <TempoMarking>"`, `"Playing • 4/4 Time"`) and receives transport commands through `PlaybackCommandListener`.

**Android (`AndroidPlaybackSession` → `MetronomePlaybackService`)**
- Runs as an explicit `foregroundServiceType="mediaPlayback"` service.
- Acquires a partial `PowerManager.WakeLock` while playing (released when paused) so the CPU keeps servicing the high-priority PCM loop with the screen off.
- An active `MediaSession` and `Notification.MediaStyle` controller provides `-1 BPM`, `Play/Pause`, and `+1 BPM` controls, both as compact `Notification.Action`s and in the Android 13+ media carousel (`PlaybackState.CustomAction`).
- Title `"120 BPM • Allegro"`, subtitle `"Playing • 4/4 Time"` / `"Paused • 4/4 Time"`. Pausing keeps the notification (with a Play button) instead of dismissing it.
- Updates (GitHub flavor): `AppUpdateManager` checks `DamionGans/Anchor` (fallback `DamionGans/PulseMetronome`) over `HttpURLConnection` and opens the APK with a zero-permission `Intent.ACTION_VIEW`, so the app never needs `REQUEST_INSTALL_PACKAGES`.

**Windows**: no system media controls yet (`NoOpPlaybackSession`; SMTC planned, see `windows.md`).

**iOS (`IosPlaybackSession`)**
- `AVAudioSession` playback category with the `audio` background mode keeps the engine running when the app is in the background or the screen is locked.
- `MPNowPlayingInfoCenter` shows tempo and meter on the Lock Screen / Control Center; `MPRemoteCommandCenter` maps play, pause, toggle, stop, and next/previous track (`+1` / `-1 BPM`).

---

## 4. Kotlin Multiplatform Architecture (Android + iPhone + Windows)

All application logic lives once in the `shared` Kotlin Multiplatform module. Platform code is limited to I/O and UI: the Android app (`androidApp/`), the Windows app (`desktopApp/` + `shared/src/desktopMain`), and the iPhone app (`iosApp/`, SwiftUI, + `shared/src/iosMain`). Android and Windows render the same Compose Multiplatform UI from `sharedUi/`.

```text
shared/src/commonMain/kotlin/com/damiongans/anchor/     # Shared logic (no platform imports)
├── model/MetronomeModels.kt            # Immutable domain models
├── domain/
│   ├── DelayCompensationEngine.kt      # Frame-accurate visual/haptic/Bluetooth scheduler
│   └── TapTempoCalculator.kt           # Tap tempo outlier filtering & pulse detection
├── audio/
│   ├── IAudioEngine.kt                 # Audio engine contract
│   ├── BaseMetronomeAudioEngine.kt     # Shared engine state, clamping, route publishing
│   ├── ClickSynthesizer.kt             # Click waveform synthesis (all sound sets)
│   ├── MetronomeSequencer.kt           # Warmup, sample-accurate sequencing, playhead dispatch,
│   │                                   #   PlayheadEstimator, WrappingFrameCounter
│   └── AudioRouteSelector.kt           # Output list normalization & default route priority
├── haptics/IHapticsManager.kt          # Haptics contract
├── playback/IPlaybackSession.kt        # System media controls contract
├── repository/
│   ├── ISettingsRepository.kt          # Settings contract & default accent rules
│   └── KeyValueSettingsRepository.kt   # Shared keys/defaults/clamping over IKeyValueStore
├── update/
│   ├── IUpdateRepository.kt            # Update check contract & AppReleaseInfo
│   └── ReleaseVersioning.kt            # GitHub repo config, asset selection, SemVer comparison
└── viewmodel/MetronomeController.kt    # MetronomeUiState + all user actions (shared state holder)

shared/src/iosMain/kotlin/com/damiongans/anchor/        # iOS platform implementations
├── audio/IosAudioEngine.kt             # AVAudioEngine + AVAudioPlayerNode I/O loop
├── haptics/IosHapticsManager.kt        # UIImpactFeedbackGenerator
├── repository/NSUserDefaultsKeyValueStore.kt
├── update/IosUpdateRepository.kt       # NSURLSession + NSJSONSerialization transport
├── playback/IosPlaybackSession.kt      # Now Playing + remote commands
└── ios/AnchorIosApp.kt                 # Swift entry point (wires iOS implementations)

shared/src/desktopMain/kotlin/com/damiongans/anchor/    # Desktop JVM (Windows x64 + arm64 app)
├── audio/DesktopAudioEngine.kt         # javax.sound SourceDataLine I/O loop (+ output standby) on the shared sequencer
├── repository/PreferencesKeyValueStore.kt  # java.util.prefs (Windows registry)
├── update/DesktopUpdateRepository.kt   # java.net.http, per-architecture .msi selection
├── platform/WindowsArch.kt             # X64 / ARM64 detection
└── desktop/AnchorDesktop.kt            # Composition root used by desktopApp (see windows.md)

sharedUi/src/                            # Compose Multiplatform UI (android + jvm "desktop")
├── commonMain/.../ui/                  # MetronomeScreen(controller), components/, dialogs/, icons/, theme/
│   └── platform/PlatformUi.kt          # expect hooks: dynamic color, system bars, labels, vibration, version
├── androidMain/.../ui/platform/        # Material You dynamic color, status/navigation bars
└── desktopMain/.../ui/platform/        # Windows defaults

desktopApp/                              # Windows host: Main.kt (window, icon), KeyboardShortcuts.kt, MSI packaging

androidApp/src/main/kotlin/com/damiongans/anchor/       # Android platform layer
├── audio/MetronomeAudioEngine.kt       # AudioTrack Fast Path I/O loop & device routing
├── haptics/AndroidHapticsManager.kt    # VibrationEffect haptics
├── repository/SharedPreferencesSettingsRepository.kt  # SharedPreferences store + legacy migration
├── service/                            # MetronomePlaybackService + AndroidPlaybackSession
├── update/AppUpdateManager.kt          # HttpURLConnection + org.json transport, ACTION_VIEW
└── viewmodel/MetronomeViewModel.kt     # AndroidViewModel hosting MetronomeController (UI comes from sharedUi)

iosApp/                                  # SwiftUI app (XcodeGen project.yml)
├── Anchor/App/                         # AnchorApp.swift, MetronomeStore.swift (display-link
│                                       #   throttled ObservableObject bridge), Theme.swift
├── Anchor/Views/                       # ContentView, SettingsView, TimeSignatureView
└── Anchor/Resources/Assets.xcassets    # App icon & accent color
```

### Shared audio pipeline
All platform engines (Android, iOS, desktop) extend `BaseMetronomeAudioEngine` and drive the same `MetronomeSequencer`:
1. `prepare()` resets the timeline; the platform writes `MetronomeSequencer.warmupChunkCount(...)` chunks of `±1` LSB dither (`advanceWarmup`). A warm start on an output that is already running (Android standby) calls `alignToOutputFrame(outputFrames)` instead.
2. `startFirstBeat()` anchors Beat 1 at the exact post-warmup frame.
3. `renderChunk()` synthesizes clicks sample-accurately (slew-limited BPM changes) and records every tick in `DelayCompensationEngine`.
4. The platform reports its hardware playhead (Android `AudioTrack.getPlaybackHeadPosition()`, iOS player-node sample time, desktop `SourceDataLine.longFramePosition`), smoothed by `PlayheadEstimator`, and `processPlayhead()` dispatches visual, vibration, bar, and pendulum-phase events.

The iOS loop keeps four 256-frame buffers scheduled ahead of the player-node playhead on a high-priority thread; Android writes blocking chunks to `AudioTrack`.

### Swift interop
`AnchorIosApp` exposes Swift-friendly methods (no default arguments), catalog lists for pickers, and `observe(onChange:)`, which delivers `MetronomeUiState` on the main thread. `MetronomeStore` publishes the latest state at most once per display frame.

---

## 5. Minimal APK Footprint, Plain AOSP & Google Play Store Compatibility

Anchor avoids bloated third-party dependencies so the release APK remains fast, self-contained, and lightweight (~2 MB):
- **Zero Google Play Services Dependencies**: Built exclusively on standard Android platform APIs and Jetpack Compose (`androidx.*`). Contains zero `com.google.android.gms` or Firebase SDKs, ensuring 100% compatibility on plain AOSP, LineageOS, GrapheneOS, and Google Play devices alike.
- **Zero Heavy Libraries**: Uses platform `HttpURLConnection` and `org.json` instead of Retrofit/OkHttp/Moshi, custom Compose vector paths (`AnchorIcons.kt`) instead of `material-icons-extended`, and `SharedPreferences` for instant settings persistence.
- **R8 & Resource Shrinking**: Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true`.
- **Reproducible Archives**: Archive tasks enforce `isPreserveFileTimestamps = false` and `isReproducibleFileOrder = true` for deterministic APK packaging.

---

## 6. Builds & CI

- **Distribution channels**: shared `DistributionChannel` (`GITHUB`, `FDROID`, `PLAY_STORE`, `APP_STORE`). Only GitHub builds run the GitHub Releases update checker; Android `fdroid` and `play` flavors also drop the `INTERNET` permission. iOS selects the channel with the `ANCHOR_DISTRIBUTION` build setting.

- **Android** (`.github/workflows/android.yml`, every branch push and PRs; publishing only from `main` / `v*` tags): lint, `testGithubDebugUnitTest`, `:shared:testAndroidHostTest`, reproducible signed `assembleGithubRelease` (GitHub Release APK), `assembleFdroidRelease`, `bundlePlayRelease` (Play AAB, optional tag upload to the internal track), and the GitHub Release.
- **Windows** (`.github/workflows/desktop.yml`): on Windows x64 (`windows-latest`) and Windows arm64 (`windows-11-arm`): `:shared:desktopTest`, then `:desktopApp:packageMsi` staged as `Anchor-vX.Y.Z-windows-{x64,arm64}.msi` (+ SHA-256); on `main` / `v*`, both MSIs are attached to the GitHub Release. See [`windows.md`](./windows.md).
- **Website** (`.github/workflows/pages.yml`): deploys `website/` to GitHub Pages on `main` / `v*` pushes that touch `website/**` or `CHANGELOG.md`, independent of the app builds.
- **iPhone** (`.github/workflows/ios.yml`, macOS runner): `:shared:iosSimulatorArm64Test`, `xcodegen generate`, simulator build, and an unsigned device build packaged as `Anchor-vX.Y.Z-unsigned.ipa` (+ SHA-256) uploaded as a workflow artifact; on tags, an optional App Store Connect upload (`ANCHOR_DISTRIBUTION=APP_STORE`).
- **Local iOS build**: `brew install xcodegen`, `cd iosApp && xcodegen generate`, open `Anchor.xcodeproj`. The "Build Kotlin shared framework" phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`.

---

## 7. Contributing & AI Agent Guidelines

For developer workflow rules, testing requirements, and release checklists, see [**`AGENTS.md`**](../AGENTS.md).

---

## 8. Test Inventory

- **Shared** (`shared/src/commonTest`, `kotlin.test`; runs on the Android host, desktop JVM, and iOS simulator):
  - `TapTempoCalculatorTest`
  - `DelayCompensationEngineTest`: 0 ms exact frame match, negative/positive offsets, Bluetooth shift, subdivisions
  - `MetronomeControllerTest`: state, persistence, calibration clamping, playback session commands, update flow, store channels never self-update, output standby passthrough
  - `MetronomeSequencerTest`: synthesis, warmup, exact beat frames, warm start on a running output, steady pulse under subdivision/meter changes, pendulum continuity, playhead estimator, route selection
  - `KeyValueSettingsRepositoryTest`, `ReleaseVersioningTest`
- **Desktop** (`shared/src/desktopTest`, run on Windows x64 + arm64): `DesktopAudioEngineTest` (lifecycle and standby without an audio device), `PreferencesKeyValueStoreTest`, `WindowsArchTest`, `DesktopUpdateRepositoryTest`.
- **Android** (`androidApp/src/test`, Robolectric): `MetronomeUnitTest`, `MetronomeViewModelTest` (ViewModel wiring, notification service), `AppUpdateManagerTest` (`BuildConfig.VERSION_NAME` alignment), `ExampleRobolectricTest`.
