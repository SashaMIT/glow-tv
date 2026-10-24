package com.tv98.glow;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The ordered list of ways to try playing one channel. The player walks it until something draws a frame:
 *
 *   1. the link that last worked for this channel (remembered per channel),
 *   2. the chosen link, then every other link for the same channel (same iptv-org id, or the same name
 *      in another list), dead-checked links last,
 *   3. finally the box's built-in player on the chosen link, which occasionally manages what ExoPlayer can't.
 */
final class Sources {
    static final class Attempt {
        final String url;
        final Map<String, String> headers;
        final boolean builtIn;
        /** Set for the "official YouTube live" step: the broadcaster's YouTube channels, resolved only when reached. */
        final List<String> youtubeChannels;

        Attempt(String url, Map<String, String> headers, boolean builtIn) {
            this(url, headers, builtIn, null);
        }

        Attempt(String url, Map<String, String> headers, boolean builtIn, List<String> youtubeChannels) {
            this.url = url;
            this.headers = headers;
            this.builtIn = builtIn;
            this.youtubeChannels = youtubeChannels;
        }
    }

    private static final String PREFS = "channels";

    private Sources() {}

    /** Call off the main thread: may parse the free lists the first time. */
    static List<Attempt> forChannel(Context c, Channel ch, Xtream.Account acct) {
        List<Attempt> out = new ArrayList<>();
        if (ch.url == null) { // My IPTV: the provider's HLS link, then its raw MPEG-TS link
            if (acct == null) return out;
            String hls = Xtream.streamUrl(acct, ch, "m3u8");
            out.add(new Attempt(hls, null, false));
            out.add(new Attempt(Xtream.streamUrl(acct, ch, "ts"), null, false));
            out.add(new Attempt(hls, null, true));
            return out;
        }

        Map<String, Channel> links = new LinkedHashMap<>();
        String good = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("good:" + identity(ch), null);
        List<Channel> same = alternates(c, ch);
        if (good != null) {
            for (Channel alt : same) if (alt.url.equals(good)) links.put(alt.url, alt);
        }
        links.put(ch.url, ch);
        List<Channel> doubtful = new ArrayList<>();
        for (Channel alt : same) {
            if (links.containsKey(alt.url)) continue;
            if (Health.isDead(c, alt)) doubtful.add(alt);
            else links.put(alt.url, alt);
        }
        for (Channel alt : doubtful) links.put(alt.url, alt);

        for (Channel link : links.values()) {
            if (link.url.contains(".mpd")) continue; // DASH isn't wired in; skip rather than waste a try
            out.add(new Attempt(link.url, link.headers(), false));
        }
        // Broadcasters' own links and their YouTube live stream, for well-known news channels.
        String[] official = OfficialLive.match(ch);
        if (official != null) {
            for (String s : OfficialLive.streams(official)) {
                if (!links.containsKey(s)) out.add(new Attempt(s, null, false));
            }
        }
        List<String> youtube = OfficialLive.youtubeChannels(ch);
        if (!youtube.isEmpty()) out.add(new Attempt(null, null, false, youtube));
        out.add(new Attempt(ch.url, ch.headers(), true));
        return out;
    }

    /** Other links for the same channel across all free lists. */
    private static List<Channel> alternates(Context c, Channel ch) {
        List<Channel> out = new ArrayList<>();
        String id = identity(ch);
        for (Channel other : FreeTv.cached(c)) {
            if (other.url != null && !other.url.equals(ch.url) && id.equals(identity(other))) out.add(other);
        }
        return out;
    }

    /** iptv-org id when there is one, otherwise the name without "HD"/"SD" style suffixes. */
    private static String identity(Channel ch) {
        if (ch.tvgId != null) return ch.tvgId;
        return ch.name.toLowerCase(Locale.ROOT).replaceAll("\\s+(hd|sd|fhd|uhd|4k)$", "").trim();
    }

    private static final java.util.concurrent.ExecutorService PROBES = java.util.concurrent.Executors.newFixedThreadPool(4);

    interface ProbeResult {
        void done(int attemptIndex, boolean ok);
    }

    /**
     * Checks every link of a channel at the same time (first 2 KB of each playlist) so the player can
     * skip dead links at once instead of waiting out its timer on each.
     */
    static void probeAll(List<Attempt> attempts, ProbeResult cb) {
        for (int i = 0; i < attempts.size(); i++) {
            final int k = i;
            final Attempt a = attempts.get(i);
            if (a.builtIn || a.youtubeChannels != null) continue;
            PROBES.execute(() -> {
                Channel probe = new Channel(0, 0, "", "", null, a.url,
                        a.headers == null ? null : a.headers.get("User-Agent"),
                        a.headers == null ? null : a.headers.get("Referer"));
                cb.done(k, Health.check(probe, 3000, 4000)); // a link that can't answer in ~4 s isn't worth waiting on
            });
        }
    }

    /**
     * Opens a link's playlist ahead of time so DNS, the TCP/TLS connection and the server are warm when the
     * viewer zaps to it. The connection is returned to Android's keep-alive pool rather than closed.
     */
    static void warm(Attempt a) {
        if (a.url == null || a.builtIn) return;
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(a.url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", a.headers != null && a.headers.get("User-Agent") != null
                    ? a.headers.get("User-Agent") : "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 Glow/1.0");
            if (a.headers != null && a.headers.get("Referer") != null) c.setRequestProperty("Referer", a.headers.get("Referer"));
            try (java.io.InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8192];
                int total = 0, n;
                while ((n = in.read(buf)) > 0 && (total += n) < 256 * 1024) { /* drain so the socket can be reused */ }
            }
        } catch (Exception ignored) {
        }
    }

    static void rememberWorking(Context c, Channel ch, String url) {
        if (ch.url == null) return;
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString("good:" + identity(ch), url).apply();
    }
}
