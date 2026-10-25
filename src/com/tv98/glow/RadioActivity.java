package com.tv98.glow;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
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

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Radio: thousands of free stations from radio-browser.info. Sections on the left, stations in the middle,
 * now playing on the right (with the song title when the station sends it). While a station plays the screen
 * dims after 30 seconds; any key brings it back.
 */
public class RadioActivity extends Activity {
    private static final long DIM_AFTER_MS = 30_000;

    private ListView sectionList;
    private GridView grid;
    private TextView heading, nowName, nowSong, nowInfo, nowState, dimClock, dimName, dimSong;
    private ImageView nowLogo, dimLogo;
    private FrameLayout dim;
    private List<Radio.Station> shown = new ArrayList<>();
    private Radio.Station playing;
    private ExoPlayer exo;
    private Tile lit;
    private int sectionGen;
    private final Runnable apply = this::applySection;
    private final Runnable dimNow = this::showDim;
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            dimClock.setText(DateFormat.format(Ui.is24h(RadioActivity.this) ? "HH:mm" : "h:mm", System.currentTimeMillis()));
            dimClock.postDelayed(this, 20_000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        FrameLayout frame = new FrameLayout(this);
        LinearLayout root = new LinearLayout(this);
        root.setBackground(new Ui.Aurora());
        root.setPadding(Ui.dp(32), Ui.dp(28), Ui.dp(24), Ui.dp(24));
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Sections.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackground(Ui.glass(Ui.dp(18), false));
        left.setPadding(Ui.dp(10), Ui.dp(16), Ui.dp(10), Ui.dp(10));
        root.addView(left, new LinearLayout.LayoutParams(Ui.dp(190), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView t = Ui.label(this, "Radio");
        t.setPadding(Ui.dp(12), 0, 0, Ui.dp(10));
        left.addView(t);
        sectionList = new ListView(this);
        sectionList.setDivider(null);
        sectionList.setSelector(Ui.glass(Ui.dp(10), true));
        sectionList.setVerticalScrollBarEnabled(false);
        sectionList.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return Radio.SECTIONS.length; }
            @Override public Object getItem(int i) { return Radio.SECTIONS[i]; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                TextView tv = convert instanceof TextView ? (TextView) convert : Ui.text(RadioActivity.this, 15, Ui.TEXT, 400);
                tv.setText(Radio.SECTIONS[i][0]);
                tv.setSingleLine();
                tv.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(10), Ui.dp(10));
                return tv;
            }
        });
        sectionList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                sectionList.removeCallbacks(apply);
                sectionList.postDelayed(apply, 250);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        sectionList.setOnItemClickListener((p, v, pos, id) -> grid.requestFocus());
        left.addView(sectionList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView credit = Ui.text(this, 10, Ui.TEXT_DIM, 400);
        credit.setText("Stations: radio-browser.info");
        credit.setPadding(Ui.dp(12), Ui.dp(8), 0, Ui.dp(2));
        left.addView(credit);

        // Stations.
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setPadding(Ui.dp(20), 0, 0, 0);
        root.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        heading = Ui.text(this, 24, Ui.TEXT, 300);
        heading.setPadding(Ui.dp(6), 0, 0, 0);
        middle.addView(heading);
        grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(Ui.dp(10));
        grid.setVerticalSpacing(Ui.dp(10));
        grid.setPadding(0, Ui.dp(14), Ui.dp(6), Ui.dp(14));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setSelector(new ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(new StationAdapter());
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { light(grid.hasFocus() ? (Tile) v : null); }
            @Override public void onNothingSelected(AdapterView<?> p) { light(null); }
        });
        grid.setOnFocusChangeListener((v, has) -> light(has ? (Tile) grid.getSelectedView() : null));
        grid.setOnItemClickListener((p, v, pos, id) -> { if (pos < shown.size()) play(shown.get(pos)); });
        middle.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Now playing.
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xCC1A2140, 0xCC0B0E1C});
        pbg.setCornerRadius(Ui.dp(18));
        pbg.setStroke(Math.max(1, Ui.dp(0.75f)), 0x408FA8FF);
        panel.setBackground(pbg);
        panel.setPadding(Ui.dp(18), Ui.dp(22), Ui.dp(18), Ui.dp(18));
        nowState = Ui.label(this, "Choose a station");
        nowState.setPadding(0, 0, 0, Ui.dp(14));
        panel.addView(nowState);
        nowLogo = new ImageView(this);
        nowLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        panel.addView(nowLogo, new LinearLayout.LayoutParams(Ui.dp(150), Ui.dp(150)));
        nowName = Ui.text(this, 19, Ui.TEXT, 600);
        nowName.setGravity(Gravity.CENTER);
        nowName.setMaxLines(2);
        nowName.setEllipsize(TextUtils.TruncateAt.END);
        nowName.setPadding(0, Ui.dp(16), 0, 0);
        nowSong = Ui.text(this, 14, Ui.ACCENT, 400);
        nowSong.setGravity(Gravity.CENTER);
        nowSong.setMaxLines(3);
        nowSong.setEllipsize(TextUtils.TruncateAt.END);
        nowSong.setPadding(0, Ui.dp(10), 0, 0);
        nowInfo = Ui.text(this, 11, Ui.TEXT_DIM, 400);
        nowInfo.setGravity(Gravity.CENTER);
        nowInfo.setPadding(0, Ui.dp(10), 0, 0);
        panel.addView(nowName);
        panel.addView(nowSong);
        panel.addView(nowInfo);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(290), ViewGroup.LayoutParams.MATCH_PARENT);
        plp.leftMargin = Ui.dp(18);
        root.addView(panel, plp);

        // Dimmed screen while listening.
        dim = new FrameLayout(this);
        dim.setBackgroundColor(0xF2050810);
        dim.setVisibility(View.GONE);
        LinearLayout dimBox = new LinearLayout(this);
        dimBox.setOrientation(LinearLayout.VERTICAL);
        dimBox.setGravity(Gravity.CENTER);
        dimClock = Ui.text(this, 72, 0x99FFFFFF, 300);
        dimLogo = new ImageView(this);
        dimLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dimLogo.setAlpha(0.6f);
        dimName = Ui.text(this, 18, 0x99FFFFFF, 600);
        dimName.setPadding(0, Ui.dp(14), 0, 0);
        dimSong = Ui.text(this, 14, 0x778FA8FF, 400);
        dimSong.setPadding(0, Ui.dp(6), 0, 0);
        dimBox.addView(dimClock);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(96), Ui.dp(96));
        dl.topMargin = Ui.dp(20);
        dimBox.addView(dimLogo, dl);
        dimBox.addView(dimName);
        dimBox.addView(dimSong);
        for (TextView tv : new TextView[] {dimClock, dimName, dimSong}) tv.setGravity(Gravity.CENTER);
        dim.addView(dimBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        frame.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(frame);
        sectionList.requestFocus();
        applySection();
    }

    private void applySection() {
        final int pos = Math.max(0, sectionList.getSelectedItemPosition());
        final int gen = ++sectionGen;
        heading.setText(Radio.SECTIONS[pos][0]);
        Net.IO.execute(() -> {
            final List<Radio.Station> list = Radio.section(this, pos);
            Net.MAIN.post(() -> {
                if (gen != sectionGen || isFinishing()) return;
                shown = list;
                lit = null;
                heading.setText(Radio.SECTIONS[pos][0] + "   ·   " + (list.isEmpty() ? "couldn't load stations" : list.size()));
                ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
            });
        });
    }

    // ---- playing ----

    private void play(Radio.Station s) {
        playing = s;
        nowState.setText("CONNECTING…");
        nowName.setText(s.name);
        nowSong.setText("");
        nowInfo.setText(Radio.describe(s));
        Net.image(nowLogo, s.logo, Ui.dp(300), () -> nowLogo.setImageDrawable(new SourceArt(Ui.initials(s.name), Ui.ACCENT, Ui.dp(16))));
        if (exo == null) {
            exo = new ExoPlayer.Builder(this).build();
            exo.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        nowState.setText("NOW PLAYING");
                        scheduleDim();
                    } else if (state == Player.STATE_BUFFERING) {
                        nowState.setText("CONNECTING…");
                    }
                }

                @Override
                public void onMediaMetadataChanged(MediaMetadata m) {
                    // Song titles stations send in their stream ("Artist - Title").
                    CharSequence song = m.title != null ? m.title : m.displayTitle;
                    if (song != null && song.length() > 0) {
                        nowSong.setText(song);
                        dimSong.setText(song);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException e) {
                    nowState.setText("THIS STATION ISN'T AVAILABLE");
                    android.util.Log.i("GlowPlayer", "radio " + (playing == null ? "" : playing.name) + ": " + e.getErrorCodeName());
                }
            });
        }
        exo.setMediaItem(MediaItem.fromUri(s.url));
        exo.prepare();
        exo.play();
    }

    private void scheduleDim() {
        dim.removeCallbacks(dimNow);
        if (exo != null && exo.isPlaying()) dim.postDelayed(dimNow, DIM_AFTER_MS);
    }

    private void showDim() {
        if (playing == null || exo == null || !exo.isPlaying()) return;
        dimName.setText(playing.name);
        dimLogo.setImageDrawable(nowLogo.getDrawable());
        clockTick.run();
        dim.setAlpha(0f);
        dim.setVisibility(View.VISIBLE);
        dim.animate().alpha(1f).setDuration(1200).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (dim.getVisibility() == View.VISIBLE) { // any key wakes the screen, and does nothing else
            if (e.getAction() == KeyEvent.ACTION_UP) {
                dim.setVisibility(View.GONE);
                dimClock.removeCallbacks(clockTick);
                scheduleDim();
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) scheduleDim();
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onStop() {
        super.onStop();
        dim.removeCallbacks(dimNow);
        if (exo != null) {
            exo.release();
            exo = null;
        }
        nowState.setText("Choose a station");
    }

    private void light(Tile t) {
        if (lit == t) return;
        if (lit != null) lit.setLit(false);
        lit = t;
        if (t != null) t.setLit(true);
    }

    /** A station: logo, name, and where it's from / what it plays. */
    private final class StationAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Tile t;
            if (convert instanceof Tile) {
                t = (Tile) convert;
                if (t == lit) light(null);
            } else {
                t = new Tile(RadioActivity.this, 0, 64);
                t.setFocusable(false);
                t.setClickable(false);
                t.setLift(1.02f);
                t.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(64)));
                LinearLayout line = new LinearLayout(RadioActivity.this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(Ui.dp(10), 0, Ui.dp(12), 0);
                ImageView logo = new ImageView(RadioActivity.this);
                logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44));
                llp.rightMargin = Ui.dp(12);
                line.addView(logo, llp);
                LinearLayout words = new LinearLayout(RadioActivity.this);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView name = Ui.text(RadioActivity.this, 14, Ui.TEXT, 600);
                name.setSingleLine();
                name.setEllipsize(TextUtils.TruncateAt.END);
                TextView meta = Ui.text(RadioActivity.this, 11, Ui.TEXT_DIM, 400);
                meta.setSingleLine();
                meta.setEllipsize(TextUtils.TruncateAt.END);
                meta.setPadding(0, Ui.dp(4), 0, 0);
                words.addView(name);
                words.addView(meta);
                line.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                t.card.addView(line);
            }
            Radio.Station s = shown.get(i);
            LinearLayout line = (LinearLayout) t.card.getChildAt(0);
            final ImageView logo = (ImageView) line.getChildAt(0);
            Net.image(logo, s.logo, Ui.dp(88), () -> logo.setImageDrawable(new SourceArt(Ui.initials(s.name), Ui.ACCENT, Ui.dp(8))));
            LinearLayout words = (LinearLayout) line.getChildAt(1);
            ((TextView) words.getChildAt(0)).setText(s.name);
            ((TextView) words.getChildAt(1)).setText(Radio.describe(s));
            return t;
        }
    }
}
