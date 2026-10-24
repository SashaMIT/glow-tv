package com.tv98.glow;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Headlines from World Monitor's public news digest (worldmonitor.app), the same keyless feed its
 * terminal dashboard reads. Cached on disk; refreshed at most every 10 minutes.
 */
final class World {
    static final class Story {
        String title, source, snippet, category, threat;
        long at;
        boolean alert;
        int importance;
        /** Other outlets carrying the same story (folded into this one in Top stories and Alerts). */
        int moreSources;
        Set<String> words;
    }

    private static final String URL =
            "https://api.worldmonitor.app/api/news/v1/list-feed-digest?variant=full&lang=en&public=1";
    private static final String FILE = "world.json";
    private static final long FRESH_MS = 10 * 60 * 1000L;

    /** {id, label}. "__top" and "__alerts" are built from all categories. */
    static final String[][] SECTIONS = {
            {"__top", "Top stories"}, {"__alerts", "Alerts"},
            {"asia", "Asia"}, {"europe", "Europe"}, {"us", "US"}, {"middleeast", "Middle East"},
            {"africa", "Africa"}, {"latam", "Latin America"}, {"politics", "Politics"}, {"gov", "Government"},
            {"tech", "Tech"}, {"ai", "AI"}, {"science", "Science"}, {"finance", "Finance"},
            {"commodities", "Commodities"}, {"energy", "Energy"}, {"thinktanks", "Think tanks"},
            {"intel", "Intelligence"}, {"layoffs", "Layoffs"}, {"crisis", "Crisis"},
    };

    private static List<Story> memo;
    private static long memoStamp = -1;

    private World() {}

    static boolean stale(Context c) {
        File f = new File(c.getFilesDir(), FILE);
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > FRESH_MS;
    }

    /** Downloads a fresh digest; keeps the previous one if this fails. Call off the main thread. */
    static void refresh(Context c) {
        try {
            byte[] body = Net.bytes(URL, 6 * 1024 * 1024);
            JSONObject root = new JSONObject(new String(body, "UTF-8"));
            if (root.optJSONObject("categories") == null) return;
            File tmp = new File(c.getFilesDir(), FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(body);
            }
            tmp.renameTo(new File(c.getFilesDir(), FILE));
        } catch (Exception ignored) {
        }
    }

    /** All stories from the saved digest. Call off the main thread (the file is a few hundred KB). */
    static synchronized List<Story> cached(Context c) {
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return new ArrayList<>();
        if (memo != null && f.lastModified() == memoStamp) return memo;
        List<Story> out = new ArrayList<>();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            JSONObject cats = new JSONObject(new String(b, "UTF-8")).getJSONObject("categories");
            for (Iterator<String> it = cats.keys(); it.hasNext(); ) {
                String cat = it.next();
                JSONArray items = cats.getJSONObject(cat).optJSONArray("items");
                if (items == null) continue;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject o = items.getJSONObject(i);
                    Story s = new Story();
                    s.title = o.optString("title").trim();
                    if (s.title.isEmpty()) continue;
                    s.source = o.optString("source");
                    s.snippet = o.optString("snippet").trim();
                    s.category = cat;
                    s.at = o.optLong("publishedAt");
                    s.alert = o.optBoolean("isAlert");
                    s.importance = o.optInt("importanceScore");
                    JSONObject threat = o.optJSONObject("threat");
                    s.threat = threat == null ? "" : threat.optString("level").replace("THREAT_LEVEL_", "");
                    out.add(s);
                }
            }
        } catch (Exception e) {
            return memo != null ? memo : new ArrayList<>();
        }
        memo = out;
        memoStamp = f.lastModified();
        return out;
    }

    static List<Story> section(List<Story> all, String id) {
        List<Story> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Story s : all) {
            boolean in = "__top".equals(id) || ("__alerts".equals(id) ? s.alert : id.equals(s.category));
            if (in && seen.add(s.title)) out.add(s);
        }
        if (id.startsWith("__")) {
            Collections.sort(out, (a, b) -> b.importance - a.importance);
            out = fold(out);
            if (out.size() > 50) out = new ArrayList<>(out.subList(0, 50));
        } else {
            Collections.sort(out, (a, b) -> Long.compare(b.at, a.at));
        }
        return out;
    }

    /** Keeps the most important version of each story; near-identical headlines count as more sources. */
    private static List<Story> fold(List<Story> ranked) {
        List<Story> kept = new ArrayList<>();
        for (Story s : ranked) {
            if (s.words == null) s.words = words(s.title);
            Story same = null;
            for (Story k : kept) {
                if (similar(k.words, s.words)) {
                    same = k;
                    break;
                }
            }
            if (same == null) {
                Story copy = new Story(); // per-list copy, so lists don't overwrite each other's counts
                copy.title = s.title;
                copy.source = s.source;
                copy.snippet = s.snippet;
                copy.category = s.category;
                copy.threat = s.threat;
                copy.at = s.at;
                copy.alert = s.alert;
                copy.importance = s.importance;
                copy.words = s.words;
                kept.add(copy);
            } else {
                same.moreSources++;
            }
        }
        return kept;
    }

    private static Set<String> words(String title) {
        Set<String> w = new HashSet<>();
        for (String t : title.toLowerCase().split("[^\\p{L}\\p{N}]+")) if (t.length() >= 4) w.add(t);
        return w;
    }

    /** Share at least 3 meaningful words and over a third of them. */
    private static boolean similar(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        int common = 0;
        for (String w : a) if (b.contains(w)) common++;
        int union = a.size() + b.size() - common;
        return common >= 3 && common * 3 >= union;
    }

    static String ago(long at) {
        long min = Math.max(0, (System.currentTimeMillis() - at) / 60000);
        if (at <= 0) return "";
        if (min < 1) return "just now";
        if (min < 60) return min + " min ago";
        if (min < 48 * 60) return (min / 60) + " h ago";
        return (min / 1440) + " days ago";
    }

    /** Red for high/critical, amber for elevated/medium, soft blue otherwise. */
    static int color(Story s) {
        if (s.threat.equals("CRITICAL") || s.threat.equals("HIGH")) return 0xFFFF7A6B;
        if (s.threat.equals("ELEVATED") || s.threat.equals("MEDIUM")) return 0xFFFFC66B;
        return Ui.ACCENT;
    }
}
