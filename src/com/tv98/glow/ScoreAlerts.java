package com.tv98.glow;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Score banners over live TV: a goal in a football match, a game or fight card finishing, a race ending, or a match
 * kicking off, in the leagues chosen in Settings. NBA/NFL score changes are left out (they'd never stop).
 * Glow ▸ Settings ▸ Score alerts turns them off. `adb shell setprop log.tag.GlowAlertTest DEBUG` makes the next
 * check show a sample banner, for testing.
 */
final class ScoreAlerts {
    /** One banner: league index (badge colour and name), a short headline and the line under it. */
    static final class Alert {
        final int league;
        final String headline, line;

        Alert(int league, String headline, String line) {
            this.league = league;
            this.headline = headline;
            this.line = line;
        }
    }

    // What each game looked like at the last check: state and score. Empty until the first check (no banners then).
    private static final Map<String, String> LAST = new HashMap<>();
    private static boolean primed;

    private ScoreAlerts() {}

    static boolean enabled(Context c) {
        return c.getSharedPreferences("sports", Context.MODE_PRIVATE).getBoolean("alerts", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("sports", Context.MODE_PRIVATE).edit().putBoolean("alerts", on).apply();
    }

    /** Off the main thread. What changed since the last check. */
    static synchronized List<Alert> check(Context c) {
        List<Alert> out = new ArrayList<>();
        List<Sports.Game> games = Sports.load(c);
        for (Sports.Game g : games) {
            String key = g.league + "|" + g.title;
            String now = g.state + "|" + g.homeScore + "|" + g.awayScore;
            String before = LAST.put(key, now);
            if (!primed || before == null || before.equals(now)) continue;
            int was = before.charAt(0) - '0';
            String path = Sports.LEAGUES[g.league][0];
            boolean football = path.startsWith("soccer/");
            String score = g.teams() ? g.home + "  " + g.homeScore + " – " + g.awayScore + "  " + g.away : g.title;
            if (g.state == Sports.LIVE && was == Sports.LIVE && football) {
                out.add(new Alert(g.league, "GOAL", score + (g.status.isEmpty() ? "" : "   " + g.status)
                        + (g.scorer.isEmpty() ? "" : "   ·   " + g.scorer)));
            } else if (g.state == Sports.DONE && was == Sports.LIVE) {
                out.add(new Alert(g.league, football ? "FULL TIME" : "FINAL", g.teams() ? score : g.title + "   ·   " + g.line2));
            } else if (g.state == Sports.LIVE && was == Sports.PRE && (football || !g.teams())) {
                out.add(new Alert(g.league, football ? "KICK-OFF" : "STARTED", g.teams() ? g.home + " v " + g.away : g.title));
            }
        }
        primed = true;
        if (android.util.Log.isLoggable("GlowAlertTest", android.util.Log.DEBUG) && !games.isEmpty()) {
            Sports.Game g = games.get(0);
            out.add(new Alert(g.league, "TEST", (g.teams() ? g.home + " v " + g.away : g.title) + "   ·   sample banner"));
        }
        return out;
    }
}
