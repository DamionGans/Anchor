# Distribution & Store Release Guide

Anchor is **free and open source on GitHub and F-Droid** and sold as an **upfront paid app** (one-time price, identical features) on **Google Play** and the **Apple App Store**. No build contains ads, subscriptions, in-app purchases, analytics, or accounts. The rules are canonical in [`AGENTS.md`](../AGENTS.md), section 2.

## Channels

| Channel | Build | Updates | Network access |
|---|---|---|---|
| GitHub | `androidApp` flavor `github` (APK), unsigned iOS IPA | Built-in GitHub Releases check | `INTERNET` (update check only) |
| F-Droid | flavor `fdroid`, built by F-Droid | F-Droid client | none |
| Google Play | flavor `play` (AAB) | Google Play | none |
| App Store | `ANCHOR_DISTRIBUTION=APP_STORE` archive | App Store | none |
| Windows (planned) | per-architecture MSI `Anchor-vX.Y.Z-windows-{x64,arm64}.msi` on GitHub Releases | Built-in GitHub Releases check (`DesktopUpdateRepository`) | update check only |

The Windows app is in preparation and not released yet; see [`windows.md`](./windows.md).

The channel is the shared `DistributionChannel` (`shared/src/commonMain/.../update/ReleaseVersioning.kt`). Only `GITHUB` builds self-update.

## One-time setup (owner only)

### Google Play
1. Create a Play Console developer account ($25 one-time) and the app `com.damiongans.anchor` as a **paid** app.
2. Generate a real upload key (never reuse `signing/debug.keystore.base64`):
   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
   Store it as the `SIGNING_KEYSTORE_BASE64` secret (`base64 -i upload.jks`) and enroll in Play App Signing.
3. Upload the first `.aab` manually (the `play-bundle` artifact of the Android workflow).
4. Create a Play Console API service account with release permissions and store its JSON as the `PLAY_SERVICE_ACCOUNT_JSON` secret. Later `v*` tags upload to the internal track as drafts.
5. Fill in the Data safety form ("No data collected or shared") and link the privacy policy: `https://damiongans.github.io/Anchor/privacy.html`.

### Apple App Store
1. Join the Apple Developer Program ($99/year) and create the App ID `com.damiongans.anchor` and a paid app in App Store Connect.
2. Add these secrets: `APPLE_TEAM_ID`, `APPLE_DIST_CERT_P12_BASE64`, `APPLE_DIST_CERT_PASSWORD`, `APPLE_PROVISIONING_PROFILE_BASE64`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8`.
3. Set the privacy label to "Data Not Collected" and link the privacy policy.
4. `v*` tags archive the app and upload it to App Store Connect / TestFlight.

### F-Droid
1. Open a merge request to [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) that builds the `fdroid` flavor (`gradle: [fdroid]`, subdir `androidApp`).
2. Listing text and changelogs come from `fastlane/metadata/android/en-US/`. Add `changelogs/<versionCode>.txt` for every release.

## Releasing a version
1. Follow the version sync checklist in `AGENTS.md`, section 8.
2. Merge to `main`. The Android workflow publishes the GitHub Release APK and deploys `website/`.
3. Push a `v*` tag to trigger the store uploads (each skips until its secrets exist).
