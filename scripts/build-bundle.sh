#!/usr/bin/env bash
set -euo pipefail
cd /workspace
# Rebuild classes/resources so the bundle cannot silently contain an older APK's code.
bash scripts/build.sh
SDK="${ANDROID_HOME}/platforms/android-36/android.jar"
rm -rf build/bundle
mkdir -p build/bundle/base/manifest build/bundle/base/dex
aapt2 link --proto-format -o build/bundle/resources.apk -I "$SDK" \
    --manifest app/AndroidManifest.xml build/compiled.zip
unzip -q build/bundle/resources.apk -d build/bundle/base
mv build/bundle/base/AndroidManifest.xml build/bundle/base/manifest/
cp build/dex/classes*.dex build/bundle/base/dex/
(cd build/bundle/base && zip -qr ../base.zip .)
java -jar /opt/bundletool.jar build-bundle --modules=build/bundle/base.zip \
    --output=build/button-remapper-unsigned.aab --overwrite
java -jar /opt/bundletool.jar validate --bundle=build/button-remapper-unsigned.aab
printf '\nBuilt build/button-remapper-unsigned.aab. Sign with your upload key before uploading.\n'
