package com.tv98.glow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Rain radar around the weather city: the last two hours from RainViewer (free tiles, 30-minute steps, looping)
 * over land and borders drawn by Glow from Natural Earth 1:50m. The map is laid out at web-map zoom 6 (~20° across);
 * radar comes in at zoom 5 and is drawn twice the size (it's smooth data), a quarter of the pixels to fetch and hold.
 */
class RadarView extends View {
    private static final int Z = 6, FRAMES = 5, STEP = 3; // RainViewer frames are 10 min apart: every 3rd = 30 min
    private static short[][] land, borders;

    private final double lat, lon;
    private final String city;
    private Bitmap base;
    private final List<Bitmap> frames = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    private int shown;
    private boolean loading = true, failed;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            radar = new Paint(Paint.FILTER_BITMAP_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), chip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (frames.isEmpty()) return;
            shown = (shown + 1) % frames.size();
            invalidate();
            postDelayed(this, shown == frames.size() - 1 ? 2200 : 650); // rest on "now"
        }
    };

    RadarView(Context c, double lat, double lon, String city) {
        super(c);
        this.lat = lat;
        this.lon = lon;
        this.city = city;
        setBackgroundColor(0xFF0B1226);
        radar.setAlpha(235);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(Ui.dp(13));
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setColor(0xCC0B1226);
    }

    // ---- web-map (Mercator) pixels at a zoom ----

    private static double wx(double lon, int z) {
        return (lon + 180) / 360 * 256 * (1 << z);
    }

    private static double wy(double lat, int z) {
        double r = Math.toRadians(Math.max(-85, Math.min(85, lat)));
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 256 * (1 << z);
    }

    private double ox() {
        return wx(lon, Z) - getWidth() / 2.0;
    }

    private double oy() {
        return wy(lat, Z) - getHeight() / 2.0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        Net.IO.execute(() -> {
            Bitmap b = drawBase(w, h);
            post(() -> {
                base = b;
                invalidate();
            });
            loadFrames(w, h);
        });
    }

    private Bitmap drawBase(int w, int h) {
        synchronized (RadarView.class) {
            if (land == null) land = WorldMapView.shapes(getContext(), "land_50m");
            if (borders == null) borders = WorldMapView.shapes(getContext(), "borders_50m");
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565); // opaque: half the memory
        Canvas c = new Canvas(b);
        c.drawColor(0xFF0B1226);
        double ox = ox(), oy = oy();
        // Its own paints: this runs off the main thread while the view may be drawing.
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFF22325A);
        stroke.setStyle(Paint.Style.STROKE);
        Path p = new Path();
        for (int pass = 0; pass < 2; pass++) {
            short[][] shapes = pass == 0 ? land : borders;
            for (short[] s : shapes) {
                p.reset();
                float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                for (int j = 0; j < s.length; j += 2) {
                    float x = (float) (wx(s[j] / 100.0, Z) - ox), y = (float) (wy(s[j + 1] / 100.0, Z) - oy);
                    if (j == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                if (maxX < 0 || minX > w || maxY < 0 || minY > h) continue; // off this view
                if (pass == 0) {
                    p.close();
                    c.drawPath(p, fill);
                    stroke.setColor(0x885577C0);
                    stroke.setStrokeWidth(Math.max(1, Ui.dp(0.8f)));
                } else {
                    stroke.setColor(0x55C8D4FF);
                    stroke.setStrokeWidth(Math.max(1, Ui.dp(0.7f)));
                }
                c.drawPath(p, stroke);
            }
        }
        return b;
    }


    /**
     * Off the main thread: the frame list (cached 5 min), then the frames newest first, each shown as soon as it's
     * ready (tiles are kept on disk; a frame's never change).
     */
    private void loadFrames(int w, int h) {
        long t0 = android.os.SystemClock.uptimeMillis();
        try {
            String json = Intel.cached(getContext(), "rainviewer", "https://api.rainviewer.com/public/weather-maps.json", 5 * 60_000L);
            JSONObject d = new JSONObject(json);
            String host = d.getString("host");
            JSONArray past = d.getJSONObject("radar").getJSONArray("past");
            // Zoom-5 pixels this view covers (half of zoom 6), and the tiles under them.
            double ox = ox() / 2, oy = oy() / 2;
            int fw = (w + 1) / 2, fh = (h + 1) / 2;
            int tx0 = (int) Math.floor(ox / 256), tx1 = (int) Math.floor((ox + fw) / 256);
            int ty0 = (int) Math.floor(oy / 256), ty1 = (int) Math.floor((oy + fh) / 256);
            int made = 0;
            for (int i = past.length() - 1; i >= 0 && made < FRAMES; i -= STEP, made++) {
                if (!isAttachedToWindow() && made > 0) return;
                JSONObject f = past.getJSONObject(i);
                List<java.util.concurrent.Future<Bitmap>> parts = new ArrayList<>();
                List<int[]> where = new ArrayList<>();
                for (int tx = tx0; tx <= tx1; tx++) {
                    for (int ty = ty0; ty <= ty1; ty++) {
                        if (ty < 0 || ty >= 32) continue;
                        final String url = host + f.getString("path") + "/256/5/" + (((tx % 32) + 32) % 32) + "/" + ty + "/2/1_1.png";
                        parts.add(Rain.TILES.submit(() -> Net.fileBitmap(getContext(), url)));
                        where.add(new int[] {tx, ty});
                    }
                }
                Bitmap frame = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(frame);
                for (int k = 0; k < parts.size(); k++) {
                    Bitmap tile = parts.get(k).get();
                    if (tile == null) continue;
                    c.drawBitmap(tile, (float) (where.get(k)[0] * 256 - ox), (float) (where.get(k)[1] * 256 - oy), null);
                    tile.recycle();
                }
                final long at = f.getLong("time") * 1000;
                final boolean last = made == FRAMES - 1 || i - STEP < 0;
                if (made == 0) android.util.Log.i("GlowPerf", "radar: latest frame in " + (android.os.SystemClock.uptimeMillis() - t0) + " ms");
                post(() -> {
                    if (!isAttachedToWindow()) {
                        frame.recycle();
                        return;
                    }
                    frames.add(0, frame); // oldest first, so the loop runs forwards in time
                    times.add(0, at);
                    loading = false;
                    shown = frames.size() - 1;
                    invalidate();
                    if (last) {
                        removeCallbacks(step);
                        postDelayed(step, 1500);
                    }
                });
            }
            android.util.Log.i("GlowPerf", "radar: " + made + " frames in " + (android.os.SystemClock.uptimeMillis() - t0) + " ms");
        } catch (Exception | OutOfMemoryError e) {
            post(() -> {
                failed = frames.isEmpty();
                loading = false;
                invalidate();
            });
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(step);
        for (Bitmap b : frames) b.recycle();
        frames.clear();
        if (base != null) base.recycle();
        base = null;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (base != null) c.drawBitmap(base, 0, 0, null);
        if (!frames.isEmpty()) {
            Bitmap f = frames.get(shown);
            c.drawBitmap(f, new Rect(0, 0, f.getWidth(), f.getHeight()), new Rect(0, 0, f.getWidth() * 2, f.getHeight() * 2), radar);
        }
        // The city, on top of the rain.
        float cx = w / 2f, cy = h / 2f;
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(Ui.dp(2));
        c.drawCircle(cx, cy, Ui.dp(7), stroke);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, Ui.dp(2.5f), fill);
        label(c, city, cx + Ui.dp(12), cy + Ui.dp(5));

        // Time of the frame on screen, and where it is in the loop.
        String when;
        if (loading) when = "Loading radar…";
        else if (failed || times.isEmpty()) when = "Radar isn't available right now";
        else {
            long t = times.get(shown);
            long ago = (System.currentTimeMillis() - t) / 60_000;
            when = DateFormat.format(Ui.is24h(getContext()) ? "HH:mm" : "h:mm a", t) + (shown == times.size() - 1 ? "  ·  latest" : "  ·  " + ago + " min ago");
        }
        label(c, when, Ui.dp(14), h - Ui.dp(16));
        for (int i = 0; i < frames.size(); i++) {
            fill.setColor(i == shown ? 0xFFFFFFFF : 0x66FFFFFF);
            c.drawCircle(w - Ui.dp(20) - (frames.size() - 1 - i) * Ui.dp(14), h - Ui.dp(21), Ui.dp(4), fill);
        }
    }

    private void label(Canvas c, String s, float x, float baseline) {
        float tw = text.measureText(s);
        c.drawRoundRect(x - Ui.dp(7), baseline - Ui.dp(16), x + tw + Ui.dp(7), baseline + Ui.dp(6), Ui.dp(6), Ui.dp(6), chip);
        c.drawText(L.t(s), x, baseline, text);
    }
}
