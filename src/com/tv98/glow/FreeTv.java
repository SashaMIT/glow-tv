package com.tv98.glow;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Free channels from the iptv-org collection (github.com/iptv-org/iptv): public M3U playlists of
 * streams that broadcasters make freely available. Cached on disk and refreshed every few hours.
 */
final class FreeTv {
    private static final String BASE = "https://iptv-org.github.io/iptv/";
    /** {category id (playlist path), name shown in the channel browser}. */
    static final String[][] LISTS = {
            {"countries/th.m3u", "Thailand"},
            {"categories/news.m3u", "World news"},
            {"categories/sports.m3u", "Sports"},
            {"countries/uk.m3u", "UK"},
            {"countries/us.m3u", "US"},
    };
    private static final long FRESH_MS = 6 * 60 * 60 * 1000L;

    private FreeTv() {}

    static List<Xtream.Category> categories() {
        List<Xtream.Category> out = new ArrayList<>();
        for (String[] l : LISTS) out.add(new Xtream.Category(l[0], l[1]));
        return out;
    }

    private static File file(Context c, String path) {
        return new File(c.getFilesDir(), "free_" + path.replace('/', '_'));
    }

    static boolean stale(Context c) {
        for (String[] l : LISTS) {
            File f = file(c, l[0]);
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > FRESH_MS) return true;
        }
        return false;
    }

    private static List<Channel> memo;
    private static long memoStamp = -1;

    /** Parsed channels from the saved lists; re-parsed only when a list file has changed. */
    static synchronized List<Channel> cached(Context c) {
        long stamp = 0;
        for (String[] l : LISTS) stamp = stamp * 31 + file(c, l[0]).lastModified();
        if (memo != null && stamp == memoStamp) return memo;
        long t0 = android.os.SystemClock.elapsedRealtime();
        List<Channel> indexed = readIndex(c, stamp);
        if (indexed != null) {
            memo = indexed;
            memoStamp = stamp;
            android.util.Log.i("GlowPerf", "lists loaded: " + indexed.size() + " channels in "
                    + (android.os.SystemClock.elapsedRealtime() - t0) + " ms (index)");
            return indexed;
        }
        List<Channel> all = new ArrayList<>();
        for (String[] l : LISTS) {
            File f = file(c, l[0]);
            if (!f.exists()) continue;
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
                all.addAll(parse(new String(b, StandardCharsets.UTF_8), l[0]));
            } catch (IOException ignored) {
            }
        }
        memo = all;
        memoStamp = stamp;
        android.util.Log.i("GlowPerf", "lists loaded: " + all.size() + " channels in "
                + (android.os.SystemClock.elapsedRealtime() - t0) + " ms (m3u)");
        writeIndex(c, stamp, all);
        return all;
    }

    // ---- compact binary index: parsed channels saved once, read back in a fraction of the parse time ----

    private static final int INDEX_VERSION = 1;

    private static File indexFile(Context c) {
        return new File(c.getFilesDir(), "free.idx");
    }

    private static void writeIndex(Context c, long stamp, List<Channel> all) {
        File tmp = new File(c.getFilesDir(), "free.idx.tmp");
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(new FileOutputStream(tmp), 64 * 1024))) {
            out.writeInt(INDEX_VERSION);
            out.writeLong(stamp);
            out.writeInt(all.size());
            for (Channel ch : all) {
                out.writeInt(ch.num);
                str(out, ch.name);
                str(out, ch.logo);
                str(out, ch.categoryId);
                str(out, ch.url);
                str(out, ch.userAgent);
                str(out, ch.referrer);
                str(out, ch.group);
                str(out, ch.country);
                str(out, ch.tvgId);
            }
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        tmp.renameTo(indexFile(c));
    }

    private static List<Channel> readIndex(Context c, long stamp) {
        File f = indexFile(c);
        if (!f.exists()) return null;
        try (java.io.DataInputStream in = new java.io.DataInputStream(
                new java.io.BufferedInputStream(new FileInputStream(f), 64 * 1024))) {
            if (in.readInt() != INDEX_VERSION || in.readLong() != stamp) return null;
            int n = in.readInt();
            List<Channel> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int num = in.readInt();
                String name = str(in), logo = str(in), cat = str(in), url = str(in), ua = str(in), ref = str(in), group = str(in);
                Channel ch = new Channel(url == null ? 0 : url.hashCode(), num, name, logo, cat, url, ua, ref, group);
                ch.country = str(in);
                ch.tvgId = str(in);
                out.add(ch);
            }
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    private static void str(java.io.DataOutputStream out, String s) throws IOException {
        out.writeBoolean(s != null);
        if (s != null) out.writeUTF(s);
    }

    private static String str(java.io.DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    /** Downloads every list; keeps the old copy of any list that fails to download. */
    static List<Channel> refresh(Context c) {
        for (String[] l : LISTS) {
            try {
                byte[] body = Net.bytes(BASE + l[0], 8 * 1024 * 1024);
                if (!new String(body, 0, Math.min(body.length, 16), StandardCharsets.UTF_8).startsWith("#EXTM3U")) continue;
                try (FileOutputStream out = new FileOutputStream(file(c, l[0]))) {
                    out.write(body);
                }
            } catch (IOException ignored) {
            }
        }
        return cached(c);
    }

    static List<Channel> parse(String text, String categoryId) {
        List<Channel> out = new ArrayList<>();
        String name = null, logo = null, ua = null, ref = null, group = null, country = null, id = null;
        int num = 0, pos = 0, len = text.length();
        while (pos < len) {
            int eol = text.indexOf('\n', pos);
            if (eol < 0) eol = len;
            String line = text.substring(pos, eol).trim();
            pos = eol + 1;
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                String head = comma >= 0 ? line.substring(0, comma) : line;
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Channel";
                logo = attr(head, "tvg-logo=\"");
                group = attr(head, "group-title=\"");
                ua = attr(head, "http-user-agent=\"");
                ref = attr(head, "http-referrer=\"");
                String tvg = attr(head, "tvg-id=\"");
                country = tvg == null ? null : countryOf(tvg);
                id = null;
                if (tvg != null) {
                    int at = tvg.indexOf('@');
                    id = (at >= 0 ? tvg.substring(0, at) : tvg).toLowerCase(java.util.Locale.ROOT);
                    if (id.isEmpty()) id = null;
                }
            } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                ua = line.substring(line.indexOf('=') + 1);
            } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                ref = line.substring(line.indexOf('=') + 1);
            } else if (name != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                num++;
                Channel ch = new Channel(line.hashCode(), num, tidy(name), logo, categoryId, line, ua, ref, group);
                ch.country = country;
                ch.tvgId = id;
                out.add(ch);
                name = null;
            }
        }
        return out;
    }

    /** Value of key="..." inside an #EXTINF header, or null. */
    private static String attr(String head, String key) {
        int i = head.indexOf(key);
        if (i < 0) return null;
        i += key.length();
        int j = head.indexOf('"', i);
        return j < 0 ? null : head.substring(i, j);
    }

    private static String countryOf(String tvgId) {
        int at = tvgId.indexOf('@');
        String base = at >= 0 ? tvgId.substring(0, at) : tvgId;
        int dot = base.lastIndexOf('.');
        if (dot < 0 || base.length() - dot != 3) return null;
        return base.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** "13 Siam Thai (1080p) [Not 24/7]" -> "13 Siam Thai". */
    private static String tidy(String n) {
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c == '[') { // "[Not 24/7]", "[Geo-blocked]"
                int close = n.indexOf(']', i);
                if (close > 0) { i = close; continue; }
            }
            if (c == '(') { // "(1080p)", "(720p)"
                int close = n.indexOf(')', i);
                if (close > i + 3 && close - i <= 6 && n.charAt(close - 1) == 'p' && isDigits(n, i + 1, close - 1)) { i = close; continue; }
            }
            if (c == ' ' && b.length() > 0 && b.charAt(b.length() - 1) == ' ') continue; // collapse gaps
            b.append(c);
        }
        return b.toString().trim();
    }

    private static boolean isDigits(String s, int from, int to) {
        for (int i = from; i < to; i++) if (!Character.isDigit(s.charAt(i))) return false;
        return to > from;
    }
}
