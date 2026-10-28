package com.tvhome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
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

/**
 * Control-centre style panel shown over the current app when Home is held (or the gear button):
 * a row of round glass buttons for quick actions, then pill-shaped controls.
 */
public class QuickPanelActivity extends Activity {
    private static final float PANEL_W = 448, PILL_W = 186, PILL_H = 64, ROUND = 66;
    private static final int IDLE_FILL = 0x17FFFFFF, CIRCLE_FILL = 0x1FFFFFFF;

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
        int pad = Ui.dp(this, 30);
        panel.setPadding(pad, Ui.dp(this, 34), pad, pad);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0xF71A1C21, 0xF70E1013 }); // near-opaque so the app underneath doesn't show through
        bg.setStroke(Math.max(1, Ui.dp(this, 1)), Theme.GLASS_EDGE);
        float r = Ui.dp(this, 32);
        bg.setCornerRadii(new float[] { r, r, 0, 0, 0, 0, r, r });
        panel.setBackground(bg);
        Ui.noClip(panel);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        Ui.style(clock, 42, 1f, true);
        clock.setTextColor(Theme.TEXT);
        clock.setShadowLayer(0, 0, 0, 0);
        panel.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        Ui.style(date, 14, 1f, false);
        date.setTextColor(Theme.TEXT_2);
        date.setShadowLayer(0, 0, 0, 0);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(this, 4);
        dlp.bottomMargin = Ui.dp(this, 26);
        panel.addView(date, dlp);

        // Round quick actions
        LinearLayout round = new LinearLayout(this);
        round.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(round);
        View first = roundButton(round, R.drawable.ic_wifi, networkStatus(),
                () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        roundButton(round, R.drawable.ic_landscape, getString(R.string.screensaver),
                () -> open(new Intent("com.google.android.pano.action.SLEEP"))); // starts the screensaver
        if (HomeKeyService.instance != null) {
            roundButton(round, R.drawable.ic_power, getString(R.string.power), () -> {
                finish();
                HomeKeyService.instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
            });
        }
        roundButton(round, R.drawable.ic_settings, getString(R.string.settings_short),
                () -> open(new Intent(Settings.ACTION_SETTINGS)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.bottomMargin = Ui.dp(this, 22);
        panel.addView(round, rlp);

        // Pill controls
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        Ui.noClip(grid);
        panel.addView(grid);
        // Explicit screen: on some TVs several activities claim SOUND_SETTINGS and a chooser pops up
        pill(grid, R.drawable.ic_display, getString(R.string.display_sound), null, -1,
                () -> open(new Intent().setClassName("com.android.tv.settings",
                        "com.android.tv.settings.device.displaysound.DisplaySoundActivity")));
        final Intent picture = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        if (picture.resolveActivity(getPackageManager()) != null) { // TVs with picture settings, not sticks
            pill(grid, R.drawable.ic_picture, getString(R.string.picture), null, -1, () -> open(picture));
        }
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        float used = 1f - fs.getAvailableBytes() / (float) fs.getTotalBytes();
        pill(grid, R.drawable.ic_storage, getString(R.string.storage), String.format(java.util.Locale.getDefault(),
                getString(R.string.gb_free), fs.getAvailableBytes() / 1e9), used,
                () -> open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        pill(grid, R.drawable.ic_apps, getString(R.string.apps), null, -1,
                () -> open(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        // Home style: cycles through the available looks
        final String style = Theme.style(this);
        final String next = Theme.nextStyle(style);
        pill(grid, R.drawable.ic_picture, getString(R.string.style_label), getString(Theme.styleName(style)), -1, () -> {
            getSharedPreferences("home", MODE_PRIVATE).edit().putString("style", next).apply();
            finish();
        });

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(this, PANEL_W), -1, Gravity.END);
        root.addView(panel, plp);
        setContentView(root);

        panel.setTranslationX(Ui.dp(this, PANEL_W));
        panel.animate().translationX(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).withLayer().start();
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

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.util.Log.w("TvHomePanel", "open failed: " + i, e);
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    private static GradientDrawable circle(int fill, int stroke, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        if (stroke != 0) g.setStroke(strokePx, stroke);
        return g;
    }

    /** Round glass button with its label underneath. */
    private View roundButton(LinearLayout row, int icon, String label, Runnable action) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.noClip(col);
        final ImageView b = new ImageView(this);
        b.setImageResource(icon);
        int p = Ui.dp(this, 20);
        b.setPadding(p, p, p, p);
        b.setColorFilter(Theme.TEXT, PorterDuff.Mode.SRC_IN);
        final int hair = Math.max(1, Ui.dp(this, 1));
        b.setBackground(circle(CIRCLE_FILL, Theme.GLASS_EDGE, hair));
        b.setFocusable(true);
        b.setClickable(true);
        col.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, ROUND), Ui.dp(this, ROUND)));
        final TextView t = new TextView(this);
        t.setText(label);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        Ui.style(t, 11, 1f, false);
        t.setTextColor(Theme.TEXT_2);
        t.setShadowLayer(0, 0, 0, 0);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(this, 92), -2);
        tlp.topMargin = Ui.dp(this, 8);
        col.addView(t, tlp);
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? circle(0xFFF5F5F7, 0, 0) : circle(CIRCLE_FILL, Theme.GLASS_EDGE, hair));
            ((ImageView) v).setColorFilter(has ? 0xFF111111 : Theme.TEXT, PorterDuff.Mode.SRC_IN);
            t.setTextColor(has ? Theme.TEXT : Theme.TEXT_2);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(Theme.FOCUS_MS).start();
        });
        b.setOnClickListener(v -> action.run());
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return b;
    }

    /** Pill control: icon in its own circle, title + subtitle, optional usage bar (0..1). */
    private View pill(GridLayout grid, int icon, String title, String subtitle, float bar, Runnable action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 14), 0);
        t.setFocusable(true);
        t.setClickable(true);
        final float r = Ui.dp(this, PILL_H / 2);
        t.setBackground(Theme.glass(this, PILL_H / 2, IDLE_FILL));

        final ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        int ip = Ui.dp(this, 10);
        iv.setPadding(ip, ip, ip, ip);
        iv.setColorFilter(Theme.TEXT, PorterDuff.Mode.SRC_IN);
        iv.setBackground(circle(CIRCLE_FILL, 0, 0));
        t.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        final TextView name = new TextView(this);
        name.setText(title);
        name.setSingleLine(true);
        Ui.style(name, 14, 1f, false);
        name.setTextColor(Theme.TEXT);
        name.setShadowLayer(0, 0, 0, 0);
        text.addView(name);
        final TextView sub = subtitle == null ? null : new TextView(this);
        if (sub != null) {
            sub.setText(subtitle);
            sub.setSingleLine(true);
            Ui.style(sub, 11, 1f, false);
            sub.setTextColor(Theme.TEXT_2);
            sub.setShadowLayer(0, 0, 0, 0);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(this, 2);
            text.addView(sub, slp);
        }
        final View fill = bar < 0 ? null : new View(this);
        if (fill != null) { // thin usage bar
            FrameLayout track = new FrameLayout(this);
            track.setBackground(Ui.rounded(0x26FFFFFF, Ui.dp(this, 2)));
            fill.setBackground(Ui.rounded(Theme.ACCENT, Ui.dp(this, 2)));
            int barW = Ui.dp(this, 100);
            track.addView(fill, new FrameLayout.LayoutParams((int) (barW * Math.min(1f, bar)), -1));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(barW, Ui.dp(this, 3));
            blp.topMargin = Ui.dp(this, 5);
            text.addView(track, blp);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(this, 10);
        t.addView(text, tlp);

        t.setOnFocusChangeListener((v, has) -> {
            if (has) { // bright pill with a faint accent wash at the bottom, like a lit control
                GradientDrawable lit = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] { 0xFFF5F5F7, 0xFFE3E8F0 });
                lit.setCornerRadius(r);
                v.setBackground(lit);
            } else {
                v.setBackground(Theme.glass(this, PILL_H / 2, IDLE_FILL));
            }
            int fg = has ? 0xFF111111 : Theme.TEXT;
            name.setTextColor(fg);
            if (sub != null) sub.setTextColor(has ? 0xFF4A4F58 : Theme.TEXT_2);
            iv.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
            iv.setBackground(circle(has ? 0x1A000000 : CIRCLE_FILL, 0, 0));
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(Theme.FOCUS_MS).start();
        });
        t.setOnClickListener(v -> action.run());

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = Ui.dp(this, PILL_W);
        lp.height = Ui.dp(this, PILL_H);
        int g = Ui.dp(this, 5);
        lp.setMargins(g, g, g, g);
        grid.addView(t, lp);
        return t;
    }
}
