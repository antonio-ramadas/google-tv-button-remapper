#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
args=()
if [ -n "${ANDROID_SERIAL:-}" ]; then args=(-s "$ANDROID_SERIAL"); fi
adb "${args[@]}" install -r build/tv-button-remapper.apk
adb "${args[@]}" shell am start -n dev.antonio.tvremap/.MainActivity
