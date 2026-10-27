package com.tv98.glow;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;

/**
 * Banners over any app (notifications, score alerts while Netflix or YouTube is on). Needs Android's "display over
 * other apps" permission for Glow, which the viewer grants; without it nothing is shown outside Glow.
 */
final class Overlay {
    private static final ArrayDeque<String[]> queue = new ArrayDeque<>();
    private static boolean showing;

    private Overlay() {}

    static boolean allowed(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** {badge, headline, line, badge colour as hex or ""}. Main thread. */
    static void show(Context c, String badge, String headline, String line, int colour) {
        if (!allowed(c)) return;
        queue.add(new String[] {badge, headline, line, String.valueOf(colour)});
        next(c.getApplicationContext());
    }

    /**
     * A bigger card with a picture (the doorbell camera), shown at once for 20 s, over Glow or any other app.
     * Without "display over other apps" it still shows while Glow is on screen, as a dialog. Main thread.
     */
    static void picture(Context c, String headline, String line, android.graphics.Bitmap pic) {
        Context app = c.getApplicationContext();
        LinearLayout box = new LinearLayout(app);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(16));
        bg.setColor(0xF20B0E1C);
        bg.setStroke(Ui.dp(2), Ui.ACCENT);
        box.setBackground(bg);
        if (pic != null) {
            android.widget.ImageView iv = new android.widget.ImageView(app);
            iv.setImageBitmap(pic);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            box.addView(iv, new LinearLayout.LayoutParams(Ui.dp(480), Ui.dp(270)));
        }
        TextView h = Ui.text(app, 18, Ui.TEXT, 600);
        h.setText("🔔  " + L.t(headline));
        h.setPadding(Ui.dp(4), Ui.dp(10), 0, 0);
        TextView l = Ui.text(app, 13, Ui.TEXT_DIM, 400);
        l.setText(line);
        l.setPadding(Ui.dp(4), Ui.dp(2), 0, 0);
        box.addView(h);
        box.addView(l);
        final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams lp;
        if (allowed(app)) {
            lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        } else {
            android.app.Activity front = GlowApp.front();
            if (front == null) return;
            android.app.AlertDialog d = new android.app.AlertDialog.Builder(front, android.R.style.Theme_DeviceDefault_Dialog)
                    .setView(box).show();
            box.postDelayed(() -> { if (d.isShowing()) d.dismiss(); }, 20_000);
            return;
        }
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Ui.dp(32);
        lp.y = Ui.dp(28);
        try {
            wm.addView(box, lp);
        } catch (Exception e) {
            return;
        }
        box.setAlpha(0f);
        box.animate().alpha(1f).setDuration(250).start();
        box.postDelayed(() -> box.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            try {
                wm.removeView(box);
            } catch (Exception ignored) {
            }
        }).start(), 20_000);
    }

    private static void next(final Context c) {
        if (showing || queue.isEmpty()) return;
        String[] b = queue.poll();
        final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(18), Ui.dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(14));
        bg.setColor(0xEB0B0E1C);
        bg.setStroke(Math.max(1, Ui.dp(1)), 0x33FFFFFF);
        box.setBackground(bg);
        TextView badge = Ui.text(c, 11, 0xFFFFFFFF, 600);
        badge.setText(b[0]);
        badge.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        GradientDrawable bb = new GradientDrawable();
        bb.setCornerRadius(Ui.dp(6));
        bb.setColor(Integer.parseInt(b[3]));
        badge.setBackground(bb);
        box.addView(badge);
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(12), 0, 0, 0);
        TextView h = Ui.text(c, 13, Ui.ACCENT, 600);
        h.setText(b[1]);
        TextView l = Ui.text(c, 16, Ui.TEXT, 600);
        l.setText(b[2]);
        l.setSingleLine();
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l.setMaxWidth(Ui.dp(560));
        words.addView(h);
        words.addView(l);
        box.addView(words);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Ui.dp(32);
        lp.y = Ui.dp(28);
        try {
            wm.addView(box, lp);
        } catch (Exception e) {
            return;
        }
        showing = true;
        box.setAlpha(0f);
        box.animate().alpha(1f).setDuration(250).start();
        box.postDelayed(() -> box.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            try {
                wm.removeView(box);
            } catch (Exception ignored) {
            }
            showing = false;
            next(c);
        }).start(), 7000);
    }
}
