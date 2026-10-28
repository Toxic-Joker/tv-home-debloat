package com.tvhome.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/** Design tokens shared by both home styles, plus cheap colour helpers. */
final class Theme {
    private Theme() { }

    static final String STYLE_CINEMA = "cinema"; // wide artwork tiles, big app title
    static final String STYLE_GLASS = "glass";   // glossy square icons, frosted app library
    static final String STYLE_SHELF = "shelf";   // Apple TV-like: top shelf over a single app grid
    private static final String[] STYLES = { STYLE_CINEMA, STYLE_GLASS, STYLE_SHELF };

    static String nextStyle(String current) {
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(current)) return STYLES[(i + 1) % STYLES.length];
        return STYLES[0];
    }

    static int styleName(String style) {
        return STYLE_GLASS.equals(style) ? R.string.style_glass
                : STYLE_SHELF.equals(style) ? R.string.style_shelf : R.string.style_cinema;
    }

    static final int BG = 0xFF090A0D;
    static final int SURFACE = 0xFF14161B;
    static final int TEXT = 0xFFF5F5F7;
    static final int TEXT_2 = 0xFFA1A6B0;
    static final int ACCENT = 0xFF91A8CA;
    static final int GLASS_FILL = 0x1FFFFFFF;
    static final int GLASS_EDGE = 0x33FFFFFF;

    static final long FOCUS_MS = 200;
    static final long SCREEN_MS = 240;

    static String style(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE).getString("style", STYLE_CINEMA);
    }

    /** Rounded "glass" surface: faint fill and a hairline edge, no blur. */
    static GradientDrawable glass(Context c, float radiusDp, int fill) {
        GradientDrawable g = Ui.rounded(fill, Ui.dp(c, radiusDp));
        g.setStroke(Math.max(1, Ui.dp(c, 1)), GLASS_EDGE);
        return g;
    }

    /** Soft round glow; scale the view on Y to flatten it into a floor reflection. */
    static GradientDrawable glow(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(radiusPx);
        g.setColors(new int[] { (color & 0x00FFFFFF) | 0xE6000000, (color & 0x00FFFFFF) | 0x73000000, 0 });
        return g;
    }

    static Bitmap render(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    /** Most vivid colour of a logo, sampled from a tiny render (computed once per app). */
    static int accentOf(Drawable d) {
        if (d == null) return ACCENT;
        Bitmap b = render(d, 16, 9);
        float best = -1; int pick = ACCENT;
        float[] hsv = new float[3];
        for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            float score = hsv[1] * (0.3f + hsv[2]);
            if (score > best) { best = score; pick = p; }
        }
        b.recycle();
        if (best < 0.15f) return ACCENT; // grey/black/white logos: use the calm house accent
        Color.colorToHSV(pick, hsv);
        hsv[2] = Math.max(hsv[2], 0.7f);
        return Color.HSVToColor(hsv);
    }

    /** Colour at a banner's corner, so a wide banner blends into a square tile. */
    static int edgeOf(Drawable d) {
        if (d == null) return SURFACE;
        Bitmap b = render(d, 32, 18);
        int p = b.getPixel(1, 1);
        b.recycle();
        return Color.alpha(p) < 200 ? SURFACE : p | 0xFF000000;
    }

    /** A banner reduced to its logo: tight crop of the artwork plus its top/bottom background colours. */
    static final class Logo {
        Bitmap art;
        int top, bottom;
    }

    /** Crops away a banner's flat background so the logo can fill a square tile. Run off the UI thread. */
    static Logo logoOf(Drawable banner) {
        Bitmap b = render(banner, 320, 180);
        Logo l = new Logo();
        l.top = b.getPixel(160, 1) | 0xFF000000;
        l.bottom = b.getPixel(160, 178) | 0xFF000000;
        int bg = b.getPixel(2, 2);
        int minX = 320, minY = 180, maxX = -1, maxY = -1;
        for (int y = 0; y < 180; y += 2) for (int x = 0; x < 320; x += 2) {
            int p = b.getPixel(x, y);
            int edge = y < 90 ? l.top : l.bottom; // compare to the local background (handles gradients)
            if (distance(p, edge) > 60 && distance(p, bg) > 60) {
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0 || (maxX - minX) * (maxY - minY) < 400) { l.art = b; return l; } // nothing to trim
        int m = 10; // breathing room around the logo
        minX = Math.max(0, minX - m); minY = Math.max(0, minY - m);
        maxX = Math.min(319, maxX + m); maxY = Math.min(179, maxY + m);
        int w = maxX - minX + 1, h = maxY - minY + 1;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, minX, minY, w, h);
        b.recycle();
        // Key out the banner's own background (soft edge) so only the logo sits on the glossy tile
        for (int y = 0; y < h; y++) {
            float f = (minY + y) / 179f;
            int bgRow = Color.rgb((int) (Color.red(l.top) + (Color.red(l.bottom) - Color.red(l.top)) * f),
                    (int) (Color.green(l.top) + (Color.green(l.bottom) - Color.green(l.top)) * f),
                    (int) (Color.blue(l.top) + (Color.blue(l.bottom) - Color.blue(l.top)) * f));
            for (int x = 0; x < w; x++) {
                int p = px[y * w + x];
                float keep = Math.min(1f, Math.max(0f, (distance(p, bgRow) - 24) / 56f));
                px[y * w + x] = ((int) (Color.alpha(p) * keep) << 24) | (p & 0x00FFFFFF);
            }
        }
        l.art = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        return l;
    }

    private static int distance(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b))
                + Math.abs(Color.blue(a) - Color.blue(b));
    }

    static int shade(int color, float f) {
        return Color.rgb((int) (Color.red(color) * f), (int) (Color.green(color) * f), (int) (Color.blue(color) * f));
    }
}
