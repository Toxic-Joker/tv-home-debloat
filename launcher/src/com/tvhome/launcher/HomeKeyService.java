package com.tvhome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Opens the quick panel when Home is held. Google TV routes long-press Home to its own
 * (disabled) launcher, so we watch the key here. Short presses pass through untouched.
 * Also remaps remote buttons listed in the "keymap" pref (set by ConfigReceiver), e.g.
 * "247=app:org.smarttube.stable,262=panel,288=action:android.settings.SYNC_SETTINGS,211=allapps".
 */
public class HomeKeyService extends AccessibilityService {
    private static final long HOLD_MS = 550;
    static HomeKeyService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean opened;
    private SharedPreferences prefs;
    private final SparseArray<String> keymap = new SparseArray<>();
    private boolean debugKeys;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsChanged = (p, k) -> loadPrefs();
    private final Runnable openPanel = () -> {
        opened = true;
        startActivity(new Intent(this, QuickPanelActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    };

    @Override protected void onServiceConnected() {
        instance = this;
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefsChanged);
        loadPrefs();
    }

    private void loadPrefs() {
        keymap.clear();
        for (String entry : prefs.getString("keymap", "").split(",")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            try {
                keymap.put(Integer.parseInt(entry.substring(0, eq).trim()), entry.substring(eq + 1).trim());
            } catch (NumberFormatException ignored) { }
        }
        debugKeys = prefs.getBoolean("debug_keys", false);
    }

    private void run(String target) {
        try {
            Intent i = null;
            if (target.equals("panel")) i = new Intent(this, QuickPanelActivity.class);
            else if (target.equals("allapps"))
                i = new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_ALL_APPS, true);
            else if (target.startsWith("action:")) i = new Intent(target.substring(7));
            else if (target.startsWith("app:")) {
                String pkg = target.substring(4);
                i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w("TvHomeKey", "remap failed: " + target, e);
        }
    }

    @Override public boolean onUnbind(Intent intent) {
        instance = null;
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(prefsChanged);
        return super.onUnbind(intent);
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (debugKeys && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            Log.i("TvHomeKey", "keycode=" + e.getKeyCode() + " " + KeyEvent.keyCodeToString(e.getKeyCode())
                    + " scan=" + e.getScanCode() + " device=" + e.getDevice());
        }
        String target = keymap.get(e.getKeyCode());
        if (target != null) { // remapped button: act once per press, swallow every event of it
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) main.post(() -> run(target));
            return true;
        }
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            opened = false;
            main.postDelayed(openPanel, HOLD_MS);
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            main.removeCallbacks(openPanel);
            return opened; // swallow the release after a hold so we don't also go Home
        }
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }
}
