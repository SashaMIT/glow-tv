package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Glow's own settings: developer access, offline channels, phone remote, refreshes, and a way to Android's settings. */
public class SettingsActivity extends Activity {
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Ui.Aurora());
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new FrameLayout.LayoutParams(Ui.dp(620), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(28), 0, Ui.dp(28));
        scroll.addView(list);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    static boolean adbOn(Activity a) {
        return Settings.Global.getInt(a.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    private void build() {
        list.removeAllViews();
        TextView title = Ui.text(this, 26, Ui.TEXT, 300);
        title.setText("Glow settings");
        title.setPadding(Ui.dp(6), 0, 0, Ui.dp(14));
        list.addView(title);

        boolean adb = adbOn(this);
        row(adb ? "Developer access: ON" : "Developer access: off",
                adb ? "Anyone on your Wi-Fi can fully control this box while it's on. Turn it off unless you're working on the box."
                        : "Turn on only while you (or Claude) are working on the box. A warning shows on Home while it's on.",
                adb, v -> {
                    try {
                        Settings.Global.putInt(getContentResolver(), Settings.Global.ADB_ENABLED, adbOn(this) ? 0 : 1);
                    } catch (SecurityException e) {
                        Toast.makeText(this, "Glow needs a one-time permission for this (see the Glow notes).", Toast.LENGTH_LONG).show();
                    }
                    build();
                });

        final boolean hide = getSharedPreferences("channels", MODE_PRIVATE).getBoolean("hide_offline", true);
        row(hide ? "Offline channels: hidden" : "Offline channels: shown",
                "Channels that failed the link check stay out of lists and search (ones with a YouTube backup always show).",
                hide, v -> {
                    getSharedPreferences("channels", MODE_PRIVATE).edit().putBoolean("hide_offline", !hide).apply();
                    build();
                });

        boolean remote = Remote.running();
        row(remote ? "Phone remote: ON" : "Phone remote: off",
                remote ? "Pairing code " + Remote.code() + "  ·  switches itself off after 30 minutes unused." : "Control Glow and type from your phone.",
                remote, v -> {
                    if (Remote.running()) {
                        Remote.stop();
                        build();
                    } else {
                        startActivity(new Intent(this, RemoteActivity.class));
                    }
                });

        row("Refresh channels & guide now", "Re-downloads the free channel lists and today's TV guide, then re-checks links in the background.",
                null, v -> {
                    Toast.makeText(this, "Refreshing in the background…", Toast.LENGTH_SHORT).show();
                    Net.IO.execute(() -> {
                        FreeTv.refresh(this);
                        new java.io.File(getFilesDir(), "guide.idx").setLastModified(0);
                        Guide.ensure(this, null);
                        Health.scanIfDue(this);
                    });
                });

        row("Android settings", "Wi-Fi, display, sound, apps and everything else.", null,
                v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    /** A focusable glass row: title, explanation and an on/off pill (null = plain action). */
    private void row(String titleText, String sub, Boolean on, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(16), true));
        bg.addState(new int[] {}, Ui.glass(Ui.dp(16), false));
        r.setBackground(bg);
        r.setOnClickListener(click);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 17, Ui.TEXT, 600);
        t.setText(titleText);
        TextView s = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        s.setText(sub);
        s.setLineSpacing(0, 1.2f);
        s.setPadding(0, Ui.dp(5), Ui.dp(12), 0);
        words.addView(t);
        words.addView(s);
        r.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (on != null) {
            TextView pill = Ui.text(this, 13, on ? 0xFF0B0E1C : Ui.TEXT, 600);
            pill.setText(on ? "ON" : "OFF");
            pill.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable p = new android.graphics.drawable.GradientDrawable();
            p.setCornerRadius(Ui.dp(14));
            p.setColor(on ? Ui.ACCENT : 0x22FFFFFF);
            pill.setBackground(p);
            r.addView(pill, new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(28)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(10);
        list.addView(r, lp);
    }
}
