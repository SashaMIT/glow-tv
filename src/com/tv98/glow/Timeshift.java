package com.tv98.glow;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;

import androidx.media3.common.C;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rewind further than a channel's own live window (which is 8–30 s on most free streams). While a channel plays,
 * the HLS segments the player downloads anyway are also kept on storage, and the order they came in is remembered.
 * Going back past the stream's window switches the player to a playlist Glow builds from what it kept (no extra
 * internet: the same bytes, read back). Only HLS streams, only the channel on screen.
 *
 * Storage: a USB stick if one is plugged in, else internal storage, never going below Android's own low-storage line
 * (5 % of /data, at most 500 MB) plus 100 MB, and given back when the player closes. The amount kept follows the
 * space: about 4 minutes per 100 MB at 3 Mbit/s.
 */
final class Timeshift {
    static final String SCHEME = "glowts";
    /** Internal storage keeps this much free on top of Android's low-storage line, for app updates. */
    private static final long MARGIN = 100L << 20;
    private static final long MAX_CAP = 1024L << 20, MAX_INTERNAL = 256L << 20, MIN_CAP = 40L << 20;

    private static SimpleCache cache;
    private static boolean checked;
    private static long capBytes;
    private static String where = "";
    private static volatile String recordingKey;
    private static Recorder rec;

    private Timeshift() {}

    // ---- storage ----

    /** Sets up the store once; false when there's no room (then the reason is in {@link #status}). */
    static synchronized boolean ready(Context c) {
        if (cache != null) return true;
        if (checked) return false; // no room or switched off: checked again when the player next opens
        checked = true;
        if (!c.getSharedPreferences("player", Context.MODE_PRIVATE).getBoolean("timeshift", true)) {
            where = "off";
            return false;
        }
        File best = null;
        long bestCap = 0;
        File[] ext = c.getExternalCacheDirs(); // [0] is internal shared storage (the same /data partition)
        for (int i = 1; ext != null && i < ext.length; i++) {
            if (ext[i] == null) continue;
            long cap = Math.min(MAX_CAP, free(ext[i]) - (200L << 20));
            if (cap > bestCap) {
                best = ext[i];
                bestCap = cap;
            }
        }
        if (best == null) {
            best = c.getCacheDir();
            long total = new StatFs(best.getAbsolutePath()).getTotalBytes();
            long low = Math.min(total / 20, 500L << 20); // where Android starts warning about storage
            bestCap = Math.min(MAX_INTERNAL, free(best) + used(new File(best, "timeshift")) - low - MARGIN);
        }
        File dir = new File(best, "timeshift");
        if (bestCap < MIN_CAP) {
            where = "no room: " + (free(best) >> 20) + " MB free";
            android.util.Log.i("GlowPlayer", "timeshift: " + where + " (" + best + ", could keep " + (bestCap >> 20) + " MB)");
            deleteAll(dir); // anything kept from before (a stick that's gone, space since used up)
            return false;
        }
        try {
            cache = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(bestCap),
                    new androidx.media3.database.StandaloneDatabaseProvider(c.getApplicationContext()));
            capBytes = bestCap;
            where = best.getAbsolutePath().startsWith(c.getCacheDir().getAbsolutePath()) ? "internal" : "USB";
            android.util.Log.i("GlowPlayer", "timeshift: " + (bestCap >> 20) + " MB on " + where + " (" + dir + ")");
            return true;
        } catch (Throwable t) {
            where = "unavailable";
            android.util.Log.i("GlowPlayer", "timeshift: " + t);
            return false;
        }
    }

    /** Bytes already in a folder (left from last time; counted as room, since they're about to be reused). */
    private static long used(File f) {
        File[] in = f.listFiles();
        long n = f.isFile() ? f.length() : 0;
        if (in != null) for (File x : in) n += used(x);
        return n;
    }

    /** "200 MB on USB", "off", "no room: 366 MB free". */
    static synchronized String status(Context c) {
        ready(c);
        return cache != null ? (capBytes >> 20) + " MB on " + where : where;
    }

    private static long free(File f) {
        try {
            f.mkdirs();
            return new StatFs(f.getAbsolutePath()).getAvailableBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    private static void deleteAll(File f) {
        File[] in = f.listFiles();
        if (in != null) for (File x : in) deleteAll(x);
        f.delete();
    }

    // ---- what's been recorded ----

    private static final class Seg {
        long seq;
        double dur;
        String uri, key, map, variant;
        boolean disc, kept;
        long bytes;
    }

    private static final class Recorder {
        final String channel;
        final TreeMap<Long, Seg> segs = new TreeMap<>();
        final Map<String, Seg> byUri = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        String variant;
        int target = 6, version = 3, droppedDisc;
        long kept;

        Recorder(String channel) {
            this.channel = channel;
        }
    }

    /** The channel now on screen is recorded from here on (anything kept for another channel is let go). */
    static synchronized void record(Context c, String channelKey) {
        if (!ready(c)) return;
        if (rec != null && rec.channel.equals(channelKey)) return;
        rec = new Recorder(channelKey);
        recordingKey = channelKey;
    }

    /** The player closed: stop recording and give the space back. Off the main thread. */
    static void stop() {
        SimpleCache c;
        synchronized (Timeshift.class) {
            rec = null;
            recordingKey = null;
            checked = false; // space or a USB stick may have changed by next time
            c = cache;
        }
        if (c == null) return;
        try {
            for (String k : c.getKeys()) c.removeResource(k);
        } catch (Exception ignored) {
        }
    }

    /** Seconds kept for this channel that the player can go back to (0 if none). */
    static synchronized double seconds(String channelKey) {
        if (rec == null || !rec.channel.equals(channelKey)) return 0;
        double s = 0;
        boolean started = false;
        for (Seg g : rec.segs.values()) {
            started |= g.kept;
            if (started) s += g.dur;
        }
        return s;
    }

    /** Where live playback normally sits behind the newest segment: three segment lengths (HLS's own rule). */
    static synchronized long edgeMs(String channelKey) {
        return rec != null && rec.channel.equals(channelKey) ? 3_000L * rec.target : 0;
    }

    private static synchronized boolean isSegment(String channelKey, String uri) {
        return rec != null && rec.channel.equals(channelKey) && rec.byUri.containsKey(uri);
    }

    private static synchronized void kept(String channelKey, String uri, long bytes) {
        if (rec == null || !rec.channel.equals(channelKey)) return;
        Seg g = rec.byUri.get(uri);
        if (g == null || g.kept) return;
        g.kept = true;
        g.bytes = bytes;
        rec.segs.put(g.seq, g); // this variant's copy is the one on storage
        rec.kept += bytes;
        // Forget the oldest once the store is nearly full (the cache itself drops the least recently used bytes).
        while (rec.kept > capBytes * 85 / 100 && !rec.segs.isEmpty()) {
            Seg old = rec.segs.pollFirstEntry().getValue();
            rec.kept -= old.bytes;
            rec.byUri.remove(old.uri);
            if (old.disc) rec.droppedDisc++;
        }
    }

    /** A media playlist the player (or Glow's own reload) loaded: remember its segments in order. */
    private static synchronized void playlist(String channelKey, String uri, String text, Map<String, String> headers) {
        if (rec == null || !rec.channel.equals(channelKey) || !text.startsWith("#EXTM3U") || !text.contains("#EXTINF")
                || text.contains("#EXT-X-BYTERANGE")) return;
        rec.variant = uri;
        if (headers != null) rec.headers = headers;
        Uri base = Uri.parse(uri);
        long seq = 0;
        double dur = 0;
        String key = null, map = null;
        boolean disc = false;
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) seq = parseLong(line.substring(22));
            else if (line.startsWith("#EXT-X-TARGETDURATION:")) rec.target = (int) Math.max(1, parseLong(line.substring(22)));
            else if (line.startsWith("#EXT-X-VERSION:")) rec.version = (int) Math.max(rec.version, parseLong(line.substring(15)));
            else if (line.startsWith("#EXT-X-KEY:")) key = absolute(line, base);
            else if (line.startsWith("#EXT-X-MAP:")) map = absolute(line, base);
            else if (line.startsWith("#EXT-X-DISCONTINUITY") && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")) disc = true;
            else if (line.startsWith("#EXTINF:")) {
                int comma = line.indexOf(',');
                try {
                    dur = Double.parseDouble(line.substring(8, comma < 0 ? line.length() : comma));
                } catch (NumberFormatException e) {
                    dur = rec.target;
                }
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                String abs = resolve(base, line);
                Seg g = rec.byUri.get(abs);
                if (g == null) {
                    g = new Seg();
                    g.seq = seq;
                    g.dur = dur;
                    g.uri = abs;
                    g.key = key;
                    g.map = map;
                    g.disc = disc;
                    g.variant = uri;
                    rec.byUri.put(abs, g);
                    Seg have = rec.segs.get(seq);
                    if (have == null || !have.kept) rec.segs.put(seq, g); // the copy on storage wins
                }
                seq++;
                disc = false;
            }
        }
    }

    /** The playlist the player follows when rewound: everything kept, then what's new upstream. */
    private static synchronized byte[] build(String channelKey) {
        StringBuilder p = new StringBuilder("#EXTM3U\n");
        if (rec == null || !rec.channel.equals(channelKey)) return p.append("#EXT-X-ENDLIST\n").toString().getBytes();
        Long first = null;
        for (Seg g : rec.segs.values()) if (g.kept) { first = g.seq; break; }
        if (first == null) return p.append("#EXT-X-ENDLIST\n").toString().getBytes();
        p.append("#EXT-X-VERSION:").append(Math.max(3, rec.version)).append('\n');
        p.append("#EXT-X-TARGETDURATION:").append(rec.target).append('\n');
        p.append("#EXT-X-MEDIA-SEQUENCE:").append(first).append('\n');
        p.append("#EXT-X-DISCONTINUITY-SEQUENCE:").append(rec.droppedDisc).append('\n');
        String key = null, map = null, variant = null;
        long expect = first;
        for (Seg g : rec.segs.tailMap(first).values()) {
            // A gap (nothing recorded) or a change of quality is a discontinuity for the player.
            if (g.disc || g.seq != expect || (variant != null && !variant.equals(g.variant))) p.append("#EXT-X-DISCONTINUITY\n");
            if (g.key != null && !g.key.equals(key)) p.append(g.key).append('\n');
            if (g.map != null && !g.map.equals(map)) p.append(g.map).append('\n');
            key = g.key;
            map = g.map;
            variant = g.variant;
            p.append(String.format(Locale.ROOT, "#EXTINF:%.3f,\n", g.dur)).append(g.uri).append('\n');
            expect = g.seq + 1;
        }
        return p.toString().getBytes();
    }

    private static String variant(String channelKey) {
        synchronized (Timeshift.class) {
            return rec != null && rec.channel.equals(channelKey) ? rec.variant : null;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String resolve(Uri base, String ref) {
        return androidx.media3.common.util.UriUtil.resolve(base.toString(), ref);
    }

    /** A tag's URI="…" made absolute, so the line still works in Glow's own playlist. */
    private static String absolute(String line, Uri base) {
        int a = line.indexOf("URI=\"");
        if (a < 0) return line;
        int b = line.indexOf('"', a + 5);
        if (b < 0) return line;
        return line.substring(0, a + 5) + resolve(base, line.substring(a + 5, b)) + line.substring(b);
    }

    /** The URI the player opens to play what's been kept. */
    static Uri uri(String channelKey) {
        return new Uri.Builder().scheme(SCHEME).authority("rec").appendPath(Integer.toHexString(channelKey.hashCode()))
                .appendPath("index.m3u8").build();
    }

    // ---- the player's data source ----

    /**
     * Every load of a stream goes through here. For the channel being recorded, segments go through the store
     * (kept as they're read) and media playlists are read to learn the order; Glow's own playlist is built here.
     */
    static final class Source implements DataSource {
        private final DataSource plain;
        private final String channelKey;
        private final Map<String, String> headers;
        private DataSource current;
        private ByteArrayOutputStream capture;
        private String uri;
        private long read;
        private boolean segment;

        Source(DataSource plain, String channelKey, Map<String, String> headers) {
            this.plain = plain;
            this.channelKey = channelKey;
            this.headers = headers;
        }

        @Override
        public void addTransferListener(TransferListener l) {
            plain.addTransferListener(l);
        }

        @Override
        public long open(DataSpec spec) throws IOException {
            uri = spec.uri.toString();
            read = 0;
            capture = null;
            segment = false;
            if (SCHEME.equals(spec.uri.getScheme())) { // Glow's playlist of what's been kept, with the newest from upstream
                String v = variant(channelKey);
                if (v != null) {
                    try {
                        playlist(channelKey, v, fetch(plain, v), headers);
                    } catch (IOException e) {
                        android.util.Log.i("GlowPlayer", "timeshift reload: " + e);
                    }
                }
                current = new ByteArrayDataSource(build(channelKey));
                return current.open(spec);
            }
            boolean recording = channelKey != null && channelKey.equals(recordingKey);
            segment = recording && isSegment(channelKey, uri);
            if (segment && cache != null) {
                current = new CacheDataSource(cache, plain, CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            } else {
                current = plain;
                if (recording) capture = new ByteArrayOutputStream();
            }
            return current.open(spec);
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = current.read(buf, off, len);
            if (n > 0) {
                read += n;
                if (capture != null) {
                    if (capture.size() + n > 2 << 20) capture = null; // not a playlist
                    else capture.write(buf, off, n);
                }
            }
            return n;
        }

        @Override
        public Uri getUri() {
            return current == null ? null : current.getUri();
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return current == null ? new HashMap<>() : current.getResponseHeaders();
        }

        @Override
        public void close() throws IOException {
            try {
                if (current != null) current.close();
            } finally {
                if (capture != null && capture.size() > 0) playlist(channelKey, uri, capture.toString("UTF-8"), headers);
                if (segment && read > 0) kept(channelKey, uri, read);
                capture = null;
                current = null;
            }
        }

        private static String fetch(DataSource ds, String url) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                ds.open(new DataSpec(Uri.parse(url)));
                byte[] b = new byte[16 * 1024];
                int n;
                while ((n = ds.read(b, 0, b.length)) != C.RESULT_END_OF_INPUT) out.write(b, 0, n);
            } finally {
                ds.close();
            }
            return out.toString("UTF-8");
        }
    }
}
