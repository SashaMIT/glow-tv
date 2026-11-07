package com.tv98.glow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** How each outside data source answered last, so Glow can say which one is down. Streams aren't counted: they have their own checks. */
final class Feeds {
    private static final class Last {
        long at;
        String error;
    }

    private static final Map<String, Last> LAST = new TreeMap<>();

    private Feeds() {}

    /** "https://site.api.espn.com/a/b" → "site.api.espn.com"; "" when it isn't a web address. */
    static String host(String url) {
        int from = url == null ? -1 : url.indexOf("://");
        if (from < 0) return "";
        int to = from + 3;
        while (to < url.length() && "/?#:".indexOf(url.charAt(to)) < 0) to++;
        return url.substring(from + 3, to);
    }

    /** {@code error} null when it answered. */
    static synchronized void report(String url, String error, long now) {
        String h = host(url);
        if (h.isEmpty()) return;
        Last l = LAST.get(h);
        if (l == null) LAST.put(h, l = new Last());
        l.at = now;
        l.error = error;
    }

    /** Only for the tests. */
    static synchronized void clear() {
        LAST.clear();
    }

    static synchronized int answering() {
        int n = 0;
        for (Last l : LAST.values()) if (l.error == null) n++;
        return n;
    }

    /** "site.api.espn.com: HTTP 503, 5 min ago", one per source that failed on its last try. */
    static synchronized List<String> failing(long now) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Last> e : LAST.entrySet()) {
            Last l = e.getValue();
            if (l.error != null) out.add(e.getKey() + ": " + l.error + ", " + Math.max(0, (now - l.at) / 60_000) + " min ago");
        }
        return out;
    }
}
