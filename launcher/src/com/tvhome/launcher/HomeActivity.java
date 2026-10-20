package com.tvhome.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal Apple TV-style home: clock, rotating nature wallpaper, app dock, all-apps grid. */
public class HomeActivity extends Activity {
    private static final int GRID_COLUMNS = 5;
    private static final float TILE_W = 160, TILE_H = 90, RADIUS = 14;

    static final class App {
        String pkg, label;
        Drawable banner, icon;
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, App> apps = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private WallpaperRotator rotator;

    private ScrollView scroller;
    private LinearLayout dockRow;
    private GridLayout grid;
    private TextView focusLabel, greeting, allTitle;
    private View gridShade;
    private int lastDockIndex;
    private boolean firstRender = true;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    private final Runnable greetingTick = new Runnable() {
        @Override public void run() {
            updateGreeting();
            main.postDelayed(this, 5 * 60_000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        buildUi();
        rotator = new WallpaperRotator(this, (ImageView) findViewById(R.id.wp_front),
                (ImageView) findViewById(R.id.wp_back), (TextView) findViewById(R.id.caption),
                getResources().getInteger(R.integer.wallpaper_minutes) * 60_000L);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
        loadApps();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        rotator.start();
        main.post(greetingTick);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View f = getCurrentFocus();
        if (hasFocus && (f == null || f == scroller)) backToDock();
    }

    @Override protected void onPause() {
        rotator.stop();
        main.removeCallbacks(greetingTick);
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        backToDock(); // Home pressed while already home
    }

    @Override public void onBackPressed() {
        backToDock(); // never leave the launcher
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View v = getCurrentFocus();
            if (v != null && v.getTag() instanceof App) { showOptions((App) v.getTag()); return true; }
        }
        return super.onKeyDown(keyCode, e);
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        int screenH = getResources().getDisplayMetrics().heightPixels;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        ImageView back = Ui.fullImage(this), front = Ui.fullImage(this);
        back.setId(R.id.wp_back);
        front.setId(R.id.wp_front);
        root.addView(back);
        root.addView(front);
        root.addView(Ui.scrim(this, 0x8C000000, 0xB3000000));
        gridShade = new View(this);
        gridShade.setBackgroundColor(0xCC000000);
        gridShade.setAlpha(0f);
        root.addView(gridShade, new FrameLayout.LayoutParams(-1, -1));

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFocusable(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setSmoothScrollingEnabled(true);
        scroller.setOnScrollChangeListener((v, x, y, ox, oy) ->
                gridShade.setAlpha(Math.min(1f, y / (float) Ui.dp(this, 160))));
        Ui.noClip(scroller);
        root.addView(scroller, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(col);
        scroller.addView(col, new ScrollView.LayoutParams(-1, -2));

        // Hero: clock / date / greeting top-left, settings top-right, photo caption bottom-right
        int dockSection = Ui.dp(this, 34 + 122 + 48); // label + dock + bottom margin
        FrameLayout hero = new FrameLayout(this);
        col.addView(hero, new LinearLayout.LayoutParams(-1, screenH - dockSection));

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        Ui.style(clock, 76, 0.97f, true);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        Ui.style(date, 20, 0.85f, false);
        greeting = new TextView(this);
        Ui.style(greeting, 16, 0.7f, false);
        clockBox.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        addWithTopMargin(clockBox, date, 6);
        addWithTopMargin(clockBox, greeting, 4);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.dp(this, 480), -2, Gravity.TOP | Gravity.START);
        clp.setMargins(Ui.dp(this, 56), Ui.dp(this, 40), 0, 0);
        hero.addView(clockBox, clp);

        TextView settings = pill("⚙  Settings");
        settings.setOnClickListener(v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        slp.setMargins(0, Ui.dp(this, 44), Ui.dp(this, 56), 0);
        hero.addView(settings, slp);

        TextView caption = new TextView(this);
        caption.setId(R.id.caption);
        Ui.style(caption, 13, 0.75f, false);
        FrameLayout.LayoutParams cap = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        cap.setMargins(0, 0, Ui.dp(this, 60), Ui.dp(this, 8));
        hero.addView(caption, cap);

        // Dock
        focusLabel = new TextView(this);
        Ui.style(focusLabel, 20, 0.95f, false);
        focusLabel.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 24));
        flp.setMargins(Ui.dp(this, 64), 0, 0, Ui.dp(this, 10));
        col.addView(focusLabel, flp);

        HorizontalScrollView dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        Ui.noClip(dockScroll);
        dockScroll.setBackground(Ui.rounded(0x33FFFFFF, Ui.dp(this, 26)));
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(dockRow);
        int pad = Ui.dp(this, 16);
        dockRow.setPadding(pad, pad, pad, pad);
        dockScroll.addView(dockRow);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.setMargins(Ui.dp(this, 48), 0, Ui.dp(this, 48), Ui.dp(this, 48));
        col.addView(dockScroll, dlp);

        // All apps
        allTitle = new TextView(this);
        allTitle.setText("All apps");
        Ui.style(allTitle, 20, 0.9f, false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.setMargins(Ui.dp(this, 64), Ui.dp(this, 8), 0, Ui.dp(this, 12));
        col.addView(allTitle, alp);

        grid = new GridLayout(this);
        grid.setColumnCount(GRID_COLUMNS);
        Ui.noClip(grid);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.setMargins(0, 0, 0, Ui.dp(this, 64));
        col.addView(grid, glp);

        setContentView(root);
    }

    private void addWithTopMargin(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, topDp);
        parent.addView(child, lp);
    }

    private TextView pill(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        Ui.style(t, 16, 1f, false);
        t.setShadowLayer(0, 0, 0, 0);
        int h = Ui.dp(this, 18), v = Ui.dp(this, 9);
        t.setPadding(h, v, h, v);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(Ui.rounded(0x33FFFFFF, Ui.dp(this, 20)));
        t.setOnFocusChangeListener((view, has) -> {
            view.setBackground(Ui.rounded(has ? 0xF2FFFFFF : 0x33FFFFFF, Ui.dp(this, 20)));
            ((TextView) view).setTextColor(has ? 0xFF111111 : 0xFFFFFFFF);
            view.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(150).start();
            if (has) scroller.smoothScrollTo(0, 0);
        });
        return t;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening");
    }

    /** Entering the grid from the dock: bring the whole "All apps" section into view. */
    private void revealGrid() {
        int target = allTitle.getTop() - Ui.dp(this, 24);
        if (scroller.getScrollY() < target) scroller.smoothScrollTo(0, target);
    }

    private void backToDock() {
        scroller.smoothScrollTo(0, 0);
        View v = dockRow.getChildAt(Math.min(lastDockIndex, dockRow.getChildCount() - 1));
        if (v != null) v.requestFocus();
    }

    // ---------------------------------------------------------------- Apps

    private void loadApps() {
        io.execute(() -> {
            PackageManager pm = getPackageManager();
            Map<String, App> found = new LinkedHashMap<>();
            for (String cat : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai.packageName.equals(getPackageName()) || found.containsKey(ai.packageName)) continue;
                    App a = new App();
                    a.pkg = ai.packageName;
                    a.label = String.valueOf(ri.loadLabel(pm));
                    a.banner = ai.loadBanner(pm);
                    if (a.banner == null) a.banner = ai.applicationInfo.loadBanner(pm);
                    a.icon = ri.loadIcon(pm);
                    found.put(a.pkg, a);
                }
            }
            main.post(() -> {
                apps.clear();
                apps.putAll(found);
                renderApps();
            });
        });
    }

    private String defaultDock() {
        return TextUtils.join(",", getResources().getStringArray(R.array.default_dock));
    }

    private List<String> dock() {
        // A saved dock only survives while config.xml's default dock is unchanged
        String saved = defaultDock().equals(prefs.getString("dock_defaults", null)) ? prefs.getString("dock", null) : null;
        List<String> list = saved != null
                ? new ArrayList<>(Arrays.asList(TextUtils.split(saved, ",")))
                : new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.default_dock)));
        list.removeIf(String::isEmpty);
        return list;
    }

    private void saveDock(List<String> list) {
        prefs.edit().putString("dock", TextUtils.join(",", list)).putString("dock_defaults", defaultDock()).apply();
        renderApps();
    }

    private void renderApps() {
        View cur = getCurrentFocus();
        boolean hadFocus = firstRender || dockRow.hasFocus() || cur == null || cur == scroller;
        firstRender = false;
        dockRow.removeAllViews();
        int gap = Ui.dp(this, 18);
        for (String pkg : dock()) {
            App a = apps.get(pkg);
            if (a == null) continue;
            final int index = dockRow.getChildCount();
            View t = tile(a, () -> { lastDockIndex = index; scroller.smoothScrollTo(0, 0); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, TILE_W), Ui.dp(this, TILE_H));
            if (index > 0) lp.leftMargin = gap;
            dockRow.addView(t, lp);
        }

        grid.removeAllViews();
        List<App> sorted = new ArrayList<>(apps.values());
        Collections.sort(sorted, (x, y) -> x.label.compareToIgnoreCase(y.label));
        int g = Ui.dp(this, 9);
        for (App a : sorted) {
            View t = tile(a, this::revealGrid);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.dp(this, TILE_W);
            lp.height = Ui.dp(this, TILE_H);
            lp.setMargins(g, g, g, g);
            grid.addView(t, lp);
        }
        if (hadFocus) main.post(this::backToDock);
    }

    private View tile(final App a, final Runnable onFocus) {
        FrameLayout t = new FrameLayout(this);
        t.setTag(a);
        t.setFocusable(true);
        t.setClickable(true);
        t.setLongClickable(true);
        float r = Ui.dp(this, RADIUS);
        t.setBackground(Ui.rounded(0x4DFFFFFF, r));
        Ui.clipRounded(t, r);

        if (a.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            t.setBackground(Ui.rounded(0xE6202226, r));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            int s = Ui.dp(this, 46);
            box.addView(iv, new LinearLayout.LayoutParams(s, s));
            TextView name = new TextView(this);
            name.setText(a.label);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            Ui.style(name, 12, 0.9f, false);
            name.setShadowLayer(0, 0, 0, 0);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
            nlp.topMargin = Ui.dp(this, 6);
            nlp.leftMargin = nlp.rightMargin = Ui.dp(this, 8);
            box.addView(name, nlp);
            t.addView(box, new FrameLayout.LayoutParams(-1, -1));
        }

        final View ring = new View(this);
        android.graphics.drawable.GradientDrawable stroke = Ui.rounded(0x00000000, r);
        stroke.setStroke(Ui.dp(this, 3), 0xFFFFFFFF);
        ring.setBackground(stroke);
        ring.setAlpha(0f);
        t.addView(ring, new FrameLayout.LayoutParams(-1, -1));

        t.setOnFocusChangeListener((v, has) -> {
            float sc = has ? 1.12f : 1f;
            v.animate().scaleX(sc).scaleY(sc).translationZ(has ? Ui.dp(this, 12) : 0).setDuration(160).start();
            ring.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            if (has) {
                focusLabel.setText(a.label);
                if (onFocus != null) onFocus.run();
            }
        });
        t.setOnClickListener(v -> launchApp(a.pkg));
        t.setOnLongClickListener(v -> { showOptions(a); return true; });
        return t;
    }

    private void showOptions(final App a) {
        final List<String> d = dock();
        final int pos = d.indexOf(a.pkg);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (pos >= 0) {
            if (pos > 0) { labels.add("Move left"); actions.add(() -> { Collections.swap(d, pos, pos - 1); lastDockIndex = pos - 1; saveDock(d); }); }
            if (pos < d.size() - 1) { labels.add("Move right"); actions.add(() -> { Collections.swap(d, pos, pos + 1); lastDockIndex = pos + 1; saveDock(d); }); }
            labels.add("Remove from dock"); actions.add(() -> { d.remove(pos); saveDock(d); });
        } else {
            labels.add("Add to dock"); actions.add(() -> { d.add(a.pkg); lastDockIndex = d.size() - 1; saveDock(d); });
        }
        labels.add("App info");
        actions.add(() -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", a.pkg, null))));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(labels.toArray(new String[0]), (dlg, which) -> actions.get(which).run())
                .show();
    }

    private void launchApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) launch(i);
    }

    private void launch(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }
}
