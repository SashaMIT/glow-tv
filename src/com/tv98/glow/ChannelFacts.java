package com.tv98.glow;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Facts about each channel from iptv-org's database (built nightly into facts.tsv on the repo's data branch, ~120 KB,
 * fetched weekly): its real languages, and whether it has closed, been replaced, is adult or is on iptv-org's
 * blocklist. Glow hides the last four and ranks by language from facts instead of guessing from the name.
 */
final class ChannelFacts {
    private static final long FRESH_MS = 7 * 86_400_000L;
    /** Feed id -> its languages, or {@link #HIDDEN}. Only what's asked of it: whole lines were 1.4 MB of the heap. */
    private static Map<String, String> facts;
    private static final String HIDDEN = "!";
    private static long factsAt;

    private ChannelFacts() {}

    static File file(Context c) {
        return new File(c.getFilesDir(), "facts.tsv");
    }

    /** Off the main thread. */
    static void refreshIfDue(Context c) {
        File f = file(c);
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < FRESH_MS) return;
        try {
            byte[] b = Signed.fetch(Guide.GLOW_DATA + "facts.tsv", 4 * 1024 * 1024);
            if (b.length < 1000) return;
            File tmp = new File(c.getFilesDir(), "facts.tsv.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(b);
            }
            tmp.renameTo(f);
            android.util.Log.i("GlowPerf", "channel facts: " + b.length + " bytes");
        } catch (Exception e) {
            android.util.Log.i("GlowPerf", "channel facts failed: " + e);
        }
    }

    private static synchronized Map<String, String> all(Context c) {
        File f = file(c);
        if (facts != null && f.lastModified() == factsAt) return facts;
        Map<String, String> m = new HashMap<>();
        Map<String, String> same = new HashMap<>();
        if (f.exists()) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f), 32 * 1024)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("#")) continue;
                    String[] p = line.split("\t", -1);
                    if (p.length < 8) continue;
                    if (!p[4].isEmpty() || !p[5].isEmpty() || !p[6].isEmpty() || !p[7].isEmpty()) {
                        m.put(p[0], HIDDEN);
                    } else if (!p[2].isEmpty()) {
                        String langs = same.putIfAbsent(p[2], p[2]);
                        m.put(p[0], langs != null ? langs : p[2]);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        facts = m;
        factsAt = f.lastModified();
        return m;
    }

    /** Closed, replaced by another channel, adult, or blocklisted. */
    static boolean hidden(Context c, Channel ch) {
        if (ch.feedId == null) return false;
        return all(c).get(ch.feedId) == HIDDEN;
    }

    /** ISO 639-3 codes like "eng,tha", or null when the database doesn't say. */
    static String languages(Context c, Channel ch) {
        if (ch.feedId == null) return null;
        String langs = all(c).get(ch.feedId);
        return langs == HIDDEN ? null : langs;
    }
}
