package dev.antonio.tvremap;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** Resolve only exported activities the app can launch; skip placeholder handlers. */
final class SystemIntents {
    static Intent resolve(Context context, Intent intent) {
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(intent, 0)) {
            if (!info.activityInfo.exported || !info.activityInfo.enabled || info.priority < 0
                    || info.activityInfo.packageName.endsWith(".frameworkpackagestubs")) continue;
            String permission = info.activityInfo.permission;
            if (permission != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) continue;
            return new Intent(intent).setClassName(info.activityInfo.packageName, info.activityInfo.name);
        }
        return null;
    }
}
