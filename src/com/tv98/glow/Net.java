package com.tv98.glow;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small HTTP + image helpers. No libraries: the box has 1 GB of RAM. */
final class Net {
    static final ExecutorService IO = Executors.newFixedThreadPool(3);
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> IMAGES = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap v) {
            return v.getByteCount();
        }
    };

    private Net() {}

    /** Drops decoded pictures held in memory (they reload from the storage cache in milliseconds). */
    static void trim() {
        IMAGES.evictAll();
    }

    /** The first {@code limit} bytes of a web page (enough for its <head>), as a browser would ask for it. */
    static String head(String url, int limit) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        c.setRequestProperty("Accept", "text/html");
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() >= 400) throw new IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while (out.size() < limit && (n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static byte[] bytes(String url, int limit) throws IOException {
        HttpURLConnection c = null;
        for (int hop = 0; hop < 4; hop++) {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Glow/1.0 (Android TV)");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            if (code >= 400) throw new IOException("HTTP " + code);
            break;
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > limit) throw new IOException("response too large");
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    // ---- logo cache on storage: each logo is downloaded once, then read from the box's storage ----

    private static final long LOGO_CACHE_MAX = 24L * 1024 * 1024;
    private static boolean pruned;

    private static java.io.File logoDir(android.content.Context c) {
        java.io.File d = new java.io.File(c.getCacheDir(), "logos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static byte[] logoBytes(android.content.Context c, String url) throws IOException {
        java.io.File f = new java.io.File(logoDir(c), Integer.toHexString(url.hashCode()) + "_" + url.length());
        // A live stream's thumbnail is a recent frame: keep it for half an hour, not forever.
        boolean live = url.contains("_live.jpg");
        if (f.exists() && f.length() > 0 && (!live || System.currentTimeMillis() - f.lastModified() < 30 * 60_000L)) {
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            }
            if (!live) f.setLastModified(System.currentTimeMillis());
            android.util.Log.i("GlowPerf", "logo from disk: " + b.length + " bytes");
            return b;
        }
        byte[] data;
        try {
            data = bytes(url, 3 * 1024 * 1024);
        } catch (IOException e) {
            if (!live || !f.exists() || f.length() == 0) throw e;
            f.setLastModified(System.currentTimeMillis() - 25 * 60_000L); // offline: the older frame, retried in 5 min
            return logoBytes(c, url);
        }
        android.util.Log.i("GlowPerf", "logo from network: " + data.length + " bytes");
        java.io.File tmp = new java.io.File(f.getPath() + ".tmp");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
            out.write(data);
        }
        tmp.renameTo(f);
        if (!pruned) {
            pruned = true;
            prune(logoDir(c));
        }
        return data;
    }

    /** Keeps the logo cache under its cap by dropping the least recently used files. */
    private static void prune(java.io.File dir) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (java.io.File f : files) total += f.length();
        if (total <= LOGO_CACHE_MAX) return;
        java.util.Arrays.sort(files, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
        for (java.io.File f : files) {
            if (total <= LOGO_CACHE_MAX * 3 / 4) break;
            total -= f.length();
            f.delete();
        }
    }

    static String text(String url) throws IOException {
        return new String(bytes(url, 40 * 1024 * 1024), StandardCharsets.UTF_8);
    }

    /** Loads a remote image into a (possibly recycled) ImageView, downsampled to maxPx. */
    interface BitmapCallback {
        void loaded(Bitmap bm);
    }

    /** A cached bitmap right now, or null (doesn't start a load). */
    static Bitmap cachedBitmap(String url) {
        return url == null ? null : IMAGES.get(url);
    }

    /** Loads a logo (memory, then disk, then network) off the main thread; calls back on the main thread, null on failure. */
    static void bitmap(final android.content.Context c, final String url, final int maxPx, final BitmapCallback cb) {
        if (url == null || url.isEmpty()) {
            cb.loaded(null);
            return;
        }
        Bitmap hit = IMAGES.get(url);
        if (hit != null) {
            cb.loaded(hit);
            return;
        }
        final android.content.Context app = c.getApplicationContext();
        IO.execute(() -> {
            Bitmap bm = decode(app, url, maxPx);
            if (bm != null) IMAGES.put(url, bm);
            MAIN.post(() -> cb.loaded(bm));
        });
    }

    private static Bitmap decode(android.content.Context c, String url, int maxPx) {
        try {
            byte[] data = logoBytes(c, url);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx / 2) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Bytes through the storage cache (network the first time), or null. */
    static byte[] cachedBytes(android.content.Context c, String url) {
        try {
            return logoBytes(c, url);
        } catch (Exception e) {
            return null;
        }
    }

    /** A picture through the storage cache only (not kept in memory here): for big one-off images like radar tiles. */
    static Bitmap fileBitmap(android.content.Context c, String url) {
        try {
            byte[] data = logoBytes(c, url);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    static void image(final ImageView iv, final String url, final int maxPx, final Runnable onFail) {
        iv.setTag(url);
        if (url == null || url.isEmpty()) {
            if (onFail != null) onFail.run();
            return;
        }
        Bitmap hit = IMAGES.get(url);
        if (hit != null) {
            iv.setImageBitmap(hit);
            return;
        }
        iv.setImageDrawable(null);
        IO.execute(() -> {
            Bitmap bm = null;
            try {
                byte[] data = logoBytes(iv.getContext(), url);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int s = 1;
                while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx / 2) s *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = s;
                bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            } catch (Exception | OutOfMemoryError ignored) {
            }
            final Bitmap result = bm;
            if (result != null) IMAGES.put(url, result);
            MAIN.post(() -> {
                if (!url.equals(iv.getTag())) return;
                if (result != null) iv.setImageBitmap(result);
                else if (onFail != null) onFail.run();
            });
        });
    }
}
