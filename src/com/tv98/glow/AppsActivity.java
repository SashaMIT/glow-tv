package com.tv98.glow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Glow ▸ Settings ▸ Apps on Home: OK shows or hides an app on the shelf; ◀ ▶ move it earlier or later. */
public class AppsActivity extends Activity {
    /** Apps shown first by default, in this order. Everything else follows A–Z. */
    static final List<String> DEFAULT_FIRST = Arrays.asList(
            "com.netflix.mediaclient", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.spotify.tv.android", "com.plexapp.android", "org.xbmc.kodi");
    /** Never on the shelf (Glow itself, settings, store and the stock launcher are reachable elsewhere). */
    static final List<String> NEVER = Arrays.asList(
            "com.tv98.glow", "com.android.tv.settings", "com.android.vending", "com.google.android.tvlauncher");

    private LinearLayout list;
    private List<ResolveInfo> apps = new ArrayList<>();

    /** Every launchable TV app in shelf order; hidden ones included only when {@code withHidden}. */
    static List<ResolveInfo> ordered(Context c, boolean withHidden) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        SharedPreferences p = c.getSharedPreferences("apps", Context.MODE_PRIVATE);
        List<String> saved = new ArrayList<>(Arrays.asList(TextUtils.split(p.getString("order", ""), ",")));
        Set<String> hidden = p.getStringSet("hidden", new HashSet<>());
        final Map<String, ResolveInfo> byPkg = new HashMap<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (!NEVER.contains(pkg) && !byPkg.containsKey(pkg)) byPkg.put(pkg, ri);
        }
        List<ResolveInfo> out = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (List<String> source : Arrays.asList(saved, DEFAULT_FIRST)) {
            for (String pkg : source) if (byPkg.containsKey(pkg) && placed.add(pkg)) out.add(byPkg.get(pkg));
        }
        List<ResolveInfo> rest = new ArrayList<>();
        final Map<ResolveInfo, String> labels = new HashMap<>();
        for (ResolveInfo ri : byPkg.values()) {
            if (placed.contains(ri.activityInfo.packageName)) continue;
            rest.add(ri);
            labels.put(ri, ri.loadLabel(pm).toString());
        }
        Collections.sort(rest, (x, y) -> labels.get(x).compareToIgnoreCase(labels.get(y)));
        out.addAll(rest);
        if (!withHidden) {
            List<ResolveInfo> shown = new ArrayList<>();
            for (ResolveInfo ri : out) if (!hidden.contains(ri.activityInfo.packageName)) shown.add(ri);
            return shown;
        }
        return out;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Ui.Aurora());
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new FrameLayout.LayoutParams(Ui.dp(620), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(28), 0, Ui.dp(28));
        scroll.addView(list);
        setContentView(root);
        apps = ordered(this, true);
        build(0);
    }

    private void build(int focus) {
        list.removeAllViews();
        TextView title = Ui.text(this, 26, Ui.TEXT, 300);
        title.setText("Apps on Home");
        title.setPadding(Ui.dp(6), 0, 0, Ui.dp(4));
        list.addView(title);
        TextView hint = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        hint.setText("OK shows or hides an app   ·   ◀ ▶ moves it earlier or later on the shelf");
        hint.setPadding(Ui.dp(6), 0, 0, Ui.dp(14));
        list.addView(hint);
        Set<String> hidden = getSharedPreferences("apps", MODE_PRIVATE).getStringSet("hidden", new HashSet<>());
        PackageManager pm = getPackageManager();
        for (int i = 0; i < apps.size(); i++) {
            final int pos = i;
            final ResolveInfo ri = apps.get(i);
            final String pkg = ri.activityInfo.packageName;
            final boolean off = hidden.contains(pkg);
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setFocusable(true);
            r.setClickable(true);
            r.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(20), Ui.dp(10));
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(14), true));
            bg.addState(new int[] {}, Ui.glass(Ui.dp(14), false));
            r.setBackground(bg);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(ri.loadIcon(pm));
            icon.setAlpha(off ? 0.4f : 1f);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36));
            ilp.rightMargin = Ui.dp(14);
            r.addView(icon, ilp);
            TextView name = Ui.text(this, 16, off ? Ui.TEXT_DIM : Ui.TEXT, 600);
            name.setText((pos + 1) + ".  " + ri.loadLabel(pm));
            r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView state = Ui.text(this, 13, off ? Ui.TEXT_DIM : Ui.ACCENT, 600);
            state.setText(off ? "HIDDEN" : "ON HOME");
            r.addView(state);
            r.setOnClickListener(v -> {
                Set<String> h = new HashSet<>(getSharedPreferences("apps", MODE_PRIVATE).getStringSet("hidden", new HashSet<>()));
                if (!h.remove(pkg)) h.add(pkg);
                getSharedPreferences("apps", MODE_PRIVATE).edit().putStringSet("hidden", h).apply();
                build(pos);
            });
            r.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                int to = code == KeyEvent.KEYCODE_DPAD_LEFT ? pos - 1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? pos + 1 : -1;
                if ((code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) || to < 0 || to >= apps.size()) {
                    return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
                }
                Collections.swap(apps, pos, to);
                saveOrder();
                build(to);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(8);
            list.addView(r, lp);
        }
        int idx = 2 + Math.max(0, Math.min(focus, apps.size() - 1));
        if (list.getChildCount() > idx) list.getChildAt(idx).requestFocus();
    }

    private void saveOrder() {
        List<String> pkgs = new ArrayList<>();
        for (ResolveInfo ri : apps) pkgs.add(ri.activityInfo.packageName);
        getSharedPreferences("apps", MODE_PRIVATE).edit().putString("order", TextUtils.join(",", pkgs)).apply();
    }
}
