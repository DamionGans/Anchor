# Contributing to Anchor Metronome

Thanks for helping improve Anchor! Please read [`AGENTS.md`](./AGENTS.md) first. It is the canonical source for architecture rules, audio/timing invariants, UI guidelines, and the release checklist.

## Repository layout

```text
androidApp/   Android app (Jetpack Compose UI + Android platform code)
iosApp/       iPhone app (SwiftUI) + XcodeGen project.yml
shared/       Kotlin Multiplatform module with all shared app logic
docs/         Developer documentation (architecture, distribution, Windows roadmap)
website/      GitHub Pages site
fastlane/     F-Droid / store listing metadata
.github/      CI workflows, templates, Dependabot
```

The golden rule: **app logic goes in `shared/src/commonMain`**. Platform folders (`androidApp/`, `shared/src/iosMain`, `iosApp/`) contain only OS integrations and UI. See [`docs/architecture.md`](./docs/architecture.md).

## Prerequisites
- JDK 17+ and the Android SDK (API 37)
- For iOS: macOS with Xcode 16.4+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)

## Build & test

```bash
./gradlew :shared:testAndroidHostTest          # shared logic tests (any OS)
./gradlew :shared:desktopTest                  # shared + desktop (JVM/Windows) tests (any OS)
./gradlew :androidApp:testGithubDebugUnitTest  # Android unit + Robolectric tests
./gradlew :androidApp:assembleGithubDebug      # Android debug APK
./gradlew :shared:iosSimulatorArm64Test        # shared tests on the iOS simulator (macOS)
```

```bash
cd iosApp && xcodegen generate && open Anchor.xcodeproj
```

## Pull requests
- Branch from `main` and keep PRs focused on one change.
- Update `AGENTS.md` whenever behavior, architecture, build setup, or standing rules change.
- Apply user-facing changes to both the Compose and SwiftUI UIs.
- Use a neutral, factual tone in commit messages and `CHANGELOG.md`.
- CI (`android.yml`, `ios.yml`) must be green before merging.

## Product principles
No ads, subscriptions, in-app purchases, analytics, accounts, or Google Play Services. Keep the metronome a focused, single-screen instrument.
