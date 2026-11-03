#!/usr/bin/env bash
# Creates the Google Play *upload* key for Anchor and stores it as GitHub Actions secrets.
#
# Play App Signing keeps the real app signing key at Google; this upload key only proves that
# a bundle comes from you. Never reuse signing/debug.keystore.base64 (its private key is public).
#
# Usage (run it yourself; keytool and gh prompt for the passwords, nothing is echoed or logged):
#   scripts/create-play-upload-key.sh [output-dir]
#
# Keep the generated .jks file and both passwords in your password manager. If you lose the
# upload key, Google can reset it, but that takes a support request and several days.
set -euo pipefail

OUT_DIR="${1:-$HOME/anchor-play-upload-key}"
KEYSTORE="$OUT_DIR/anchor-play-upload.jks"
ALIAS="upload" # must match signingConfigs.release.keyAlias in androidApp/build.gradle.kts

command -v keytool >/dev/null || { echo "keytool not found (install a JDK)"; exit 1; }

if [ -e "$KEYSTORE" ]; then
  echo "Refusing to overwrite $KEYSTORE"
  exit 1
fi
mkdir -p "$OUT_DIR"

echo "Creating $KEYSTORE (alias '$ALIAS'). keytool will ask for a store password, your name and"
echo "organisation details, and a key password (you may press Enter to reuse the store password)."
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 \
  -validity 10000

echo
echo "Upload certificate (register nothing yet; Play shows this fingerprint after the first upload):"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" | grep -E "Owner:|SHA256:" || true

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
  echo
  read -r -p "Store the key as GitHub Actions secrets for DamionGans/Anchor now? [y/N] " answer
  if [ "${answer:-N}" = "y" ] || [ "${answer:-N}" = "Y" ]; then
    base64 < "$KEYSTORE" | tr -d '\r\n' | gh secret set PLAY_UPLOAD_KEYSTORE_BASE64 --repo DamionGans/Anchor
    echo "Enter the STORE password for the secret PLAY_UPLOAD_STORE_PASSWORD:"
    gh secret set PLAY_UPLOAD_STORE_PASSWORD --repo DamionGans/Anchor
    echo "Enter the KEY password for the secret PLAY_UPLOAD_KEY_PASSWORD:"
    gh secret set PLAY_UPLOAD_KEY_PASSWORD --repo DamionGans/Anchor
    echo "Secrets stored. The next Android workflow run builds a Play-ready bundle (artifact 'play-bundle')."
    exit 0
  fi
fi

cat <<EOF

Next, add these repository secrets (GitHub > Settings > Secrets and variables > Actions):
  PLAY_UPLOAD_KEYSTORE_BASE64  = output of: base64 < "$KEYSTORE" | tr -d '\r\n'
  PLAY_UPLOAD_STORE_PASSWORD   = the store password you just chose
  PLAY_UPLOAD_KEY_PASSWORD     = the key password you just chose
EOF
