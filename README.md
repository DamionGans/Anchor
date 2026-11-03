<div align="center">
  <img src="website/assets/anchor-logo.svg" alt="Anchor Metronome Logo" width="108" height="108" />

  # Anchor Metronome

  <p><strong>Rock-solid time for serious play</strong></p>

  [![Website & Download](https://img.shields.io/badge/Website-Download%20App-00E5FF.svg)](https://damiongans.github.io/Anchor/)
  [![Latest Release](https://github.com/DamionGans/Anchor/actions/workflows/android.yml/badge.svg)](https://github.com/DamionGans/Anchor/releases/latest)
  [![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84.svg?logo=android&logoColor=white)](https://www.android.com/)
  [![Platform](https://img.shields.io/badge/Platform-iPhone%20iOS%2016%2B-000000.svg?logo=apple&logoColor=white)](#iphone)
  [![Platform](https://img.shields.io/badge/Platform-Windows%20x64%20%7C%20arm64-0078D4.svg)](#windows)
  [![iOS Build](https://github.com/DamionGans/Anchor/actions/workflows/ios.yml/badge.svg)](https://github.com/DamionGans/Anchor/actions/workflows/ios.yml)
  [![Windows Build](https://github.com/DamionGans/Anchor/actions/workflows/desktop.yml/badge.svg)](https://github.com/DamionGans/Anchor/actions/workflows/desktop.yml)
  [![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)
</div>

---

**Anchor Metronome** (**Anchor**) is the metronome you can trust from the practice room to the stage. It starts exactly when you press Play, keeps every control on one screen, and has your whole setlist ready—on your phone, tablet, or computer. No ads, no subscriptions, no account.

| Platform | Status | Get it |
|---|---|---|
| **Android** 7.0+ | Available | APK from the [website](https://damiongans.github.io/Anchor/) / [GitHub Releases](https://github.com/DamionGans/Anchor/releases/latest); F-Droid and Google Play coming soon |
| **iPhone** (iOS 16+) | Available (sideload / build from source) | [Unsigned IPA from CI](#iphone); App Store coming soon |
| **Windows** 10/11 (x64 + arm64) | Available | MSI installer from [GitHub Releases](https://github.com/DamionGans/Anchor/releases/latest) ([guide](./docs/windows.md)) |

Anchor runs on any Android phone or tablet, including devices without Google services.

👉 **[Visit the Website & Download the App](https://damiongans.github.io/Anchor/)**

---

## Why Musicians Love Anchor

* **A count-in you can trust**: Press Play and Beat 1 sounds right away, loud and clear—never swallowed while the speaker wakes up.
* **Your whole set, ready**: Save each song's tempo, time signature, and accents, build setlists, and step to the next song with one tap on the main screen.
* **Everything on one screen**: Tempo slider, quick steps (`-5`, `-1`, `+1`, `+5`, `½`, `2×`), and **Tap Tempo**—no menus or scrolling mid-song.
* **See it and feel it**: Beat bar, swinging pendulum, full-screen flash, and vibration for accents, beats, and subdivisions—made for loud rooms, drummers, and silent practice.
* **In sync with your earbuds**: Using Bluetooth earbuds or speakers? Nudge the visual, vibration, and Bluetooth timing in 1 ms steps until what you see, feel, and hear line up.
* **Your groove, your accents**: Tap any beat to make it **Accent**, **Normal**, **Soft**, or **Mute**. Quarter, eighth, triplet, and 16th-note subdivisions, odd meters included, and Anchor remembers your accent pattern for every meter.
* **Five click sounds**: **Woodblock**, **Mechanical**, **Digital Beep**, **Rimshot**, and **808 Percussion**, with adjustable pitch, voiced to cut through acoustic instruments and in-ear monitors.
* **Keeps going while you read**: Switch to your sheet-music or chord-chart app or lock the screen—the beat keeps going, with play/pause and tempo controls in the Android notification or on the iPhone lock screen.
* **Remembers your setup**: Tempo, meter, sound, accents, timing, and theme (Light, Dark, or Android Material You colors) are saved automatically.
* **Free, open source, private**: The full app is free on GitHub (and F-Droid). Paid Google Play and App Store versions—identical features, one-time price, automatic store updates—are coming soon as a way to support development. Every version has no ads, no tracking, no account, and works offline.

---

## Download & Installation

### Android

1. Visit the **[Anchor Website](https://damiongans.github.io/Anchor/)** or the **[GitHub Releases page](https://github.com/DamionGans/Anchor/releases/latest)** on your Android device (Android 7.0+).
2. Download the latest `Anchor-v1.7.1.apk` file and open it to install.
3. Future updates can be checked and installed directly from **Settings → About**.

### iPhone

Anchor for iPhone (iOS 16+) is built automatically by the **[Build iOS App](https://github.com/DamionGans/Anchor/actions/workflows/ios.yml)** workflow:

* Download the `Anchor-vX.Y.Z-unsigned.ipa` artifact from the latest successful run and install it with your own signing certificate or a sideloading tool, or
* Build it yourself on a Mac with Xcode: run `brew install xcodegen`, then `cd iosApp && xcodegen generate`, open `Anchor.xcodeproj`, choose your team, and press Run.

### Windows

1. Open the **[latest GitHub Release](https://github.com/DamionGans/Anchor/releases/latest)** and download `Anchor-v1.7.1-windows-x64.msi` (Intel/AMD PCs) or `Anchor-v1.7.1-windows-arm64.msi` (ARM PCs).
2. Run the installer. It installs for your user account (no admin rights) and adds Anchor to the Start menu.
3. The installer isn't code-signed yet: if SmartScreen shows "Windows protected your PC", choose **More info → Run anyway**.

Same metronome and interface as on Android, plus keyboard shortcuts (Space: play/pause, Up/Down: tempo, T: tap tempo). Updates are offered inside the app. See the **[Windows guide](./docs/windows.md)**.

---

## Under the Hood (Technical Documentation)

Curious how Anchor achieves sample-accurate timing, prevents Android audio hardware from clipping Beat 1, synchronizes visuals and haptics to the hardware audio playhead, or shares one Kotlin Multiplatform core between Android, iPhone, and Windows?

Detailed engineering documentation is kept separate so this README stays focused on musicians:

* 📄 **[Technical Architecture & Audio Engine Guide (`docs/architecture.md`)](./docs/architecture.md)** — In-depth explanation of the 44.1 kHz real-time PCM synthesis engine, 55ms sub-LSB hardware warmup, playhead-anchored `DelayCompensationEngine`, and the shared Kotlin Multiplatform architecture behind the Android, iPhone, and Windows apps.
* 🌐 **[Web Technical Deep-Dive (`architecture.html`)](https://damiongans.github.io/Anchor/architecture.html)** — Visual waveform diagrams and system breakdown on the project website.
* 🧭 **[Distribution & Store Release Guide (`docs/distribution.md`)](./docs/distribution.md)** — GitHub, F-Droid, Google Play, and App Store channels and the release process.
* 🪟 **[Windows Guide (`docs/windows.md`)](./docs/windows.md)** — Installing, keyboard shortcuts, architecture, and MSI packaging for Windows x64 and arm64.
* 🤖 **[Developer & AI Agent Guidelines (`AGENTS.md`)](./AGENTS.md)** — Authoritative contributor rules, testing invariants, and release checklists for human developers and AI coding agents.
* 📋 **[Changelog (`CHANGELOG.md`)](./CHANGELOG.md)** — Full version history.

---

## Contributing

Anchor is a Kotlin Multiplatform project:

```text
androidApp/   Android app
desktopApp/   Windows app (Compose Multiplatform Desktop, MSI packaging)
iosApp/       iPhone app (SwiftUI)
shared/       Shared Kotlin logic used by every platform (incl. desktopMain for Windows)
sharedUi/     Compose Multiplatform UI shared by Android and Windows
docs/         Developer documentation
website/      Project website (GitHub Pages)
```

See **[`CONTRIBUTING.md`](./CONTRIBUTING.md)** for build and test commands, and **[`SECURITY.md`](./SECURITY.md)** to report a vulnerability.

---

## License

Released under the [Apache 2.0 License](LICENSE).
