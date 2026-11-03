package dev.antonio.tvremap;

import android.app.Instrumentation;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.view.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Framework instrumentation only. Uses isolated preferences, never the user's shortcuts. */
public final class DeviceTests extends Instrumentation {
    private String phase;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args.getString("phase", "verify"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            MappingStore store = new MappingStore(getTargetContext(), "test-remaps");
            if (phase.equals("screenshots")) {
                StringBuilder images = new StringBuilder();
                for (String name : new String[]{"autosave-editor.png", "autosave-help.png"}) {
                    java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), name);
                    images.append(name).append(':').append(android.util.Base64.encodeToString(
                            java.nio.file.Files.readAllBytes(file.toPath()), android.util.Base64.NO_WRAP)).append('\n');
                }
                result.putString("stream", images.toString());
                finish(-1, result);
                return;
            } else if (phase.equals("autosave")) {
                checks += new AutosaveTests(this).run();
            } else if (phase.equals("autosave-verify")) {
                MappingStore saved = new MappingStore(getTargetContext(), "test-autosave");
                Mapping mapping = saved.find(183, 123);
                check(mapping != null && mapping.name.equals("Movie night") && mapping.action.equals("app")
                        && !mapping.target.isEmpty(), "automatic changes survive process restart");
                check(!saved.enabled(), "autosave preserves pause state");
                check(saved.reset(), "clean autosave fixture");
            } else if (phase.equals("disclosure")) {
                testDisclosure();
            } else if (phase.equals("names")) {
                check(ButtonNames.forKey(KeyEvent.KEYCODE_MEDIA_PLAY).equals("Play"), "standard button label");
                check(ButtonNames.format("KEYCODE_EXAMPLE_QUICK_MENU", "Example", "Generic").equals("Quick menu"), "runtime manufacturer prefix");
                check(ButtonNames.format("KEYCODE_PRIMEVIDEO", "Example", "Generic").equals("Prime Video"), "readable app label");
                check(ButtonNames.format(null, "Example", "Generic").equals("Unnamed button"), "unknown button fallback");
                check(ButtonNames.savedName(KeyEvent.KEYCODE_MEDIA_PLAY, 0, "My button").equals("My button"), "preserve custom name");
                check(ButtonNames.savedName(KeyEvent.KEYCODE_MEDIA_PLAY, 0, "Button " + KeyEvent.KEYCODE_MEDIA_PLAY).equals("Play"), "upgrade generated name");
                check(Actions.available(getTargetContext(), "app"), "app action always offered");
                check(!Actions.available(getTargetContext(), "unsupported"), "unknown action hidden");
                StringBuilder report = new StringBuilder("PASS names: " + checks + " checks\n");
                for (Mapping m : new MappingStore(getTargetContext()).all()) report.append(m.name).append("\n");
                result.putString("stream", report.toString()); finish(-1, result); return;
            } else if (phase.equals("screensaver")) {
                runOnMainSync(() -> Actions.run(getTargetContext(),
                        new Mapping(0, 0, "Screensaver test", "screensaver", "")));
            } else if (phase.equals("seed")) {
                check(store.reset(), "reset test fixture");
                check(store.consent(), "save consent");
                check(store.setEnabled(false), "save pause");
                check(store.save(new Mapping(183, 123, "Example button", "none", "")), "save mapping");
            } else {
                check(!store.enabled(), "pause survives process restart");
                check(store.consented(), "consent survives process restart");
                Mapping saved = store.find(183, 0);
                check(saved != null && saved.name.equals("Example button"), "mapping survives process restart");
                check(store.all().size() == 1, "list persisted mappings");
                check(store.setEnabled(true), "resume");
                runOnMainSync(() -> testKeyStream(store));
                check(store.remove(saved), "remove one mapping");
                check(store.find(183, 123) == null, "removed mapping no longer matches");
                check(store.reset(), "reset all data");
                check(store.all().isEmpty() && !store.consented() && store.enabled(), "reset restores defaults");
            }
            result.putString("stream", "\nPASS " + phase + ": " + checks + " checks\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "\nFAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(error) + "\n"); finish(0, result);
        }
    }

    private void testDisclosure() throws Exception {
        MappingStore fixture = new MappingStore(getTargetContext(), "test-disclosure");
        check(fixture.reset(), "clean disclosure fixture");
        check(fixture.save(new Mapping(183, 0, "Preserved", "none", "")), "seed saved remap");
        check(fixture.setEnabled(false), "seed pause");
        getTargetContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("test-disclosure", Context.MODE_PRIVATE)
                .edit().putBoolean("consent", true).commit();
        check(!fixture.consented(), "legacy one-button consent requires renewal");
        check(fixture.all().size() == 1 && !fixture.enabled(), "upgrade preserves remaps and pause");

        Intent launch = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity = (MainActivity) startActivitySync(launch);
        Field storeField = MainActivity.class.getDeclaredField("store");
        storeField.setAccessible(true);
        Field dialogField = MainActivity.class.getDeclaredField("disclosureDialog");
        dialogField.setAccessible(true);
        Method show = MainActivity.class.getDeclaredMethod("disclosure");
        show.setAccessible(true);
        runOnMainSync(() -> {
            try {
                AlertDialog initial = (AlertDialog) dialogField.get(activity);
                if (initial != null) initial.dismiss();
                storeField.set(activity, fixture);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        final int[] settingsLaunches = {0};
        ActivityMonitor monitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                if (intent.getAction() != null && intent.getAction().startsWith("android.settings.")) {
                    check(fixture.consented(), "consent saved before requesting settings");
                    settingsLaunches[0]++;
                    return new ActivityResult(0, null);
                }
                return null;
            }
        };
        addMonitor(monitor);
        try {
            showDisclosure(activity, show);
            AlertDialog dialog = (AlertDialog) dialogField.get(activity);
            runOnMainSync(() -> {
                check(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isShown(), "agree button visible");
                check(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isShown(), "decline button visible");
                check(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus(), "decline focused by default");
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            check(!fixture.consented() && settingsLaunches[0] == 0, "decline does not consent or open settings");
            check(dialogField.get(activity) == null, "decline dismisses prompt");
            showDisclosure(activity, show);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            check(dialogField.get(activity) == null, "Back dismisses prompt");
            check(!fixture.consented() && settingsLaunches[0] == 0, "Back does not consent or open settings");
            showDisclosure(activity, show);
            shell("input keyevent KEYCODE_HOME");
            waitForIdleSync();
            check(!fixture.consented() && settingsLaunches[0] == 0, "Home does not consent or open settings");
            shell("am start -W -f 0x20000000 -n " + getTargetContext().getPackageName() + "/.MainActivity");
            waitForIdleSync();
            check(!fixture.consented(), "return from Home does not consent");
            showDisclosure(activity, show);
            AlertDialog returned = (AlertDialog) dialogField.get(activity);
            runOnMainSync(() -> {
                check(returned.isShowing(), "disclosure still available after Home");
                returned.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            check(fixture.consented() && settingsLaunches[0] == 1, "explicit agree opens settings once");
            check(new MappingStore(getTargetContext(), "test-disclosure").consented(), "new consent persisted");
            check(fixture.all().size() == 1 && !fixture.enabled(), "consent preserves remap and pause");
        } finally {
            removeMonitor(monitor);
            runOnMainSync(activity::finish);
            fixture.reset();
        }
    }

    private void showDisclosure(MainActivity activity, Method show) {
        runOnMainSync(() -> {
            try { show.invoke(activity); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        waitForIdleSync();
        // Idle is not enough: the TV's window transition may still own input.
        final boolean[] focused = {false};
        for (int attempt = 0; attempt < 50 && !focused[0]; attempt++) {
            runOnMainSync(() -> {
                try {
                    Field field = MainActivity.class.getDeclaredField("disclosureDialog");
                    field.setAccessible(true);
                    AlertDialog dialog = (AlertDialog) field.get(activity);
                    focused[0] = dialog != null && dialog.getWindow().getDecorView().hasWindowFocus();
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            if (!focused[0]) android.os.SystemClock.sleep(100);
        }
        check(focused[0], "disclosure window ready for remote input");
    }

    private void shell(String command) throws java.io.IOException {
        try (ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
                java.io.InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            while (input.read() != -1) { /* Wait for the shell command. */ }
        }
    }

    private void testKeyStream(MappingStore store) {
        try {
            RemapService service = new RemapService();
            Field field = RemapService.class.getDeclaredField("store"); field.setAccessible(true); field.set(service, store);
            RemapService.appVisible = false;
            check(service.onKeyEvent(event(0, 183, 0)), "mapped down consumed");
            check(service.onKeyEvent(event(0, 183, 1)), "mapped repeat consumed");
            check(service.onKeyEvent(event(1, 183, 0)), "mapped up consumed");
            check(!service.onKeyEvent(event(1, 183, 0)), "orphan up passes through");
            check(!service.onKeyEvent(event(0, 184, 0)), "unmapped down passes through");
            check(!service.onKeyEvent(event(1, 184, 0)), "unmapped up passes through");
            check(store.save(new Mapping(185, 0, "Old brightness remap", "brightness_up", "")), "save legacy fixture");
            check(!service.onKeyEvent(event(0, 185, 0)), "removed action passes down through");
            check(!service.onKeyEvent(event(1, 185, 0)), "removed action passes up through");
            store.setEnabled(false);
            check(!service.onKeyEvent(event(0, 183, 0)), "paused mappings pass through");
            store.setEnabled(true);
            RemapService.appVisible = true;
            check(!service.onKeyEvent(event(0, 183, 0)), "editor bypasses remaps");
            final int[] captured = {0};
            RemapService.capture = (code, scan) -> captured[0] = code;
            check(!service.onKeyEvent(event(0, KeyEvent.KEYCODE_BACK, 0)), "Back protected during learning");
            check(service.onKeyEvent(event(0, 184, 0)) && captured[0] == 184, "learn button");
            RemapService.capture = null;
            check(service.onKeyEvent(event(1, 184, 0)), "learned up consumed after dialog closes");
            RemapService.appVisible = false;
            check(service.onKeyEvent(event(0, 183, 0)), "mapped press before pause");
            store.setEnabled(false);
            check(service.onKeyEvent(event(1, 183, 0)), "up remains consumed when paused mid-press");
            testProtectedPowerKeys(service, store);
            for (int code : new int[]{3, 4, 19, 20, 21, 22, 23, 26, 66, 219, 231}) {
                check(RemapService.protectedKey(code), "essential key protected: " + code);
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { RemapService.capture = null; RemapService.appVisible = false; }
    }
    private void testProtectedPowerKeys(RemapService service, MappingStore store) {
        store.setEnabled(true);
        RemapService.appVisible = false;
        for (int code : new int[]{KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER,
                KeyEvent.KEYCODE_STB_POWER, KeyEvent.KEYCODE_AVR_POWER}) {
            // Simulate a remap saved before this key was protected.
            check(store.save(new Mapping(code, 0, "Power", "none", "")), "save power fixture: " + code);
            check(!service.onKeyEvent(event(0, code, 0)), "saved power down passes through: " + code);
            check(!service.onKeyEvent(event(0, code, 1)), "saved power repeat passes through: " + code);
            check(!service.onKeyEvent(event(1, code, 0)), "saved power up passes through: " + code);

            final int[] captured = {0};
            RemapService.capture = (key, scan) -> captured[0]++;
            check(!service.onKeyEvent(event(0, code, 0)), "learning power down passes through: " + code);
            check(!service.onKeyEvent(event(0, code, 1)), "learning power repeat passes through: " + code);
            check(!service.onKeyEvent(event(1, code, 0)), "learning power up passes through: " + code);
            check(captured[0] == 0, "power cannot be learned: " + code);
            RemapService.capture = null;
        }
    }

    private KeyEvent event(int action, int code, int repeat) { return new KeyEvent(100, 200 + repeat * 150, action, code, repeat); }
    private void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
