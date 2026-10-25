package com.tv98.glow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The world's rain right now, for the World map: RainViewer's latest radar at web-map zoom 2 (16 tiles of 256 px,
 * ~150 KB, kept on disk) redrawn row by row from the web map's projection into the World map's flat one.
 */
final class Rain {
    private static final int Z = 2, N = 1 << Z;
    /** Radar tiles come in four at a time (here and on the Rain radar page). */
    static final java.util.concurrent.ExecutorService TILES = java.util.concurrent.Executors.newFixedThreadPool(4);

    private Rain() {}

    /** Off the main thread. A w×h picture covering lat top..bottom and lon -180..180, or null. */
    static Bitmap world(Context c, int w, int h, double top, double bottom) {
        if (w <= 0 || h <= 0) return null;
        try {
            String json = Intel.cached(c, "rainviewer", "https://api.rainviewer.com/public/weather-maps.json", 5 * 60_000L);
            JSONObject d = new JSONObject(json);
            JSONArray past = d.getJSONObject("radar").getJSONArray("past");
            String base = d.getString("host") + past.getJSONObject(past.length() - 1).getString("path") + "/256/" + Z + "/";
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            Rect src = new Rect();
            RectF dst = new RectF();
            float colW = (float) w / N;
            // Fetch (or read from disk) all 16 in parallel first; then draw one tile at a time.
            java.util.List<java.util.concurrent.Future<byte[]>> parts = new java.util.ArrayList<>();
            for (int ty = 0; ty < N; ty++) {
                for (int tx = 0; tx < N; tx++) {
                    final String url = base + tx + "/" + ty + "/2/1_1.png";
                    parts.add(TILES.submit(() -> Net.cachedBytes(c, url)));
                }
            }
            for (int ty = 0; ty < N; ty++) {
                // Output rows whose latitude falls in this tile row of the web map.
                int y0 = -1, y1 = -1;
                for (int y = 0; y < h; y++) {
                    double my = mercY(top - (y + 0.5) / h * (top - bottom)) - ty * 256;
                    if (my >= 0 && my < 256) {
                        if (y0 < 0) y0 = y;
                        y1 = y;
                    }
                }
                if (y0 < 0) continue;
                for (int tx = 0; tx < N; tx++) {
                    byte[] png = parts.get(ty * N + tx).get();
                    Bitmap tile = png == null ? null : android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
                    if (tile == null) continue;
                    for (int y = y0; y <= y1; y++) {
                        int sy = (int) (mercY(top - (y + 0.5) / h * (top - bottom)) - ty * 256);
                        src.set(0, sy, 256, sy + 1);
                        dst.set(tx * colW, y, (tx + 1) * colW, y + 1);
                        canvas.drawBitmap(tile, src, dst, p);
                    }
                    tile.recycle();
                }
            }
            return out;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Web-map pixel row of a latitude at zoom 2 (0..1024). */
    private static double mercY(double lat) {
        double r = Math.toRadians(Math.max(-85, Math.min(85, lat)));
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 256 * N;
    }
}
