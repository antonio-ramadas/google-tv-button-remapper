package dev.antonio.tvremap;

import android.os.Build;
import android.view.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Uses the running device's definitions, never a manufacturer/key-code lookup table. */
final class ButtonNames {
    private static final Map<Integer, String> DEVICE_NAMES = readDeviceNames();

    static String forKey(int code) {
        if (code == KeyEvent.KEYCODE_UNKNOWN) return "Unnamed button";
        String symbol = KeyEvent.keyCodeToString(code);
        if (!symbol.startsWith("KEYCODE_")) symbol = DEVICE_NAMES.get(code);
        return format(symbol, Build.MANUFACTURER, Build.BRAND);
    }

    static String savedName(int code, int scan, String name) {
        // Upgrade old generated labels while preserving names the user supplied.
        String oldDefault = "Button " + (code == 0 ? scan : code);
        return name.equals(oldDefault) || name.equals("Unnamed button") ? forKey(code) : name;
    }

    private static Map<Integer, String> readDeviceNames() {
        Map<Integer, String> names = new HashMap<>();
        try {
            for (Field field : KeyEvent.class.getFields()) {
                if (field.getName().startsWith("KEYCODE_") && field.getType() == int.class
                        && Modifier.isStatic(field.getModifiers())) {
                    try { names.put(field.getInt(null), field.getName()); }
                    catch (IllegalAccessException | RuntimeException ignored) { /* Optional device metadata. */ }
                }
            }
        } catch (RuntimeException ignored) { /* Some firmware restricts these optional fields. */ }
        return names;
    }

    static String format(String symbol, String manufacturer, String brand) {
        if (symbol == null || !symbol.startsWith("KEYCODE_")) return "Unnamed button";
        String name = symbol.substring("KEYCODE_".length());
        for (String prefix : new String[]{manufacturer, brand}) {
            if (prefix == null || prefix.isEmpty()) continue;
            String normalized = prefix.toUpperCase(Locale.ROOT).replace(' ', '_') + "_";
            if (name.startsWith(normalized)) name = name.substring(normalized.length());
        }
        // These are human-readable action/product names, independent of device and numeric codes.
        switch (name) {
            case "PRIMEVIDEO": return "Prime Video";
            case "YOUTUBE": return "YouTube";
            case "APPLETV": return "Apple TV";
            case "BROWSEHERE": return "BrowseHere";
            case "MEDIA_PLAY_PAUSE": return "Play / pause";
            case "PROG_RED": return "Red button";
            case "PROG_GREEN": return "Green button";
            case "PROG_YELLOW": return "Yellow button";
            case "PROG_BLUE": return "Blue button";
            case "TV_INPUT": return "Input";
            case "EPG": return "TV guide";
            case "SET": return "Settings";
            default: break;
        }
        if (name.startsWith("MEDIA_")) name = name.substring(6);
        name = name.replace('_', ' ').toLowerCase(Locale.ROOT);
        return name.isEmpty() ? "Unnamed button" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
