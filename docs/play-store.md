# Google Play launch guide

Everything needed to publish **Anchor Metronome** (`com.damiongans.anchor`, flavor `play`) on Google Play as a one-time paid app. Steps marked **Owner** need the developer account holder (account, payments, identity, passwords); everything else is prepared in this repository.

Facts checked on 2026-11-02 against Google's documentation; re-check anything marked *verify* in Play Console before relying on it.

## 1. What the `play` build is

| Item | Value |
|---|---|
| Package | `com.damiongans.anchor` |
| Format | Android App Bundle (`bundlePlayRelease`), ~3.9 MB AAB, ~1.6 MB installed APK |
| Target / min SDK | 36 / 24 — meets the rule that new apps target API 36 from 31 Aug 2026 |
| Network | none: the `play` flavor removes `INTERNET` (CI fails if it comes back) |
| Updates | by Google Play only; the in-app GitHub update check is disabled |
| Permissions | `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` |

## 2. Signing (upload key)

Play App Signing keeps the real app signing key at Google. You sign bundles with a separate **upload key**.

1. **Owner:** run `scripts/create-play-upload-key.sh`. It creates `anchor-play-upload.jks` (alias `upload`); `keytool` asks for the passwords, and the script can store the key as the GitHub secrets `PLAY_UPLOAD_KEYSTORE_BASE64`, `PLAY_UPLOAD_STORE_PASSWORD`, `PLAY_UPLOAD_KEY_PASSWORD`. Keep the file and passwords in a password manager.
2. The Android workflow then builds a signed, Play-ready bundle on every run and uploads it as the `play-bundle` artifact. It refuses to produce a bundle signed with the debug key; without the secrets it skips the bundle with a notice.
3. Never use `signing/debug.keystore.base64` for Play: its private key is public.

## 3. Play Console setup (Owner)

1. Create a developer account (one-time US$25), complete identity verification. Personal accounts created after 13 Nov 2023 must pass a closed test before production (section 5); organization accounts with a D-U-N-S number are exempt.
2. Set up a **payments profile** (merchant account) — required for paid apps.
3. Create the app: name *Anchor Metronome*, default language English (US), **App**, **Paid**. Free→paid is impossible later; paid→free is allowed.
4. Enroll in Play App Signing (default) when uploading the first bundle.

## 4. App content answers (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | `https://damiongans.github.io/Anchor/privacy.html` |
| Ads | No, the app has no ads |
| App access | All functionality is available without restrictions (no login) |
| Data safety | **No** user data collected; **no** data shared. No data is transmitted at all (no internet permission). Account creation: none; data deletion: not applicable |
| Content rating (IARC) | Category: *All other app types*. Violence, sexuality, language, controlled substances, gambling, horror: **No**. Users interact or share content: **No**. Shares location: **No**. Digital purchases inside the app: **No**. Expected result: Everyone / PEGI 3 |
| Target audience | Owner decides. *13 and over* keeps review simple. Including under-13s (music students) brings the app under the Families policy; Anchor already meets it (no ads, no data, no third-party SDKs) but review is stricter |
| Government / financial / health / news app | No |
| Foreground service permissions | Type **Media playback**. Use: "Plays the metronome click continuously while the app is in the background or the screen is locked (for example while reading sheet music). The user starts and stops it with the play button or the media notification; it stops when playback stops." Play may ask for a short screen recording showing this (*verify*) |
| `POST_NOTIFICATIONS` | No declaration; used for the playback notification with play/pause and tempo buttons |

Store settings: category **Music & Audio**, contact email (Owner), website `https://damiongans.github.io/Anchor/`.

## 5. Closed test → production (personal accounts)

Requirement: **at least 12 testers opted in continuously for the 14 days before** you apply for production. Google usually reviews the application within about a week.

**Paid-app catch:** in closed and open tests of a paid app, testers must **buy** the app; only internal tests are free, and internal tests do not count toward the requirement. Options (Owner decides):

1. Add testers to **License testing** (Settings → License testing) and check whether they can install the paid build with Google's test card instead of a real charge (*verify* — Google documents this for in-app purchases, not clearly for the paid app itself).
2. Ask testers (band mates, students, friends) to buy it and reimburse them.
3. Register as an organization (D-U-N-S) to skip the closed test.

Do **not** run the closed test as a free app intending to switch to paid: Play does not allow free→paid.

Steps: Testing → Closed testing → create track → upload the `play-bundle` AAB → add testers (email list or Google Group) → share the opt-in link → keep ≥12 opted in for 14 days → Dashboard → *Apply for production*.

## 6. Store listing

Text and graphics live in `fastlane/metadata/android/en-US/` (title, short and full description, icon 512×512, feature graphic 1024×500, phone screenshots). Copy rules: musician-first, no claims the app cannot back, never call the store version free.

## 7. Releasing updates

1. Bump with `scripts/set-version.sh X.Y.Z` (versionCode = MAJOR·10000 + MINOR·100 + PATCH); the Play changelog is `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤500 chars).
2. The first bundle is uploaded by hand in Play Console. Afterwards, create a Play Console API service account with release rights and store its JSON as `PLAY_SERVICE_ACCOUNT_JSON`; `v*` tags then upload to the **internal** track as drafts (`publish-play` job), which you promote in the Console.
3. Never upload to a store unless the owner asks (AGENTS.md).
