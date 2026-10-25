package com.tv98.glow;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * TV guide (now / next).
 *  - UK and US channels: epg.pw's daily XMLTV files (gzip), streamed and parsed without ever holding the
 *    file in memory; only today's programmes for channels Glow actually has are kept, in a small index.
 *  - Thai channels: TrueID's per-channel schedule, fetched on demand and kept for 30 minutes.
 */
final class Guide {
    static final class Prog {
        final long start, stop;
        final String title;
        /** First sentence or so of the synopsis, or null. */
        final String desc;

        Prog(long start, long stop, String title) {
            this(start, stop, title, null);
        }

        Prog(long start, long stop, String title, String desc) {
            this.start = start;
            this.stop = stop;
            this.title = title;
            this.desc = desc == null || desc.isEmpty() ? null : desc;
        }
    }

    private static final int DESC_MAX = 160;

    interface Listener {
        void updated();
    }

    private static final String[][] FEEDS = {
            {"https://epg.pw/xmltv/epg_GB.xml.gz", "gb"},
            {"https://epg.pw/xmltv/epg_US.xml.gz", "us"},
    };
    private static final long FEED_FRESH_MS = 20 * 60 * 60 * 1000L;
    /**
     * Pluto TV's own schedules (matthuisman/i.mjh.nz, about 20 h ahead, 1.5 MB a day). They're matched on the
     * Pluto channel id inside the stream link (jmp2.uk/plu-<id>), so they're exact, and they cover most of the
     * free Entertainment, Movies and Kids channels that epg.pw doesn't. Refreshed every 6 hours.
     */
    private static final String[] PLUTO_FEEDS = {"https://i.mjh.nz/PlutoTV/us.xml.gz", "https://i.mjh.nz/PlutoTV/gb.xml.gz"};
    private static final long PLUTO_FRESH_MS = 6 * 60 * 60 * 1000L;
    private static final Set<String> plutoKeys = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final long KEEP_BEFORE_MS = 60 * 60 * 1000L, KEEP_AFTER_MS = 26 * 60 * 60 * 1000L;
    private static final long THAI_FRESH_MS = 30 * 60 * 1000L;

    /** Thai channels (iptv-org id) -> TrueID channel slug. Built from iptv-org's guide database + TrueID's list. */
    private static final Map<String, String> TRUEID = new HashMap<>();

    static {
        String[] m = {
                "3hd.th", "ch3-hd", "channel7.th", "ch7-hd", "workpointtv.th", "workpointtv", "thairathtv.th", "thairathtv-hd",
                "thaipbs.th", "thaipbs", "amarintv.th", "amarintv-hd", "gmm25.th", "gmm25", "mcothd.th", "9mcot-hd",
                "channel8.th", "ch8", "nationtv.th", "nationtv", "nbt2hd.th", "nbt", "true4u.th", "true4u",
                "one31.th", "one-hd", "topnews.th", "tid-top-news"};
        for (int i = 0; i < m.length; i += 2) TRUEID.put(m[i], m[i + 1]);
    }

    private static final Map<String, List<Prog>> byKey = new ConcurrentHashMap<>();
    private static final Map<String, Long> thaiFetched = new ConcurrentHashMap<>();
    private static final Set<String> thaiInFlight = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static volatile boolean loaded, refreshing;
    private static String trueIdBuild;
    private static long trueIdBuildAt;

    private Guide() {}

    // ---- lookups (cheap; safe on the main thread once loaded) ----

    static Prog now(Channel ch) {
        List<Prog> l = byKey.get(ch.key());
        if (l == null) return null;
        long t = System.currentTimeMillis();
        for (Prog p : l) if (p.start <= t && t < p.stop) return p;
        return null;
    }

    static Prog next(Channel ch) {
        List<Prog> l = byKey.get(ch.key());
        if (l == null) return null;
        long t = System.currentTimeMillis();
        for (Prog p : l) if (p.start > t) return p;
        return null;
    }

    /**
     * One programme at a time: sorted by start, and where listings overlap (some feeds carry a long
     * "1pm-7am" block on top of the real shows) the earlier one is cut off where the next begins.
     */
    static List<Prog> tidy(List<Prog> in) {
        List<Prog> sorted = new ArrayList<>(in);
        java.util.Collections.sort(sorted, (a, b) -> Long.compare(a.start, b.start));
        List<Prog> out = new ArrayList<>(sorted.size());
        for (Prog p : sorted) {
            if (p.stop <= p.start) continue;
            if (!out.isEmpty()) {
                Prog last = out.get(out.size() - 1);
                if (p.start == last.start) continue; // duplicate slot: keep the first
                if (last.stop > p.start) out.set(out.size() - 1, new Prog(last.start, p.start, last.title, last.desc));
            }
            out.add(p);
        }
        return out;
    }

    /** True when the saved guide (or a fetched Thai schedule) has programmes for this channel. */
    static boolean hasData(Channel ch) {
        return byKey.containsKey(ch.key());
    }

    static boolean has(Channel ch) {
        return byKey.containsKey(ch.key()) || hasThai(ch);
    }

    /** Reads the saved index only (a few ms) - no network. Call off the main thread. */
    static void loadSaved(Context ctx) {
        if (loaded) return;
        synchronized (Guide.class) {
            if (!loaded) {
                readIndex(ctx.getApplicationContext());
                loaded = true;
            }
        }
    }

    /** Programmes overlapping [from, to), in time order (empty when there's no guide for the channel). */
    static List<Prog> between(Channel ch, long from, long to) {
        List<Prog> l = byKey.get(ch.key());
        List<Prog> out = new ArrayList<>();
        if (l == null) return out;
        for (Prog p : l) if (p.stop > from && p.start < to) out.add(p);
        return out;
    }

    static String clock(long t) {
        return new SimpleDateFormat("HH:mm", Locale.ROOT).format(new java.util.Date(t));
    }

    // ---- loading ----

    private static File index(Context c) {
        return new File(c.getFilesDir(), "guide.idx");
    }

    private static File plutoIndex(Context c) {
        return new File(c.getFilesDir(), "guide-pluto.idx");
    }

    /** Loads the saved index and refreshes the UK/US guide if it's a day old. Call off the main thread. */
    static void ensure(Context ctx, Listener l) {
        Context c = ctx.getApplicationContext();
        if (!loaded) {
            loadSaved(c);
            if (l != null) l.updated();
        }
        if (refreshing) return;
        refreshing = true;
        try {
            File p = plutoIndex(c);
            if (!p.exists() || System.currentTimeMillis() - p.lastModified() > PLUTO_FRESH_MS) {
                refreshPluto(c);
                if (l != null) l.updated();
            }
            File f = index(c);
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > FEED_FRESH_MS) {
                refreshFeeds(c);
                if (l != null) l.updated();
            }
        } finally {
            refreshing = false;
        }
    }

    /** "https://jmp2.uk/plu-6462190b7cb4b1000862dd60.m3u8" -> "6462190b7cb4b1000862dd60", else null. */
    static String plutoId(Channel ch) {
        if (ch.url == null) return null;
        int at = ch.url.indexOf("jmp2.uk/plu-");
        if (at < 0) return null;
        int from = at + "jmp2.uk/plu-".length(), to = from;
        while (to < ch.url.length() && Character.isLetterOrDigit(ch.url.charAt(to))) to++;
        return to > from ? ch.url.substring(from, to) : null;
    }

    private static void refreshPluto(Context c) {
        Map<String, List<String>> byId = new HashMap<>();
        for (Channel ch : FreeTv.cached(c)) {
            String id = plutoId(ch);
            if (id != null) byId.computeIfAbsent(id, k -> new ArrayList<>()).add(ch.key());
        }
        if (byId.isEmpty()) return;
        Map<String, List<Prog>> fresh = new HashMap<>();
        for (String url : PLUTO_FEEDS) parseFeed(url, "pluto", null, byId, fresh);
        if (fresh.isEmpty()) return;
        for (String k : plutoKeys) if (!thaiFetched.containsKey(k)) byKey.remove(k);
        plutoKeys.clear();
        for (Map.Entry<String, List<Prog>> e : fresh.entrySet()) {
            byKey.put(e.getKey(), tidy(e.getValue()));
            plutoKeys.add(e.getKey());
        }
        writeIndex(c, fresh, plutoIndex(c));
    }

    private static String norm(String s) {
        String n = s.toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (char ch : n.toCharArray()) if (Character.isLetterOrDigit(ch)) b.append(ch);
        String r = b.toString();
        for (String suffix : new String[] {"uhd", "fhd", "hd", "sd", "4k"}) {
            if (r.endsWith(suffix) && r.length() > suffix.length() + 2) {
                r = r.substring(0, r.length() - suffix.length());
                break;
            }
        }
        return r;
    }

    private static void refreshFeeds(Context c) {
        // Our channels by normalised name, per country, so "Sky News HD" in the guide finds "Sky News" in Glow.
        Map<String, List<Channel>> mine = new HashMap<>();
        for (Channel ch : FreeTv.cached(c)) {
            mine.computeIfAbsent(norm(ch.name), k -> new ArrayList<>()).add(ch);
        }
        Map<String, List<Prog>> fresh = new HashMap<>();
        for (String[] feed : FEEDS) parseFeed(feed[0], feed[1], mine, null, fresh);
        if (fresh.isEmpty()) return;
        for (Map.Entry<String, List<Prog>> e : fresh.entrySet()) {
            java.util.Collections.sort(e.getValue(), (a, b) -> Long.compare(a.start, b.start));
        }
        // Keep Thai and Pluto entries; replace the epg.pw part. Pluto's own schedule wins where both have a channel.
        for (String k : new ArrayList<>(byKey.keySet())) if (!thaiFetched.containsKey(k) && !plutoKeys.contains(k)) byKey.remove(k);
        for (Map.Entry<String, List<Prog>> e : fresh.entrySet()) {
            if (!plutoKeys.contains(e.getKey())) byKey.put(e.getKey(), tidy(e.getValue()));
        }
        writeIndex(c, fresh, index(c));
    }

    private static boolean countryFits(Channel ch, String feedCountry) {
        String cc = ch.country;
        if (cc == null) return true;
        return feedCountry.equals("gb") ? cc.equals("uk") || cc.equals("gb") : cc.equals(feedCountry);
    }

    /**
     * Streams one gzip XMLTV file: <channel> names first, then <programme> entries. Channels are matched by
     * name ({@code mine}) or, when {@code byId} is given, by the feed's own channel ids.
     */
    private static void parseFeed(String url, String country, Map<String, List<Channel>> mine, Map<String, List<String>> byId,
            Map<String, List<Prog>> out) {
        long t0 = SystemClock.elapsedRealtime();
        Runtime rt = Runtime.getRuntime();
        long heap0 = rt.totalMemory() - rt.freeMemory();
        long peak = heap0;
        int programmes = 0, kept = 0, matchedChannels = 0;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("User-Agent", "Glow/1.0 (Android TV)");
            try (InputStream in = new GZIPInputStream(new BufferedInputStream(conn.getInputStream(), 64 * 1024), 64 * 1024)) {
                Map<String, List<String>> idToKeys = byId != null ? byId : new HashMap<>();
                XmltvScan.Names names = byId != null ? null : displayName -> {
                    List<Channel> match = mine.get(norm(displayName));
                    if (match == null) return null;
                    List<String> keys = new ArrayList<>();
                    for (Channel ch : match) if (countryFits(ch, country)) keys.add(ch.key());
                    return keys;
                };
                long now = System.currentTimeMillis();
                XmltvScan.Stats st = XmltvScan.scan(in, idToKeys, names, now - KEEP_BEFORE_MS, now + KEEP_AFTER_MS, out, DESC_MAX);
                programmes = st.programmes;
                kept = st.kept;
                matchedChannels = byId != null ? byId.size() : st.channelsMatched;
                peak = Math.max(peak, rt.totalMemory() - rt.freeMemory());
            }
        } catch (Exception e) {
            Log.i("GlowPerf", "guide " + country + " failed: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        Log.i("GlowPerf", "guide " + country + ": " + programmes + " programmes read, " + kept + " kept for "
                + matchedChannels + " channels in " + (SystemClock.elapsedRealtime() - t0) + " ms, peak Java heap +"
                + (Math.max(0, peak - heap0) / 1024) + " KB");
    }

    private static void writeIndex(Context c, Map<String, List<Prog>> data, File target) {
        File tmp = new File(c.getFilesDir(), target.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(tmp), 32 * 1024))) {
            out.writeInt(2); // v2: + description
            out.writeInt(data.size());
            for (Map.Entry<String, List<Prog>> e : data.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue().size());
                for (Prog p : e.getValue()) {
                    out.writeLong(p.start);
                    out.writeLong(p.stop);
                    out.writeUTF(p.title.length() > 200 ? p.title.substring(0, 200) : p.title);
                    out.writeUTF(p.desc == null ? "" : p.desc);
                }
            }
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        tmp.renameTo(target);
    }

    private static void readIndex(Context c) {
        readIndex(index(c), false);
        readIndex(plutoIndex(c), true); // after epg.pw, so Pluto's exact schedules win
    }

    private static void readIndex(File f, boolean pluto) {
        if (!f.exists()) return;
        long t0 = SystemClock.elapsedRealtime();
        int n = 0;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 32 * 1024))) {
            int version = in.readInt();
            if (version != 1 && version != 2) return;
            if (version == 1) f.setLastModified(0); // an older index without descriptions: refresh it soon
            int channels = in.readInt();
            long cutoff = System.currentTimeMillis() - KEEP_BEFORE_MS;
            for (int i = 0; i < channels; i++) {
                String key = in.readUTF();
                int count = in.readInt();
                List<Prog> l = new ArrayList<>(count);
                for (int j = 0; j < count; j++) {
                    long s = in.readLong(), e = in.readLong();
                    String t = in.readUTF();
                    String d = version >= 2 ? in.readUTF() : null;
                    if (e >= cutoff) l.add(new Prog(s, e, t, d));
                }
                if (!l.isEmpty()) {
                    l = tidy(l);
                    byKey.put(key, l);
                    if (pluto) plutoKeys.add(key);
                    n += l.size();
                }
            }
        } catch (Exception ignored) {
        }
        Log.i("GlowPerf", "guide index " + f.getName() + ": " + byKey.size() + " channels, " + n + " programmes in " + (SystemClock.elapsedRealtime() - t0) + " ms");
    }

    // ---- Thai channels: TrueID, on demand ----

    static boolean hasThai(Channel ch) {
        return ch.tvgId != null && TRUEID.containsKey(ch.tvgId);
    }

    /** Fetches a Thai channel's schedule in the background if it's missing or older than 30 minutes. */
    static void wantThai(Context c, Channel ch, Listener l) {
        if (!hasThai(ch)) return;
        final String key = ch.key();
        Long at = thaiFetched.get(key);
        long now = System.currentTimeMillis();
        List<Prog> have = byKey.get(key);
        // TrueID only lists the rest of today: near midnight, check again every 5 minutes for tomorrow's.
        boolean runningOut = have == null || have.isEmpty() || have.get(have.size() - 1).stop < now + 60 * 60 * 1000L;
        if (at != null && now - at < (runningOut ? 5 * 60 * 1000L : THAI_FRESH_MS)) return;
        if (!thaiInFlight.add(key)) return;
        final Context app = c.getApplicationContext();
        Net.IO.execute(() -> {
            try {
                List<Prog> progs = fetchTrueId(TRUEID.get(ch.tvgId));
                if (!progs.isEmpty()) {
                    byKey.put(key, tidy(progs));
                    thaiFetched.put(key, System.currentTimeMillis());
                    if (l != null) Net.MAIN.post(l::updated);
                }
            } catch (Exception ignored) {
            } finally {
                thaiInFlight.remove(key);
            }
        });
    }

    private static synchronized String trueIdBuild() throws Exception {
        if (trueIdBuild != null && System.currentTimeMillis() - trueIdBuildAt < 6 * 60 * 60 * 1000L) return trueIdBuild;
        String html = Net.text("https://tv.trueid.net/th-en");
        int at = html.indexOf("\"buildId\":\"");
        if (at < 0) throw new Exception("no build id");
        int s = at + 11, e = html.indexOf('"', s);
        trueIdBuild = html.substring(s, e);
        trueIdBuildAt = System.currentTimeMillis();
        return trueIdBuild;
    }

    private static List<Prog> fetchTrueId(String slug) throws Exception {
        String body = Net.text("https://tv.trueid.net/_next/data/" + trueIdBuild() + "/th-en.json?channelSlug=" + slug + "&path=" + slug);
        JSONArray epg = new JSONObject(body).getJSONObject("pageProps").optJSONArray("epgList");
        List<Prog> out = new ArrayList<>();
        if (epg == null) return out;
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < epg.length(); i++) {
            JSONObject p = epg.getJSONObject(i);
            out.add(new Prog(iso.parse(p.getString("start_date")).getTime(), iso.parse(p.getString("end_date")).getTime(),
                    p.optString("title").trim()));
        }
        return out;
    }
}
