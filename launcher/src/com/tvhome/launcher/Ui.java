package com.tvhome.launcher;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Small view helpers so the launcher needs no XML layouts. */
final class Ui {
    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static ImageView fullImage(Context c) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        return v;
    }

    /** Full-screen vertical gradient: darker at the top and bottom for legible text. */
    static View scrim(Context c, int top, int bottom) {
        View v = new View(c);
        v.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { top, 0x00000000, 0x00000000, bottom }));
        v.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        return v;
    }

    static void style(TextView t, float sp, float alpha, boolean thin) {
        t.setTextColor(0xFFFFFFFF);
        t.setAlpha(alpha);
        t.setTextSize(sp);
        t.setTypeface(Typeface.create(thin ? "sans-serif-light" : "sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(12f, 0f, 2f, 0x99000000);
        t.setIncludeFontPadding(false);
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static void clipRounded(View v, final float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    static void noClip(ViewGroup g) {
        g.setClipChildren(false);
        g.setClipToPadding(false);
    }
}
