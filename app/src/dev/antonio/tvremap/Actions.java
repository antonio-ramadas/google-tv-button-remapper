package dev.antonio.tvremap;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.os.Build;
import android.widget.Toast;

final class Actions {
    static final String[] IDS = {"app", "screensaver", "display", "none"};
    static final String[] LABELS = {"Open an app", "Start screensaver", "Open picture settings", "Do nothing"};

    static String label(Mapping mapping) {
        if ("app".equals(mapping.action)) return "Open " + mapping.target.substring(mapping.target.indexOf('|') + 1);
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(mapping.action)) return LABELS[i];
        return "Choose an action";
    }

    static boolean supported(String action) {
        for (String id : IDS) if (id.equals(action)) return true;
        return false;
    }

    private static Intent systemIntent(Context context, String action) {
        Intent intent;
        if (action.equals("display")) intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        else if (action.equals("screensaver") && Build.VERSION.SDK_INT < 33) {
            // AOSP's optional launcher; newer Android versions removed its behavior.
            intent = new Intent(Intent.ACTION_MAIN)
                    .setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
        } else return null;
        return SystemIntents.resolve(context, intent);
    }

    static boolean available(Context context, String action) {
        return action.equals("app") || action.equals("none") || systemIntent(context, action) != null;
    }

    static void run(Context context, Mapping mapping) {
        try {
            switch (mapping.action) {
                case "app":
                    String pkg = mapping.target.split("\\|", 2)[0];
                    Intent launch = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                    if (launch == null) launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch == null) { message(context, "App unavailable. Choose another app in Button Remapper."); return; }
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
                case "screensaver":
                case "display":
                    Intent system = systemIntent(context, mapping.action);
                    if (system == null) {
                        message(context, "This action is unavailable on this TV. Choose another action.");
                        return;
                    }
                    context.startActivity(system.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
                    break;
                case "none": break;
                default: message(context, "Choose a new action in Button Remapper.");
            }
        } catch (RuntimeException exception) {
            message(context, "This action is unavailable on this TV. Choose another action.");
        }
    }

    static void message(Context context, String message) { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); }
}
