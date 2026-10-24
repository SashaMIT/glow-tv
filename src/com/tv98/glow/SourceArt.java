package com.tv98.glow;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Stand-in picture for a story without one: a gradient in the story's colour with the outlet's name. */
final class SourceArt extends Drawable {
    private final String name;
    private final int color;
    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    SourceArt(String source, int color, float radiusPx) {
        this.name = source == null ? "" : source;
        this.color = color;
        this.radius = radiusPx;
        text.setColor(0xEEFFFFFF);
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas c) {
        r.set(getBounds());
        fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                (color & 0x00FFFFFF) | 0xAA000000, 0xFF141A33, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, fill);
        float size = r.height() * 0.24f;
        text.setTextSize(size);
        while (size > 8 && text.measureText(name) > r.width() * 0.84f) text.setTextSize(size -= 1);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(name, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text);
    }

    @Override public void setAlpha(int a) { fill.setAlpha(a); text.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
