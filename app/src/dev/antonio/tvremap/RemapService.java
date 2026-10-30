package dev.antonio.tvremap;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashMap;
import java.util.Map;

public final class RemapService extends AccessibilityService {
    interface Capture { void onKey(int code, int scan); }
    static RemapService instance;
    static Capture capture;
    static boolean appVisible;
    private MappingStore store;
    private final Map<String, Mapping> held = new HashMap<>();

    @Override protected void onServiceConnected() { store = new MappingStore(this); instance = this; }
    @Override public void onDestroy() { if (instance == this) instance = null; held.clear(); super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* No screen events requested. */ }
    @Override public void onInterrupt() { held.clear(); }

    static boolean protectedKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_TV_POWER: case KeyEvent.KEYCODE_STB_POWER: case KeyEvent.KEYCODE_AVR_POWER:
            case KeyEvent.KEYCODE_SLEEP: case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_ASSIST: case KeyEvent.KEYCODE_VOICE_ASSIST:
                return true;
            default: return false;
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode(), scan = event.getScanCode();
        String id = event.getDeviceId() + ":" + Mapping.identity(code, scan);
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return held.remove(id) != null;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        // Actions fire once per press; still consume repeats and the matching release.
        if (event.getRepeatCount() > 0) return held.containsKey(id);
        held.remove(id); // Recover if a remote disconnected without sending key-up.
        if (protectedKey(code) || (code == 0 && scan == 0)) return false;
        if (capture != null) {
            held.put(id, new Mapping(code, scan, "", "none", ""));
            capture.onKey(code, scan);
            return true;
        }
        if (appVisible || store == null || !store.consented() || !store.enabled()) return false;
        Mapping mapping = store.find(code, scan);
        if (mapping == null || !Actions.supported(mapping.action)) return false;
        held.put(id, mapping);
        Actions.run(this, mapping);
        return true;
    }
}
