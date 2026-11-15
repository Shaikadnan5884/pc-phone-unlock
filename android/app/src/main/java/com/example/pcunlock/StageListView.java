package com.example.pcunlock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Draws the six stages and keeps them in sync with StageTracker. */
public class StageListView extends LinearLayout implements StageTracker.Listener {
    private final TextView[] dot = new TextView[6];
    private final TextView[] title = new TextView[6];
    private final TextView[] info = new TextView[6];
    private final TextView[] clock = new TextView[6];
    private final ProgressBar[] spin = new ProgressBar[6];

    public StageListView(Context c) { super(c); init(); }
    public StageListView(Context c, AttributeSet a) { super(c, a); init(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void init() {
        setOrientation(VERTICAL);
        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            FrameLayoutLike left = new FrameLayoutLike(getContext());
            dot[i] = new TextView(getContext());
            dot[i].setGravity(Gravity.CENTER);
            dot[i].setTextColor(Color.WHITE);
            dot[i].setTypeface(Typeface.DEFAULT_BOLD);
            dot[i].setLayoutParams(new LayoutParams(dp(32), dp(32)));
            spin[i] = new ProgressBar(getContext());
            spin[i].setIndeterminate(true);
            spin[i].setLayoutParams(new LayoutParams(dp(32), dp(32)));
            left.addView(dot[i]);
            left.addView(spin[i]);
            row.addView(left, new LayoutParams(dp(32), dp(32)));

            LinearLayout mid = new LinearLayout(getContext());
            mid.setOrientation(VERTICAL);
            LayoutParams mp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            mp.leftMargin = dp(14);
            title[i] = new TextView(getContext());
            title[i].setTextSize(16);
            title[i].setTypeface(Typeface.DEFAULT_BOLD);
            title[i].setText((i + 1) + ". " + StageTracker.TITLES[i]);
            info[i] = new TextView(getContext());
            info[i].setTextSize(13);
            info[i].setAlpha(0.75f);
            mid.addView(title[i]);
            mid.addView(info[i]);
            row.addView(mid, mp);

            clock[i] = new TextView(getContext());
            clock[i].setTextSize(12);
            clock[i].setAlpha(0.6f);
            row.addView(clock[i]);

            addView(row);
        }
        refresh();
    }

    /** Tiny stacking container so the spinner can sit exactly on top of the dot. */
    private static class FrameLayoutLike extends android.widget.FrameLayout {
        FrameLayoutLike(Context c) { super(c); }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        StageTracker.addListener(this);
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        StageTracker.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override public void onChanged() { refresh(); }

    private void refresh() {
        for (int i = 0; i < 6; i++) {
            int st = StageTracker.state(i);
            int color;
            String symbol;
            switch (st) {
                case StageTracker.DONE: color = 0xFF2E7D32; symbol = "\u2713"; break;
                case StageTracker.FAILED: color = 0xFFC62828; symbol = "\u2715"; break;
                case StageTracker.ACTIVE: color = 0xFF1E88E5; symbol = ""; break;
                default: color = 0xFF9E9E9E; symbol = String.valueOf(i + 1);
            }
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(st == StageTracker.ACTIVE ? 0x331E88E5 : color);
            dot[i].setBackground(g);
            dot[i].setText(symbol);
            spin[i].setVisibility(st == StageTracker.ACTIVE ? View.VISIBLE : View.GONE);
            title[i].setTextColor(st == StageTracker.PENDING ? 0xFF9E9E9E : Color.TRANSPARENT);
            if (st != StageTracker.PENDING) title[i].setTextColor(getDefaultText());
            String d = StageTracker.detail(i);
            info[i].setText(d);
            info[i].setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);
            info[i].setTextColor(st == StageTracker.FAILED ? 0xFFC62828 : getDefaultText());
            clock[i].setText(StageTracker.timeText(i));
        }
    }

    private int getDefaultText() {
        int night = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
                ? 0xFFEEEEEE : 0xFF212121;
    }
}