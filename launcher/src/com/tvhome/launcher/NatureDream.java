package com.tvhome.launcher;

import android.content.Context;
import android.graphics.Color;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

/** Screensaver: nature wallpapers crossfading every minute, with a clock. */
public class NatureDream extends DreamService {
    private WallpaperRotator rotator;

    /** Builds the slideshow view; the rotator is stored as the view's tag. */
    static View build(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.BLACK);
        ImageView back = Ui.fullImage(ctx), front = Ui.fullImage(ctx);
        root.addView(back);
        root.addView(front);
        root.addView(Ui.scrim(ctx, 0x00000000, 0x99000000));

        TextClock clock = new TextClock(ctx);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        Ui.style(clock, 72, 0.95f, true);
        TextView caption = new TextView(ctx);
        Ui.style(caption, 18, 0.75f, false);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        lp.setMargins((int) (64 * d), 0, 0, (int) (72 * d));
        root.addView(clock, lp);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        cp.setMargins((int) (68 * d), 0, 0, (int) (44 * d));
        root.addView(caption, cp);

        root.setTag(new WallpaperRotator(ctx, front, back, caption, 60_000));
        return root;
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        View v = build(this);
        rotator = (WallpaperRotator) v.getTag();
        setContentView(v);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        rotator.start();
    }

    @Override public void onDreamingStopped() {
        rotator.stop();
        super.onDreamingStopped();
    }
}
