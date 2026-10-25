package com.tv98.glow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * A glass card that lifts and glows when it has focus (or is "lit" by a list selection).
 * The glow is a pre-blurred bitmap drawn behind the card, so focusing costs one bitmap draw.
 */
class Tile extends FrameLayout {
    private static final Map<String, Bitmap> GLOWS = new HashMap<>();

    final FrameLayout card;
    /** How far the glow reaches past the card. Rows leave at least this much room so it never gets cut. */
    static final int GLOW_DP = 11;

    private final int radius = Ui.dp(14);
    private final int pad = Ui.dp(GLOW_DP);
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private ValueAnimator anim;
    private float level;
    private boolean lit;
    private float lift = 1.05f;
    private int solid; // a plain card colour (0 = glass)

    Tile(Context c, int widthDp, int heightDp) {
        super(c);
        setWillNotDraw(false);
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(widthDp), Ui.dp(heightDp));
        lp.setMargins(Ui.dp(9), 0, Ui.dp(9), 0);
        setLayoutParams(lp);

        card = new FrameLayout(c);
        card.setBackground(Ui.glass(radius, false));
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        // Each card is drawn once into its own GPU texture and reused while rows scroll and tiles scale, instead of
        // re-issuing its glass, clip and contents every frame (the Mali-G31's bottleneck): Home scrolling went from
        // ~30 % late frames to ~1 %. Home releases the textures when it's hidden (Tile.release).
        card.setLayerType(LAYER_TYPE_HARDWARE, null);
        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Gives back the GPU textures of a row's cards (their screen is going out of sight). */
    static void release(ViewGroup row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Tile) ((Tile) v).card.setLayerType(LAYER_TYPE_NONE, null);
        }
    }

    /** Rows kept between visits to Home get their cached textures back (released in onPause). */
    static void restore(ViewGroup row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Tile && ((Tile) v).card.getLayerType() != LAYER_TYPE_HARDWARE) {
                ((Tile) v).card.setLayerType(LAYER_TYPE_HARDWARE, null);
            }
        }
    }

    void setLift(float scale) {
        lift = scale;
    }

    @Override
    protected void onFocusChanged(boolean gain, int dir, Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        setLit(gain);
    }

    /** A plain white or black card (channel logos), kept through focus changes; the glow shows focus. */
    void setSolid(int colour) {
        solid = colour;
        if (colour == 0) {
            card.setBackground(Ui.glass(radius, lit));
            return;
        }
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(radius);
        g.setColor(colour);
        card.setBackground(g);
    }

    void setLit(boolean on) {
        if (lit == on) return;
        lit = on;
        if (solid == 0) card.setBackground(Ui.glass(radius, on));
        // No elevation: a real-time shadow under every focused tile was the GPU's biggest per-frame cost,
        // and the glow already gives the lifted look.
        animate().scaleX(on ? lift : 1f).scaleY(on ? lift : 1f)
                .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(level, on ? 1f : 0f).setDuration(on ? 200 : 140);
        anim.addUpdateListener(a -> { level = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    public void draw(Canvas canvas) {
        if (level > 0.01f && getWidth() > 0) {
            Bitmap g = glow(getWidth(), getHeight());
            glowPaint.setAlpha((int) (255 * level));
            canvas.drawBitmap(g, -pad, -pad, glowPaint);
        }
        super.draw(canvas);
    }

    private Bitmap glow(int w, int h) {
        String key = w + "x" + h;
        Bitmap b = GLOWS.get(key);
        if (b != null) return b;
        b = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Blur stays well inside the bitmap so the glow fades to nothing before its edge.
        p.setMaskFilter(new BlurMaskFilter(pad * 0.45f, BlurMaskFilter.Blur.NORMAL));
        p.setColor(0x5C8FA8FF);
        float in = pad * 0.5f;
        c.drawRoundRect(new RectF(in, in, w + 2 * pad - in, h + 2 * pad - in), radius * 1.2f, radius * 1.2f, p);
        p.setColor(0x2EB57CFF);
        c.drawRoundRect(new RectF(in, in * 1.4f, w + 2 * pad - in, h + 2 * pad - in * 0.7f),
                radius * 1.2f, radius * 1.2f, p);
        GLOWS.put(key, b);
        return b;
    }
}
