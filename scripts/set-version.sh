#!/usr/bin/env bash
# Sets the app version in every mechanical location (AGENTS.md §9).
# Usage: scripts/set-version.sh X.Y.Z
# Changelog text (CHANGELOG.md, ChangelogDialog.kt, website changelog/highlights, fastlane note)
# is written by hand afterwards; this script never edits release history.
set -euo pipefail

NEW="${1:-}"
if ! [[ "$NEW" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "usage: $0 MAJOR.MINOR.PATCH" >&2
  exit 2
fi
NEW_CODE=$(( BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3] ))

cd "$(dirname "$0")/.."

OLD=$(sed -nE 's/^ *\?: "([0-9]+\.[0-9]+\.[0-9]+)"$/\1/p' androidApp/build.gradle.kts | head -n 1)
OLD_CODE=$(sed -nE 's/^ *\?: ([0-9]+)$/\1/p' androidApp/build.gradle.kts | head -n 1)
if [ -z "$OLD" ] || [ -z "$OLD_CODE" ]; then
  echo "error: could not read the current version from androidApp/build.gradle.kts" >&2
  exit 1
fi
if [ "$OLD" = "$NEW" ]; then
  echo "already at $NEW ($NEW_CODE); nothing to do"
  exit 0
fi

OLD_RE=${OLD//./\\.}
TODAY=$(date +%Y-%m-%d)
missing=0

# replace FILE SED_EXPR... : applies the expressions; fails if FILE does not contain OLD.
replace() {
  local file=$1; shift
  if ! grep -q "$OLD_RE" "$file"; then
    echo "error: $file does not contain $OLD" >&2
    missing=1
    return
  fi
  local args=()
  for expr in "$@"; do args+=(-e "$expr"); done
  sed -i.bak -E "${args[@]}" "$file" && rm -f "$file.bak"
  echo "updated $file"
}

replace androidApp/build.gradle.kts "s/\\?: \"$OLD_RE\"/?: \"$NEW\"/" "s/\\?: $OLD_CODE\$/?: $NEW_CODE/"
replace iosApp/project.yml "s/MARKETING_VERSION: \"$OLD_RE\"/MARKETING_VERSION: \"$NEW\"/" \
  "s/CURRENT_PROJECT_VERSION: \"$OLD_CODE\"/CURRENT_PROJECT_VERSION: \"$NEW_CODE\"/"
replace desktopApp/build.gradle.kts "s/\\?: \"$OLD_RE\"/?: \"$NEW\"/"
replace desktopApp/src/main/kotlin/com/damiongans/anchor/desktop/Main.kt "s/DEV_VERSION = \"$OLD_RE\"/DEV_VERSION = \"$NEW\"/"
replace androidApp/src/main/kotlin/com/damiongans/anchor/viewmodel/MetronomeViewModel.kt "s/ifBlank \\{ \"$OLD_RE\" \\}/ifBlank { \"$NEW\" }/"
replace shared/src/iosMain/kotlin/com/damiongans/anchor/ios/AnchorIosApp.kt "s/\\?: \"$OLD_RE\"/?: \"$NEW\"/"
replace .github/workflows/ios.yml "s/VERSION_NAME:-$OLD_RE/VERSION_NAME:-$NEW/"
replace .github/workflows/desktop.yml "s/CHANGELOG_VER:-$OLD_RE/CHANGELOG_VER:-$NEW/"
replace androidApp/src/test/kotlin/com/damiongans/anchor/AppUpdateManagerTest.kt "s/$OLD_RE/$NEW/g"
replace website/version.json "s/$OLD_RE/$NEW/g" "s/\"versionCode\": $OLD_CODE/\"versionCode\": $NEW_CODE/" \
  "s/\"releaseDate\": \"[0-9-]+\"/\"releaseDate\": \"$TODAY\"/"
replace website/app.js "s/$OLD_RE/$NEW/g"
# index.html: links, badges, buttons, QR caption; never the changelog list (release-badge / Release comments).
replace website/index.html "/release-badge|<!-- Release v/!s/v$OLD_RE/v$NEW/g"
replace README.md "s/Anchor-v$OLD_RE/Anchor-v$NEW/g"

FASTLANE="fastlane/metadata/android/en-US/changelogs/$NEW_CODE.txt"
[ -f "$FASTLANE" ] || { : > "$FASTLANE"; echo "created $FASTLANE"; }

if [ "$missing" -ne 0 ]; then
  echo "some files did not contain $OLD; fix them by hand" >&2
  exit 1
fi

cat <<EOF

Version $OLD ($OLD_CODE) -> $NEW ($NEW_CODE). Now write the release text:
  - CHANGELOG.md: top '## [v$NEW]' section
  - sharedUi/src/commonMain/kotlin/com/damiongans/anchor/ui/dialogs/ChangelogDialog.kt: CURRENT_RELEASE_CHANGELOG
  - website/index.html: new changelog entry (Latest / Previous / Earlier labels)
  - website/version.json: highlights
  - $FASTLANE
EOF
