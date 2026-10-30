package dev.antonio.tvremap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 25, 32), CARD = Color.rgb(29, 44, 53);
    private static final int INK = Color.rgb(240, 246, 247), MUTED = Color.rgb(166, 186, 196), ACCENT = Color.rgb(140, 229, 206);
    private MappingStore store;
    private LinearLayout content;
    private TextView status;
    private Button pauseButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable unregisterBack;
    private AlertDialog captureDialog;
    private Mapping editing;
    private boolean connected;
    private boolean setupPromptShown;
    private final Runnable refreshStatus = new Runnable() {
        @Override public void run() {
            boolean now = RemapService.instance != null;
            if (now != connected && editing == null && captureDialog == null) showHome();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new MappingStore(this);
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        RemapService.appVisible = true;
        if (editing == null) showHome(); else showEditor(editing);
        handler.post(refreshStatus);
        // Prompt once per app opening, without repeatedly interrupting a user who dismisses the prompt.
        // Android can take a moment to bind an enabled service, so check its saved setting too.
        if (!setupPromptShown) {
            setupPromptShown = true;
            if (!store.consented() || !remoteAccessEnabled()) disclosure();
        }
    }

    @Override protected void onPause() {
        RemapService.appVisible = false;
        RemapService.capture = null;
        if (captureDialog != null) captureDialog.dismiss();
        handler.removeCallbacks(refreshStatus);
        super.onPause();
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        // Older TVs deliver remote Back presses through key events.
        if (Build.VERSION.SDK_INT < 33 && editing != null && code == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0) event.startTracking();
            return true;
        }
        return super.onKeyDown(code, event);
    }

    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if (Build.VERSION.SDK_INT < 33 && editing != null && code == KeyEvent.KEYCODE_BACK) {
            if (event.isTracking() && !event.isCanceled()) showHome();
            return true;
        }
        return super.onKeyUp(code, event);
    }

    private void updateBackNavigation() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (editing != null && unregisterBack == null) unregisterBack = BackApi33.register(this, this::showHome);
        else if (editing == null && unregisterBack != null) {
            unregisterBack.run();
            unregisterBack = null;
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (unregisterBack != null) unregisterBack.run();
        super.onDestroy();
    }

    /** Keep newer platform types out of the older-TV execution path. */
    private static final class BackApi33 {
        static Runnable register(Activity activity, Runnable action) {
            OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            OnBackInvokedCallback callback = action::run;
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private GradientDrawable background(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(12));
        if (stroke != 0) shape.setStroke(dp(2), stroke);
        return shape;
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label); view.setAllCaps(false); view.setTextSize(17); view.setTextColor(INK);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setPadding(dp(20), dp(12), dp(20), dp(12)); view.setMinHeight(dp(58));
        view.setBackground(background(CARD, 0));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.bottomMargin = dp(10); view.setLayoutParams(layout);
        view.setOnFocusChangeListener((v, focused) -> {
            view.setBackground(background(focused ? ACCENT : CARD, focused ? Color.WHITE : 0));
            view.setTextColor(focused ? BG : INK);
        });
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private Button sideButton(String id, String label, Runnable action) {
        Button view = button(label, action);
        view.setTag(id);
        view.setTextSize(14);
        view.setMinHeight(dp(42));
        view.setMinimumHeight(dp(42));
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        return view;
    }

    private void frame(String title, String subtitle, String footer) {
        connected = RemapService.instance != null;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(42), dp(30), dp(42), dp(24)); root.setBackgroundColor(BG);
        LinearLayout rail = new LinearLayout(this); rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(0, 0, dp(34), 0);
        root.addView(rail, new LinearLayout.LayoutParams(dp(248), -1));
        TextView brand = text("REMOTE / YOUR WAY", 12, ACCENT); brand.setLetterSpacing(.14f); brand.setIncludeFontPadding(false); rail.addView(brand);
        TextView heading = text("Button\nRemapper", 36, INK); heading.setTypeface(null, Typeface.BOLD); rail.addView(heading);
        View spacer = new View(this); rail.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        pauseButton = null;
        if (!store.all().isEmpty()) {
            pauseButton = sideButton("side:pause", "", () -> {
                if (!store.setEnabled(!store.enabled())) Actions.message(this, "Could not save. Try again.");
                // Update in place: retain focus, scroll position, and any unsaved edit.
                updateStatus();
            });
            rail.addView(pauseButton);
        }
        rail.addView(sideButton("side:help", "Help & Permissions", this::help));
        rail.addView(sideButton("side:reset", "Reset Button Remapper", this::reset));
        status = text("", 16, MUTED);
        updateStatus();
        rail.addView(status);
        rail.addView(text("Fully private and saved locally.", 13, MUTED));
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(4), 0, dp(4), 0);
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        TextView pageTitle = text(title, 28, INK); pageTitle.setIncludeFontPadding(false);
        // Align the visible tops of the two headings despite their different font sizes.
        Rect titleBounds = new Rect(), brandBounds = new Rect();
        pageTitle.getPaint().getTextBounds(title, 0, title.length(), titleBounds);
        String brandText = brand.getText().toString();
        brand.getPaint().getTextBounds(brandText, 0, brandText.length(), brandBounds);
        int titleInset = titleBounds.top - pageTitle.getPaint().getFontMetricsInt().ascent;
        int brandInset = brandBounds.top - brand.getPaint().getFontMetricsInt().ascent;
        brand.setPadding(0, dp(4) + Math.max(0, titleInset - brandInset), 0, dp(8));
        pageTitle.setPadding(0, dp(4) + Math.max(0, brandInset - titleInset), 0, dp(8));
        main.addView(pageTitle);
        if (subtitle != null) main.addView(text(subtitle, 15, MUTED));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(2), 0, dp(12));
        scroll.addView(content); main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (footer != null) {
            TextView reminder = text(footer, 13, MUTED);
            reminder.setPadding(0, dp(12), 0, 0);
            main.addView(reminder);
        }
        setContentView(root);
    }

    private void showHome() {
        String previousFocus = sidebarFocus();
        editing = null;
        frame("Your button remaps", null,
                "Home, Back and navigation always keep their original behavior.\nButton remaps pause while you are in this app.");
        if (!connected || !store.consented()) {
            Button setup = button("Remote button permission  ›", this::disclosure); content.addView(setup); setup.requestFocus();
        }
        Button add = button("＋  Add a button", this::startCapture); content.addView(add);
        if (connected && store.consented()) add.requestFocus();
        List<Mapping> mappings = store.all();
        if (mappings.isEmpty()) {
            content.addView(text("No button remaps yet", 21, INK));
            content.addView(text("Open your favorite app with a remote button.\nButton remaps stay saved after restarting the app or TV.", 16, MUTED));
        } else {
            for (Mapping mapping : mappings) content.addView(button("Button: " + mapping.name + "\n" + Actions.label(mapping) + "  ›", () -> showEditor(mapping)));
        }
        restoreSidebarFocus(previousFocus);
        updateBackNavigation();
    }

    private String sidebarFocus() {
        View focused = getCurrentFocus();
        Object tag = focused == null ? null : focused.getTag();
        return tag instanceof String && ((String) tag).startsWith("side:") ? (String) tag : null;
    }

    private void restoreSidebarFocus(String tag) {
        if (tag == null) return;
        View view = getWindow().getDecorView().findViewWithTag(tag);
        if (view != null) view.requestFocus();
    }

    private void updateStatus() {
        if (pauseButton != null) pauseButton.setText(store.enabled() ? "Pause all remaps" : "Resume all remaps");
        status.setText(!connected ? "●  Setup needed" : !store.consented() ? "●  Consent needed"
                : store.enabled() ? "●  Remapping is on" : "●  Remapping paused");
        status.setTextColor(connected ? ACCENT : MUTED);
    }

    private boolean remoteAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName service = new ComponentName(this, RemapService.class);
        for (String name : enabled.split(":")) {
            if (service.equals(ComponentName.unflattenFromString(name))) return true;
        }
        return false;
    }

    private void disclosure() {
        new AlertDialog.Builder(this).setTitle("Allow remote button access?")
            .setMessage("Button Remapper uses Android’s accessibility service to receive remote button presses, even while other apps are open, and run only the remaps you choose.\n\nIt does not read screen content, store a history of presses, or collect or share your data. Only your remap choices are saved on this TV.\n\nIn Accessibility settings, find Button Remapper under Services or Downloaded apps and turn it on. Then press Back to return here and add a button. You can disable access or reset the app at any time.")
            .setPositiveButton("Agree & open settings", (d, w) -> {
                if (store.consent()) openAccessibilitySettings();
                else Actions.message(this, "Could not save consent. Try again.");
            }).show();
    }

    private void startCapture() {
        if (RemapService.instance == null || !store.consented()) { disclosure(); return; }
        captureDialog = new AlertDialog.Builder(this).setTitle("Press the button to change")
            .setMessage("Press the remote button you want to change.\n\nHome, Back, arrows, OK, power and voice are reserved. Press Back to cancel.\n\nIf the TV opens the original app instead, its firmware may reserve that button.")
            .create();
        captureDialog.setOnDismissListener(d -> { RemapService.capture = null; captureDialog = null; });
        captureDialog.show();
        RemapService.capture = (code, scan) -> {
            RemapService.capture = null;
            handler.post(() -> {
                if (captureDialog == null) return;
                captureDialog.dismiss();
                Mapping previous = store.find(code, scan);
                String name = ButtonNames.forKey(code);
                Mapping selected = previous != null ? previous : new Mapping(code, scan, name, "", "");
                showEditor(selected);
                chooseAction(selected);
            });
        };
    }

    private void showEditor(Mapping mapping) {
        String previousFocus = sidebarFocus();
        editing = mapping;
        frame("Make it yours", "Choose what happens when you press this button.", null);
        Button name = button("Button name: " + mapping.name + "  ›", () -> rename(mapping)); content.addView(name);
        Button choose = button(mapping.action.isEmpty() ? "Choose an action  ›" : Actions.label(mapping) + "  ›", () -> chooseAction(mapping));
        content.addView(choose); choose.requestFocus();
        if (Actions.supported(mapping.action)) {
            content.addView(button("Save remap button", () -> {
                if (store.save(mapping)) { showHome(); Actions.message(this, "Remap saved. Press Home to try it."); }
                else Actions.message(this, "Could not save. Try again.");
            }));
        }
        if (store.find(mapping.keyCode, mapping.scanCode) != null) content.addView(button("Remove this button remap", () -> new AlertDialog.Builder(this)
            .setTitle("Remove " + mapping.name + "?").setMessage("This button will return to its original behavior.")
            .setPositiveButton("Remove", (d, w) -> {
                if (store.remove(mapping)) showHome(); else Actions.message(this, "Could not remove. Try again.");
            }).show()));
        content.addView(text("Press Back to return without saving.", 13, MUTED));
        restoreSidebarFocus(previousFocus);
        updateBackNavigation();
    }

    private void rename(Mapping mapping) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(mapping.name); input.selectAll();
        new AlertDialog.Builder(this).setTitle("Button name").setView(input)
            .setPositiveButton("Done", (d, w) -> {
                String name = input.getText().toString().trim();
                showEditor(new Mapping(mapping.keyCode, mapping.scanCode, name.isEmpty() ? mapping.name : name, mapping.action, mapping.target));
            }).show();
    }

    private void chooseAction(Mapping mapping) {
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < Actions.IDS.length; i++) {
            if (Actions.available(this, Actions.IDS[i])) {
                ids.add(Actions.IDS[i]); labels.add(Actions.LABELS[i]);
            }
        }
        new AlertDialog.Builder(this).setTitle("What should this button do?")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                String action = ids.get(which);
                if (action.equals("app")) chooseApp(mapping);
                else showEditor(new Mapping(mapping.keyCode, mapping.scanCode, mapping.name, action, ""));
            }).show();
    }

    private void chooseApp(Mapping mapping) {
        Map<String, String> apps = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : getPackageManager().queryIntentActivities(intent, 0)) {
                String pkg = info.activityInfo.packageName;
                if (!pkg.equals(getPackageName())) apps.put(pkg, info.loadLabel(getPackageManager()).toString());
            }
        }
        List<String> packages = new ArrayList<>(apps.keySet());
        Collections.sort(packages, (a, b) -> apps.get(a).compareToIgnoreCase(apps.get(b)));
        String[] labels = new String[packages.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(packages.get(i));
        if (labels.length == 0) { Actions.message(this, "No launchable apps found on this TV."); return; }
        new AlertDialog.Builder(this).setTitle("Open which app?").setItems(labels, (d, which) -> {
            String pkg = packages.get(which);
            showEditor(new Mapping(mapping.keyCode, mapping.scanCode, mapping.name, "app", pkg + "|" + apps.get(pkg)));
        }).show();
    }

    private void help() {
        new AlertDialog.Builder(this).setTitle("Help & Permissions")
            .setMessage("Remote button permission lets Button Remapper detect button presses while you use other apps. It does not allow anyone to connect to your TV or read your screen.\n\nSome TVs reserve certain buttons. If a button cannot be detected, try another one or turn off other button-remapping apps.\n\nPress Back to close this screen or leave an edit without saving.")
            .setNeutralButton("Privacy policy", (d, w) -> privacy())
            .setPositiveButton("Remote button permission", (d, w) -> disclosure()).show();
    }

    private void privacy() {
        new AlertDialog.Builder(this).setTitle("Privacy policy")
            .setMessage(getString(R.string.privacy_policy)).show();
    }

    private void reset() {
        new AlertDialog.Builder(this).setTitle("Reset Button Remapper?")
            .setMessage("Delete all remaps and turn off Button Remapper? Your remote buttons will return to their original behavior.\n\nYour TV settings and installed apps will stay as they are. Press Back to keep your remaps.")
            .setPositiveButton("Reset everything", (d, w) -> {
                if (!store.reset()) { Actions.message(this, "Could not reset. Try again."); return; }
                if (RemapService.instance != null) RemapService.instance.disableSelf();
                showHome(); Actions.message(this, "Button remaps cleared. Remapping turned off.");
            }).show();
    }

    private void openAccessibilitySettings() {
        // Some Google TVs resolve the standard action to a placeholder that only shows an error.
        // Prefer the TV OEM link, then a real standard handler, then the main settings screen.
        for (String action : new String[]{"android.settings.ACCESSIBILITY_TV_OEM_LINK",
                Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS}) {
            Intent intent = SystemIntents.resolve(this, new Intent(action));
            if (intent == null) continue;
            try {
                startActivity(intent);
                if (action.equals(Settings.ACTION_SETTINGS))
                    Actions.message(this, "Find Accessibility in TV settings, then select Button Remapper.");
                return;
            } catch (RuntimeException ignored) { /* Try another supported settings route. */ }
        }
        Actions.message(this, "Find Accessibility in TV settings, then select Button Remapper.");
    }
}
