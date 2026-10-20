package com.tvhome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Opens the quick panel when Home is held. Google TV routes long-press Home to its own
 * (disabled) launcher, so we watch the key here. Short presses pass through untouched.
 */
public class HomeKeyService extends AccessibilityService {
    private static final long HOLD_MS = 550;
    static HomeKeyService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean opened;
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
    }

    @Override public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
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
