#!/usr/bin/env bash
# Meldet einen Health-Check-Fehler: kommentiert ein offenes Issue mit Titel $TITLE
# oder legt ein neues an, damit nicht jeden Tag ein weiteres Issue entsteht.
set -euo pipefail

NR=$(gh issue list --state open --search "\"$TITLE\" in:title" --json number,title \
       --jq "map(select(.title | startswith(env.TITLE))) | .[0].number // empty")

if [ -n "$NR" ]; then
  gh issue comment "$NR" --body "$BODY"
else
  gh issue create --title "$TITLE" --body "$BODY"
fi
