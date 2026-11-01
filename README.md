<div align="center">
  <img src="docs/assets/anchor-logo.svg" alt="Anchor Logo" width="108" height="108" />

  # Anchor

  <p><strong>Rock-solid time for serious play</strong></p>

  [![Website & Download](https://img.shields.io/badge/Website-Download%20App-00E5FF.svg)](https://damiongans.github.io/Anchor/)
  [![Latest Release](https://github.com/DamionGans/Anchor/actions/workflows/release.yml/badge.svg)](https://github.com/DamionGans/Anchor/releases/latest)
  [![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84.svg?logo=android&logoColor=white)](https://www.android.com/)
  [![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)
</div>

---

**Anchor** is a fast, distraction-free metronome built for musicians, drummers, teachers, and ensembles.

It puts every essential control on a single, uncluttered screen—giving you unwavering tempo, a crisp first beat that never gets cut off, and flexible visual and vibration cues without ads, pop-ups, or subscriptions. Built on pure Android platform APIs with zero reliance on Google Play Services and a platform-agnostic Kotlin core, Anchor runs seamlessly on both standard Google Play devices and plain AOSP phones.

👉 **[Visit the Website & Download the App](https://damiongans.github.io/Anchor/)**

---

## Why Musicians Love Anchor

* **Instant, Unclipped First Beat**: Press Play and hear Beat 1 immediately at full clarity—no swallowed first clicks while your phone speaker wakes up.
* **Everything on One Screen**: Adjust your tempo via the slider, quick steppers (`-5`, `-1`, `+1`, `+5`, `½`, `2×`), or **Tap Tempo** without digging through menus or scrolling.
* **6 Musical Click Voices**: Choose between **Tick**, **Click**, **Woodblock**, **Cowbell**, **Beep**, and **Hi-Hat**—voiced to cut cleanly through acoustic instruments or headphones.
* **Custom Accents & Subdivisions**: Tap any beat indicator to cycle between **Accented**, **Normal**, and **Muted** (great for testing your internal pulse), with per-count accents remembered across time signature changes. Supports quarter, eighth, triplet, and 16th-note subdivisions.
* **See & Feel the Tempo**: Practice on a loud stage or without headphones using the **Beat Bar**, smooth **Pendulum** animation, **Full-Screen Flash**, and distinct **Haptic Vibrations** for accents, beats, and subdivisions.
* **Plays While You Read Sheet Music**: Switch to your PDF score reader, chord chart app, or lock your phone screen—Anchor keeps your beat steady in the background with a multimedia player (**Play/Pause** and **Tempo −/+** buttons) right in your notification drawer.
* **Dial In Your Headphones**: Using wireless Bluetooth earbuds or an external speaker? Fine-tune Visual, Vibration, and Bluetooth timing offsets in `1 ms` steps so what you see, feel, and hear line up together.
* **Remembers Your Setup**: Your tempo, time signature, sound choice, accent pattern, calibration offsets, and theme (Light, Dark, or Android Material You colors) are saved automatically.
* **100% Free, Private & AOSP-Ready**: ~2 MB download, works completely offline with zero Google Play Services dependencies, zero ads, and zero tracking.

---

## Download & Installation

1. Visit the **[Anchor Website](https://damiongans.github.io/Anchor/)** or the **[GitHub Releases page](https://github.com/DamionGans/Anchor/releases/latest)** on your Android device (Android 7.0+).
2. Download the latest `Anchor-v1.3.0.apk` file and open it to install.
3. Future updates can be checked and installed directly from **Settings → About**.

---

## Under the Hood (Technical Documentation)

Curious how Anchor achieves sample-accurate timing, prevents Android audio hardware from clipping Beat 1, synchronizes visuals and haptics to the hardware audio playhead, or structures its core domain for future multiplatform targets?

Detailed engineering documentation is kept separate so this README stays focused on musicians:

* 📄 **[Technical Architecture & Audio Engine Guide (`ARCHITECTURE.md`)](./ARCHITECTURE.md)** — In-depth explanation of the 44.1 kHz real-time PCM synthesis engine, 55ms sub-LSB hardware warmup, playhead-anchored `DelayCompensationEngine`, and multiplatform-ready clean architecture.
* 🌐 **[Web Technical Deep-Dive (`architecture.html`)](https://damiongans.github.io/Anchor/architecture.html)** — Visual waveform diagrams and system breakdown on the project website.
* 🤖 **[Developer & AI Agent Guidelines (`AGENTS.md`)](./AGENTS.md)** — Authoritative contributor rules, testing invariants, and release checklists for human developers and AI coding agents.
* 📋 **[Changelog (`CHANGELOG.md`)](./CHANGELOG.md)** — Full version history.

---

## License

Released under the [Apache 2.0 License](LICENSE).
