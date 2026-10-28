package com.tvhome.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Crossfades between two stacked ImageViews using JPEGs from assets/wallpapers. */
final class WallpaperRotator {
    /** Gets a heavily blurred copy of each new wallpaper (for frosted overlays). */
    interface BlurListener { void onBlurred(Bitmap blurred); }

    private static final String DIR = "wallpapers";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Context ctx;
    private final ImageView front, back;
    private final TextView caption;
    private final long intervalMs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> files = new ArrayList<>();
    private final Map<String, String> captions = new HashMap<>();
    private int index;
    private Bitmap shown;
    private boolean running;
    private BlurListener blurListener;

    void setBlurListener(BlurListener l) { blurListener = l; }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            next();
            main.postDelayed(this, intervalMs);
        }
    };

    WallpaperRotator(Context ctx, ImageView front, ImageView back, TextView caption, long intervalMs) {
        this.ctx = ctx.getApplicationContext();
        this.front = front;
        this.back = back;
        this.caption = caption;
        this.intervalMs = intervalMs;
        try {
            for (String f : ctx.getAssets().list(DIR)) if (f.endsWith(".jpg")) files.add(f);
            try (InputStream in = ctx.getAssets().open(DIR + "/credits.txt");
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int bar = line.indexOf('|');
                    if (bar > 0) captions.put(line.substring(0, bar), line.substring(bar + 1));
                }
            }
        } catch (Exception ignored) { }
        Collections.shuffle(files);
    }

    void start() {
        if (running || files.isEmpty()) return;
        running = true;
        if (shown == null) next();
        main.postDelayed(tick, intervalMs);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    /** Free the decoded wallpaper while the launcher is hidden; start() shows the next one. */
    void release() {
        front.animate().cancel();
        front.setImageDrawable(null);
        back.setImageDrawable(null);
        index = Math.max(0, index - 1); // come back to the same photo
        if (shown != null) { shown.recycle(); shown = null; }
    }

    private void next() {
        final String file = files.get(index++ % files.size());
        IO.execute(() -> {
            Bitmap bmp = decode(file);
            if (bmp == null) return;
            // Cheap "frosted glass": shrink to 64x36 and let bilinear upscaling blur it. Done once per wallpaper.
            Bitmap blurred = null;
            if (blurListener != null) {
                Bitmap tiny = Bitmap.createScaledBitmap(bmp, 64, 36, true);
                blurred = Bitmap.createScaledBitmap(tiny, 256, 144, true);
                tiny.recycle();
            }
            final Bitmap frost = blurred;
            main.post(() -> {
                show(bmp, captions.get(file));
                if (frost != null && blurListener != null) blurListener.onBlurred(frost);
            });
        });
    }

    private Bitmap decode(String file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565; // half the memory of ARGB_8888
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        o.inSampleSize = w > 0 && w <= 1280 ? 2 : 1;
        try (InputStream in = ctx.getAssets().open(DIR + "/" + file)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    private void show(Bitmap bmp, String text) {
        final Bitmap old = shown;
        shown = bmp;
        back.setImageBitmap(old);
        back.setAlpha(1f);
        front.setAlpha(0f);
        front.setImageBitmap(bmp);
        front.animate().alpha(1f).setDuration(old == null ? 400 : 1600).withEndAction(() -> {
            back.setImageDrawable(null);
            if (old != null && old != shown) old.recycle();
        }).start();
        if (caption != null) {
            caption.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                caption.setText(text == null ? "" : text);
                caption.animate().alpha(0.75f).setDuration(800).start();
            }).start();
        }
    }
}
