# GitHub Copilot & AI Agent Instructions

Please read and strictly follow **[`AGENTS.md`](../AGENTS.md)** at the root of this repository before generating or modifying any code.

Key reminders:
- **Living Document**: Any change to project architecture, timing/audio behavior, build setup, or standing user instructions must be reflected in [`AGENTS.md`](../AGENTS.md) in the same change.
- **Audio & Sync**: Visuals, screen flash, pendulum, and vibration are scheduled via `DelayCompensationEngine` anchored to `AudioTrack.getPlaybackHeadPosition()` (`playheadFrame`), never `writeFrame`, with a 55ms sub-LSB dither hardware warmup before Beat 1.
- **ViewModel DI**: Keep `@JvmOverloads constructor` on `MetronomeViewModel` so `AndroidViewModelFactory` reflection can instantiate `(Application)`.
- **Releases & Changelog**: Keep version `X.Y.Z` synchronized across `app/build.gradle.kts`, `CHANGELOG.md` (neutral tone), `ChangelogDialog.kt`, `docs/version.json`, `docs/index.html`, and `AppUpdateManagerTest.kt`.
