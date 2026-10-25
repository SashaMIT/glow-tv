package com.tv98.glow;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small line icons for the Home header, drawn as vectors so they stay crisp at any size. */
final class Icons extends Drawable {
    static final int SEARCH = 0, WORLD = 1, PHONE = 2, IPTV = 3, SETTINGS = 4, SHIELD = 5, GUIDE = 6, CAST = 7;

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    Icons(int kind, int color) {
        this.kind = kind;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width(), h = getBounds().height();
        float s = Math.min(w, h), x0 = getBounds().left + (w - s) / 2, y0 = getBounds().top + (h - s) / 2;
        float u = s / 24f; // drawn on a 24-unit grid
        p.setStrokeWidth(1.9f * u);
        p.setStyle(Paint.Style.STROKE);
        c.save();
        c.translate(x0, y0);
        switch (kind) {
            case SEARCH:
                c.drawCircle(10.5f * u, 10.5f * u, 6.5f * u, p);
                c.drawLine(15.3f * u, 15.3f * u, 20.5f * u, 20.5f * u, p);
                break;
            case WORLD:
                c.drawCircle(12 * u, 12 * u, 8.5f * u, p);
                r.set(8 * u, 3.5f * u, 16 * u, 20.5f * u);
                c.drawOval(r, p);
                c.drawLine(3.8f * u, 9 * u, 20.2f * u, 9 * u, p);
                c.drawLine(3.8f * u, 15 * u, 20.2f * u, 15 * u, p);
                break;
            case PHONE:
                r.set(7 * u, 2.5f * u, 17 * u, 21.5f * u);
                c.drawRoundRect(r, 2.5f * u, 2.5f * u, p);
                c.drawLine(10.5f * u, 18.3f * u, 13.5f * u, 18.3f * u, p);
                break;
            case IPTV:
                r.set(2.5f * u, 6 * u, 21.5f * u, 18 * u);
                c.drawRoundRect(r, 2 * u, 2 * u, p);
                c.drawLine(8.5f * u, 21 * u, 15.5f * u, 21 * u, p);
                c.drawLine(9 * u, 2.5f * u, 12 * u, 5.5f * u, p);
                c.drawLine(15 * u, 2.5f * u, 12 * u, 5.5f * u, p);
                break;
            case SETTINGS:
                c.drawCircle(12 * u, 12 * u, 3.2f * u, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cx = 12 * u, cy = 12 * u;
                    c.drawLine(cx + (float) Math.cos(a) * 6.3f * u, cy + (float) Math.sin(a) * 6.3f * u,
                            cx + (float) Math.cos(a) * 9.3f * u, cy + (float) Math.sin(a) * 9.3f * u, p);
                }
                c.drawCircle(12 * u, 12 * u, 6.3f * u, p);
                break;
            case SHIELD:
                path.reset();
                path.moveTo(12 * u, 2.5f * u);
                path.lineTo(19.5f * u, 5.5f * u);
                path.lineTo(19.5f * u, 11 * u);
                path.cubicTo(19.5f * u, 16 * u, 16 * u, 19.5f * u, 12 * u, 21.5f * u);
                path.cubicTo(8 * u, 19.5f * u, 4.5f * u, 16 * u, 4.5f * u, 11 * u);
                path.lineTo(4.5f * u, 5.5f * u);
                path.close();
                c.drawPath(path, p);
                c.drawLine(12 * u, 7.5f * u, 12 * u, 12.5f * u, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12 * u, 16 * u, 1.2f * u, p);
                break;
            case GUIDE:
                r.set(3 * u, 4 * u, 21 * u, 20 * u);
                c.drawRoundRect(r, 2 * u, 2 * u, p);
                c.drawLine(3 * u, 9 * u, 21 * u, 9 * u, p);
                c.drawLine(3 * u, 14.5f * u, 21 * u, 14.5f * u, p);
                c.drawLine(9 * u, 4 * u, 9 * u, 20 * u, p);
                break;
            case CAST: // a screen with cast waves in its corner
                path.reset();
                path.moveTo(2.5f * u, 8 * u);
                path.lineTo(2.5f * u, 6 * u);
                path.quadTo(2.5f * u, 4 * u, 4.5f * u, 4 * u);
                path.lineTo(19.5f * u, 4 * u);
                path.quadTo(21.5f * u, 4 * u, 21.5f * u, 6 * u);
                path.lineTo(21.5f * u, 18 * u);
                path.quadTo(21.5f * u, 20 * u, 19.5f * u, 20 * u);
                path.lineTo(14 * u, 20 * u);
                c.drawPath(path, p);
                r.set(-6 * u, 9.5f * u, 15 * u, 30.5f * u);
                c.drawArc(r, 270, 90, false, p);
                r.set(-2.5f * u, 13 * u, 8.5f * u, 24 * u);
                c.drawArc(r, 270, 90, false, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(3 * u, 19.5f * u, 1.3f * u, p);
                break;
            default:
                break;
        }
        c.restore();
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
