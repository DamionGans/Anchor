# GitHub APK signing: moving off the public debug key

## Problem

Until this change, the GitHub-release APK was signed with `signing/debug.keystore.base64`, whose private key is committed to this public repository. Anyone could sign a modified APK that Android accepts as an update to Anchor. (Google Play and F-Droid builds are not affected: Play uses its own upload key and Play App Signing; F-Droid signs its own builds.)

## Approach: APK Signature Scheme v3 key rotation

Android lets the old signing key vouch for a new one through a signed **lineage**. Each GitHub APK is then signed by:

- the **old** key (v2 signature) — what Android 7–8 (API 24–27) verify, and
- the **new** private key plus the lineage (v3 signature, `--rotation-min-sdk-version 28`) — what Android 9+ verify.

The lineage sets the old key's `rollback` capability to false: once a device has installed a rotated version, it rejects APKs signed only with the old (public) key.

| Users | What happens | Protected from the public key? |
|---|---|---|
| Android 9+ | Update in place, data kept | Yes, from the first rotated version on |
| Android 7–8 | Update in place, data kept (they still verify the old key) | No, until phase 3 |
| New installs | Normal install | Android 9+: yes; Android 7–8: no, until phase 3 |

Verified on an Android 16 emulator on 2026-11-02 with throwaway keys and the exact CI commands: a debug-signed install updated in place to the rotated APK (same `firstInstallTime`, so data kept), and afterwards an APK signed only with the old key failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Android 7–8 behaviour follows the v2 rules and was not tested on a device.

## Phases

1. **Prepared (in the repo):** `scripts/create-github-release-key.sh`; the `Rotate GitHub APK Signing Key` step in `android.yml` re-signs the APK once the secrets and lineage exist (until then it warns and leaves the APK unchanged), and fails if the v3 signer is still the debug key.
2. **Owner, once:** run `scripts/create-github-release-key.sh` → creates the private release key (keep it and its password in a password manager; losing it means a new rotation), writes `signing/release-rotation.lineage` (public data: certificates and signatures — commit it), and stores the secrets `GITHUB_RELEASE_KEYSTORE_BASE64`, `GITHUB_RELEASE_STORE_PASSWORD`, `GITHUB_RELEASE_KEY_PASSWORD`. Do **not** set the old `SIGNING_KEYSTORE_BASE64` secret: CI's old-key slot must stay the original debug key. Then publish the new certificate SHA-256 on the website/README so users can verify downloads.
3. **Later, optional (owner decides):** sign the v2 signature with the new key too (or raise `minSdk` to 28). Android 7–8 users then have to uninstall and reinstall once (settings and song library are lost unless exported), but nobody trusts the public key any more. Announce it one release ahead.

## Rules

- Never sign release builds with the debug key alone once phase 2 is done; never commit the new keystore or its passwords.
- Debug builds (`assemble*Debug`) keep using the debug keystore; that is expected.
- The lineage file is append-only history: never regenerate it; a future rotation extends it (`apksigner rotate --in signing/release-rotation.lineage ...`).
