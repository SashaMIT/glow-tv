package com.tv98.glow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Glow ▸ Settings ▸ Extra apps: open-source apps installed from their official releases, only when picked. */
public class ExtraAppsActivity extends Activity {
    private LinearLayout list;
    private TextView status;

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
        h.setText("Extra apps");
        col.addView(h);
        TextView note = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        note.setText("Open-source apps, downloaded from their developers' own releases when you choose one. Android asks you to"
                + " confirm each install. They're separate apps with their own terms; install them only if you want to.");
        note.setLineSpacing(0, 1.2f);
        note.setPadding(0, Ui.dp(8), 0, Ui.dp(16));
        col.addView(note);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        status = Ui.text(this, 14, Ui.ACCENT, 600);
        status.setPadding(0, Ui.dp(14), 0, 0);
        col.addView(status);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill(); // installed versions may have changed
    }

    private void fill() {
        View focused = getCurrentFocus();
        int at = focused != null ? list.indexOfChild(focused) : 0;
        list.removeAllViews();
        for (final String[] app : ExtraApps.APPS) {
            final String version = ExtraApps.installed(this, app[1]);
            String source = app[3].startsWith("https://") ? "kodi.tv official mirror" : "github.com/" + app[3];
            row(app[0] + (version != null ? "   ·   installed " + version : ""), app[2] + "\n" + source + "   ·   " + app[5],
                    version != null ? "OPEN" : "INSTALL", v -> {
                        if (version != null) {
                            Intent open = getPackageManager().getLeanbackLaunchIntentForPackage(app[1]);
                            if (open == null) open = getPackageManager().getLaunchIntentForPackage(app[1]);
                            if (open != null) startActivity(open);
                        } else {
                            offer(app);
                        }
                    });
        }
        if (list.getChildCount() > 0) list.getChildAt(Math.max(0, Math.min(at, list.getChildCount() - 1))).requestFocus();
    }

    /** Looks up the latest official release, says what it is, and installs it only after a yes. */
    private void offer(final String[] app) {
        status.setText("Checking " + app[0] + "'s latest release…");
        Net.IO.execute(() -> {
            ExtraApps.Release r;
            try {
                r = ExtraApps.latest(app);
            } catch (Exception e) {
                r = null;
            }
            final ExtraApps.Release rel = r;
            Net.MAIN.post(() -> {
                if (isFinishing()) return;
                if (rel == null) {
                    status.setText("Couldn't find " + app[0] + "'s release right now. Try again later.");
                    return;
                }
                status.setText("");
                String size = rel.size > 0 ? (rel.size >> 20) + " MB" : "about 80 MB";
                new L.Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Install " + app[0] + " " + rel.version + "?")
                        .setMessage(app[2] + ".\n\nDownloads " + size + " from " + rel.url.replaceFirst("^https://", "").replaceFirst("/[^/]*$", "/…")
                                + "\nLicence " + app[5] + ". Android will ask you to confirm.")
                        .setPositiveButton("Download and install", (d, w) -> Net.IO.execute(() ->
                                Updates.installApk(this, rel.url, app[0], null, text -> Net.MAIN.post(() -> status.setText(text)))))
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });
    }

    private void row(String title, String sub, String action, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(16), true));
        bg.addState(new int[] {}, Ui.glass(Ui.dp(16), false));
        r.setBackground(bg);
        r.setOnClickListener(click);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 17, Ui.TEXT, 600);
        t.setText(title);
        TextView s = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        s.setText(sub);
        s.setLineSpacing(0, 1.2f);
        s.setPadding(0, Ui.dp(5), Ui.dp(12), 0);
        words.addView(t);
        words.addView(s);
        r.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView pill = Ui.text(this, 12, Ui.ACCENT, 600);
        pill.setText(action);
        pill.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(6));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Ui.dp(14));
        g.setStroke(Math.max(1, Ui.dp(1)), Ui.ACCENT);
        pill.setBackground(g);
        r.addView(pill);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(10);
        list.addView(r, lp);
    }
}
