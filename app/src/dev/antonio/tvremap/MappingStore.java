package dev.antonio.tvremap;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class MappingStore {
    private final SharedPreferences prefs;

    MappingStore(Context context) { this(context, "remaps"); }

    MappingStore(Context context, String fileName) {
        // Available before unlock; no receiver or always-running foreground service needed.
        prefs = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(fileName, Context.MODE_PRIVATE);
    }

    boolean enabled() { return prefs.getBoolean("enabled", true); }
    boolean consented() { return prefs.getBoolean("consent", false); }
    boolean setEnabled(boolean value) { return prefs.edit().putBoolean("enabled", value).commit(); }
    boolean consent() { return prefs.edit().putBoolean("consent", true).commit(); }
    boolean reset() { return prefs.edit().clear().commit(); }
    boolean remove(Mapping mapping) { return prefs.edit().remove(mapping.id()).commit(); }

    Mapping find(int code, int scan) {
        return decode(prefs.getString(Mapping.identity(code, scan), null));
    }

    List<Mapping> all() {
        List<Mapping> result = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("key_") && !entry.getKey().startsWith("scan_")) continue;
            Mapping mapping = decode(String.valueOf(entry.getValue()));
            if (mapping != null) result.add(mapping);
        }
        Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    boolean save(Mapping mapping) {
        try {
            JSONObject json = new JSONObject().put("key", mapping.keyCode)
                    .put("scan", mapping.scanCode).put("name", mapping.name)
                    .put("action", mapping.action).put("target", mapping.target);
            return prefs.edit().putString(mapping.id(), json.toString()).commit();
        } catch (JSONException exception) { return false; }
    }

    private Mapping decode(String value) {
        if (value == null) return null;
        try {
            JSONObject json = new JSONObject(value);
            int code = json.getInt("key"), scan = json.getInt("scan");
            return new Mapping(code, scan, ButtonNames.savedName(code, scan, json.getString("name")),
                    json.getString("action"), json.getString("target"));
        } catch (JSONException exception) { return null; }
    }
}
