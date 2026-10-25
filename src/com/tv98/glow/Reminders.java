package com.tv98.glow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Programme reminders set from the TV guide. A minute before the start, Glow offers to switch (a dialog when a
 * Glow screen is in front, a notice over other apps such as Netflix). Kept in preferences; checked every 30 s.
 */
final class Reminders {
    private static final String PREFS = "reminders";
    private static final long EARLY_MS = 60_000L;
    private static Context app;

    private Reminders() {}

    /** "channelKey\tstart\ttitle" */
    private static String id(Channel ch, Guide.Prog p) {
        return ch.key() + "\t" + p.start + "\t" + p.title.replace('\t', ' ');
    }

    private static Set<String> all(Context c) {
        return new LinkedHashSet<>(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("set", new LinkedHashSet<>()));
    }

    private static void save(Context c, Set<String> s) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("set", s).apply();
    }

    static boolean has(Context c, Channel ch, Guide.Prog p) {
        return all(c).contains(id(ch, p));
    }

    /** Sets or clears a reminder; returns true when it's now set. */
    static boolean toggle(Context c, Channel ch, Guide.Prog p) {
        Set<String> s = all(c);
        boolean on = !s.remove(id(ch, p));
        if (on) s.add(id(ch, p));
        save(c, s);
        return on;
    }

    /** Starts the background check (called once from GlowApp; Glow stays running as the Home app). */
    static void start(Context c) {
        app = c.getApplicationContext();
        Net.MAIN.postDelayed(CHECK, 5_000);
    }

    private static final Runnable CHECK = new Runnable() {
        @Override
        public void run() {
            try {
                check();
            } finally {
                Net.MAIN.postDelayed(this, 30_000);
            }
        }
    };

    private static void check() {
        Set<String> s = all(app);
        if (s.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<String> due = new ArrayList<>();
        for (String r : s) {
            String[] f = r.split("\t", 3);
            if (f.length < 3) { due.add(r); continue; }
            long start = Long.parseLong(f[1]);
            if (now >= start - EARLY_MS) due.add(r);
        }
        if (due.isEmpty()) return;
        s.removeAll(due);
        save(app, s);
        for (String r : due) {
            String[] f = r.split("\t", 3);
            if (f.length < 3 || now - Long.parseLong(f[1]) > 30 * 60_000L) continue; // missed by more than half an hour
            fire(f[0], f[2]);
        }
    }

    private static void fire(final String key, final String title) {
        Net.IO.execute(() -> {
            GuideLineup l = GuideLineup.build(app);
            Channel found = null;
            for (Channel ch : l.allChannels()) if (ch.key().equals(key)) found = ch;
            final Channel ch = found;
            if (ch == null) return;
            final String where = (l.number(ch) > 0 ? l.number(ch) + " " : "") + ch.name;
            Net.MAIN.post(() -> {
                Activity a = GlowApp.front();
                if (a != null && !a.isFinishing()) {
                    AlertDialog d = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Starting now: " + title)
                            .setMessage("On " + where + "\n\nOK to watch")
                            .setPositiveButton("Watch", (dlg, w) -> watch(a, l, ch))
                            .setNegativeButton("Not now", null)
                            .show();
                    d.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus(); // OK on the remote = watch
                } else {
                    // Another app is in front: a notice it can't hide. Press Home and open the guide to switch.
                    for (int i = 0; i < 2; i++) {
                        Toast.makeText(app, "⏰  Starting now: " + title + "  ·  " + where, Toast.LENGTH_LONG).show();
                    }
                }
            });
        });
    }

    private static void watch(Activity a, GuideLineup l, Channel ch) {
        List<Channel> list = new ArrayList<>(l.allChannels());
        Xtream.playlist = list;
        a.startActivity(new Intent(a, PlayerActivity.class).putExtra("index", Math.max(0, list.indexOf(ch))));
    }
}
