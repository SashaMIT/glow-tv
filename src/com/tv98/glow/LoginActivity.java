package com.tv98.glow;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Sign in with the Xtream details an IPTV provider gives you (server, username, password). */
public class LoginActivity extends Activity {
    private EditText server, user, pass;
    private TextView status;
    private Button signIn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Ui.Aurora());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.glass(Ui.dp(22), false));
        card.setPadding(Ui.dp(34), Ui.dp(28), Ui.dp(34), Ui.dp(26));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.dp(500), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(card, clp);

        TextView title = Ui.text(this, 26, Ui.TEXT, 300);
        title.setText("IPTV login");
        card.addView(title);
        TextView sub = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        sub.setText("Enter the Xtream details from your provider. They're kept only on this box.");
        sub.setPadding(0, Ui.dp(8), 0, Ui.dp(18));
        sub.setLineSpacing(0, 1.2f);
        card.addView(sub);

        Xtream.Account a = Xtream.load(this);
        server = field(card, "Server address  (e.g. http://example.com:8080)", InputType.TYPE_TEXT_VARIATION_URI, a == null ? "" : a.base);
        user = field(card, "Username", InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, a == null ? "" : a.user);
        pass = field(card, "Password", InputType.TYPE_TEXT_VARIATION_PASSWORD, a == null ? "" : a.pass);
        server.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        user.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        pass.setImeOptions(EditorInfo.IME_ACTION_GO);
        pass.setOnEditorActionListener((v, action, ev) -> {
            boolean enterKey = action == EditorInfo.IME_NULL && ev != null && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_GO && action != EditorInfo.IME_ACTION_DONE && !enterKey) return false;
            signIn();
            return true;
        });

        status = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        status.setPadding(Ui.dp(4), Ui.dp(4), 0, Ui.dp(14));
        status.setText(a == null ? " " : "Signed in to " + a.base);
        card.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        signIn = button("Sign in");
        signIn.setOnClickListener(v -> signIn());
        buttons.addView(signIn);
        if (a != null) {
            Button out = button("Sign out");
            out.setOnClickListener(v -> {
                Xtream.signOut(this);
                finish();
            });
            buttons.addView(out);
        }
        card.addView(buttons);

        setContentView(root);
        (a == null ? server : signIn).requestFocus();
    }

    private EditText field(LinearLayout parent, String hint, int variation, String value) {
        EditText e = new EditText(this);
        e.setHint(L.t(hint));
        e.setText(value);
        e.setSingleLine();
        e.setInputType(InputType.TYPE_CLASS_TEXT | variation);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(0x66F2F4FF);
        e.setTextSize(15);
        e.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        e.setBackground(states(Ui.dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(12);
        parent.addView(e, lp);
        return e;
    }

    /** Text sent from the phone remote goes into the focused field; "submit" then signs in. */
    void typeFromPhone(String text, boolean submit) {
        android.view.View f = getCurrentFocus();
        EditText target = f instanceof EditText ? (EditText) f : server;
        target.setText(text);
        target.setSelection(target.length());
        if (target == server) user.requestFocus();
        else if (target == user) pass.requestFocus();
        if (submit) signIn();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
    }

    private Button button(String text) {
        Button bt = new Button(this);
        bt.setOnFocusChangeListener((v, has) -> {
            if (has) hideKeyboard();
        });
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextColor(Ui.TEXT);
        bt.setTextSize(15);
        bt.setStateListAnimator(null);
        bt.setPadding(Ui.dp(26), Ui.dp(10), Ui.dp(26), Ui.dp(10));
        bt.setBackground(states(Ui.dp(22)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(12);
        bt.setLayoutParams(lp);
        return bt;
    }

    private static StateListDrawable states(int radius) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = Ui.glass(radius, true);
        s.addState(new int[] {android.R.attr.state_focused}, on);
        s.addState(new int[] {}, Ui.glass(radius, false));
        return s;
    }

    private void signIn() {
        String s = server.getText().toString().trim(), u = user.getText().toString().trim(), p = pass.getText().toString();
        if (s.isEmpty() || u.isEmpty() || p.isEmpty()) {
            status.setText("Fill in all three: server, username and password.");
            return;
        }
        final Xtream.Account a = new Xtream.Account(Xtream.normalizeBase(s), u, p);
        hideKeyboard();
        signIn.requestFocus();
        status.setText("Checking with the provider…");
        signIn.setEnabled(false);
        Net.IO.execute(() -> {
            final Xtream.Status st = Xtream.check(a);
            Net.MAIN.post(() -> {
                signIn.setEnabled(true);
                if (st.ok) {
                    Xtream.save(this, a);
                    status.setText("Signed in. " + st.message);
                    signIn.postDelayed(this::finish, 900);
                } else {
                    status.setText(st.message);
                }
            });
        });
    }
}
