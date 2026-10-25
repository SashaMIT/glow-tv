package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
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
    private LinearLayout apps, live, quick;
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
        fillQuick();
        headline.removeCallbacks(rotateHeadline);
        if (!headlines.isEmpty()) headline.postDelayed(rotateHeadline, 9000);
        loadHeadlines();
        if (getCurrentFocus() == null && apps.getChildCount() > 0) apps.getChildAt(0).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        Tile guide = actionTile("▤", "TV Guide", "What's on, channel by channel");
        guide.setOnClickListener(v -> startActivity(new Intent(this, ClassicGuideActivity.class)));
        live.addView(guide);
        Tile free = actionTile("📡", "Free TV", "2,500+ free channels");
        free.setOnClickListener(v -> startActivity(new Intent(this, ChannelsActivity.class)
                .putExtra(ChannelsActivity.EXTRA_SOURCE, ChannelsActivity.SOURCE_FREE)));
        live.addView(free);

        final Xtream.Account acct = Xtream.load(this);
        if (acct != null) {
            Tile mine = actionTile("▦", "My IPTV", "Your provider's channels");
            mine.setOnClickListener(v -> startActivity(new Intent(this, ChannelsActivity.class)));
            live.addView(mine);
        }
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
        if (focusInRow && live.getChildCount() > 0) live.getChildAt(Math.min(sourceTiles, live.getChildCount() - 1)).requestFocus();
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
        Net.image(logo, ch.logo, Ui.dp(120), () -> initials.setVisibility(View.VISIBLE));

        TextView name = Ui.text(a, 12, Ui.TEXT, 400);
        int number = GuideLineup.numberOf(ch);
        String label = number > 0 ? number + "  " + ch.name : ch.name;
        name.setText(Picks.isFavourite(a, ch) ? ChannelRow.star(label) : label);
        name.setSingleLine();
        name.setGravity(Gravity.CENTER);
        name.setPadding(Ui.dp(8), 0, Ui.dp(8), Ui.dp(now != null ? 3 : 9));
        LinearLayout words = new LinearLayout(a);
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
