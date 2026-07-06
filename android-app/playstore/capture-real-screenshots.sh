#!/usr/bin/env bash
# Capture real device screenshots for the Play listing.
#
# Google Play requires at least 2 (up to 8) real phone screenshots:
#   - PNG or JPEG, 16:9 or 9:16, each side 320px–3840px.
# The generated images in assets/screenshots/ are promotional slides; Google prefers
# (and reviewers expect) screenshots that show the actual app. Use this script to grab them.
#
# Usage:
#   1. Connect a phone with USB debugging on (or a running emulator).
#   2. Install the app:  adb install -r ../app/build/outputs/apk/release/app-universal-release.apk
#      (or build+install a debug build).
#   3. Navigate to a screen you want, then run:  ./capture-real-screenshots.sh home
#      Repeat with different names for each screen (home, pair, transfer, history, ...).

set -euo pipefail
NAME="${1:-screen}"
OUT_DIR="$(cd "$(dirname "$0")" && pwd)/assets/screenshots-real"
mkdir -p "$OUT_DIR"

if ! adb get-state >/dev/null 2>&1; then
  echo "No device/emulator detected. Connect one and enable USB debugging." >&2
  exit 1
fi

TS="$(date +%H%M%S)"
FILE="$OUT_DIR/${NAME}-${TS}.png"
adb exec-out screencap -p > "$FILE"
echo "Saved $FILE"
echo "Suggested screens to capture: Home (sync on), Pair (QR/nearby), Transfer (progress), History."
