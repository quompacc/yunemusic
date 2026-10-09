#!/usr/bin/env bash
# Baut aus signierten Release-APKs ein eigenes F-Droid-Repo (Index signiert mit dem App-Keystore).
#
# Nutzung: scripts/build-fdroid-repo.sh <apk-verzeichnis> <ausgabe-verzeichnis>
#
# Benötigt fdroidserver (pip install fdroidserver), Java (keytool/jarsigner) und
# Umgebungsvariablen: REPO_URL, KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
# Optional: ANDROID_HOME (für apksigner; sonst nutzt fdroidserver seine Fallbacks).
set -euo pipefail

APK_DIR=$(realpath "$1")
OUT=$(realpath -m "$2")
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APP_ID=io.github.quompacc.yunemusic
: "${REPO_URL:?}" "${KEYSTORE_FILE:?}" "${KEYSTORE_PASSWORD:?}" "${KEY_ALIAS:?}" "${KEY_PASSWORD:?}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/repo" "$WORK/metadata/$APP_ID"

cp "$APK_DIR"/*.apk "$WORK/repo/"
# repo_icon (Standard: icon.png) wird relativ zum Arbeitsverzeichnis gesucht
cp "$ROOT/fastlane/metadata/android/en-US/images/icon.png" "$WORK/icon.png"
# Fastlane-Texte, Screenshots und Changelogs übernimmt fdroidserver aus metadata/<appid>/<locale>/
cp -r "$ROOT/fastlane/metadata/android/." "$WORK/metadata/$APP_ID/"

cat > "$WORK/metadata/$APP_ID.yml" <<'EOF'
AntiFeatures:
  NonFreeNet:
    en-US: Depends on YouTube for all content.
Categories:
  - Multimedia
License: GPL-3.0-or-later
AuthorName: quompacc
SourceCode: https://github.com/quompacc/yunemusic
IssueTracker: https://github.com/quompacc/yunemusic/issues
Changelog: https://github.com/quompacc/yunemusic/releases
EOF

cat > "$WORK/config.yml" <<EOF
repo_url: $REPO_URL
repo_name: YuneMusic
repo_description: >-
  Official repository of YuneMusic, an ad-free music player for YouTube content.
  Source code: https://github.com/quompacc/yunemusic
archive_older: 0
keystore: $(realpath "$KEYSTORE_FILE")
repo_keyalias: $KEY_ALIAS
keystorepass: $KEYSTORE_PASSWORD
keypass: $KEY_PASSWORD
EOF
[ -n "${ANDROID_HOME:-}" ] && echo "sdk_path: $ANDROID_HOME" >> "$WORK/config.yml"
chmod 600 "$WORK/config.yml"

(cd "$WORK" && fdroid update --use-date-from-apk)

rm -rf "$OUT"
mkdir -p "$OUT"
cp -r "$WORK/repo" "$OUT/"
echo "F-Droid-Repo erstellt: $OUT/repo"
