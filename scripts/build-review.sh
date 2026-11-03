#!/usr/bin/env bash
# Run in the Docker build image. Separate identity preserves an installed app
# when its signing key differs from the local development key.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/review/.local
cp -R app scripts tests build/review/
cp .local/debug.keystore build/review/.local/
find build/review/app build/review/tests -type f \( -name '*.java' -o -name '*.xml' \) \
  -exec sed -i 's/dev\.antonio\.tvremap/dev.antonio.tvremap.review/g' {} +
sed -i 's/dev\.antonio\.tvremap/dev.antonio.tvremap.review/g' build/review/scripts/test.sh
bash build/review/scripts/build.sh
bash build/review/scripts/build-device-tests.sh
cp build/review/build/tv-button-remapper.apk build/button-remapper-review.apk
cp build/review/build/device-tests.apk build/button-remapper-review-tests.apk
