# Windows Support Roadmap (x64 + arm64)

Anchor is **prepared** for a Windows app but does not ship one yet. This document records the chosen architecture, what already exists, and the remaining steps.

## Why the JVM (not Kotlin/Native)

Kotlin/Native can target Windows x64 (`mingwX64`) but has **no Windows-on-ARM64 target**. The JVM runs natively on both Windows x64 and Windows arm64 (Temurin ships both JDKs), and **Compose Multiplatform Desktop**, the UI toolkit chosen for the Windows app, runs on the JVM. So Windows support is a JVM `desktop` target of the shared module. Do not add `mingwX64`.

## What exists today

| Piece | Location | Notes |
|---|---|---|
| JVM desktop target | `shared/build.gradle.kts` (`jvm("desktop")`, JVM 17) | All `commonMain` logic (sequencer, delay compensation, controller, settings rules, release versioning) compiles and is tested on it. |
| Audio | `shared/src/desktopMain/.../audio/DesktopAudioEngine.kt` | `javax.sound.sampled.SourceDataLine` (48 kHz, 16-bit mono) driven by the shared `MetronomeSequencer`, playhead from `longFramePosition` smoothed by `PlayheadEstimator`. Output mixers are listed and selectable; "System Default Output" is first. |
| Settings | `.../repository/PreferencesKeyValueStore.kt` | `java.util.prefs` (Windows registry, `HKCU\Software\JavaSoft\Prefs`) behind the shared `KeyValueSettingsRepository`. |
| Updates | `.../update/DesktopUpdateRepository.kt` | `java.net.http` + `kotlinx-serialization-json` (desktop only). Picks the installer for this machine's architecture. |
| Architecture | `.../platform/WindowsArch.kt` | `os.arch` → `X64` / `ARM64`, asset markers `windows-x64` / `windows-arm64`. |
| Composition root | `.../desktop/AnchorDesktop.kt` | `AnchorDesktop.createController(scope, versionName)` returns a fully wired `MetronomeController`. |
| CI | `.github/workflows/desktop.yml` | Runs `:shared:desktopTest` on `windows-latest` (x64) and `windows-11-arm` (arm64). |

Haptics use `NoOpHapticsManager` (desktop has no vibration motor). System media controls use `NoOpPlaybackSession` for now.

## Remaining steps to ship a Windows app

1. **App module `desktopApp/`** (name reserved): Compose Multiplatform Desktop with the JetBrains Compose Gradle plugin, an `application { mainClass = ... }` entry point, a Swing main `CoroutineScope` (`kotlinx-coroutines-swing`), and `AnchorDesktop.createController(...)`.
2. **Share the UI**: move the Compose UI from `androidApp/src/main/kotlin/.../ui/` into a shared UI module (Compose Multiplatform, `commonMain`) used by Android and desktop. Android-only APIs (Material You dynamic color, `Context`) stay behind small platform hooks.
3. **Packaging**: Compose `nativeDistributions` (jpackage + jlink bundles a trimmed JRE). jpackage cannot cross-compile, so build **one MSI per architecture on a matching runner**: `windows-latest` → `Anchor-vX.Y.Z-windows-x64.msi`, `windows-11-arm` (Temurin aarch64) → `Anchor-vX.Y.Z-windows-arm64.msi`. These names are what `DesktopUpdateRepository` looks for.
4. **Code signing**: sign both MSIs (Authenticode / Azure Trusted Signing) to avoid SmartScreen warnings.
5. **Media keys**: implement an `IPlaybackSession` on Windows System Media Transport Controls (SMTC) through native interop (JNA / a small helper).
6. **Microsoft Store (optional)**: MSIX packaging plus a `MICROSOFT_STORE` `DistributionChannel` (no self-updates, like the other stores).
7. **Release CI**: extend `desktop.yml` to build, sign, and attach both MSIs to GitHub Releases on `main` / `v*`.

## Audio latency

The Windows `javax.sound` mixer (DirectSound) has higher output latency than Android's AAudio fast path. Visual and haptic timing is anchored to the hardware playhead, so the delay is compensated rather than adding up. If lower latency is needed, a WASAPI-exclusive engine can implement `IAudioEngine` on top of the same `BaseMetronomeAudioEngine` / `MetronomeSequencer`.
