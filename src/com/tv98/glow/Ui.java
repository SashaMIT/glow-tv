package com.tv98.glow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Shared look: palette, sizes, the aurora background and frosted-glass surfaces. */
final class Ui {
    static final int ACCENT = 0xFF8FA8FF;
    static final int ACCENT_2 = 0xFFB57CFF;
    static final int TEXT = 0xFFF2F4FF;
    static final int TEXT_DIM = 0x99F2F4FF;
    static final int BASE = 0xFF070A14;

    static float density = 1f;

    private Ui() {}

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    /** Glow ▸ Settings ▸ Clock: automatic (the box's setting), 12-hour or 24-hour. */
    static boolean is24h(Context c) {
        String mode = c.getSharedPreferences("display", Context.MODE_PRIVATE).getString("clock", "auto");
        return mode.equals("24") || (mode.equals("auto") && android.text.format.DateFormat.is24HourFormat(c));
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static TextView text(Context c, float sp, int color, int weight) {
        TextView t = new L.Text(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(weight >= 600 ? "sans-serif-medium"
                : weight <= 300 ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, 13, TEXT_DIM, 600);
        t.setText(s.toUpperCase());
        t.setLetterSpacing(0.14f);
        t.setPadding(dp(26), dp(16), 0, 0);
        return t;
    }

    /** Frosted-glass card: soft white gradient fill with a hairline edge; accent edge when lit. */
    static GradientDrawable glass(float radiusPx, boolean lit) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                lit ? new int[] {0x45FFFFFF, 0x1AFFFFFF} : new int[] {0x22FFFFFF, 0x0DFFFFFF});
        g.setCornerRadius(radiusPx);
        g.setStroke(lit ? dp(2) : Math.max(1, dp(0.75f)), lit ? ACCENT : 0x2EFFFFFF);
        return g;
    }

    static TextView badge(Context c, String name, float sp) {
        TextView t = text(c, sp, TEXT, 600);
        t.setGravity(Gravity.CENTER);
        t.setText(initials(name));
        return t;
    }

    /** "Big Buck Bunny" -> "BB"; used when a channel has no logo. */
    static String initials(String name) {
        String s = name == null ? "" : name.trim();
        StringBuilder b = new StringBuilder();
        for (String w : s.split("[\\s\\-_|:]+")) {
            if (!w.isEmpty() && Character.isLetterOrDigit(w.charAt(0))) b.append(Character.toUpperCase(w.charAt(0)));
            if (b.length() == 2) break;
        }
        return b.length() == 0 ? "TV" : b.toString();
    }

    /** Dark navy with slow-glowing colour pools; rendered once, small, then scaled up smoothly. */
    static final class Aurora extends Drawable {
        private Bitmap bmp;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        @Override
        protected void onBoundsChange(Rect b) {
            if (b.width() <= 0 || b.height() <= 0) return;
            int w = 480, h = Math.max(1, w * b.height() / b.width());
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(BASE);
            pool(c, w * 0.12f, h * 0.05f, w * 0.75f, 0x704A62FF);
            pool(c, w * 0.92f, h * 0.20f, w * 0.60f, 0x55A05CFF);
            pool(c, w * 0.55f, h * 1.15f, w * 0.85f, 0x4022C3EE);
        }

        private static void pool(Canvas c, float x, float y, float r, int color) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }

        @Override
        public void draw(Canvas canvas) {
            if (bmp != null) canvas.drawBitmap(bmp, null, getBounds(), paint);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
