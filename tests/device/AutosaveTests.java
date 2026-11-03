package dev.antonio.tvremap;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Actual framework UI clicks with isolated preferences; no external test library. */
final class AutosaveTests {
    private final Instrumentation test;
    private int checks;
    private MainActivity activity;
    private MappingStore store;

    AutosaveTests(Instrumentation test) { this.test = test; }

    int run() throws Exception {
        store = new MappingStore(test.getTargetContext(), "test-autosave");
        check(store.reset() && store.consent() && store.setEnabled(false), "prepare isolated fixture");
        activity = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        test.runOnMainSync(() -> {
            try {
                Field dialog = MainActivity.class.getDeclaredField("disclosureDialog");
                dialog.setAccessible(true);
                AlertDialog initial = (AlertDialog) dialog.get(activity);
                if (initial != null) initial.dismiss();
                Field field = MainActivity.class.getDeclaredField("store");
                field.setAccessible(true);
                field.set(activity, store);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        try {
            editor(new Mapping(183, 123, "Test button", "", ""));
            await("Choose an action  ›");
            check(node("Save remap button") == null, "no manual save control");
            check(saved() == null, "unconfigured button not persisted");
            click("Choose an action  ›");
            click("Open an app");
            await("Open which app?");
            back();
            await("Choose an action  ›");
            check(saved() == null, "cancel app picker does not save incomplete action");
            click("Choose an action  ›");
            click("Do nothing");
            await("Do nothing  ›");
            check(saved() != null && saved().action.equals("none"), "action saved immediately");
            check(node("Remove this button remap") != null, "saved button can be removed immediately");
            check(!store.enabled(), "action keeps pause state");

            click("Button name: Test button  ›");
            AccessibilityNodeInfo input = editable(test.getUiAutomation().getRootInActiveWindow());
            check(input != null, "rename input available");
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Movie night");
            check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "enter new name");
            click("Done");
            await("Button name: Movie night  ›");
            check(saved().name.equals("Movie night"), "confirmed name saved immediately");

            click("Do nothing  ›");
            click("Open an app");
            await("Open which app?");
            back();
            await("Do nothing  ›");
            check(saved().action.equals("none"), "cancel changed app picker preserves existing action");
            click("Do nothing  ›");
            await("What should this button do?");
            back();
            await("Do nothing  ›");
            check(saved().action.equals("none"), "cancel action picker preserves existing action");

            click("Do nothing  ›");
            click("Open an app");
            await("Open which app?");
            // Choose a launchable app actually present in the visible list on this TV.
            String target = null;
            for (ResolveInfo info : activity.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
                String label = info.loadLabel(activity.getPackageManager()).toString();
                if (!info.activityInfo.packageName.equals(activity.getPackageName()) && node(label) != null) {
                    target = info.activityInfo.packageName;
                    click(label);
                    break;
                }
            }
            check(target != null, "select an installed app from picker");
            await("Button name: Movie night  ›");
            check(saved().action.equals("app") && saved().target.startsWith(target + "|"), "app target saved immediately");
            check(saved().name.equals("Movie night"), "changing action keeps name");
            check(node("Save remap button") == null, "no save button after configuration");
            screenshot("autosave-editor.png");
            click("Help & Permissions");
            await("Privacy policy");
            screenshot("autosave-help.png");
            back();
            await("Button name: Movie night  ›");
            back();
            await("Your button remaps");
            check(saved().name.equals("Movie night") && saved().action.equals("app"), "Back keeps saved changes");

            // A name alone is still a draft, and leaving it creates no broken remap.
            editor(new Mapping(184, 0, "Unfinished button", "", ""));
            await("Choose an action  ›");
            back();
            await("Your button remaps");
            check(store.find(184, 0) == null && store.all().size() == 1, "cancel unfinished button preserves saved remaps");
            return checks;
        } finally {
            test.runOnMainSync(activity::finish);
        }
    }

    private Mapping saved() { return store.find(183, 123); }

    private void editor(Mapping mapping) {
        test.runOnMainSync(() -> {
            try {
                Method method = MainActivity.class.getDeclaredMethod("showEditor", Mapping.class);
                method.setAccessible(true);
                method.invoke(activity, mapping);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        test.waitForIdleSync();
    }

    private AccessibilityNodeInfo node(String label) {
        AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo found : root.findAccessibilityNodeInfosByText(label)) {
            if (label.equalsIgnoreCase(found.getText() == null ? "" : found.getText().toString())
                    && found.isVisibleToUser()) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo await(String label) {
        for (int attempt = 0; attempt < 50; attempt++) {
            AccessibilityNodeInfo found = node(label);
            if (found != null) return found;
            SystemClock.sleep(100);
        }
        throw new AssertionError("UI not found: " + label);
    }

    private void click(String label) {
        AccessibilityNodeInfo found = await(label);
        while (found != null && !found.isClickable()) found = found.getParent();
        check(found != null && found.performAction(AccessibilityNodeInfo.ACTION_CLICK), "click " + label);
        test.waitForIdleSync();
    }

    private AccessibilityNodeInfo editable(AccessibilityNodeInfo root) {
        if (root == null || root.isEditable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo found = editable(root.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private void back() {
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        test.waitForIdleSync();
    }

    private void screenshot(String name) throws Exception {
        test.getUiAutomation().waitForIdle(300, 5000);
        Bitmap bitmap = test.getUiAutomation().takeScreenshot();
        check(bitmap != null, "capture " + name);
        try (FileOutputStream output = new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null), name))) {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "save " + name);
        }
    }

    private void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
