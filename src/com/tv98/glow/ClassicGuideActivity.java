package com.tv98.glow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * "TV Guide": the classic blue satellite-box guide. A numbered menu of sections, then the channel × time grid
 * with an info panel and a small muted live preview of the highlighted channel.
 */
public class ClassicGuideActivity extends Activity implements GuideGridView.Listener {
    static final String EXTRA_CHANNEL_KEY = "channel";
    /** A colour key pressed on the phone remote while the guide wasn't open (KEYCODE_PROG_*). */
    static final String EXTRA_COLOUR = "colour";
    private static final int PREVIEW_DWELL_MS = 1200;

    private GuideLineup lineup;
    private GuideLineup.Section section;
    private LinearLayout menuScreen, menuList, tabs, gridScreen;
    private TextView menuClock, menuTitle;
    private boolean inMore;
    // Radio, inside the guide: the RADIO tab shows its numbered menu, then a station list in the guide's style.
    private RadioListView radioList;
    private android.widget.ImageView radioArt;
    private TextView legend;
    private boolean radioMode;
    /** ▲ from the top of the menu goes to the tab you're in, not whichever tab sits above it. */
    private int currentTabId = View.NO_ID;
    private int radioSection = -1;
    private ExoPlayer radioExo;
    private Radio.Station radioPlaying;
    private String radioSong;
    private int radioGen;
    private FrameLayout radioDim;
    private TextView radioDimClock, radioDimName, radioDimSong;
    private static final long RADIO_DIM_MS = 60_000;
    private final Runnable radioDimNow = this::showRadioDim;
    private GuideGridView grid;
    private android.widget.ImageView infoLogo;
    private String infoLogoUrl;
    private TextView infoDesc;
    private TextView heading, infoTime, infoTitle, infoChannel, infoNext, previewNote, clock;
    private FrameLayout preview;
    private SurfaceView previewSurface;
    private ExoPlayer exo;
    private Channel previewing, wanted;
    private List<Sources.Attempt> previewAttempts = new ArrayList<>();
    private int previewAttempt;
    private long openedAt;
    private final Guide.Listener guideChanged = () -> Net.MAIN.post(this::refresh);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        openedAt = SystemClock.elapsedRealtime();
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {GuideGridView.SKY_TOP, 0xFF4A86D8, GuideGridView.SKY_BOTTOM}));

        LinearLayout col = gridScreen = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(40), Ui.dp(22), Ui.dp(40), Ui.dp(14));
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top: info panel on the left, live preview on the right.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(186)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        heading = bold(28);
        heading.setText("TV Guide");
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        clock = bold(20);
        clock.setGravity(Gravity.END);
        titleRow.addView(clock, new LinearLayout.LayoutParams(Ui.dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        clock.setPadding(0, 0, Ui.dp(24), 0);
        info.addView(titleRow);
        infoTime = plain(17, 0xFFFFF3B0);
        infoTime.setPadding(0, 0, 0, Ui.dp(2));
        infoTitle = bold(24);
        infoTitle.setSingleLine();
        infoTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        infoTitle.setPadding(0, 0, Ui.dp(24), 0);
        infoChannel = plain(16, 0xFFFFFFFF);
        infoChannel.setPadding(0, Ui.dp(6), 0, 0);
        infoNext = plain(15, 0xFFDCE7FF);
        infoNext.setSingleLine();
        infoNext.setEllipsize(android.text.TextUtils.TruncateAt.END);
        infoNext.setPadding(0, Ui.dp(6), Ui.dp(24), 0);
        // Single-line, full-width text: changing it on every key press redraws without re-measuring the screen.
        infoTime.setSingleLine();
        infoChannel.setSingleLine();
        infoChannel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // Channel logo beside the programme details, like the old guides' info banner.
        LinearLayout details = new LinearLayout(this);
        details.setGravity(Gravity.CENTER_VERTICAL);
        infoLogo = new android.widget.ImageView(this);
        infoLogo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        // Just the logo on the guide's blue, vertically centred on the text block beside it.
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(108), Ui.dp(68));
        ilp.rightMargin = Ui.dp(20);
        ilp.gravity = Gravity.CENTER_VERTICAL;
        details.addView(infoLogo, ilp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(infoTime);
        texts.addView(infoTitle);
        texts.addView(infoChannel);
        infoDesc = plain(14, 0xFFEAF1FF);
        infoDesc.setSingleLine();
        infoDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        infoDesc.setPadding(0, Ui.dp(5), Ui.dp(24), 0);
        infoDesc.setVisibility(View.GONE);
        texts.addView(infoDesc);
        texts.addView(infoNext);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.gravity = Gravity.CENTER_VERTICAL;
        details.addView(texts, tlp);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        info.addView(details, dlp);

        preview = new FrameLayout(this);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(0xFF000000);
        frame.setStroke(Ui.dp(2), 0xFFFFFFFF);
        preview.setBackground(frame);
        preview.setPadding(Ui.dp(2), Ui.dp(2), Ui.dp(2), Ui.dp(2));
        previewSurface = new SurfaceView(this);
        preview.addView(previewSurface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        previewNote = plain(13, 0xFFDCE7FF);
        previewNote.setGravity(Gravity.CENTER);
        previewNote.setBackgroundColor(0xFF0B1E4F); // covers the last frame whenever there's no live picture
        previewNote.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        preview.addView(previewNote, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        radioArt = new android.widget.ImageView(this);
        radioArt.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        radioArt.setBackgroundColor(0xFF0B1E4F);
        radioArt.setPadding(Ui.dp(40), Ui.dp(24), Ui.dp(40), Ui.dp(24));
        radioArt.setVisibility(View.GONE);
        preview.addView(radioArt, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        top.addView(preview, new LinearLayout.LayoutParams(Ui.dp(320), Ui.dp(180)));

        // Middle: the menu or the grid (one at a time).
        FrameLayout middle = new FrameLayout(this);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        mlp.topMargin = Ui.dp(10);
        col.addView(middle, mlp);
        grid = new GuideGridView(this);
        grid.setListener(this);
        grid.setNowNext(getSharedPreferences("display", MODE_PRIVATE).getBoolean("now_next", false));
        middle.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        radioList = new RadioListView(this);
        radioList.setVisibility(View.GONE);
        radioList.setListener(new RadioListView.Listener() {
            @Override public void onSelect(Radio.Station st, int number) { showRadioInfo(st, number); }
            @Override public void onPlay(Radio.Station st, int number) { toggleRadio(st, number); }
        });
        middle.addView(radioList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Bottom: the key legend, like the coloured-button bar of the old guides.
        legend = plain(13, 0xFFFFFFFF);
        legend.setText(guideLegend());
        legend.setGravity(Gravity.CENTER);
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(0x55102A6E);
        lb.setCornerRadius(Ui.dp(4));
        legend.setBackground(lb);
        legend.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(8);
        col.addView(legend, llp);

        gridScreen.setVisibility(View.GONE);
        buildMenuScreen(root);
        buildRadioDim(root);
        setContentView(root);
        menuTitle.setText("Loading the guide…");

        final String startKey = getIntent().getStringExtra(EXTRA_CHANNEL_KEY);
        Net.IO.execute(() -> {
            long t0 = SystemClock.elapsedRealtime();
            final GuideLineup l = GuideLineup.build(this);
            Log.i("GlowPerf", "guide lineup: " + l.numbers.size() + " numbered channels in " + (SystemClock.elapsedRealtime() - t0) + " ms");
            Net.MAIN.post(() -> {
                if (isFinishing()) return;
                lineup = l;
                if (startKey != null) {
                    openSection(l.main.get(0), startKey);
                    if (section == null) showMenu(false, 0);
                } else {
                    showMenu(false, 0);
                    int colour = getIntent().getIntExtra(EXTRA_COLOUR, 0);
                    if (colour != 0) colourKey(colour);
                }
                Log.i("GlowPerf", "guide shown " + (SystemClock.elapsedRealtime() - openedAt) + " ms after open");
            });
            Guide.ensure(this, guideChanged); // loads the saved guide at once; refreshes UK/US in the background if a day old
        });
    }

    private TextView bold(float sp) {
        TextView t = Ui.text(this, sp, 0xFFFFFFFF, 600);
        t.setShadowLayer(Ui.dp(2), 0, Ui.dp(1), 0x66000000);
        return t;
    }

    private TextView plain(float sp, int color) {
        TextView t = Ui.text(this, sp, color, 400);
        t.setShadowLayer(Ui.dp(2), 0, Ui.dp(1), 0x55000000);
        return t;
    }

    // ---- menu (the classic numbered list with tabs across the top) ----

    private static final int NUM_BG = 0xFF3A66CC, NUM_BG_ON = 0xFFE3A81B, NUM_TEXT = 0xFFC4D4F6;
    private static final int TAB_BLUE = 0xFF2350C0;

    private void buildMenuScreen(FrameLayout root) {
        menuScreen = new LinearLayout(this);
        menuScreen.setOrientation(LinearLayout.VERTICAL);
        menuScreen.setPadding(Ui.dp(44), Ui.dp(16), Ui.dp(44), Ui.dp(12));
        root.addView(menuScreen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Header: logo, then the tabs (TV GUIDE is where you are; the others open Search, World news, Settings).
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout logo = new LinearLayout(this);
        logo.setOrientation(LinearLayout.VERTICAL);
        TextView glow = Ui.text(this, 34, 0xFFFFFFFF, 600);
        glow.setText("glow");
        glow.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD_ITALIC));
        glow.setShadowLayer(Ui.dp(3), 0, Ui.dp(1), 0x66000000);
        TextView guide = plain(18, 0xFFFFFFFF);
        guide.setText("guide");
        guide.setPadding(Ui.dp(28), Ui.dp(2), 0, 0);
        logo.addView(glow);
        logo.addView(guide);
        head.addView(logo, new LinearLayout.LayoutParams(Ui.dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        tabs = new LinearLayout(this);
        buildTabs();
        head.addView(tabs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        menuClock = bold(22);
        head.addView(menuClock);
        menuScreen.addView(head);

        menuTitle = bold(15);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(640), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        tlp.topMargin = Ui.dp(14);
        menuScreen.addView(menuTitle, tlp);

        menuList = new LinearLayout(this);
        menuList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(640), ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        mlp.topMargin = Ui.dp(6);
        menuScreen.addView(menuList, mlp);

        // Colour keys, as on the old remotes (on the phone remote too).
        LinearLayout keys = new LinearLayout(this);
        keys.setGravity(Gravity.CENTER);
        keys.addView(colourKey(0xFFE0312B, "Recently watched"));
        keys.addView(colourKey(0xFF39B54A, "Search A–Z"));
        keys.addView(colourKey(0xFFF5D33C, "World news"));
        keys.addView(colourKey(0xFF1E4FD6, "Favourites"));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.topMargin = Ui.dp(8);
        menuScreen.addView(keys, klp);
    }

    /** TV GUIDE and RADIO switch the menu in place; the others open their screens. */
    private void buildTabs() {
        tabs.removeAllViews();
        View tv = tab("▤", "TV GUIDE", !radioMode, () -> { if (lineup != null) showMenu(false, 0); });
        View radio = tab("♫", "RADIO", radioMode, () -> showRadioMenu(0));
        tv.setId(View.generateViewId());
        radio.setId(View.generateViewId());
        currentTabId = radioMode ? radio.getId() : tv.getId();
        tabs.addView(tv);
        // Now & Next: rows show what's on and what's next instead of the half-hour grid (remembered).
        final boolean nn = getSharedPreferences("display", MODE_PRIVATE).getBoolean("now_next", false);
        View nowNextTab = tab("≡", nn ? "NOW & NEXT ✓" : "NOW & NEXT", false, () -> {
            boolean on = !getSharedPreferences("display", MODE_PRIVATE).getBoolean("now_next", false);
            getSharedPreferences("display", MODE_PRIVATE).edit().putBoolean("now_next", on).apply();
            grid.setNowNext(on);
            buildTabs();
            if (lineup != null) showMenu(false, 0);
        });
        tabs.addView(nowNextTab);
        tabs.addView(radio);
        tabs.addView(tab("⌕", "SEARCH", false, () -> startActivity(new Intent(this, SearchActivity.class))));
        tabs.addView(tab("◍", "WORLD NEWS", false, () -> startActivity(new Intent(this, WorldActivity.class))));
        tabs.addView(tab("⚙", "SETTINGS", false, () -> startActivity(new Intent(this, SettingsActivity.class))));
    }

    private View tab(String glyph, String label, boolean current, final Runnable action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        TextView icon = Ui.text(this, 26, current ? 0xFFFFFFFF : TAB_BLUE, 400);
        icon.setText(glyph);
        icon.setGravity(Gravity.CENTER);
        icon.setDuplicateParentStateEnabled(true);
        StateListDrawable box = new StateListDrawable();
        box.addState(new int[] {android.R.attr.state_focused}, tabBox(current, true));
        box.addState(new int[] {}, tabBox(current, false));
        icon.setBackground(box);
        t.addView(icon, new LinearLayout.LayoutParams(Ui.dp(56), Ui.dp(40)));
        TextView name = Ui.text(this, 15, 0xFFFFFFFF, 600);
        name.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        name.setText(label);
        name.setSingleLine();
        name.setPadding(0, Ui.dp(5), 0, 0);
        name.setDuplicateParentStateEnabled(true);
        name.setTextColor(new android.content.res.ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}}, new int[] {GuideGridView.HILITE, 0xFFFFFFFF}));
        name.setShadowLayer(Ui.dp(2), 0, Ui.dp(1), 0x66000000);
        t.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private GradientDrawable tabBox(boolean current, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.dp(6));
        g.setColor(current ? TAB_BLUE : 0xFFF4F8FF);
        if (focused) g.setStroke(Ui.dp(3), GuideGridView.HILITE);
        else if (current) g.setStroke(Ui.dp(2), 0xFFFFFFFF);
        return g;
    }

    private View colourKey(int colour, String label) {
        LinearLayout k = new LinearLayout(this);
        k.setGravity(Gravity.CENTER_VERTICAL);
        k.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        View sq = new View(this);
        sq.setBackgroundColor(colour);
        k.addView(sq, new LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16)));
        TextView t = bold(16);
        t.setText(label);
        t.setPadding(Ui.dp(8), 0, 0, 0);
        k.addView(t);
        return k;
    }

    /** Shows the main menu (more = false) or the "More…" list, focusing row {@code focus}. */
    private void showMenu(boolean more, int focus) {
        section = null;
        inMore = more;
        if (radioMode) {
            radioMode = false;
            radioSection = -1;
            buildTabs();
        }
        gridScreen.setVisibility(View.GONE);
        menuScreen.setVisibility(View.VISIBLE);
        stopPreview("");
        menuClock.setText(DateFormat.format(Ui.is24h(this) ? "HH:mm" : "h:mm a", System.currentTimeMillis()));
        menuTitle.setText(more ? "TV GUIDE  ›  MORE" : DateFormat.format("EEEE d MMMM", System.currentTimeMillis()).toString().toUpperCase());
        menuList.removeAllViews();
        lineup.refreshPersonal(this); // favourites or recently watched may have changed since the lineup was built
        List<GuideLineup.Section> list = more ? lineup.more : lineup.main;
        for (int i = 0; i < list.size() && i < 9; i++) {
            final GuideLineup.Section s = list.get(i);
            menuList.addView(menuRow(String.valueOf(i + 1), s.name, s.channels.size(), v -> openSection(s, null)));
        }
        if (!more) menuList.addView(menuRow("0", "More…", -1, v -> showMenu(true, 0)));
        if (menuList.getChildCount() > 0) menuList.getChildAt(0).setNextFocusUpId(currentTabId);
        menuList.getChildAt(Math.max(0, Math.min(focus, menuList.getChildCount() - 1))).requestFocus();
    }

    private View menuRow(String num, String label, int count, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(click);
        android.content.res.ColorStateList onOff = new android.content.res.ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}}, new int[] {GuideGridView.HILITE_TEXT, 0xFFFFFFFF});

        TextView n = Ui.text(this, 18, NUM_TEXT, 600);
        n.setText(num);
        n.setGravity(Gravity.CENTER);
        n.setDuplicateParentStateEnabled(true);
        n.setTextColor(new android.content.res.ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {GuideGridView.HILITE_TEXT, NUM_TEXT}));
        n.setBackground(states(flat(NUM_BG_ON), flat(NUM_BG)));
        row.addView(n, new LinearLayout.LayoutParams(Ui.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout cell = new LinearLayout(this);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setDuplicateParentStateEnabled(true);
        GradientDrawable off = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {GuideGridView.CELL_EDGE, GuideGridView.CELL, GuideGridView.CELL});
        cell.setBackground(states(flat(GuideGridView.HILITE), off));
        cell.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
        TextView t = Ui.text(this, 17, 0xFFFFFFFF, 600);
        t.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        t.setText(label.toUpperCase(java.util.Locale.ROOT));
        t.setLetterSpacing(0.03f);
        t.setDuplicateParentStateEnabled(true);
        t.setTextColor(onOff);
        cell.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (count >= 0) {
            TextView c = Ui.text(this, 14, 0xFFFFFFFF, 400);
            c.setText(count == 0 ? "empty" : count + " channels");
            c.setDuplicateParentStateEnabled(true);
            c.setTextColor(new android.content.res.ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                    new int[] {GuideGridView.HILITE_TEXT, 0xFFAFC3EE}));
            cell.addView(c);
        }
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        clp.leftMargin = Ui.dp(3);
        row.addView(cell, clp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(29));
        lp.bottomMargin = Ui.dp(3);
        row.setLayoutParams(lp);
        return row;
    }

    private static GradientDrawable flat(int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        return g;
    }

    private static StateListDrawable states(android.graphics.drawable.Drawable on, android.graphics.drawable.Drawable off) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, on);
        s.addState(new int[] {}, off);
        return s;
    }

    private void openSection(GuideLineup.Section s, String selectKey) {
        if (s.channels.isEmpty()) {
            android.widget.Toast.makeText(this, s.name.equals("Favourites") ? "No favourites yet: highlight a channel in the guide and press the blue key"
                    : s.name.equals("Recently watched") ? "Nothing watched yet" : "No working channels here right now",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        section = s;
        heading.setText("TV Guide  ·  " + s.name);
        menuScreen.setVisibility(View.GONE);
        gridScreen.setVisibility(View.VISIBLE);
        showTvParts();
        int select = 0;
        if (selectKey != null) {
            for (int i = 0; i < s.channels.size(); i++) if (s.channels.get(i).key().equals(selectKey)) select = i;
        }
        grid.setRows(s.channels, lineup, select);
        grid.requestFocus();
    }

    private GuideLineup.Section named(String name) {
        for (GuideLineup.Section s : lineup.main) if (s.name.equals(name)) return s;
        for (GuideLineup.Section s : lineup.more) if (s.name.equals(name)) return s;
        return null;
    }

    /** Red / green / yellow / blue, anywhere in the guide. Blue on a highlighted channel stars it. */
    /**
     * Colour buttons from the phone remote. They're handled here rather than injected as key presses because
     * this box's firmware also opens its file manager on the blue key.
     */
    static void colourFromPhone(android.content.Context app, int code) {
        Activity a = GlowApp.front();
        if (a instanceof ClassicGuideActivity && ((ClassicGuideActivity) a).lineup != null) {
            ((ClassicGuideActivity) a).colourKey(code);
            return;
        }
        Class<?> target = code == KeyEvent.KEYCODE_PROG_GREEN ? SearchActivity.class
                : code == KeyEvent.KEYCODE_PROG_YELLOW ? WorldActivity.class : ClassicGuideActivity.class;
        app.startActivity(new Intent(app, target).putExtra(EXTRA_COLOUR, code).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private boolean colourKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_PROG_RED:
                GuideLineup.Section recent = named("Recently watched");
                if (recent != null) openSection(recent, null);
                return true;
            case KeyEvent.KEYCODE_PROG_GREEN:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case KeyEvent.KEYCODE_PROG_YELLOW:
                startActivity(new Intent(this, WorldActivity.class));
                return true;
            case KeyEvent.KEYCODE_PROG_BLUE:
                if (section != null) {
                    Channel ch = section.channels.get(grid.selectedIndex());
                    HomeActivity.toastFavourite(this, ch, Picks.toggleFavourite(this, ch));
                    lineup.refreshPersonal(this);
                } else {
                    openSection(named("Favourites"), null);
                }
                return true;
            default:
                return false;
        }
    }

    // ---- radio ----

    private void showTvParts() {
        radioList.setVisibility(View.GONE);
        radioArt.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        legend.setText(guideLegend());
    }

    /** The RADIO tab: the same numbered menu, listing radio sections (1–9, 0). */
    private void showRadioMenu(int focus) {
        section = null;
        inMore = false;
        radioSection = -1;
        if (!radioMode) {
            radioMode = true;
            buildTabs();
        }
        gridScreen.setVisibility(View.GONE);
        menuScreen.setVisibility(View.VISIBLE);
        stopPreview("");
        menuTitle.setText("RADIO   ·   FREE STATIONS FROM AROUND THE WORLD" + (radioPlaying != null ? "   ·   ♫ " + radioPlaying.name : ""));
        menuList.removeAllViews();
        for (int i = 0; i < Radio.SECTIONS.length; i++) {
            final int idx = i;
            menuList.addView(menuRow(String.valueOf((i + 1) % 10), Radio.SECTIONS[i][0], -1, v -> openRadio(idx)));
        }
        menuList.getChildAt(0).setNextFocusUpId(currentTabId);
        menuList.getChildAt(Math.max(0, Math.min(focus, menuList.getChildCount() - 1))).requestFocus();
    }

    /** A radio section as a guide-style list; stations numbered 0101… for section 1, 0201… for section 2. */
    private void openRadio(final int idx) {
        radioSection = idx;
        menuScreen.setVisibility(View.GONE);
        gridScreen.setVisibility(View.VISIBLE);
        grid.setVisibility(View.GONE);
        radioList.setVisibility(View.VISIBLE);
        stopPreview("");
        previewNote.setVisibility(View.GONE);
        radioArt.setVisibility(View.VISIBLE);
        heading.setText("Radio  ·  " + Radio.SECTIONS[idx][0]);
        legend.setText("▲▼ Stations     CH± Page     0–9 Station number     OK Listen / stop     BACK Radio menu");
        setTime("RADIO");
        infoTitle.setText("Loading stations…");
        infoChannel.setText("");
        infoDesc.setVisibility(View.GONE);
        infoNext.setText("");
        final int gen = ++radioGen;
        Net.IO.execute(() -> {
            final List<Radio.Station> list = Radio.section(this, idx);
            Net.MAIN.post(() -> {
                if (gen != radioGen || isFinishing() || radioSection != idx) return;
                if (list.isEmpty()) {
                    infoTitle.setText("Couldn't load stations");
                    infoChannel.setText("Check the internet connection and try again.");
                    return;
                }
                radioList.setStations(list, (idx + 1) * 100);
                radioList.setPlaying(radioPlaying == null ? null : radioPlaying.uuid, radioSong);
                radioList.requestFocus();
            });
        });
    }

    private void showRadioInfo(Radio.Station st, int number) {
        boolean on = radioPlaying != null && radioPlaying.uuid.equals(st.uuid);
        setTime("RADIO  ·  " + RadioListView.label(number) + (on ? "   ·   ♫ ON AIR" : ""));
        infoTitle.setText(st.name);
        infoChannel.setText(Radio.describe(st));
        boolean song = on && radioSong != null && !radioSong.isEmpty();
        infoDesc.setText(song ? radioSong : "");
        infoDesc.setVisibility(song ? View.VISIBLE : View.GONE);
        infoNext.setText(on ? "OK to stop" : "OK to listen" + (radioPlaying != null ? "   ·   now playing: " + radioPlaying.name : ""));
        if (st.logo == null || st.logo.isEmpty()) {
            infoLogoUrl = null;
            infoLogo.setImageDrawable(new SourceArt(Ui.initials(st.name), Ui.ACCENT, Ui.dp(10)));
            infoLogo.setVisibility(View.VISIBLE);
        } else if (!st.logo.equals(infoLogoUrl)) {
            infoLogoUrl = st.logo;
            infoLogo.setVisibility(View.VISIBLE);
            final String want = st.logo;
            infoLogo.setImageDrawable(null);
            Net.bitmap(this, want, Ui.dp(240), bm -> {
                if (!want.equals(infoLogoUrl)) return;
                if (bm == null) infoLogo.setImageDrawable(new SourceArt(Ui.initials(st.name), Ui.ACCENT, Ui.dp(10)));
                else infoLogo.setImageBitmap(GuideGridView.trimmed(want, bm));
            });
        }
    }

    private void toggleRadio(Radio.Station st, int number) {
        if (radioPlaying != null && radioPlaying.uuid.equals(st.uuid)) {
            stopRadio();
        } else {
            radioPlaying = st;
            radioSong = null;
            if (radioExo == null) {
                radioExo = new ExoPlayer.Builder(this).build();
                radioExo.addListener(new Player.Listener() {
                    @Override
                    public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata m) {
                        CharSequence t = m.title != null ? m.title : m.displayTitle;
                        if (t == null || t.length() == 0 || radioPlaying == null) return;
                        radioSong = t.toString();
                        radioList.setPlaying(radioPlaying.uuid, radioSong);
                        radioDimSong.setText(radioSong);
                        if (radioSection >= 0) radioList.post(() -> radioList.dispatchSelect());
                    }

                    @Override
                    public void onPlayerError(PlaybackException e) {
                        android.widget.Toast.makeText(ClassicGuideActivity.this, "That station isn't available right now",
                                android.widget.Toast.LENGTH_SHORT).show();
                        stopRadio();
                    }
                });
            }
            radioExo.setMediaItem(androidx.media3.common.MediaItem.fromUri(st.url));
            radioExo.prepare();
            radioExo.play();
            // Keep the screen on while listening; Glow dims it itself after a minute without a key press.
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            scheduleRadioDim();
            Net.image(radioArt, st.logo, Ui.dp(240), () -> radioArt.setImageDrawable(new SourceArt(Ui.initials(st.name), Ui.ACCENT, Ui.dp(12))));
        }
        radioList.setPlaying(radioPlaying == null ? null : radioPlaying.uuid, radioSong);
        showRadioInfo(st, number);
    }

    private void stopRadio() {
        if (radioExo != null) radioExo.stop();
        radioPlaying = null;
        radioSong = null;
        radioList.setPlaying(null, null);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        radioDim.removeCallbacks(radioDimNow);
    }

    private void buildRadioDim(FrameLayout root) {
        radioDim = new FrameLayout(this);
        radioDim.setBackgroundColor(0xF2050810);
        radioDim.setVisibility(View.GONE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        radioDimClock = Ui.text(this, 72, 0x99FFFFFF, 300);
        radioDimName = Ui.text(this, 20, 0x99FFFFFF, 600);
        radioDimName.setPadding(0, Ui.dp(18), 0, 0);
        radioDimSong = Ui.text(this, 15, 0x778FA8FF, 400);
        radioDimSong.setPadding(0, Ui.dp(6), 0, 0);
        for (TextView t : new TextView[] {radioDimClock, radioDimName, radioDimSong}) {
            t.setGravity(Gravity.CENTER);
            box.addView(t);
        }
        radioDim.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(radioDim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void scheduleRadioDim() {
        radioDim.removeCallbacks(radioDimNow);
        if (radioPlaying != null) radioDim.postDelayed(radioDimNow, RADIO_DIM_MS);
    }

    /** Long listening: dim the screen (clock, station, song), moved a little each time against burn-in. */
    private void showRadioDim() {
        if (radioPlaying == null) return;
        radioDimClock.setText(DateFormat.format(Ui.is24h(this) ? "HH:mm" : "h:mm", System.currentTimeMillis()));
        radioDimName.setText("♫  " + radioPlaying.name);
        radioDimSong.setText(radioSong == null ? "" : radioSong);
        View box = radioDim.getChildAt(0); // move the words, not the dark layer, so the whole screen stays dimmed
        box.setTranslationX((float) (Math.random() - 0.5) * Ui.dp(60));
        box.setTranslationY((float) (Math.random() - 0.5) * Ui.dp(40));
        radioDim.setAlpha(0f);
        radioDim.setVisibility(View.VISIBLE);
        radioDim.animate().alpha(1f).setDuration(1200).start();
        radioDim.postDelayed(radioDimNow, RADIO_DIM_MS); // refresh the clock and move it
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (radioDim != null && radioDim.getVisibility() == View.VISIBLE) { // any key wakes the screen, nothing else
            if (e.getAction() == KeyEvent.ACTION_UP) {
                radioDim.setVisibility(View.GONE);
                scheduleRadioDim();
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && radioPlaying != null) scheduleRadioDim();
        return super.dispatchKeyEvent(e);
    }

    // ---- grid callbacks ----

    @Override
    public void onSelect(Channel ch, Guide.Prog prog, int number) {
        if (number < 0) { // typing a channel number
            setTime("");
            infoTitle.setText("Channel " + (-number) + "_");
            infoChannel.setText("");
            infoNext.setText("");
            return;
        }
        showInfo(ch, prog, number);
        wanted = ch;
        preview.removeCallbacks(startPreview);
        if (ch != previewing) {
            if (exo != null) exo.stop();
            previewing = null;
            previewNote.setText(ch.name);
            previewNote.setVisibility(View.VISIBLE);
            preview.postDelayed(startPreview, PREVIEW_DWELL_MS);
        }
    }

    private void showInfo(Channel ch, Guide.Prog prog, int number) {
        if (ch.logo == null || ch.logo.isEmpty()) {
            infoLogoUrl = null;
            infoLogo.setVisibility(View.INVISIBLE);
        } else if (!ch.logo.equals(infoLogoUrl)) {
            infoLogoUrl = ch.logo;
            infoLogo.setVisibility(View.VISIBLE);
            // Trimmed of its empty border too, so it centres on the text block like the list logos do.
            final String want = ch.logo;
            infoLogo.setImageDrawable(null);
            Net.bitmap(this, want, Ui.dp(240), bm -> {
                if (!want.equals(infoLogoUrl)) return;
                if (bm == null) infoLogo.setVisibility(View.INVISIBLE);
                else infoLogo.setImageBitmap(GuideGridView.trimmed(want, bm));
            });
        }
        CharSequence now = DateFormat.format(Ui.is24h(this) ? "HH:mm" : "h:mm a", System.currentTimeMillis());
        if (!now.toString().contentEquals(clock.getText())) clock.setText(now);
        String num = number > 0 ? number + "  " : "";
        String where = ch.group == null || ch.group.isEmpty() ? "" : "  ·  " + ch.group;
        infoChannel.setText(num + ch.name + where);
        if (prog == null) {
            setTime("");
            infoTitle.setText(ch.name);
            infoDesc.setVisibility(View.GONE);
            infoNext.setText(Guide.hasData(ch) ? "No listings for this time  ·  OK to watch"
                    : Guide.hasThai(ch) ? "Fetching this channel's schedule…" : "Live now  ·  OK to watch");
            return;
        }
        boolean live = prog.start <= System.currentTimeMillis();
        setTime((live ? "NOW  " : "") + time(prog.start) + " – " + time(prog.stop));
        infoTitle.setText(prog.title);
        infoDesc.setText(prog.desc == null ? "" : prog.desc);
        infoDesc.setVisibility(prog.desc == null ? View.GONE : View.VISIBLE);
        Guide.Prog after = null;
        for (Guide.Prog p : Guide.between(ch, prog.stop, prog.stop + 12 * 60 * 60 * 1000L)) {
            if (p.start >= prog.stop) { after = p; break; }
        }
        String then = after == null ? (live ? "OK to watch" : "") : "Then  " + time(after.start) + "  " + after.title;
        if (!live) then = (Reminders.has(this, ch, prog) ? "⏰ Reminder set  ·  OK to clear" : "OK to set a reminder") + (then.isEmpty() ? "" : "   ·   " + then);
        infoNext.setText(then);
    }

    /** The time line above the title disappears when empty, so the text block stays centred on the logo. */
    private void setTime(String t) {
        infoTime.setText(t);
        infoTime.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String guideLegend() {
        return "▲▼ Channels     " + (grid != null && grid.nowNext() ? "◀▶ Now / Next" : "◀▶ Time")
                + "     0–9 Channel number     OK Watch / ⏰ Remind (later shows)     ■ Blue: ★ favourite     BACK Menu";
    }

    private String time(long t) {
        return DateFormat.format(Ui.is24h(this) ? "HH:mm" : "h.mma", t).toString().toLowerCase();
    }

    @Override
    public void onWatch(Channel ch, int indexInList) {
        if (section == null) return;
        stopPreview("");
        Xtream.playlist = new ArrayList<>(section.channels);
        startActivity(new Intent(this, PlayerActivity.class).putExtra("index", indexInList));
    }

    @Override
    public void onRemind(Channel ch, Guide.Prog prog) {
        boolean on = Reminders.toggle(this, ch, prog);
        android.widget.Toast.makeText(this, on ? "⏰  Reminder set: " + prog.title + " at " + time(prog.start)
                : "Reminder cleared: " + prog.title, android.widget.Toast.LENGTH_SHORT).show();
        grid.dataChanged();
        showInfo(ch, prog, lineup.number(ch));
    }

    @Override
    public void onNeedGuide(List<Channel> visible) {
        for (Channel ch : visible) Guide.wantThai(this, ch, guideChanged);
    }

    private void refresh() {
        if (section == null || isFinishing()) return;
        grid.dataChanged();
        Channel ch = section.channels.get(grid.selectedIndex());
        showInfo(ch, grid.selectedProg(), lineup.number(ch));
    }

    // ---- muted live preview ----

    private final Runnable startPreview = () -> {
        if (wanted == null || isFinishing()) return;
        previewing = wanted;
        previewAttempts = new ArrayList<>();
        for (Sources.Attempt a : Sources.forChannel(this, wanted, Xtream.load(this))) {
            if (a.url != null && !a.builtIn && a.youtubeChannels == null) previewAttempts.add(a);
        }
        previewAttempt = -1;
        nextPreviewAttempt();
    };

    private void nextPreviewAttempt() {
        previewAttempt++;
        if (previewing == null) return;
        if (previewAttempt >= Math.min(3, previewAttempts.size())) {
            previewNote.setText(previewing.name + "\nNo preview  ·  OK to watch");
            previewNote.setVisibility(View.VISIBLE);
            return;
        }
        if (exo == null) createExo();
        previewNote.setText(previewing.name);
        exo.setMediaSource(PlayerActivity.source(previewAttempts.get(previewAttempt)));
        exo.prepare();
    }

    private void createExo() {
        DefaultTrackSelector tracks = new DefaultTrackSelector(this);
        // A 320×180 window: the smallest rendition is plenty and keeps the decoder and network light.
        tracks.setParameters(tracks.buildUponParameters().setMaxVideoSize(854, 480).setForceLowestBitrate(true));
        DefaultLoadControl load = new DefaultLoadControl.Builder().setBufferDurationsMs(4_000, 8_000, 1_000, 2_000).build();
        exo = new ExoPlayer.Builder(this).setTrackSelector(tracks).setLoadControl(load).build();
        exo.setVolume(0f);
        exo.setVideoSurfaceView(previewSurface);
        exo.setPlayWhenReady(true);
        exo.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                previewNote.setVisibility(View.GONE);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                nextPreviewAttempt();
            }
        });
    }

    private void stopPreview(String note) {
        preview.removeCallbacks(startPreview);
        previewing = null;
        wanted = null;
        if (exo != null) exo.stop();
        previewNote.setText(note);
        previewNote.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (section != null) refresh();
        if (section != null) {
            wanted = section.channels.get(grid.selectedIndex());
            preview.postDelayed(startPreview, PREVIEW_DWELL_MS);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRadio(); // leaving the guide (to watch TV, or Home) ends the radio too
        if (radioExo != null) {
            radioExo.release();
            radioExo = null;
        }
        stopPreview("");
        if (exo != null) {
            exo.release();
            exo = null;
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (radioMode && radioSection < 0 && code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            openRadio(code == KeyEvent.KEYCODE_0 ? 9 : code - KeyEvent.KEYCODE_1);
            return true;
        }
        if (lineup == null) return super.onKeyDown(code, e);
        if (radioMode) return super.onKeyDown(code, e);
        if (colourKey(code)) return true;
        if (section == null && code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            if (code == KeyEvent.KEYCODE_0) {
                if (!inMore) showMenu(true, 0);
                return true;
            }
            List<GuideLineup.Section> list = inMore ? lineup.more : lineup.main;
            int i = code - KeyEvent.KEYCODE_1;
            if (i < list.size()) openSection(list.get(i), null);
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public void onBackPressed() {
        if (radioSection >= 0) { // station list -> radio menu -> TV guide menu
            showRadioMenu(radioSection);
            return;
        }
        if (radioMode && lineup != null) {
            showMenu(false, 0);
            return;
        }
        if (lineup != null && section != null) {
            int inMain = lineup.main.indexOf(section);
            if (inMain >= 0) showMenu(false, inMain);
            else showMenu(true, lineup.more.indexOf(section));
            return;
        }
        if (lineup != null && inMore) {
            showMenu(false, 9);
            return;
        }
        super.onBackPressed();
    }
}
