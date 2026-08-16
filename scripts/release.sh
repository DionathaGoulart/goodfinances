#!/usr/bin/env bash
# Publishes a new version to GitHub Releases (Linux/WSL).
#
# The installed app finds the release on its own (checked once a day) and offers
# a one-tap update — but only if the release honours the AtualizacaoManager
# contract. This script exists so no item of that contract is ever missed; see
# docs/release.md for the reasoning behind each one.
#
# Usage:
#   1. Bump versionCode (+1) and versionName in app/build.gradle.kts
#   2. Write the version's section in CHANGELOG.md (the release body comes from it)
#   3. Commit, then:  ./scripts/release.sh 1.3.0
#
# Requires: an authenticated gh CLI (gh auth login), JDK 17, the Android SDK,
# and key.properties + finapp-release.jks in the repository root.

set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
    echo "Usage: ./scripts/release.sh <version>   (e.g. ./scripts/release.sh 1.3.0)" >&2
    exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

: "${JAVA_HOME:=$HOME/androidtools/jdk17}"
: "${ANDROID_HOME:=$HOME/androidtools/sdk}"
export JAVA_HOME ANDROID_HOME
PATH="$PATH:$HOME/androidtools/gh/bin"

fail() { echo "ERROR: $*" >&2; exit 1; }

# ---------- Checks that stop a release nobody would receive ----------

# AtualizacaoManager compares X.Y.Z only: a suffix such as "-beta" ties with the
# stable version, so anyone on the beta would never be told about it.
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
    || fail "version must be X.Y.Z with no suffix (got '$VERSION')."

grep -q "versionName = \"$VERSION\"" app/build.gradle.kts \
    || fail "versionName in app/build.gradle.kts is not $VERSION."

CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
PREVIOUS=$(git show HEAD~1:app/build.gradle.kts 2>/dev/null | grep -oP 'versionCode = \K[0-9]+' || echo 0)
[[ "$CODE" -gt "$PREVIOUS" ]] \
    || echo "WARNING: versionCode ($CODE) did not increase since the previous commit ($PREVIOUS) — Android refuses to install over an equal or lower one."

[[ -f key.properties && -f finapp-release.jks ]] \
    || fail "key.properties/finapp-release.jks missing — without them the APK is unsigned and will not install over the existing app."

[[ -z "$(git status --porcelain)" ]] \
    || fail "working tree is dirty — commit everything before publishing."

git rev-parse "v$VERSION" >/dev/null 2>&1 \
    && fail "tag v$VERSION already exists."

command -v gh >/dev/null || fail "gh CLI not found."
gh auth status >/dev/null 2>&1 || fail "gh is not authenticated — run 'gh auth login'."

# ---------- Release notes: this version's section in the CHANGELOG ----------

# Keep a Changelog heading: "## [1.3.0] - 2026-08-16". Stops at the next
# version heading or a horizontal rule.
NOTES=$(awk -v v="## [$VERSION]" '
    index($0, v) == 1 {found=1; next}
    found && (/^## / || /^---[[:space:]]*$/) {exit}
    found {print}
' CHANGELOG.md)
[[ -n "${NOTES// /}" ]] || fail "CHANGELOG.md has no '## [$VERSION]' section."

# ---------- Build ----------

echo "==> Tests and release APK..."
./gradlew testDebugUnitTest assembleRelease --console=plain

APK="GoodFinances-$VERSION.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"

# The app picks the first asset ending in .apk; without one, the dialog can only
# open the release page in a browser.
[[ "$APK" == *.apk ]] || fail "the asset must end in .apk."

# ---------- Publish ----------

echo "==> Tag and push..."
git push origin main
git tag "v$VERSION"
git push origin "v$VERSION"

echo "==> Creating the release..."
# --latest, and the absence of --prerelease/--draft, are mandatory: the app
# queries /releases/latest, which ignores drafts and pre-releases.
gh release create "v$VERSION" "$APK" \
    --title "GoodFinances $VERSION" \
    --notes "$NOTES" \
    --latest

rm -f "$APK"

echo
echo "Release v$VERSION published. Installed apps will prompt when Home opens,"
echo "the first time more than 24h after their last check."
