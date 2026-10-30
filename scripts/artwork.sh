#!/usr/bin/env bash
set -euo pipefail
cd /workspace
mkdir -p build/artwork
javac --release 17 -Xlint:all -Werror -d build/artwork scripts/artwork/Artwork.java
java -Djava.awt.headless=true -cp build/artwork Artwork "$@"
