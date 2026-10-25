package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** The home screen: clock and weather, a shelf of apps, a Live TV row and quick actions. */
public class HomeActivity extends Activity {
    private TextView clock, date, weather, headline, quickLabel;
    private boolean resumedBefore;
    private final Runnable trimAfterReturn = () -> {
        Net.trim();
        System.gc();
        Runtime.getRuntime().runFinalization();
    };
    private List<World.Story> headlines = new ArrayList<>();
    private int headlineIndex;
    /** Rotates the top world headlines under the date, one every 9 seconds. */
    private final Runnable rotateHeadline = new Runnable() {
        @Override
        public void run() {
            if (!headlines.isEmpty()) {
                final World.Story s = headlines.get(headlineIndex++ % headlines.size());
                headline.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    headline.setText(s.title + "   ·   " + s.source);
                    headline.animate().alpha(1f).setDuration(350).start();
                }).start();
            }
            headline.postDelayed(this, 9000);
        }
    };
    private LinearLayout apps, live, quick, intel;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            if (Ui.is24h(HomeActivity.this)) {
                clock.setText(DateFormat.format("HH:mm", now));
            } else {
                String hm = DateFormat.format("h:mm", now).toString();
                SpannableString s = new SpannableString(hm + " " + DateFormat.format("a", now).toString().toUpperCase());
                s.setSpan(new RelativeSizeSpan(0.36f), hm.length(), s.length(), 0);
                clock.setText(s);
            }
            date.setText(DateFormat.format("EEEE d MMMM", now));
            clock.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Ui.Aurora());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        root.addView(scroll);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.dp(28), Ui.dp(30), Ui.dp(28), Ui.dp(30));
        scroll.addView(col);

        col.addView(header());
        col.addView(headline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(Ui.label(this, "Apps"));
        col.addView(shelf(apps = row()));
        col.addView(Ui.label(this, "Live TV"));
        col.addView(shelf(live = row()));
        col.addView(Ui.label(this, "Intelligence"));
        col.addView(shelf(intel = row()));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back on Home after the guide, player or World: let go of their pictures and buffers once Home has drawn.
        if (resumedBefore) clock.postDelayed(trimAfterReturn, 3000);
        resumedBefore = true;
        tick.run();
        Weather.load(this, t -> weather.setText(t));
        fillApps();
        fillLive();
        fillIntel();
        fillQuick();
        restoreFocus();
        headline.removeCallbacks(rotateHeadline);
        if (!headlines.isEmpty()) headline.postDelayed(rotateHeadline, 9000);
        loadHeadlines();
        if (getCurrentFocus() == null && apps.getChildCount() > 0) apps.getChildAt(0).requestFocus();
    }

    // Where focus was when Home was left, so Back returns to the same card (the rows are rebuilt on resume).
    private LinearLayout leftRow;
    private int leftIndex = -1, pendingLive = -1;

    private void restoreFocus() {
        LinearLayout row = leftRow;
        leftRow = null;
        if (row == null || leftIndex < 0) return;
        if (row == live && leftIndex >= sourceTiles) pendingLive = leftIndex; // channel tiles arrive a moment later
        int i = Math.min(row == live ? Math.min(leftIndex, sourceTiles - 1) : leftIndex, row.getChildCount() - 1);
        if (i >= 0) row.getChildAt(i).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        View f = getCurrentFocus();
        leftRow = null;
        for (LinearLayout row : new LinearLayout[] {apps, live, intel, quick}) {
            if (f != null && row != null && f.getParent() == row) {
                leftRow = row;
                leftIndex = row.indexOfChild(f);
            }
        }
        // The tiles' GPU textures are only worth their memory while Home is on screen (rebuilt on resume).
        Tile.release(apps);
        Tile.release(live);
        Tile.release(intel);
        Tile.release(quick);
        clock.removeCallbacks(tick);
        headline.removeCallbacks(rotateHeadline);
    }

    @Override
    public boolean onKeyDown(int code, android.view.KeyEvent e) {
        if (code == android.view.KeyEvent.KEYCODE_GUIDE) {
            startActivity(new Intent(this, ClassicGuideActivity.class));
            return true;
        }
        if (code == android.view.KeyEvent.KEYCODE_SEARCH) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private void loadHeadlines() {
        Net.IO.execute(() -> {
            if (World.stale(this)) World.refresh(this);
            List<World.Story> top = World.section(World.cached(this), "__top");
            final List<World.Story> pick = top.size() > 8 ? new ArrayList<>(top.subList(0, 8)) : top;
            Net.MAIN.post(() -> {
                boolean first = headlines.isEmpty();
                headlines = pick;
                if (first && !pick.isEmpty()) {
                    headline.removeCallbacks(rotateHeadline);
                    rotateHeadline.run();
                }
            });
        });
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    /** Clock and date on the left; weather above the quick-action pills on the right. */
    private View header() {
        FrameLayout h = new FrameLayout(this);
        h.setClipChildren(false);
        h.setPadding(Ui.dp(26), 0, Ui.dp(16), Ui.dp(6));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        clock = Ui.text(this, 54, Ui.TEXT, 300);
        clock.setShadowLayer(Ui.dp(18), 0, 0, 0x668FA8FF);
        date = Ui.text(this, 16, Ui.TEXT_DIM, 400);
        date.setPadding(Ui.dp(3), Ui.dp(6), 0, 0);
        left.addView(clock);
        left.addView(date);
        headline = Ui.text(this, 13, 0xCCB9C6FF, 400);
        headline.setSingleLine();
        headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headline.setPadding(Ui.dp(29), Ui.dp(4), Ui.dp(16), 0);
        // Too long for one line: scroll it sideways instead of cutting it off.
        headline.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        headline.setMarqueeRepeatLimit(1);
        headline.setHorizontalFadingEdgeEnabled(true);
        headline.setSelected(true);
        h.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));

        weather = Ui.text(this, 22, Ui.TEXT, 300);
        weather.setPadding(0, Ui.dp(4), Ui.dp(10), 0);
        h.addView(weather, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP));

        quick = new LinearLayout(this);
        quick.setClipChildren(false);
        FrameLayout.LayoutParams qlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        qlp.topMargin = Ui.dp(50);
        h.addView(quick, qlp);
        // The focused header button's name, so the icon-only row stays readable.
        quickLabel = Ui.text(this, 12, Ui.TEXT_DIM, 600);
        quickLabel.setLetterSpacing(0.08f);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        llp.topMargin = Ui.dp(104);
        llp.rightMargin = Ui.dp(12);
        h.addView(quickLabel, llp);
        return h;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(Ui.dp(18), Ui.dp(18), Ui.dp(40), Ui.dp(18));
        return r;
    }

    private View shelf(LinearLayout r) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.addView(r);
        return hs;
    }

    private void fillApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> ordered = AppsActivity.ordered(this, false); // Glow ▸ Settings ▸ Apps on Home

        apps.removeAllViews();
        for (final ResolveInfo ri : ordered) {
            Tile t = new Tile(this, 176, 99);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            if (banner != null) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageDrawable(banner);
                t.card.addView(iv);
            } else {
                t.card.addView(brandCard(ri.loadIcon(pm), ri.loadLabel(pm).toString()));
            }
            final Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            t.setOnClickListener(v -> startActivity(launch));
            t.setContentDescription(ri.loadLabel(pm));
            apps.addView(t);
        }
        final Intent store = pm.getLeanbackLaunchIntentForPackage("com.android.vending");
        if (store != null) {
            Tile more = actionTile("⬇", "Get more apps", "Google Play");
            more.setOnClickListener(v -> startActivity(store));
            apps.addView(more);
        }
    }

    /**
     * For apps without a TV banner (Netflix's phone build, for one): an adaptive icon's own background fills
     * the whole card with its logo large in the middle, like the banner apps. Other icons sit on a colour
     * taken from the icon, with the app's name.
     */
    private View brandCard(Drawable icon, String name) {
        if (icon instanceof android.graphics.drawable.AdaptiveIconDrawable) {
            android.graphics.drawable.AdaptiveIconDrawable a = (android.graphics.drawable.AdaptiveIconDrawable) icon;
            FrameLayout f = new FrameLayout(this);
            if (a.getBackground() != null) {
                View bg = new View(this);
                bg.setBackground(a.getBackground());
                f.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (a.getForeground() != null) {
                ImageView fg = new ImageView(this);
                fg.setImageDrawable(a.getForeground());
                fg.setScaleType(ImageView.ScaleType.FIT_CENTER);
                // The foreground layer is 108 units with the logo in the middle 72: show it at card height.
                f.addView(fg, new FrameLayout.LayoutParams(Ui.dp(132), Ui.dp(132), Gravity.CENTER));
            }
            return f;
        }
        View v = iconWithName(icon, name);
        v.setBackgroundColor(averageColour(icon));
        return v;
    }

    /** A dark version of an icon's average colour, for the card behind it. */
    private static int averageColour(Drawable d) {
        try {
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            d.setBounds(0, 0, 24, 24);
            d.draw(c);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                int px = b.getPixel(x, y);
                if ((px >>> 24) < 128) continue;
                r += (px >> 16) & 255; g += (px >> 8) & 255; bl += px & 255; n++;
            }
            b.recycle();
            if (n == 0) return 0x33FFFFFF;
            return 0xFF000000 | (int) (r / n * 0.45) << 16 | (int) (g / n * 0.45) << 8 | (int) (bl / n * 0.45);
        } catch (Exception e) {
            return 0x33FFFFFF;
        }
    }

    private View iconWithName(Drawable icon, String name) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        box.addView(iv, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        TextView tv = Ui.text(this, 13, Ui.TEXT, 600);
        tv.setText(name);
        tv.setSingleLine();
        tv.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), 0);
        box.addView(tv);
        return box;
    }

    private int sourceTiles;

    private void fillLive() {
        live.removeAllViews();
        // One way in: the TV Guide has every channel (free and My IPTV), radio, and the country lists under More.
        Tile guide = actionTile("▤", "TV Guide", "TV and radio, channel by channel");
        guide.setOnClickListener(v -> startActivity(new Intent(this, ClassicGuideActivity.class)));
        live.addView(guide);

        final Xtream.Account acct = Xtream.load(this);
        sourceTiles = live.getChildCount();
        showChannels();

        Net.IO.execute(() -> {
            if (FreeTv.stale(this)) {
                FreeTv.refresh(this);
                Net.MAIN.post(() -> { if (!isFinishing()) showChannels(); });
            }
            Health.scanIfDue(this);
            Guide.loadSaved(this);
            Picks.seedFavourites(this);
            boolean fresh = GuideLineup.current() == null;
            GuideLineup.build(this); // ready before the TV Guide is opened (kept 30 min)
            if (fresh) Net.MAIN.post(() -> { if (!isFinishing()) showChannels(); }); // tiles now show guide numbers
            Health.priorityIfDue(this);
            Updates.checkIfDue(this); // weekly: is there a newer Glow on GitHub Releases?
            Guide.ensure(this, null);
        });
        if (acct != null) {
            Net.IO.execute(() -> {
                try {
                    Xtream.channels(this, acct);
                } catch (Exception ignored) {
                    // Offline or expired: keep whatever was cached.
                }
            });
        }
    }

    /**
     * Recently watched from both sources; before anything's been watched, a few free Thai channels.
     * Lists can hold thousands of channels, so they're read off the main thread.
     */
    private void showChannels() {
        final boolean signedIn = Xtream.load(this) != null;
        Net.IO.execute(() -> {
            List<Channel> free = FreeTv.cached(this);
            List<Channel> all = new ArrayList<>(free);
            if (signedIn) all.addAll(Xtream.cached(this));
            List<Channel> pick = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            List<Channel> mine = new ArrayList<>(Picks.favourites(this, all));
            mine.addAll(Channel.recent(this, all));
            for (Channel ch : mine) if (!Channel.isOffline(this, ch) && seen.add(ch.key())) pick.add(ch);
            if (pick.isEmpty()) {
                for (Channel ch : Xtream.inCategory(free, FreeTv.LISTS[0][0])) {
                    if (!Channel.isOffline(this, ch)) pick.add(ch);
                    if (pick.size() == 10) break;
                }
            }
            Net.MAIN.post(() -> {
                if (!isFinishing()) renderChannels(pick);
            });
        });
    }

    private void renderChannels(final List<Channel> shown) {
        View focused = getCurrentFocus();
        boolean focusInRow = focused != null && focused.getParent() == live && live.indexOfChild(focused) >= sourceTiles;
        while (live.getChildCount() > sourceTiles) live.removeViewAt(sourceTiles);
        for (int i = 0; i < shown.size(); i++) {
            final int index = i;
            Tile t = channelTile(this, shown.get(i));
            if (Channel.isOffline(this, shown.get(i))) t.card.setAlpha(0.4f);
            t.setOnClickListener(v -> {
                Xtream.playlist = new ArrayList<>(shown);
                startActivity(new Intent(this, PlayerActivity.class).putExtra("index", index));
            });
            final Channel ch = shown.get(i);
            t.setOnLongClickListener(v -> {
                toastFavourite(this, ch, Picks.toggleFavourite(this, ch));
                fillChannel(this, t.card, ch);
                return true;
            });
            live.addView(t);
        }
        if (pendingLive >= 0 && live.getChildCount() > 0) {
            live.getChildAt(Math.min(pendingLive, live.getChildCount() - 1)).requestFocus();
            pendingLive = -1;
        } else if (focusInRow && live.getChildCount() > 0) {
            live.getChildAt(Math.min(sourceTiles, live.getChildCount() - 1)).requestFocus();
        }
    }

    static void toastFavourite(Activity a, Channel ch, boolean on) {
        android.widget.Toast.makeText(a, on ? "★  " + ch.name + " added to favourites" : ch.name + " removed from favourites",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    static Tile channelTile(Activity a, Channel ch) {
        Tile t = new Tile(a, 150, 99);
        fillChannel(a, t.card, ch);
        t.setContentDescription(ch.name);
        return t;
    }

    /** Logo (or initials when there's no logo) over the channel name. */
    static void fillChannel(Activity a, FrameLayout card, Channel ch) {
        card.removeAllViews();
        if (card.getParent() instanceof Tile) ((Tile) card.getParent()).setSolid(0); // until this logo says otherwise
        final FrameLayout art = new FrameLayout(a);
        FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        final Guide.Prog now = Guide.now(ch);
        artLp.bottomMargin = Ui.dp(now != null ? 40 : 26);
        card.addView(art, artLp);
        final ImageView logo = new ImageView(a);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(2));
        art.addView(logo);
        final TextView initials = Ui.badge(a, ch.name, 26);
        initials.setVisibility(View.GONE);
        art.addView(initials);
        TextView name = Ui.text(a, 12, Ui.TEXT, 400);
        int number = GuideLineup.numberOf(ch);
        String label = number > 0 ? number + "  " + ch.name : ch.name;
        name.setText(Picks.isFavourite(a, ch) ? ChannelRow.star(label) : label);
        name.setSingleLine();
        name.setGravity(Gravity.CENTER);
        name.setPadding(Ui.dp(8), 0, Ui.dp(8), Ui.dp(now != null ? 3 : 9));
        final LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(name);
        if (now != null) { // what's on right now, under the channel name
            TextView on = Ui.text(a, 10.5f, Ui.TEXT_DIM, 400);
            on.setText("▶ " + now.title);
            on.setSingleLine();
            on.setEllipsize(android.text.TextUtils.TruncateAt.END);
            on.setGravity(Gravity.CENTER);
            on.setPadding(Ui.dp(10), 0, Ui.dp(10), Ui.dp(8));
            words.addView(on);
        }
        logo.setTag(ch.logo);
        Net.bitmap(a, ch.logo, Ui.dp(120), bm -> {
            if (ch.logo == null || !ch.logo.equals(logo.getTag()) || logo.getParent() != art) return; // card reused
            if (bm == null) {
                initials.setVisibility(View.VISIBLE);
                return;
            }
            logo.setImageBitmap(bm);
            boolean dark = LogoTone.wantsDark(ch.logo, bm);
            if (card.getParent() instanceof Tile) ((Tile) card.getParent()).setSolid(dark ? 0xFF0E0F14 : 0xFFF6F7FA);
            for (int i = 0; i < words.getChildCount(); i++) {
                TextView tv = (TextView) words.getChildAt(i);
                tv.setTextColor(i == 0 ? (dark ? 0xFFFFFFFF : 0xFF14172A) : (dark ? 0xB3FFFFFF : 0x9914172A));
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        card.addView(words, lp);
    }

    private void fillQuick() {
        quick.removeAllViews();
        quickButton(Icons.SEARCH, "Search", Ui.ACCENT, v -> startActivity(new Intent(this, SearchActivity.class)));
        quickButton(Icons.WORLD, "World news", Ui.ACCENT, v -> startActivity(new Intent(this, WorldActivity.class)));
        quickButton(Icons.PHONE, "Phone remote", Ui.ACCENT, v -> startActivity(new Intent(this, RemoteActivity.class)));
        quickButton(Icons.IPTV, "My IPTV sign-in", Ui.ACCENT, v -> startActivity(new Intent(this, LoginActivity.class)));
        quickButton(Icons.SETTINGS, "Settings", Ui.ACCENT, v -> startActivity(new Intent(this, SettingsActivity.class)));
        // Developer access left on lets anyone on the Wi-Fi control the box: a red button that switches it off.
        if (SettingsActivity.adbOn(this)) {
            quickButton(Icons.SHIELD, "Developer access is ON · press to switch off", 0xFFFF8A7A, v -> {
                try {
                    android.provider.Settings.Global.putInt(getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0);
                    android.widget.Toast.makeText(this, "Developer access switched off", android.widget.Toast.LENGTH_SHORT).show();
                } catch (SecurityException e) {
                    startActivity(new Intent(this, SettingsActivity.class));
                }
                fillQuick();
            });
        }
    }

    /** Round icon-only button in the header; its name shows underneath while it has focus. */
    private void quickButton(int icon, final String name, int color, View.OnClickListener click) {
        Tile t = new Tile(this, 46, 46);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icons(icon, color));
        int pad = Ui.dp(12);
        iv.setPadding(pad, pad, pad, pad);
        t.card.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
        lp.setMargins(Ui.dp(6), 0, Ui.dp(6), 0);
        t.setContentDescription(name);
        t.setOnClickListener(click);
        t.setOnFocusChangeListener((v, has) -> {
            if (has) quickLabel.setText(name.toUpperCase(java.util.Locale.ROOT));
            else if (quickLabel.getText().toString().equalsIgnoreCase(name)) quickLabel.setText("");
        });
        quick.addView(t);
    }

    // ---- Intelligence: the world map, weather, prediction markets and one-click live cams ----

    /** Cams worth a place on Home (the rest are under Intelligence ▸ Live cams). */
    private static final String[] HOME_CAMS = {"london", "new-york", "tokyo", "iss-earth", "paris", "sydney"};
    private static Bitmap mapShot;
    private static long mapShotAt;
    private static String mapLine = "Earthquakes, disasters and news, live";

    private void fillIntel() {
        intel.removeAllViews();
        Tile map = new Tile(this, 176, 99);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(0xFF0B1226);
        if (mapShot != null) img.setImageBitmap(mapShot);
        map.card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(14), Ui.dp(16), Ui.dp(12), Ui.dp(9));
        words.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xE60B0E1C, 0x000B0E1C}));
        TextView a = Ui.text(this, 15, Ui.TEXT, 600);
        a.setText("World map");
        final TextView sub = Ui.text(this, 11, Ui.TEXT_DIM, 400);
        sub.setSingleLine();
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setText(mapLine);
        words.addView(a);
        words.addView(sub);
        map.card.addView(words, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        map.setContentDescription("World map");
        map.setOnClickListener(v -> openIntel(IntelActivity.MAP));
        intel.addView(map);

        final Tile weatherTile = actionTile("☀", "Weather", Weather.cityName(this));
        weatherTile.setOnClickListener(v -> openIntel(IntelActivity.WEATHER));
        intel.addView(weatherTile);
        final Tile odds = actionTile("%", "Predictions", "What the world expects");
        odds.setOnClickListener(v -> openIntel(IntelActivity.PREDICTIONS));
        intel.addView(odds);

        for (String slot : HOME_CAMS) {
            for (int i = 0; i < WmCams.CAMS.length; i++) {
                if (!WmCams.CAMS[i][0].equals(slot)) continue;
                final int idx = i;
                Tile cam = IntelActivity.camTile(this, 176, 99);
                IntelActivity.fillCam(this, cam, i);
                cam.setOnClickListener(v -> IntelActivity.openCam(this, idx));
                intel.addView(cam);
            }
        }
        Tile all = actionTile("◉", "All live cams", WmCams.CAMS.length + " places, live now");
        all.setOnClickListener(v -> openIntel(IntelActivity.CAMS));
        intel.addView(all);

        final int h = Ui.dp(99), w = h * 5 / 2; // the map's own shape; the card crops the far Pacific
        Net.IO.execute(() -> {
            Intel.Weather wx = Intel.weather(this);
            List<Intel.Market> m = Intel.markets(this);
            Bitmap shot = null;
            String line = null;
            if (mapShot == null || System.currentTimeMillis() - mapShotAt > 10 * 60_000L) {
                List<Intel.Event> ev = new ArrayList<>();
                List<Intel.Event> q = Intel.quakes(this), al = Intel.alerts(this), news = Intel.newsHotspots(this);
                ev.addAll(q);
                ev.addAll(al);
                ev.addAll(news);
                ev.addAll(Intel.natural(this));
                shot = WorldMapView.snapshot(this, ev, w, h);
                line = q.size() + " quakes · " + al.size() + " alerts · " + news.size() + " in the news";
            }
            final Bitmap fs = shot;
            final String fl = line;
            Net.MAIN.post(() -> {
                if (isFinishing()) return;
                if (fs != null) {
                    mapShot = fs;
                    mapShotAt = System.currentTimeMillis();
                    mapLine = fl;
                }
                if (mapShot != null) { // also when another refresh made it first
                    img.setImageBitmap(mapShot);
                    sub.setText(mapLine);
                }
                if (wx.code >= 0) {
                    ((TextView) ((LinearLayout) weatherTile.card.getChildAt(0)).getChildAt(0)).setText(Weather.symbol(wx.code));
                    ((TextView) ((LinearLayout) weatherTile.card.getChildAt(0)).getChildAt(1)).setText(Math.round(wx.temp) + "°  " + wx.city);
                    ((TextView) ((LinearLayout) weatherTile.card.getChildAt(0)).getChildAt(2)).setText(
                            "H " + Math.round(wx.max) + "°  L " + Math.round(wx.min) + "°" + (wx.aqi >= 0 ? "  ·  air " + Intel.aqiBand(wx.aqi)[0] : ""));
                }
                if (!m.isEmpty()) {
                    Intel.Market top = m.get(0);
                    TextView s = (TextView) ((LinearLayout) odds.card.getChildAt(0)).getChildAt(2);
                    s.setMaxLines(2);
                    s.setText(Math.round(top.yes * 100) + "%  " + top.question);
                }
            });
        });
    }

    private void openIntel(int section) {
        startActivity(new Intent(this, IntelActivity.class).putExtra(IntelActivity.EXTRA_SECTION, section));
    }

    private Tile actionTile(String glyph, String title, String sub) {
        Tile t = new Tile(this, 176, 99);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(16), 0, Ui.dp(12), 0);
        TextView g = Ui.text(this, 22, Ui.ACCENT, 300);
        g.setText(glyph);
        TextView a = Ui.text(this, 15, Ui.TEXT, 600);
        a.setText(title);
        a.setPadding(0, Ui.dp(8), 0, Ui.dp(3));
        TextView s = Ui.text(this, 11, Ui.TEXT_DIM, 400);
        s.setText(sub);
        box.addView(g);
        box.addView(a);
        box.addView(s);
        t.card.addView(box);
        t.setContentDescription(title);
        return t;
    }
}
