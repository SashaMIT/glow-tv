package com.tv98.glow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Photos sent from the phone (phone remote ▸ More) for the screensaver: shrunk to TV size and saved as JPEG on the
 * box; the newest 30 are kept.
 */
final class Photos {
    private static final int KEEP = 30, MAX_PX = 1920;
    static final int MAX_UPLOAD = 20 * 1024 * 1024;

    private Photos() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Newest first. */
    static File[] all(Context c) {
        File[] f = dir(c).listFiles((d, n) -> n.endsWith(".jpg"));
        if (f == null) return new File[0];
        Arrays.sort(f, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return f;
    }

    /** Reads {@code length} bytes of an uploaded picture, shrinks it and saves it. Returns false if it isn't an image. */
    static boolean save(Context c, InputStream in, int length) throws Exception {
        File tmp = new File(c.getCacheDir(), "upload.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int left = length;
            while (left > 0) {
                int n = in.read(buf, 0, Math.min(buf.length, left));
                if (n <= 0) break;
                out.write(buf, 0, n);
                left -= n;
            }
        }
        try {
            Bitmap bm = decode(tmp, MAX_PX);
            if (bm == null) return false;
            File dest = new File(dir(c), System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(dest)) {
                bm.compress(Bitmap.CompressFormat.JPEG, 88, out);
            }
            bm.recycle();
        } finally {
            tmp.delete();
        }
        File[] list = all(c);
        for (int i = KEEP; i < list.length; i++) list[i].delete();
        return true;
    }

    /** A photo no wider/taller than about maxPx (power-of-two sampling), or null. */
    static Bitmap decode(File f, int maxPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        try {
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
