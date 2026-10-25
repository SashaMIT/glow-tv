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

        final String update = Updates.available(this);
        if (update != null) {
            row("Update available: Glow " + update, "Press OK to download it; Android then asks you to confirm the install.",
                    null, v -> {
                        Toast.makeText(this, "Downloading Glow " + update + "…", Toast.LENGTH_SHORT).show();
                        Net.IO.execute(() -> Updates.install(this,
                                msg -> Net.MAIN.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show())));
                    });
        }

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

        final boolean dream = GlowDreamSettings.on(this);
        row(dream ? "Screensaver: Glow ambient, after 15 min" : "Screensaver: off",
                dream ? "Clock, weather, world headlines with pictures and what's on your favourites. Any key wakes the box. "
                        + "Videos playing in any app keep the screen awake."
                        : "Shows Glow's ambient screen after 15 minutes without a key press (switching off puts back the box's own settings).",
                dream, v -> {
                    String problem = GlowDreamSettings.set(this, !GlowDreamSettings.on(this));
                    if (problem != null) Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
                    build();
                });

        final android.content.SharedPreferences display = getSharedPreferences("display", MODE_PRIVATE);
        final int city = display.getInt("weather_city", 0);
        row("Weather: " + Weather.CITIES[Math.min(city, Weather.CITIES.length - 1)][0], "Press OK to choose the next city.", null, v -> {
            display.edit().putInt("weather_city", (city + 1) % Weather.CITIES.length).apply();
            Weather.reset(this);
            build();
            focusRow("Weather");
        });

        final String clockMode = display.getString("clock", "auto");
        row("Clock: " + (clockMode.equals("12") ? "12-hour" : clockMode.equals("24") ? "24-hour" : "automatic"),
                "Home, the TV guide and the screensaver. Press OK to switch.", null, v -> {
                    display.edit().putString("clock", clockMode.equals("auto") ? "12" : clockMode.equals("12") ? "24" : "auto").apply();
                    build();
                    focusRow("Clock");
                });

        final boolean thai = display.getBoolean("thai_first", false);
        row(thai ? "Thai channels first: on" : "Thai channels first: off",
                "Puts the main Thai broadcasters (3HD, Channel 7, Workpoint…) ahead of the English picks in Entertainment. Channel numbers stay the same.",
                thai, v -> {
                    display.edit().putBoolean("thai_first", !thai).apply();
                    GuideLineup.invalidate();
                    build();
                    focusRow("Thai channels");
                });

        row("Sports leagues", Sports.chosenNames(this) + ". Press OK to choose which appear in Sports now.", null, v -> {
            final boolean[] on = Sports.chosen(this);
            String[] names = new String[Sports.LEAGUES.length];
            for (int i = 0; i < names.length; i++) names[i] = Sports.LEAGUES[i][1];
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Sports leagues")
                    .setMultiChoiceItems(names, on, (d, which, checked) -> on[which] = checked)
                    .setPositiveButton("Done", (d, w) -> {
                        Sports.choose(this, on);
                        build();
                        focusRow("Sports leagues");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        final java.util.Set<String> favTeams = Sports.favourites(this);
        row("Favourite teams" + (favTeams.isEmpty() ? "" : ": " + favTeams.size()),
                "Their games go first in Sports now and on Home, and can be the only score alerts. Press OK to choose.", null, v -> {
                    Toast.makeText(this, "Loading teams…", Toast.LENGTH_SHORT).show();
                    Net.IO.execute(() -> {
                        java.util.List<String[]> teams = Sports.teams(this);
                        Net.MAIN.post(() -> {
                            if (isFinishing() || teams.isEmpty()) return;
                            String[] names = new String[teams.size()];
                            boolean[] on = new boolean[teams.size()];
                            for (int i = 0; i < names.length; i++) {
                                names[i] = teams.get(i)[1] + "   ·   " + teams.get(i)[2];
                                on[i] = favTeams.contains(teams.get(i)[0]);
                            }
                            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle("Favourite teams")
                                    .setMultiChoiceItems(names, on, (d, which, checked) -> on[which] = checked)
                                    .setPositiveButton("Done", (d, w) -> {
                                        java.util.Set<String> keep = new java.util.HashSet<>();
                                        for (String k : favTeams) { // teams from leagues not shown stay chosen
                                            boolean listed = false;
                                            for (String[] t : teams) if (t[0].equals(k)) listed = true;
                                            if (!listed) keep.add(k);
                                        }
                                        for (int i = 0; i < on.length; i++) if (on[i]) keep.add(teams.get(i)[0]);
                                        Sports.setFavourites(this, keep);
                                        build();
                                        focusRow("Favourite teams");
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        });
                    });
                });

        final int alertMode = ScoreAlerts.mode(this);
        row(alertMode == ScoreAlerts.OFF ? "Score alerts: off" : alertMode == ScoreAlerts.FAVOURITES ? "Score alerts: favourite teams only" : "Score alerts: all my leagues",
                "A small banner over live TV for goals, kick-offs and final results. Press OK to switch.", alertMode != ScoreAlerts.OFF, v -> {
                    ScoreAlerts.setMode(this, alertMode == ScoreAlerts.ALL ? ScoreAlerts.FAVOURITES : alertMode == ScoreAlerts.FAVOURITES ? ScoreAlerts.OFF : ScoreAlerts.ALL);
                    build();
                    focusRow("Score alerts");
                });

        row("Apps on Home", "Hide apps you don't use and choose their order on the shelf.", null,
                v -> startActivity(new Intent(this, AppsActivity.class)));

        row("Refresh channels & guide now", "Re-downloads the free channel lists and today's TV guide, then re-checks links in the background.",
                null, v -> {
                    Toast.makeText(this, "Refreshing in the background…", Toast.LENGTH_SHORT).show();
                    Net.IO.execute(() -> {
                        FreeTv.refresh(this);
                        new java.io.File(getFilesDir(), "guide.idx").setLastModified(0);
                        new java.io.File(getFilesDir(), "guide-pluto.idx").setLastModified(0);
                        Guide.ensure(this, null);
                        Health.scanIfDue(this);
                    });
                });

        row("Android settings", "Wi-Fi, display, sound, apps and everything else.", null,
                v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        TextView version = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        String name = "";
        try {
            name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        version.setText("Glow " + name + "   ·   github.com/" + Updates.REPO);
        version.setPadding(Ui.dp(8), Ui.dp(6), 0, 0);
        list.addView(version);

        if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    /** After a rebuild, puts focus back on the row whose title starts with {@code prefix}. */
    private void focusRow(String prefix) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View r = list.getChildAt(i);
            if (r instanceof LinearLayout && ((LinearLayout) r).getChildCount() > 0 && ((LinearLayout) r).getChildAt(0) instanceof LinearLayout) {
                View t = ((LinearLayout) ((LinearLayout) r).getChildAt(0)).getChildAt(0);
                if (t instanceof TextView && ((TextView) t).getText().toString().startsWith(prefix)) {
                    r.requestFocus();
                    return;
                }
            }
        }
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
