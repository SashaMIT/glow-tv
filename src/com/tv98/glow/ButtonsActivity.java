package com.tv98.glow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Glow ▸ Settings ▸ Remote buttons: press a spare button on the remote, then choose what it does (and when held). */
public class ButtonsActivity extends Activity {
    private LinearLayout list;
    private TextView prompt;
    private boolean listening;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new Ui.Aurora());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(140), Ui.dp(36), Ui.dp(140), Ui.dp(36));
        scroll.addView(col);
        TextView h = Ui.text(this, 26, Ui.TEXT, 300);
        h.setText("Remote buttons");
        col.addView(h);
        TextView note = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        note.setText("Give spare buttons on your remote a job: an app, the TV Guide, Sports now, Quick settings… Press does one thing,"
                + " holding can do another. Moving around, OK, Back, Home, numbers and volume keep their usual jobs.");
        note.setLineSpacing(0, 1.2f);
        note.setPadding(0, Ui.dp(8), 0, Ui.dp(16));
        col.addView(note);
        prompt = Ui.text(this, 16, Ui.ACCENT, 600);
        prompt.setPadding(0, 0, 0, Ui.dp(12));
        col.addView(prompt);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        list.removeAllViews();
        boolean everywhere = Buttons.everywhere(this);
        row(everywhere ? "Works in every app: on" : "Works in every app: off",
                everywhere ? "Your buttons work in Netflix, YouTube and everywhere else too."
                        : "Right now your buttons work while Glow is on screen. To use them in every app, switch on "
                        + "\"Glow remote buttons\" in Android's Accessibility settings (press OK to open them).",
                v -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) {
                        prompt.setText("This box doesn't open Accessibility settings directly: Android Settings ▸ Device preferences ▸ Accessibility.");
                    }
                });
        row("Add or change a button", "Press OK, then the button on your remote.", v -> {
            listening = true;
            prompt.setText("Press the remote button you want to set…");
        });
        for (final int code : Buttons.all(this)) {
            row(Buttons.keyName(code) + "   →   " + Buttons.label(this, Buttons.action(this, code, false)),
                    "Hold: " + Buttons.label(this, Buttons.action(this, code, true)) + "   ·   OK to change or clear",
                    v -> edit(code));
        }
        if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (listening && e.getAction() == KeyEvent.ACTION_DOWN) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) {
                listening = false;
                prompt.setText("");
                return true;
            }
            if (!Buttons.assignable(code)) {
                prompt.setText(Buttons.keyName(code) + " already has a job in Glow. Try another button (Back to stop).");
                return true;
            }
            listening = false;
            prompt.setText("");
            edit(code);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void edit(final int code) {
        final List<String> actions = new ArrayList<>(), labels = new ArrayList<>();
        Buttons.choices(this, actions, labels);
        actions.add(0, null);
        labels.add(0, "Nothing (clear this button)");
        new L.Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Buttons.keyName(code) + ": when pressed")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    final String press = actions.get(which);
                    if (press == null) {
                        Buttons.set(this, code, null, null);
                        fill();
                        return;
                    }
                    labels.set(0, "Nothing extra");
                    new L.Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle(Buttons.keyName(code) + ": when held")
                            .setItems(labels.toArray(new String[0]), (d2, w2) -> {
                                Buttons.set(this, code, press, actions.get(w2));
                                fill();
                                prompt.setText(Buttons.keyName(code) + " set." + (Buttons.everywhere(this) ? "" : " It works while Glow is on screen."));
                            })
                            .show();
                })
                .show();
    }

    private void row(String title, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(16), true));
        bg.addState(new int[] {}, Ui.glass(Ui.dp(16), false));
        r.setBackground(bg);
        r.setOnClickListener(click);
        TextView t = Ui.text(this, 17, Ui.TEXT, 600);
        t.setText(title);
        TextView s = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        s.setText(sub);
        s.setLineSpacing(0, 1.2f);
        s.setPadding(0, Ui.dp(5), 0, 0);
        r.addView(t);
        r.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(10);
        list.addView(r, lp);
    }
}
