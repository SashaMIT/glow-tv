package com.tv98.glow;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** F1 timing sheet (OK on an F1 row in Sports now): the latest finished session, every driver, gaps and tyres. */
public class F1Activity extends Activity {
    private LinearLayout col;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new Ui.Aurora());
        scroll.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(70), Ui.dp(28), Ui.dp(70), Ui.dp(28));
        scroll.addView(col);
        setContentView(scroll);
        TextView h = Ui.text(this, 22, Ui.TEXT, 300);
        h.setText("F1 timing");
        col.addView(h);
        TextView wait = Ui.text(this, 14, Ui.TEXT_DIM, 400);
        wait.setText("Loading the latest session…");
        wait.setPadding(0, Ui.dp(10), 0, 0);
        col.addView(wait);
        final long t0 = android.os.SystemClock.uptimeMillis();
        Net.IO.execute(() -> {
            F1Timing.Sheet s = F1Timing.latest(this);
            android.util.Log.i("GlowPerf", "f1 timing in " + (android.os.SystemClock.uptimeMillis() - t0) + " ms, "
                    + (s == null ? "none" : s.rows.size() + " drivers"));
            Net.MAIN.post(() -> {
                if (isFinishing()) return;
                if (s == null) wait.setText("Couldn't load F1 timing right now. Check the internet connection.");
                else fill(s);
            });
        });
    }

    private void fill(F1Timing.Sheet s) {
        col.removeAllViews();
        TextView h = Ui.text(this, 22, Ui.TEXT, 300);
        h.setText("F1 timing   ·   " + s.session + "   ·   " + s.place);
        col.addView(h);
        TextView note = Ui.text(this, 11.5f, Ui.TEXT_DIM, 400);
        note.setText(L.f("Ended {}. OpenF1's free data arrives after each session; live timing is their paid tier, so during a race Sports now shows ESPN's running order.",
                L.date("EEE d MMM HH:mm", s.ended)));
        note.setLineSpacing(0, 1.15f);
        note.setPadding(0, Ui.dp(4), 0, Ui.dp(12));
        col.addView(note);
        for (F1Timing.Row r : s.rows) col.addView(row(r));
        if (col.getChildCount() > 2) col.getChildAt(2).requestFocus();
    }

    private View row(F1Timing.Row r) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setBackground(Ui.glass(Ui.dp(10), false));
        row.setOnFocusChangeListener((v, has) -> v.setBackground(Ui.glass(Ui.dp(10), has)));
        row.setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(14), Ui.dp(7));
        TextView pos = Ui.text(this, 16, Ui.TEXT, 600);
        pos.setText(r.pos > 0 ? String.valueOf(r.pos) : "–");
        pos.setGravity(Gravity.END);
        row.addView(pos, new LinearLayout.LayoutParams(Ui.dp(30), ViewGroup.LayoutParams.WRAP_CONTENT));
        View bar = new View(this);
        bar.setBackgroundColor(r.colour);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(4), Ui.dp(24));
        blp.setMargins(Ui.dp(12), 0, Ui.dp(10), 0);
        row.addView(bar, blp);
        TextView code = Ui.text(this, 16, Ui.TEXT, 600);
        code.setText(r.code);
        row.addView(code, new LinearLayout.LayoutParams(Ui.dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView name = Ui.text(this, 13, 0xCCF2F4FF, 400);
        name.setText(r.name);
        name.setSingleLine();
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView team = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        team.setText(r.team);
        team.setSingleLine();
        row.addView(team, new LinearLayout.LayoutParams(Ui.dp(170), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        for (String t : r.tyres) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF15161C);
            g.setStroke(Ui.dp(3), F1Timing.tyreColour(t));
            d.setBackground(g);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14));
            dlp.rightMargin = Ui.dp(4);
            dots.addView(d, dlp);
        }
        row.addView(dots, new LinearLayout.LayoutParams(Ui.dp(110), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView laps = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        laps.setText(L.f("{} laps", r.laps));
        laps.setGravity(Gravity.END);
        row.addView(laps, new LinearLayout.LayoutParams(Ui.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView gap = Ui.text(this, 14, r.gap.startsWith("D") ? 0xFFFF8A7A : Ui.TEXT, 600);
        gap.setText(r.gap);
        gap.setGravity(Gravity.END);
        row.addView(gap, new LinearLayout.LayoutParams(Ui.dp(110), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(4);
        row.setLayoutParams(lp);
        return row;
    }
}
