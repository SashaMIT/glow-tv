package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What goes at the top of each list: the viewer's own favourites first, then hand-picked major
 * channels for that list or country, then everything else in playlist order.
 *
 * Picks are the best-known free channels. Pay-TV networks (CNN, Fox News Channel, ESPN, Sky Sports,
 * beIN 1–8…) are deliberately not auto-picked: free links to them are usually unofficial re-streams.
 * Anyone can still star them as favourites.
 *
 * Patterns are lower-case channel names: "=name" must match exactly, otherwise a prefix matches
 * ("bbc news" matches "BBC News Africa").
 */
final class Picks {
    private static final String[] THAILAND = {
            "=3hd", "=channel 7 (thailand)", "=workpoint tv", "=one 31", "=thai pbs", "=thairath tv", "=gmm 25",
            "=amarin tv", "=mcot hd", "=channel 8 (thailand)", "=mono 29", "=true4u", "=nation tv", "=tnn16",
            "=channel 5 (thailand)", "=nbt 2 hd", "=t sports 7", "=jkn 18", "=top news", "=altv"};
    private static final String[] UK = {
            "=bbc one london hd", "=bbc one", "=bbc two hd", "=bbc news uk hd", "=channel 4",
            "=channel 5 (united kingdom)", "=bbc three hd", "=bbc three", "=bbc four", "=gb news",
            "=bbc parliament", "=bbc scotland", "=bbc alba"};
    private static final String[] US = {
            "=abc news live", "=abc news live 1", "=cbs news 24/7", "nbc news now", "livenow from fox", "=fox weather",
            "=bloomberg tv", "reuters", "cheddar news", "scripps news", "newsnation", "=pbs", "=nbc", "=cbs", "=abc",
            "ion tv", "=cbs sports hq", "=cbs sports golazo network", "weather"};
    private static final String[] WORLD_NEWS = {
            "=bbc news uk hd", "=bbc news north america", "bbc news", "=al jazeera english hd", "=al jazeera english",
            "=france 24 english hd", "=france 24 english", "=dw english", "=sky news", "sky news extra",
            "=euronews english hd", "=euronews english", "=nhk world-japan hd", "=nhk world-japan",
            "=cna international", "=cna (singapore)", "=bloomberg tv", "reuters", "=abc news", "=cbs news 24/7",
            "nbc news now", "=abc news live", "=abc news live 1", "livenow from fox", "=trt world hd", "=trt world",
            "=arirang tv hd", "=arirang tv", "=cgtn", "=wion", "i24news english"};
    private static final String[] SPORTS = {
            "red bull tv", "fifa+", "nfl channel", "=bein sports xtra", "=cbs sports hq", "=cbs sports golazo network",
            "dazn combat", "dazn darts", "pga tour", "=acc digital network", "stadium", "bellator", "billiard tv",
            "world poker tour", "=t sports 7", "dd sports", "olympic"};
    private static final String[] NEWS_IN = {
            "dd news", "=ndtv 24x7", "=india today", "=aaj tak hd", "=aaj tak", "=times now", "=republic tv", "=wion",
            "=zee news", "abp news", "=cnn news 18"};
    private static final String[] NEWS_US = {
            "=abc news live", "=abc news live 1", "=cbs news 24/7", "nbc news now", "livenow from fox", "=fox weather",
            "=bloomberg tv", "reuters", "cheddar news", "scripps news", "newsnation"};
    private static final String[] NEWS_UK = {"=bbc news uk hd", "bbc news", "=sky news", "sky news extra", "=gb news", "=bbc parliament"};
    private static final String[] NEWS_TH = {"=thai pbs", "=thairath tv", "=nation tv", "=tnn16", "=top news", "=nbt 2 hd"};

    private static final Map<String, String[]> BY_LIST = new HashMap<>();

    static {
        BY_LIST.put("countries/th.m3u", THAILAND);
        BY_LIST.put("countries/uk.m3u", UK);
        BY_LIST.put("countries/us.m3u", US);
        BY_LIST.put("categories/news.m3u", WORLD_NEWS);
        BY_LIST.put("categories/sports.m3u", SPORTS);
        BY_LIST.put("news:in", NEWS_IN);
        BY_LIST.put("news:us", NEWS_US);
        BY_LIST.put("news:uk", NEWS_UK);
        BY_LIST.put("news:th", NEWS_TH);
    }

    private Picks() {}

    /** Position in the list's picks (0 = most important), or -1 when the channel isn't a pick there. */
    static int rank(String listId, Channel ch) {
        String[] p = listId == null ? null : BY_LIST.get(listId);
        if (p == null) return -1;
        String n = ch.name.toLowerCase(Locale.ROOT).trim();
        for (int i = 0; i < p.length; i++) {
            String pat = p[i];
            if (pat.startsWith("=") ? n.equals(pat.substring(1)) : (n.equals(pat) || n.startsWith(pat + " ") || n.startsWith(pat + "("))) {
                return i;
            }
        }
        return -1;
    }

    // ---- favourites ----

    private static final String PREFS = "channels";

    private static Set<String> favs(Context c) {
        String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("favs", "");
        Set<String> s = new LinkedHashSet<>();
        for (String k : raw.split("\n")) if (!k.isEmpty()) s.add(k);
        return s;
    }

    static boolean isFavourite(Context c, Channel ch) {
        return favs(c).contains(ch.key());
    }

    /** Stars or un-stars a channel; returns the new state. */
    static boolean toggleFavourite(Context c, Channel ch) {
        Set<String> s = favs(c);
        boolean now = !s.remove(ch.key());
        if (now) s.add(ch.key());
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString("favs", android.text.TextUtils.join("\n", s)).apply();
        return now;
    }

    static List<Channel> favourites(Context c, List<Channel> all) {
        Map<String, Channel> byKey = new HashMap<>();
        for (Channel ch : all) byKey.put(ch.key(), ch);
        List<Channel> out = new ArrayList<>();
        for (String k : favs(c)) {
            Channel ch = byKey.get(k);
            if (ch != null) out.add(ch);
        }
        return out;
    }

    /**
     * Favourites, then this list's picks in pick order, then the rest unchanged (stable).
     * Each pick pins only its first match, so "Red Bull TV" pins one channel, not every regional copy.
     * The pinned channels are added to {@code pinnedOut} for the TOP PICK tag.
     */
    static List<Channel> ordered(Context c, String listId, List<Channel> list, Set<Channel> pinnedOut) {
        return ordered(c, listId, list, pinnedOut, true);
    }

    /** As above; without favourites first (for the guide's fixed channel numbers). */
    static List<Channel> ordered(Context c, String listId, List<Channel> list, Set<Channel> pinnedOut, boolean favouritesFirst) {
        final Set<String> f = favouritesFirst ? favs(c) : new LinkedHashSet<String>();
        final Map<Channel, Integer> score = new HashMap<>();
        final Set<Integer> used = new java.util.HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            Channel ch = list.get(i);
            int r = rank(listId, ch);
            boolean pinned = r >= 0 && used.add(r);
            if (pinned) pinnedOut.add(ch);
            score.put(ch, f.contains(ch.key()) ? -1 : pinned ? r : 100000 + i);
        }
        List<Channel> out = new ArrayList<>(list);
        Collections.sort(out, (a, b) -> Integer.compare(score.get(a), score.get(b)));
        return out;
    }
}
