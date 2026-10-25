package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A live channel from either source: an Xtream account (played via the account's stream URL)
 * or a free M3U playlist (played from its own URL, sometimes with required request headers).
 */
final class Channel {
    final int id, num;
    final String name, logo, categoryId;
    /** Direct stream URL for playlist channels; null for Xtream channels. */
    final String url;
    final String userAgent, referrer;
    /** Genre from the playlist ("News", "Music"...), or null. */
    final String group;
    /** Two-letter country code from the playlist's channel id ("uk", "th"...), or null. */
    String country;
    /** iptv-org channel id without the feed ("bbcnews.uk"): the same channel under other links shares it. */
    String tvgId;
    /** The playlist's full iptv-org id with its feed, e.g. "AlJazeera.qa@English" (tvgId is just "aljazeera.qa"). */
    String feedId;
    /** Lazily computed: does the broadcaster have an official YouTube channel Glow can fall back to? */
    private Boolean officialYoutube;

    boolean hasOfficialYoutube() {
        if (officialYoutube == null) officialYoutube = url != null && !OfficialLive.youtubeChannels(this).isEmpty();
        return officialYoutube;
    }

    Channel(int id, int num, String name, String logo, String categoryId) {
        this(id, num, name, logo, categoryId, null, null, null);
    }

    Channel(int id, int num, String name, String logo, String categoryId, String url, String userAgent, String referrer) {
        this(id, num, name, logo, categoryId, url, userAgent, referrer, null);
    }

    Channel(int id, int num, String name, String logo, String categoryId, String url, String userAgent, String referrer,
            String group) {
        this.group = group == null || group.isEmpty() || group.equalsIgnoreCase("Undefined") ? null : group.replace(';', ' ');
        this.id = id;
        this.num = num;
        this.name = name;
        this.logo = logo == null ? "" : logo;
        this.categoryId = categoryId;
        this.url = url;
        this.userAgent = userAgent;
        this.referrer = referrer;
    }

    private static final java.util.Map<String, String> COUNTRY_NAMES = new java.util.HashMap<>();

    /** "th" -> "Thailand", "uk" -> "United Kingdom"; null when unknown. */
    static String countryName(String code) {
        if (code == null || code.length() != 2) return null;
        String name = COUNTRY_NAMES.get(code);
        if (name == null) {
            String iso = code.equals("uk") ? "GB" : code.toUpperCase(java.util.Locale.ROOT);
            name = new java.util.Locale("", iso).getDisplayCountry(java.util.Locale.ENGLISH);
            if (name.equalsIgnoreCase(iso)) name = "";
            COUNTRY_NAMES.put(code, name);
        }
        return name.isEmpty() ? null : name;
    }

    /** Stable identity across app restarts, used for "recently watched" and "offline". */
    String key() {
        return url != null ? "f:" + url : "x:" + id;
    }

    Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        if (userAgent != null) h.put("User-Agent", userAgent);
        if (referrer != null) h.put("Referer", referrer);
        return h;
    }

    private static final String PREFS = "channels";
    private static final long OFFLINE_FOR_MS = 12 * 60 * 60 * 1000L;

    static void markWatched(Context c, Channel ch) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        StringBuilder b = new StringBuilder(ch.key());
        int kept = 1;
        for (String s : p.getString("recent", "").split("\n")) {
            if (s.isEmpty() || s.equals(ch.key())) continue;
            if (kept++ >= 12) break;
            b.append('\n').append(s);
        }
        p.edit().putString("recent", b.toString()).apply();
    }

    static List<Channel> recent(Context c, List<Channel> all) {
        Map<String, Channel> byKey = new HashMap<>();
        for (Channel ch : all) byKey.put(ch.key(), ch);
        List<Channel> out = new ArrayList<>();
        for (String s : c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("recent", "").split("\n")) {
            Channel ch = byKey.get(s);
            if (ch != null) out.add(ch);
        }
        return out;
    }

    /** Remember a channel that wouldn't play, so lists can show it as offline for a while. */
    static void markOffline(Context c, Channel ch, boolean offline) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (offline) e.putLong("off:" + ch.key(), System.currentTimeMillis());
        else e.remove("off:" + ch.key());
        e.apply();
    }

    /**
     * Offline = the link check failed or the channel recently failed to play. Channels with an official YouTube
     * fallback are never hidden by the link check, and a full failure only hides them for 30 minutes, because
     * broadcasters go live on YouTube at different times of day.
     */
    static boolean isOffline(Context c, Channel ch) {
        boolean youtube = ch.hasOfficialYoutube();
        if (!youtube && Health.isDead(c, ch)) return true;
        long at = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("off:" + ch.key(), 0);
        long window = youtube ? 30 * 60 * 1000L : OFFLINE_FOR_MS;
        return at > 0 && System.currentTimeMillis() - at < window;
    }
}
