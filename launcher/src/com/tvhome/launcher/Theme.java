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

    static int shade(int color, float f) {
        return Color.rgb((int) (Color.red(color) * f), (int) (Color.green(color) * f), (int) (Color.blue(color) * f));
    }
}
