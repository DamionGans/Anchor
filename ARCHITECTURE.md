# Anchor — Technical Architecture & Implementation Guide

Wait-free, sample-accurate timing on Android requires bypassing high-level media players and timer loops. This document explains how **Anchor** achieves rock-solid audio synthesis, zero-clipping first beats, frame-accurate visual and haptic synchronization, and a platform-agnostic Kotlin core ready for future iOS and desktop targets.

> Looking for the musician overview and download links? See the main [README.md](./README.md) or visit the [Anchor Website](https://damiongans.github.io/Anchor/).

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

## 3. Background Playback & Multimedia Notification (`MetronomePlaybackService`)

When a musician switches to a PDF sheet music reader or locks their phone screen, Android's background execution limits can throttle CPU threads.
- When playback runs, `MetronomePlaybackService` operates as an explicit `foregroundServiceType="mediaPlayback"` service.
- It acquires a partial `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) while playing (releasing it when paused) so the CPU keeps servicing the high-priority PCM synthesis loop without interruption when the screen is off.
- An active `MediaSession` and `Notification.MediaStyle` controller in the notification drawer and lock screen displays the current tempo (`"<BPM> BPM • <TempoMarking>"`) and provides `-1 BPM`, `Play/Pause`, and `+1 BPM` transport controls.

---

## 4. Clean Architecture & Multiplatform-Ready Core

The project is structured in clean, unit-tested layers under `com.damiongans.anchor`. All core domain models, timing/tap calculators, and service interfaces are written in 100% pure Kotlin (zero `android.*` or `androidx.*` imports) so future iOS and desktop targets can share the core engine while Android-specific implementations live in dedicated platform files:

```text
app/src/main/java/com/damiongans/anchor/
├── MainActivity.kt                  # Edge-to-edge Compose host & lifecycle coordinator
├── audio/
│   ├── IAudioEngine.kt              # Pure Kotlin audio engine interface (multiplatform-ready)
│   └── MetronomeAudioEngine.kt      # Android 44.1 kHz PCM synthesizer & playhead loop
├── domain/
│   ├── DelayCompensationEngine.kt   # Pure Kotlin frame-accurate visual/haptic/BT scheduler
│   └── TapTempoCalculator.kt        # Pure Kotlin BPM clamping & tap-tempo outlier filtering
├── haptics/
│   ├── IHapticsManager.kt           # Pure Kotlin haptics interface (multiplatform-ready)
│   └── AndroidHapticsManager.kt     # Android precomputed VibrationEffect waveforms (Accent/Beat/Sub)
├── model/
│   └── MetronomeModels.kt           # Pure Kotlin immutable domain models (zero Android/Compose imports)
├── repository/
│   ├── ISettingsRepository.kt       # Pure Kotlin settings persistence contract & default accent rules
│   └── SharedPreferencesSettingsRepository.kt # Android SharedPreferences persistence implementation
├── service/
│   └── MetronomePlaybackService.kt  # Android foreground mediaPlayback service, MediaSession & WakeLock
├── ui/
│   ├── MetronomeScreen.kt           # Non-scrolling single-screen musician UI
│   ├── components/                  # BeatBar, PendulumVisualizer, QuickControlsBar, TempoControls
│   ├── dialogs/                     # SettingsBottomSheet, TimeSignatureDialog, ChangelogDialog, UpdateAvailableDialog
│   ├── icons/                       # AnchorIcons.kt custom Compose vector icons
│   └── theme/                       # Material 3 Dynamic Color & custom accent palettes
├── update/
│   ├── IUpdateRepository.kt         # Pure Kotlin release metadata model & update check contract
│   └── AppUpdateManager.kt          # Semantic version comparison & GitHub Release checker (DamionGans/Anchor)
└── viewmodel/
    └── MetronomeViewModel.kt        # StateFlow state holder with constructor injection
```

---

## 5. Minimal APK Footprint, Plain AOSP & Google Play Store Compatibility

Anchor avoids bloated third-party dependencies so the release APK remains fast, self-contained, and lightweight (~2 MB):
- **Zero Google Play Services Dependencies**: Built exclusively on standard Android platform APIs and Jetpack Compose (`androidx.*`). Contains zero `com.google.android.gms` or Firebase SDKs, ensuring 100% compatibility on plain AOSP, LineageOS, GrapheneOS, and Google Play devices alike.
- **Zero Heavy Libraries**: Uses platform `HttpURLConnection` and `org.json` instead of Retrofit/OkHttp/Moshi, custom Compose vector paths (`AnchorIcons.kt`) instead of `material-icons-extended`, and `SharedPreferences` for instant settings persistence.
- **R8 & Resource Shrinking**: Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true`.
- **Reproducible Archives**: Archive tasks enforce `isPreserveFileTimestamps = false` and `isReproducibleFileOrder = true` for deterministic APK packaging.

---

## 6. Contributing & AI Agent Guidelines

For developer workflow rules, testing requirements, and release checklists, see [**`AGENTS.md`**](./AGENTS.md).
