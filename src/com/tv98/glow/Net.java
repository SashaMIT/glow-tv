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
        if (f.exists() && f.length() > 0) {
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            }
            f.setLastModified(System.currentTimeMillis());
            android.util.Log.i("GlowPerf", "logo from disk: " + b.length + " bytes");
            return b;
        }
        byte[] data = bytes(url, 3 * 1024 * 1024);
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
