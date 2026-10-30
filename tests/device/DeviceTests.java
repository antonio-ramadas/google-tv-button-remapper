package dev.antonio.tvremap;

import android.app.Instrumentation;
import android.os.Bundle;
import android.view.KeyEvent;
import java.lang.reflect.Field;

/** Framework instrumentation only. Uses isolated preferences, never the user's shortcuts. */
public final class DeviceTests extends Instrumentation {
    private String phase;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args.getString("phase", "verify"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            MappingStore store = new MappingStore(getTargetContext(), "test-remaps");
            if (phase.equals("names")) {
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
            result.putString("stream", "\nFAIL: " + error + "\n"); finish(0, result);
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
