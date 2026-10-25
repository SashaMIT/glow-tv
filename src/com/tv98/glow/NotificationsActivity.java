package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Glow ▸ Settings ▸ Notifications & banners: what's allowed, and the latest notifications from the box's apps. */
public class NotificationsActivity extends Activity {
    private LinearLayout list;

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
        h.setText("Notifications & banners");
        col.addView(h);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(16), 0, 0);
        col.addView(list);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        list.removeAllViews();
        boolean over = Overlay.allowed(this), notes = GlowNotifications.allowed(this);
        row("Banners over other apps: " + (over ? "allowed" : "not allowed"),
                over ? "Score alerts and notifications show while Netflix, YouTube or any app is on."
                        : "Android needs \"Display over other apps\" for Glow (press OK to open it). Without it, banners only show in Glow.",
                v -> open(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true));
        row("Notifications from other apps: " + (notes ? "on" : "off"),
                notes ? "New notifications show as a banner, and the latest 30 are listed below."
                        : "Android needs notification access for Glow (press OK to open it).",
                v -> open("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", false));
        synchronized (GlowNotifications.recent) {
            if (GlowNotifications.recent.isEmpty()) row("No notifications yet", "", null);
            for (String[] n : GlowNotifications.recent) {
                row(n[1] + "   ·   " + android.text.format.DateFormat.format("HH:mm", Long.parseLong(n[0])), n[2] + (n[3].isEmpty() ? "" : "\n" + n[3]), null);
            }
        }
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private void open(String action, boolean withPackage) {
        try {
            Intent i = new Intent(action);
            if (withPackage) i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "This box doesn't have that settings page; it can be allowed over adb instead.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void row(String title, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setClickable(click != null);
        r.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(16), true));
        bg.addState(new int[] {}, Ui.glass(Ui.dp(16), false));
        r.setBackground(bg);
        if (click != null) r.setOnClickListener(click);
        TextView t = Ui.text(this, 17, Ui.TEXT, 600);
        t.setText(title);
        r.addView(t);
        if (!sub.isEmpty()) {
            TextView s = Ui.text(this, 12, Ui.TEXT_DIM, 400);
            s.setText(sub);
            s.setLineSpacing(0, 1.2f);
            s.setPadding(0, Ui.dp(5), 0, 0);
            r.addView(s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(10);
        list.addView(r, lp);
    }
}
