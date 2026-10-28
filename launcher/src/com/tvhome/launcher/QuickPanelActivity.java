package com.tvhome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Quick settings panel shown over the current app when Home is held. */
public class QuickPanelActivity extends Activity {
    private static final float PANEL_W = 448, TILE_W = 182, TILE_H = 70, RADIUS = 22;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        // Soft fade over the app so the panel stands out
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { 0x00000000, 0x66000000, 0xB3000000 }));
        root.setOnClickListener(v -> finish());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        int pad = Ui.dp(this, 28);
        panel.setPadding(pad, Ui.dp(this, 36), pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF7111317); // near-opaque so the app underneath doesn't show through
        bg.setStroke(Math.max(1, Ui.dp(this, 1)), Theme.GLASS_EDGE);
        float r = Ui.dp(this, 28);
        bg.setCornerRadii(new float[] { r, r, 0, 0, 0, 0, r, r });
        panel.setBackground(bg);
        Ui.noClip(panel);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        Ui.style(clock, 44, 0.97f, true);
        panel.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        Ui.style(date, 15, 0.7f, false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(this, 6);
        dlp.bottomMargin = Ui.dp(this, 24);
        panel.addView(date, dlp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        Ui.noClip(grid);
        panel.addView(grid);

        View first = add(grid, R.drawable.ic_wifi, getString(R.string.network), networkStatus(),
                () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        // Explicit screen: on some TVs several activities claim SOUND_SETTINGS and a chooser pops up
        add(grid, R.drawable.ic_display, getString(R.string.display_sound), null,
                () -> open(new Intent().setClassName("com.android.tv.settings",
                        "com.android.tv.settings.device.displaysound.DisplaySoundActivity")));
        final Intent picture = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        if (picture.resolveActivity(getPackageManager()) != null) { // TVs with picture settings, not sticks
            add(grid, R.drawable.ic_picture, getString(R.string.picture), null, () -> open(picture));
        }
        add(grid, R.drawable.ic_storage, getString(R.string.storage), storageStatus(),
                () -> open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        add(grid, R.drawable.ic_apps, getString(R.string.apps), null,
                () -> open(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        add(grid, R.drawable.ic_landscape, getString(R.string.screensaver), getString(R.string.start_now),
                () -> open(new Intent("com.google.android.pano.action.SLEEP"))); // starts the screensaver
        if (HomeKeyService.instance != null) {
            add(grid, R.drawable.ic_power, getString(R.string.power), getString(R.string.sleep_restart), () -> {
                finish();
                HomeKeyService.instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
            });
        }
        // Home style switch (Cinema / Glass), for comparing the two looks
        final boolean glassNow = Theme.STYLE_GLASS.equals(Theme.style(this));
        add(grid, R.drawable.ic_picture, getString(R.string.style_label),
                getString(glassNow ? R.string.style_glass : R.string.style_cinema), () -> {
                    getSharedPreferences("home", MODE_PRIVATE).edit()
                            .putString("style", glassNow ? Theme.STYLE_CINEMA : Theme.STYLE_GLASS).apply();
                    finish();
                });
        View all = add(grid, R.drawable.ic_settings, getString(R.string.all_settings), null,
                () -> open(new Intent(Settings.ACTION_SETTINGS)));
        if (grid.getChildCount() % 2 == 1) { // last tile spans the row when the count is odd
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) all.getLayoutParams();
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2);
            lp.width = Ui.dp(this, 2 * TILE_W + 12);
            all.setLayoutParams(lp);
        }

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(this, PANEL_W), -1, Gravity.END);
        root.addView(panel, plp);
        setContentView(root);

        panel.setTranslationX(Ui.dp(this, PANEL_W));
        panel.animate().translationX(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(180).start();
        first.requestFocus();
    }

    @Override protected void onPause() {
        super.onPause();
        finish(); // panel is transient: gone as soon as anything covers it
    }

    private String networkStatus() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities c = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (c == null) return getString(R.string.not_connected);
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return getString(R.string.ethernet);
        return c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? getString(R.string.wifi_connected) : getString(R.string.connected);
    }

    private String storageStatus() {
        android.os.StatFs fs = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
        return String.format(java.util.Locale.getDefault(), getString(R.string.gb_free), fs.getAvailableBytes() / 1e9);
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.util.Log.w("TvHomePanel", "open failed: " + i, e);
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    private View add(GridLayout grid, int icon, String title, String subtitle, Runnable action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 14);
        t.setPadding(p, 0, Ui.dp(this, 10), 0);
        t.setFocusable(true);
        t.setClickable(true);
        final float r = Ui.dp(this, RADIUS);
        t.setBackground(Theme.glass(this, RADIUS, 0x14FFFFFF));

        final ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        int s = Ui.dp(this, 24);
        t.addView(iv, new LinearLayout.LayoutParams(s, s));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        final TextView name = new TextView(this);
        name.setText(title);
        name.setSingleLine(true);
        Ui.style(name, 14, 1f, false);
        name.setShadowLayer(0, 0, 0, 0);
        text.addView(name);
        final TextView sub = subtitle == null ? null : new TextView(this);
        if (sub != null) {
            sub.setText(subtitle);
            Ui.style(sub, 11, 0.6f, false);
            sub.setShadowLayer(0, 0, 0, 0);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(this, 3);
            text.addView(sub, slp);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(this, 12);
        t.addView(text, tlp);

        t.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? Ui.rounded(0xF2F5F5F7, r) : Theme.glass(this, RADIUS, 0x14FFFFFF));
            int fg = has ? 0xFF111111 : 0xFFFFFFFF;
            name.setTextColor(fg);
            if (sub != null) sub.setTextColor(fg);
            iv.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(140).start();
        });
        t.setOnClickListener(v -> action.run());

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = Ui.dp(this, TILE_W);
        lp.height = Ui.dp(this, TILE_H);
        int g = Ui.dp(this, 6);
        lp.setMargins(g, g, g, g);
        grid.addView(t, lp);
        return t;
    }
}
