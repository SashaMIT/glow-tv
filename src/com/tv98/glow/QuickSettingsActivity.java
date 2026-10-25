package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Quick settings: a small panel over whatever's on (give it a remote button in Remote buttons). Night sound,
 * subtitles, picture size and the sleep timer apply to Glow's player straight away.
 */
public class QuickSettingsActivity extends Activity {
    private static final String[] PICTURE = {"Fit", "Fill", "Stretch", "Zoom"};
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(18));
        bg.setColor(0xF20B0E1C);
        bg.setStroke(Math.max(1, Ui.dp(1)), 0x33FFFFFF);
        box.setBackground(bg);
        setContentView(box, new ViewGroup.LayoutParams(Ui.dp(420), ViewGroup.LayoutParams.WRAP_CONTENT));
        getWindow().setGravity(Gravity.END | Gravity.TOP);
        fill(0);
    }

    private void fill(int focus) {
        box.removeAllViews();
        TextView h = Ui.text(this, 18, Ui.TEXT, 600);
        h.setText("Quick settings");
        h.setPadding(Ui.dp(6), 0, 0, Ui.dp(10));
        box.addView(h);
        final SharedPreferences p = getSharedPreferences("player", MODE_PRIVATE);
        boolean night = p.getBoolean("night_sound", false), subs = p.getString("subs_lang", null) != null;
        int pic = p.getInt("picture_mode", 0);
        long left = PlayerActivity.sleepAt == 0 ? 0 : Math.max(0, PlayerActivity.sleepAt - System.currentTimeMillis()) / 60_000;
        item("♪  Night sound: " + (night ? "on" : "off"), () -> p.edit().putBoolean("night_sound", !night).apply());
        item("⓪  Subtitles: " + (subs ? "on" : "off"), () -> {
            if (subs) p.edit().remove("subs_lang").apply();
            else p.edit().putString("subs_lang", "en").apply();
        });
        item("▭  Picture: " + PICTURE[pic], () -> p.edit().putInt("picture_mode", (pic + 1) % PICTURE.length).apply());
        item("☾  Sleep timer: " + (left > 0 ? left + " min left" : "off"), () -> {
            long now = System.currentTimeMillis();
            long[] steps = {30, 60, 90, 120};
            long next = 0;
            for (long s : steps) if (s > left + 1) { next = s; break; }
            PlayerActivity.sleepAt = next == 0 ? 0 : now + next * 60_000L;
        });
        item("◐  Screensaver now", () -> {
            try {
                startActivity(new Intent().setClassName("com.android.systemui", "com.android.systemui.Somnambulator"));
            } catch (Exception ignored) {
            }
            finish();
        });
        item("⌔  Wi-Fi", () -> open(android.provider.Settings.ACTION_WIFI_SETTINGS));
        item("▣  Picture & sound (Android)", () -> open(android.provider.Settings.ACTION_DISPLAY_SETTINGS));
        item("⚙  Glow settings", () -> startActivity(new Intent(this, SettingsActivity.class)));
        int first = 1 + Math.max(0, Math.min(focus, box.getChildCount() - 2));
        box.getChildAt(first).requestFocus();
    }

    private void open(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception ignored) {
        }
    }

    private void item(String text, Runnable act) {
        final int at = box.getChildCount() - 1;
        TextView t = Ui.text(this, 15, Ui.TEXT, 400);
        t.setText(text);
        t.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(10), true));
        bg.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0));
        t.setBackground(bg);
        t.setOnClickListener(v -> {
            act.run();
            if (!isFinishing()) fill(at); // show the new value, focus stays put
        });
        box.addView(t);
    }
}
