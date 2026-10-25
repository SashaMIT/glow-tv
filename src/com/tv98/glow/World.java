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
        String title, source, snippet, category, threat, link;
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
                    s.title = plain(o.optString("title"));
                    if (s.title.isEmpty()) continue;
                    s.source = o.optString("source");
                    s.link = o.optString("link");
                    s.snippet = plain(o.optString("snippet"));
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
                copy.link = s.link;
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

    // ---- pictures: the article's own preview image (og:image), looked up when a story is shown ----

    private static final java.util.Map<String, String> IMAGES = new java.util.concurrent.ConcurrentHashMap<>();

    interface ImageFound {
        void found(String imageUrl);
    }

    /** The story's preview picture URL ("" if none) — from memory, else read from the article's <head> in the background. */
    static void image(final Story s, final ImageFound cb) {
        if (s.link == null || s.link.isEmpty()) {
            cb.found("");
            return;
        }
        String known = IMAGES.get(s.link);
        if (known != null) {
            cb.found(known);
            return;
        }
        Net.IO.execute(() -> {
            String img = "";
            try {
                img = previewImage(s.link, Net.head(s.link, 192 * 1024));
            } catch (Exception ignored) {
            }
            if (IMAGES.size() > 400) IMAGES.clear();
            IMAGES.put(s.link, img);
            final String found = img;
            Net.MAIN.post(() -> cb.found(found));
        });
    }

    /** og:image, else twitter:image, from a page's head; resolved against the page address. */
    static String previewImage(String page, String html) {
        for (String key : new String[] {"og:image", "twitter:image"}) {
            int at = 0;
            while ((at = html.indexOf(key, at)) >= 0) {
                int open = html.lastIndexOf('<', at), close = html.indexOf('>', at);
                at += key.length();
                if (open < 0 || close < 0) continue;
                String tag = html.substring(open, close);
                if (!tag.startsWith("<meta")) continue;
                int c = tag.indexOf("content=");
                if (c < 0) continue;
                char q = tag.charAt(c + 8);
                int end = q == '"' || q == '\'' ? tag.indexOf(q, c + 9) : tag.indexOf(' ', c + 8);
                String v = tag.substring(q == '"' || q == '\'' ? c + 9 : c + 8, end < 0 ? tag.length() : end).replace("&amp;", "&").trim();
                if (v.isEmpty()) continue;
                try {
                    return new java.net.URL(new java.net.URL(page), v).toString();
                } catch (Exception e) {
                    return "";
                }
            }
        }
        return "";
    }

    // ---- "watch live": a news channel that covers the story's part of the world ----

    private static final String[][] LIVE = {
            {"middleeast", "al jazeera english", "france 24 english", "dw english"},
            {"africa", "al jazeera english", "france 24 english", "dw english"},
            {"europe", "dw english", "france 24 english", "euronews english", "bbc news", "sky news"},
            {"asia", "cna international", "nhk world-japan", "al jazeera english"},
            {"us", "abc news live", "nbc news now", "cbs news 24/7", "livenow from fox"},
            {"politics", "abc news live", "nbc news now", "cbs news 24/7"},
            {"gov", "abc news live", "nbc news now", "cbs news 24/7"},
            {"latam", "france 24 english", "dw english", "al jazeera english"},
            {"finance", "bloomberg tv", "cheddar news"},
            {"commodities", "bloomberg tv"},
            {"energy", "bloomberg tv"},
            {"tech", "bloomberg tv", "cheddar news"},
            {"ai", "bloomberg tv", "cheddar news"},
            {"*", "al jazeera english", "dw english", "france 24 english", "nbc news now"},
    };

    /** Stories from an outlet that has its own free channel go to that channel. {source contains, channels…} */
    private static final String[][] BY_SOURCE = {
            {"bbc", "bbc news", "bbc news uk hd"}, {"al jazeera", "al jazeera english", "al jazeera english hd"},
            {"france 24", "france 24 english"}, {"dw", "dw english"}, {"deutsche welle", "dw english"},
            {"cna", "cna international"}, {"channel newsasia", "cna international"}, {"nhk", "nhk world-japan"},
            {"bloomberg", "bloomberg tv"}, {"cbs", "cbs news 24/7"}, {"nbc", "nbc news now"}, {"abc news", "abc news live"},
            {"sky news", "sky news"}, {"euronews", "euronews english"}, {"fox", "livenow from fox"}, {"reuters", "reuters tv"},
            {"trt", "trt world"}, {"wion", "wion"}, {"arirang", "arirang tv"}, {"i24", "i24news english"},
            {"gb news", "gb news"}, {"thai pbs", "thai pbs"}, {"bangkok post", "thai pbs"}, {"thairath", "thairath tv"},
            {"financial times", "bloomberg tv"}, {"wall street journal", "bloomberg tv"}, {"cnbc", "bloomberg tv"},
            {"marketwatch", "bloomberg tv"}, {"yahoo finance", "bloomberg tv"}, {"economist", "bloomberg tv"},
    };

    /** Places named in the headline: {words…, "|", channels…}. The row naming the most places wins; ties go to the earlier row. */
    private static final String[][] BY_PLACE = {
            {"thailand", "thai", "bangkok", "|", "thai pbs", "thairath tv", "cna international"},
            {"japan", "japanese", "tokyo", "|", "nhk world-japan", "cna international"},
            {"korea", "korean", "seoul", "pyongyang", "|", "arirang tv", "nhk world-japan", "cna international"},
            {"singapore", "malaysia", "indonesia", "philippines", "vietnam", "myanmar", "cambodia", "laos", "asean",
                    "china", "chinese", "beijing", "taiwan", "hong kong", "|", "cna international", "nhk world-japan"},
            {"india", "indian", "delhi", "modi", "pakistan", "bangladesh", "sri lanka", "|", "wion", "cna international"},
            {"israel", "israeli", "gaza", "hamas", "hezbollah", "lebanon", "iran", "iranian", "tehran", "syria", "yemen",
                    "saudi", "qatar", "iraq", "jordan", "egypt", "|", "al jazeera english", "i24news english", "france 24 english"},
            {"turkey", "turkish", "erdogan", "ankara", "istanbul", "|", "trt world", "dw english"},
            {"ukraine", "ukrainian", "kyiv", "russia", "russian", "moscow", "kremlin", "putin", "zelensky", "|",
                    "dw english", "france 24 english", "euronews english", "bbc news"},
            {"france", "french", "paris", "macron", "|", "france 24 english", "euronews english"},
            {"germany", "german", "berlin", "merz", "|", "dw english", "euronews english"},
            {"britain", "british", "uk", "london", "england", "scotland", "starmer", "westminster", "|",
                    "bbc news", "sky news", "gb news"},
            {"trump", "white house", "congress", "senate", "washington", "biden", "pentagon", "fbi", "|",
                    "abc news live", "nbc news now", "cbs news 24/7", "livenow from fox"},
            {"africa", "african", "nigeria", "kenya", "ethiopia", "sudan", "congo", "somalia", "sahel", "|",
                    "al jazeera english", "france 24 english", "dw english"},
            {"brazil", "mexico", "argentina", "venezuela", "colombia", "chile", "peru", "cuba", "|",
                    "france 24 english", "dw english", "al jazeera english"},
            {"market", "markets", "stocks", "fed", "inflation", "oil price", "earnings", "|", "bloomberg tv", "cheddar news"},
    };

    /**
     * A working news channel for this story, or null: the outlet's own channel, else one covering the place in
     * the headline, else one for the story's region. Call off the main thread (reads the channel lists).
     */
    static Channel liveFor(Context c, Story s) {
        List<Channel> all = FreeTv.cached(c);
        String src = s.source == null ? "" : s.source.toLowerCase(java.util.Locale.ROOT);
        for (String[] row : BY_SOURCE) {
            if (hasWord(src, row[0])) {
                Channel ch = firstWorking(c, all, row, 1);
                if (ch != null) { lastReason = "outlet"; return ch; }
            }
        }
        String title = " " + (s.title == null ? "" : s.title.toLowerCase(java.util.Locale.ROOT)) + " ";
        String[] best = null;
        int bestHits = 0;
        String bestWord = "";
        for (String[] row : BY_PLACE) {
            int bar = java.util.Arrays.asList(row).indexOf("|"), hits = 0;
            String first = "";
            for (int i = 0; i < bar; i++) {
                if (hasWord(title, row[i])) {
                    if (hits == 0) first = row[i];
                    hits++;
                }
            }
            if (hits > bestHits && firstWorking(c, all, row, bar + 1) != null) {
                best = row;
                bestHits = hits;
                bestWord = first;
            }
        }
        if (best != null) {
            lastReason = "place: " + bestWord + (bestHits > 1 ? " +" + (bestHits - 1) : "");
            return firstWorking(c, all, best, java.util.Arrays.asList(best).indexOf("|") + 1);
        }
        for (String[] row : LIVE) {
            if (!row[0].equals(s.category) && !row[0].equals("*")) continue;
            Channel ch = firstWorking(c, all, row, 1);
            if (ch != null) { lastReason = "region: " + row[0]; return ch; }
        }
        return null;
    }

    /** Why the last liveFor() picked its channel (for the debug log). */
    static volatile String lastReason = "";

    /** Whole-word (or phrase) match, so "uk" doesn't match "ukraine" and "fed" doesn't match "federal". */
    private static boolean hasWord(String text, String word) {
        int at = 0;
        while ((at = text.indexOf(word, at)) >= 0) {
            boolean startOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            int end = at + word.length();
            boolean endOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) return true;
            at = end;
        }
        return false;
    }

    private static Channel firstWorking(Context c, List<Channel> all, String[] names, int from) {
        for (int i = from; i < names.length; i++) {
            for (Channel ch : all) {
                if (ch.name.toLowerCase(java.util.Locale.ROOT).equals(names[i]) && !Channel.isOffline(c, ch)) return ch;
            }
        }
        return null;
    }

    /** Feed text can carry HTML entities ("&nbsp;", "&#39;"): turn them into plain characters. */
    private static String plain(String s) {
        if (s == null) return "";
        String t = s.indexOf('&') >= 0 || s.indexOf('<') >= 0
                ? android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString() : s;
        return t.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
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
