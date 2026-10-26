package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Kids mode PIN: four digits from the remote's number keys or the keypad on screen. Choosing one asks twice;
 * checking one allows five tries, then a minute's wait. "leave" switches Kids mode off and goes Home.
 */
public class PinActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String SET = "set", CHECK = "check", LEAVE = "leave";

    private String mode, first;
    private final StringBuilder typed = new StringBuilder();
    private TextView title, sub, dots;

    static void leave(Activity from) {
        from.startActivity(new Intent(from, PinActivity.class).putExtra(EXTRA_MODE, LEAVE));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = CHECK;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(28), Ui.dp(22), Ui.dp(28), Ui.dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(20));
        bg.setColor(0xF50B0E1C);
        bg.setStroke(Math.max(1, Ui.dp(1)), 0x33FFFFFF);
        box.setBackground(bg);

        title = Ui.text(this, 20, Ui.TEXT, 600);
        title.setGravity(Gravity.CENTER);
        sub = Ui.text(this, 12.5f, Ui.TEXT_DIM, 400);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(0, 1.2f);
        sub.setPadding(0, Ui.dp(6), 0, Ui.dp(12));
        dots = Ui.text(this, 30, Ui.ACCENT, 400);
        dots.setGravity(Gravity.CENTER);
        dots.setLetterSpacing(0.35f);
        dots.setPadding(0, 0, 0, Ui.dp(12));
        box.addView(title);
        box.addView(sub, new LinearLayout.LayoutParams(Ui.dp(320), ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(dots);

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✕"};
        View firstKey = null;
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int k = 0; k < 3; k++) {
                final String key = keys[r * 3 + k];
                TextView t = Ui.text(this, 20, Ui.TEXT, 400);
                t.setText(key);
                t.setGravity(Gravity.CENTER);
                t.setFocusable(true);
                t.setClickable(true);
                StateListDrawable s = new StateListDrawable();
                s.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(26), true));
                s.addState(new int[] {}, Ui.glass(Ui.dp(26), false));
                t.setBackground(s);
                t.setContentDescription(key.equals("⌫") ? "Delete" : key.equals("✕") ? "Cancel" : key);
                t.setOnClickListener(v -> press(key));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(52));
                lp.setMargins(Ui.dp(6), Ui.dp(5), Ui.dp(6), Ui.dp(5));
                row.addView(t, lp);
                if (key.equals("1")) firstKey = t;
            }
            box.addView(row);
        }
        setContentView(box, new ViewGroup.LayoutParams(Ui.dp(380), ViewGroup.LayoutParams.WRAP_CONTENT));
        show();
        if (firstKey != null) firstKey.requestFocus();
    }

    private void show() {
        long wait = Kids.waitMs(this);
        if (wait > 0 && !mode.equals(SET)) {
            title.setText("Too many tries");
            sub.setText(L.f("Try again in {} seconds.", (wait + 999) / 1000));
            dots.postDelayed(this::show, 1000);
        } else if (mode.equals(SET)) {
            title.setText(first == null ? "Choose a 4-digit PIN" : "Enter the PIN again");
            sub.setText("Grown-ups need it to leave Kids mode or open Settings. Keep it away from little eyes.");
        } else {
            title.setText(mode.equals(LEAVE) ? "Leave Kids mode" : "Enter the PIN");
            sub.setText("Number keys on the remote, or the keypad below.");
        }
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < 4; i++) d.append(i < typed.length() ? "●" : "○");
        dots.setText(d.toString());
    }

    private void press(String key) {
        if (key.equals("✕")) {
            finish();
            return;
        }
        if (key.equals("⌫")) {
            if (typed.length() > 0) typed.setLength(typed.length() - 1);
            show();
            return;
        }
        if (typed.length() >= 4 || (Kids.waitMs(this) > 0 && !mode.equals(SET))) return;
        typed.append(key);
        show();
        if (typed.length() == 4) dots.postDelayed(this::entered, 150);
    }

    private void entered() {
        String pin = typed.toString();
        typed.setLength(0);
        if (mode.equals(SET)) {
            if (first == null) {
                first = pin;
            } else if (first.equals(pin)) {
                Kids.setPin(this, pin);
                setResult(RESULT_OK);
                finish();
                return;
            } else {
                first = null;
                L.toast(this, "Those didn't match. Choose the PIN again.", android.widget.Toast.LENGTH_SHORT).show();
            }
            show();
            return;
        }
        if (Kids.check(this, pin)) {
            if (mode.equals(LEAVE)) {
                Kids.setOn(this, false);
                startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            }
            setResult(RESULT_OK);
            finish();
            return;
        }
        L.toast(this, "Wrong PIN", android.widget.Toast.LENGTH_SHORT).show();
        dots.animate().translationX(Ui.dp(10)).setDuration(60).withEndAction(() ->
                dots.animate().translationX(0).setDuration(120).start()).start();
        show();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            press(String.valueOf(code - KeyEvent.KEYCODE_0));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DEL) {
            press("⌫");
            return true;
        }
        return super.onKeyDown(code, e);
    }
}
