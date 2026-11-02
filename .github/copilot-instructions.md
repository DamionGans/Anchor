# GitHub Copilot & AI Agent Instructions

Please read and strictly follow **[`AGENTS.md`](../AGENTS.md)** at the root of this repository before generating or modifying any code.

Key reminders:
- **Layout**: `androidApp/` (Android), `iosApp/` (iPhone), `shared/` (all shared logic), `docs/` (Markdown docs), `website/` (GitHub Pages). Kotlin sources live in `src/<sourceSet>/kotlin/`.
- **Living Document**: Any change to project architecture, timing/audio behavior, build setup, or standing user instructions must be reflected in [`AGENTS.md`](../AGENTS.md) in the same change.
- **Shared Logic**: All app logic lives in the Kotlin Multiplatform `shared/src/commonMain` module; `androidApp/` (Android) and `shared/src/iosMain` + `iosApp/` (iPhone) contain only platform-specific code and UI.
- **Audio & Sync**: Visuals, screen flash, pendulum, and vibration are scheduled via `DelayCompensationEngine` anchored to the hardware playhead (Android `AudioTrack.getPlaybackHeadPosition()`, iOS player-node sample time), never the write position, with a 55ms sub-LSB dither hardware warmup before Beat 1.
- **ViewModel DI**: Keep `@JvmOverloads constructor` on `MetronomeViewModel` so `AndroidViewModelFactory` reflection can instantiate `(Application)`.
- **Releases & Changelog**: Keep version `X.Y.Z` synchronized across `androidApp/build.gradle.kts`, `CHANGELOG.md` (neutral tone), `ChangelogDialog.kt`, `website/version.json`, `website/index.html`, `AppUpdateManagerTest.kt`, and `iosApp/project.yml`.
