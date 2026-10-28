package com.tvhome.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import android.view.animation.DecelerateInterpolator;
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

/**
 * Minimal Apple TV-style home: clock, rotating wallpaper, app dock, and an "All apps" library
 * that slides over the home screen. Two looks share this code (pref "style"):
 *   cinema — wide artwork tiles with labels and a large title for the focused app
 *   glass  — glossy square tiles with a coloured floor reflection, frosted app library
 * Performance rules: no elevation shadows, no live blur, one shared glow view, no full re-renders on focus.
 */
public class HomeActivity extends Activity {
    static final String EXTRA_ALL_APPS = "all_apps";
    private static final int GRID_COLUMNS = 5;

    static final class App {
        String pkg, label, category;
        Drawable banner, icon;
        int accent, edge;
        Theme.Logo logo; // cropped banner logo for glossy square tiles (dock apps only)
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, App> apps = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsChanged = (p, key) -> {
        if ("dock".equals(key)) { if (this.glass) loadApps(); else renderApps(); } // glass crops logos for dock apps only
        if ("hidden".equals(key)) loadApps();
        if ("style".equals(key)) recreate();
    };
    private WallpaperRotator rotator;
    private boolean glass, shelf;
    private FrameLayout shelfArt;
    private ScrollView shelfScroll;

    private FrameLayout backdrop, homeLayer, library;
    private LinearLayout dockRow;
    private View dockGlow;
    private GridLayout grid;
    private ScrollView gridScroll;
    private ImageView frost;
    private TextView heroTitle, heroSub, greeting, libraryCount;
    private int lastDockIndex;
    private boolean firstRender = true;
    private boolean wantAllApps; // opened by the remote's apps button before the grid was built

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
        prefs.registerOnSharedPreferenceChangeListener(prefsChanged);
        glass = Theme.STYLE_GLASS.equals(Theme.style(this));
        shelf = Theme.STYLE_SHELF.equals(Theme.style(this));
        if (shelf) buildShelfUi(); else buildUi();
        rotator = new WallpaperRotator(this, (ImageView) findViewById(R.id.wp_front),
                (ImageView) findViewById(R.id.wp_back), (TextView) findViewById(R.id.caption),
                getResources().getInteger(R.integer.wallpaper_minutes) * 60_000L);
        if (!shelf) rotator.setBlurListener(b -> {
            BitmapDrawable d = new BitmapDrawable(getResources(), b);
            d.setFilterBitmap(true);
            frost.setImageDrawable(d);
        });

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
        loadApps();
        wantAllApps = getIntent().getBooleanExtra(EXTRA_ALL_APPS, false);
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsChanged);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        ensureKeyService();
        rotator.start();
        main.post(greetingTick);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getCurrentFocus() == null) backToDock();
    }

    /**
     * Android drops an accessibility service from the enabled list whenever its app is force-stopped
     * (Settings > Force stop, memory cleaners, updates). Without it the remote remaps and hold-Home
     * stop working, so turn it back on each time the home screen shows (needs WRITE_SECURE_SETTINGS,
     * granted by set-launcher.sh).
     */
    private void ensureKeyService() {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return;
        String svc = getPackageName() + "/" + HomeKeyService.class.getName();
        try {
            String cur = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
            if (cur != null && cur.contains(svc)) return;
            Settings.Secure.putString(getContentResolver(), "enabled_accessibility_services",
                    cur == null || cur.isEmpty() ? svc : cur + ":" + svc);
            Settings.Secure.putInt(getContentResolver(), "accessibility_enabled", 1);
        } catch (Exception e) {
            android.util.Log.w("TvHome", "could not re-enable key service", e);
        }
    }

    /** Launcher hidden behind an app: give back the wallpaper bitmaps and GPU textures. */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            rotator.release();
            frost.setImageDrawable(null);
        }
    }

    @Override protected void onPause() {
        rotator.stop();
        main.removeCallbacks(greetingTick);
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_ALL_APPS, false)) openLibrary();
        else backToDock(); // Home pressed while already home
    }

    @Override public void onBackPressed() {
        backToDock(); // never leave the launcher
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            View f = getCurrentFocus();
            boolean inLibrary = library.getVisibility() == View.VISIBLE;
            if (!shelf && !inLibrary && k == KeyEvent.KEYCODE_DPAD_DOWN && f != null && f.getParent() != null
                    && isInDock(f)) { openLibrary(); return true; }
            if (inLibrary && k == KeyEvent.KEYCODE_DPAD_UP && f != null && grid.indexOfChild(f) >= 0
                    && grid.indexOfChild(f) < GRID_COLUMNS) { backToDock(); return true; }
            if (k == KeyEvent.KEYCODE_MENU && f != null && f.getTag() instanceof App) {
                showOptions((App) f.getTag()); return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private boolean isInDock(View v) {
        for (View p = v; p != null && p.getParent() instanceof View; p = (View) p.getParent()) {
            if (p == dockRow) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        backdrop = new FrameLayout(this); // wallpaper + scrims; hidden while the opaque library covers it
        ImageView back = Ui.fullImage(this), front = Ui.fullImage(this);
        back.setId(R.id.wp_back);
        front.setId(R.id.wp_front);
        backdrop.addView(back);
        backdrop.addView(front);
        // Dark at the top (clock) and bottom (dock); the photo breathes in the middle
        backdrop.addView(Ui.scrim(this, 0xB3090A0D, 0xF2090A0D));
        if (!glass) { // cinema: extra shade on the left behind the big app title
            View side = new View(this);
            side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[] { 0xA6090A0D, 0x00090A0D }));
            backdrop.addView(side, new FrameLayout.LayoutParams(Ui.dp(this, 560), -1));
        }
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        homeLayer = new FrameLayout(this);
        Ui.noClip(homeLayer);
        root.addView(homeLayer, new FrameLayout.LayoutParams(-1, -1));
        buildHeader(homeLayer);
        // Shared focus glow, drawn behind the dock and moved to the focused tile (never affects layout)
        dockGlow = new View(this);
        dockGlow.setAlpha(0f);
        dockGlow.setLayerType(View.LAYER_TYPE_HARDWARE, null); // gradient drawn once, then only moved
        homeLayer.addView(dockGlow, new FrameLayout.LayoutParams(Ui.dp(this, 300), Ui.dp(this, 300)));
        if (glass) buildGlassDock(homeLayer); else buildCinemaDock(homeLayer);

        TextView caption = new TextView(this);
        caption.setId(R.id.caption);
        Ui.style(caption, 12, 0.6f, false);
        caption.setShadowLayer(0, 0, 0, 0);
        FrameLayout.LayoutParams cap = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        cap.setMargins(0, 0, Ui.dp(this, 56), Ui.dp(this, 20));
        homeLayer.addView(caption, cap);

        buildLibrary(root);
        setContentView(root);
    }

    /** HyperOS-like header: big thin clock, date and greeting beside it, glass settings button. */
    private void buildHeader(FrameLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        Ui.style(clock, 44, 0.97f, true);
        clock.setTextColor(Theme.TEXT);
        row.addView(clock);

        View bar = new View(this);
        bar.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(this, 1)), Ui.dp(this, 34));
        blp.leftMargin = blp.rightMargin = Ui.dp(this, 16);
        row.addView(bar, blp);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 320), -2));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        Ui.style(date, 14, 1f, false);
        date.setTextColor(Theme.TEXT);
        greeting = new TextView(this);
        Ui.style(greeting, 13, 1f, false);
        greeting.setTextColor(Theme.TEXT_2);
        meta.addView(date, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = Ui.dp(this, 3);
        meta.addView(greeting, glp);
        row.addView(meta);

        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        rlp.setMargins(Ui.dp(this, 56), Ui.dp(this, 36), 0, 0);
        parent.addView(row, rlp);

        // Round glass button: opens the quick panel (same as holding Home)
        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        int p = Ui.dp(this, 11);
        gear.setPadding(p, p, p, p);
        gear.setFocusable(true);
        gear.setClickable(true);
        gear.setBackground(roundGlass(false));
        gear.setOnFocusChangeListener((v, has) -> {
            v.setBackground(roundGlass(has));
            ((ImageView) v).setColorFilter(has ? 0xFF111111 : Theme.TEXT);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(Theme.FOCUS_MS).start();
        });
        gear.setOnClickListener(v -> launch(new Intent(this, QuickPanelActivity.class)));
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.TOP | Gravity.END);
        gp.setMargins(0, Ui.dp(this, 40), Ui.dp(this, 56), 0);
        parent.addView(gear, gp);
    }

    private GradientDrawable roundGlass(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(focused ? 0xF2F5F5F7 : Theme.GLASS_FILL);
        g.setStroke(Math.max(1, Ui.dp(this, 1)), focused ? 0 : Theme.GLASS_EDGE);
        return g;
    }

    /** Cinema: large title of the focused app above a row of wide artwork tiles. */
    private void buildCinemaDock(FrameLayout parent) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        heroSub = new TextView(this);
        Ui.style(heroSub, 13, 1f, false);
        heroSub.setTextColor(Theme.ACCENT);
        heroSub.setLetterSpacing(0.12f);
        heroSub.setAllCaps(true);
        heroTitle = new TextView(this);
        Ui.style(heroTitle, 42, 1f, true);
        heroTitle.setTextColor(Theme.TEXT);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroSub);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(this, 6);
        hero.addView(heroTitle, tlp);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(Ui.dp(this, 700), -2, Gravity.BOTTOM | Gravity.START);
        hlp.setMargins(Ui.dp(this, 56), 0, 0, Ui.dp(this, 236));
        parent.addView(hero, hlp);

        parent.addView(dockStrip(false), dockParams(false));
    }

    /** Glass: centred glossy squares with a coloured reflection underneath and the name below. */
    private void buildGlassDock(FrameLayout parent) {
        heroTitle = new TextView(this);
        Ui.style(heroTitle, 18, 1f, false);
        heroTitle.setTextColor(Theme.TEXT);
        heroTitle.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.bottomMargin = Ui.dp(this, 36);
        parent.addView(heroTitle, lp);
        parent.addView(dockStrip(true), dockParams(true));
    }

    /** Dock = horizontal scroller holding [shared glow view, row of tiles]. */
    private View dockStrip(boolean glassStyle) {
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        Ui.noClip(sv);
        FrameLayout content = new FrameLayout(this);
        Ui.noClip(content);
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(dockRow);
        int pad = Ui.dp(this, 24);
        dockRow.setPadding(pad, pad, pad, glassStyle ? Ui.dp(this, 40) : pad);
        content.addView(dockRow, new FrameLayout.LayoutParams(-2, -2));
        sv.addView(content);
        return sv;
    }

    private FrameLayout.LayoutParams dockParams(boolean glassStyle) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | (glassStyle ? Gravity.CENTER_HORIZONTAL : Gravity.START));
        lp.leftMargin = glassStyle ? 0 : Ui.dp(this, 32);
        lp.bottomMargin = Ui.dp(this, glassStyle ? 52 : 40);
        return lp;
    }

    /** All apps: hidden overlay with the blurred wallpaper behind it; fades/slides in from the dock. */
    private void buildLibrary(FrameLayout root) {
        library = new FrameLayout(this);
        library.setVisibility(View.GONE);
        library.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        frost = Ui.fullImage(this);
        frost.setScaleType(ImageView.ScaleType.FIT_XY);
        library.addView(frost);
        View tint = new View(this);
        tint.setBackgroundColor(glass ? 0x8C090A0D : 0xCC090A0D);
        library.addView(tint, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL); // clips children, so the grid stops above the hint
        TextView title = new TextView(this);
        title.setText(getString(R.string.library_title));
        Ui.style(title, glass ? 30 : 34, 1f, !glass);
        title.setTextColor(Theme.TEXT);
        title.setShadowLayer(0, 0, 0, 0);
        libraryCount = new TextView(this);
        Ui.style(libraryCount, 13, 1f, false);
        libraryCount.setTextColor(Theme.TEXT_2);
        libraryCount.setShadowLayer(0, 0, 0, 0);
        int gravity = glass ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.gravity = gravity;
        col.addView(title, tlp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.gravity = gravity;
        clp.topMargin = Ui.dp(this, 4);
        clp.bottomMargin = Ui.dp(this, 14);
        col.addView(libraryCount, clp);

        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);
        gridScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        gridScroll.setFocusable(false);
        gridScroll.setClipToPadding(false); // clip rows at the scroll area, but leave room for focus scaling
        grid = new GridLayout(this);
        grid.setColumnCount(GRID_COLUMNS);
        Ui.noClip(grid);
        int gp = Ui.dp(this, 14);
        grid.setPadding(gp, gp, gp, Ui.dp(this, 40));
        ScrollView.LayoutParams sgl = new ScrollView.LayoutParams(-2, -2);
        sgl.gravity = Gravity.CENTER_HORIZONTAL;
        gridScroll.addView(grid, sgl);
        col.addView(gridScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.library_hint));
        Ui.style(hint, 12, 1f, false);
        hint.setTextColor(Theme.TEXT_2);
        hint.setShadowLayer(0, 0, 0, 0);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = Ui.dp(this, 8);
        col.addView(hint, hlp);

        FrameLayout.LayoutParams lp;
        if (glass) { // frosted panel floating over the blurred wallpaper
            col.setBackground(Theme.glass(this, 32, 0x59141619));
            int pad = Ui.dp(this, 28);
            col.setPadding(pad, pad, pad, Ui.dp(this, 18));
            lp = new FrameLayout.LayoutParams(Ui.dp(this, 820), -1, Gravity.CENTER_HORIZONTAL);
            lp.setMargins(0, Ui.dp(this, 36), 0, Ui.dp(this, 36));
        } else {
            col.setPadding(Ui.dp(this, 56), Ui.dp(this, 40), Ui.dp(this, 56), Ui.dp(this, 20));
            lp = new FrameLayout.LayoutParams(-1, -1);
        }
        library.addView(col, lp);
        root.addView(library, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openLibrary() {
        if (grid.getChildCount() == 0) { wantAllApps = true; return; }
        if (shelf) { grid.getChildAt(0).requestFocus(); return; } // Top Shelf: All apps is the grid below
        if (library.getVisibility() != View.VISIBLE) {
            library.setVisibility(View.VISIBLE);
            library.setAlpha(0f);
            library.setTranslationY(Ui.dp(this, 24));
            // Only the library animates (as one cached layer); once it's opaque, stop drawing what's under it
            library.animate().alpha(1f).translationY(0).setDuration(Theme.SCREEN_MS)
                    .setInterpolator(new DecelerateInterpolator()).withLayer()
                    .withEndAction(() -> {
                        if (library.getVisibility() != View.VISIBLE) return;
                        homeLayer.setVisibility(View.INVISIBLE);
                        backdrop.setVisibility(View.INVISIBLE);
                    }).start();
        }
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        library.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        gridScroll.scrollTo(0, 0);
        grid.getChildAt(0).requestFocus();
    }

    private void closeLibrary() {
        if (shelf) { shelfScroll.smoothScrollTo(0, 0); return; }
        if (library.getVisibility() != View.VISIBLE) return;
        library.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        homeLayer.setVisibility(View.VISIBLE);
        backdrop.setVisibility(View.VISIBLE);
        library.animate().alpha(0f).translationY(Ui.dp(this, 24)).setDuration(Theme.SCREEN_MS).withLayer()
                .withEndAction(() -> library.setVisibility(View.GONE)).start();
    }

    private void backToDock() {
        closeLibrary();
        View v = dockRow.getChildAt(Math.min(lastDockIndex, dockRow.getChildCount() - 1));
        if (v != null) dockTileOf(v).requestFocus();
    }

    private View dockTileOf(View cell) { // cells wrap the focusable tile (label column, shadow frame)
        if (cell.getTag() instanceof App || !(cell instanceof ViewGroup)) return cell;
        ViewGroup g = (ViewGroup) cell;
        for (int i = g.getChildCount() - 1; i >= 0; i--) {
            View c = dockTileOf(g.getChildAt(i));
            if (c.getTag() instanceof App) return c;
        }
        return cell;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? getString(R.string.good_night) : h < 12 ? getString(R.string.good_morning) : h < 18 ? getString(R.string.good_afternoon) : getString(R.string.good_evening));
    }

    // ---------------------------------------------------------------- Apps

    private void loadApps() {
        io.execute(() -> {
            PackageManager pm = getPackageManager();
            Map<String, App> found = new LinkedHashMap<>();
            List<String> hidden = Arrays.asList(TextUtils.split(prefs.getString("hidden", ""), ","));
            List<String> dockSet = dock();
            for (String cat : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai.packageName.equals(getPackageName()) || found.containsKey(ai.packageName)
                            || hidden.contains(ai.packageName)) continue;
                    App a = new App();
                    a.pkg = ai.packageName;
                    a.label = String.valueOf(ri.loadLabel(pm));
                    a.banner = ai.loadBanner(pm);
                    if (a.banner == null) a.banner = ai.applicationInfo.loadBanner(pm);
                    a.icon = prerender(ri.loadIcon(pm), Ui.dp(this, 64));
                    Drawable logo = a.banner != null ? a.banner : a.icon;
                    a.accent = Theme.accentOf(logo); // once per app, off the UI thread
                    a.edge = a.banner != null ? Theme.edgeOf(a.banner) : Theme.shade(a.accent, 0.35f);
                    a.category = categoryName(ai.applicationInfo.category);
                    if (a.banner != null && !shelf && (!glass || dockSet.contains(a.pkg))) a.logo = Theme.logoOf(a.banner);
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

    /** Rasterise an icon once in the background so the first draw on screen is a plain bitmap. */
    private Drawable prerender(Drawable d, int px) {
        if (d == null || d instanceof BitmapDrawable) return d;
        BitmapDrawable b = new BitmapDrawable(getResources(), Theme.render(d, px, px));
        b.setFilterBitmap(true);
        return b;
    }

    private String categoryName(int c) {
        switch (c) {
            case ApplicationInfo.CATEGORY_VIDEO: return getString(R.string.cat_video);
            case ApplicationInfo.CATEGORY_AUDIO: return getString(R.string.cat_audio);
            case ApplicationInfo.CATEGORY_GAME: return getString(R.string.cat_game);
            case ApplicationInfo.CATEGORY_NEWS: return getString(R.string.cat_news);
            case ApplicationInfo.CATEGORY_IMAGE: return getString(R.string.cat_image);
            case ApplicationInfo.CATEGORY_SOCIAL: return getString(R.string.cat_social);
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return getString(R.string.cat_productivity);
            default: return "";
        }
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
        prefs.edit().putString("dock", TextUtils.join(",", list)).putString("dock_defaults", defaultDock()).apply(); // prefsChanged re-renders
    }

    private void renderApps() {
        View cur = getCurrentFocus();
        boolean inLibrary = library.getVisibility() == View.VISIBLE;
        // An empty dock (e.g. before this TV's dock was configured) never got focus, so claim it now
        boolean hadFocus = !inLibrary && (firstRender || dockRow.getChildCount() == 0 || dockRow.hasFocus() || cur == null);
        firstRender = false;

        dockRow.removeAllViews();
        int gap = Ui.dp(this, glass ? 28 : 20);
        for (String pkg : dock()) {
            App a = apps.get(pkg);
            if (a == null) continue;
            final int index = dockRow.getChildCount();
            View cell = shelf ? shelfTile(a, index) : glass ? glassDockTile(a, index) : cinemaDockTile(a, index);
            LinearLayout.LayoutParams lp = cell.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? (LinearLayout.LayoutParams) cell.getLayoutParams() : new LinearLayout.LayoutParams(-2, -2);
            if (index > 0 && !shelf) lp.leftMargin = gap; // shelf tiles carry grid-matching margins
            dockRow.addView(cell, lp);
        }

        grid.removeAllViews();
        List<App> sorted = new ArrayList<>(apps.values());
        Collections.sort(sorted, (x, y) -> x.label.compareToIgnoreCase(y.label));
        List<String> docked = dock();
        for (App a : sorted) {
            if (shelf && docked.contains(a.pkg)) continue; // Top Shelf: favourites are the first row
            grid.addView(shelf ? shelfTile(a, -1) : glass ? glassGridCell(a) : cinemaGridCell(a));
        }
        libraryCount.setText(getResources().getQuantityString(R.plurals.app_count, sorted.size(), sorted.size()));

        if (wantAllApps) { wantAllApps = false; main.post(this::openLibrary); }
        else if (hadFocus) main.post(this::backToDock);
    }

    // ---------------------------------------------------------------- Tiles

    private static final float CINEMA_SIZE = 132, GLASS_SIZE = 150;

    /** Wide banner artwork; logo-only apps get their icon on a tinted card. */
    private FrameLayout artwork(App a, float wDp, float hDp, float radiusDp) {
        FrameLayout t = new FrameLayout(this);
        float r = Ui.dp(this, radiusDp);
        Ui.clipRounded(t, r);
        if (a.banner != null) {
            t.setBackground(Ui.rounded(a.edge, r));
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            t.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] { Theme.shade(a.accent, 0.45f), Theme.shade(a.accent, 0.18f) }));
            ((GradientDrawable) t.getBackground()).setCornerRadius(r);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            int s = Ui.dp(this, Math.min(wDp, hDp) * 0.5f);
            t.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }
        // Hairline edge so dark artwork still reads against the dark background
        View edge = new View(this);
        edge.setBackground(Theme.glass(this, radiusDp, 0));
        t.addView(edge, new FrameLayout.LayoutParams(-1, -1));
        return t;
    }

    /**
     * Glossy rounded square (Apple-style app icon): seamless two-tone background taken from the
     * banner's own edges, the logo cropped to fill it, a top gloss, bottom shade and hairline edge.
     * Everything is plain drawables: no blur, no elevation.
     */
    private FrameLayout glossyTile(App a, float s) {
        float rp = Ui.dp(this, s * 0.24f);
        FrameLayout t = new FrameLayout(this);
        Ui.clipRounded(t, rp);
        int top, bottom;
        if (a.logo != null) { top = a.logo.top; bottom = a.logo.bottom; }
        else { int base = Theme.shade(a.accent, 0.42f); top = lighten(base, 0.12f); bottom = Theme.shade(base, 0.75f); }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { top, bottom });
        bg.setCornerRadius(rp);
        t.setBackground(bg);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (a.logo != null) {
            BitmapDrawable d = new BitmapDrawable(getResources(), a.logo.art);
            d.setFilterBitmap(true);
            iv.setImageDrawable(d);
            int pad = Ui.dp(this, s * 0.14f);
            iv.setPadding(pad, pad, pad, pad);
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            iv.setImageDrawable(a.icon);
            int is = Ui.dp(this, s * 0.56f);
            t.addView(iv, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        }
        View gloss = new View(this); // light falling on the top half
        gloss.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x40FFFFFF, 0x14FFFFFF, 0x00FFFFFF }));
        t.addView(gloss, new FrameLayout.LayoutParams(-1, Ui.dp(this, s * 0.5f), Gravity.TOP));
        View base = new View(this); // soft shade towards the bottom edge
        base.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] { 0x33000000, 0x00000000 }));
        t.addView(base, new FrameLayout.LayoutParams(-1, Ui.dp(this, s * 0.35f), Gravity.BOTTOM));
        View edge = new View(this);
        GradientDrawable e = Ui.rounded(0, rp);
        e.setStroke(Math.max(1, Ui.dp(this, 1)), 0x47FFFFFF);
        edge.setBackground(e);
        t.addView(edge, new FrameLayout.LayoutParams(-1, -1));
        return t;
    }

    /** Static soft shadow under a tile (a flattened radial gradient). */
    private View contactShadow(FrameLayout parent, float s) {
        View shadow = new View(this);
        shadow.setBackground(Theme.glow(0xFF000000, Ui.dp(this, s * 0.55f)));
        shadow.setScaleY(0.22f);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Ui.dp(this, s * 1.1f), Ui.dp(this, s * 1.1f), Gravity.CENTER_HORIZONTAL);
        slp.topMargin = Ui.dp(this, s * 0.48f);
        parent.addView(shadow, slp);
        return shadow;
    }

    /** Cinema: glossy logo square with its name underneath; the big title and colour glow follow focus. */
    private View cinemaDockTile(App a, int index) {
        float s = CINEMA_SIZE;
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.noClip(cell);
        FrameLayout wrap = new FrameLayout(this);
        Ui.noClip(wrap);
        View shadow = contactShadow(wrap, s);
        FrameLayout t = glossyTile(a, s);
        wrap.addView(t, new FrameLayout.LayoutParams(Ui.dp(this, s), Ui.dp(this, s)));
        cell.addView(wrap, new LinearLayout.LayoutParams(Ui.dp(this, s), Ui.dp(this, s)));
        TextView name = label(a.label, 13);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0.55f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(this, s + 20), -2);
        nlp.topMargin = Ui.dp(this, 16);
        cell.addView(name, nlp);
        makeFocusable(t, a, 1.1f, has -> {
            name.animate().alpha(has ? 1f : 0.55f).translationY(has ? Ui.dp(this, 6) : 0).setDuration(Theme.FOCUS_MS).start();
            shadow.animate().scaleX(has ? 1.15f : 1f).alpha(has ? 0.7f : 1f).setDuration(Theme.FOCUS_MS).start();
            if (has) { lastDockIndex = index; focusHero(a); moveGlow(t, a, false); }
        });
        return cell;
    }

    private View glassDockTile(App a, int index) {
        float s = GLASS_SIZE;
        FrameLayout wrap = new FrameLayout(this); // [contact shadow, tile]
        Ui.noClip(wrap);
        View shadow = contactShadow(wrap, s);
        FrameLayout t = glossyTile(a, s);
        wrap.addView(t, new FrameLayout.LayoutParams(Ui.dp(this, s), Ui.dp(this, s)));
        wrap.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, s), Ui.dp(this, s)));
        makeFocusable(t, a, 1.1f, has -> {
            shadow.animate().scaleX(has ? 1.15f : 1f).alpha(has ? 0.7f : 1f).setDuration(Theme.FOCUS_MS).start();
            if (has) { lastDockIndex = index; heroTitle.setText(a.label); moveGlow(t, a, true); }
        });
        return wrap;
    }

    private View cinemaGridCell(App a) {
        float s = 104;
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.noClip(cell);
        FrameLayout t = glossyTile(a, s);
        cell.addView(t, new LinearLayout.LayoutParams(Ui.dp(this, s), Ui.dp(this, s)));
        TextView name = label(a.label, 12);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0.6f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(this, 124), -2);
        nlp.topMargin = Ui.dp(this, 10);
        cell.addView(name, nlp);
        makeFocusable(t, a, 1.1f, has -> name.setAlpha(has ? 1f : 0.6f));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12));
        cell.setLayoutParams(lp);
        return wrapForGrid(cell, t);
    }

    private View glassGridCell(App a) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.noClip(cell);
        FrameLayout box = new FrameLayout(this);
        box.setBackground(Theme.glass(this, 22, 0x0DFFFFFF));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(a.icon);
        int is = Ui.dp(this, 56);
        box.addView(iv, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        cell.addView(box, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 92)));
        TextView name = label(a.label, 13);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0.75f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(this, 128), -2);
        nlp.topMargin = Ui.dp(this, 10);
        cell.addView(name, nlp);
        makeFocusable(box, a, 1.1f, has -> {
            box.setBackground(Theme.glass(this, 22, has ? 0x40FFFFFF : 0x0DFFFFFF));
            name.setAlpha(has ? 1f : 0.75f);
        });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        cell.setLayoutParams(lp);
        return wrapForGrid(cell, box);
    }

    /** Grid children must be the focusable views themselves so row math (index / columns) works. */
    private View wrapForGrid(LinearLayout cell, View focusTarget) {
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setTag(focusTarget.getTag());
        focusTarget.setFocusable(false);
        focusTarget.setClickable(false);
        View.OnFocusChangeListener inner = focusTarget.getOnFocusChangeListener();
        cell.setOnFocusChangeListener((v, has) -> inner.onFocusChange(focusTarget, has));
        App a = (App) focusTarget.getTag();
        cell.setOnClickListener(v -> launchApp(a.pkg));
        cell.setOnLongClickListener(v -> { showOptions(a); return true; });
        return cell;
    }

    private interface FocusHook { void on(boolean has); }

    private void makeFocusable(View t, App a, float scale, FocusHook hook) {
        t.setTag(a);
        t.setFocusable(true);
        t.setClickable(true);
        t.setLongClickable(true);
        t.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).withLayer() // cached while zooming
                    .setDuration(Theme.FOCUS_MS).setInterpolator(new DecelerateInterpolator()).start();
            hook.on(has);
        });
        t.setOnClickListener(v -> launchApp(a.pkg));
        t.setOnLongClickListener(v -> { showOptions(a); return true; });
    }

    private TextView label(String text, float sp) {
        TextView name = new TextView(this);
        name.setText(text);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        Ui.style(name, sp, 1f, false);
        name.setTextColor(Theme.TEXT);
        name.setShadowLayer(0, 0, 0, 0);
        return name;
    }

    private void focusHero(App a) {
        heroTitle.setText(a.label);
        heroSub.setText(a.category);
        heroSub.setVisibility(a.category.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        heroTitle.setAlpha(0.4f);
        heroTitle.setTranslationX(Ui.dp(this, -8));
        heroTitle.animate().alpha(1f).translationX(0).setDuration(Theme.FOCUS_MS).start();
    }

    /** One glow view follows focus: behind the tile (cinema) or flattened under it as a reflection (glass). */
    private void moveGlow(View tile, App a, boolean reflection) {
        dockGlow.post(() -> placeGlow(tile, a, reflection)); // after layout, so screen positions are valid
    }

    private void placeGlow(View tile, App a, boolean reflection) {
        int size = dockGlow.getLayoutParams().width;
        dockGlow.setBackground(Theme.glow(a.accent, size / 2f));
        int[] at = new int[2], origin = new int[2]; // tile position relative to homeLayer
        tile.getLocationInWindow(at);
        homeLayer.getLocationInWindow(origin);
        float left = at[0] - origin[0] - (tile.getScaleX() - 1) * tile.getWidth() / 2f;
        float top = at[1] - origin[1] - (tile.getScaleY() - 1) * tile.getHeight() / 2f;
        float cx = left + tile.getWidth() / 2f;
        float cy = reflection ? top + tile.getHeight() + Ui.dp(this, 10) : top + tile.getHeight() / 2f;
        dockGlow.setScaleX(reflection ? 0.9f : 1.4f);
        dockGlow.setScaleY(reflection ? 0.3f : 0.95f);
        float x = cx - size / 2f, y = cy - size / 2f;
        if (dockGlow.getAlpha() == 0f) { dockGlow.setTranslationX(x); dockGlow.setTranslationY(y); }
        dockGlow.animate().translationX(x).translationY(y).alpha(reflection ? 1f : 0.85f)
                .setDuration(Theme.FOCUS_MS).setInterpolator(new DecelerateInterpolator()).start();
    }

    private static int lighten(int c, float f) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000 | ((int) (r + (255 - r) * f) << 16) | ((int) (g + (255 - g) * f) << 8) | (int) (b + (255 - b) * f);
    }


    // ---------------------------------------------------------------- Top Shelf (Apple TV-like)

    private static final float SHELF_W = 142, SHELF_H = 89, SHELF_TOP = 290;

    /** Wallpaper as a full-bleed "top shelf", fading into a single scrolling grid of 16:10 app tiles. */
    private void buildShelfUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        backdrop = new FrameLayout(this);
        shelfArt = new FrameLayout(this);
        ImageView back = Ui.fullImage(this), front = Ui.fullImage(this);
        back.setId(R.id.wp_back);
        front.setId(R.id.wp_front);
        shelfArt.addView(back);
        shelfArt.addView(front);
        View fade = new View(this); // shelf melts into the background
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x59090A0D, 0x00090A0D, 0xCC090A0D, 0xFF090A0D }));
        shelfArt.addView(fade, new FrameLayout.LayoutParams(-1, -1));
        backdrop.addView(shelfArt, new FrameLayout.LayoutParams(-1, Ui.dp(this, SHELF_TOP + 150)));
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        TextClock clock = new TextClock(this); // tvOS-style small clock, top right
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        Ui.style(clock, 20, 0.9f, false);
        clock.setTextColor(Theme.TEXT);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        clp.setMargins(0, Ui.dp(this, 30), Ui.dp(this, 56), 0);
        root.addView(clock, clp);
        greeting = new TextView(this); // kept for the shared greeting timer; not shown

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        heroSub = new TextView(this);
        Ui.style(heroSub, 13, 1f, false);
        heroSub.setTextColor(Theme.ACCENT);
        heroSub.setAllCaps(true);
        heroSub.setLetterSpacing(0.12f);
        heroTitle = new TextView(this);
        Ui.style(heroTitle, 44, 1f, false);
        heroTitle.setTextColor(Theme.TEXT);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        title.addView(heroSub);
        title.addView(heroTitle);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(Ui.dp(this, 700), -2, Gravity.TOP | Gravity.START);
        tlp.setMargins(Ui.dp(this, 64), Ui.dp(this, SHELF_TOP - 96), 0, 0);
        shelfArt.addView(title, tlp);

        shelfScroll = new ScrollView(this);
        shelfScroll.setVerticalScrollBarEnabled(false);
        shelfScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shelfScroll.setFocusable(false);
        shelfScroll.setClipToPadding(false);
        int shelfFade = Ui.dp(this, 120);
        shelfScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> { // shelf fades and drifts up as you browse
            shelfArt.setAlpha(Math.max(0f, 1f - y / (float) shelfFade));
            shelfArt.setTranslationY(-y * 0.35f);
        });
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(col);
        col.setPadding(Ui.dp(this, 52), Ui.dp(this, SHELF_TOP), Ui.dp(this, 52), Ui.dp(this, 120));
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(dockRow);
        col.addView(dockRow, new LinearLayout.LayoutParams(-2, -2));
        grid = new GridLayout(this);
        grid.setColumnCount(GRID_COLUMNS);
        Ui.noClip(grid);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.topMargin = Ui.dp(this, 8);
        col.addView(grid, glp);
        shelfScroll.addView(col);
        root.addView(shelfScroll, new FrameLayout.LayoutParams(-1, -1));

        homeLayer = new FrameLayout(this);          // shared fields the other styles use
        library = new FrameLayout(this);
        library.setVisibility(View.GONE);
        frost = new ImageView(this);
        libraryCount = new TextView(this);
        gridScroll = shelfScroll;
        dockGlow = new View(this);
        setContentView(root);
    }

    /** 16:10 rounded tile; name and a glossy sheen appear only on the focused tile (tvOS-like). */
    private View shelfTile(App a, int dockIndex) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(cell);
        FrameLayout t = artwork(a, SHELF_W, SHELF_H, 14);
        View sheen = new View(this);
        sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { 0x40FFFFFF, 0x00FFFFFF, 0x00FFFFFF }));
        sheen.setAlpha(0f);
        t.addView(sheen, new FrameLayout.LayoutParams(-1, -1));
        cell.addView(t, new LinearLayout.LayoutParams(Ui.dp(this, SHELF_W), Ui.dp(this, SHELF_H)));
        TextView name = label(a.label, 13);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(this, SHELF_W), -2);
        nlp.topMargin = Ui.dp(this, 12);
        cell.addView(name, nlp);
        makeFocusable(t, a, 1.14f, has -> {
            name.animate().alpha(has ? 1f : 0f).setDuration(Theme.FOCUS_MS).start();
            sheen.animate().alpha(has ? 1f : 0f).setDuration(Theme.FOCUS_MS).start();
            if (!has) return;
            heroTitle.setText(a.label);
            heroSub.setText(a.category);
            heroSub.setVisibility(a.category.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            if (dockIndex >= 0) { lastDockIndex = dockIndex; shelfScroll.smoothScrollTo(0, 0); }
        });
        int g = Ui.dp(this, 14);
        if (dockIndex >= 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(g, g, g, 0);
            cell.setLayoutParams(lp);
            return cell;
        }
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(g, g, g, 0);
        cell.setLayoutParams(lp);
        return wrapForGrid(cell, t);
    }

    // ---------------------------------------------------------------- Actions

    private void showOptions(final App a) {
        final List<String> d = dock();
        final int pos = d.indexOf(a.pkg);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (pos >= 0) {
            if (pos > 0) { labels.add(getString(R.string.move_left)); actions.add(() -> { Collections.swap(d, pos, pos - 1); lastDockIndex = pos - 1; saveDock(d); }); }
            if (pos < d.size() - 1) { labels.add(getString(R.string.move_right)); actions.add(() -> { Collections.swap(d, pos, pos + 1); lastDockIndex = pos + 1; saveDock(d); }); }
            labels.add(getString(R.string.remove_dock)); actions.add(() -> { d.remove(pos); saveDock(d); });
        } else {
            labels.add(getString(R.string.add_dock)); actions.add(() -> { d.add(a.pkg); lastDockIndex = d.size() - 1; saveDock(d); });
        }
        labels.add(getString(R.string.app_info));
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
