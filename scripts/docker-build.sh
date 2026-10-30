#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker build --platform linux/amd64 -t tv-button-remapper-build .
docker run --rm --platform linux/amd64 --network none -v "$PWD:/workspace" tv-button-remapper-build
