package com.tv98.glow;

import android.content.Context;
import android.text.format.DateFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Sports now: live scores, today's fixtures and recent results from ESPN's public scoreboards (no key; ~62 KB
 * gzipped for all six leagues). Fight and motorsport first, as on the TV Guide's Sports list.
 */
final class Sports {
    /** {ESPN path, short name, badge colour}. Order = priority when several are live. */
    static final String[][] LEAGUES = {
            {"mma/ufc", "UFC", "D20A0A"},
            {"racing/f1", "F1", "E10600"},
            {"soccer/eng.1", "Premier League", "3D195B"},
            {"soccer/uefa.champions", "Champions League", "0E1E5B"},
            {"basketball/nba", "NBA", "1D428A"},
            {"football/nfl", "NFL", "013369"},
    };
    static final int PRE = 0, LIVE = 1, DONE = 2;

    static final class Game {
        int league, state;
        long start;
        String title = "", line2 = "", status = "", tv = "";
        // Team games: two sides with scores and logos (empty for UFC cards and F1 weekends).
        String home = "", away = "", homeScore = "", awayScore = "", homeLogo = "", awayLogo = "";
        boolean homeWon, awayWon;

        boolean teams() {
            return !home.isEmpty();
        }
    }

    private Sports() {}

    /** Live first, then league by league in priority order (fight and motorsport first): fixtures, then results. */
    static List<Game> load(Context c) {
        long t0 = android.os.SystemClock.uptimeMillis();
        List<Game> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        // The six scoreboards in parallel (one after another took 3.7 s on the box).
        List<java.util.concurrent.Future<String>> feeds = new ArrayList<>();
        for (int i = 0; i < LEAGUES.length; i++) {
            final int league = i;
            // Scores move: refresh every 3 min, but a league with nothing on today only every 30 min.
            final long fresh = liveHint[i] ? 3 * 60_000L : 30 * 60_000L;
            feeds.add(FETCH.submit(() -> Intel.cached(c, "espn-" + LEAGUES[league][0].replace('/', '-'),
                    "https://site.api.espn.com/apis/site/v2/sports/" + LEAGUES[league][0] + "/scoreboard", fresh)));
        }
        for (int i = 0; i < LEAGUES.length; i++) {
            String s;
            try {
                s = feeds.get(i).get();
            } catch (Exception e) {
                s = null;
            }
            if (s == null) continue;
            List<Game> games = new ArrayList<>();
            try {
                JSONArray events = new JSONObject(s).optJSONArray("events");
                for (int e = 0; events != null && e < events.length(); e++) {
                    Game g = parse(i, events.getJSONObject(e), c);
                    if (g != null) games.add(g);
                }
            } catch (Exception ignored) {
            }
            boolean anyLive = false;
            // Fight cards and race weekends a week ahead; team games within two days (a full NFL Sunday is 14).
            long ahead = g0(games) ? 7 * 86_400_000L : 48 * 3_600_000L;
            Collections.sort(games, Sports::order);
            int shown = 0;
            for (Game g : games) {
                if (g.state == LIVE) anyLive = true;
                boolean wanted = g.state == LIVE || (g.state == PRE && g.start - now < ahead)
                        || (g.state == DONE && now - g.start < 30 * 3_600_000L);
                if (wanted && (g.state == LIVE || shown < 6)) {
                    out.add(g);
                    shown++;
                }
            }
            liveHint[i] = anyLive || startsSoon(games, now);
        }
        Collections.sort(out, Sports::order);
        android.util.Log.i("GlowPerf", "sports: " + out.size() + " games in " + (android.os.SystemClock.uptimeMillis() - t0) + " ms");
        return out;
    }

    private static int order(Game a, Game b) {
        if ((a.state == LIVE) != (b.state == LIVE)) return a.state == LIVE ? -1 : 1;
        if (a.league != b.league) return a.league - b.league;
        if (a.state != b.state) return rank(a.state) - rank(b.state);
        return a.state == DONE ? Long.compare(b.start, a.start) : Long.compare(a.start, b.start);
    }

    /** UFC and F1 events are whole cards/weekends rather than single games. */
    private static boolean g0(List<Game> games) {
        return !games.isEmpty() && !games.get(0).teams();
    }

    // Whether a league had a game on (or about to start) last time: then its scores refresh every 3 minutes.
    // Starts true so the first look after Glow starts is never older than that.
    private static final java.util.concurrent.ExecutorService FETCH = java.util.concurrent.Executors.newFixedThreadPool(3);

    private static final boolean[] liveHint = {true, true, true, true, true, true};

    private static boolean startsSoon(List<Game> games, long now) {
        for (Game g : games) if (g.state == PRE && g.start - now < 30 * 60_000L) return true;
        return false;
    }

    private static int rank(int state) {
        return state == LIVE ? 0 : state == PRE ? 1 : 2;
    }

    private static int state(JSONObject status) {
        String st = status == null ? "" : status.optJSONObject("type") == null ? "" : status.optJSONObject("type").optString("state");
        return st.equals("in") ? LIVE : st.equals("post") ? DONE : PRE;
    }

    private static Game parse(int league, JSONObject e, Context c) throws Exception {
        JSONArray comps = e.optJSONArray("competitions");
        if (comps == null || comps.length() == 0) return null;
        Game g = new Game();
        g.league = league;
        String path = LEAGUES[league][0];
        if (path.startsWith("mma/")) return fightCard(g, e, comps, c);
        if (path.startsWith("racing/")) return raceWeekend(g, e, comps, c);

        JSONObject comp = comps.getJSONObject(0);
        g.start = time(comp.optString("date", e.optString("date")));
        g.state = state(e.optJSONObject("status"));
        JSONArray sides = comp.optJSONArray("competitors");
        if (sides == null || sides.length() < 2) return null;
        for (int k = 0; k < 2; k++) {
            JSONObject side = sides.getJSONObject(k);
            JSONObject team = side.optJSONObject("team");
            if (team == null) continue;
            String name = team.optString("shortDisplayName", team.optString("displayName"));
            boolean home = side.optString("homeAway").equals("home") || (k == 0 && !side.has("homeAway"));
            if (home) {
                g.home = name;
                g.homeScore = side.optString("score");
                g.homeLogo = team.optString("logo");
                g.homeWon = side.optBoolean("winner");
            } else {
                g.away = name;
                g.awayScore = side.optString("score");
                g.awayLogo = team.optString("logo");
                g.awayWon = side.optBoolean("winner");
            }
        }
        g.title = g.home + " v " + g.away;
        JSONObject type = e.optJSONObject("status") == null ? null : e.getJSONObject("status").optJSONObject("type");
        String detail = type == null ? "" : type.optString("shortDetail");
        g.status = g.state == PRE ? when(c, g.start) : g.state == LIVE ? detail : type != null && type.optString("description").startsWith("Postponed") ? "Postponed" : detail;
        JSONObject venue = comp.optJSONObject("venue");
        g.line2 = venue == null ? "" : venue.optString("fullName");
        g.tv = broadcasters(comp);
        return g;
    }

    /** A UFC card: the event, and its main event (the last bout) or the bout on now. */
    private static Game fightCard(Game g, JSONObject e, JSONArray bouts, Context c) throws Exception {
        g.title = e.optString("name");
        g.state = state(e.optJSONObject("status"));
        g.start = time(e.optString("date"));
        JSONObject main = bouts.getJSONObject(bouts.length() - 1);
        JSONObject now = null;
        for (int i = 0; i < bouts.length(); i++) {
            JSONObject b = bouts.getJSONObject(i);
            if (state(b.optJSONObject("status")) == LIVE) now = b;
        }
        if (now != null) g.state = LIVE;
        JSONObject b = now != null ? now : main;
        String[] f = fighters(b);
        if (g.state == DONE && f[2] != null) g.line2 = f[2] + " beat " + (f[2].equals(f[0]) ? f[1] : f[0]) + " in the main event";
        else g.line2 = (now != null ? "Fighting now: " : "Main event: ") + f[0] + " v " + f[1];
        g.status = g.state == PRE ? when(c, g.start) : g.state == LIVE ? "Live" : "Final";
        g.tv = broadcasters(main);
        return g;
    }

    private static String[] fighters(JSONObject bout) {
        String[] f = {"", "", null};
        JSONArray cs = bout.optJSONArray("competitors");
        for (int k = 0; cs != null && k < cs.length() && k < 2; k++) {
            JSONObject x = cs.optJSONObject(k);
            JSONObject a = x == null ? null : x.optJSONObject("athlete");
            f[k] = a == null ? "" : a.optString("shortName", a.optString("displayName"));
            if (x != null && x.optBoolean("winner")) f[2] = f[k];
        }
        return f;
    }

    /** An F1 weekend: the session on now, else the next one, else the race result. */
    private static Game raceWeekend(Game g, JSONObject e, JSONArray sessions, Context c) throws Exception {
        g.title = e.optString("name").replace("MSC Cruises ", "").replace("Formula 1 ", "");
        JSONObject pick = null;
        int pickState = DONE;
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.getJSONObject(i);
            int st = state(s.optJSONObject("status"));
            if (st == LIVE) { pick = s; pickState = LIVE; break; }
            if (st == PRE && pickState != PRE) { pick = s; pickState = PRE; }
        }
        if (pick == null) pick = sessions.getJSONObject(sessions.length() - 1);
        g.state = pickState;
        g.start = time(pick.optString("date"));
        String session = sessionName(pick);
        JSONArray cs = pick.optJSONArray("competitors");
        StringBuilder top = new StringBuilder();
        if (g.state != PRE) {
            for (int k = 0; cs != null && k < cs.length(); k++) {
                JSONObject x = cs.getJSONObject(k);
                int order = x.optInt("order", 99);
                if (order > 3) continue;
                JSONObject a = x.optJSONObject("athlete");
                if (top.length() > 0) top.append("   ");
                top.append(order).append(" ").append(a == null ? "?" : a.optString("shortName", a.optString("displayName")));
            }
        }
        if (g.state == PRE) {
            // The latest finished session's top three is worth showing while waiting for the next.
            String last = lastResult(sessions);
            g.line2 = session + (last.isEmpty() ? "" : "   ·   " + last);
            g.status = when(c, g.start);
        } else {
            g.line2 = session + (top.length() > 0 ? ":   " + top : "");
            g.status = g.state == LIVE ? "Live" : "Final";
        }
        g.tv = broadcasters(pick);
        return g;
    }

    private static String lastResult(JSONArray sessions) {
        String out = "";
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null || state(s.optJSONObject("status")) != DONE) continue;
            JSONArray cs = s.optJSONArray("competitors");
            for (int k = 0; cs != null && k < cs.length(); k++) {
                JSONObject x = cs.optJSONObject(k);
                if (x != null && x.optInt("order", 99) == 1 && x.optJSONObject("athlete") != null) {
                    out = sessionName(s) + ": " + x.optJSONObject("athlete").optString("shortName", "?") + " fastest";
                }
            }
        }
        return out;
    }

    private static String sessionName(JSONObject s) {
        JSONObject t = s.optJSONObject("type");
        String a = t == null ? "" : t.optString("abbreviation");
        switch (a) {
            case "Qual": return "Qualifying";
            case "Race": return "Race";
            case "SS": return "Sprint shootout";
            case "Sprint": return "Sprint";
            default: return a.startsWith("FP") ? "Practice " + a.substring(2) : a;
        }
    }

    private static String broadcasters(JSONObject comp) {
        JSONArray b = comp.optJSONArray("broadcasts");
        List<String> names = new ArrayList<>();
        for (int i = 0; b != null && i < b.length(); i++) {
            JSONArray n = b.optJSONObject(i) == null ? null : b.optJSONObject(i).optJSONArray("names");
            for (int k = 0; n != null && k < n.length(); k++) if (!names.contains(n.optString(k)) && names.size() < 3) names.add(n.optString(k));
        }
        return android.text.TextUtils.join(", ", names);
    }

    /** ESPN dates: 2026-10-25T14:00Z or 2026-10-25T14:00:00Z, UTC. */
    static long time(String s) {
        try {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(5, 7)) - 1, Integer.parseInt(s.substring(8, 10)),
                    Integer.parseInt(s.substring(11, 13)), Integer.parseInt(s.substring(14, 16)));
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    /** "Today 21:00", "Tomorrow 2:00 AM", "Sat 21:00" in the box's own time zone. */
    static String when(Context c, long t) {
        Calendar now = Calendar.getInstance(), at = Calendar.getInstance();
        at.setTimeInMillis(t);
        int days = (at.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 366 + at.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR);
        String hm = DateFormat.format(Ui.is24h(c) ? "HH:mm" : "h:mm a", t).toString();
        if (days == 0) return "Today " + hm;
        if (days == 1) return "Tomorrow " + hm;
        return DateFormat.format("EEE", t) + " " + hm;
    }

    /** For a card: something live, else the next thing to start, else the latest result. */
    static Game headline(List<Game> games) {
        Game next = null;
        for (Game g : games) {
            if (g.state == LIVE) return g;
            if (g.state == PRE && (next == null || g.start < next.start)) next = g;
        }
        return next != null ? next : games.get(0);
    }

    /** A free channel in the guide named like one of the broadcasters (never a pay-TV re-stream), or null. */
    static Channel freeChannel(Game g) {
        GuideLineup l = GuideLineup.current();
        if (l == null || g.tv.isEmpty()) return null;
        for (String b : g.tv.split(", ")) {
            String want = b.toLowerCase(Locale.ROOT).trim();
            if (want.length() < 4) continue;
            for (GuideLineup.Section sec : l.main) {
                if (!sec.name.equals("Sports")) continue;
                for (Channel ch : sec.channels) {
                    if (!Genres.neverPromote(ch) && ch.name.toLowerCase(Locale.ROOT).trim().equals(want)) return ch;
                }
            }
        }
        return null;
    }
}
