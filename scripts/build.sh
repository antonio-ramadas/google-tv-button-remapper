#!/usr/bin/env bash
set -euo pipefail
cd /workspace
SDK="${ANDROID_HOME}/platforms/android-36/android.jar"
mkdir -p build .local
rm -rf build/classes build/generated build/dex build/compiled.zip build/unsigned.apk build/aligned.apk
mkdir -p build/classes build/generated build/dex
aapt2 compile --dir app/res -o build/compiled.zip
aapt2 link -o build/unsigned.apk -I "$SDK" --manifest app/AndroidManifest.xml --java build/generated build/compiled.zip
find app/src build/generated -name '*.java' | sort > build/sources.txt
javac --release 17 -Xlint:all -Werror -classpath "$SDK" -d build/classes @build/sources.txt
jar cf build/classes.jar -C build/classes .
d8 --lib "$SDK" --min-api 26 --output build/dex build/classes.jar
(cd build/dex && zip -q ../unsigned.apk classes*.dex)
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f .local/debug.keystore ]; then
    keytool -genkeypair -keystore .local/debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug' >/dev/null 2>&1
fi
apksigner sign --ks .local/debug.keystore --ks-pass pass:android --out build/tv-button-remapper.apk build/aligned.apk
apksigner verify build/tv-button-remapper.apk
bash scripts/test.sh
printf '\nBuilt build/tv-button-remapper.apk\n'
