# Development notes

[← Back to the README](../README.md)

The app uses Java 17 and Android framework APIs, with no third-party app or test libraries. All build tools run in Docker. The app supports API 26 and later and targets API 36.

## Code layout

- `MainActivity.java`: native TV-friendly setup, learning, editing, permissions, and reset UI.
- `RemapService.java`: key filtering, protected keys, complete down/up consumption, held-key repeat consumption.
- `MappingStore.java`: persistence and JSON serialization.
- `Mapping.java`: immutable remap identity.
- `ButtonNames.java`: readable labels from runtime Android key metadata.
- `SystemIntents.java`: capability checks for exported, launchable settings activities.
- `Actions.java`: Android app launch, screensaver, display settings, and no-op actions.
- `scripts/`: SDK-only Docker build and test commands.

The accessibility service and activity share the default app process. Transient capture/visibility state is in memory; only explicit user configuration is persisted. Unmapped events pass through. A mapped press consumes both down and up, even when its action opens another app or pause changes mid-press. Actions fire once per press; holding a button does not repeatedly launch an app.

## Tests

Run these commands from the project root after building and installing the app.

Every build runs the plain Java mapping-identity checks. Device instrumentation uses Android's built-in `Instrumentation`; no test libraries. It writes a separate `test-remaps` preference file, never the user's remaps.

```sh
docker run --rm --platform linux/amd64 --network none \
  -v "$PWD:/workspace" tv-button-remapper-build bash scripts/build-device-tests.sh
adb install -r build/device-tests.apk
adb shell am instrument -w -e phase seed \
  dev.antonio.tvremap.tests/dev.antonio.tvremap.DeviceTests
adb shell am instrument -w -e phase verify \
  dev.antonio.tvremap.tests/dev.antonio.tvremap.DeviceTests
adb shell am instrument -w -e phase names \
  dev.antonio.tvremap.tests/dev.antonio.tvremap.DeviceTests
adb uninstall dev.antonio.tvremap.tests
```

The seed and verify phases run in separate app processes; names checks button labels. Instrumentation terminates the target process; Android may then mark its accessibility service disconnected. After testing, toggle Button Remapper off and on in accessibility settings and confirm the app shows remapping is on. Seed must run before verify. These checks cover persistent mapping/consent/pause, reset, removal, reserved keys, unmapped events, repeats, paired key consumption, capture, and editor bypass. To check storage over a real reboot, run seed, reboot and reconnect the TV, then run verify. The instrumentation harness invokes the service's filter directly; physical remote interception must also be tested.

Manual acceptance on each TV:

- Learn a remote button, map it to an installed app, leave Button Remapper, and verify that only the selected app opens.
- Hold the button: the selected app should open once.
- Close/reopen the app and restart the TV; check remaps without opening Button Remapper first.
- Pause/resume, remove one remap, cancel reset, then confirm reset and verify original behavior.
- Verify that picture settings opens and that Back dismisses every dialog and discards unsaved edits.
- Check Back/Home/navigation, missing target apps, and another active key-filtering service.

## Persistence and device limitations

Mappings, consent, and pause state are saved synchronously in private, device-protected `SharedPreferences`. They survive app process death, normal TV restarts, and APK updates signed with the same key. Uninstalling or clearing app data removes them. Android retains the accessibility setting and binds the service again after reboot; no boot receiver, foreground-service notification, ADB connection, or open activity is required. Force-stop is different from a normal restart and may require reopening the app or re-enabling its accessibility service.

Home, Back, arrows, OK/Enter, power/sleep/wake, and voice-assistant keys are reserved. Button labels use the running Android system's standard key names and public vendor key constants. There is no manufacturer or numeric-key lookup table. Old generated numeric labels are upgraded when a name is available; custom labels remain editable. Unknown keys display **Unnamed button**. Names are hints supplied by firmware, not a guarantee that the physical remote has that exact printed label.

Some manufacturers intercept branded buttons before accessibility services; such buttons cannot be supported through this public API. Only one enabled accessibility service can filter keys at a time. Buttons use their Android key code; UNKNOWN vendor keys use their scan code. The same key code on another connected remote/keyboard shares the mapping.

**Open picture settings** uses the TV's supported display settings screen and needs no extra permission. Direct brightness and volume control are intentionally omitted. Remaps saved by an older version for those removed actions pass through until the user chooses a new action.

**Start screensaver** launches the currently configured screensaver through the exported System UI activity on compatible Android 8–12 TVs. It needs no extra permission. Android 13 and later removed this activity's launch behavior, so this option is hidden there. It is also hidden if the launcher is missing or requires a permission the app does not hold. Vendor firmware may also limit support. It does not change your selected screensaver or idle timeout.

## Permissions and privacy

- Accessibility key filtering: enabled explicitly by the user. The service requests no screen-content retrieval, gestures, window events, or typing history. It acts only on configured keys; during learning it captures the selected key code.
- Package visibility is restricted to launchable apps; no `QUERY_ALL_PACKAGES` permission.
- No internet, storage, microphone, location, overlays, root, privileged settings, or boot permission. No analytics, ads, or remote data collection. Android backups are disabled with `allowBackup="false"`. On Android 12+, `data_extraction_rules.xml` also excludes device-protected and ordinary shared preferences from cloud backups and device-to-device transfers. This covers remaps, consent, and pause state without affecting local persistence across restarts.

## Compatibility scope

The source does not contain TV model IDs, manufacturer-specific key-code tables, device addresses, or fixed installed-app choices. Setup tries Android's TV accessibility route, the standard accessibility action, and general settings. Settings paths and available actions depend on the device. Picture settings and screensaver actions are offered only when a suitable system activity is exposed. Screensaver support additionally requires the older Android behavior described above; resolving an activity cannot guarantee every vendor implementation works.

Validation has been performed on one physical TV only. Other manufacturers, remotes, Android versions, settings layouts, and service restart behavior remain untested. Capability checks and graceful errors improve portability; they do not establish universal compatibility. The remote's Back button dismisses dialogs and leaves unsaved edits; Android 13+ uses `OnBackInvokedCallback` while older TVs handle remote Back key events. No deprecated Back callback is overridden.

## Toolchain

Java 17 provides one compatible runtime and language level for the Android SDK, signing tools, and bundletool. Compilation uses `-Xlint:all -Werror` without warning suppressions. Android D8 converts Java bytecode to DEX for the device; Java language support does not make newer Android APIs available on older TVs.

The Docker base image is pinned by digest. SDK command-line tools and bundletool downloads are checksum-verified, and SDK packages use explicit versions. Ubuntu package repositories can change, so the workflow is repeatable but does not promise byte-for-byte identical output forever. Update tool versions and checksums deliberately, then verify the APK, bundle, and device behavior.

The first image build downloads the tools and accepts the Android SDK licenses. Subsequent app builds run without network access. No SDK or JDK is installed on the host.

## Build an App Bundle

After building the Docker image, run:

```sh
docker run --rm --platform linux/amd64 --network none \
  -v "$PWD:/workspace" tv-button-remapper-build bash scripts/build-bundle.sh
```

This produces `build/button-remapper-unsigned.aab`. Sign it with your own upload key before submitting it to Google Play. Keep signing keys out of version control.

See [store materials](../store/README.md) for rebuilding artwork.
