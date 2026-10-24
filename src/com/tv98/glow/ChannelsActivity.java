package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Categories on the left, a grid of channels on the right. */
public class ChannelsActivity extends Activity {
    static final String EXTRA_SOURCE = "source";
    static final String SOURCE_FREE = "free";

    private final List<Xtream.Category> categories = new ArrayList<>();
    private List<Channel> all = new ArrayList<>();
    private List<Channel> shown = new ArrayList<>();
    private ListView catList;
    private GridView grid;
    private TextView heading, empty;
    private Tile litTile;
    private CategoryAdapter catAdapter;
    private ChannelAdapter chAdapter;
    private final Runnable applyCategory = this::applyCategory;
    private boolean listMode;
    private String modeKey;
    private TextView viewToggle, offlineToggle, checking;
    private boolean hideOffline, mixedCountries;
    private final java.util.Set<Channel> pinned = new java.util.HashSet<>();
    private final java.util.Map<String, Integer> countryCounts = new java.util.HashMap<>();
    private static final String NEWS = "categories/news.m3u";
    private static final String HEADER = "__header";
    private final Runnable progress = new Runnable() {
        @Override
        public void run() {
            if (Health.running && Health.total > 0) {
                checking.setText("Checking which channels work from here…  " + Health.done.get() + " / " + Health.total);
                checking.postDelayed(this, 1500);
            } else {
                checking.setText("");
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        final boolean free = SOURCE_FREE.equals(getIntent().getStringExtra(EXTRA_SOURCE));
        final Xtream.Account acct = free ? null : Xtream.load(this);
        if (!free && acct == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setBackground(new Ui.Aurora());
        root.setPadding(Ui.dp(32), Ui.dp(28), Ui.dp(24), 0);
        root.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackground(Ui.glass(Ui.dp(18), false));
        left.setPadding(Ui.dp(10), Ui.dp(16), Ui.dp(10), Ui.dp(10));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(220), ViewGroup.LayoutParams.MATCH_PARENT);
        llp.bottomMargin = Ui.dp(28);
        root.addView(left, llp);

        TextView t = Ui.label(this, free ? "Free TV" : "My IPTV");
        t.setPadding(Ui.dp(12), 0, 0, Ui.dp(10));
        left.addView(t);

        catList = new ListView(this);
        catList.setDivider(null);
        catList.setSelector(Ui.glass(Ui.dp(10), true));
        catList.setVerticalScrollBarEnabled(false);
        catAdapter = new CategoryAdapter();
        catList.setAdapter(catAdapter);
        catList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                catList.removeCallbacks(applyCategory);
                catList.postDelayed(applyCategory, 220);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        catList.setOnItemClickListener((p, v, pos, id) -> grid.requestFocus());
        left.addView(catList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // List / grid switch, remembered separately for Free TV and My IPTV (MENU on the remote also flips it).
        modeKey = free ? "list_free" : "list_iptv";
        listMode = getSharedPreferences("channels", MODE_PRIVATE).getBoolean(modeKey, free);
        TextView search = pillButton();
        search.setText("⌕   Search channels");
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(Ui.dp(6), Ui.dp(6), Ui.dp(6), 0);
        left.addView(search, slp);

        viewToggle = pillButton();
        viewToggle.setOnClickListener(v -> toggleMode());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(Ui.dp(6), Ui.dp(5), Ui.dp(6), Ui.dp(2));
        left.addView(viewToggle, tlp);

        // Hide channels that failed the link check or wouldn't play (Free TV only).
        if (free) {
            hideOffline = getSharedPreferences("channels", MODE_PRIVATE).getBoolean("hide_offline", true);
            offlineToggle = pillButton();
            offlineToggle.setOnClickListener(v -> {
                hideOffline = !hideOffline;
                getSharedPreferences("channels", MODE_PRIVATE).edit().putBoolean("hide_offline", hideOffline).apply();
                applyCategory();
            });
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.setMargins(Ui.dp(6), Ui.dp(5), Ui.dp(6), Ui.dp(2));
            left.addView(offlineToggle, olp);
        }

        FrameLayout right = new FrameLayout(this);
        right.setClipChildren(false);
        root.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        heading = Ui.text(this, 24, Ui.TEXT, 300);
        heading.setPadding(Ui.dp(28), 0, 0, 0);
        right.addView(heading);

        checking = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        checking.setPadding(Ui.dp(30), Ui.dp(34), 0, 0);
        right.addView(checking);

        grid = new GridView(this);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setSelector(new ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        chAdapter = new ChannelAdapter();
        applyMode();
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                light(grid.hasFocus() ? (Tile) v : null);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { light(null); }
        });
        grid.setOnFocusChangeListener((v, has) -> light(has ? (Tile) grid.getSelectedView() : null));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            Channel ch = shown.get(pos);
            HomeActivity.toastFavourite(this, ch, Picks.toggleFavourite(this, ch));
            chAdapter.notifyDataSetChanged();
            return true;
        });
        grid.setOnItemClickListener((p, v, pos, id) -> {
            Xtream.playlist = new ArrayList<>(shown);
            startActivity(new Intent(this, PlayerActivity.class).putExtra("index", pos));
        });
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        glp.topMargin = Ui.dp(30);
        right.addView(grid, glp);

        empty = Ui.text(this, 15, Ui.TEXT_DIM, 400);
        empty.setGravity(Gravity.CENTER);
        empty.setText("Loading channels…");
        right.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        if (free) {
            checking.post(progress);
            Net.IO.execute(() -> {
                List<Channel> cached = FreeTv.cached(this);
                boolean stale = FreeTv.stale(this);
                final List<Channel> list = cached.isEmpty() ? FreeTv.refresh(this) : cached;
                Net.MAIN.post(() -> {
                    if (isFinishing()) return;
                    if (list.isEmpty()) empty.setText("Couldn't download the free channel lists. Check the internet connection.");
                    else show(FreeTv.categories(), list, false);
                });
                if (stale && !cached.isEmpty()) FreeTv.refresh(this); // newer lists for next time
                Health.scanIfDue(this);
                Net.MAIN.post(progress);
            });
            return;
        }

        Net.IO.execute(() -> {
            final List<Channel> cachedX = Xtream.cached(this);
            Net.MAIN.post(() -> all = cachedX);
            try {
                final List<Xtream.Category> cats = Xtream.categories(acct);
                final List<Channel> fresh = Xtream.channels(this, acct);
                Net.MAIN.post(() -> show(cats, fresh, true));
            } catch (Exception e) {
                Net.MAIN.post(() -> {
                    if (all.isEmpty()) empty.setText("Couldn't load channels. Check the login under IPTV account (top right of the home screen).");
                    else show(new ArrayList<>(), all, true);
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the player: refresh "offline" dimming and recents.
        if (chAdapter != null) chAdapter.notifyDataSetChanged();
    }

    private void show(List<Xtream.Category> cats, List<Channel> channels, boolean withAll) {
        all = channels;
        categories.clear();
        if (!Picks.favourites(this, all).isEmpty()) categories.add(new Xtream.Category("__favs", "★  Favourites"));
        if (!Channel.recent(this, all).isEmpty()) categories.add(new Xtream.Category("__recent", "Recently watched"));
        if (withAll) categories.add(new Xtream.Category(null, "All channels"));
        for (Xtream.Category c : cats) if (!Xtream.inCategory(all, c.id).isEmpty()) categories.add(c);
        if (offlineToggle != null) addNewsCountries();
        catAdapter.notifyDataSetChanged();
        catList.requestFocus();
        catList.setSelection(0);
        applyCategory();
    }

    /** "News by country": World news split by the country in each channel's id, busiest countries first. */
    private void addNewsCountries() {
        countryCounts.clear();
        for (Channel ch : all) {
            if (!NEWS.equals(ch.categoryId) || Channel.countryName(ch.country) == null || Channel.isOffline(this, ch)) continue;
            Integer n = countryCounts.get(ch.country);
            countryCounts.put(ch.country, n == null ? 1 : n + 1);
        }
        if (countryCounts.isEmpty()) return;
        List<String> codes = new ArrayList<>(countryCounts.keySet());
        java.util.Collections.sort(codes, (x, y) -> {
            int d = countryCounts.get(y) - countryCounts.get(x);
            return d != 0 ? d : Channel.countryName(x).compareTo(Channel.countryName(y));
        });
        categories.add(new Xtream.Category(HEADER, "News by country"));
        for (String code : codes) categories.add(new Xtream.Category("news:" + code, Channel.countryName(code)));
    }

    private List<Channel> channelsFor(Xtream.Category c) {
        if ("__recent".equals(c.id)) return Channel.recent(this, all);
        if ("__favs".equals(c.id)) return Picks.favourites(this, all);
        if (c.id != null && c.id.startsWith("news:")) {
            String code = c.id.substring(5);
            List<Channel> out = new ArrayList<>();
            for (Channel ch : all) if (NEWS.equals(ch.categoryId) && code.equals(ch.country)) out.add(ch);
            return out;
        }
        return Xtream.inCategory(all, c.id);
    }

    private void applyCategory() {
        int pos = Math.max(0, catList.getSelectedItemPosition());
        if (pos >= categories.size()) return;
        Xtream.Category c = categories.get(pos);
        if (HEADER.equals(c.id)) return;
        mixedCountries = c.id != null && (c.id.startsWith("__") || c.id.startsWith("categories/"));
        List<Channel> inCat = channelsFor(c);
        pinned.clear();
        if (c.id == null || !c.id.startsWith("__")) inCat = Picks.ordered(this, c.id, inCat, pinned);
        shown = inCat;
        if (offlineToggle != null) {
            List<Channel> working = new ArrayList<>();
            for (Channel ch : inCat) if (!Channel.isOffline(this, ch)) working.add(ch);
            if (hideOffline) shown = working;
            heading.setText(c.name + "   ·   " + working.size() + " working" + (hideOffline ? "" : " of " + inCat.size()));
            offlineToggle.setText(hideOffline ? "◐   Show offline too" : "◑   Hide offline");
        } else {
            heading.setText(c.name + "   ·   " + shown.size());
        }
        empty.setText(shown.isEmpty() ? "No channels here." : "");
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < Math.min(30, shown.size()); i++) {
            Guide.wantThai(this, shown.get(i), () -> chAdapter.notifyDataSetChanged());
        }
        litTile = null;
        chAdapter.notifyDataSetChanged();
        if (!shown.isEmpty()) grid.setSelection(0);
    }

    private TextView pillButton() {
        TextView b = Ui.text(this, 13, Ui.TEXT, 600);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
        android.graphics.drawable.StateListDrawable pill = new android.graphics.drawable.StateListDrawable();
        pill.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(20), true));
        pill.addState(new int[] {}, Ui.glass(Ui.dp(20), false));
        b.setBackground(pill);
        return b;
    }

    private void toggleMode() {
        listMode = !listMode;
        getSharedPreferences("channels", MODE_PRIVATE).edit().putBoolean(modeKey, listMode).apply();
        applyMode();
    }

    /** One wide column of rows, or a grid of cards. Re-setting the adapter drops recycled views of the other kind. */
    private void applyMode() {
        int keep = Math.max(0, grid.getSelectedItemPosition());
        litTile = null;
        if (listMode) {
            grid.setNumColumns(1);
            grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setVerticalSpacing(Ui.dp(8));
            grid.setPadding(Ui.dp(26), Ui.dp(18), Ui.dp(30), Ui.dp(40));
        } else {
            grid.setNumColumns(GridView.AUTO_FIT);
            grid.setColumnWidth(Ui.dp(150));
            grid.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);
            grid.setVerticalSpacing(Ui.dp(20));
            grid.setHorizontalSpacing(Ui.dp(14));
            grid.setPadding(Ui.dp(22), Ui.dp(22), Ui.dp(22), Ui.dp(40));
        }
        grid.setAdapter(chAdapter);
        if (!shown.isEmpty()) grid.setSelection(Math.min(keep, shown.size() - 1));
        viewToggle.setText(listMode ? "▦   Grid view" : "☰   List view");
    }

    @Override
    public boolean onKeyDown(int code, android.view.KeyEvent e) {
        if (code == android.view.KeyEvent.KEYCODE_MENU) {
            toggleMode();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private void light(Tile t) {
        if (litTile == t) return;
        if (litTile != null) litTile.setLit(false);
        litTile = t;
        if (t != null) t.setLit(true);
    }

    private final class CategoryAdapter extends BaseAdapter {
        @Override public int getCount() { return categories.size(); }
        @Override public Object getItem(int i) { return categories.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int i) { return !HEADER.equals(categories.get(i).id); }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return isEnabled(i) ? 0 : 1; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Xtream.Category c = categories.get(i);
            if (!isEnabled(i)) {
                TextView h = convert instanceof TextView ? (TextView) convert : Ui.label(ChannelsActivity.this, c.name);
                h.setPadding(Ui.dp(14), Ui.dp(22), 0, Ui.dp(6));
                return h;
            }
            TextView tv = convert instanceof TextView ? (TextView) convert : Ui.text(ChannelsActivity.this, 15, Ui.TEXT, 400);
            Integer n = c.id != null && c.id.startsWith("news:") ? countryCounts.get(c.id.substring(5)) : null;
            tv.setText(n == null ? c.name : c.name + "  (" + n + ")");
            tv.setSingleLine();
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(10), Ui.dp(11));
            return tv;
        }
    }

    private final class ChannelAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return shown.get(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            if (convert == litTile && convert != null) light(null);
            Channel ch = shown.get(i);
            boolean offline = Channel.isOffline(ChannelsActivity.this, ch);
            if (listMode) {
                return ChannelRow.of(ChannelsActivity.this, convert).bind(ch, offline, mixedCountries, pinned.contains(ch));
            }
            Tile t;
            if (convert instanceof Tile && ((Tile) convert).getTag() == null) {
                t = (Tile) convert;
            } else {
                t = new Tile(ChannelsActivity.this, 150, 99);
                t.setFocusable(false);
                t.setClickable(false);
                t.setLayoutParams(new AbsListView.LayoutParams(Ui.dp(150), Ui.dp(99)));
            }
            HomeActivity.fillChannel(ChannelsActivity.this, t.card, ch);
            t.card.setAlpha(offline ? 0.4f : 1f);
            return t;
        }
    }
}
