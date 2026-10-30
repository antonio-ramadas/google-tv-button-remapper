#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/tests
javac --release 17 -Xlint:all -Werror -d build/tests app/src/dev/antonio/tvremap/Mapping.java tests/MappingTest.java
java -cp build/tests dev.antonio.tvremap.MappingTest
