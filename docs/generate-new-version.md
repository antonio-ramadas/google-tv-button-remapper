For Google Play, generate a signed .aab bundle. Run these commands on your laptop from the project directory, with Docker running. All Java and Android tools stay inside Docker.
1. Build the Docker image and app:
./scripts/docker-build.sh

2. Open an interactive shell in the build container:
docker run --rm -it --platform linux/amd64 --network none \
  -v "$PWD:/workspace" tv-button-remapper-build bash

Run the remaining commands inside this shell.

3. Create your upload key once. If you already registered an upload key with Google Play, use that existing key instead.
mkdir -p .local

keytool -genkeypair \
  -keystore .local/upload-key.jks \
  -storetype JKS \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

Follow the prompts for a password and certificate identity. When asked for the key password, press Enter to reuse the keystore password. Password characters won’t appear while typing.

Back up this file and store its password securely. Your project’s .gitignore already excludes .local/ and .jks files. Use the same upload key for future releases, including from another laptop.

4. Build the bundle:
bash scripts/build-bundle.sh

5. Sign it, entering your password when prompted:
jarsigner \
  -keystore .local/upload-key.jks \
  -signedjar build/button-remapper-release.aab \
  build/button-remapper-unsigned.aab \
  upload

6. Verify the result:
jarsigner -verify build/button-remapper-release.aab

java -jar /opt/bundletool.jar validate \
  --bundle=build/button-remapper-release.aab

Look for jar verified. Self-signed certificate and missing timestamp warnings are expected for this upload key.

7. Exit the container:
exit

Your upload file is now:
build/button-remapper-release.aab

In Play Console, create an internal testing release, follow the Play App Signing setup, and upload that file. Google uses your upload signature to verify the bundle, then signs the distributed app with its app signing key. Official signing guide