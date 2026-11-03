#!/usr/bin/env bash
# Creates the private signing key for the GitHub-release APK and the key-rotation lineage that
# lets existing installs (signed with the public debug key) update in place.
#
# Why: GitHub APKs used to be signed with signing/debug.keystore.base64, whose private key is
# public, so anyone could sign a lookalike "update". With APK Signature Scheme v3 key rotation,
# the old key vouches for the new one once; Android 9+ devices then update in place, keep their
# data, and stop accepting APKs signed only with the old key. See docs/release-signing.md.
#
# Usage (run it yourself; keytool, apksigner and gh prompt for passwords, nothing is logged):
#   scripts/create-github-release-key.sh [output-dir]
# Then commit signing/release-rotation.lineage (public data: certificates and signatures).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:-$HOME/anchor-github-release-key}"
KEYSTORE="$OUT_DIR/anchor-github-release.jks"
ALIAS="release"
LINEAGE="$REPO_ROOT/signing/release-rotation.lineage"

command -v keytool >/dev/null || { echo "keytool not found (install a JDK)"; exit 1; }
APKSIGNER="$(command -v apksigner || true)"
if [ -z "$APKSIGNER" ]; then
  # ANDROID_HOME, then the default SDK folders on Linux/macOS and Windows (Git Bash).
  for SDK in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"     "${LOCALAPPDATA:+$(cygpath -u "$LOCALAPPDATA" 2>/dev/null)/Android/Sdk}"; do
    [ -n "$SDK" ] || continue
    APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner* 2>/dev/null | sort -V | tail -n1 || true)"
    [ -n "$APKSIGNER" ] && break
  done
fi
[ -n "$APKSIGNER" ] || { echo "apksigner not found (install Android build-tools or set ANDROID_HOME)"; exit 1; }

[ -e "$KEYSTORE" ] && { echo "Refusing to overwrite $KEYSTORE"; exit 1; }
[ -e "$LINEAGE" ] && { echo "Refusing to overwrite $LINEAGE (rotation is a one-time step)"; exit 1; }
mkdir -p "$OUT_DIR"

echo "1/3 Creating $KEYSTORE (alias '$ALIAS'). keytool asks for passwords and your details."
keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000

echo
echo "2/3 Creating the rotation lineage (old debug key -> new release key)."
OLD_KEYSTORE="$(mktemp)"
trap 'rm -f "$OLD_KEYSTORE"' EXIT
tr -d '\r\n' < "$REPO_ROOT/signing/debug.keystore.base64" | base64 -d > "$OLD_KEYSTORE"
# The old key's password is the public debug password; apksigner asks for the new key's password.
# --set-rollback false: after a device has the rotated version, it rejects APKs signed only
# with the old (public) key.
"$APKSIGNER" rotate --out "$LINEAGE" \
  --old-signer --ks "$OLD_KEYSTORE" --ks-key-alias androiddebugkey --ks-pass pass:android --set-rollback false \
  --new-signer --ks "$KEYSTORE" --ks-key-alias "$ALIAS"
echo "Lineage written to $LINEAGE — commit it."

echo
echo "New release certificate (publish this fingerprint so users can verify downloads):"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" | grep -E "Owner:|SHA256:" || true

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
  echo
  read -r -p "3/3 Store the key as GitHub Actions secrets for DamionGans/Anchor now? [y/N] " answer
  if [ "${answer:-N}" = "y" ] || [ "${answer:-N}" = "Y" ]; then
    base64 < "$KEYSTORE" | tr -d '\r\n' | gh secret set GITHUB_RELEASE_KEYSTORE_BASE64 --repo DamionGans/Anchor
    echo "Enter the STORE password for the secret GITHUB_RELEASE_STORE_PASSWORD:"
    gh secret set GITHUB_RELEASE_STORE_PASSWORD --repo DamionGans/Anchor
    echo "Enter the KEY password for the secret GITHUB_RELEASE_KEY_PASSWORD:"
    gh secret set GITHUB_RELEASE_KEY_PASSWORD --repo DamionGans/Anchor
    echo "Done. Commit signing/release-rotation.lineage; the next release APK uses the new key."
    exit 0
  fi
fi

cat <<EOF

3/3 Add these repository secrets (GitHub > Settings > Secrets and variables > Actions):
  GITHUB_RELEASE_KEYSTORE_BASE64 = output of: base64 < "$KEYSTORE" | tr -d '\r\n'
  GITHUB_RELEASE_STORE_PASSWORD  = the store password you just chose
  GITHUB_RELEASE_KEY_PASSWORD    = the key password you just chose
Then commit signing/release-rotation.lineage.
EOF
