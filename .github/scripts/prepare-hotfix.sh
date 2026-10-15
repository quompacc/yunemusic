#!/usr/bin/env bash
# Bereitet ein Hotfix-Release vor: ausgehend vom letzten stabilen Release-Tag
# wird der getestete NewPipe-Commit gepinnt, versionCode/-Name erhöht und ein
# Changelog angelegt. Ergebnis: Commit auf dem Branch "hotfix" plus Tag.
#
# Nutzung: prepare-hotfix.sh <letztes-release-tag> <newpipe-commit>
# Gibt das neue Tag auf stdout aus. Pusht nichts.
set -euo pipefail

BASE_TAG=$1
SHA=$2
F=app/build.gradle.kts

git checkout -q -B hotfix "$BASE_TAG"

CODE=$(grep -oP 'toIntOrNull\(\) \?: \K[0-9]+' "$F")
NAME=$(grep -oP 'ciVersionName"\) as\? String\) \?: "\K[^"]+' "$F")
# master kann schon einen höheren versionCode haben (vorbereitetes Release)
MASTER_CODE=$(git show origin/master:"$F" | grep -oP 'toIntOrNull\(\) \?: \K[0-9]+')
NEW_CODE=$(( (CODE > MASTER_CODE ? CODE : MASTER_CODE) + 1 ))
NEW_NAME=$(awk -F. '{ printf "%d.%d.%d", $1, $2, $3 + 1 }' <<<"$NAME")
TAG="v$NEW_NAME"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "Tag $TAG existiert bereits — Hotfix muss von Hand erstellt werden." >&2
  exit 1
fi

sed -i -E \
  -e "s/(toIntOrNull\(\) \?: )$CODE\b/\1$NEW_CODE/" \
  -e "s/(ciVersionName\"\) as\? String\) \?: \")$NAME\"/\1$NEW_NAME\"/" \
  -e "s/(newpipeVersion\"\) as\? String\) \?: \")[^\"]+\"/\1$SHA\"/" \
  "$F"

grep -q "toIntOrNull() ?: $NEW_CODE\$" "$F"
grep -q "?: \"$NEW_NAME\"\$" "$F"
grep -q "?: \"$SHA\"\$" "$F"

CL=fastlane/metadata/android
printf -- "- Restores YouTube playback: updated NewPipe Extractor (%s)\n" "$SHA" > "$CL/en-US/changelogs/$NEW_CODE.txt"
printf -- "- YouTube-Wiedergabe funktioniert wieder: NewPipe Extractor aktualisiert (%s)\n" "$SHA" > "$CL/de-DE/changelogs/$NEW_CODE.txt"

git add "$F" "$CL"/*/changelogs/"$NEW_CODE".txt
git commit -q -m "Release $NEW_NAME: NewPipe hotfix ($SHA)

Automatically created by the NewPipe health check: the NewPipe version
of $BASE_TAG no longer passed the smoke test, NewPipeExtractor $SHA does."
git tag -a "$TAG" -m "YuneMusic $NEW_NAME"

echo "$TAG"
