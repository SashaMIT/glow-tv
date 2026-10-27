package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Tonight on free TV (TV Guide ▸ TONIGHT, or Home's Tonight row): OK watches what's on now, or sets a reminder. */
public class TonightActivity extends Activity {
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
        getWindow().setBackgroundDrawable(null);
        TextView h = Ui.text(this, 22, Ui.TEXT, 300);
        h.setText("Tonight on free TV");
        col.addView(h);
        TextView wait = Ui.text(this, 14, Ui.TEXT_DIM, 400);
        wait.setText("Looking through tonight's listings…");
        wait.setPadding(0, Ui.dp(10), 0, 0);
        col.addView(wait);
        Net.IO.execute(() -> {
            List<Tonight.Pick> picks = Tonight.picks(this, 40);
            Net.MAIN.post(() -> { if (!isFinishing()) fill(picks); });
        });
    }

    private void fill(List<Tonight.Pick> picks) {
        col.removeAllViews();
        TextView h = Ui.text(this, 22, Ui.TEXT, 300);
        h.setText("Tonight on free TV");
        col.addView(h);
        TextView note = Ui.text(this, 11.5f, Ui.TEXT_DIM, 400);
        note.setText(picks.isEmpty() ? "Nothing stands out in tonight's listings yet. They arrive overnight; try again later."
                : "Films, live sport, premieres and news specials from 18:00 to midnight, your favourite channels and teams first. "
                + "OK watches what's on now, or sets a reminder for later.");
        note.setLineSpacing(0, 1.15f);
        note.setPadding(0, Ui.dp(4), 0, Ui.dp(12));
        col.addView(note);
        for (Tonight.Pick k : picks) col.addView(row(k, picks));
        if (col.getChildCount() > 2) col.getChildAt(2).requestFocus();
    }

    private View row(final Tonight.Pick k, final List<Tonight.Pick> all) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(Ui.glass(Ui.dp(12), false));
        row.setOnFocusChangeListener((v, has) -> v.setBackground(Ui.glass(Ui.dp(12), has)));
        row.setPadding(Ui.dp(14), Ui.dp(9), Ui.dp(16), Ui.dp(9));
        TextView when = Ui.text(this, 15, k.onNow() ? 0xFFFF6B6B : Ui.ACCENT, 600);
        when.setText(k.onNow() ? L.t("On now") : android.text.format.DateFormat.format(Ui.is24h(this) ? "HH:mm" : "h:mm a", k.prog.start));
        row.addView(when, new LinearLayout.LayoutParams(Ui.dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, 15.5f, Ui.TEXT, 600);
        title.setText((Reminders.has(this, k.channel, k.prog) ? "⏰  " : "") + k.prog.title);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView ch = Ui.text(this, 12, 0xCCF2F4FF, 400);
        int number = GuideLineup.numberOf(k.channel);
        ch.setText((number > 0 ? number + "  " : "") + k.channel.name);
        ch.setSingleLine();
        ch.setPadding(0, Ui.dp(3), 0, 0);
        words.addView(title);
        words.addView(ch);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView kind = Ui.text(this, 11, 0xFF0B0E1C, 600);
        kind.setText(" " + L.t(k.kind).toUpperCase(java.util.Locale.ROOT) + " ");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(5));
        bg.setColor(k.kind.startsWith("Live") ? 0xFFFF8A7A : k.kind.equals("Sport") ? 0xFFFFB86B : k.kind.equals("Film") ? 0xFFB79CFF : k.kind.equals("Premiere") ? 0xFFF5D33C
                : k.kind.equals("News special") ? 0xFF9FB4FF : 0xFF6BE3A0);
        kind.setBackground(bg);
        kind.setPadding(Ui.dp(6), Ui.dp(3), Ui.dp(6), Ui.dp(3));
        row.addView(kind);
        row.setOnClickListener(v -> act(this, k, all, title));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(5);
        row.setLayoutParams(lp);
        return row;
    }

    /** On now: watch it (▲ ▼ go through tonight's other picks on now). Later: set or clear a reminder. */
    static void act(Activity a, Tonight.Pick k, List<Tonight.Pick> all, TextView title) {
        if (k.onNow()) {
            List<Channel> list = new ArrayList<>();
            for (Tonight.Pick p : all) if (p.onNow() && !list.contains(p.channel)) list.add(p.channel);
            if (!list.contains(k.channel)) list.add(0, k.channel);
            Xtream.playlist = list;
            a.startActivity(new Intent(a, PlayerActivity.class).putExtra("index", list.indexOf(k.channel)));
            return;
        }
        boolean on = Reminders.toggle(a, k.channel, k.prog);
        String time = android.text.format.DateFormat.format(Ui.is24h(a) ? "HH:mm" : "h:mm a", k.prog.start).toString();
        L.toast(a, on ? L.f("⏰  Reminder set: {} at {}", k.prog.title, time) : L.f("Reminder cleared: {}", k.prog.title),
                android.widget.Toast.LENGTH_SHORT).show();
        if (title != null) title.setText((on ? "⏰  " : "") + k.prog.title);
    }
}
