# Windows App (x64 + arm64)

Anchor runs natively on Windows PCs with Intel/AMD (**x64**) and ARM (**arm64**, e.g. Snapdragon) processors. It is the same metronome as on Android: same timing engine, same sounds, same single-screen UI.

## Install

1. Open the [latest GitHub Release](https://github.com/DamionGans/Anchor/releases/latest).
2. Download the installer for your PC:
   - `Anchor-vX.Y.Z-windows-x64.msi` for Intel/AMD PCs (most PCs)
   - `Anchor-vX.Y.Z-windows-arm64.msi` for ARM PCs (Windows on Snapdragon, Copilot+ PCs)

   Not sure? *Settings → System → About → System type* says "x64-based processor" or "ARM-based processor".
3. Run the MSI. It installs for your user account only (no administrator rights needed) and adds Anchor to the Start menu and desktop.

The installers are not code-signed yet, so Windows SmartScreen may show "Windows protected your PC". Choose **More info → Run anyway**. Each release lists SHA-256 checksums (`.msi.sha256`) to verify the download.

**Updates**: Anchor checks GitHub for new versions and offers the installer for your architecture. Installing a newer MSI upgrades the existing installation in place, and your settings are kept.

## Keyboard shortcuts

| Key | Action |
|---|---|
| Space | Play / pause |
| Up / Down (or + / −) | Tempo +1 / −1 BPM |
| Shift + Up / Down | Tempo +5 / −5 BPM |
| T | Tap tempo |
| PageUp / PageDown (or `[` / `]`) | Previous / next song in active playlist |

## Architecture

| Piece | Location | Notes |
|---|---|---|
| App host | `desktopApp/` | Compose Multiplatform Desktop: window, icon, keyboard shortcuts, MSI packaging. |
| UI | `sharedUi/` | The **same Compose UI as Android** (Compose Multiplatform). Platform differences live in `ui/platform/PlatformUi` (no Material You colors and no vibration settings on Windows). |
| Logic | `shared/src/commonMain` | `MetronomeController`, sequencer, delay compensation, settings rules, release versioning: identical on every platform. |
| Audio | `shared/src/desktopMain/.../audio/DesktopAudioEngine.kt` | `javax.sound.sampled.SourceDataLine` (48 kHz, 16-bit mono) on the shared `MetronomeSequencer`; playhead from `longFramePosition` via `PlayheadEstimator`. **Output standby** keeps the line open with inaudible dither while the app is open, so Beat 1 starts in the next ~5 ms chunk. Output devices are listed and selectable ("System Default Output" first). |
| Settings | `.../repository/PreferencesKeyValueStore.kt` | `java.util.prefs` (registry, `HKCU\Software\JavaSoft\Prefs`) behind the shared `KeyValueSettingsRepository`. |
| Updates | `.../update/DesktopUpdateRepository.kt` | `java.net.http` + `kotlinx-serialization-json` (desktop only); picks the MSI matching `WindowsArch`. |
| Composition root | `.../desktop/AnchorDesktop.kt` | Wires the desktop implementations into `MetronomeController`. |

### Why the JVM (not Kotlin/Native)
Kotlin/Native can target Windows x64 (`mingwX64`) but has **no Windows-on-ARM64 target**. The JVM runs natively on both (Temurin ships x64 and aarch64 JDKs), and Compose Multiplatform Desktop runs on the JVM. Each MSI bundles a jlink-trimmed Java runtime for its architecture, so users don't need Java installed.

## Build & run

```bash
./gradlew :desktopApp:run                                 # run from source (any OS with a JDK 17+)
./gradlew :shared:desktopTest                             # shared + desktop tests
./gradlew :desktopApp:packageMsi -PversionName=1.6.0      # MSI for this machine's architecture (Windows only)
```

jpackage cannot cross-build: an x64 machine produces the x64 MSI and an ARM machine the arm64 MSI. Output: `desktopApp/build/compose/binaries/main/msi/`.

## CI & releases

`.github/workflows/desktop.yml` runs on `windows-latest` (x64) and `windows-11-arm` (arm64): shared/desktop tests, then `packageMsi`, staged as `Anchor-vX.Y.Z-windows-{x64,arm64}.msi` + `.sha256` workflow artifacts. On `main` and `v*` tags, a release job waits for the GitHub Release created by `android.yml` for the same version and attaches both MSIs.

## Not yet done

- **Code signing** (Authenticode / Azure Trusted Signing) to remove the SmartScreen warning.
- **Media keys**: Windows System Media Transport Controls (SMTC) need native interop; the desktop app uses `NoOpPlaybackSession` for now.
- **Microsoft Store** (MSIX + a `MICROSOFT_STORE` `DistributionChannel` without self-updates).
- **Lower latency**: a WASAPI-exclusive engine could implement `IAudioEngine` on the same `BaseMetronomeAudioEngine` / `MetronomeSequencer`. Visual and haptic timing is already anchored to the hardware playhead, so output latency is compensated rather than added.
