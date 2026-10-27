package com.tv98.glow;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tonight on free TV: films, live sport, premieres and news specials from 18:00 to midnight (from now, once it's
 * evening) on working channels, picked from the listings Glow already has. The listings carry titles and times but no
 * categories, so a pick is judged by its channel's genre, its length and its title. Favourite channels and teams
 * come first; long-running loops ("25 Words or Less" all night) and fillers are left out.
 */
final class Tonight {
    static final class Pick {
        Channel channel;
        Guide.Prog prog;
        String kind; // "Film", "Live sport", "Sport", "Premiere", "News special", "Documentary"
        double score;

        boolean onNow() {
            long now = System.currentTimeMillis();
            return prog.start <= now && now < prog.stop;
        }
    }

    private static final String[] FILLER = {"paid programming", "infomercial", "to be announced", "tba", "no information",
            "close", "closedown", "off air", "programmes start", "teleshopping", "shopping", "station closed"};

    private static List<Pick> memo;
    private static long memoAt;

    private Tonight() {}

    /** Off the main thread. The best {@code max} picks, in time order (worked out again every 10 minutes). */
    static synchronized List<Pick> picks(Context c, int max) {
        if (memo == null || System.currentTimeMillis() - memoAt > 10 * 60_000L) {
            memo = build(c);
            memoAt = System.currentTimeMillis();
        }
        List<Pick> out = new ArrayList<>(memo.size() > max ? memo.subList(0, max) : memo);
        Collections.sort(out, (a, b) -> Long.compare(a.prog.start, b.prog.start));
        return out;
    }

    private static final java.util.regex.Pattern REPLAY = java.util.regex.Pattern.compile(
            "highlights?|\\bhl?ts?\\b|\\bhls\\b|replay|classic|rewind|best of|recap|throwback|\\bvault\\b");
    private static final java.util.regex.Pattern YEAR = java.util.regex.Pattern.compile("\\b(19[5-9]\\d|20[0-4]\\d)\\b");

    /** Highlights, replays and anything naming a past year ("Bellator 199", "World Cup 2019"…) aren't tonight's sport. */
    private static boolean replay(String t) {
        if (REPLAY.matcher(t).find()) return true;
        java.util.regex.Matcher y = YEAR.matcher(t);
        int now = Calendar.getInstance().get(Calendar.YEAR);
        while (y.find()) if (Integer.parseInt(y.group(1)) < now) return true;
        return false;
    }

    /** Listings arrived or favourites changed. */
    static synchronized void invalidate() {
        memo = null;
    }

    private static List<Pick> build(Context c) {
        long t0 = android.os.SystemClock.uptimeMillis();
        GuideLineup l = GuideLineup.build(c);
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 18);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long evening = cal.getTimeInMillis(), midnight = evening + 6 * 3_600_000L;
        if (now > midnight - 30 * 60_000L) { // late at night: tomorrow evening's instead
            evening += 86_400_000L;
            midnight += 86_400_000L;
        }
        long from = Math.max(evening, now - 15 * 60_000L), to = midnight; // what began a few minutes ago still counts
        Set<String> teams = new HashSet<>();
        for (String name : Sports.favouriteNames(c)) teams.add(name.toLowerCase(Locale.ROOT));
        List<Pick> all = new ArrayList<>();
        Map<String, Integer> perChannel = new HashMap<>();
        for (Channel ch : l.allChannels()) {
            // Working channels only, never a pay channel's re-stream, and in English or Thai (or not known).
            if (Channel.isOffline(c, ch) || Genres.neverPromote(ch) || Genres.language(ch) == 2) continue;
            List<Guide.Prog> progs = Guide.between(ch, from, to);
            if (progs.isEmpty()) continue;
            // The same title over and over is a loop channel, not tonight's highlight.
            Map<String, Integer> repeats = new HashMap<>();
            for (Guide.Prog p : progs) repeats.merge(p.title.toLowerCase(Locale.ROOT), 1, Integer::sum);
            List<Integer> genres = Genres.of(ch);
            boolean fav = Picks.isFavourite(c, ch);
            for (Guide.Prog p : progs) {
                if (p.start < from || p.start >= to) continue;
                String t = p.title.toLowerCase(Locale.ROOT).trim();
                if (t.length() < 3 || filler(t) || repeats.get(t) > 2) continue;
                long minutes = (p.stop - p.start) / 60_000L;
                Pick k = new Pick();
                k.channel = ch;
                k.prog = p;
                if (genres.contains(Genres.SPORTS) && (replay(t) || !IntelActivity.looksLive(p.title))) {
                    continue; // sport channels' magazines, replays and highlights
                } else if (genres.contains(Genres.SPORTS)) {
                    // "Live" in the title is the only sign the listings give that it really is live.
                    boolean live = java.util.regex.Pattern.compile("\\blive\\b").matcher(t).find();
                    k.kind = live ? "Live sport" : "Sport";
                    k.score = live ? 3 : 1.8;
                } else if (t.contains("premiere") || t.startsWith("new:") || t.contains("(new)") || t.contains("series finale")) {
                    k.kind = "Premiere";
                    k.score = 2.5;
                } else if (genres.contains(Genres.NEWS) && (t.contains("special") || t.contains("debate") || t.contains("election")
                        || t.contains("live coverage") || t.contains("breaking"))) {
                    k.kind = "News special";
                    k.score = 2;
                } else if (genres.contains(Genres.MOVIES) && minutes >= 80 && minutes <= 200) {
                    k.kind = "Film";
                    k.score = 2;
                } else if (genres.contains(Genres.DOCS) && minutes >= 45) {
                    k.kind = "Documentary";
                    k.score = 1;
                } else {
                    continue;
                }
                for (String team : teams) if (team.length() > 3 && t.contains(team)) k.score += 3; // my team plays
                if (fav) k.score += 2;
                if (p.start >= from + 2 * 3_600_000L && p.start < from + 4 * 3_600_000L) k.score += 0.3; // prime time
                all.add(k);
            }
        }
        Collections.sort(all, (a, b) -> Double.compare(b.score, a.score) != 0 ? Double.compare(b.score, a.score) : Long.compare(a.prog.start, b.prog.start));
        // One of each title, at most two from a channel.
        List<Pick> out = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        for (Pick k : all) {
            int n = perChannel.getOrDefault(k.channel.key(), 0);
            if (n >= 2 || !titles.add(k.prog.title.toLowerCase(Locale.ROOT))) continue;
            perChannel.put(k.channel.key(), n + 1);
            out.add(k);
            if (out.size() >= 40) break;
        }
        android.util.Log.i("GlowPerf", "tonight: " + out.size() + " picks from " + all.size() + " in "
                + (android.os.SystemClock.uptimeMillis() - t0) + " ms");
        return out;
    }

    private static boolean filler(String t) {
        for (String f : FILLER) if (t.equals(f) || t.startsWith(f + " ")) return true;
        return false;
    }

    /** "20:30  ·  Film  ·  1 h 45" (or "On now · Film") for a card. */
    static String line(Context c, Pick k) {
        long min = (k.prog.stop - k.prog.start) / 60_000L;
        String len = min >= 60 ? (min / 60) + " h" + (min % 60 > 0 ? " " + min % 60 : "") : min + " min";
        String when = k.onNow() ? L.t("On now") : android.text.format.DateFormat.format(Ui.is24h(c) ? "HH:mm" : "h:mm a", k.prog.start).toString();
        return when + "  ·  " + L.t(k.kind) + (k.kind.equals("Film") ? "  ·  " + len : "");
    }
}
