#!/usr/bin/env bash
set -euo pipefail
cd /workspace
SDK="${ANDROID_HOME}/platforms/android-36/android.jar"
mkdir -p build/device-tests/classes build/device-tests/dex
aapt2 link -o build/device-tests/unsigned.apk -I "$SDK" --manifest tests/device/AndroidManifest.xml
javac --release 17 -Xlint:all -Werror -classpath "$SDK:build/classes" -d build/device-tests/classes tests/device/DeviceTests.java
jar cf build/device-tests/classes.jar -C build/device-tests/classes .
d8 --lib "$SDK" --classpath build/classes.jar --min-api 26 --output build/device-tests/dex build/device-tests/classes.jar
(cd build/device-tests/dex && zip -q ../unsigned.apk classes.dex)
zipalign -f -p 4 build/device-tests/unsigned.apk build/device-tests/aligned.apk
apksigner sign --ks .local/debug.keystore --ks-pass pass:android --out build/device-tests.apk build/device-tests/aligned.apk
