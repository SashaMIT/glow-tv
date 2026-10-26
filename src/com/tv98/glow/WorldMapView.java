package com.tv98.glow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * The world, drawn by Glow: Natural Earth land (public domain) in a flat projection from 84°N to 58°S, today's
 * day/night line from the sun's position, and live pins (earthquakes, disaster alerts, natural events, news
 * hotspots, webcams, the ISS). The land is drawn once into a bitmap; pins are drawn on top each frame.
 */
class WorldMapView extends View {
    static final double TOP = 84, BOTTOM = -58;
    private static short[][] land; // shared: lon*100, lat*100 pairs per polygon

    private final Paint landFill = new Paint(Paint.ANTI_ALIAS_FLAG), coast = new Paint(Paint.ANTI_ALIAS_FLAG),
            grid = new Paint(), night = new Paint(Paint.ANTI_ALIAS_FLAG), pin = new Paint(Paint.ANTI_ALIAS_FLAG),
            label = new Paint(Paint.ANTI_ALIAS_FLAG), labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap base;
    private final Path nightPath = new Path();
    private long nightAt;
    private List<Intel.Event> events = new ArrayList<>();
    private Intel.Event selected;
    private double[] iss;
    private boolean compact;
    private Bitmap rain;
    // Land + night + rain in one opaque picture, so a frame is one bitmap and the pins. Two of them (rain under the
    // pins, rain on its own) so switching to the rain view is instant.
    private final Bitmap[] baked = new Bitmap[2];
    private boolean bakedStale = true;
    private boolean rainOnly;
    private final Paint rainPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    WorldMapView(Context c) {
        super(c);
        landFill.setColor(0xFF22325A);
        coast.setColor(0x885577C0);
        coast.setStyle(Paint.Style.STROKE);
        coast.setStrokeWidth(Math.max(1, Ui.dp(0.7f)));
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(1);
        night.setColor(0x66000614);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(Ui.dp(12));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelBg.setColor(0xCC0B1226);
        setBackgroundColor(0xFF0B1226);
        loadLand(c);
    }

    /** Home's small card: smaller pins, no labels. */
    void setCompact(boolean on) {
        compact = on;
        landFill.setColor(on ? 0xFF2E4377 : 0xFF22325A); // a small card needs the land to read at a glance
    }

    void setEvents(List<Intel.Event> e) {
        events = e;
        invalidate();
    }

    void setSelected(Intel.Event e) {
        selected = e;
        invalidate();
    }

    /** Today's rain (from Rain.world, same size and projection as the map); drawn under the pins. */
    void setRain(Bitmap b) {
        rain = b;
        bakedStale = true;
        invalidate();
    }

    /** Only the rain, strong; pins hidden by the caller. */
    void setRainOnly(boolean on) {
        rainOnly = on;
        invalidate();
    }

    void setIss(double[] latLon) {
        iss = latLon;
        invalidate();
    }

    private static synchronized void loadLand(Context c) {
        if (land == null) land = shapes(c, "world_land");
    }

    /** A res/raw shape file from tools/gen_world_map.py: count, then per shape n and n (lon*100, lat*100) pairs. */
    static short[][] shapes(Context c, String raw) {
        int id = c.getResources().getIdentifier(raw, "raw", c.getPackageName());
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(c.getResources().openRawResource(id), 65536))) {
            int n = in.readInt();
            short[][] polys = new short[n][];
            for (int i = 0; i < n; i++) {
                int pts = in.readInt();
                short[] p = new short[pts * 2];
                for (int j = 0; j < pts * 2; j++) p[j] = in.readShort();
                polys[i] = p;
            }
            return polys;
        } catch (Exception e) {
            return new short[0][];
        }
    }

    float x(double lon) {
        return (float) ((lon + 180) / 360 * getWidth());
    }

    float y(double lat) {
        return (float) ((TOP - lat) / (TOP - BOTTOM) * getHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        base = null;
        baked[0] = baked[1] = null;
        nightAt = 0;
    }

    private void drawBase() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(base);
        for (int lon = -150; lon < 180; lon += 30) c.drawLine(x(lon), 0, x(lon), h, grid);
        for (int lat = -30; lat <= 60; lat += 30) c.drawLine(0, y(lat), w, y(lat), grid);
        Path p = new Path();
        for (short[] poly : land) {
            p.reset();
            for (int j = 0; j < poly.length; j += 2) {
                float px = x(poly[j] / 100.0), py = y(poly[j + 1] / 100.0);
                if (j == 0) p.moveTo(px, py);
                else p.lineTo(px, py);
            }
            p.close();
            c.drawPath(p, landFill);
            if (!compact) c.drawPath(p, coast);
        }
    }

    /** The night side, from the sun's position now (declination and the longitude where it's noon). */
    private void buildNight() {
        long now = System.currentTimeMillis();
        nightAt = now;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(now);
        int day = cal.get(Calendar.DAY_OF_YEAR);
        double hours = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0;
        double decl = -23.44 * Math.cos(2 * Math.PI / 365 * (day + 10));
        if (Math.abs(decl) < 0.1) decl = 0.1;
        double sunLon = -15 * (hours - 12);
        double tanD = Math.tan(Math.toRadians(decl));
        nightPath.reset();
        int w = getWidth();
        for (int px = 0; px <= w; px += 4) {
            double lon = px * 360.0 / w - 180;
            double lat = Math.toDegrees(Math.atan(-Math.cos(Math.toRadians(lon - sunLon)) / tanD));
            if (px == 0) nightPath.moveTo(px, y(lat));
            else nightPath.lineTo(px, y(lat));
        }
        // Northern summer: night is south of the line; northern winter: north of it.
        float edge = decl > 0 ? getHeight() : 0;
        nightPath.lineTo(w, edge);
        nightPath.lineTo(0, edge);
        nightPath.close();
    }

    /** Rebuilt when the rain arrives, the rain view toggles or the night line moves (once a minute). */
    private void bake() {
        for (int k = 0; k < (rain != null ? 2 : 1); k++) {
            if (baked[k] == null) baked[k] = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.RGB_565);
            Canvas b = new Canvas(baked[k]);
            b.drawColor(0xFF0B1226);
            b.drawBitmap(base, 0, 0, null);
            b.drawPath(nightPath, night);
            if (rain != null && rain.getWidth() == getWidth()) {
                rainPaint.setAlpha(k == 1 ? 255 : 150); // under the pins it's context, not the story
                b.drawBitmap(rain, 0, 0, rainPaint);
            }
        }
        bakedStale = false;
    }

    static int colour(Intel.Event e) {
        switch (e.kind) {
            case Intel.QUAKE: return 0xFFFFA14A;
            case Intel.ALERT: return e.severity >= 2 ? 0xFFFF5D5D : 0xFFFFC34A;
            case Intel.NEWS: return 0xFF9FB4FF;
            case Intel.CAM: return 0xFF6BE3A0;
            default:
                String k = e.category == null ? "" : e.category;
                if (k.startsWith("Wildfire")) return 0xFFFF7A3D;
                if (k.startsWith("Severe")) return 0xFF6FC3FF;
                if (k.startsWith("Volcano")) return 0xFFFF4D8D;
                if (k.startsWith("Sea and Lake Ice") || k.startsWith("Snow")) return 0xFFCFE8FF;
                return 0xFFB79CFF;
        }
    }

    float radius(Intel.Event e) {
        float r = e.kind.equals(Intel.NEWS) ? 3.5f + Math.min(e.count, 8) * 1.1f : e.severity == 2 ? 7.5f : e.severity == 1 ? 5f : 3.2f;
        if (e.kind.equals(Intel.CAM)) r = 3.6f;
        return Ui.dp(compact ? r * 0.6f : r);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (base == null) drawBase();
        if (base == null) return;
        if (System.currentTimeMillis() - nightAt > 60_000) {
            buildNight();
            bakedStale = true;
        }
        if (compact) { // Home's one-off snapshot: no need to keep a baked copy
            c.drawBitmap(base, 0, 0, null);
            c.drawPath(nightPath, night);
        } else {
            if (baked[0] == null || bakedStale) bake();
            c.drawBitmap(baked[rainOnly && rain != null ? 1 : 0], 0, 0, null);
        }

        for (Intel.Event e : events) {
            if (e == selected) continue;
            drawPin(c, e, false);
        }
        if (iss != null) {
            float ix = x(iss[1]), iy = y(iss[0]);
            pin.setStyle(Paint.Style.STROKE);
            pin.setStrokeWidth(Ui.dp(1.5f));
            pin.setColor(0xCCFFFFFF);
            c.drawCircle(ix, iy, Ui.dp(compact ? 4 : 7), pin);
            pin.setStyle(Paint.Style.FILL);
            pin.setColor(0xFFFFFFFF);
            c.drawCircle(ix, iy, Ui.dp(compact ? 1.5f : 2.5f), pin);
            if (!compact) c.drawText("ISS", ix + Ui.dp(9), iy + Ui.dp(4), label);
        }
        if (selected != null) {
            drawPin(c, selected, true);
            if (!compact) drawLabel(c, selected);
        }
    }

    private void drawPin(Canvas c, Intel.Event e, boolean sel) {
        float px = x(e.lon), py = y(e.lat), r = radius(e);
        int col = colour(e);
        if (e.severity >= 1 || sel) {
            pin.setStyle(Paint.Style.FILL);
            pin.setColor((col & 0x00FFFFFF) | (sel ? 0x66000000 : 0x33000000));
            c.drawCircle(px, py, r * (sel ? 2.6f : 1.9f), pin);
        }
        pin.setStyle(Paint.Style.FILL);
        pin.setColor(col);
        c.drawCircle(px, py, r, pin);
        if (sel) {
            pin.setStyle(Paint.Style.STROKE);
            pin.setStrokeWidth(Ui.dp(2));
            pin.setColor(0xFFFFFFFF);
            c.drawCircle(px, py, r + Ui.dp(4), pin);
        }
    }

    private void drawLabel(Canvas c, Intel.Event e) {
        String t = L.t(e.title);
        if (t.length() > 40) t = t.substring(0, 39) + "…";
        float w = label.measureText(t) + Ui.dp(14), h = Ui.dp(22);
        float px = x(e.lon), py = y(e.lat);
        float lx = Math.min(Math.max(px + Ui.dp(14), Ui.dp(4)), getWidth() - w - Ui.dp(4));
        float ly = Math.min(Math.max(py - h - Ui.dp(8), Ui.dp(4)), getHeight() - h - Ui.dp(4));
        c.drawRoundRect(lx, ly, lx + w, ly + h, Ui.dp(6), Ui.dp(6), labelBg);
        c.drawText(t, lx + Ui.dp(7), ly + h - Ui.dp(7), label);
    }

    /** A small picture of the map with its pins, for Home's card. */
    static Bitmap snapshot(Context ctx, List<Intel.Event> events, int w, int h) {
        WorldMapView v = new WorldMapView(ctx);
        v.setCompact(true);
        v.layout(0, 0, w, h);
        v.events = events;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF0B1226);
        v.onDraw(c);
        return b;
    }
}
