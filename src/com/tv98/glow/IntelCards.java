package com.tv98.glow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What the Intelligence pages say, from each source's data, with no screen code: the Overview cards
 * ({label, big line, small lines}, or null when the source has nothing) and the Sports page's free channels.
 */
final class IntelCards {
    private IntelCards() {}

    static String[] weather(Intel.Weather w) {
        if (w == null || w.code < 0) return null;
        String rain = w.daily.isEmpty() ? "" : "   ·   " + L.f("rain {n}%", w.daily.get(0)[4]);
        return new String[] {"Weather", Weather.symbol(w.code) + "  " + Math.round(w.temp) + "°  " + w.city,
                "H " + Math.round(w.max) + "°  L " + Math.round(w.min) + "°" + rain + (w.aqi >= 0 ? "   ·   air " + Intel.aqiBand(w.aqi)[0] : "")};
    }

    /** Something live, else the next to start, else the latest result: "Leeds  1–0  Spurs" or the event's name. */
    static String[] sports(List<Sports.Game> games) {
        if (games == null || games.isEmpty()) return null;
        Sports.Game g = Sports.headline(games);
        String what = g.teams() ? g.home + (g.state == Sports.PRE ? " v " : "  " + g.homeScore + "–" + g.awayScore + "  ") + g.away : g.title;
        return new String[] {"Sports now", what, Sports.LEAGUES[g.league][1] + "   ·   "
                + (g.state == Sports.LIVE ? "LIVE" : g.status) + (g.teams() || g.line2.isEmpty() ? "" : "\n" + g.line2)};
    }

    static String[] markets(List<Markets.Quote> q) {
        if (q == null || q.isEmpty()) return null;
        Markets.Quote first = q.get(0);
        return new String[] {"Markets", (first.symbol.equals("XAU") ? "Gold" : first.symbol) + "  " + Markets.price(first) + "  " + Markets.change(first),
                Markets.line(q.subList(1, q.size())) + "\n" + Markets.baht(q)};
    }

    /** The first earthquake, disaster alert or natural event (the list comes biggest first); cams and news don't count. */
    static String[] world(List<Intel.Event> events) {
        if (events == null) return null;
        for (Intel.Event e : events) {
            if (e.kind.equals(Intel.CAM) || e.kind.equals(Intel.NEWS)) continue;
            return new String[] {e.kind.equals(Intel.QUAKE) ? "Biggest earthquake" : e.kind.equals(Intel.ALERT) ? "Disaster alert" : "Natural event",
                    e.title, e.detail};
        }
        return null;
    }

    static String[] prediction(List<Intel.Market> m) {
        if (m == null || m.isEmpty()) return null;
        return new String[] {"Predictions", Math.round(m.get(0).yes * 100) + "%", m.get(0).question};
    }

    /** The podium of the latest session: "VER  NOR  LEC", then session, place and names. */
    static String[] f1(F1Timing.Sheet f1) {
        if (f1 == null || f1.rows.size() < 3) return null;
        StringBuilder podium = new StringBuilder();
        for (int i = 0; i < 3; i++) podium.append(i == 0 ? "" : "\n").append(i + 1).append("  ").append(f1.rows.get(i).name);
        return new String[] {"F1 timing", f1.rows.get(0).code + "  " + f1.rows.get(1).code + "  " + f1.rows.get(2).code,
                f1.session + "   ·   " + f1.place + "\n" + podium};
    }

    /** A programme title that reads like live sport rather than a replay or magazine show. */
    static boolean looksLive(String title) {
        String t = " " + title.toLowerCase(Locale.ROOT) + " ";
        if (t.contains("highlights") || t.contains("replay") || t.contains("classic") || t.contains("review")) return false;
        return t.contains(" live") || t.contains(" vs ") || t.contains(" vs. ") || t.contains(" v ");
    }

    /**
     * Free sports channels for "On free TV now": ones showing something live first, then ones with a programme on,
     * then the rest, each group in the guide's order; at most {@code n}, leaving out {@code skip} (offline, never promoted).
     * {@code nowTitle} gives what's on (null when the channel has no listings).
     */
    static <T> List<T> freeSports(List<T> channels, Function<T, String> nowTitle, Predicate<T> skip, int n) {
        List<T> onLive = new ArrayList<>(), listed = new ArrayList<>(), rest = new ArrayList<>(), out = new ArrayList<>();
        for (T ch : channels) {
            if (skip.test(ch)) continue;
            String now = nowTitle.apply(ch);
            if (now == null) rest.add(ch);
            else if (looksLive(now)) onLive.add(ch);
            else listed.add(ch);
        }
        for (List<T> group : java.util.Arrays.asList(onLive, listed, rest)) {
            for (T ch : group) if (out.size() < n) out.add(ch);
        }
        return out;
    }

    /** The points of a small price line in a {@code w} × {@code h} box, {x0, y0, x1, y1, …}: oldest left, highest top. */
    static float[] spark(double[] v, int w, int h, float pad) {
        if (v == null || v.length < 2) return new float[0];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double x : v) {
            lo = Math.min(lo, x);
            hi = Math.max(hi, x);
        }
        double span = hi - lo == 0 ? 1 : hi - lo;
        float[] out = new float[v.length * 2];
        for (int i = 0; i < v.length; i++) {
            out[2 * i] = pad + (w - 2 * pad) * i / (float) (v.length - 1);
            out[2 * i + 1] = (float) (h - pad - (h - 2 * pad) * (v[i] - lo) / span);
        }
        return out;
    }

    /** A prediction's money traded: "$1.2M", "$350K", "$90". */
    static String money(double v) {
        if (v >= 1e6) return String.format(Locale.ROOT, "$%.1fM", v / 1e6);
        if (v >= 1e3) return String.format(Locale.ROOT, "$%.0fK", v / 1e3);
        return String.format(Locale.ROOT, "$%.0f", v);
    }
}
