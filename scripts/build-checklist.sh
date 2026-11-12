#!/usr/bin/env bash
#
# build-checklist.sh - Render CHECKLIST.html to CHECKLIST.pdf with headless Chrome.
#
#   bash scripts/build-checklist.sh

set -euo pipefail

cd "$(dirname "$0")/.."

CHROME=""
for c in google-chrome chromium chromium-browser \
         "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"; do
    if command -v "$c" >/dev/null 2>&1 || [[ -x "$c" ]]; then CHROME="$c"; break; fi
done
[[ -n "$CHROME" ]] || { echo "ERROR: Google Chrome or Chromium is required." >&2; exit 1; }

"$CHROME" --headless --disable-gpu --no-pdf-header-footer \
    --print-to-pdf="$PWD/CHECKLIST.pdf" "file://$PWD/CHECKLIST.html" 2>/dev/null
echo "Wrote CHECKLIST.pdf"
