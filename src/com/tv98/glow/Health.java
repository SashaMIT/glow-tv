package com.tv98.glow;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Checks free channel links from the box itself (its own country and connection), so lists can hide
 * channels that are geo-blocked, removed or down. Each check reads only the first 2 KB of the playlist.
 */
final class Health {
    private static final String FILE = "health.tsv";
    private static final long RECHECK_MS = 12 * 60 * 60 * 1000L;
    private static final int THREADS = 6;

    private static Map<String, Boolean> results;
    static volatile boolean running;
    static final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
    static volatile int total;

    private Health() {}

    private static synchronized Map<String, Boolean> results(Context c) {
        if (results != null) return results;
        results = new ConcurrentHashMap<>();
        File f = new File(c.getFilesDir(), FILE);
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) results.put(line.substring(tab + 1), line.startsWith("ok"));
                }
            } catch (Exception ignored) {
            }
        }
        return results;
    }

    /** True only when the link was checked and failed; unchecked channels count as fine. */
    static boolean isDead(Context c, Channel ch) {
        if (ch.url == null) return false;
        Boolean ok = results(c).get(ch.url);
        return ok != null && !ok;
    }

    /** A channel that actually played is working, whatever the last check said. */
    static void markWorking(Context c, Channel ch) {
        if (ch.url != null) results(c).put(ch.url, true);
    }

    static void markWorking(Context c, String url) {
        if (url != null) results(c).put(url, true);
    }

    /** Starts a check unless one is already running (home and the browser both ask; only one may run). */
    static void scanIfDue(Context ctx) {
        final Context c = ctx.getApplicationContext();
        final List<Channel> all = FreeTv.cached(c);
        if (all.isEmpty()) return;
        synchronized (Health.class) {
            if (running) return;
            File f = new File(c.getFilesDir(), FILE);
            boolean fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < RECHECK_MS;
            if (fresh && results(c).keySet().containsAll(urls(all).keySet())) return;
            running = true;
            final boolean onlyMissing = fresh; // recent results stand; just fill the gaps
            new Thread(() -> scan(c, all, onlyMissing), "glow-health").start();
        }
    }

    private static Map<String, Channel> urls(List<Channel> all) {
        Map<String, Channel> u = new LinkedHashMap<>();
        for (Channel ch : all) if (ch.url != null) u.put(ch.url, ch);
        return u;
    }

    private static void scan(Context c, List<Channel> all, boolean onlyMissing) {
        final Map<String, Boolean> map = results(c);
        final Map<String, Channel> todo = urls(all);
        if (onlyMissing) todo.keySet().removeAll(map.keySet());
        total = todo.size();
        done.set(0);
        ExecutorService ex = Executors.newFixedThreadPool(THREADS);
        for (final Channel ch : todo.values()) {
            ex.execute(() -> {
                map.put(ch.url, check(ch));
                if (done.incrementAndGet() % 250 == 0) save(c, map); // keep progress if the app is stopped mid-scan
            });
        }
        ex.shutdown();
        try {
            ex.awaitTermination(2, TimeUnit.HOURS);
        } catch (InterruptedException ignored) {
        }
        save(c, map);
        running = false;
    }

    private static synchronized void save(Context c, Map<String, Boolean> map) {
        File tmp = new File(c.getFilesDir(), FILE + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Boolean> e : new ArrayList<>(map.entrySet())) {
                w.write(e.getValue() ? "ok\t" : "dead\t");
                w.write(e.getKey());
                w.write('\n');
            }
        } catch (Exception e) {
            return;
        }
        tmp.renameTo(new File(c.getFilesDir(), FILE));
    }

    /** Reachable, allowed from here, and a format the box's player can open. */
    static boolean check(Channel ch) {
        return check(ch, 6000, 6000);
    }

    /** Same check with custom timeouts (the player's quick probes use shorter ones). */
    static boolean check(Channel ch, int connectMs, int readMs) {
        String url = ch.url;
        if (url.contains(".mpd")) return false; // DASH: the box's built-in player can't play it
        HttpURLConnection conn = null;
        try {
            for (int hop = 0; hop < 4; hop++) {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(connectMs);
                conn.setReadTimeout(readMs);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", ch.userAgent != null ? ch.userAgent : "Mozilla/5.0 (Linux; Android 10)");
                if (ch.referrer != null) conn.setRequestProperty("Referer", ch.referrer);
                int code = conn.getResponseCode();
                String loc = conn.getHeaderField("Location");
                if (code >= 300 && code < 400 && loc != null) {
                    url = new URL(new URL(url), loc).toString();
                    conn.disconnect();
                    continue;
                }
                if (code != 200) return false;
                byte[] head = new byte[2048];
                int n = 0, r;
                try (InputStream in = conn.getInputStream()) {
                    while (n < head.length && (r = in.read(head, n, head.length - n)) > 0) n += r;
                }
                String start = new String(head, 0, n, StandardCharsets.UTF_8);
                return !url.contains(".m3u8") || start.contains("#EXTM3U");
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
